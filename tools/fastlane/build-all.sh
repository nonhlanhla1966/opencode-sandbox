#!/usr/bin/env bash
# build-all.sh — CI/regression multi-app builder used by the Fast Lane workflow.
#
# Builds every app (or a slice) with shared Gradle caches, then publishes
# releases + DOWNLOAD_READY comments. Supports parallel workers (-j N, default
# sequential) and a live estimate banner for the whole run.
#
# Usage: build-all.sh [--apps a,b,c] [-j N] [--publish]
#   Without --apps: every apps/*/ that has build.gradle.
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

APPS="" JOBS=1 PUBLISH=0 EXTRA_APP_DIRS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apps) APPS="$2"; shift 2;;
    -j) JOBS="$2"; shift 2;;
    --publish) PUBLISH=1; shift;;
    *) EXTRA_APP_DIRS="$EXTRA_APP_DIRS $1"; shift;;
  esac
done

if [ -z "$APPS" ]; then
  for d in "$REPO_ROOT"/apps/*/; do
    [ -f "$d/build.gradle" ] && APPS="$APPS $(basename "$d")"
  done
fi
APPS="$(echo $APPS | xargs -n1 | sort -u | xargs)"

[ -n "$APPS" ] || die "no apps to build"
n_apps=$(echo $APPS | wc -w)

# ---- whole-run estimate banner ---------------------------------------------------
complexity_total=0
for a in $APPS; do
  sc="$REPO_ROOT/apps/$a/app-spec.json"
  if [ -f "$sc" ]; then
    lvl="$(json_get "$(cat "$sc")" '.complexity.level')"
    complexity_total=$((complexity_total + $(est_seconds_for_level "$lvl")))
  else
    complexity_total=$((complexity_total + 120))
  fi
done
if [ "$JOBS" -gt 1 ]; then jobs_denom="$JOBS"; else jobs_denom=1; fi
est=$(( complexity_total / jobs_denom ))
est="$(telemetry_adjust total "$est")"
leader "Build-time estimate"
echo "  Apps:              $n_apps (${JOBS} worker(s))"
echo "  Estimated build:   ~$(human_dur "$est") (warm Gradle/SDK cache)"
echo "  Elapsed:           0s"
start_ms="$(now_ms)"

mkdir -p "$FL_TMP"
rm -f "$FL_TMP/run-*" "$FL_TMP/*.result" 2>/dev/null || true

worker() {
  local a="$1"
  local ret=0
  if [ -d "$REPO_ROOT/apps/$a" ] && [ -f "$REPO_ROOT/apps/$a/build.gradle" ]; then
    if bash "$FL_ROOT/build.sh" "$REPO_ROOT/apps/$a" full 2>&1; then
      ok "worker: $a OK"
    else
      err "worker: $a FAILED"
      ret=1
    fi
  fi
  return $ret
}
export -f worker
export FL_ROOT REPO_ROOT FL_TMP

# Run with optional parallelism (safe, bounded: -j2 on 4-core runners).
failures=0
if [ "$JOBS" -gt 1 ]; then
  results="$(mktemp)"
  echo "$APPS" | xargs -n1 -P "$JOBS" bash -c 'worker "$0"' >>"$results" 2>&1
  cat "$results"
  if grep -q "worker:.*FAILED" "$results"; then
    failures="$(grep -c "worker:.*FAILED" "$results")"
  else
    failures=0
  fi
  rm -f "$results"
else
  for a in $APPS; do worker "$a" || failures=$((failures+1)); done
fi

elapsed=$(( ($(now_ms) - start_ms) / 1000 ))
leader "Build complete"
echo "  Apps total:        $n_apps"
echo "  Failures:          $failures"
echo "  Elapsed:           $(human_dur "$elapsed")"
echo "  Estimated:         ~$(human_dur "$est")"

# ---- telemetry for the run ---------------------------------------------------------
python3 -c "
import json,os
run={'run_id':os.environ.get('GITHUB_RUN_ID','local')+'-batch','complexity':'MIXED',
     'cold_cache':os.environ.get('FL_COLD_CACHE','0')=='1',
     'total':$elapsed,'apps':$n_apps,'recorded_at':'$(now_iso)'}
json.dump(run,open('$FL_TMP/run-batch.json','w'))
" 2>/dev/null || true
"$REPO_ROOT/tools/fastlane/telemetry.sh" record "$FL_TMP/run-batch.json" >/dev/null 2>&1 || true

[ "$failures" -gt 0 ] && exit 1
exit 0