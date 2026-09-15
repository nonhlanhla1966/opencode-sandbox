#!/usr/bin/env python3
"""spec_validate.py — Fast Lane 3.0 Specification Validation Engine.

Validates app-spec.json against canonical schema, computes sha256 checksum,
records assumptions for missing info, emits structured validation report.

Usage:
  spec_validate.py check <app-spec.json>       → validate + emit report
  spec_validate.py checksum <app-spec.json>    → emit sha256 only
  spec_validate.py contract <spec.json> <arch.json> [app-dir] → spec coverage

Exit 0 if valid/satisfied, exit 1 if invalid, exit 2 on usage error.
Output: JSON to stdout.
"""

import hashlib
import json
import os
import re
import sys
from pathlib import Path

REQUIRED_KEYS = [
    "name", "slug", "application_id", "summary", "features", "screens",
    "navigation", "data", "modules", "archetype", "complexity", "estimate",
]

OPTIONAL_KEYS = [
    "apis", "permissions", "auth", "storage", "notifications", "media",
    "testing", "spec_version", "assumptions", "checksum_sha256",
]

ASSUMPTION_KEYWORDS = [
    ("default_ui", "UI details not specified"),
    ("default_auth", "Authentication not specified"),
    ("default_storage", "Storage preferences not specified"),
    ("default_testing", "Testing requirements not specified"),
    ("default_permissions", "Permissions not specified"),
]

SPEC_V3_REQUIRED = [
    "name", "slug", "application_id", "summary", "features", "screens",
    "navigation", "data", "modules", "archetype", "complexity", "estimate",
    "spec_version", "assumptions",
]


def compute_checksum(spec: dict) -> str:
    """Deterministic sha256 of the canonical spec (sorted keys, no metadata)."""
    clean = {k: v for k, v in spec.items()
             if k not in ("checksum_sha256", "generated_by", "final", "spec_version")}
    canonical = json.dumps(clean, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def detect_assumptions(spec: dict) -> list:
    """Record what assumptions were made for missing info."""
    assumptions = list(spec.get("assumptions", []))
    if not spec.get("apis"):
        assumptions.append({"key": "default_apis", "note": "No API requirements specified; assuming offline-only where applicable"})
    if not spec.get("auth") or spec.get("auth") == "none":
        assumptions.append({"key": "default_auth", "note": "No authentication specified; using none"})
    if not spec.get("storage") or spec.get("storage") == ["datastore/prefs"]:
        assumptions.append({"key": "default_storage", "note": "Using default storage (datastore/prefs)"})
    if not spec.get("testing") or spec.get("testing") == ["unit"]:
        assumptions.append({"key": "default_testing", "note": "Using default testing (unit only)"})
    if not spec.get("permissions"):
        assumptions.append({"key": "default_permissions", "note": "No permissions required"})
    if len(spec.get("screens", [])) <= 1:
        assumptions.append({"key": "default_ui", "note": "Minimal screens; navigation simplified"})
    return assumptions


def validate_spec(spec: dict) -> dict:
    """Validate spec against canonical schema. Returns report dict."""
    errors = []
    warnings = []

    # Check required keys
    for key in REQUIRED_KEYS:
        if key not in spec:
            errors.append(f"missing required key: {key}")

    # Type checks
    if "features" in spec and not isinstance(spec["features"], list):
        errors.append("features must be a list")
    if "screens" in spec and not isinstance(spec["screens"], list):
        errors.append("screens must be a list")
    if "complexity" in spec:
        cx = spec["complexity"]
        if not isinstance(cx, dict):
            errors.append("complexity must be an object")
        elif "level" not in cx or "score" not in cx:
            errors.append("complexity must have level and score")
        elif cx["level"] not in ("SIMPLE", "MEDIUM", "COMPLEX", "EXTREME"):
            errors.append(f"invalid complexity level: {cx['level']}")
        elif not (0 <= cx.get("score", 0) <= 100):
            errors.append("complexity score must be 0-100")
    if "estimate" in spec:
        est = spec["estimate"]
        if not isinstance(est, dict):
            errors.append("estimate must be an object")
        elif "build_time_seconds" not in est:
            errors.append("estimate must have build_time_seconds")
    if "modules" in spec and not isinstance(spec["modules"], list):
        errors.append("modules must be a list")

    # Name/slug checks
    if "slug" in spec:
        slug = spec["slug"]
        if not re.match(r'^[a-z0-9][a-z0-9\-]*$', slug):
            warnings.append(f"slug format unusual: {slug}")
    if "application_id" in spec:
        aid = spec["application_id"]
        if not re.match(r'^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$', aid):
            warnings.append(f"application_id format unusual: {aid}")

    # FL3 spec_version check
    if spec.get("spec_version") == "3.0":
        for key in SPEC_V3_REQUIRED:
            if key not in spec:
                errors.append(f"FL3 spec_version=3.0 requires key: {key}")

    return {
        "valid": len(errors) == 0,
        "spec_version": spec.get("spec_version", "2.0"),
        "errors": errors,
        "warnings": warnings,
        "assumptions": detect_assumptions(spec),
    }


def validate_checksum(spec: dict) -> dict:
    """Validate checksum if present, or compute and attach."""
    checksum = compute_checksum(spec)
    stored = spec.get("checksum_sha256")
    if stored and stored != checksum:
        return {
            "valid": False,
            "checksum_sha256": checksum,
            "stored_checksum": stored,
            "error": "checksum mismatch — spec was modified after generation",
        }
    return {"valid": True, "checksum_sha256": checksum}


def spec_coverage(spec: dict, arch: dict, app_dir: str = None) -> dict:
    """Map features to modules → source files → tests → gate. Emit coverage report."""
    features = spec.get("features", [])
    modules = spec.get("modules", [])
    app_path = Path(app_dir) if app_dir else None

    coverage = []
    for feature in features:
        # Find which modules serve this feature
        feature_modules = []
        for mod in modules:
            mod_lower = mod.lower().replace("-", " ").replace("_", " ")
            feature_lower = feature.lower().replace("-", " ").replace("_", " ")
            # Heuristic: feature keyword appears in module name or vice versa
            if any(w in mod_lower for w in feature_lower.split() if len(w) > 3):
                feature_modules.append(mod)

        # Find source/test evidence
        source_files = []
        test_files = []
        if app_path and app_path.is_dir():
            src_dir = app_path / "src"
            if src_dir.is_dir():
                for java_file in src_dir.rglob("*.java"):
                    try:
                        content = java_file.read_text(errors="ignore").lower()
                        if feature.lower().split()[0][:4] in content:
                            source_files.append(str(java_file.relative_to(app_path)))
                    except Exception:
                        pass
                for test_file in src_dir.rglob("*Test.java"):
                    try:
                        content = test_file.read_text(errors="ignore").lower()
                        if feature.lower().split()[0][:4] in content:
                            test_files.append(str(test_file.relative_to(app_path)))
                    except Exception:
                        pass

        # Gate mapping (heuristic)
        gate_evidence = []
        if source_files:
            gate_evidence.append("src_implemented")
        if test_files:
            gate_evidence.append("test_implemented")
        if not feature_modules:
            gate_evidence.append("no_module_mapping")

        covered = bool(source_files or feature_modules)
        coverage.append({
            "feature": feature,
            "modules": feature_modules,
            "source_files": source_files,
            "test_files": test_files,
            "gate_evidence": gate_evidence,
            "covered": covered,
        })

    total = len(coverage) if coverage else 1
    covered_count = sum(1 for c in coverage if c["covered"])
    coverage_fraction = covered_count / total if total > 0 else 0.0

    return {
        "features_total": len(features),
        "features_covered": covered_count,
        "coverage_fraction": round(coverage_fraction, 3),
        "coverage_percentage": round(coverage_fraction * 100, 1),
        "per_feature": coverage,
        "spec_version": spec.get("spec_version", "2.0"),
        "checksum_sha256": spec.get("checksum_sha256", compute_checksum(spec)),
    }


def cmd_check(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: spec_validate.py check <app-spec.json>\n")
        return 2
    spec_path = Path(argv[2])
    if not spec_path.is_file():
        sys.stderr.write(f"error: {spec_path} not found\n")
        return 1
    spec = json.loads(spec_path.read_text())

    validation = validate_spec(spec)
    checksum = validate_checksum(spec)

    report = {
        "command": "check",
        "spec_file": str(spec_path),
        **validation,
        "checksum": checksum,
    }

    print(json.dumps(report, indent=1))
    return 0 if validation["valid"] else 1


def cmd_checksum(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: spec_validate.py checksum <app-spec.json>\n")
        return 2
    spec_path = Path(argv[2])
    if not spec_path.is_file():
        sys.stderr.write(f"error: {spec_path} not found\n")
        return 1
    spec = json.loads(spec_path.read_text())
    checksum = compute_checksum(spec)
    print(json.dumps({"checksum_sha256": checksum, "spec_file": str(spec_path)}))
    return 0


def cmd_contract(argv):
    if len(argv) < 4:
        sys.stderr.write("usage: spec_validate.py contract <spec.json> <arch.json> [app-dir]\n")
        return 2
    spec = json.loads(Path(argv[2]).read_text())
    arch = json.loads(Path(argv[3]).read_text())
    app_dir = argv[4] if len(argv) > 4 else None
    report = spec_coverage(spec, arch, app_dir)
    print(json.dumps(report, indent=1))
    return 0


def main(argv):
    if len(argv) < 2 or argv[1] not in ("check", "checksum", "contract"):
        sys.stderr.write("usage: spec_validate.py <check|checksum|contract> [args...]\n")
        return 2
    cmd = argv[1]
    if cmd == "check":
        return cmd_check(argv)
    elif cmd == "checksum":
        return cmd_checksum(argv)
    elif cmd == "contract":
        return cmd_contract(argv)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
