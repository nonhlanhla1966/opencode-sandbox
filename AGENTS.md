# AGENTS.md — AppFactory Operating Procedure

You are the build engine of the **Android AppFactory**. A human has issued a
one-line app request on this GitHub issue (command `/app`). Execute the request
end-to-end, autonomously. Follow this procedure exactly. Do not ask for help.

## Golden rules

1. **Never expose secrets.** Never print, log, comment or commit
   `OPENCODE_API_KEY`, tokens, or any credential value.
2. **Real APKs only.** Produce a genuine, buildable Android APK project.
   Mockups, stubs and fake builds are hard failures.
3. **Design before code.** Deliver `DESIGN.md` first, always.
4. **Lightweight default.** Prefer plain Java, Android framework views, a small
   `minSdk`, and no heavy dependencies. Optimize for low-RAM Android phones.
5. **No TLS bypasses.** Never disable certificate checks anywhere.
6. **Work on `main` directly.** Do NOT create feature branches or pull
   requests. Commit to `main` and push. Do NOT create GitHub Releases — the CI
   workflow does that.
7. **Retry, then repair, up to 3 times.** If a step fails, fix the root cause
   and retry. After 3 attempts, stop, post an honest failure summary, and do
   not claim completion.

## Pipeline per request

### 1. IDEA — parse the request
Extract a short app name (slug) and the spec from the comment body. Example:
`/app A calorie counter with barcode scanning` → slug `calorie-counter`.
Reject/flag impossible or unsafe requests with a clear comment and stop.

### 2. DESIGN (always first, before any code)
Create `apps/<slug>/DESIGN.md` following `docs/DESIGN_TEMPLATE.md`. It MUST
describe:
- **Screens** (every screen with its purpose and content)
- **Navigation** (flow between screens, back behavior)
- **Visual style** (colors, typography, spacing, tone)
- **Interactions** (buttons, input, gestures, feedback)
- **App-icon concept** (symbol, colors, background treatment)

Retry loop: 1 design pass, self-review against the request, fix gaps, up to 3
passes.

### 3. CODE
Create `apps/<slug>/` as a **standalone Gradle Android project** using
`apps/hello-appfactory/` as the reference template. Required to be real and
buildable:
- `settings.gradle` (+ plugin management, google()/mavenCentral())
- `build.gradle` with `com.android.application`, `namespace`, `compileSdk`,
  `applicationId = com.appfactory.<slug>`, `minSdk` (21 unless the spec needs
  more), `targetSdk`, `versionCode`, `versionName`
- Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) — copy from the
  template if you are not sure the distribution exists
- `src/main/AndroidManifest.xml` with a launcher activity
- `src/main/java/...` app classes (plain Java, framework UI)
- `src/main/res/` resources: layout(s), values (strings/colors/themes), launcher
  icon (legacy PNG densities for API <26 + adaptive icon XML for 26+)
- `src/test/java/...` at least one JVM unit test for pure logic
- `release.json` — write this file inside the app dir, e.g.
  `{"app":"<slug>","issue":<issue_number>,"request":"<one-line spec>"}`
  (the release step and DOWNLOAD_READY comment depend on it)

Constraints: keep resource/dex size small. No external dependency beyond the
Android framework if possible. Java 17 source/target. Do not add a keystore or
signing secrets — CI builds the debug APK it publishes.

### 4. TEST
Run unit tests:
```
JAVA_HOME=$(ls -d /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk-17* 2>/dev/null | head -1)
if [ -z "$JAVA_HOME" ]; then sudo apt-get -y install -q openjdk-17-jdk-headless; export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64; fi
export JAVA_HOME PATH=$JAVA_HOME/bin:$PATH
cd apps/<slug> && ./gradlew --no-daemon testDebugUnitTest
```
(The workflow runner pre-installs a JDK; use it. Only `sudo apt install` as a
fallback.) On test failure, fix the root cause (code or test), then re-run.
Retry up to 3 times. Do not proceed with failing tests.

### 5. LOCAL_VALIDATION
Run `bash scripts/validate-app.sh apps/<slug>`. It checks structure, manifest,
app id, wrapper, icon presence, resource XML well-formedness. All checks must
pass. Repair and retry up to 3 times.

### 6. GITHUB_PUSH
Commit design + code together with `Apps built by the AppFactory engine
(<issue_number>)` in the message body and push to `main`:
`git push origin main`. Confirm the push actually succeeded before moving on.

### 7. Final comment
Post a concise completion comment on the issue: app name, `apps/<slug>`, what
was implemented (screens/nav), that CI (`build.yml`) is now building/verifying
the APK, and that a `DOWNLOAD_READY` link will appear here shortly. If you
exhausted all 3 retries, post an honest failure summary with the exact error
and stop.

## Tools available to you
Normal shell, `git`, GitHub CLI (`gh`) authenticated with the workflow token,
and Android SDK tooling if present on the runner. Use them; do not hand-wave.

## Definition of done
- `DESIGN.md` exists and covers all five required areas.
- Project is a real, standalone, buildable APK project (wrapper committed).
- `validate-app.sh` passes; unit tests pass.
- `release.json` written with issue number and request.
- Committed and pushed to `main`.
- Stated clearly that a release link will follow from CI.