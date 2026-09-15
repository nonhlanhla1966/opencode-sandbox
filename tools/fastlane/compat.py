#!/usr/bin/env python3
"""compat.py — Fast Lane 3.0 Module Compatibility + Registry Engine.

Provides registry-driven module selection, versioned registry generation,
and per-module compatibility checking against project pins.

Usage:
  compat.py registry                    → generate modules/REGISTRY.json
  compat.py check <spec.json> <arch.json> → per-module compat report
  compat.py select <spec.json>          → registry-driven module selection

Exit 0 on success/pass, exit 1 on failure, exit 2 on usage error.
Output: JSON to stdout.
"""

import hashlib
import json
import sys
from pathlib import Path

MODULES_DIR = Path(__file__).resolve().parent / ".." / ".." / "modules"

# Project pins (FL3 reproducible build requirements)
PROJECT_PINS = {
    "jdk": "17",
    "gradle": "8.7",
    "agp": "8.5.2",
    "kotlin": "n/a",
    "compile_sdk": 34,
    "min_sdk": 21,
    "target_sdk": 34,
}


def sha256_file(path: Path) -> str:
    """Compute sha256 of a file."""
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def load_module_meta(module_dir: Path) -> dict:
    """Load module.json from a module directory, with metadata enrichment."""
    mj = module_dir / "module.json"
    if not mj.is_file():
        return None
    try:
        meta = json.loads(mj.read_text())
    except Exception:
        return None

    # Compute checksums of source and test files
    src_files = list(module_dir.rglob("*.java"))
    test_files = list(module_dir.rglob("*Test.java"))
    src_checksum = hashlib.sha256()
    for f in sorted(src_files):
        src_checksum.update(f.read_bytes())
    test_checksum = hashlib.sha256()
    for f in sorted(test_files):
        test_checksum.update(f.read_bytes())

    meta["checksums"] = {
        "source": src_checksum.hexdigest() if src_files else None,
        "tests": test_checksum.hexdigest() if test_files else None,
    }
    meta["file_counts"] = {
        "source": len(src_files),
        "tests": len(test_files),
    }
    meta["compatibility"] = {
        "jdk": PROJECT_PINS["jdk"],
        "gradle": PROJECT_PINS["gradle"],
        "agp": PROJECT_PINS["agp"],
        "compile_sdk": PROJECT_PINS["compile_sdk"],
        "min_sdk": PROJECT_PINS["min_sdk"],
    }
    return meta


def generate_registry() -> dict:
    """Scan modules/ and build versioned REGISTRY.json."""
    registry = {
        "version": "3.0",
        "generated_by": "fastlane-compat-registry",
        "project_pins": PROJECT_PINS,
        "modules": {},
    }
    if not MODULES_DIR.is_dir():
        return registry
    for d in sorted(MODULES_DIR.iterdir()):
        if not d.is_dir():
            continue
        meta = load_module_meta(d)
        if meta and "id" in meta:
            registry["modules"][meta["id"]] = meta
    return registry


def check_compatibility(spec: dict, arch: dict) -> dict:
    """Check each module in arch against project pins."""
    modules = arch.get("modules", [])
    module_compat = arch.get("module_compat", {})
    results = []

    for mod_id in modules:
        compat_info = module_compat.get(mod_id, {})
        min_api = compat_info.get("api", 21)
        level = compat_info.get("level", "UNKNOWN")

        ok = True
        reasons = []

        # Check min API compatibility
        if min_api > PROJECT_PINS["compile_sdk"]:
            ok = False
            reasons.append(f"min_api {min_api} > compile_sdk {PROJECT_PINS['compile_sdk']}")
        if min_api > PROJECT_PINS["min_sdk"]:
            # Module requires higher API than project min — warning but not failure
            reasons.append(f"min_api {min_api} > project min_sdk {PROJECT_PINS['min_sdk']} (may need minSdk adjustment)")

        # Check module exists in registry
        registry_path = MODULES_DIR / "REGISTRY.json"
        if registry_path.is_file():
            registry = json.loads(registry_path.read_text())
            if mod_id not in registry.get("modules", {}):
                ok = False
                reasons.append(f"module {mod_id} not in REGISTRY.json")
            else:
                reg_mod = registry["modules"][mod_id]
                # Check for known limitations
                limitations = reg_mod.get("limitations", [])
                for lim in limitations:
                    reasons.append(f"limitation: {lim}")

        results.append({
            "module": mod_id,
            "ok": ok,
            "reasons": reasons if reasons else ["compatible"],
            "level": level,
            "min_api": min_api,
        })

    all_ok = all(r["ok"] for r in results)
    return {
        "all_compatible": all_ok,
        "modules": results,
        "project_pins": PROJECT_PINS,
    }


def registry_select(spec: dict) -> dict:
    """Registry-driven module selection based on spec requirements."""
    spec_modules = set(spec.get("modules", []))
    features = [f.lower() for f in spec.get("features", [])]
    storage = [s.lower() for s in spec.get("storage", [])]
    apis = [a.lower() for a in spec.get("apis", [])]

    # Always include core modules
    core = {"json", "text", "validation", "time"}
    selected = set(core)

    # Registry mapping: feature → required modules
    feature_module_map = {
        "sqlite": ["storage-sqlite"],
        "offline": ["storage-sqlite"],
        "notes": ["storage-sqlite"],
        "todo": ["storage-sqlite"],
        "habit": ["storage-sqlite"],
        "journal": ["storage-sqlite"],
        "inventory": ["storage-sqlite"],
        "expense": ["storage-sqlite"],
        "budget": ["storage-sqlite"],
        "chat": ["http-rest"],
        "messaging": ["http-rest"],
        "assistant": ["http-rest"],
        "api": ["http-rest"],
        "weather": ["http-rest"],
        "notification": ["notifications"],
        "remind": ["notifications"],
        "camera": ["media-image"],
        "photo": ["media-image"],
        "gallery": ["media-image"],
        "scan": ["media-image"],
        "settings": ["settings"],
        "preference": ["settings"],
        "encrypt": ["crypto"],
        "secure": ["crypto"],
        "vault": ["crypto"],
        "password": ["crypto"],
    }

    for feature in features:
        for keyword, mods in feature_module_map.items():
            if keyword in feature:
                selected.update(mods)

    for s in storage:
        if "sqlite" in s:
            selected.add("storage-sqlite")
    for a in apis:
        if "rest" in a or "openai" in a or "generic" in a:
            selected.add("http-rest")

    # Add spec-declared modules
    selected.update(spec_modules)

    # Always include settings if not present
    if "settings" not in selected:
        selected.add("settings")

    return {
        "selected": sorted(selected),
        "core": sorted(core),
        "feature_derived": sorted(selected - core - spec_modules),
        "spec_declared": sorted(spec_modules),
    }


def cmd_registry(argv):
    registry = generate_registry()
    out_path = MODULES_DIR / "REGISTRY.json"
    out_path.write_text(json.dumps(registry, indent=1, sort_keys=True))
    print(json.dumps({"status": "ok", "modules_count": len(registry["modules"]),
                       "output": str(out_path)}))
    return 0


def cmd_check(argv):
    if len(argv) < 4:
        sys.stderr.write("usage: compat.py check <spec.json> <arch.json>\n")
        return 2
    spec = json.loads(Path(argv[2]).read_text())
    arch = json.loads(Path(argv[3]).read_text())
    result = check_compatibility(spec, arch)
    print(json.dumps(result, indent=1))
    return 0 if result["all_compatible"] else 1


def cmd_select(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: compat.py select <spec.json>\n")
        return 2
    spec = json.loads(Path(argv[2]).read_text())
    result = registry_select(spec)
    print(json.dumps(result, indent=1))
    return 0


def main(argv):
    if len(argv) < 2 or argv[1] not in ("registry", "check", "select"):
        sys.stderr.write("usage: compat.py <registry|check|select> [args...]\n")
        return 2
    cmd = argv[1]
    if cmd == "registry":
        return cmd_registry(argv)
    elif cmd == "check":
        return cmd_check(argv)
    elif cmd == "select":
        return cmd_select(argv)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
