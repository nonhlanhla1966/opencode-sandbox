"""HTTPS-only transport for the AppFactory AI Assistant.

Golden rules enforced here:
- only `https://` endpoints are ever contacted (`http://` is rejected)
- TLS is always validated (python ssl defaults; no verify-off switches)
- API keys never appear in errors or returned payloads
"""

from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.request
from urllib.parse import urlparse

from common import sanitize_secrets

_ALLOWED_SCHEMES = ("https",)


def assert_https(url: str) -> None:
    scheme = urlparse(url).scheme.lower()
    if scheme not in _ALLOWED_SCHEMES:
        raise ValueError(f"insecure endpoint rejected (HTTPS/TLS only): {sanitize_secrets(url)}")


def _redact_headers(headers: dict) -> dict:
    red = {}
    for k, v in headers.items():
        if k.lower() in ("authorization", "x-api-key", "api-key", "cookie"):
            red[k] = "<redacted>"
        else:
            red[k] = v
    return red


def post_json(url: str, headers: dict | None = None, payload: dict | None = None, timeout: int = 90) -> tuple[int, dict | None, str | None]:
    """POST JSON. Returns (status_code, parsed_body_or_None, error_or_None).

    Never writes the API key into the returned error strings.
    """
    headers = headers or {}
    payload = payload or {}
    try:
        assert_https(url)
    except ValueError as e:
        return 0, None, sanitize_secrets(str(e))
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                return resp.status, json.loads(raw), None
            except ValueError:
                return resp.status, {"raw": raw[:20000]}, None
    except urllib.error.HTTPError as e:
        raw = ""
        try:
            raw = e.read().decode("utf-8", errors="replace")[:4000]
        except Exception:
            pass
        return e.code, None, sanitize_secrets(raw or e.reason and str(e.reason))
    except (urllib.error.URLError, TimeoutError, ssl.SSLError, OSError) as e:
        return 0, None, sanitize_secrets(str(e))


def get_text(url: str, headers: dict | None = None, timeout: int = 45) -> tuple[int, str | None, str | None]:
    headers = headers or {}
    try:
        assert_https(url)
    except ValueError as e:
        return 0, None, sanitize_secrets(str(e))
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, sanitize_secrets(resp.read().decode("utf-8", errors="replace")), None
    except urllib.error.HTTPError as e:
        raw = ""
        try:
            raw = e.read().decode("utf-8", errors="replace")[:4000]
        except Exception:
            pass
        return e.code, None, sanitize_secrets(raw)
    except (urllib.error.URLError, TimeoutError, ssl.SSLError, OSError) as e:
        return 0, None, sanitize_secrets(str(e))


def stream_chat_lines(url: str, headers: dict, payload: dict, timeout: int = 120):
    """Yield OpenAI-style SSE `data:` chunk contents for a streaming completion.

    Header values are never logged. Malformed lines are skipped, matching the
    server-sent-events convention.
    """
    assert_https(url)
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        for raw in resp:
            line = raw.decode("utf-8", errors="replace").strip()
            if not line or line.startswith(":"):
                continue
            if line.startswith("data:"):
                chunk = line[len("data:"):].strip()
                if not chunk or chunk == "[DONE]":
                    continue
                try:
                    evt = json.loads(chunk)
                except ValueError:
                    continue
                delta = evt.get("choices", [{}])[0].get("delta", {})
                content = delta.get("content")
                if content:
                    yield content


def stream_chat_chunks(url: str, headers: dict, payload: dict, timeout: int = 120):
    """Yield (text_chunk, done_flag) tuples; always yields a final done=True."""
    try:
        for text in stream_chat_lines(url, headers, payload, timeout=timeout):
            yield text, False
    except Exception as e:  # noqa: BLE001 — surface sanitized error to the caller
        yield sanitize_secrets(str(e)), True
    yield "", True