#!/usr/bin/env python3
"""preflight.py — Fast Lane 3.0 Preflight + Predictive Error Engine.

Runs pre-build validation checks on a generated app before expensive
compilation: package/path mismatch, duplicate classes, duplicate resources,
missing manifest structure, missing file references, manifest XML validity,
and gradle sanity. Predicts failures using known failure signaturess
(knowledge base) and recommends patches.

Usage:
  preflight.py check <app-dir>                 → preflight report (exit 0/1)
  preflight.py predict <app-dir> <build-log>   → predictable failures + patches

Exit 0 if clean, exit 1 if failures found, exit 2 on usage error.
Output: JSON to stdout.
"""

import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

FAILURES_DB = Path(__file__).resolve().parent / "knowledge" / "failures.json"


def load_failures_db() -> dict:
    """Load known failure signatures from knowledge base."""
    if not FAILURES_DB.is_file():
        return {"version": "3.0", "failures": []}
    try:
        return json.loads(FAILURES_DB.read_text())
    except Exception:
        return {"version": "3.0", "failures": []}


def check_package_path(app_dir: Path, manifest: Path) -> list:
    """Check package name matches directory structure and files."""
    findings = []
    if not manifest.is_file():
        findings.append({"severity": "CRITICAL", "check": "package_path",
                         "finding": "AndroidManifest.xml missing"})
        return findings

    try:
        text = manifest.read_text()
        m = re.search(r'package="([^"]+)"', text)
        # AGP 8.x may use namespace in build.gradle instead
        package = None
        if m:
            package = m.group(1)
        else:
            build_file = app_dir / "build.gradle"
            if build_file.is_file():
                btext = build_file.read_text()
                nm = re.search(r"namespace\s*['\"]([^'\"]+)['\"]", btext)
                if nm:
                    package = nm.group(1)
                    findings.append({"severity": "INFO", "check": "package_path",
                                     "finding": f"namespace from build.gradle: {package}"})
        if not package:
            findings.append({"severity": "ERROR", "check": "package_path",
                             "finding": "manifest has no package attribute and build.gradle has no namespace"})
            return findings
        pkg_path = package.replace(".", "/")
        src_dir = app_dir / "src" / "main" / "java"
        if src_dir.is_dir():
            expected = src_dir / pkg_path
            if not expected.is_dir() and not any(src_dir.rglob("*.java")):
                findings.append({"severity": "WARNING", "check": "package_path",
                                 "finding": f"package {package} dir not found: {expected.relative_to(src_dir)}"})
        # Check java files declare correct package. Shared Fast Lane modules are
        # inlined under com.appfactory.modules.* and are freely usable by apps.
        app_pkg_prefix = package + "."
        module_pkg_prefix = "com.appfactory.modules."
        for java_file in src_dir.rglob("*.java"):
            content = java_file.read_text(errors="ignore")
            pkg_m = re.search(r'^package\s+([\w.]+);', content, re.MULTILINE)
            if pkg_m:
                p = pkg_m.group(1)
                if p != package and not p.startswith(app_pkg_prefix) and not p.startswith(module_pkg_prefix):
                    findings.append({"severity": "ERROR", "check": "package_path",
                                     "finding": f"{java_file.name} declares package {p}, expected {package} or {module_pkg_prefix}*"})
    except Exception as e:
        findings.append({"severity": "ERROR", "check": "package_path",
                         "finding": f"could not inspect manifest: {e}"})
    return findings


def check_manifest_xml(app_dir: Path, manifest: Path) -> list:
    """Validate manifest is well-formed XML."""
    findings = []
    if not manifest.is_file():
        findings.append({"severity": "CRITICAL", "check": "manifest_xml",
                         "finding": "AndroidManifest.xml missing"})
        return findings
    try:
        ET.parse(manifest)
        root = ET.parse(manifest).getroot()
        if root.tag != "manifest":
            findings.append({"severity": "ERROR", "check": "manifest_xml",
                             "finding": "root element is not <manifest>"})
        # Check launcher activity present
        text = manifest.read_text()
        if "MAIN" not in text or "LAUNCHER" not in text:
            findings.append({"severity": "ERROR", "check": "manifest_xml",
                             "finding": "no MAIN/LAUNCHER intent filter found"})
        # Check application element
        if root.find("application") is None:
            findings.append({"severity": "ERROR", "check": "manifest_xml",
                             "finding": "no <application> element"})
    except ET.ParseError as e:
        findings.append({"severity": "CRITICAL", "check": "manifest_xml",
                         "finding": f"manifest XML not well-formed: {e}"})
    return findings


def check_duplicate_classes(app_dir: Path) -> list:
    """Check for duplicate class names across the source tree."""
    findings = []
    class_locations = {}
    src_dir = app_dir / "src"
    if not src_dir.is_dir():
        return findings
    for java_file in src_dir.rglob("*.java"):
        content = java_file.read_text(errors="ignore")
        for m in re.finditer(r'(?:public\s+|final\s+)*class\s+(\w+)', content):
            class_name = m.group(1)
            if class_name in class_locations:
                findings.append({"severity": "ERROR", "check": "duplicate_classes",
                                 "finding": f"duplicate class {class_name}: "
                                            f"{class_locations[class_name]} and {java_file.relative_to(app_dir)}"})
            else:
                # Track first occurrence if it looks like a top-level declaration
                if m.start() < len(content) * 0.5:
                    class_locations[class_name] = str(java_file.relative_to(app_dir))
    return findings


def check_duplicate_resources(app_dir: Path) -> list:
    """Check for duplicate resource names across layout/values directories."""
    findings = []
    res_dir = app_dir / "src" / "main" / "res"
    if not res_dir.is_dir():
        return findings
    seen_names = {}
    for res_file in res_dir.rglob("*.xml"):
        try:
            root = ET.parse(res_file).getroot()
            for elem in root.iter():
                res_name = elem.get("{http://schemas.android.com/apk/res/android}name")
                if res_name:
                    key = f"{elem.tag}/{res_name}"
                    if key in seen_names:
                        findings.append({"severity": "ERROR", "check": "duplicate_resources",
                                         "finding": f"duplicate resource {key}: {seen_names[key]} and {res_file.relative_to(app_dir)}"})
                    else:
                        seen_names[key] = str(res_file.relative_to(app_dir))
        except ET.ParseError:
            findings.append({"severity": "ERROR", "check": "duplicate_resources",
                             "finding": f"resource XML not well-formed: {res_file.relative_to(app_dir)}"})
    return findings


def check_missing_references(app_dir: Path) -> list:
    """Check layout references (@+id/) are declared and Java code references exist in layouts."""
    findings = []
    res_dir = app_dir / "src" / "main" / "res"
    src_dir = app_dir / "src" / "main" / "java"
    if not res_dir.is_dir() or not src_dir.is_dir():
        return findings

    declared_ids = set()
    for layout_dir in [d for d in res_dir.iterdir() if d.is_dir() and "layout" in d.name]:
        for layout in layout_dir.rglob("*.xml"):
            try:
                root = ET.parse(layout).getroot()
                for elem in root.iter():
                    eid = elem.get("{http://schemas.android.com/apk/res/android}id")
                    if eid:
                        declared_ids.add(eid.replace("@+id/", "").replace("@id/", ""))
            except ET.ParseError:
                pass

    # Check Java references to view ids exist in layouts
    for java_file in src_dir.rglob("*.java"):
        content = java_file.read_text(errors="ignore")
        for m in re.finditer(r'R\.id\.(\w+)', content):
            ref_id = m.group(1)
            if ref_id not in declared_ids:
                # Might be referenced but defined elsewhere (view binding); only flag missing if also referenced as @+id elsewhere
                findings.append({"severity": "ERROR", "check": "missing_references",
                                 "finding": f"view id R.id.{ref_id} referenced in {java_file.name} but not found in any layout"})
    return findings


def check_gradle(app_dir: Path) -> list:
    """Check gradle build file for required structure."""
    findings = []
    build_file = app_dir / "build.gradle"
    if not build_file.is_file():
        findings.append({"severity": "CRITICAL", "check": "gradle",
                         "finding": "build.gradle missing"})
        return findings
    content = build_file.read_text()
    if "com.android.application" not in content:
        findings.append({"severity": "CRITICAL", "check": "gradle",
                         "finding": "android plugin not applied"})
    if "android {" not in content:
        findings.append({"severity": "ERROR", "check": "gradle",
                         "finding": "no android {} block"})
    if "compileSdk" not in content and "compileSdkVersion" not in content:
        findings.append({"severity": "ERROR", "check": "gradle",
                         "finding": "compileSdk not set"})
    if "applicationId" not in content:
        findings.append({"severity": "ERROR", "check": "gradle",
                         "finding": "applicationId not set"})
    if "minSdk" not in content and "minSdkVersion" not in content:
        findings.append({"severity": "WARNING", "check": "gradle",
                         "finding": "minSdk not set (defaults may be surprising)"})
    return findings


def check_java_errors(app_dir: Path) -> list:
    """Heuristic Java error check: obvious syntax problems.

    Deliberately conservative: only flags things that indicate real problems
    (brace imbalance outside strings/comments, bad trailing commas). Produces
    WARNING-level diagnostics for heuristic findings so preflight ``clean``
    only fails on actionable CRITICAL/ERROR findings.
    """
    findings = []
    src_dir = app_dir / "src"
    if not src_dir.is_dir():
        return findings

    def strip_literals(content: str) -> str:
        """Remove string/char literals and comments so brace counting is accurate."""
        out = []
        i = 0
        n = len(content)
        while i < n:
            c = content[i]
            nxt = content[i + 1] if i + 1 < n else ""
            if c == '"':
                i += 1
                while i < n:
                    if content[i] == "\\":
                        i += 2
                        continue
                    if content[i] == '"':
                        break
                    i += 1
            elif c == "'" and nxt != "'":
                i += 1
                while i < n:
                    if content[i] == "\\":
                        i += 2
                        continue
                    if content[i] == "'":
                        break
                    i += 1
            elif c == "/" and nxt == "/":
                while i < n and content[i] != "\n":
                    i += 1
            elif c == "/" and nxt == "*":
                i += 2
                while i < n - 1 and not (content[i] == "*" and content[i + 1] == "/"):
                    i += 1
                i += 2
                continue
            else:
                out.append(c)
            i += 1
        return "".join(out)

    for java_file in src_dir.rglob("*.java"):
        content = java_file.read_text(errors="ignore")
        clean = strip_literals(content)
        # Unbalanced braces outside strings/comments
        opens = clean.count("{")
        closes = clean.count("}")
        if opens != closes:
            findings.append({"severity": "ERROR", "check": "java_syntax",
                             "finding": f"brace imbalance ({{={opens}, }}={closes}) in {java_file.name}"})
        # Trailing commas in array initializers
        if re.search(r',\s*}', clean):
            findings.append({"severity": "WARNING", "check": "java_syntax",
                             "finding": f"possible trailing comma in initializer in {java_file.name}"})
    return findings


def run_preflight(app_dir: Path) -> dict:
    """Run all preflight checks."""
    manifest = app_dir / "src" / "main" / "AndroidManifest.xml"

    all_findings = []
    all_findings.extend(check_package_path(app_dir, manifest))
    all_findings.extend(check_manifest_xml(app_dir, manifest))
    all_findings.extend(check_duplicate_classes(app_dir))
    all_findings.extend(check_duplicate_resources(app_dir))
    all_findings.extend(check_missing_references(app_dir))
    all_findings.extend(check_gradle(app_dir))
    all_findings.extend(check_java_errors(app_dir))

    severity_order = {"CRITICAL": 0, "ERROR": 1, "WARNING": 2, "INFO": 3}
    all_findings.sort(key=lambda f: severity_order.get(f.get("severity", "WARNING"), 3))

    criticals = [f for f in all_findings if f["severity"] == "CRITICAL"]
    errors = [f for f in all_findings if f["severity"] in ("CRITICAL", "ERROR")]
    warnings = [f for f in all_findings if f["severity"] == "WARNING"]
    infos = [f for f in all_findings if f["severity"] == "INFO"]

    return {
        "app_dir": str(app_dir),
        "clean": len(errors) == 0,
        "severity_counts": {
            "critical": len(criticals),
            "error": len(errors),
            "warning": len(warnings),
            "info": len(infos),
        },
        "findings": all_findings,
    }


def predict_failures(app_dir: Path, build_log: str) -> dict:
    """Correlate build log against known failure signatures."""
    failures_db = load_failures_db()
    log_text = ""
    try:
        log_path = Path(build_log)
        if log_path.is_file():
            log_text = log_path.read_text(errors="ignore")
        else:
            log_text = build_log  # treat as raw text
    except Exception:
        log_text = build_log

    predictions = []
    for failure in failures_db.get("failures", []):
        signature = failure.get("signature", "")
        if signature and signature in log_text:
            predictions.append({
                "id": failure.get("id"),
                "category": failure.get("category"),
                "description": failure.get("description"),
                "patch": failure.get("patch"),
                "confidence": failure.get("confidence", 0.7),
                "matched": True,
            })
    if not predictions:
        predictions = [{
            "id": None,
            "category": "unknown",
            "description": "No known failure signature matched",
            "patch": None,
            "confidence": 0.0,
            "matched": False,
        }]

    return {
        "predictions": predictions,
        "matched": any(p["matched"] for p in predictions),
    }


def cmd_check(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: preflight.py check <app-dir>\n")
        return 2
    app_dir = Path(argv[2])
    if not app_dir.is_dir():
        sys.stderr.write(f"error: {app_dir} not found\n")
        return 1
    report = run_preflight(app_dir)
    print(json.dumps(report, indent=1))
    return 0 if report["clean"] else 1


def cmd_predict(argv):
    if len(argv) < 4:
        sys.stderr.write("usage: preflight.py predict <app-dir> <build-log>\n")
        return 2
    app_dir = Path(argv[2])
    build_log = argv[3]
    result = predict_failures(app_dir, build_log)
    print(json.dumps(result, indent=1))
    return 0


def main(argv):
    if len(argv) < 2 or argv[1] not in ("check", "predict"):
        sys.stderr.write("usage: preflight.py <check|predict> [args...]\n")
        return 2
    if argv[1] == "check":
        return cmd_check(argv)
    elif argv[1] == "predict":
        return cmd_predict(argv)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))