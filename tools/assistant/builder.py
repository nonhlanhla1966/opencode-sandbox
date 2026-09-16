"""AI -> App Builder bridge for AppFactory Assistant.

Turns a plain-language app request (or a request to change an existing app)
into the deterministic Fast Lane artifacts by driving the existing FL CLIs:
  analyze -> spec  -> plan -> architecture  -> scaffold -> real project
Then hands off to the AppFactory release pipeline (GitHub Actions / cloud),
exactly like the `/oc` flow in `.github/workflows/opencode.yml`.

Guarantees:
- existing apps are never deleted; modifications are additive and logged
- history of every build/change is preserved under `<state>/builds/<slug>`
- generation is deterministic and never hand-written
- no Android build tooling is required locally (release stays in the cloud)
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "fastlane"))
import spec_validate  # noqa: E402

import policy  # noqa: E402
from common import BUILD_DIR, REPO_ROOT, ensure_dirs, out  # noqa: E402

FASTLANE = REPO_ROOT / "tools" / "fastlane"
APPS_DIR = REPO_ROOT / "apps"


def _run(cmd: list[str], cwd: Path | None = None) -> str:
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=str(cwd or REPO_ROOT))
    if r.returncode != 0:
        raise RuntimeError((r.stderr or r.stdout or "command failed").strip()[:3000])
    return r.stdout.strip()


def analyze_idea(idea: str) -> dict:
    raw = _run([sys.executable, str(FASTLANE / "analyze.py"), idea])
    return json.loads(raw)


def plan_spec(spec: dict, outdir: Path, outfile: str = "architecture.json") -> dict:
    outdir.mkdir(parents=True, exist_ok=True)
    spec_path = outdir / "spec.json"
    spec_path.write_text(json.dumps(spec, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    arch_raw = _run([sys.executable, str(FASTLANE / "plan.py"), str(spec_path), "--out", str(outdir)])
    arch = json.loads(arch_raw) if arch_raw else json.loads((outdir / outfile).read_text(encoding="utf-8"))
    (outdir / outfile).write_text(json.dumps(arch, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return arch


def scaffold_app(spec: dict, arch: dict, parent_dir) -> Path:
    parent_dir = Path(parent_dir)
    spec_path = parent_dir / "spec.json"
    spec_path.write_text(json.dumps(spec, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    arch_path = parent_dir / "architecture.json"
    arch_path.write_text(json.dumps(arch, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    _run([sys.executable, str(FASTLANE / "scaffold.py"), str(spec_path), str(arch_path), str(parent_dir)])
    return parent_dir / spec["slug"]


def _checksum(spec: dict) -> str:
    return spec_validate.compute_checksum(spec)


def _union_keep_order(a: list, b: list) -> list:
    seen = set()
    merged = []
    for item in list(a) + list(b):
        key = str(item).strip().lower()
        if key and key not in seen:
            seen.add(key)
            merged.append(item)
    return merged


def _load_existing_spec(slug: str) -> dict | None:
    p = APPS_DIR / slug / "app-spec.json"
    if p.is_file():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            return None
    return None


def build_app(
    idea: str,
    release: bool = False,
    modify: str | None = None,
    staging: str | None = None,
) -> dict:
    check = policy.check(idea)
    if not check["allowed"]:
        return {"ok": False, "command": "build", "policy": "refused", "reason": check["reason"]}

    ensure_dirs()
    slug = modify or ""
    released_idea = idea

    if modify:
        existing = _load_existing_spec(modify)
        if existing is None:
            return {
                "ok": False,
                "command": "build",
                "modify": modify,
                "error": f"existing app '{modify}' has no readable app-spec.json",
            }
        new = analyze_idea(idea)
        merged = dict(existing)
        merged["features"] = _union_keep_order(existing.get("features", []), new.get("features", []))
        merged["screens"] = _union_keep_order(existing.get("screens", []), new.get("screens", []))
        merged["modules"] = _union_keep_order(existing.get("modules", []), new.get("modules", []))
        merged.setdefault("changes", []).append(
            {
                "ts": new.get("checksum_sha256", ""),
                "request": idea[:4000],
                "added_features": [f for f in new.get("features", []) if str(f).strip().lower() not in {str(e).strip().lower() for e in existing.get("features", [])}],
            }
        )
        merged["change_request"] = idea[:4000]
        merged["checksum_sha256"] = _checksum(merged)
        spec = merged
        slug = modify
        released_idea = f"existing app '{modify}': {idea}"
    else:
        spec = analyze_idea(idea)
        slug = spec["slug"]

    workroot = Path(staging) if staging else BUILD_DIR / slug
    run_dir = workroot / f"run-{len(list(workroot.glob('run-*')))}"
    run_dir.mkdir(parents=True, exist_ok=True)
    if staging:
        staging = Path(staging)

    # deterministic spec artifact
    spec_path = run_dir / "app-spec.json"
    spec_path.write_text(json.dumps(spec, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    # plan -> architecture + PLAN.md
    arch = plan_spec(spec, run_dir)

    # scaffold a real buildable project (staging or into apps/ for the real factory)
    app_dir: Path | None = None
    if staging:
        app_dir = scaffold_app(spec, arch, staging)
    else:
        app_dir = scaffold_app(spec, arch, APPS_DIR)

    history = run_dir / "history.jsonl"
    record = {
        "ts": spec.get("checksum_sha256", "")[:12],
        "idea": idea[:4000],
        "modify": modify or None,
        "slug": slug,
        "spec": str(spec_path),
        "architecture": str(run_dir / "architecture.json"),
        "app_dir": str(app_dir),
        "checksum_sha256": spec.get("checksum_sha256", ""),
    }
    history.write_text(json.dumps(record, sort_keys=True) + "\n", encoding="utf-8")

    result = {
        "ok": True,
        "command": "build",
        "mode": "modify" if modify else "create",
        "slug": slug,
        "app_dir": str(app_dir),
        "spec": str(spec_path),
        "architecture": str(run_dir / "architecture.json"),
        "checksum_sha256": spec.get("checksum_sha256", ""),
        "spec_version": spec.get("spec_version", "3.0"),
        "app_dir_exists": app_dir.is_dir() if app_dir else False,
        "release": False,
        "note": "deterministic FL artifacts generated; APK building/publishing stays in the AppFactory CI/cloud pipeline",
    }

    if release and not staging:
        release_result = _trigger_cloud_release(slug)
        result["release"] = True
        result.update(release_result)

    return result


def _trigger_cloud_release(slug: str) -> dict:
    """Commit generated app sources and trigger the cloud build workflow.

    Mirrors the `/oc` flow. Silent (no-op-ish) when no service backend exists.
    """
    gh = shutil.which("gh")
    try:
        git = subprocess.run(["git", "-C", str(REPO_ROOT), "status", "--porcelain"], capture_output=True, text=True)
        dirty = bool(git.stdout.strip())
    except Exception:
        dirty = False
    if not dirty:
        return {"release": True, "cloud": "no_changes", "workflow": None}
    if not gh:
        return {"release": True, "cloud": "pending_commit_no_gh", "workflow": None}
    try:
        subprocess.run(["git", "-C", str(REPO_ROOT), "add", str(APPS_DIR / slug)], check=True, capture_output=True)
        subprocess.run(
            ["git", "-C", str(REPO_ROOT), "commit", "-m", f"feat(app): {slug} (AppFactory Assistant build)"],
            check=True,
            capture_output=True,
        )
        subprocess.run(["git", "-C", str(REPO_ROOT), "push"], check=True, capture_output=True)
        run = subprocess.run(
            ["gh", "workflow", "run", "build.yml", "--repo", "nonhlanhla1966/opencode-sandbox", "--ref", "main", "-f", f"app={slug}"],
            capture_output=True,
            text=True,
        )
        if run.returncode != 0:
            return {"release": True, "cloud": "workflow_trigger_failed", "stderr": (run.stderr or "")[:400]}
        return {"release": True, "cloud": "triggered", "workflow": "build.yml"}
    except subprocess.CalledProcessError as e:
        return {"release": True, "cloud": "git_failed", "stderr": (e.stderr or str(e))[:400]}
    except Exception as e:  # noqa: BLE001
        return {"release": True, "cloud": "error", "stderr": str(e)[:400]}