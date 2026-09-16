"""AI Tool Router for AppFactory Assistant.

Classifies a user request into capabilities:
  CHAT, VISION, WEB, IMAGE, FILE, DATA, APP_BUILDER, APP_MODIFIER
A single request can combine capabilities (e.g. VISION then APP_BUILDER in the
same conversation). Routing is deterministic (pure function of the text plus
any attachments). The same conversation can flow from assistant mode to app
building by the router switching the primary capability.

`contextual` marks follow-up requests that should reuse the conversation
history (summary/q&a context) rather than starting from scratch.
"""

from __future__ import annotations

from common import sanitize_secrets

CAPABILITIES = ("CHAT", "VISION", "WEB", "IMAGE", "FILE", "DATA", "APP_BUILDER", "APP_MODIFIER")

IMAGE_EXTS = (".jpg", ".jpeg", ".png", ".webp")
DATA_EXTS = (".csv", ".tsv", ".json", ".jsonl", ".xlsx")

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

_MODIFY_VERBS = ("add", "update", "modify", "change", "improve", "enhance", "upgrade", "extend", "include")
_MODIFY_LEADS = [
    "add a ", "add an ", "add one more ", "add new ", "add to my app", "add to the app",
    "add a feature to the app", "add a feature to my app",
    "update my app", "update the app", "update my android app",
    "modify my app", "modify the app", "modify the android app",
    "change my app", "change the app", "change my android app",
    "improve my app", "improve the app", "improve the android app",
    "upgrade my app", "upgrade the app", "enhance my app", "enhance the app",
    "extend the app", "extend my app", "also add", "can you add", "please add",
]

_WEB_WORDS = [
    "latest", "today", "news", "current", "live", "trending", "weather",
    "who won", "score", "price", "stock", "search the web", "search web",
    "recent", "how much", " what is", "when was", "who is", "population",
    "election", "reviews", "compare", "top 10", "top ten",
]

_IMAGE_LEAD = [
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

_DATA_WORDS = [
    "analyze the data", "analyze this data", "analyze the dataset", "analyze this dataset",
    "data analysis", "data analytics", "analyze this csv", "analyze this spreadsheet",
    "statistics", "statistical", "average", "the average", "mean of", "median of",
    "what is the average", "what's the average", "average value", "how many rows",
    "sum the ", "correlat", "csv file", "tsv", "dataset", "summarize the numbers",
    "compute", "plot", "chart", "insight from the data", "data file", "data table",
]

_FOLLOW_UP_WORDS = [
    "tell me more", "what about ", "what about it", "and then", "explain again",
    "how does that work", "why", "how? ", "yes", "continue", "go on", "more details",
    "what else", "really?", "can you expand", "elaborate", "in other words",
    "for example", "for instance", "give me details", "what does that mean",
    "and also", "wait", "hmm", "ok", "okay", "so what", "what should", "try again",
    "that makes sense", "good", "great", "anything else", "more", "another",
]


def _attachment_kind(attach: str) -> str | None:
    a = (attach or "").strip().lower()
    if not a:
        return None
    if a.endswith(IMAGE_EXTS):
        return "image"
    if a.endswith(DATA_EXTS):
        return "data"
    if any(c in a for c in (".", "/", "\\")) or "." in a:
        return "file"
    return None


def _kinds(attachments) -> set[str]:
    return {k for k in (_attachment_kind(a) for a in attachments) if k}


def _has(attachments, kind: str) -> bool:
    return kind in _kinds(attachments)


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


def _looks_like_modify(t: str) -> bool:
    if _looks_like_build(t):
        return False
    app_word = "app" in t or "widget" in t
    if not app_word:
        return False
    for lead in _MODIFY_LEADS:
        if lead in t:
            return True
    heads = t.split()
    if not heads:
        return False
    first = heads[0].strip(".,!;:")
    return first in _MODIFY_VERBS


def route(text: str, attachments: list[str] | None = None) -> dict:
    attachments = [a for a in (attachments or []) if a]
    t = " " + (text or "").strip().lower() + " "
    capabilities: list[str] = []

    def add(cap: str) -> None:
        if cap not in capabilities:
            capabilities.append(cap)

    build_request = _looks_like_build(t)
    modify_request = _looks_like_modify(t)

    is_image_req = bool(
        _has(attachments, "image")
        or any(w in t for w in _VISION_SINGLE)
    )
    if is_image_req and not (build_request or modify_request):
        add("VISION")

    is_file_req = bool(
        _has(attachments, "file")
        or any(w in t for w in _FILE_WORDS)
    )
    if is_file_req and not (build_request or modify_request):
        add("FILE")

    is_data_req = bool(
        _has(attachments, "data")
        or any(w in t for w in _DATA_WORDS)
    )
    if is_data_req and not (build_request or modify_request):
        add("DATA")

    is_web = any(w in t for w in _WEB_WORDS)
    if is_web or t.startswith(" search ") or "search the web" in t:
        add("WEB")

    is_img_gen = bool(
        not is_image_req and any(w in t for w in _IMAGE_LEAD) and "image of a" not in t[:6]
    )
    if is_img_gen:
        if "generate" in t and "android app" in t:
            pass  # "generate an android app" is a build, not an image generation
        elif " app" in t and ("generate a" in t or "create a" in t) and "a widget" not in t and _looks_like_build(t):
            add("APP_BUILDER")
        else:
            add("IMAGE")

    if "generate" in t and "image" in t and "image of a" in t and "android app" not in t:
        add("IMAGE")

    if modify_request:
        add("APP_MODIFIER")
    elif build_request:
        add("APP_BUILDER")

    if not capabilities:
        capabilities.append("CHAT")

    # Primary = first by descending priority order.
    strong_data = bool(
        "DATA" in capabilities
        and any(k in t for k in ("average", "mean", "median", "statistics", "correlat", "csv", "tsv", "rows", "dataset"))
    )
    if strong_data:
        priority_order = ("APP_MODIFIER", "APP_BUILDER", "IMAGE", "VISION", "DATA", "WEB", "FILE", "CHAT")
    else:
        priority_order = ("APP_MODIFIER", "APP_BUILDER", "IMAGE", "VISION", "WEB", "DATA", "FILE", "CHAT")
    primary = next((c for c in priority_order if c in capabilities), "CHAT")

    contextual = _looks_like_follow_up(t, capabilities)

    intent = {
        "APP_BUILDER": "app build request",
        "APP_MODIFIER": "app modification request",
        "VISION": "image understanding",
        "IMAGE": "image generation",
        "WEB": "web search",
        "FILE": "document understanding",
        "DATA": "data analysis",
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
        "contextual": contextual,
        "attachments": attachments,
    }


def _looks_like_follow_up(t: str, capabilities: list[str]) -> bool:
    if "CHAT" not in capabilities or len(capabilities) > 1:
        return False
    return any(w in t for w in _FOLLOW_UP_WORDS)