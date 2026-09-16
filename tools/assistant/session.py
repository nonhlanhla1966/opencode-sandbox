"""ChatGPT-like session orchestration for AppFactory Assistant.

A `session` turn routes text + attachments through the complete AI tool router
(CHAT, VISION, WEB, IMAGE, FILE, DATA, APP_BUILDER, APP_MODIFIER) and appends a
single structured assistant turn with the tool outcomes, so one conversation can
flow back and forth between chat, analysis, search, and app building.

Rules:
- Every turn passes the content policy gate first.
- Tool outcomes are rendered verbatim (JSON-derived) — nothing is fabricated.
- Capabilities that require an unavailable provider report their honest
  decline (mock mode) instead of pretending success.
- The conversation history is never reset; follow-ups reuse it.
"""

from __future__ import annotations

import re
import uuid

import policy
import router
from common import STATE_DIR, ensure_dirs, sanitize_secrets

_GENERATED_DIR = STATE_DIR / "generated"


def _rendered(capability: str, result: dict) -> str:
    """Deterministic single-line-ish human rendering of a tool result."""
    prefix = f"[{capability}] "
    if isinstance(result, dict) and result.get("ok") is False:
        return prefix + "unavailable: " + str(result.get("error") or result.get("reason") or "command failed")[:500]
    if capability == "CHAT":
        text = result.get("assistant", "")
        return "CHAT: " + sanitize_secrets(text)[:4000] if text else prefix + "no reply"
    if capability == "VISION":
        if result.get("verified"):
            return prefix + "verified: " + sanitize_secrets(str(result.get("caption") or ""))[:2500]
        return prefix + "unverified — " + sanitize_secrets(str(result.get("note") or result.get("caption") or ""))[:1200]
    if capability == "WEB":
        results = result.get("results", [])
        lines = []
        for r in results[:10]:
            lines.append(f"- {r.get('title', '')} ({r.get('source', '')}): {r.get('url', '')}")
        head = "\n".join(lines) or "(no results)"
        return prefix + f"{len(results)} source-aware result(s):\n" + head[:3000]
    if capability == "IMAGE":
        if result.get("generated"):
            return prefix + "generated image written to " + str(result.get("path", ""))
        return prefix + "image generation unavailable in this mode (nothing was faked)"
    if capability == "FILE":
        ex = str(result.get("excerpt", ""))
        note = str(result.get("note", "") or "")
        return prefix + f"{len(ex)} chars extracted from {result.get('name', '')}" + (f";\\n{ex[:1200]}" if ex else "") + (f" ({note})" if note else "")
    if capability == "DATA":
        return prefix + str(result.get("insight", "")) + "\nrows=%s cols=%s" % (result.get("rows", 0), result.get("columns", 0))
    if capability in ("APP_BUILDER", "APP_MODIFIER"):
        return (
            prefix
            + f"FL artifacts for '{result.get('slug', '')}' (mode {result.get('mode', 'create')}) written to "
            + str(result.get("app_dir", ""))[:240]
        )
    return prefix + sanitize_secrets(str(result))[:2000]


def run(engine, conv_id: str, text: str, attachments: list[dict] | None = None, opts: dict | None = None) -> dict:
    """Execute one session turn. `attachments` are [{'type','path'}].

    Returns the conversation update plus per-capability tool outcomes.
    """
    opts = opts or {}
    ensure_dirs()
    _GENERATED_DIR.mkdir(parents=True, exist_ok=True)

    check = policy.check(text)
    if not check["allowed"]:
        return {
            "ok": False,
            "command": "session",
            "policy": "refused",
            "refused": check["refused"],
            "reason": check["reason"],
            "conversation_id": conv_id,
        }

    conv = engine.get(conv_id)
    if conv is None:
        return {"ok": False, "command": "session", "error": f"unknown conversation: {conv_id}"}

    attachments = [a for a in (attachments or []) if isinstance(a, dict) and a.get("path")]
    route = router.route(text, [a.get("path", "") for a in attachments])
    capabilities = route["capabilities"]

    conv.append_message("user", text)
    if conv.title == "new chat":
        conv.data["title"] = (text or "").strip()[:44] or "new chat"
    if attachments:
        conv.data.setdefault("attachments", []).extend(attachments)

    results: dict = {}
    if capabilities == ["CHAT"]:
        out = engine.send(conv_id, text, opts=opts, stream=False, run_policy=False)
        results["CHAT"] = out
        rendered = _rendered("CHAT", out)
        status = "ok"
    else:
        for cap in capabilities:
            results[cap] = _dispatch(engine, text, attachments, cap, route, sql=opts.get("system"))
        rendered = "\n\n".join(_rendered(c, results[c]) for c in capabilities)
        status = "ok"

    conv.append_message("assistant", rendered, status=status)
    conv.save()

    return {
        "ok": True,
        "command": "session",
        "conversation_id": conv_id,
        "title": conv.title,
        "intent": route["intent"],
        "primary": route["primary"],
        "capabilities": capabilities,
        "contextual": route["contextual"],
        "results": results,
        "assistant": rendered,
        "status": status,
    }


def _dispatch(engine, text: str, attachments: list[dict], cap: str, route: dict, sql=None) -> dict:
    """Run the tool for one capability. Deterministic and honest in mock mode."""
    import builder  # local import keeps startup light

    low = " " + (text or "").strip().lower() + " "

    def image_path():
        return next((a["path"] for a in attachments if a.get("type") == "image"), None)

    def file_path():
        return next(
            (a["path"] for a in attachments if a.get("type") in ("file", "data")),
            None,
        )

    if cap == "VISION":
        import vision

        p = image_path()
        if not p:
            return {"ok": False, "error": "vision needs an image attachment"}
        if "ocr" in low or "read the text" in low or "read this" in low:
            return vision.ocr(engine.registry, p)
        if "identify" in low or "plant" in low or ("what" in low and image_path()):
            kind = "plant" if "plant" in low else "auto"
            return vision.identify(engine.registry, p, kind)
        return vision.describe(engine.registry, p, text or None)

    if cap == "WEB":
        import websearch

        return websearch.search(engine.registry, text, limit=8)

    if cap == "IMAGE":
        import imagegen

        stamp = uuid.uuid4().hex[:10]
        out = _GENERATED_DIR / f"gen-{stamp}.png"
        return imagegen.generate(engine.registry, text, str(out))

    if cap == "FILE":
        import files

        p = file_path()
        if not p:
            return {"ok": False, "error": "file analysis needs a file attachment"}
        return files.extract(p)

    if cap == "DATA":
        import data

        p = file_path()
        if p:
            return data.analyze_data(p, question=text)
        return {"ok": False, "error": "data analysis needs a data file attachment (csv/tsv/json/jsonl)"}

    if cap in ("APP_BUILDER", "APP_MODIFIER"):
        slug = None
        if cap == "APP_MODIFIER":
            slug = _existing_slug_from(text)
        staging = STATE_DIR / "builds" / "session"
        modify = slug or None
        return builder.build_app(text, modify=modify, staging=staging)

    return {"ok": False, "error": f"unsupported capability {cap}"}


def _existing_slug_from(text: str) -> str | None:
    """Best-effort slug detection in modify requests. Deterministic."""

    def slugify(s: str) -> str:
        s = re.sub(r"[^a-z0-9]+", "-", s.strip().lower()).strip("-")
        return s[:80]

    for m in re.finditer(r"\b(the|my)\s+([a-z0-9 \-]{3,60})\s+app\b", text.lower()):
        return slugify(m.group(2))
    return None