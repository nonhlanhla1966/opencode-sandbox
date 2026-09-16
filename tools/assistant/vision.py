"""Vision capability for AppFactory Assistant.

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
    p, err = _validate_image(image_path)
    if err:
        return {"ok": False, "command": "vision", "error": err}

    provider = registry.resolve("vision")
    base = {
        "command": "vision",
        "image": str(p),
        "name": p.name,
        "bytes": p.stat().st_size,
        "question": question or "Describe this image and, if it shows a plant or animal, identify it with uncertainty.",
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

    bullet = question or (
        "Describe this image. If it appears to show a plant or animal, identify it "
        "while stating your confidence and the observable characteristics you rely on."
    )
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": sanitize_secrets(bullet)},
                {"type": "image_url", "image_url": {"url": _data_url(p)}},
            ],
        }
    ]
    payload = registry.chat_payload(provider, messages, stream=False, opts={})
    headers = registry.auth_header(provider)
    status, body, err = transport.post_json(provider.endpoint, headers, payload)
    if err or status >= 400:
        base.update(
            {
                "ok": False,
                "verified": False,
                "error": err or f"HTTP {status}",
            }
        )
        return base
    try:
        content = body["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError):
        base.update({"ok": False, "verified": False, "error": "unexpected provider response shape"})
        return base
    base.update({"ok": True, "verified": True, "mode": "provider", "caption": sanitize_secrets(str(content))})
    return base