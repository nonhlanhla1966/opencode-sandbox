#!/usr/bin/env python3
"""plan.py — Fast Lane AI Planning & Architecture (2. AI PLANNING).

Deterministic, structured planning pipeline. Consumes app-spec.json (from
analyze.py) and produces:
  * architecture.json — machine-readable architecture (modules, layers, db
    schema, apis, security, permissions, ui, testing, release plan)
  * PLAN.md          — human-readable plan the agent follows

The opencode engine enriches this plan while the agent codes the app; the
machine-readable plan is the guardrail that keeps generation structured.

Usage:
  plan.py <app-spec.json> [--out <dir>]
  (writes architecture.json and PLAN.md into <dir>; defaults to cwd)
"""

import json
import sys
from pathlib import Path

MODULE_COMPAT = {
    "storage-sqlite":      {"level": "OFFLINE", "test": "unit+db",  "api": 21},
    "settings":            {"level": "OFFLINE", "test": "unit",     "api": 21},
    "notifications":       {"level": "OFFLINE", "test": "unit",     "api": 26},
    "http-rest":           {"level": "ONLINE",  "test": "unit+api", "api": 21},
    "json":                {"level": "OFFLINE", "test": "unit",     "api": 21},
    "crypto":              {"level": "OFFLINE", "test": "unit",     "api": 23},
    "validation":          {"level": "OFFLINE", "test": "unit",     "api": 21},
    "text":                {"level": "OFFLINE", "test": "unit",     "api": 21},
    "time":                {"level": "OFFLINE", "test": "unit",     "api": 21},
    "network":             {"level": "ONLINE",  "test": "unit",     "api": 21},
    "retry":               {"level": "ONLINE",  "test": "unit",     "api": 21},
    "media-image":         {"level": "DEVICE",  "test": "manual/ui","api": 21},
    "storage-file":        {"level": "OFFLINE", "test": "unit",     "api": 21},
    "themegen":            {"level": "OFFLINE", "test": "unit",     "api": 21},
}


def plan(spec: dict) -> dict:
    perm_notes = {
        "android.permission.CAMERA": "request at runtime for API>=23; deny-safe UI",
        "android.permission.INTERNET": "no runtime request; add to manifest only",
        "android.permission.ACCESS_FINE_LOCATION": "runtime request; show rationale",
        "android.permission.POST_NOTIFICATIONS": "runtime request on API>=33",
    }
    modules = list(spec.get("modules", []))
    # always include the small shared cores that generated skeletons rely on
    for m in ("json", "text", "validation", "time", "settings"):
        if m not in modules:
            modules.append(m)

    db_entities = spec.get("data", {}).get("entities", [])
    return {
        "name": spec.get("name", "app"),
        "application_id": spec.get("application_id"),
        "layers": ["ui", "logic", "persistence", "network"],
        "archetype": spec.get("archetype", "display"),
        "modules": modules,
        "module_compat": {m: MODULE_COMPAT.get(m, {}) for m in modules},
        "database": {
            "engine": "sqlite" if "storage-sqlite" in modules else "flat-file/prefs",
            "entities": db_entities,
            "versioned_migrations": "storage-sqlite" in modules,
        },
        "apis": [
            {
                "name": a,
                "transport": "https",
                "tls_bypass": False,
                "auth": spec.get("auth", "none"),
            }
            for a in spec.get("apis", [])
        ],
        "security": {
            "tls": "always-on certificate validation",
            "api_keys": "never hardcoded; user-supplied via Settings, stored encrypted",
            "logging": "no secrets in logs",
            "exported_components": "only launcher exported",
            "allow_backup": False,
        },
        "permissions": [
            {"permission": p, "note": perm_notes.get(p, "declared on demand")}
            for p in spec.get("permissions", [])
        ],
        "ui": {
            "screens": spec.get("screens", ["Home"]),
            "nav": spec.get("navigation", "back-stack"),
            "theme": "Material-ish, dark-mode aware (system), system fonts",
            "min_sdk": 21,
        },
        "testing": {
            "unit": True,
            "database": "storage-sqlite" in modules,
            "api": any(a in ("openai-compatible", "generic rest") for a in spec.get("apis", [])),
            "ui_smoke": True,
            "none": False,
        },
        "release": {
            "signing": "CI debug-signed APK (installable, unsigned=false)",
            "verify": ["compile", "unit tests", "lint", "resource/XML", "manifest",
                       "security scan", "apk badging", "apksigner verify", "sha256"],
            "artifact": "debug APK",
        },
        "reproducible": {
            "jdk": "17 (Temurin)",
            "gradle": "8.7 wrapper (or later pinned)",
            "agp": "8.5.2",
            "kotlin": "n/a (Java-first)",
            "sdk": "compileSdk 34",
        },
    }


def render_plan_md(spec: dict, arch: dict) -> str:
    w = []
    w.append(f"# PLAN — {spec.get('name')} (`{spec.get('slug')}`)")
    w.append("")
    w.append("Structured plan produced by Fast Lane planning pipeline "
             "(deterministic architecture.json). The agent implements against "
             "this plan; deviations must be additive, never contradictory.")
    w.append("")
    w.append("## Complexity")
    cx = spec.get("complexity", {})
    w.append(f"- **{cx.get('level')}** (score {cx.get('score')}/100) — {', '.join(cx.get('factors', []))}")
    est = spec.get("estimate", {})
    w.append(f"- Estimated build time (warm cache): ~{est.get('build_time_seconds')}s "
             f"({est.get('band')})")
    w.append("")
    w.append("## Screens & navigation")
    for s in spec.get("screens", []):
        w.append(f"- {s}")
    w.append(f"- Navigation: {spec.get('navigation')}")
    w.append("")
    w.append("## Modules (verified reusable library)")
    arch_m = arch.get("modules", [])
    w.append("| Module | Level | Tested | minApi |")
    w.append("|--------|-------|--------|--------|")
    for m in arch_m:
        c = arch.get("module_compat", {}).get(m, {})
        w.append(f"| {m} | {c.get('level','-')} | {c.get('test','-')} | {c.get('api','-')} |")
    w.append("")
    w.append("## Database / storage")
    db = arch.get("database", {})
    w.append(f"- Engine: {db.get('engine')}")
    for e in db.get("entities", []):
        w.append(f"- Entity `{e.get('name')}` fields: {e.get('fields')}")
    w.append("")
    w.append("## APIs")
    for a in arch.get("apis", []):
        w.append(f"- `{a.get('name')}` over HTTP(S), no TLS bypass, auth={a.get('auth')}")
    w.append("")
    w.append("## Permissions")
    for p in arch.get("permissions", []):
        w.append(f"- `{p.get('permission')}` — {p.get('note')}")
    w.append("")
    w.append("## Security")
    for k, v in arch.get("security", {}).items():
        w.append(f"- {k}: {v}")
    w.append("")
    w.append("## Testing plan")
    for t, on in arch.get("testing", {}).items():
        if on and t != "none":
            w.append(f"- {t}")
    w.append("")
    w.append("## Release plan")
    for v in arch.get("release", {}).get("verify", []):
        w.append(f"- [ ] quality gate: {v}")
    w.append("- [ ] SHA-256 checksum published with release")
    w.append("")
    w.append("## Reproducibility pins")
    for k, v in arch.get("reproducible", {}).items():
        w.append(f"- {k}: {v}")
    w.append("")
    w.append("_Generated by fastlane-plan; agent-approved before coding._")
    w.append("")
    return "\n".join(w)


def main(argv):
    spec_file = argv[1] if len(argv) > 1 else None
    out_dir = Path(argv[argv.index("--out") + 1]) if "--out" in argv else Path.cwd()
    if not spec_file:
        sys.stderr.write("usage: plan.py <app-spec.json> [--out <dir>]\n")
        return 2
    spec = json.loads(Path(spec_file).read_text())
    arch = plan(spec)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "architecture.json").write_text(json.dumps(arch, indent=1, sort_keys=True))
    (out_dir / "PLAN.md").write_text(render_plan_md(spec, arch))
    print(json.dumps(arch, indent=1, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))