"""Common helpers for the AppFactory AI Assistant layer.

Fast Lane golden rules are inherited:
- deterministic CLIs that emit JSON
- secrets live only in the environment, never on stdout/stderr/logs
- TLS validation always on, HTTPS only
- never fake an external capability that is actually unavailable
"""

import json
import os
import re
import sys
import time
from pathlib import Path

ASSISTANT_DIR = Path(__file__).resolve().parent
REPO_ROOT = ASSISTANT_DIR.parents[1]

STATE_DIR = Path(
    os.environ.get("APPFACTORY_ASSISTANT_DATA") or (REPO_ROOT / ".fastlane" / "assistant")
)
CHAT_DIR = STATE_DIR / "chat"
BUILD_DIR = STATE_DIR / "builds"


def utc_now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def ensure_dirs() -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    CHAT_DIR.mkdir(parents=True, exist_ok=True)
    BUILD_DIR.mkdir(parents=True, exist_ok=True)


def write_json(path: Path, obj: dict) -> None:
    ensure_dirs()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(
        json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    tmp.replace(path)


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {}
    except (ValueError, OSError):
        return {}


def out(obj) -> None:
    sys.stdout.write(json.dumps(obj, sort_keys=True, ensure_ascii=False) + "\n")


def fail(msg: str, code: int = 1) -> None:
    out({"ok": False, "error": msg})
    sys.exit(code)


def sanitize_secrets(text: str) -> str:
    """Redact anything that looks like a credential before it reaches logs/output."""
    patterns = [
        (r"(?i)authorization\s*[:=]\s*\S+", "authorization=<redacted>"),
        (r"sk-[A-Za-z0-9\-_]{8,}", "sk-<redacted>"),
        (r"ghp_[A-Za-z0-9]{20,}", "ghp_<redacted>"),
        (r"ghs_[A-Za-z0-9]{20,}", "ghs_<redacted>"),
        (r"ghu_[A-Za-z0-9]{20,}", "ghu_<redacted>"),
        (r"AKIA[0-9A-Z]{16}", "AKIA<redacted>"),
        (r"(?i)bearer\s+[A-Za-z0-9._\-]+", "bearer <redacted>"),
        (r"(?i)api[_-]?key\s*[:=]\s*[A-Za-z0-9._\-]{8,}", "api-key=<redacted>"),
        (r"(?i)x-api-key\s*[:=]\s*\S+", "x-api-key=<redacted>"),
    ]
    for pat, repl in patterns:
        text = re.sub(pat, repl, text)
    return text