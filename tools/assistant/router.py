"""AI Tool Router for AppFactory Assistant.

Classifies a user request into capabilities:
  CHAT, VISION, WEB, IMAGE_GENERATION, FILE_ANALYSIS, APP_BUILDER
A single request can combine capabilities (e.g. VISION then APP_BUILDER in the
same conversation). Routing is deterministic (pure function of the text plus
any attachments). The same conversation can flow from assistant mode to app
building by the router switching the primary capability.
"""

from __future__ import annotations

from common import sanitize_secrets

CAPABILITIES = ("CHAT", "VISION", "WEB", "IMAGE_GENERATION", "FILE_ANALYSIS", "APP_BUILDER")

IMAGE_EXTS = (".jpg", ".jpeg", ".png", ".webp")

_APP_BUILDER_LEAD = [
    "build me an app", "build me an android app", "build an app", "build a app",
    "build a ", "make an app", "make a app", "make me an app", "make an android app",
    "create an app", "create a app", "create an android app", "generate an app",
    "generate a app", "i want an app", "i need an app", "give me an app",
    "new app", "turn this into an app", "appfactory", "fast lane",
    "fastlane", "app factory", "build an android app", "build the",
]
_APP_BUILDER_MID = [
    " android app", " mobile app", " an app that", " a app that", "app idea",
    "an app for ", " a widget", " a calculator", " a chatbot app", "to the app",
]

_BUILD_VERBS = ("build ", "build me", "build a", "build an", "make ", "make me", "create ", "generate ", "develop ", "scaffold ")

_WEB_WORDS = [
    "latest", "today", "news", "current", "live", "trending", "weather",
    "who won", "score", "price", "stock", "search the web", "search web",
    "recent", "how much", " what is", "when was", "who is", "population",
    "election", "reviews", "compare", "top 10", "top ten",
]

_IMAGE_GEN_WORDS = [
    "generate an image", "generate a image", "create an image", "create a image",
    "generate a picture", "create a picture", "draw ", "drawing", "make a logo",
    "logo", "illustration", "illustration of", "wallpaper of", "image of a",
    "a painting of", "generate a photo", "picture of a", "cover art",
    "an icon of", "generate art", "make an icon",
]

_VISION_SINGLE = [
    "what plant is this", "what kind of plant", "identify this plant",
    "what animal is this", "identify this image", "describe this photo",
    "describe this image", "describe this picture", "what is this", "what's this",
    "identify", "ocr", "read the text in this", "what breed", "what species",
    "is this ripe", "what is in this image", "analyze this image", "look at this photo",
]

_FILE_WORDS = [
    "summarize this file", "summarize this document", "analyze this document",
    "analyze this file", "read this file", "extract from this", "parse this",
    "understand this document", "talk about this document", "what does this file",
    "summarize this pdf", "translate this file", "convert this",
]


def _attachment_kind(attach: str) -> str | None:
    a = (attach or "").strip().lower()
    if not a:
        return None
    if a.endswith(IMAGE_EXTS):
        return "image"
    if any(c in a for c in (".", "/", "\\")) or "." in a:
        return "file"
    return None


def _has_image_attachment(attachments) -> bool:
    return any(_attachment_kind(a) == "image" for a in attachments)


def _has_file_attachment(attachments) -> bool:
    return any(_attachment_kind(a) == "file" for a in attachments)


def route(text: str, attachments: list[str] | None = None) -> dict:
    attachments = [a for a in (attachments or []) if a]
    t = " " + (text or "").strip().lower() + " "
    capabilities: list[str] = []

    def add(cap: str) -> None:
        if cap not in capabilities:
            capabilities.append(cap)

    is_image_req = bool(
        _has_image_attachment(attachments)
        or any(w in t for w in _VISION_SINGLE)
    )
    if is_image_req and not _looks_like_build(t):
        add("VISION")

    is_file_req = bool(
        _has_file_attachment(attachments)
        or any(w in t for w in _FILE_WORDS)
    )
    if is_file_req:
        add("FILE_ANALYSIS")

    is_web = any(w in t for w in _WEB_WORDS)
    if is_web or t.startswith(" search ") or "search the web" in t:
        add("WEB")

    is_img_gen = bool(
        not is_image_req and any(w in t for w in _IMAGE_GEN_WORDS) and "image of a" not in t[:6]
    )
    if is_img_gen:
        add("IMAGE_GENERATION")

    # Normalize image-generation wording that also contains "draw the ..." etc.
    if "generate" in t and "image" in t and "image of a" in t and "android app" not in t:
        add("IMAGE_GENERATION")

    is_app = _looks_like_build(t)
    if is_app:
        add("APP_BUILDER")

    if not capabilities:
        capabilities.append("CHAT")

    # Primary = first by descending priority order.
    priority_order = ("APP_BUILDER", "IMAGE_GENERATION", "VISION", "FILE_ANALYSIS", "WEB", "CHAT")
    primary = next((c for c in priority_order if c in capabilities), "CHAT")

    intent = {
        "APP_BUILDER": "app build request",
        "VISION": "image understanding",
        "IMAGE_GENERATION": "image generation",
        "WEB": "web search",
        "FILE_ANALYSIS": "document understanding",
        "CHAT": "conversation",
    }[primary]

    return {
        "ok": True,
        "command": "router",
        "text": sanitize_secrets(text)[:2000],
        "intent": intent,
        "primary": primary,
        "capabilities": capabilities,
        "combined": len(capabilities) > 1,
        "attachments": attachments,
    }


def _looks_like_build(t: str) -> bool:
    for lead in _APP_BUILDER_LEAD:
        if lead in t:
            return True
    app_word = "app" in t or "widget" in t
    if app_word and any(v in t for v in _BUILD_VERBS):
        return True
    if any(mid in t for mid in _APP_BUILDER_MID) and app_word:
        return True
    return False