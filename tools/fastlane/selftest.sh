#!/usr/bin/env bash
# selftest.sh — Fast Lane engine self-tests (no Android SDK required).
#
# Verifies: idea analyzer, planning, scaffolding, build estimation, repair
# classifier, security scanner, dependency policy and telemetry.
#
# Usage: fastlane.sh selftest   (or)  bash tools/fastlane/selftest.sh
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

PASS=0; FAIL=0
t() { PASS=$((PASS+1)); printf "  ${C_G}PASS${C_X} %s\n" "$1"; }
f() { FAIL=$((FAIL+1)); printf "  ${C_R}FAIL${C_X} %s\n" "$1"; }
ck() { if "$@" >/dev/null 2>&1; then t "$*"; else f "$*"; fi; }

ANALYZE="python3 $FL_ROOT/analyze.py"
TMP="$FL_TMP/selftest"; mkdir -p "$TMP"

echo "== 1. App Idea Analyzer =="
for idea in \
  "A calorie counter with barcode scanning" \
  "An offline habit tracker with streaks and friendly reminders" \
  "A flashlight with one big button" \
  "A chat app with an AI assistant that streams replies"; do
  out="$($ANALYZE "$idea" 2>/dev/null)"
  [ -n "$out" ] || { f "analyze empty for: $idea"; continue; }
  python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["complexity"]["level"] in ("SIMPLE","MEDIUM","COMPLEX","EXTREME"); assert d["slug"]; assert isinstance(d["features"],list); assert d["estimate"]["build_time_seconds"] > 0' <<<"$out" && t "analyze valid spec: ${idea:0:30}" || f "analyze schema: $idea"
done

out1="$($ANALYZE "A Todo List app with offline storage" 2>/dev/null)"
out2="$($ANALYZE "A Todo List app with offline storage" 2>/dev/null)"
[ "$out1" = "$out2" ] && t "analyze is deterministic" || f "analyze nondeterministic"

echo "== 2. Planning =="
spec="$TMP/spec.json"; arch="$TMP/arch.json"
anal="$($ANALYZE "A budget tracker with expenses, offline history and dark mode" 2>/dev/null)"
printf '%s\n' "$anal" > "$spec"
python3 "$FL_ROOT/plan.py" "$spec" --out "$TMP" > "$arch" 2>/dev/null
[ -f "$arch" ] && [ -f "$TMP/PLAN.md" ] && python3 -c 'import json;d=json.load(open("'"$arch"'"));assert "modules" in d;assert d["layers"]' \
  && t "plan produced architecture.json + PLAN.md" || f "plan output missing"
python3 -c 'import json;d=json.load(open("'"$arch"'"));assert d["security"]["tls"]=="always-on certificate validation"; assert not d["security"]["allow_backup"]' \
  && t "plan security posture default" || f "plan security defaults wrong"

echo "== 3. Scaffolding =="
sdir="$TMP/app"
rm -rf "$sdir"
python3 "$FL_ROOT/scaffold.py" "$spec" "$arch" "$sdir" >/dev/null 2>&1 || { f "scaffold crashed"; exit 1; }
slug="$(printf '%s' "$anal" | python3 -c 'import json,sys;print(json.load(sys.stdin)["slug"])')"
jsafe="$(printf '%s' "$slug" | python3 -c 'import sys,re;print(re.sub(r"[^a-z0-9]","",sys.stdin.read().strip()))')"
appdir="$sdir/$slug"
for f in build.gradle settings.gradle gradlew gradle/wrapper/gradle-wrapper.jar \
          src/main/AndroidManifest.xml app-spec.json DESIGN.md release.json \
          "src/main/java/com/appfactory/$jsafe/MainActivity.java" \
          "src/test/java/com/appfactory/$jsafe/ItemTest.java"; do
  [ -e "$appdir/$f" ] && t "scaffold: $f" || f "scaffold missing: $f"
done
bash "$REPO_ROOT/scripts/validate-app.sh" "$appdir" >/dev/null 2>&1 \
  && t "scaffolded app passes validate-app" || f "validate-app fails on scaffold"
[ -e "$appdir/src/main/java/com/appfactory/modules/json/Json.java" ] \
  && t "scaffold inlined json module" || f "json module not inlined"

echo "== 4. Build-time estimation =="
est="$($FL_ROOT/estimate.sh compute "$spec" 2>/dev/null)"
python3 -c 'import json,sys;d=json.load(sys.stdin);assert int(d["per_app_seconds"])>0' <<<"$est" \
  && t "estimate emits positive seconds" || f "estimate bad: $est"
bash "$FL_ROOT/telemetry.sh" estimate COMPLEX warm 2 >/dev/null 2>&1 && t "telemetry estimate ok" || f "telemetry estimate failed"

echo "== 5. Repair classifier =="
log="$TMP/kotlin.log"; printf 'e: file.kt:12:15 error: unresolved reference: foo\n' > "$log"
[ "$(bash "$FL_ROOT/repair.sh" classify "$log" | python3 -c 'import json,sys;print("kotlin" in json.load(sys.stdin)["categories"])')" = "True" ] \
  && t "classify kotlin" || f "classify kotlin"
printf 'error: resource color/not_found (aka com.x:color/not_found) not found.\n' > "$log"
[ "$(bash "$FL_ROOT/repair.sh" classify "$log" | python3 -c 'import json,sys;print("resource" in json.load(sys.stdin)["categories"])')" = "True" ] \
  && t "classify resource" || f "classify resource"
printf 'Configuration cache: problems were found\n' > "$log"
[ "$(bash "$FL_ROOT/repair.sh" classify "$log" | python3 -c 'import json,sys;print("configcache" in json.load(sys.stdin)["categories"])')" = "True" ] \
  && t "classify configcache" || f "classify configcache"

echo "== 6. Security scanner =="
bash "$FL_ROOT/security-scan.sh" "$sdir" >/dev/null 2>&1 && t "security: clean scaffold passes" || f "security: clean scaffold flagged"
bad="$TMP/bad-app"; rm -rf "$bad"; mkdir -p "$bad/src/main/java/x" "$bad/src/main/res/values"
printf 'package x;\npublic class C {\n String herp = "ghp_01234567890123456789012345678901";\n String key = "sk-AAAAAAAAAAAAAAAAAAAA";\n}\n' > "$bad/src/main/java/x/C.java"
printf '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><uses-permission android:name="android.permission.READ_SMS"/><application android:allowBackup="true" android:usesCleartextTraffic="true"/></manifest>\n' > "$bad/src/main/AndroidManifest.xml"
bash "$FL_ROOT/security-scan.sh" "$bad" >/dev/null 2>&1 && f "security: leaks not detected" || t "security: leaks detected"

echo "== 7. Dependency policy =="
printf "plugins { id 'com.android.application' version '8.5.2' }\ndependencies { testImplementation 'junit:junit:4.13.2' }\n" > "$TMP/ok.gradle"
bash "$FL_ROOT/deps.sh" check "$TMP/ok.gradle" >/dev/null 2>&1 && t "deps: allowlist ok" || f "deps: allowlist rejects known-good"
printf "dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.14.0' }\n" > "$TMP/bad.gradle"
bash "$FL_ROOT/deps.sh" check "$TMP/bad.gradle" >/dev/null 2>&1 && f "deps: vulnerable dep allowed" || t "deps: vulnerable dep rejected"

echo "== 8. Regression lab =="
bash "$FL_ROOT/regression.sh" list >/dev/null 2>&1 && t "regression list runs" || f "regression list failed"
bash "$FL_ROOT/regression.sh" validate "$appdir" >/dev/null 2>&1 && t "regression validate generated app" || f "regression validate failed"

echo ""
echo "==== SELFTEST RESULT: pass=$PASS fail=$FAIL ===="
[ "$FAIL" -eq 0 ] && echo "FASTLANE-SELFTEST: ALL PASS" && exit 0
echo "FASTLANE-SELFTEST: FAILED" >&2
exit 1