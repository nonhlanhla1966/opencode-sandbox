"""Provider abstraction for the AppFactory AI Assistant.

Providers are declared in `providers.json` under the assistant state dir
(default `<repo>/.fastlane/assistant/providers.json`). A provider entry only
ever references an API key **by environment-variable name** — the key value
itself is never stored in the config, printed, or logged.

The Big Pickle / OpenCode preset uses the exact supported model id `big-pickle`
(never `opencode/big-pickle`) against the OpenAI-compatible OpenCode Zen
chat-completions endpoint. Endpoints remain user-configurable.

Modes:
  APPFactory_ASSISTANT_MODE=mock  -> the deterministic mock provider is always
                                     used (this is also the default in CI).
  APPFactory_ASSISTANT_MODE=real  -> configured providers are used; a command
                                     still fails cleanly (never fakes) when a
                                     required provider is not configured.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

from common import STATE_DIR, ensure_dirs, out, sanitize_secrets

CONFIG_VERSION = "1.0"

BIG_PICKLE_PRESET = {
    "id": "big-pickle",
    "name": "Big Pickle — OpenCode Zen (OpenAI-compatible)",
    "kind": "chat",
    "endpoint": "https://opencode.ai/zen/v1/chat/completions",
    "model": "big-pickle",
    "api_key_env": "OPENCODE_API_KEY",
    "stream": True,
    "note": "Model id is exactly 'big-pickle'. OpenCode Zen: free chat model, "
            "no Authorization header required for free models when no key is set.",
}

EXAMPLE_CONFIG = {
    "version": CONFIG_VERSION,
    "default_chat": "big-pickle",
    "providers": [
        BIG_PICKLE_PRESET,
        {
            "id": "big-pickle-vision",
            "name": "Big Pickle vision-capable (OpenCode Zen)",
            "kind": "vision",
            "endpoint": "https://opencode.ai/zen/v1/chat/completions",
            "model": "big-pickle",
            "api_key_env": "OPENCODE_API_KEY",
            "stream": False,
        },
        {
            "id": "big-pickle-imagegen",
            "name": "Big Pickle image generation (OpenCode Zen)",
            "kind": "imagegen",
            "endpoint": "https://opencode.ai/zen/v1/chat/completions",
            "model": "big-pickle",
            "api_key_env": "OPENCODE_API_KEY",
            "stream": False,
        },
        {
            "id": "generic-websearch",
            "name": "Generic web search (any HTTPS JSON search provider)",
            "kind": "websearch",
            "endpoint": "https://example.invalid/search",
            "model": "",
            "api_key_env": "",
            "stream": False,
        },
    ],
}

MOCK = "mock"


def default_config_path() -> Path:
    return STATE_DIR / "providers.json"


def _is_ci() -> bool:
    return os.environ.get("CI", "").lower() in ("1", "true", "yes")


def force_mock() -> bool:
    mode = os.environ.get("APPFACTORY_ASSISTANT_MODE", "").strip().lower()
    if mode == "real":
        return False
    if mode == "mock":
        return True
    return _is_ci()


class Provider:
    """A configured provider entry. Never holds the API key value."""

    def __init__(self, entry: dict):
        self.id = str(entry.get("id") or "")
        self.name = str(entry.get("name") or self.id)
        self.kind = str(entry.get("kind") or "chat")
        self.endpoint = str(entry.get("endpoint") or "").strip()
        self.model = str(entry.get("model") or "").strip()
        self.api_key_env = str(entry.get("api_key_env") or "").strip()
        self.stream = bool(entry.get("stream", False))
        self.extra = entry

    @property
    def api_key(self) -> str:
        if not self.api_key_env:
            return ""
        return os.environ.get(self.api_key_env, "")

    def api_key_set(self) -> bool:
        return bool(self.api_key)

    def sanitized(self) -> dict:
        d = dict(self.extra)
        d.pop("api_key", None)
        return d

    def __repr__(self) -> str:  # never print the key
        return f"Provider(id={self.id!r}, kind={self.kind!r}, endpoint={self.endpoint!r}, model={self.model!r})"


class MockProvider(Provider):
    """Deterministic offline provider used in mock/CI mode.

    Never produces a real network call. Deliberately refuses to fake vision
    descriptions, image generation, or web provenance — it only ever returns
    clearly-labeled mock data or a calibrated-uncertainty answer.
    """

    def __init__(self, kind: str = "chat"):
        super().__init__(
            {
                "id": "mock",
                "name": "Deterministic mock provider (offline)",
                "kind": kind,
                "endpoint": "mock://offline",
                "model": "mock",
                "stream": True,
            }
        )


class ProviderRegistry:
    def __init__(self, config_path: Path | None = None, mode: str | None = None):
        self.config_path = config_path or default_config_path()
        self._mode = mode or os.environ.get("APPFACTORY_ASSISTANT_MODE", "").strip().lower()
        self.providers_by_id: dict[str, Provider] = {}
        self.providers_by_kind: dict[str, list[Provider]] = {}
        self.default_chat = "big-pickle"
        self.version = CONFIG_VERSION
        self._load()

    def _load(self) -> None:
        self._mode = self._mode or os.environ.get("APPFACTORY_ASSISTANT_MODE", "").strip().lower()
        if self.config_path.exists():
            try:
                cfg = json.loads(self.config_path.read_text(encoding="utf-8"))
            except (ValueError, OSError):
                cfg = {}
        else:
            cfg = {}
        self.version = str(cfg.get("version") or CONFIG_VERSION)
        self.default_chat = str(cfg.get("default_chat") or "big-pickle")
        for entry in cfg.get("providers", []):
            if not isinstance(entry, dict) or not entry.get("id"):
                continue
            p = Provider(entry)
            self.providers_by_id[p.id] = p
            self.providers_by_kind.setdefault(p.kind, []).append(p)

    def get(self, provider_id: str) -> Provider | None:
        return self.providers_by_id.get(provider_id)

    def by_kind(self, kind: str) -> list[Provider]:
        return list(self.providers_by_kind.get(kind, []))

    def _eff_mode(self) -> str:
        return (self._mode or os.environ.get("APPFACTORY_ASSISTANT_MODE", "").strip().lower()).strip().lower()

    def _mock_allowed(self) -> bool:
        mode = self._eff_mode()
        if mode == "real":
            return False
        if mode == "mock":
            return True
        return _is_ci()

    def resolve(self, kind: str, provider_id: str | None = None) -> Provider | None:
        """Return the active provider for a capability or None.

        - mock/CI mode                   -> deterministic MockProvider
        - real mode + requested id set   -> that provider (if kind matches)
        - real mode + configured kind    -> first configured provider
        - real mode + nothing configured -> None (engines must report the
          capability as unavailable instead of guessing)
        """
        if self._mock_allowed():
            return MockProvider(kind)
        if provider_id:
            p = self.get(provider_id)
            if p and p.kind == kind:
                return p
        by_kind = self.by_kind(kind)
        if by_kind:
            return by_kind[0]
        return None

    def chat_payload(self, provider: Provider, messages: list[dict], stream: bool, opts: dict | None = None) -> dict:
        """OpenAI-compatible chat payload builder (testable).

        Never mutates the configured model id and never adds an `opencode/`
        prefix; the request must contain exactly the supported model id.
        """
        opts = opts or {}
        payload = {
            "model": provider.model,
            "messages": messages,
            "stream": bool(stream),
        }
        if opts.get("temperature") is not None:
            payload["temperature"] = float(opts["temperature"])
        if opts.get("max_tokens") is not None:
            payload["max_tokens"] = int(opts["max_tokens"])
        if opts.get("system"):
            payload["messages"] = [{"role": "system", "content": opts["system"]}] + list(messages)
        return payload

    def auth_header(self, provider: Provider) -> dict:
        headers = {"Content-Type": "application/json", "User-Agent": "AppFactoryAssistant/1.0"}
        if provider.api_key_set():
            headers["Authorization"] = f"Bearer {provider.api_key}"
        return headers

    def listing(self) -> dict:
        mode = self._eff_mode()
        if mode not in ("real", "mock"):
            mode = "mock" if force_mock() else "real"
        return {
            "version": self.version,
            "mode": mode,
            "default_chat": self.default_chat,
            "providers": [p.sanitized() for p in self.providers_by_id.values()],
        }


def init_example_config(path: Path | None = None) -> Path:
    ensure_dirs()
    path = path or default_config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        return path
    path.write_text(json.dumps(EXAMPLE_CONFIG, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return path


def report_listing(registry: ProviderRegistry) -> None:
    listing = registry.listing()
    out({"ok": True, "command": "providers list", **listing})


def report_init(path: Path) -> None:
    out({"ok": True, "command": "providers init", "config": str(path),
         "note": "API keys stay in the environment; config only references env var names."})


def report_test(registry: ProviderRegistry, provider_id: str | None, kind: str | None = None) -> None:
    if provider_id:
        p = registry.get(provider_id)
        if not p:
            out({"ok": False, "command": "providers test", "error": f"unknown provider: {provider_id}"})
            return
        kinds = [p.kind]
    elif kind:
        p = registry.resolve(kind)
        kinds = [kind]
    else:
        p = MockProvider("chat")
        kinds = ["chat"]
    if p is None:
        out({"ok": True, "command": "providers test", "kind": ",".join(kinds),
             "mode": "real", "reachable": "not-configured",
             "note": "no provider of this kind is configured in real mode"})
        return
    key_state = "env-key-present" if getattr(p, "api_key_set", lambda: False)() else "no-key-set"
    result = {
        "ok": True,
        "command": "providers test",
        "provider": p.id,
        "kind": ",".join(kinds),
        "mode": "mock" if isinstance(p, MockProvider) else "real",
        "endpoint": p.endpoint if isinstance(p, Provider) else "",
        "model": getattr(p, "model", ""),
        "auth": key_state,
        "reachable": "not-tested-offline",
    }
    if not isinstance(p, MockProvider) and p.endpoint.startswith("http://"):
        result["ok"] = False
        result["error"] = "insecure endpoint rejected (HTTPS/TLS only)"
    out(result)