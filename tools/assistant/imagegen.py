"""Image generation capability for AppFactory Assistant.

Hard rule: NEVER fabricate a successful generation. When no image-generation
provider is configured (or in mock/CI mode) the command returns
`generated: false` — it does not write a fake/placeholder image and does not
claim success.
"""

from __future__ import annotations

import base64
import json
import re
from pathlib import Path

import policy
import providers
import transport
from common import out, sanitize_secrets, utc_now

SAFE_OUT_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp"}


def _safe_prompt(prompt: str) -> str:
    return sanitize_secrets(prompt[:4000])


def generate(registry: providers.ProviderRegistry, prompt: str, out_path: str, size: str = "1024x1024") -> dict:
    check = policy.check(prompt)
    if not check["allowed"]:
        return {"ok": False, "generated": False, "policy": "refused", "reason": check["reason"]}

    opath = Path(out_path)
    if opath.suffix.lower() not in SAFE_OUT_SUFFIXES:
        return {
            "ok": False,
            "generated": False,
            "error": f"output must be one of {sorted(SAFE_OUT_SUFFIXES)}",
        }
    if "x" not in size or not re.fullmatch(r"\d+x\d+", size):
        return {"ok": False, "generated": False, "error": f"invalid size '{size}'"}

    provider = registry.resolve("imagegen")
    if provider is None or isinstance(provider, providers.MockProvider):
        return {
            "ok": False,
            "command": "imagegen",
            "generated": False,
            "reason": (
                "no image-generation provider configured (mock/offline mode). "
                "No image was produced; nothing was faked."
            ),
            "note": "never fabricate a generated image — configure an imagegen provider to get a real one",
        }

    payload = _imagegen_payload(provider, prompt, size)
    headers = registry.auth_header(provider)
    status, body, err = transport.post_json(provider.endpoint, headers, payload)
    if err or status >= 400:
        return {"ok": False, "generated": False, "error": err or f"HTTP {status}"}

    image_bytes, err = _extract_bytes(body)
    if err or not image_bytes:
        return {"ok": False, "generated": False, "error": err or "provider returned no image data"}
    opath.parent.mkdir(parents=True, exist_ok=True)
    opath.write_bytes(image_bytes)
    return {
        "ok": True,
        "command": "imagegen",
        "generated": True,
        "path": str(opath),
        "bytes": len(image_bytes),
        "size_requested": size,
        "ts": utc_now(),
    }


def _imagegen_payload(provider: providers.Provider, prompt: str, size: str) -> dict:
    """Try an OpenAI-compatible chat completion first with an explicit generation
    instruction; providers that support a native images endpoint can be set via
    provider.extra['request_kind'] = 'images'.
    """
    prompt_text = (
        f"Generate an image for this request and output ONLY the base64 of the image data: {_safe_prompt(prompt)}"
    )
    if provider.extra.get("request_kind") == "images":
        return {
            "prompt": _safe_prompt(prompt),
            "size": size,
            "n": 1,
            "response_format": "b64_json",
            "model": provider.model,
        }
    return {
        "model": provider.model,
        "messages": [{"role": "user", "content": prompt_text}],
        "stream": False,
    }


def _extract_bytes(body) -> tuple[bytes | None, str | None]:
    if not isinstance(body, dict):
        return None, "provider returned a non-JSON object"
    candidates = [
        body.get("data", [{}])[0].get("b64_json", "") if isinstance(body.get("data"), list) else "",
        body.get("b64_json", ""),
        body.get("image", ""),
    ]
    for c in candidates:
        if isinstance(c, str) and c:
            try:
                return base64.b64decode(c, validate=True), None
            except (ValueError, TypeError):
                continue
    nested = body.get("choices", [{}])
    if isinstance(nested, list):
        for ch in nested:
            content = ch.get("message", {}).get("content", "")
            if isinstance(content, str) and content.strip():
                return content.strip().encode("utf-8"), None
    return None, "no image payload found in provider response"