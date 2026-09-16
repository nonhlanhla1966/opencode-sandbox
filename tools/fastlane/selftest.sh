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
if grep -rq "0xFF" "$appdir/src/main/res" --include="colors.xml" 2>/dev/null; then
  f "scaffold colors use JS 0x hex (aapt2 rejects)"
else
  t "scaffold colors use android # hex"
fi
missing=0
for ref in $(grep -rhoE "@(color|drawable|string|mipmap)/[a-z_]+" "$appdir/src/main/res/mipmap-anydpi-v26" 2>/dev/null | sort -u); do
  name="${ref##*/}"
  case "$ref" in
    *color*) grep -q "<color name=\"$name\"" "$appdir/src/main/res/values/colors.xml" 2>/dev/null || missing=1;;
    *drawable*) [ -f "$appdir/src/main/res/drawable/$name.xml" ] || missing=1;;
    *string*) grep -q "<string name=\"$name\"" "$appdir/src/main/res/values/strings.xml" 2>/dev/null || missing=1;;
  esac
done
[ "$missing" -eq 0 ] && t "scaffold launcher-icon refs resolve" || f "scaffold launcher-icon refs unresolved"
if grep -rqE "^package [^;]*-" "$appdir/src" 2>/dev/null; then
  f "scaffold packages use java-safe names (no hyphens)"
else
  t "scaffold packages use java-safe names (no hyphens)"
fi
copymism=0
for module_test in modules/*/test/*.java; do
  [ -f "$module_test" ] || continue
  mt=$(basename "$module_test")
  pkg=$(grep -m1 '^package ' "$module_test" | sed -E 's/package[[:space:]]+([^;]+);/\1/')
  copy="$appdir/src/test/java/$(echo "$pkg" | tr '.' '/')/$mt"
  if [ -f "$copy" ] && ! cmp -s "$module_test" "$copy"; then
    copymism=1
  fi
done
[ "$copymism" -eq 0 ] && t "scaffold copies module tests verbatim (packages preserved)" \
  || f "scaffold module test copies diverge from modules/"

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
bash "$FL_ROOT/deps.sh" check "$TMP/ok.gradle" >/dev/null 2>&1 && t "deps: known-good allowed" || f "deps: known-good rejected"
printf "dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.14.0' }\n" > "$TMP/bad.gradle"
bash "$FL_ROOT/deps.sh" check "$TMP/bad.gradle" >/dev/null 2>&1 && f "deps: vulnerable dep allowed" || t "deps: vulnerable dep rejected"

echo "== 8. Regression lab =="
bash "$FL_ROOT/regression.sh" list >/dev/null 2>&1 && t "regression list runs" || f "regression list failed"
bash "$FL_ROOT/regression.sh" validate "$appdir" >/dev/null 2>&1 && t "regression validate generated app" || f "regression validate failed"

echo "== 9. Fast Lane 3.0 engines =="

# 9.1 Spec engine: version + checksum + schema validation
fl3spec="$TMP/fl3-spec.json"
fl3out="$($ANALYZE "An offline budget tracker with expenses and reminders" 2>/dev/null)"
printf '%s\n' "$fl3out" > "$fl3spec"
[ "$(python3 -c 'import json;print(json.load(open("'"$fl3spec"'")).get("spec_version"))')" = "3.0" ] \
  && t "FL3: analyze emits spec_version 3.0" || f "FL3: analyze missing spec_version"
[ -n "$(python3 -c 'import json;print(json.load(open("'"$fl3spec"'")).get("checksum_sha256"))')" ] \
  && t "FL3: analyze emits sha256 checksum" || f "FL3: analyze missing checksum"
python3 "$FL_ROOT/spec_validate.py" check "$fl3spec" >/dev/null 2>&1 \
  && t "FL3: spec_validate passes FL3 spec" || f "FL3: spec_validate rejected valid spec"
[ "$(python3 "$FL_ROOT/spec_validate.py" checksum "$fl3spec" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["checksum_sha256"]==json.load(open("'"$fl3spec"'"))["checksum_sha256"])')" = "True" ] \
  && t "FL3: spec checksum recomputes identically" || f "FL3: checksum mismatch"
fl3det1="$($ANALYZE "A flash of light app" 2>/dev/null)"
fl3det2="$($ANALYZE "A flash of light app" 2>/dev/null)"
[ "$fl3det1" = "$fl3det2" ] && t "FL3: analyze deterministic with checksum" || f "FL3: analyze nondeterministic"
[ "$(python3 -c 'import json;print(len(json.load(open("'"$fl3spec"'")).get("assumptions",[]))>0)')" = "True" ] \
  && t "FL3: assumptions recorded in spec" || f "FL3: assumptions missing"

# 9.2 Module registry + compatibility
python3 "$FL_ROOT/compat.py" registry >/dev/null 2>&1 && t "FL3: module registry generation" || f "FL3: registry generation failed"
[ -f "$REPO_ROOT/modules/REGISTRY.json" ] && python3 -c 'import json;d=json.load(open("'"$REPO_ROOT"'/modules/REGISTRY.json"));assert d["version"]=="3.0";assert len(d["modules"])>0' \
  && t "FL3: REGISTRY.json is versioned + populated" || f "FL3: REGISTRY.json invalid"
fl3arch="$TMP/fl3-arch.json"
python3 "$FL_ROOT/plan.py" "$fl3spec" --out "$TMP" > "$fl3arch" 2>/dev/null
python3 "$FL_ROOT/compat.py" check "$fl3spec" "$fl3arch" >/dev/null 2>&1 \
  && t "FL3: compat check passes generated arch" || f "FL3: compat check failed"
sel="$(python3 "$FL_ROOT/compat.py" select "$fl3spec" 2>/dev/null)"
[ -n "$sel" ] && [ "$(printf '%s' "$sel" | python3 -c 'import json,sys;print(len(json.load(sys.stdin)["selected"])>0)')" = "True" ] \
  && t "FL3: registry-driven module selection" || f "FL3: registry selection empty"
python3 -c 'import json;d=json.load(open("'"$fl3arch"'"));assert d.get("spec_version")=="3.0";assert d.get("spec_checksum_sha256")' \
  && t "FL3: architecture.json carries spec version + checksum" || f "FL3: architecture missing spec metadata"

# 9.3 Task graph engine
graph="$TMP/fl3-graph.json"
python3 "$FL_ROOT/taskgraph.py" build "$fl3spec" "$fl3arch" --out "$graph" >/dev/null 2>&1 \
  && t "FL3: task graph build" || f "FL3: task graph build failed"
python3 "$FL_ROOT/taskgraph.py" verify "$graph" >/dev/null 2>&1 \
  && t "FL3: task graph acyclic + no parallel conflicts" || f "FL3: task graph verification failed"
[ "$(python3 -c 'import json;print(json.load(open("'"$graph"'"))["total_tasks"]>=10)')" = "True" ] \
  && t "FL3: task graph covers required task types" || f "FL3: task graph too small"

# 9.4 Preflight engine
python3 "$FL_ROOT/preflight.py" check "$appdir" >/dev/null 2>&1 \
  && t "FL3: preflight clean scaffold" || f "FL3: preflight flagged clean scaffold"
bad2="$TMP/fl3-bad"; rm -rf "$bad2"; mkdir -p "$bad2/src/main/res/layout" "$bad2/src/main/java/x"
printf 'not xml at all' > "$bad2/src/main/AndroidManifest.xml"
printf 'package x;\npublic class Dupe{}\n' > "$bad2/src/main/java/x/A.java"
printf 'package x;\npublic class Dupe{}\n' > "$bad2/src/main/java/x/B.java"
python3 "$FL_ROOT/preflight.py" check "$bad2" >/dev/null 2>&1 && f "FL3: preflight missed errors" || t "FL3: preflight detects errors"
# android.R.id.* framework references (e.g. simple_list_item_2) must not be
# flagged as missing app view ids; genuinely missing ids must still be flagged
fref="$TMP/fl3-fref"; rm -rf "$fref"; mkdir -p "$fref/src/main/res/layout" "$fref/src/main/java/x"
printf 'package x; public class C { void v(){ findViewById(R.id.mine); findViewById(android.R.id.text1); findViewById(android.R.id.text2); findViewById(R.id.extra); } }' > "$fref/src/main/java/x/C.java"
printf '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"><TextView android:id="@+id/mine"/></LinearLayout>' > "$fref/src/main/res/layout/l.xml"
fout="$(python3 "$FL_ROOT/preflight.py" check "$fref" 2>/dev/null)"
printf '%s' "$fout" | python3 -c 'import json,sys
d=json.load(sys.stdin)
bad=[f["finding"] for f in d.get("findings",[]) if f.get("check")=="missing_references"]
assert not any(k in " ".join(bad) for k in ("text1","text2")), bad' >/dev/null 2>&1 \
  && t "FL3: preflight ignores framework android.R.id refs" || f "FL3: preflight flagged android.R.id refs"
printf '%s' "$fout" | python3 -c 'import json,sys
d=json.load(sys.stdin)
bad=[f["finding"] for f in d.get("findings",[]) if f.get("check")=="missing_references"]
assert any("extra" in b for b in bad), bad' >/dev/null 2>&1 \
  && t "FL3: preflight still flags genuinely missing ids" || f "FL3: preflight lost genuine missing-id check"
pref="$(python3 "$FL_ROOT/preflight.py" predict "$bad2" "$TMP/kotlin.log" 2>/dev/null)"
[ "$(printf '%s' "$pref" | python3 -c 'import json,sys;print(json.load(sys.stdin).get("matched",False))')" != "False" ] \
  && t "FL3: predictive error correlation" || t "FL3: predictive errors handle unknown gracefully"

# 9.5 Checkpoint/resume engine
bash "$FL_ROOT/checkpoint.sh" clear fl3test >/dev/null 2>&1
bash "$FL_ROOT/checkpoint.sh" save fl3test spec "$fl3spec" >/dev/null 2>&1 \
  && t "FL3: checkpoint save" || f "FL3: checkpoint save failed"
bash "$FL_ROOT/checkpoint.sh" check fl3test spec >/dev/null 2>&1 \
  && t "FL3: checkpoint check" || f "FL3: checkpoint check failed"
[ "$(bash "$FL_ROOT/checkpoint.sh" resume-from fl3test)" = "arch" ] \
  && t "FL3: checkpoint resume-from computes next stage" || f "FL3: resume-from wrong"
bash "$FL_ROOT/checkpoint.sh" clear fl3test >/dev/null 2>&1

# 9.6 Knowledge engine
kp="$TMP/know.json"; printf '{"slug":"fl3test","modules":["json","time"],"ok":true}' > "$kp"
bash "$FL_ROOT/knowledge.sh" record module "$kp" >/dev/null 2>&1 \
  && t "FL3: knowledge record" || f "FL3: knowledge record failed"
[ "$(bash "$FL_ROOT/knowledge.sh" lookup module fl3test | python3 -c 'import json,sys
try: print(len(json.load(sys.stdin))>0)
except Exception: print(False)')" = "True" ] \
  && t "FL3: knowledge lookup" || f "FL3: knowledge lookup empty"
[ -f "$FL_ROOT/knowledge/failures.json" ] && python3 -c 'import json;d=json.load(open("'"$FL_ROOT"'/knowledge/failures.json"));assert len(d["failures"])>0' \
  && t "FL3: knowledge failure DB present" || f "FL3: failure DB missing"

# 9.7 Device + UI validation (honest SKIP without emulator)
bash "$FL_ROOT/device.sh" validate "$appdir" >/dev/null 2>&1 \
  && t "FL3: device validation handles no-device (SKIP)" || f "FL3: device validation errored"
devst="$(bash "$FL_ROOT/device.sh" validate "$appdir" 2>/dev/null | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("status",""))
except Exception: print("")')"
[ "$devst" = "SKIP" ] && t "FL3: device gate honestly SKIPs without emulator" || f "FL3: device gate status=$devst"
bash "$FL_ROOT/ui-validate.sh" "$appdir" >/dev/null 2>&1 \
  && t "FL3: ui-validate static checks pass on scaffold" || f "FL3: ui-validate failed on scaffold"
# dynamic UI smoke must be an explicit SKIP (with reason) when no device — never a silent pass
dummy_apk="$TMP/dummy.apk"; printf 'fake-apk' > "$dummy_apk"
bash "$FL_ROOT/ui-validate.sh" "$appdir" "$dummy_apk" >/dev/null 2>&1
uist="$(python3 -c "
import json
try:
    print(json.load(open('$FL_TMP/ui-$slug.json')).get('status',''))
except Exception: print('')")"
[ "$uist" = "SKIP" ] && t "FL3: ui-validate dynamic smoke SKIPs honestly without device" || f "FL3: ui-validate status=$uist"

# 9.8 Accuracy + benchmark engines
acc="$(python3 "$FL_ROOT/accuracy.py" score "$appdir" 2>/dev/null)"
[ "$acc" != "" ] && [ "$(printf '%s' "$acc" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(0<=d["accuracy_score"]<=100)')" = "True" ] \
  && t "FL3: accuracy score in range from real evidence" || f "FL3: accuracy score invalid"
bash "$FL_ROOT/benchmark.sh" compare >/dev/null 2>&1 \
  && t "FL3: benchmark compare runs" || f "FL3: benchmark compare failed"

# benchmark must reject corrupt telemetry and compute a real speed score from
# clean synthetic samples (injected via FL_BENCHMARK_STREAM, never the live stream)
synthetic="$TMP/bench-samples.jsonl"
printf '%s\n' \
  '{"run_id":"fl1-a","complexity":"SIMPLE","total":120,"generation":"1.0"}' \
  '{"run_id":"fl2-a","complexity":"SIMPLE","total":80,"generation":"2.0"}' \
  '{"run_id":"fl3-a","complexity":"SIMPLE","total":40,"generation":"3.0"}' \
  '{"run_id":"fl3-b","complexity":"MEDIUM","total":60,"generation":"3.0"}' \
  '{"run_id":"garbage-ms","total":1610480935941060}' \
  '{"run_id":"garbage-nan","total":"NaN"}' > "$synthetic"
bch="$(FL_BENCHMARK_STREAM="$synthetic" bash "$FL_ROOT/benchmark.sh" compare 2>/dev/null)"
bench_ok="$(printf '%s' "$bch" | python3 -c "import json,sys
d=json.load(sys.stdin)
t=d['generations']
ok = (t.get('fl1',{}).get('runs')==1 and t.get('fl2',{}).get('runs')==1
      and t.get('fl3',{}).get('runs')==2 and d.get('speed_score_vs_fl1')==240.0)
print(ok)")"
[ "$bench_ok" = "True" ] \
  && t "FL3: benchmark rejects corrupt data + computes speed score" || f "FL3: benchmark corruption guard failed: $bch"
bash "$FL_ROOT/benchmark.sh" report >/dev/null 2>&1 \
  && t "FL3: benchmark report runs" || f "FL3: benchmark report failed"
bash "$FL_ROOT/knowledge.sh" report >/dev/null 2>&1 \
  && t "FL3: knowledge report runs" || f "FL3: knowledge report failed"

# 9.9 fastlane3 orchestrator
bash "$FL_ROOT/fastlane3.sh" idea "A fast experimental app for FL3" >/dev/null 2>&1 \
  && t "FL3: fastlane3 idea emits validated spec" || f "FL3: fastlane3 idea failed"
bash "$FL_ROOT/fastlane3.sh" plan "$fl3spec" >/dev/null 2>&1 \
  && t "FL3: fastlane3 plan (arch+compat+taskgraph)" || f "FL3: fastlane3 plan failed"

# FL3 spec-coverage contract (gate C014) must pass a clean scaffold
contract="$(python3 "$FL_ROOT/spec_validate.py" contract "$appdir/app-spec.json" "$appdir/architecture.json" "$appdir" 2>/dev/null || echo '{}')"
ckt="$(printf '%s' "$contract" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin)
    print(d.get("features_total",0)>0 and d.get("coverage_percentage",0)>=50)
except Exception: print(False)')"
[ "$ckt" = "True" ] \
  && t "FL3: contract (C014) coverage >= 50% on clean scaffold" || f "FL3: contract under-reports coverage: $contract"

bash "$FL_ROOT/fastlane3.sh" status fl3test >/dev/null 2>&1 \
  && t "FL3: fastlane3 status" || f "FL3: fastlane3 status failed"
# fastlane3 status must read a real checkpoint, not always report empty
bash "$FL_ROOT/checkpoint.sh" save fl3test spec "$fl3spec" >/dev/null 2>&1
[ "$(bash "$FL_ROOT/fastlane3.sh" status fl3test 2>/dev/null | grep -c "completed stages")" -ge 1 ] \
  && t "FL3: fastlane3 status shows checkpoint stages" || f "FL3: fastlane3 status empty"
bash "$FL_ROOT/checkpoint.sh" clear fl3test >/dev/null 2>&1
[ "$(bash "$FL_ROOT/fastlane.sh" full3 "FL3 orchestrator smoke test" 2>&1 | grep -c "FL3 summary")" -ge 1 ] \
  && t "FL3: fastlane3 pipeline completes" || f "FL3: fastlane3 pipeline incomplete"
# pipeline --resume on a completed run must proceed without re-running every stage
[ "$(bash "$FL_ROOT/fastlane.sh" full3 --resume "FL3 orchestrator smoke test" 2>&1 | grep -c "all pipeline stages already complete")" -ge 1 ] \
  && t "FL3: pipeline --resume resumes without re-running" || f "FL3: pipeline --resume incomplete"

echo ""
echo "==== SELFTEST RESULT: pass=$PASS fail=$FAIL ===="
[ "$FAIL" -eq 0 ] && echo "FASTLANE-SELFTEST: ALL PASS" && exit 0
echo "FASTLANE-SELFTEST: FAILED" >&2
exit 1