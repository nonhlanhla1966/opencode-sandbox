"""File / document understanding for AppFactory Assistant.

- Extracts readable text from a curated set of text-based formats plus safe
  text layers from common document formats (PDF, DOCX) using only the Python
  standard library.
- Secrets are redacted before the excerpt leaves the file boundary.
- Unsupported or binary files return `ok: false` with a reason — the assistant
  never claims to understand a file it cannot read. PDFs without an extractable
  text layer report exactly that (scanned/image-only PDFs need OCR).
- Excerpts are size-limited, so large documents never blow the context/logs.
"""

from __future__ import annotations

import io
import re
import zipfile
from pathlib import Path

from common import read_json, sanitize_secrets

TEXT_EXTENSIONS = {
    ".txt", ".md", ".json", ".jsonl", ".csv", ".tsv", ".html", ".htm", ".xml",
    ".yaml", ".yml", ".toml", ".ini", ".sh", ".py", ".java", ".kt", ".js",
    ".ts", ".css", ".log",
}
DOCUMENT_EXTENSIONS = {".pdf", ".docx"}
SUPPORTED_EXTENSIONS = TEXT_EXTENSIONS | DOCUMENT_EXTENSIONS
MAX_EXCERPT_CHARS = 8000
MIN_TEXT_RATIO = 0.5
MIN_PDF_TEXT_CHARS = 8


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
    raw = p.read_bytes()

    if ext == ".pdf":
        return _extract_pdf(p, raw, max_chars)
    if ext == ".docx":
        return _extract_docx(p, raw, max_chars)

    if ext not in TEXT_EXTENSIONS:
        # attempt a light sniff for UTF-8 text even for unknown extensions
        if not _looks_like_bytes(raw):
            return {
                "ok": False,
                "command": "file_analysis",
                "error": f"unsupported or binary format '{ext}'; supported text formats: {sorted(SUPPORTED_EXTENSIONS)}",
            }

    if not _looks_like_bytes(raw):
        return {"ok": False, "command": "file_analysis", "error": "binary content cannot be summarized as text"}

    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        text = raw.decode("utf-8", errors="replace")

    return _package(p, raw, ext, text, max_chars)


def _package(p: Path, raw: bytes, ext: str, text: str, max_chars: int, note: str | None = None) -> dict:
    original = text.strip()
    redacted = sanitize_secrets(original)
    excerpt = redacted[:max_chars]
    result = {
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
    if note:
        result["note"] = note
    return result


def _extract_pdf(p: Path, raw: bytes, max_chars: int) -> dict:
    """Minimal pure-stdlib text-layer extraction for PDFs.

    Pulls text shown by `(...) Tj` / `[...] TJ` operators. If the document is a
    scanned/image-only PDF there is no text layer and we say so honestly — we do
    NOT emit a fake summary; OCR is the job of a configured vision provider.
    """
    text = _pdf_text_layer(raw)
    if len(text.strip()) < MIN_PDF_TEXT_CHARS:
        return {
            "ok": False,
            "command": "file_analysis",
            "name": p.name,
            "bytes": len(raw),
            "format": "pdf",
            "error": (
                "PDF contains no extractable text layer (scanned or image-only). "
                "The page contents cannot be read without OCR, which needs a configured vision provider."
            ),
        }
    return _package(
        p,
        raw,
        ".pdf",
        text,
        max_chars,
        note="text extracted from the PDF text layer; scanned pages without a text layer are not summarized",
    )


def _pdf_text_layer(raw: bytes) -> str:
    try:
        body = raw.decode("latin-1")
    except (UnicodeDecodeError, ValueError):
        return ""
    if not re.search(r"/Type\s*/Page", body):
        return ""
    # content streams live between `stream` and `endstream`
    chunks = re.findall(r"stream\r?\n(.*?)\r?\nendstream", body, re.S)
    shown: list[str] = []
    for chunk in chunks:
        shown.extend(re.findall(r"\(((?:[^()\\]|\\.)*)\)\s*Tj", chunk))
        for arr in re.findall(r"\[(.*?)\]\s*TJ", chunk, re.S):
            shown.extend(re.findall(r"\(((?:[^()\\]|\\.)*)\)", arr))
    parts = [_unescape_pdf(t) for t in shown]
    text = "".join(parts)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def _unescape_pdf(t: str) -> str:
    return (
        t.replace(r"\(", "(")
        .replace(r"\)", ")")
        .replace(r"\\", "\\")
        .replace(r"\n", " ")
        .replace(r"\r", " ")
        .replace(r"\t", " ")
    )


def _extract_docx(p: Path, raw: bytes, max_chars: int) -> dict:
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            xml = z.read("word/document.xml")
    except (zipfile.BadZipFile, KeyError, OSError) as e:
        return {
            "ok": False,
            "command": "file_analysis",
            "name": p.name,
            "bytes": len(raw),
            "format": "docx",
            "error": f"docx could not be unpacked: {e}",
        }
    text = xml.decode("utf-8", errors="replace")
    text = re.sub(r"<w:p\b[^>]*>", "\n", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\n{2,}", "\n", text).strip()
    if len(text.strip()) < 1:
        return {
            "ok": False,
            "command": "file_analysis",
            "name": p.name,
            "bytes": len(raw),
            "format": "docx",
            "error": "docx contains no readable paragraph text",
        }
    return _package(p, raw, ".docx", text, max_chars)


def _looks_like_bytes(raw: bytes) -> bool:
    sample = raw[:4096]
    if not sample:
        return True
    if b"\x00" in sample:
        return False
    printable = sum(1 for b in sample if 9 <= b <= 13 or 32 <= b <= 126)
    return printable / len(sample) >= MIN_TEXT_RATIO