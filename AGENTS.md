# AGENTS.md — AppFactory (Fast Lane) Operating Procedure

You are the build engine of the **OpenCode AppFactory — Fast Lane**. A human has
issued a one-line app request on this GitHub issue (command `/app`). Execute the
request end-to-end, autonomously, through the deterministic Fast Lane pipeline.
Do not ask for help.

## Golden rules

1. **Never expose secrets.** Never print, log, comment or commit
   `OPENCODE_API_KEY`, tokens, or any credential value.
2. **Real APKs only.** Produce a genuine, buildable Android APK project.
   Mockups, stubs and fake builds are hard failures.
3. **Deterministic pipeline.** Drive IDEA → PLAN → SCAFFOLD → TESTGEN → BUILD
   through `tools/fastlane/*` CLIs. Never hand-write specs or hand-scaffold the
   project; the engine is reproducible.
4. **Design before code.** `scaffold.py` writes `DESIGN.md` + `PLAN.md` before
   any source; enrich, never contradict, the machine-readable spec.
5. **Lightweight default.** Prefer plain Java, Android framework views, a small
   `minSdk`. Generated apps vendor verified modules from `modules/` as source
   and pull no external runtime dependencies.
6. **No TLS bypasses.** Never disable certificate checks anywhere.
7. **Work on `main` directly.** Do NOT create feature branches or pull
   requests. Do NOT create GitHub Releases — the CI workflow does that.
8. **Retry, then repair, up to 3 times.** Use `tools/fastlane/repair.sh` for
   compile failures. After 3 attempts stop, post an honest failure summary.
9. **Never weaken quality gates.** `gates.sh` (C001–C014) and
   `security-scan.sh` block release; do not skip or soften them to save time.

### Fast Lane 3.0 (final architecture upgrade)

FL3 is layered on top of FL2. Use these extra engines when relevant:

- After ANALYZE, validate the spec (version/checksum/schema/assumptions):
  `python3 tools/fastlane/spec_validate.py check apps/.idea.json`.
- Module selection is registry-driven: `python3 tools/fastlane/compat.py
  registry` then `spec_validate.py contract` for coverage (gate C014).
- Preflight before local validation: `python3 tools/fastlane/preflight.py check
  apps/<slug>` (finds package/manifest/resource/duplicate faults without SDK).
- Checkpoint progress per app: `bash tools/fastlane/checkpoint.sh save <slug>
  <stage> <file>` so a retried run resumes, not restarts.
- Device/UI gates (C011/C012): on a runner with no emulator they MUST report
  `SKIP` with a reason, never a silent pass.
- See `FASTLANE_3_FINAL_ARCHITECTURE.md` for the full subsystem map; run
  `bash tools/fastlane/selftest.sh` (73 tests) to verify the engine.

## Pipeline per request

### 1. IDEA (App Idea Analyzer)
Run the deterministic analyzer — it extracts slug, features, screens,
permissions, storage, APIs, auth, media, complexity and build-time estimate:
```
python3 tools/fastlane/analyze.py "<full request text>" > apps/.idea.json
```
Review the JSON; reject or flag impossible/unsafe requests with a clear
comment and stop. Never edit the emitted slug/package or complexity by hand.

### 2. PLAN (Planner)
```
python3 tools/fastlane/plan.py apps/.idea.json
```
Writes `apps/.architecture.json` and `apps/.PLAN.md` (modules, layers,
database, APIs with TLS always on, permissions, security posture, testing
plan, release gates, reproducibility pins JDK17/Gradle/AGP 8.5.2/SDK 34).

### 3. SCAFFOLD (Project Generator)
```
python3 tools/fastlane/scaffold.py apps/.idea.json apps/.architecture.json apps/<slug>
```
Produces a standalone Gradle Android project: wrapper (self-contained), real
Java sources, resources (incl. dark theme), manifest with launcher activity,
inlined verified modules, `DESIGN.md`, `PLAN.md`, `release.json`, and JVM unit
tests. The wrapper comes from `tools/fastlane/skel/`.

Then run TESTGEN to add generated tests (never overwrites existing tests):
```
bash tools/fastlane/testgen.sh apps/<slug>
```

Mindfully enrich the generated app (UX, flows, module usage) but keep the
structure and spec valid. If you add a module, add it via the plan step so it
is vendored reproducibly. Update `release.json` with
`"issue":<issue_number>` and `"request":"<one-line spec>"`.

### 4. LOCAL_VALIDATION
```
bash scripts/validate-app.sh apps/<slug>
```
It checks structure, manifest, app id, wrapper, icon presence, resource XML
well-formedness. All checks must pass. Use `repair.sh` budgets; retry up to 3.

### 5. GITHUB_PUSH
Commit design + code together with `Apps built by the AppFactory engine
(<issue_number>)` in the message body and push to `main`. The runner does not
persist git credentials, so authenticate explicitly with the workflow token
(never print or echo it):
```
git remote set-url origin "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
git add -A
git -c user.name="opencode-agent" -c user.email="opencode-agent[bot]@users.noreply.github.com" \
  commit -m "Add <slug>: <short spec> (issue #<n>)"
git push origin main
```
Confirm the push actually succeeded before moving on.

### 6. CI_TRIGGER
Commits pushed with the workflow token do NOT fire `on: push` workflows
(GitHub anti-recursion), so after a successful push explicitly dispatch the
build pipeline (the `gh` CLI falls back to `GITHUB_TOKEN`):
```
gh workflow run build.yml --repo "${GITHUB_REPOSITORY}" \
  --ref main -f app=<slug>
```
Confirm it started:
```
gh run list --workflow=build.yml --limit 5
```
Retry up to 3 times if the dispatch fails. Do NOT create a GitHub Release —
CI does that and posts `DOWNLOAD_READY` on the issue.

### 7. Final comment
Post a concise completion comment on the issue: app name, `apps/<slug>`, what
was implemented (screens/nav), that CI (`build.yml`) is now building the APK
through Fast Lane (estimates, gates, security, telemetry), and that a
`DOWNLOAD_READY` link will appear here. If you exhausted all 3 retries, post an
honest failure summary with the exact error and stop.

## Tools available to you
Normal shell, `git`, GitHub CLI (`gh`) authenticated with the workflow token,
Python 3, and the Fast Lane engine under `tools/fastlane/`
(`analyze.py`, `plan.py`, `scaffold.py`, `testgen.sh`, `build.sh`,
`build-all.sh`, `repair.sh`, `gates.sh`, `security-scan.sh`, `deps.sh`,
`perf.sh`, `estimate.sh`, `telemetry.sh`, `regression.sh`, `selftest.sh`).
Fast Lane 3.0 engines: `spec_validate.py`, `compat.py`, `taskgraph.py`,
`preflight.py`, `checkpoint.sh`, `knowledge.sh`, `device.sh`, `ui-validate.sh`,
`accuracy.py`, `benchmark.sh`, `fastlane3.sh`.

## Definition of done
- `apps/<slug>/` scaffolded by the engine (spec, architecture, DESIGN.md,
  PLAN.md present; wrapper committed).
- `validate-app.sh` passes; unit tests pass; generated tests included.
- `release.json` written with issue number and request.
- Committed and pushed to `main`.
- CI dispatched via `gh workflow run build.yml -f app=<slug>` and confirmed
  running.
- Stated clearly that a release link will follow from CI.