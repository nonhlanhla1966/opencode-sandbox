# Android AppFactory

Autonomous Android app factory built on top of the OpenCode / GitHub Actions
integration. Ask for an app in one line; the factory designs it, builds it on
cloud CI, verifies the APK, publishes a GitHub Release, and gives you a direct
download link.

> The original `opencode/big-pickle` + `OPENCODE_API_KEY` configuration is
> preserved and drives this factory. OpenCode itself is untouched — this
> repository is the factory foundation.

## One-line request

Comment on any issue (or PR) in this repository:

```
/app A lightweight offline habit tracker with a 7-day streak view and gentle reminders
```

The engine (an OpenCode agent running on `opencode/big-pickle` in GitHub
Actions) drives the deterministic **Fast Lane** pipeline (est', quality gates,
security scan and stage telemetry run on every build):

```
IDEA -> ANALYZE -> PLAN -> SCAFFOLD -> TESTGEN -> LOCAL_VALIDATION
     -> GITHUB_PUSH -> CI_BUILD -> FAST_LANE (est/gates/security/telemetry)
     -> APK_VERIFY -> RELEASE -> DOWNLOAD_READY
```

When the build finishes you get a `DOWNLOAD_READY` comment on the issue with a
direct public APK download link.

## Fast Lane engine

Deterministic, reproducible engine in `tools/fastlane/` — the agent runs the
CLIs; it never hand-writes specs or scaffolds by hand.

| Tool | What it does |
|---|---|
| `analyze.py` | App Idea Analyzer — deterministic features/screens/permissions/APIs/storage/complexity + build-time estimate → `app-spec.json` |
| `plan.py` | Planner — `architecture.json` + `PLAN.md` (modules, layers, DB, TLS-always-on APIs, permissions, testing, release gates, JDK17/Gradle/AGP8.5.2/SDK34 pins) |
| `scaffold.py` | Project generator — real Java Android Gradle app under `apps/<slug>/`, vendored modules, wrapper from `skel/`, dark theme, DESIGN.md, PLAN.md, unit tests |
| `testgen.sh` | Automatic unit-test generation (never overwrites existing tests) |
| `build.sh` | Per-app orchestrator: est banner → compile (repair budget) → tests → gates → security → perf → verify → telemetry |
| `build-all.sh` | Batch/parallel build for CI (`-j N`), whole-run estimate |
| `estimate.sh` | Build-time estimator, self-tuned from `.fastlane/telemetry.json` |
| `telemetry.sh` | Stage timings per build, tapped into the run history |
| `repair.sh` + `fixes/` | Error classification + bounded repair loop (budget 3) |
| `gates.sh` | Quality gates C001–C014 — any FAIL blocks release |
| `security-scan.sh` | Static scan: secrets, cleartext, exported components, weak perms |
| `deps.sh` + `modules/DEPENDENCY_REGISTRY.json` | Dependency allowlist + known-vulnerable table |
| `perf.sh` | APK size / method-count heuristics |
| `regression.sh` | Regression lab over the protected apps |
| `selftest.sh` | No-SDK engine self-tests (75/75 pass — FL2 baseline + FL3 engines) |

### Fast Lane 3.0 engines (layered on the FL2 engine)

| Tool | What it does |
|---|---|
| `spec_validate.py` | Specification Engine — canonical `app-spec.json` schema validation, `spec_version: "3.0"`, sha256 checksum, assumptions, feature→module→test→gate coverage (`contract`) |
| `compat.py` | Module Registry + Dependency Intelligence — versioned `modules/REGISTRY.json`, checksums/metadata, pin compatibility, registry-driven module selection |
| `taskgraph.py` | Task Graph Engine — deterministic DAG + topological order + safe parallel fan-out + cycle/conflict verification |
| `preflight.py` | Preflight + Predictive Errors — machines-report pre-build faults; correlates build logs with `knowledge/failures.json` signatures |
| `checkpoint.sh` | Recovery/Checkpoint Engine — per-app stage checkpoints, resume from last completed stage |
| `knowledge.sh` | Knowledge/Cache Engine — persistent cross-run knowledge + predictive failure DB |
| `device.sh` | Device Validation Engine — install/launch on real device; honest `SKIP` w/ reason if none |
| `ui-validate.sh` | UI Validation Engine — static layout/id checks always run; dynamic UI `SKIP` w/ reason |
| `accuracy.py` | Accuracy Score (0–100) from real artifacts only — APK, tests, gates, coverage, security, deps |
| `benchmark.sh` | Speed Score — FL1/FL2/FL3 comparison from real run telemetry |
| `fastlane3.sh` | Fast Lane 3.0 orchestrator (`fastlane.sh full3 "<idea>"`) — checkpointed pipeline end-to-end |

## Verified modules

`modules/<id>/` are reusable Java components (pure JVM + optional Android
glue), each with tests, vendored into generated apps as source. Currently:
`json`, `text`, `validation`, `time`, `crypto`, `storage-sqlite`, `http-rest`,
`settings`, `network`, `retry`, `themegen`, `storage-file`, `notifications`,
`media-image`.

## Pipeline stages

| Stage | Owner | What happens |
|-------|-------|--------------|
| IDEA | You | One-line `/app <description>` comment |
| ANALYZE | Fast Lane | `analyze.py` — deterministic spec + complexity + estimate |
| PLAN | Fast Lane | `plan.py` — architecture.json + PLAN.md |
| SCAFFOLD | Fast Lane | `scaffold.py` — real project under `apps/<slug>/` with DESIGN.md |
| TESTGEN | Fast Lane | `testgen.sh` — generated unit tests |
| LOCAL_VALIDATION | OpenCode agent | `scripts/validate-app.sh` structural checks |
| GITHUB_PUSH | OpenCode agent | Commit and push to `main` (design + code together) |
| CI_BUILD | GitHub Actions | `build.yml` compiles `apps/*` on `ubuntu-latest` via `build-all.sh` |
| FAST_LANE | GitHub Actions | Estimate banner, quality gates C001–C014, security scan, perf, telemetry |
| APK_VERIFY | GitHub Actions | `aapt dump badging` checks package, version, launchable activity |
| RELEASE | GitHub Actions | Idempotent rolling release `app-<slug>-latest` with APK + `SHA256SUMS` |
| DOWNLOAD_READY | GitHub Actions | Posts the public download link on the requesting issue |

The agent repairs and retries its own failures up to **3 times** (design/code/
test/validation), and `build.sh` uses the same bounded repair budget in CI. The
quality gates and security scan can never be softened to save time.

## Remote gateway (CLI)

`tools/gateway/gateway.sh` is a thin JSON control plane that drives this
factory from a script or a ChatGPT/MCP client. It layers **on top of** the
proven issue→CI→release engine — it never forks or replaces it. Three commands,
strict JSON on stdout, nothing secret ever printed:

| Command | What it does |
|---|---|
| `create_app_request "<one-line idea>"` | Creates the factory issue (title carries the slug) and posts the `/oc` trigger comment the `opencode.yml` watcher reacts to. Reuses an already-open issue for the same slug. |
| `get_build_status <slug-or-issue>` | Pipeline position: AppFactory status, CI status (`build.yml`), release status, APK availability. |
| `get_latest_apk <slug>` | Verified APK URL, version and SHA-256 — the checksum is **recomputed locally** against the release `SHA256SUMS`; `verified:true` only on a match. |

```
bash tools/gateway/gateway.sh create_app_request "A calorie counter with barcode scanning"
bash tools/gateway/gateway.sh get_build_status calorie-counter
bash tools/gateway/gateway.sh get_latest_apk calorie-counter
```

Auth is the same supported mechanism as the rest of the repo: `gh` CLI with
`GITHUB_TOKEN`/`GH_TOKEN` env — no new keys, no credentials in URLs, no repo
branching. Self-tests (JVM-free, bounded live calls):
`bash tools/gateway/gateway-test.sh`. Contract, JSON schemas and security model:
`tools/gateway/DESIGN.md`.

## Apps live here

* `apps/<slug>/` — each directory is a **standalone, self-contained Android
  Gradle project** (its own wrapper, settings + build files). No root build
  files are required.
* `apps/hello-appfactory/` — seed/template app: a real, buildable, dependency-
  free Java APK (minSdk 21, two screens). Use it as the reference template
  whenever you implement a new app.
* `apps/opencode-chatbot/` — open-source AI chat client: streaming via any
  OpenAI-compatible API, markdown + code highlighting, per-conversation
  storage, encrypted API keys, light/dark/system themes, no ads, no accounts.

## Architecture rules

* **Real APKs only.** Every app must be a genuine, buildable APK project.
  Mockups, placeholders and fake builds are rejected.
* **Lightweight by default.** Java-first, no heavy UI libraries, small minSdk,
  minimal install size — suited to low-RAM Android phones.
* **Cloud build.** All compilation happens in GitHub Actions; CI does the
  build, not the local agent.
* **No TLS bypasses.** Certificate validation is always on.
* **No routine confirmations.** The pipeline runs unattended; it does not wait
  for approvals.
* **Secrets stay secret.** The API key is a CI secret only; it is never written
  to files, logs, or comments.

## Manual / developer entry points

* `workflow_dispatch` on **build all apps** — rebuild everything and refresh
  releases.
* Slack-shaped command surface: edit `.github/workflows/opencode.yml` to add
  more trigger phrases (defaults: `/app`, `/oc`, `/opencode`).

## Fast Lane 3.0

Fast Lane 3.0 is the **final major architecture upgrade** and is layered on top
of the working FL1/FL2 stack — nothing existing was removed. It adds the
canonical spec (versioned + checksummed + schema-validated, assumptions
recorded), the versioned Module Registry with registry-driven selection, the
Task Graph with safe fan-out, Preflight + predictive errors, Checkpoint/Resume,
the Knowledge/Cache + device/UI engines (honest `SKIP` w/ reason), Accuracy and
Speed scores, gates C011–C014, and an end-to-end orchestrator. Full document:
`FASTLANE_3_FINAL_ARCHITECTURE.md`.

### AppFactory AI Assistant (app layer on top of Fast Lane 3)

A ChatGPT-like conversational AI assistant lives in `tools/assistant/`. It routes
every turn (text + attachments) through a complete 8-capability tool router
and bridges AI conversation directly into the FL3 AppFactory pipeline.

| Capability | What it does |
|------------|--------------|
| `CHAT`     | Persistent contextual conversations with follow-up detection |
| `VISION`   | Image Q&A, OCR, plant/object/animal identification (calibrated) |
| `WEB`      | HTTPS-only web search with source-aware results |
| `IMAGE`    | Image generation (provider-dependent, never fakes) |
| `FILE`     | PDF text-layer, DOCX, code and text extraction with redaction |
| `DATA`     | Deterministic CSV/JSON/TSV analysis: stats, correlations, insight |
| `APP_BUILDER` | AI-to-AppFactory bridge: idea → spec → FL3 scaffold → CI release |
| `APP_MODIFIER` | Extend an existing app with new features through the pipeline |

**CLI entry point:** `python3 tools/assistant/assistant.py`

```
python3 tools/assistant/assistant.py route "Analyze my harvest data" --file harvest.csv
python3 tools/assistant/assistant.py data analyze harvest.csv --question "average yield?"
python3 tools/assistant/assistant.py vision ocr photo.png
python3 tools/assistant/assistant.py session <conv-id> "Build me a plant ID app"
```

**Selftest:** `python3 tools/assistant/assistantselftest.py` → **141/141 PASS** (no
network, no credentials; deterministic mock provider).

```
bash tools/fastlane/fastlane.sh full3 "<one-line idea>"   # checkpointed FL3 pipeline
bash tools/fastlane/selftest.sh                            # 75 no-SDK engine tests
bash tools/fastlane/accuracy.py score apps/<slug>          # Accuracy Score from real artifacts
bash tools/fastlane/benchmark.sh report                    # Speed Score: FL1 vs FL2 vs FL3
```

## Layout

```
.
├── AGENTS.md                  # Operating procedure the OpenCode agent follows
├── README.md
├── FASTLANE_3_FINAL_ARCHITECTURE.md  # FL3.0 architecture document
├── docs/DESIGN_TEMPLATE.md    # Design-first template (screens/nav/style/icon)
├── scripts/                   # validate-app, retry, verify-apk, release, note
├── apps/<slug>/               # one standalone Android project per app
├── modules/<id>/              # verified reusable Java modules (+ tests)
├── modules/DEPENDENCY_REGISTRY.json
├── modules/REGISTRY.json      # FL3 versioned module registry (generated)
├── .fastlane/telemetry.json   # aggregated build telemetry (self-tuning estimates)
├── .fastlane/checkpoints/     # FL3 per-app stage checkpoints (resume support)
├── tools/fastlane/            # Fast Lane engine (FL2 + FL3 engines)
├── tools/gateway/             # JSON CLI control plane (create/status/apk)
└── .github/workflows/
    ├── opencode.yml           # Engine entry point (agent + trigger)
    └── build.yml              # CI build, verify, release pipeline
```