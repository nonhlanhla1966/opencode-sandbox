# FAST LANE 3.0 — FINAL AUTONOMOUS APPFACTORY ARCHITECTURE

**Repository:** `nonhlanhla1966/opencode-sandbox`
**Spec:** Fast Lane 3.0 (Final Autonomous AppFactory Architecture)
**Spec version in emitted artifacts:** `spec_version: "3.0"`
**Status:** Implemented on top of the working Fast Lane 2.x system.

> Fast Lane 3.0 is an **upgrade layered on top of** the existing Fast Lane 1.x/2.x
> AppFactory. No existing applications were deleted, no Fast Lane 1.x/2.x
> functionality was removed, and no working system was rewritten. Future work
> should be normal maintenance/minor releases rather than another complete
> AppFactory rewrite.

---

## 1. Primary objective

Turn a single plain-language app idea into a verified, installable Android APK
**autonomously and deterministically**, with machine-checkable quality gates,
checkpoint/resume recovery, accuracy + speed scoring, and honest reporting
(device/UI checks are `SKIP`ped with a reason when no device exists — never
silently passed).

The Canonical Pipeline (unchanged from Fast Lane, now FL3-hardened):

```
IDEA → SPEC → ARCHITECTURE → MODULE REGISTRY → TASK GRAPH → GENERATE →
PREFLIGHT → BUILD (repair loop) → TEST → DEVICE/UI → SECURITY → PERF → DEPS →
GATES C001–C014 → RELEASE → DOWNLOAD_READY
```

---

## 2. Subsystem map (20 subsystems → concrete engines)

Each subsystem has a clear responsibility and a single owning engine file.
Fast Lane 3.0 deliberately **avoids a giant monolithic script**; each engine has
a well-defined CLI and emits JSON.

| # | Subsystem | Engine | Responsibility |
|---|-----------|--------|----------------|
| 1 | Idea Engine | `tools/fastlane/analyze.py` | Extract purpose, features, screens, navigation, data, permissions, networking, auth, notifications, media, offline, security, performance from a plain-language idea. No unnecessary questions; missing info → reasonable defaults **recorded as assumptions** in the spec. Deterministic. |
| 2 | Specification Engine | `tools/fastlane/spec_validate.py` (+ `analyze.py` emit) | Canonical `app-spec.json` schema, **schema validation** before generation, `spec_version: "3.0"`, **sha256 checksum** (spec changes are detectable), assumption recording, feature→module→test→gate **contract/coverage** mapping (`spec_validate.py contract`). |
| 3 | Architecture Engine | `tools/fastlane/plan.py` | Deterministic `architecture.json` + `PLAN.md`: modules, layers, db schema, apis, security, permissions, ui, testing, release, reproducible pins. Carries `spec_version` + `spec_checksum_sha256` through. |
| 4 | Dependency Intelligence | `tools/fastlane/deps.sh` (FL2) + `compat.py check` | Dependency policy enforcement (allow-list, no vulnerable versions, no insecure protocols). FL3 adds module-level compatibility checks against project pins (JDK17/Gradle8.7/AGP8.5.2/SDK34). |
| 5 | Module Registry | `tools/fastlane/compat.py registry` + `modules/REGISTRY.json` | Versioned registry (`version: "3.0"`), generated from per-module `module.json` (license, security status, checksums, file counts, compatibility). `compat.py select` performs **registry-driven module selection** replacing keyword-only selection. |
| 6 | Task Graph Engine | `tools/fastlane/taskgraph.py` | Builds a deterministic task DAG (scaffold/resource/manifest/database/ui/nav/api/settings/tests/docs/preflight/build/security/release). **Topological sort**, grouped fan-out with **safe parallelism** (parallel tasks never share output paths), cycle detection, `verify` command. |
| 7 | Generation Engine | `tools/fastlane/scaffold.py` (FL2) | Deterministically generates `apps/<slug>/` from spec+architecture incl. `DESIGN.md`, `PLAN.md`, `release.json`, vendored verified module code + tests. Unchanged; FL3 drives it through the task graph. |
| 8 | Preflight Engine | `tools/fastlane/preflight.py check` | Machines-report pre-build faults: package/path mismatch (AGP8 namespace aware, module subpackages allowed), manifest XML validity, duplicate classes, duplicate resources, unresolved view-id references, gradle sanity, and Java brace hygiene (string/comment aware). Runs before the expensive build. |
| 9 | Build Engine | `tools/fastlane/build.sh` (FL2) + `fastlane3.sh build` | Cached, incremental, parallel-capable Gradle assembleDebug with live estimate banner, repair loop, stage telemetry. FL3 adds the failing-fast preflight hook and records outcome + accuracy. |
| 10 | Test Engine | `tools/fastlane/testgen.sh`, `build.sh testDebugUnitTest` (FL2), `spec_validate.py contract` | Unit tests generated + run per app; junit XML drives `C003_UNIT_TESTS` and the accuracy score. FL3 adds feature→test-mapping evidence for coverage. |
| 11 | Repair Engine | `tools/fastlane/repair.sh` + `fixes/` (FL2) + `preflight.py predict` | Classify failures, apply budgeted deterministic patches (≤3 repairs). FL3 adds **predictive error correlation** against `knowledge/failures.json` recognized signatures with confidence + suggested safe patches. |
| 12 | Security Engine | `tools/fastlane/security-scan.sh` (FL2, gate `C008`) | No secrets/keys in source, no overly-broad permissions, TLS always validated, no cleartext, exported components limited. Confidence: report artifact feeds accuracy (`critical`/`high` counts). |
| 13 | Performance Engine | `tools/fastlane/perf.sh` | APK size (< 8 MB debug ceiling), dex method-count heuristic, `budget_ok` field → gate `C013_PERFORMANCE`. |
| 14 | Device Validation Engine | `tools/fastlane/device.sh` | Installs + launches the APK on a real device/emulator and probes focus. **No device → honest `SKIP` with reason** (never silently passes). Gate `C011_DEVICE_TEST`. |
| 15 | UI Validation Engine | `tools/fastlane/ui-validate.sh` | Static UI checks (layout XML well-formedness, id-reference completeness, preflight reuse) always run; dynamic screenshot/smoke checks `SKIP` with reason when no device. Gate `C012_UI_TEST`. |
| 16 | Release Engine | CI workflow (`.github/workflows/build.yml`) + `scripts/release.sh` (FL2) | Build-all, gates, release artifacts, DOWNLOAD_READY. FL3 gates C011–C014 plug into the same gate list; releases remain CI-drivered, deterministic. |
| 17 | Knowledge/Cache Engine | `tools/fastlane/knowledge.sh` + `tools/fastlane/knowledge/failures.json` | Persistent cross-run knowledge (successful module integrations, build history) and the predictive failure DB. All under `.fastlane/` (never secrets). |
| 18 | Telemetry Engine | `tools/fastlane/telemetry.sh` (FL2, extended) | Stage durations, per-app run records (now include `generation: "3.0"` + `accuracy_score`), aggregate for the estimator. `stage-stop` is now tolerant of missing stage files. |
| 19 | Regression Lab | `tools/fastlane/regression.sh` (FL2) + `selftest.sh` + `benchmark.sh` | Regression list/validate, 73-test engine selftest (no Android SDK required), and the FL1/FL2/FL3 **benchmark** from real run telemetry. |
| 20 | Recovery/Checkpoint Engine | `tools/fastlane/checkpoint.sh` | Per-app stage checkpoints in `.fastlane/checkpoints/<slug>/`; `save`/`check`/`resume-from`/`state`/`clear`. Failed runs **resume from the last completed stage** instead of restarting. |

---

## 3. Idea Engine (+ Specification details)

Input: a single plain-language app idea.

Extracted automatically (with reasonable, recorded defaults when absent):

- app purpose (summary) + users/roles (via archetype + assumptions)
- features, screens, navigation
- data (entities), storage (sqlite / prefs)
- networking/APIs (openai-compatible, weather, maps, generic rest)
- authentication (none / cloud account / api-key)
- permissions (camera, internet, fine location, notifications, …)
- notifications, media (camera/audio/video/image/pdf)
- offline requirements, security/crypto requirements
- performance/estimate (deterministic complexity score 0–100 + build band)

Every missing field is recorded under `spec.assumptions[]` with a reason —
the requirement "If information is missing, make reasonable defaults and
record assumptions in the App Specification" is enforced by both emit side
(`analyze.py`) and validation side (`spec_validate.py detect_assumptions`).

## 4. App Specification (canonical + validated)

Every emitted spec is validated **before generation**:

```
spec_validate.py check <app-spec.json>   → { valid, spec_version, errors, warnings, assumptions, checksum }
spec_validate.py checksum <app-spec.json> → deterministic sha256
spec_validate.py contract <spec.json> <arch.json> [app-dir] → coverage_fraction
```

- `spec_version: "3.0"` is mandatory; `check` errors on any missing required key
  (`name, slug, application_id, summary, features, screens, navigation, data,
  modules, archetype, complexity, estimate, spec_version, assumptions`).
- The sha256 checksum is computed over the **canonical sorted spec** (metadata
  excluded) so a spec modified after generation is detected.
- `analysis-emit` and `validate` share the same deterministic checksum function
  (selftest verifies recomputation equality).
- `contract` maps every feature → modules → source files → test files → gate
  evidence and reports `coverage_percentage` (drives gate `C014_SPEC_COVERAGE`,
  threshold 50%).

## 5. Module Registry + Dependency Intelligence

- `compat.py registry` regenerates `modules/REGISTRY.json` from every
  `modules/<id>/module.json`, adding `checksums` (source+tests), `file_counts`,
  `compatibility` pins, and preserving license/security/limitation metadata.
- `compat.py check <spec> <arch>` verifies every module against the pins and
  rejects modules missing from the registry or requiring API > compileSdk.
- `compat.py select <spec>` performs **registry-driven selection** (core +
  feature-derived + spec-declared) — deterministic, replaces keyword-only logic.
- `deps.sh` (FL2) continues to enforce the dependency policy at build time.

## 6. Task Graph Engine

- `taskgraph.py build <spec> <arch> --out graph.json` creates the DAG and a
  **topological order**; `taskgraph.py verify` rejects cycles and **parallel
  output-path conflicts** (parallel tasks never write the same path).
- Report: `{total_tasks, max_parallel, has_cycle, groups[]{level, tasks, parallel_safe}}`.
- The `apps/<slug>/src/` tree is the shared output domain; fan-out is safe by
  construction (verified in selftest).

## 7–9. Generation → Preflight → Build

Generation stays `scaffold.py` (FL2, deterministic). Before compile:

```
preflight.py check <app-dir>
```

catches the classes of fault that cost a CI cycle to discover, all without an
Android SDK. On pass, the FL2 build engine assembles + tests + gates; the FL3
gates run inside the same `gates.sh` pass.

## 10–13. Test, Repair, Security, Performance

JUnit via `testDebugUnitTest` (test-results XML), repair budget ≤ 3 with
classifier + predictive correlation (`preflight.py predict`), security scan
(`C008`, no secrets/TLS bypass/over-broad perms), perf (`budget_ok`, APK size +
dex methods) feeding C013.

## 14–15. Device + UI validation — honest SKIP discipline

- `device.sh`: installs + launches on real devices only. Report is `SKIP` with
  an explicit reason (no adb, no connected device, no apk) otherwise.
- `ui-validate.sh`: static layout/id checks always execute; dynamic
  screenshot/smoke checks are `SKIP`-with-reason without a device.
- The gates C011/C012 consume the reports; a missing/`SKIP` report can never be
  masked as a pass.

## 16. Release + gates C001–C014

Gate list now:

| Gate | Check |
|------|-------|
| C001 | Structural (`scripts/validate-app.sh`) |
| C002 | Compile (APK present/non-empty) |
| C003 | Unit tests (junit green) |
| C004 | Android lint |
| C005 | Resource (aapt2 compile) |
| C006 | Manifest structure |
| C007 | Dependency policy |
| C008 | Security scan |
| C009 | APK verification (badging + apksigner) |
| C010 | SHA-256 generation |
| C011 | **Device validation** (`device.sh`) — SKIP w/ reason if no device |
| C012 | **UI validation** (`ui-validate.sh`) |
| C013 | **Performance budget** (`perf.sh budget_ok`) |
| C014 | **Spec coverage** (`spec_validate.py contract` ≥ 50%) |

A release is only reached when every applicable gate passes; any FAIL blocks.

## 17–18. Knowledge/Cache + Telemetry

- `knowledge.sh record/lookup/report` persists successful integrations + build
  history; `knowledge/failures.json` is the predictive error DB (signature →
  category → safe patch → confidence).
- Telemetry records now tag `generation` and (when computed) `accuracy_score`
  so the benchmark can separate FL1/FL2/FL3 runs.

## 19. Regression Lab + Accuracy + Speed scores

- `selftest.sh`: **73 tests** (34 FL2 baseline + 39 FL3), no Android SDK needed.
- `benchmark.sh compare|report`: reads `.fastlane/tmp/runs.jsonl`, splits by
  generation, computes per-gen median/mean totals and by-complexity medians, and
  the **Speed Score** (FL3 median vs FL1 baseline).
- `accuracy.py score|score-all`: **Accuracy Score 0–100 from real artifacts
  only** — APK presence/checksum (20%), unit tests (20%), gates passed (25%),
  spec coverage (15%), security scan (10%), dependency policy (10%). No
  guesses; missing artifacts lower the score or act as a neutral signal and are
  itemized in `breakdown`.

## 20. Recovery/Checkpoint Engine

`checkpoint.sh` keeps `.fastlane/checkpoints/<slug>/<stage>.json` for stages
`spec arch modules taskgraph generate preflight build test security release`.
The FL3 orchestrator (`fastlane3.sh pipeline`) consults `resume-from` so an
interrupted run picks up where it left off: `pipeline` starts fresh by default
(clears the slug's checkpoints), and `pipeline --resume` continues from the last
completed stage instead of re-running every stage. A completed pipeline re-entered
with `--resume` prints its checkpoint state and stops.

## 21. Assistant Layer (AI Chat + Vision + Imagegen + Web Search + File Understanding + AI→App Builder Bridge)

`tools/assistant/` adds a full conversational AI layer on top of FL3, providing
chat, vision, image generation, web search, document understanding, and a
deterministic bridge from AI conversation to the FL3 AppFactory pipeline.

### Core Engines (all deterministic in mock/offline mode)

| Module | Purpose |
|--------|---------|
| `common.py` | State dirs (`<repo>/.fastlane/assistant/{chat,builds}`), JSON IO, `sanitize_secrets` |
| `transport.py` | `assert_https`, `post_json`, `get_text`, SSE streaming; errors always redacted |
| `providers.py` | `ProviderRegistry`, `MockProvider`, `BIG_PICKLE_PRESET`; env-mode (mock\|real, CI default mock) |
| `chat.py` | Conversation CRUD, streaming send, retry, regenerate, edit+resend, stop support |
| `vision.py` | Image validation, calibrated uncertainty response; never guesses without a provider |
| `imagegen.py` | Never fabricates success; clean decline without a configured provider |
| `websearch.py` | HTTPS-only results, `source`-aware, graceful decline without provider |
| `files.py` | Text extraction, binary rejection, secrets redacted from all output |
| `policy.py` | Deterministic content-policy gate (CSAM/refusal/safety-bypass blocked; 18+ flagged) |
| `router.py` | Intent router — CHAT/VISION/WEB/IMAGE_GENERATION/FILE_ANALYSIS/APP_BUILDER |
| `builder.py` | AI→AppFactory bridge — `analyze.py` → `plan.py` → `scaffold.py` via `_run()` |
| `assistant.py` | Single CLI entrypoint for all subcommands |

### AI→App Factory Bridge (`builder.py`)

- **Create**: `build_app(idea, staging=<staging>)` runs analyze+plan+scaffold,
  writes `app-spec.json` with `checksum_sha256`, records `history.jsonl`,
  isolates staging from real `apps/`.
- **Modify**: `build_app(idea, modify=<slug>, staging=<staging>)` loads the
  existing spec, merges features/screens/modules, records changes, re-emits
  a new spec+checksum.
- **Cloud Release**: optional `_trigger_cloud_release(slug)` dispatches
  `build.yml` via `gh workflow run` when `GIT_REMOTE` + `GH_TOKEN` are set.

### Golden Rules Preserved

- **Never fake capabilities**: mock mode emits explicit `MOCK_CHAT:`
  prefix; vision returns calibrated uncertainty; imagegen never writes a file.
- **No secrets in output**: `sanitize_secrets` redacts Bearer/sk-/ghp_/AKIA
  patterns; transport errors are sanitized.
- **HTTPS-only**: `assert_https` rejects `http://` endpoints.
- **Never modify real `apps/` in staging**: staging dir isolates all
  intermediate artifacts.
- **CI=mock**: `CI=true` forces mock mode automatically.

### Verification

`python3 tools/assistant/assistantselftest.py` — **78/78 PASS**:

- §1–2: Provider config, transport, redaction
- §3: Chat CRUD, deterministic mock, retry/regenerate/edit, stop support
- §4–5: Vision uncertainty, imagegen never-fake
- §6–7: Web search source-aware, file extraction+redaction
- §8–9: Router correctness, content policy gate
- §10: Builder create/modify, staging isolation, history+checksum
- §11: Models endpoint mock safety

---

## Orchestrator

`tools/fastlane/fastlane3.sh` is the FL3 entry point (`fastlane.sh full3 "<idea>"`):

```
idea '<idea>'        → FL3 spec emitted + validated (spec_version 3.0, checksum, assumptions)
plan  <spec.json>    → registry refresh → plan → compat → registry select → task graph verify
build <app-dir>      → FL2 build (+ preflight) + FL3 gates + accuracy
pipeline '<idea>'    → checkpointed end-to-end pipeline (CI does the real gradle build)
status <slug>        → checkpoint + next-stage view
```

## Verification

- `bash tools/fastlane/selftest.sh` → **75/75 PASS**, covering
  every FL3 engine, the honest device `SKIP`, the honest UI **`SKIP`** when the
  dynamic smoke check has no device, the C014 spec-coverage contract on a clean
  scaffold, the benchmark's corrupt-telemetry guard, registry/compat/taskgraph/
  preflight correctness, checkpoint `--resume`, and the orchestrator pipeline.
- `python3 tools/assistant/assistantselftest.py` → **78/78 PASS**:
  provider/transport, chat CRUD+controls, vision/imagegen/web/files,
  router+policy, builder create/modify, models safety.
- CI `.github/workflows/build.yml` runs selftest + `build-all.sh` (apps rebuild
  with the shared Gradle/SDK cache → the live benchmark sample set).
- Fast Lane golden rules preserved: no secrets, real APKs only, deterministic
  pipeline via CLIs, no TLS bypasses, repair ≤ 3x, gates never weakened.

---

_End of Fast Lane 3.0 Final Autonomous AppFactory Architecture document._