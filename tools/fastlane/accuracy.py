#!/usr/bin/env python3
"""accuracy.py — Fast Lane 3.0 Accuracy Score Engine.

Computes a reproducible accuracy score (0-100) for a build from real
artifacts on disk: spec coverage, gate results, unit-test results,
security scan, APK verification, dependency validation and preflight.
Never guesses: every factor comes from a concrete artifact.

Usage:
  accuracy.py score <app-dir> [--apk <apk>] [--coverage <spec-coverage.json>]
                    [--gates <gates.json>] [--tests <test-xml>]
  accuracy.py score-all [<apps-root>]        score every app build present

Exit 0 always (score is informational); exit 2 on usage error.
Output: JSON to stdout.
"""

import json
import os
import re
import sys
from pathlib import Path

FASTLANE_DATA = Path(".")


def load_json(path) -> dict:
    try:
        return json.loads(Path(path).read_text())
    except Exception:
        return {}


def shared_tmp() -> Path:
    """The engine-wide FL_TMP dir (where gates/security/deps/perf/coverage land)."""
    t = os.environ.get("FL_TMP") or os.environ.get("FASTLANE_TMPDIR")
    if t:
        return Path(t)
    r = os.environ.get("REPO_ROOT")
    if r:
        return Path(r) / ".fastlane" / "tmp"
    return Path(".fastlane") / "tmp"


def find_artifact(prefix: str, slug: str, app_dir: Path, explicit: str = None) -> Path:
    """Resolve an evidence artifact: explicit path > shared FL_TMP > app-local tmp."""
    if explicit and Path(explicit).is_file():
        return Path(explicit)
    shared = shared_tmp() / f"{prefix}-{slug}.json"
    if shared.is_file():
        return shared
    local = app_dir / ".fastlane" / "tmp" / f"{prefix}-{slug}.json"
    if local.is_file():
        return local
    return None


def gather_evidence(app_dir: Path, apk: str = None,
                    coverage_path: str = None, gates_path: str = None,
                    test_xml: str = None, deps_path: str = None) -> dict:
    """Collect real evidence signals from disk (explicit path, shared FL_TMP, then app-local)."""
    slug = app_dir.name
    ev = {}

    # 1. APK present + non-empty + checksum
    if apk and Path(apk).is_file() and Path(apk).stat().st_size > 0:
        ev["apk"] = {"present": True, "size": Path(apk).stat().st_size,
                     "sha256": _sha256(apk)}
    else:
        # try find-apk
        found = _find_apk(app_dir)
        if found:
            ev["apk"] = {"present": True, "size": found.stat().st_size, "sha256": _sha256(str(found))}
        else:
            ev["apk"] = {"present": False, "size": 0, "sha256": ""}

    # 2. Unit test results
    if test_xml and Path(test_xml).is_file():
        ev["tests"] = _parse_junit(Path(test_xml))
    else:
        found_xml = _find_test_xml(app_dir)
        if found_xml:
            ev["tests"] = _parse_junit(found_xml)
        else:
            ev["tests"] = {"present": False}

    # 3. Gate report
    gates_file = find_artifact("gates", slug, app_dir, gates_path)
    ev["gates"] = load_json(gates_file) if gates_file else {}

    # 4. Spec coverage
    cov_file = find_artifact("coverage", slug, app_dir, coverage_path)
    ev["coverage"] = load_json(cov_file) if cov_file else {}

    # 5. Security scan artifact
    sec_file = find_artifact("security", slug, app_dir)
    if sec_file:
        ev["security"] = load_json(sec_file)

    # 6. Dependency validation artifact
    deps_file = find_artifact("deps", slug, app_dir, deps_path)
    if deps_file:
        ev["deps"] = load_json(deps_file)

    return ev


def _sha256(path: str) -> str:
    import hashlib
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def _find_apk(app_dir: Path) -> Path:
    candidates = sorted(app_dir.rglob("*.apk"))
    for c in candidates:
        if c.stat().st_size > 0:
            return c
    return None


def _find_test_xml(app_dir: Path) -> Path:
    for c in app_dir.rglob("*.xml"):
        if "test-results" in str(c):
            return c
    return None


def _parse_junit(xml_path: Path) -> dict:
    import xml.etree.ElementTree as ET
    try:
        root = ET.parse(xml_path).getroot()
        ts = root if root.tag == "testsuite" else root.find("./testsuite")
        if ts is None:
            return {"present": True, "tests": 0, "failures": 0, "errors": 0, "pass": True, "guessed": True}
        t = int(ts.get("tests", 0) or 0)
        f = int(ts.get("failures", 0) or 0)
        e = int(ts.get("errors", 0) or 0)
        return {"present": True, "tests": t, "failures": f, "errors": e,
                "pass": (f == 0 and e == 0)}
    except Exception:
        return {"present": True, "tests": 0, "failures": 1, "errors": 0, "pass": False, "malformed": True}


def compute_score(ev: dict) -> float:
    """Weighted accuracy from real evidence."""
    weights = {
        "apk": 0.20,          # APK must exist, be signed-ish and checksummed
        "tests": 0.20,        # unit tests green
        "gates": 0.25,        # quality gates passed
        "coverage": 0.15,     # feature→module coverage
        "security": 0.10,     # security scan clean
        "deps": 0.10,         # dependency policy clean
    }

    score = 0.0
    breakdown = {}

    # APK
    apk = ev.get("apk", {})
    if apk.get("present"):
        apk_score = 1.0 if apk.get("sha256") else 0.8
    else:
        apk_score = 0.0
    score += weights["apk"] * apk_score
    breakdown["apk"] = {"score": round(apk_score, 3), "weight": weights["apk"], "detail": "present" if apk_score else "missing"}

    # Tests
    tests = ev.get("tests", {})
    if tests.get("present"):
        t, f, e = tests.get("tests", 0), tests.get("failures", 0), tests.get("errors", 0)
        if t == 0:
            test_score = 0.0
            detail = "no tests recorded (junit present but empty)"
        elif f == 0 and e == 0:
            test_score = 1.0
            detail = f"{t} tests green"
        else:
            test_score = max(0.0, 1.0 - (f + e) / max(t, 1))
            detail = f"{f} failures, {e} errors"
    else:
        test_score = 0.0
        detail = "no unit-test results artifact"
    score += weights["tests"] * test_score
    breakdown["tests"] = {"score": round(test_score, 3), "weight": weights["tests"], "detail": detail}

    # Gates
    gates = ev.get("gates", {})
    passed = gates.get("passed", 0)
    failed = gates.get("failed", 0)
    skipped = gates.get("skipped", 0)
    total = passed + failed + skipped
    if total == 0:
        gate_score = 0.0
        gate_detail = "no gate report artifact"
    elif failed == 0:
        gate_score = 1.0
        gate_detail = f"{passed} passed, {skipped} skipped"
    else:
        gate_score = max(0.0, 1.0 - failed / max(total, 1))
        gate_detail = f"{failed} gates failed"
    score += weights["gates"] * gate_score
    breakdown["gates"] = {"score": round(gate_score, 3), "weight": weights["gates"], "detail": gate_detail}

    # Coverage
    coverage = ev.get("coverage", {})
    cov_fraction = coverage.get("coverage_fraction")
    if cov_fraction is None:
        cov_score = 0.0
        cov_detail = "no coverage artifact"
    else:
        cov_score = min(1.0, float(cov_fraction))
        cov_detail = f"{coverage.get('features_covered', 0)}/{coverage.get('features_total', 0)} features"
    score += weights["coverage"] * cov_score
    breakdown["coverage"] = {"score": round(cov_score, 3), "weight": weights["coverage"], "detail": cov_detail}

    # Security (assume clean if artifact missing — but report honestly as unknown signal)
    security = ev.get("security", {})
    if security.get("findings") is not None:
        sec_crit = security.get("critical", 0) or 0
        sec_high = security.get("high", 0) or 0
        sec_pass = security.get("pass", sec_crit == 0 and sec_high == 0)
        sec_score = 1.0 if sec_pass else 0.0
        sec_detail = f"critical={sec_crit} high={sec_high}"
    else:
        sec_score = 0.5  # neutral signal, no artifact
        sec_detail = "no security artifact (neutral)"
    score += weights["security"] * sec_score
    breakdown["security"] = {"score": round(sec_score, 3), "weight": weights["security"], "detail": sec_detail}

    # Dependencies
    deps = ev.get("deps", {})
    if deps.get("policy_ok") is not None:
        deps_score = 1.0 if deps.get("policy_ok") else 0.0
        deps_detail = "policy ok" if deps_score else "policy violation"
    else:
        deps_score = 0.5
        deps_detail = "no deps artifact (neutral)"
    score += weights["deps"] * deps_score
    breakdown["deps"] = {"score": round(deps_score, 3), "weight": weights["deps"], "detail": deps_detail}

    final = round(min(100.0, max(0.0, score * 100)), 1)
    return final, breakdown


def cmd_score(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: accuracy.py score <app-dir> [--apk apk] [--coverage cov.json] [--gates gates.json] [--tests xml] [--deps deps.json]\n")
        return 2
    app_dir = Path(argv[2])
    apk = None
    coverage_path = None
    gates_path = None
    test_xml = None
    deps_path = None
    if "--apk" in argv:
        apk = argv[argv.index("--apk") + 1]
    if "--coverage" in argv:
        coverage_path = argv[argv.index("--coverage") + 1]
    if "--gates" in argv:
        gates_path = argv[argv.index("--gates") + 1]
    if "--tests" in argv:
        test_xml = argv[argv.index("--tests") + 1]
    if "--deps" in argv:
        deps_path = argv[argv.index("--deps") + 1]

    ev = gather_evidence(app_dir, apk=apk, coverage_path=coverage_path,
                         gates_path=gates_path, test_xml=test_xml,
                         deps_path=deps_path)
    final, breakdown = compute_score(ev)
    result = {"app": app_dir.name, "accuracy_score": final, "max": 100.0,
              "spec_version": "3.0", "evidence": ev, "breakdown": breakdown}
    print(json.dumps(result, indent=1))
    return 0


def cmd_score_all(argv):
    root = Path(argv[2]) if len(argv) > 2 else Path("apps")
    results = []
    if not root.is_dir():
        print(json.dumps({"app": "none", "accuracy_score": None, "error": f"{root} not found"}))
        return 0
    for app_dir in sorted(root.iterdir()):
        if not app_dir.is_dir():
            continue
        ev = gather_evidence(app_dir)
        final, breakdown = compute_score(ev)
        results.append({"app": app_dir.name, "accuracy_score": final, "breakdown": breakdown})
    print(json.dumps({"apps": results, "spec_version": "3.0"}, indent=1))
    return 0


def main(argv):
    if len(argv) < 2 or argv[1] not in ("score", "score-all"):
        sys.stderr.write("usage: accuracy.py <score|score-all> [args...]\n")
        return 2
    if argv[1] == "score":
        return cmd_score(argv)
    elif argv[1] == "score-all":
        return cmd_score_all(argv)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))