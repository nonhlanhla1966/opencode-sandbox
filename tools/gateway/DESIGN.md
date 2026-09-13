# DESIGN.md — AppFactory Remote Gateway · slug `gateway`

## Elevator pitch
A tiny, token-safe CLI that lets a future ChatGPT/MCP client drive the existing
AppFactory app pipeline through just three operations — submit a plain-language
app idea (which creates the `/oc`-triggered issue the factory already reacts
to), then observe build state Saturdays, and finally fetch a verified APK +
SHA-256 checksum straight from the rolling `app-<slug>-latest` release. It adds
a thin, deterministic JSON control layer **on top of** the proven issue→CI→
release engine; it does NOT replace, fork, or rebuild that pipeline.

## 1. Scope (what it IS / is NOT)
IS:
- `tools/gateway/gateway.sh <command> <args>` — one entry point, 3 commands:
  `create_app_request`, `get_build_status`, `get_latest_apk`.
- Strict JSON output on stdout (parseable by an MCP tool), human notes on
  stderr. Never prints secrets.
- Uses only GitHub's supported auth: `gh` CLI with `GITHUB_TOKEN` env (falling
  back to `GH_TOKEN`), exactly like the existing scripts do.
- Everything read-only except `create_app_request` (which opens one issue —
  the same action a human `/app`-poster performs today).

IS NOT:
- No new API keys/tokens. No second repository. No HTTPS/TLS work, no public
  server, no webhook listener, no background daemon.
- No weakening of OS/file security: no `sudo`, no chmod tricks, no disabling
  attestations, no storing tokens in files or URLs.
- No modifications to the existing `.github/workflows/`, `scripts/`, or any
  built app. The pipeline continues to run untouched.

## 2. Commands & JSON contract

### `create_app_request "<esa idea>"`
Turn a one-line idea into a factory issue and return tracking info.

- Normalize idea → slug: lowercase, `[a-z0-9]+(-[a-z0-9]+)*`, strip unsafe
  characters (`_`, `.`, spaces, path chars). Reject empty / nonprintable.
- If an open factory issue already exists for that slug, reuse it
  (`status:"request_reused"`) instead of spamming the backlog.
- Open issue `title="[AppFactory] <slug>: <idea>"` (slug in the title so
  `get_build_status` can find it later), body carries `AppFactory — build me:
  <idea>` plus the slug. The `opencode.yml` watcher triggers on an ISSUE
  COMMENT carrying `/app` | `/oc` | `/opencode`, so the gateway then posts a
  real `/oc <idea>` comment on that issue (identical to a human `/app` poster)
  to start the pipeline.
- Output:
```json
{"ok":true,"command":"create_app_request","slug":"<slug>","application_id":"com.appfactory.<slug>","issue":42,"status":"request_created","download_ready":false}
```

### `get_build_status <slug>`
Report AppFactory/CI/release/APK state WITHOUT secrets.

- Reads the issue's `DOWNLOAD_READY` (or failure) marker and the rolling
  release via `gh`.
- Output:
```json
{"ok":true,"command":"get_build_status","slug":"<slug>","issue":42,"appfactory_status":"building|ready|failed|unknown","ci_status":"in_progress|success|failure|none","release_status":"published|missing|none","apk_available":false,"apk_url":null,"sha256":null,"version":"0.1.0","updated_at":"...","checksums_present":false}
```

### `get_latest_apk <slug>`
Return the verified APK URL + checksum + version from `app-<slug>-latest`.

- Fetches `SHA256SUMS` from the release **and** recomputes the shipped APK's
  sha256 locally; only reports `verified:true` when they match.
- Output:
```json
{"ok":true,"command":"get_latest_apk","slug":"<slug>","release_url":"https://github.com/<owner>/<repo>/releases/tag/app-<slug>-latest","apk_url":"https://github.com/<owner>/<repo>/releases/download/app-<slug>-latest/<slug>-debug.apk","version":"0.1.0","sha256":"<hex>","verified":true,"apk_available":true,"build_status":"success"}
```

All three decline gracefully with `{"ok":false,"error":"..."}` on bad input or
GitHub errors; never crash, never hang, never token-leak.

## 3. Security model (maps to the 8 constraints)
| Constraint | How the gateway obeys it |
|---|---|
| Never expose OPENCODE_API_KEY | Gateway never reads/writes it; Json output schema has no field for it |
| Never expose PATs / creds in URLs | Auth via `gh` + `GITHUB_TOKEN` env only; URLs emitted are `https://github.com/...` artifact links, credentials never appended |
| Use GitHub's supported auth | `gh` CLI, `GH_TOKEN`/`GITHUB_TOKEN` env — identical to existing scripts |
| Secrets only in env, never logged | Script never echoes `$GITHUB_TOKEN` / `$GH_TOKEN`; stderr carries no token data |
| No filesystem permission prompts | No `sudo`, no chmod gating, no installer; plain bash read/JSON |
| Keep work in approved workspace | Reads repo only via `gh`; writes only the one factory issue for `create_app_request` |
| Don't weaken OS security / bypass controls | No attestation/`zipalign`/permission bypass; no disabling validation |
| No new API keys | True — reuses existing repo + token |

## 4. Failure + retry behavior
- Any `gh` failure → exit 1 with `{"ok":false,"error":".."}`; **no** built-in
  auto-mutation of the repo (opposite of the factory worker — this is a thin
  control plane, the factory owns builds).
- `get_latest_apk`: if checksum mismatch → `verified:false` (it never fakes a
  good result).
- `create_app_request`: idempotent-ish — if a matching open factory issue
  exists it reuses it (returns `issue` same, `status:"request_reused"`) rather
  than spamming the backlog.

## 5. Testing plan (design→implement, real assertions)
- `tools/gateway/gateway.test.sh` — JVM-free bash: verifies slug
  normalization, JSON shape (via `python3 -m json.tool` + field presence), and
  token non-exposure by asserting the stdout payload contains **no** substring
  of the token when a bogus token is injected.
- Local VALIDATION: run the test suite + `sh -n` on gateway.sh; validate-app.sh
  stays for Android apps.
- E2E (post-commit): `create_app_request` a trivial app (e.g. a "hi" button
  app) → the existing factory CI is already proven; the gateway then
  `get_build_status` until `DOWNLOAD_READY`, then `get_latest_apk` and
  `sha256sum -c`-verify the real published APK. Report exact results.
