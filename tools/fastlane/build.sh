#!/usr/bin/env bash
# build.sh — Fast Lane per-app build orchestrator (4/5/6/18/19).
#
# Cached, incremental, parallel-capable, with a live build-time estimate banner
# and stage telemetry. Runs: estimate -> compile (repair loop) -> unit tests
# (repair loop) -> lint -> quality gates -> security -> perf -> verify.
#
# Usage: build.sh <app-dir> [--assemble-only]
# Env:  JAVA_HOME (optional), GRADLE_USER_HOME, FL_SKIP_LINT, FL_SKIP_TESTS
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

app_dir="${1:?usage: build.sh <app-dir> [--assemble-only]}"
mode="${2:-full}"
slug="$(basename "$app_dir")"

[ -d "$app_dir" ] || die "app dir missing: $app_dir"
app_dir="$(cd "$app_dir" && pwd)"   # absolute, so repair/subprocesses work from any CWD

# ---- resolve java/gradle ---------------------------------------------------
require_cmd java
if [ -z "${JAVA_HOME:-}" ]; then
  export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
fi
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start planning

# ---- read complexity ---------------------------------------------------------
cd "$app_dir"

complexity="MEDIUM"; est_sec=120
if [ -f app-spec.json ]; then
  complexity="$(json_get "$(cat app-spec.json)" '.complexity.level')"
  est_sec="$(json_get "$(cat app-spec.json)" '.estimate.build_time_seconds')"
  [ -n "$complexity" ] || complexity="MEDIUM"
  [ -n "$est_sec" ] || est_sec=120
fi
est_sec="$(telemetry_adjust "${complexity,,}" "$est_sec")"

start_ms="$(now_ms)"
leader "Fast Lane build — $slug"
printf "  Complexity:      ${C_B}%s${C_X}\n" "$complexity"
printf "  Estimated build: ${C_B}~%s${C_X} (warm cache)\n" "$(human_dur "$est_sec")"
printf "  Elapsed:         0s | Remaining: ~%s\n" "$(human_dur "$est_sec")"

# ---- helper ------------------------------------------------------------------
eta() {
  local elapsed=$(( ($(now_ms) - start_ms) / 1000 ))
  local remain=$(( est_sec - elapsed ))
  [ "$remain" -lt 0 ] && remain=0
  printf "  Elapsed: %s | Remaining: ~%s | Current stage: %s\n" \
    "$(human_dur "$elapsed")" "$(human_dur "$remain")" "$1"
}

budget=${FL_REPAIR_BUDGET:-3}
GRADLE_ARGS=(--no-daemon --stacktrace)
if [ -f build/fastlane.disable-config-cache ]; then
  FL_CONFIG_CACHE=0
fi
if [ "${FL_CONFIG_CACHE:-1}" = "1" ]; then
  GRADLE_ARGS+=(--configuration-cache)
fi

run_gradle() {
  local task="$1"; shift
  local log="$FL_TMP/$slug-$task.log"
  if bash "$REPO_ROOT/tools/fastlane/repair.sh" "$budget" "$app_dir" "$log" -- \
     ./gradlew "${GRADLE_ARGS[@]}" "$task" "$@" >/dev/null 2>&1; then
    return 0
  fi
  err "gradle $task failed — tail of $log:"
  tail -n 40 "$log" >&2 2>/dev/null || true
  err "gradle $task failure causes:"
  grep -nE "What went wrong|Execution failed|There (were|was)|FAILED [0-9]+|error:|Caused by:|> Task .*FAILED|testDebugUnitTest FAILED|com\.appfactory\." "$log" \
    | tail -n 40 >&2 2>/dev/null || true
  return 1
}

# ---- compile (assembleDebug) --------------------------------------------------
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop planning

# ---- Fast Lane 3.0 preflight (fail fast before expensive compile) -------------
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start preflight
if [ -f "$FL_ROOT/preflight.py" ]; then
  if python3 "$FL_ROOT/preflight.py" check "$app_dir" >/dev/null 2>&1; then
    ok "  preflight: static checks clean"
  else
    warn "preflight found issues; they will surface in the build (see preflight report)"
  fi
fi
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop preflight

"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start compiling
eta COMPILING
if run_gradle assembleDebug; then
  ok "  compiled in $(( ($(now_ms) - start_ms) / 1000 ))s"
else
  warn "compile failed; classification + repair budgets above"
  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop compiling
  err "BUILD_FAILED: $slug compile did not pass"
  exit 1
fi
apk="$(bash "$REPO_ROOT/scripts/find-apk.sh" "$app_dir")"
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop compiling

# ---- unit tests (full mode only) ------------------------------------------------
if [ "$mode" = "full" ] && [ "${FL_SKIP_TESTS:-0}" != "1" ]; then
  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start testing
  eta TESTING
  if run_gradle testDebugUnitTest; then
    ok "  unit tests passed"
  else
    exit 1
  fi
  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop testing
fi

# ---- lint ----------------------------------------------------------------------
if [ "$mode" = "full" ] && [ "${FL_SKIP_LINT:-0}" != "1" ] && command -v aapt2 >/dev/null 2>&1; then
  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start linting
  eta LINT
  run_gradle lintDebug >/dev/null 2>&1 && info "  lint: completed" || warn "  lint: non-fatal issues"
  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop linting
fi

# ---- gates + security + perf + verify -----------------------------------------
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start preflighting
bash "$REPO_ROOT/tools/fastlane/perf.sh" "$app_dir" "$apk"
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop preflighting

"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start gates
eta QUALITY-GATES
if ! bash "$REPO_ROOT/tools/fastlane/gates.sh" "$app_dir" "$apk" --strict; then
  err "BUILD_FAILED: quality gates failed for $slug"
  exit 1
fi
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop gates

"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start security
bash "$REPO_ROOT/tools/fastlane/security-scan.sh" "$app_dir" || exit 1
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop security

# ---- APK verify -----------------------------------------------------------------
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start verifying
appid="$(sed -n "s/.*applicationId[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$app_dir/build.gradle" | head -1)"
if ! bash "$REPO_ROOT/scripts/verify-apk.sh" "$apk" "$appid"; then
  err "BUILD_FAILED: apk verification failed"
  exit 1
fi
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop verifying

total_s=0
run_end_ms="$(now_ms)"
if [ -n "$start_ms" ] && [ -n "$run_end_ms" ] && [ "$run_end_ms" -ge "$start_ms" ]; then
  total_s=$(( (run_end_ms - start_ms) / 1000 ))
fi
sha="$(sha256sum "$apk" | awk '{print $1}')"
sz="$(du -h "$apk" | cut -f1)"

# ---- Fast Lane 3.0 accuracy score (real artifacts only) -----------------------
acc_field=""
if [ -f "$FL_ROOT/accuracy.py" ]; then
  acc_args=("score" "$app_dir" --apk "$apk" --gates "$FL_TMP/gates-$slug.json")
  [ -f "$FL_TMP/coverage-$slug.json" ] && acc_args+=(--coverage "$FL_TMP/coverage-$slug.json")
  [ -f "$FL_TMP/deps-$slug.json" ] && acc_args+=(--deps "$FL_TMP/deps-$slug.json")
  acc="$(python3 "$FL_ROOT/accuracy.py" "${acc_args[@]}" 2>/dev/null || echo '{}')"
  accuracy_score="$(printf '%s' "$acc" | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("accuracy_score","null"))
except Exception: print("null")' 2>/dev/null || echo "null")"
  if [ "$accuracy_score" != "null" ]; then
    ok "  FL3 accuracy score: $accuracy_score/100"
    accuracy_json="$(printf '%s' "$acc" | python3 -c 'import json,sys;print(json.dumps(json.load(sys.stdin)))' 2>/dev/null)"
    printf '%s\n' "$accuracy_json" > "$FL_TMP/accuracy-$slug.json"
    acc_field=",'accuracy_score':$accuracy_score"
  fi
fi

# ---- telemetry record -------------------------------------------------------------
python3 -c "
import json,os
run={'run_id':os.environ.get('GITHUB_RUN_ID','local')+'-'+'$slug','complexity':'$complexity',
     'cold_cache':os.environ.get('FL_COLD_CACHE','0')=='1',
     'compiling':0,'testing':0,'linting':0,'gates':0,'security':0,'verifying':0,
     'total':$total_s,'apps':1,'slug':'$slug','generation':'3.0'$acc_field,
     'recorded_at':'$(now_iso)'}
json.dump(run,open('$FL_TMP/run-$slug.json','w'))
"
"$REPO_ROOT/tools/fastlane/telemetry.sh" record "$FL_TMP/run-$slug.json" >/dev/null 2>&1 || true

ok "BUILD_OK: $slug apk=$apk size=$sz sha=${sha:0:16}… total=${total_s}s"
echo "BUILD_RESULT_OK $slug $apk $sz $sha $total_s"