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

The engine (`opencode` / `opencode/big-pickle` running in GitHub Actions)
executes the full pipeline automatically:

```
IDEA -> DESIGN -> CODE -> TEST -> LOCAL_VALIDATION -> GITHUB_PUSH
     -> CI_BUILD -> APK_VERIFY -> RELEASE -> DOWNLOAD_READY
```

When the build finishes you get a `DOWNLOAD_READY` comment on the issue with a
direct public APK download link.

## Pipeline stages

| Stage | Owner | What happens |
|-------|-------|--------------|
| IDEA | You | One-line `/app <description>` comment |
| DESIGN | OpenCode agent | `apps/<slug>/DESIGN.md` — screens, navigation, visual style, interactions, app-icon concept (required before code) |
| CODE | OpenCode agent | Real, buildable, lightweight Android Gradle project under `apps/<slug>/` |
| TEST | OpenCode agent + CI | JVM unit tests; failures are repaired and retried up to 3 times |
| LOCAL_VALIDATION | OpenCode agent | `scripts/validate-app.sh` structural checks |
| GITHUB_PUSH | OpenCode agent | Commit (incl. design) and push to `main` |
| CI_BUILD | GitHub Actions | `build.yml` compiles `apps/*` on `ubuntu-latest` |
| APK_VERIFY | GitHub Actions | `aapt dump badging` checks package, version, launchable activity |
| RELEASE | GitHub Actions | Idempotent rolling release `app-<slug>-latest` with APK + `SHA256SUMS` |
| DOWNLOAD_READY | GitHub Actions | Posts the public download link on the requesting issue |

The agent repairs and retries its own failures up to **3 times** (design/code/
test/validation). CI retries build, verify and release steps up to **3 times**
before failing.

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

## Layout

```
.
├── AGENTS.md                  # Operating procedure the OpenCode agent follows
├── README.md
├── docs/DESIGN_TEMPLATE.md    # Design-first template (screens/nav/style/icon)
├── scripts/                   # validate-app, retry, verify-apk, release, note
├── apps/<slug>/               # one standalone Android project per app
├── tools/gateway/             # JSON CLI control plane (create/status/apk)
└── .github/workflows/
    ├── opencode.yml           # Engine entry point (agent + trigger)
    └── build.yml              # CI build, verify, release pipeline
```