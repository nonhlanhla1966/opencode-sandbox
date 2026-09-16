"""Web search capability for AppFactory Assistant.

- Only HTTPS/TLS endpoints are ever contacted (transport enforces this).
- Results are source-aware: every result carries its URL and `source`.
- Retrieved information is clearly distinguished from model reasoning.
- When no search provider is configured (or in mock mode) the command is
  graceful: `available: false` with empty results. It never fabricates results.
- The mock provider returns clearly-labeled deterministic placeholder results
  tagged `source: mock` so tests and demos can exercise the full contract.
"""

from __future__ import annotations

import json
import re
from urllib.parse import quote_plus

import policy
import providers
import transport
from common import out, sanitize_secrets, utc_now

_SEARCH_SYSTEM = (
    "You are a search assistant. Use ONLY the retrieved results below as facts; "
    "clearly indicate that they come from a web search and do not fabricate URLs."
)


def search(registry: providers.ProviderRegistry, query: str, limit: int = 5) -> dict:
    check = policy.check(query)
    if not check["allowed"]:
        return {"ok": False, "available": False, "results": [], "policy": "refused", "reason": check["reason"]}
    try:
        limit = max(1, min(int(limit), 10))
    except (TypeError, ValueError):
        limit = 5
    provider = registry.resolve("websearch")

    if provider is None:
        return {
            "ok": False,
            "command": "websearch",
            "query": sanitize_secrets(query),
            "available": False,
            "results": [],
            "reason": "no web_search provider configured (real mode); nothing was fabricated",
        }

    if isinstance(provider, providers.MockProvider):
        base = {
            "command": "websearch",
            "query": sanitize_secrets(query),
            "available": True,
            "mode": "mock",
            "retrieved_at": utc_now(),
            "results": [{"title": _mock_title(query, i), "url": _mock_url(query, i), "snippet": _mock_snippet(query), "source": "mock"} for i in range(1, limit + 1)],
            "reasoning": "mock provider: placeholder results for offline tests; treat as non-authoritative.",
        }
        return {"ok": True, **base}

    results, err = _search_provider(provider, query, limit)
    if err:
        return {
            "ok": False,
            "command": "websearch",
            "query": sanitize_secrets(query),
            "available": False,
            "results": [],
            "reason": err,
        }
    return {
        "ok": True,
        "command": "websearch",
        "query": sanitize_secrets(query),
        "available": True,
        "mode": "provider",
        "provider_id": provider.id,
        "retrieved_at": utc_now(),
        "results": results,
        "reasoning": (
            "The above results were retrieved from the web source listed on each item. "
            "Reasoning about them is separate from the retrieved text."
        ),
    }


def _search_provider(provider: providers.Provider, query: str, limit: int) -> tuple[list[dict], str | None]:
    endpoint = provider.endpoint
    kind = provider.extra.get("request_kind", "").lower()
    headers = {"Content-Type": "application/json", "User-Agent": "AppFactoryAssistant/1.0"}
    if provider.api_key_set():
        headers["Authorization"] = f"Bearer {provider.api_key}"

    if kind == "duckduckgo":
        url = endpoint if "{q}" in (endpoint or "") else _ddg_url(query)
        status, text, err = transport.get_text(url, headers)
        if err or status >= 400:
            return [], err or f"HTTP {status}"
        results, parse_err = _parse_ddg(text, limit)
        return results, parse_err
    if kind == "opencode-tool":
        payload = {
            "model": provider.model or "big-pickle",
            "messages": [
                {"role": "system", "content": _SEARCH_SYSTEM},
                {"role": "user", "content": f"web search: {sanitize_secrets(query)}"},
            ],
            "stream": False,
        }
        status, body, err = transport.post_json(endpoint, headers, payload)
        if err or status >= 400:
            return [], err or f"HTTP {status}"
        answer = ""
        try:
            answer = body["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            return [], "unexpected provider response shape"
        return [{"title": "search answer", "url": "https://" + (endpoint.split("//")[1].split("/")[0] if "//" in endpoint else ""), "snippet": sanitize_secrets(answer)[:2000], "source": "provider"}], None

    # generic JSON search provider: GET with ?q= or POST JSON
    payload = {"query": sanitize_secrets(query), "limit": limit}
    status, body, err = transport.post_json(endpoint, headers, payload)
    if err or status >= 400:
        return [], err or f"HTTP {status}"
    raw = body.get("results", body.get("data", [])) if isinstance(body, dict) else []
    if not isinstance(raw, list):
        return [], "provider returned no searchable result list"
    results = []
    for item in raw[:limit]:
        if not isinstance(item, dict):
            continue
        results.append(
            {
                "title": sanitize_secrets(str(item.get("title", "")))[:300],
                "url": _https_only(str(item.get("url", item.get("href", "")))),
                "snippet": sanitize_secrets(str(item.get("snippet", item.get("description", ""))))[:600],
                "source": str(item.get("source", "web")),
            }
        )
    return results, None


def _https_only(url: str) -> str:
    url = url.strip()
    if url.startswith("http://"):
        return "https://" + url[len("http://"):]
    if not url.startswith(("https://",)):
        return ""
    return url


def _ddg_url(query: str) -> str:
    return "https://html.duckduckgo.com/html/?q=" + quote_plus(query)


def _parse_ddg(html: str, limit: int) -> tuple[list[dict], str | None]:
    results = []
    for m in re.finditer(r'<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>', html, re.S):
        href = m.group(1)
        title = re.sub(r"<[^>]+>", "", m.group(2)).strip()
        url = _https_only(href)
        if not url:
            continue
        results.append({"title": title.strip(), "url": url, "snippet": "", "source": "duckduckgo"})
        if len(results) >= limit:
            break
    if not results:
        return [], "no results parsed from search response"
    return results, None


def _mock_title(query: str, i: int) -> str:
    return f"Mock result {i} for: {sanitize_secrets(query)[:60]}"


def _mock_url(query: str, i: int) -> str:
    return f"https://example.com/mock/{i}/{quote_plus(query)[:40]}"


def _mock_snippet(query: str) -> str:
    return f"Deterministic placeholder web-search result for {sanitize_secrets(query)[:80]} (mock mode)."