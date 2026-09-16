"""Vision capability for AppFactory Assistant.

Capabilities:
- visual Q&A / generic image description
- OCR (read text in an image)
- plant / object / animal identification

- Validates that the file exists and is a supported image type.
- Calibrated uncertainty: without a configured vision provider the assistant
  does NOT guess what is in the image; it says verification is required.
- A configured provider is called with an OpenAI-compatible multiline text +
  image-url payload (data-URL, base64 inline — the key is never sent there).
"""

from __future__ import annotations

import base64
import mimetypes
from pathlib import Path

import policy
import providers
import transport
from common import out, sanitize_secrets, utc_now

SUPPORTED_TYPES = {".jpg", ".jpeg", ".png", ".webp"}

_DEFAULT_QA = (
    "Describe this image. If it appears to show a plant, animal, or recognizable object, "
    "identify it while stating your confidence and the observable characteristics you rely on."
)

_OCR_PROMPT = (
    "This image may contain text. Read the text in the image exactly as written. "
    "Output the recognized characters verbatim and nothing else."
)

_IDENTIFY_PROMPTS = {
    "plant": (
        "Identify the plant in this image. Give the most likely species or genus with your "
        "confidence level and the visible characteristics (leaf shape, color, arrangement, "
        "flowers) you used. If you cannot identify it with confidence, say so."
    ),
    "object": (
        "Identify the main object in this image. Give its name, category, and your confidence "
        "level with the visible characteristics you used."
    ),
    "animal": (
        "Identify the animal in this image. Give the most likely species or breed with your "
        "confidence level and the visible characteristics you used."
    ),
    "auto": (
        "Identify what is in this image (plant, animal, or object) with your confidence and "
        "the observable characteristics you rely on."
    ),
}


def _validate_image(path: str) -> tuple[Path | None, str | None]:
    p = Path(path)
    if not p.is_file():
        return None, f"image file not found: {path}"
    if p.stat().st_size <= 0:
        return None, "image file is empty"
    if p.suffix.lower() not in SUPPORTED_TYPES:
        return None, f"unsupported image type '{p.suffix}'; supported: {sorted(SUPPORTED_TYPES)}"
    head = p.read_bytes()[:12]
    if head.startswith(b"\x00"):
        return None, "file does not look like an image"
    return p, None


def _data_url(p: Path) -> str:
    mime = mimetypes.guess_type(str(p))[0] or "image/png"
    b64 = base64.b64encode(p.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{b64}"


def describe(
    registry: providers.ProviderRegistry,
    image_path: str,
    question: str | None = None,
) -> dict:
    """Visual Q&A / image description with calibrated uncertainty."""
    p, err = _validate_image(image_path)
    if err:
        return {"ok": False, "command": "vision", "error": err}

    provider = registry.resolve("vision")
    base = {
        "command": "vision",
        "image": str(p),
        "name": p.name,
        "bytes": p.stat().st_size,
        "question": question or _DEFAULT_QA,
        "ts": utc_now(),
    }

    if provider is None or isinstance(provider, providers.MockProvider):
        base.update(
            {
                "ok": True,
                "verified": False,
                "mode": "unverified",
                "caption": (
                    f"the file '{p.name}' is a valid image ({p.stat().st_size} bytes). No vision provider is "
                    f"configured in real mode, so I cannot reliably identify its contents and I will not guess."
                ),
                "note": "calibrated uncertainty — do not treat this as a real description",
            }
        )
        return base

    bullet = question or _DEFAULT_QA
    status, body, err = _call_provider(provider, bullet, p)
    if err:
        base.update({"ok": False, "verified": False, "error": err})
        return base
    content, err = _content_of(body)
    if err:
        base.update({"ok": False, "verified": False, "error": err})
        return base
    base.update({"ok": True, "verified": True, "mode": "provider", "caption": sanitize_secrets(str(content))})
    return base


def ocr(registry: providers.ProviderRegistry, image_path: str) -> dict:
    """Extract the text layer visible in an image. Never fabricates recognition."""
    p, err = _validate_image(image_path)
    if err:
        return {"ok": False, "command": "ocr", "error": err}

    provider = registry.resolve("vision")
    base = {
        "command": "ocr",
        "image": str(p),
        "name": p.name,
        "bytes": p.stat().st_size,
        "ts": utc_now(),
    }
    if provider is None or isinstance(provider, providers.MockProvider):
        base.update(
            {
                "ok": True,
                "verified": False,
                "mode": "unverified",
                "text": None,
                "note": "OCR requires a configured vision provider; no characters were guessed.",
            }
        )
        return base

    status, body, err = _call_provider(provider, _OCR_PROMPT, p)
    if err:
        base.update({"ok": False, "verified": False, "error": err})
        return base
    content, err = _content_of(body)
    if err:
        base.update({"ok": False, "verified": False, "error": err})
        return base
    base.update({"ok": True, "verified": True, "mode": "provider", "text": sanitize_secrets(str(content)).strip()})
    return base


def identify(registry: providers.ProviderRegistry, image_path: str, kind: str = "auto") -> dict:
    """Plant / object / animal identification with calibrated uncertainty.

    `kind` is one of plant|object|animal|auto. With no provider the assistant
    reports it cannot identify with confidence (it never guesses a species).
    """
    allowed = {"plant", "object", "animal", "auto"}
    if kind not in allowed:
        return {"ok": False, "command": "vision_identify", "error": f"unknown kind '{kind}'; expected {sorted(allowed)}"}
    p, err = _validate_image(image_path)
    if err:
        return {"ok": False, "command": "vision_identify", "error": err}

    provider = registry.resolve("vision")
    base = {
        "command": "vision_identify",
        "kind": kind,
        "image": str(p),
        "name": p.name,
        "bytes": p.stat().st_size,
        "ts": utc_now(),
    }
    if provider is None or isinstance(provider, providers.MockProvider):
        base.update(
            {
                "ok": True,
                "verified": False,
                "mode": "unverified",
                "classification": None,
                "confidence": None,
                "reasoning": None,
                "note": (
                    "no vision provider is configured in real mode, so the object cannot be "
                    "identified with confidence and no guess was invented"
                ),
            }
        )
        return base

    status, body, err = _call_provider(provider, _IDENTIFY_PROMPTS[kind], p)
    if err:
        base.update({"ok": False, "verified": False, "error": err})
        return base
    content, err = _content_of(body)
    if err:
        base.update({"ok": False, "verified": False, "error": err})
        return base
    base.update(
        {
            "ok": True,
            "verified": True,
            "mode": "provider",
            "classification": sanitize_secrets(str(content)),
            "confidence": None,
            "reasoning": "classification and confidence stated by the provider response text",
        }
    )
    return base


def _call_provider(provider: providers.Provider, bullet: str, p: Path) -> tuple[int, dict | None, str | None]:
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": sanitize_secrets(bullet)},
                {"type": "image_url", "image_url": {"url": _data_url(p)}},
            ],
        }
    ]
    if isinstance(provider, providers.MockProvider):
        return 0, None, "mock provider cannot describe images"
    reg = providers.ProviderRegistry()
    pld = reg.chat_payload(provider, messages, stream=False, opts={})
    headers = reg.auth_header(provider)
    return transport.post_json(provider.endpoint, headers, pld)


def _content_of(body: dict | None) -> tuple[str | None, str | None]:
    if not isinstance(body, dict):
        return None, "unexpected provider response shape"
    try:
        content = body["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError):
        return None, "provider returned an unexpected response shape"
    return content, None