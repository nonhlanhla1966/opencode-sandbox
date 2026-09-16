"""File / document understanding for AppFactory Assistant.

- Extracts readable text from a curated set of text-based formats.
- Secrets are redacted before the excerpt leaves the file boundary.
- Unsupported or binary files return `ok: false` with a reason — the assistant
  never claims to understand a file it cannot read.
- Excerpts are size-limited, so large documents never blow the context/logs.
"""

from __future__ import annotations

from pathlib import Path

from common import read_json, sanitize_secrets

TEXT_EXTENSIONS = {
    ".txt", ".md", ".json", ".jsonl", ".csv", ".tsv", ".html", ".htm", ".xml",
    ".yaml", ".yml", ".toml", ".ini", ".sh", ".py", ".java", ".kt", ".js",
    ".ts", ".css", ".log",
}
MAX_EXCERPT_CHARS = 8000
MIN_TEXT_RATIO = 0.5


def extract(path: str, max_chars: int = MAX_EXCERPT_CHARS) -> dict:
    try:
        max_chars = int(max_chars)
    except (TypeError, ValueError):
        max_chars = MAX_EXCERPT_CHARS
    max_chars = max(200, min(max_chars, 50000))

    p = Path(path)
    if not p.is_file():
        return {"ok": False, "command": "file_analysis", "error": f"file not found: {path}"}
    if p.stat().st_size == 0:
        return {"ok": False, "command": "file_analysis", "error": "file is empty"}

    ext = p.suffix.lower()
    if ext not in TEXT_EXTENSIONS:
        # attempt a light sniff for UTF-8 text even for unknown extensions
        if not _looks_like_text(p):
            return {
                "ok": False,
                "command": "file_analysis",
                "error": f"unsupported or binary format '{ext}'; supported text formats: {sorted(TEXT_EXTENSIONS)}",
            }

    raw = p.read_bytes()
    if not _looks_like_text(p):
        return {"ok": False, "command": "file_analysis", "error": "binary content cannot be summarized as text"}

    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("utf-8", errors="replace")

    original = text.strip()
    redacted = sanitize_secrets(original)
    excerpt = redacted[:max_chars]
    return {
        "ok": True,
        "command": "file_analysis",
        "name": p.name,
        "bytes": len(raw),
        "format": ext.lstrip(".") or "text",
        "chars_read": len(original),
        "excerpt_chars": len(excerpt),
        "redacted": redacted != original,
        "excerpt": excerpt,
    }


def _looks_like_text(p: Path) -> bool:
    sample = p.read_bytes()[:4096]
    if not sample:
        return True
    if b"\x00" in sample:
        return False
    printable = sum(1 for b in sample if 9 <= b <= 13 or 32 <= b <= 126)
    return printable / len(sample) >= MIN_TEXT_RATIO