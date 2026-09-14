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
  bash "$REPO_ROOT/tools/fastlane/repair.sh" "$budget" "$app_dir" "$log" -- \
    ./gradlew "${GRADLE_ARGS[@]}" "$task" "$@" >/dev/null 2>&1
}

# ---- compile (assembleDebug) --------------------------------------------------
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop planning
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

bash "$REPO_ROOT/tools/fastlane/perf.sh" "$app_dir" "$apk"

# ---- APK verify -----------------------------------------------------------------
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start verifying
appid="$(sed -n "s/.*applicationId[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$app_dir/build.gradle" | head -1)"
if ! bash "$REPO_ROOT/scripts/verify-apk.sh" "$apk" "$appid"; then
  err "BUILD_FAILED: apk verification failed"
  exit 1
fi
"$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop verifying

total_s=$(( ($(now_ms) - start_ms) / 1000 ))
sha="$(sha256sum "$apk" | awk '{print $1}')"
sz="$(du -h "$apk" | cut -f1)"

# ---- telemetry record -------------------------------------------------------------
python3 -c "
import json,os
run={'run_id':os.environ.get('GITHUB_RUN_ID','local')+'-'+'$slug','complexity':'$complexity',
     'cold_cache':os.environ.get('FL_COLD_CACHE','0')=='1',
     'compiling':0,'testing':0,'linting':0,'gates':0,'security':0,'verifying':0,
     'total':$total_s,'apps':1,'slug':'$slug','recorded_at':'$(now_iso)'}
json.dump(run,open('$FL_TMP/run-$slug.json','w'))
"
"$REPO_ROOT/tools/fastlane/telemetry.sh" record "$FL_TMP/run-$slug.json" >/dev/null 2>&1 || true

ok "BUILD_OK: $slug apk=$apk size=$sz sha=${sha:0:16}… total=${total_s}s"
echo "BUILD_RESULT_OK $slug $apk $sz $sha $total_s"