# AppFactory AI Assistant Layer

Deterministic AI chat, vision, image generation, web search, document understanding,
and a bridge from AI conversation to the FL3 AppFactory pipeline.

## Quick start

```bash
# Mock mode (default in CI)
APPFACTORY_ASSISTANT_MODE=mock python3 tools/assistant/assistant.py providers list

# Full round-trip
python3 tools/assistant/assistant.py chat new --title "My app idea"
python3 tools/assistant/assistant.py chat send <conversation_id> --text "build me a flashlight app"
python3 tools/assistant/assistant.py route "what plant is this in the photo"
python3 tools/assistant/assistant.py vision ./photo.jpg
python3 tools/assistant/assistant.py web "latest mango harvest news"
python3 tools/assistant/assistant.py build "build me a todo app" --staging /tmp/staging
```

## Selftest

```bash
python3 tools/assistant/assistantselftest.py
# Expected: 78/78 PASS (all deterministic, no network required)
```

## Architecture

| Module | Purpose |
|--------|---------|
| `assistant.py` | Single CLI entrypoint for all subcommands |
| `common.py` | State dirs, JSON IO, `sanitize_secrets` |
| `transport.py` | HTTPS-only POST/GET, SSE streaming, error redaction |
| `providers.py` | `ProviderRegistry`, `MockProvider`, Big Pickle preset |
| `chat.py` | Conversation CRUD, streaming, retry/regenerate/edit/stop |
| `vision.py` | Image validation, calibrated uncertainty (never guesses) |
| `imagegen.py` | Never fabricates success; clean decline without provider |
| `websearch.py` | HTTPS-only source-aware results; graceful offline decline |
| `files.py` | Text extraction, secrets redacted, binary rejection |
| `policy.py` | Deterministic content-policy gate |
| `router.py` | Intent router (CHAT/VISION/WEB/IMAGE_GENERATION/FILE_ANALYSIS/APP_BUILDER) |
| `builder.py` | AI→AppFactory bridge (analyze→plan→scaffold, modify, staging isolation) |

## Environment

| Variable | Values | Default |
|----------|--------|---------|
| `APPFACTORY_ASSISTANT_MODE` | `mock` \| `real` | `mock` (forced in CI) |
| `APPFACTORY_ASSISTANT_DATA` | state dir path | `<repo>/.fastlane/assistant` |
| `OPENCODE_API_KEY` | API key for Big Pickle / OpenAI-compatible providers | (not required in mock) |

## API Keys

API keys are referenced **by env-var name only** in the provider config — never
stored in `providers.json` or printed to stdout/stderr/logs. See `providers.json`
example.

## State

All runtime state lives under `.fastlane/assistant/` (gitignored):
- `chat/` — conversation JSON files
- `builds/` — staging build artifacts

## Golden rules

1. Never fake unconfigured external capabilities
2. HTTPS-only; TLS always validated
3. No secrets in stdout/stderr/logs
4. Never modify real `apps/` directly (staging isolation)
5. All CLIs emit deterministic JSON
