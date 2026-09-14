#!/usr/bin/env bash
# estimate.sh — Build-Time Estimator (5. BUILD TIME ESTIMATE).
#
# Reads app-spec.json (or architecture.json + telemetry) and emits a human-
# readable build-time estimate banner to stdout/stderr. Used by build.sh
# before and during every build.
#
# Usage:
#   estimate.sh compute <app-spec.json> [telemetry.json]
#   estimate.sh banner  <app-spec.json> [telemetry.json]
#   estimate.sh --start-banner <complexity> <estimated_seconds> [apps]
#
# exit 0 always (estimates are always approximate).
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

banner() {
  local level="$1" est="$2" apps="${3:-1}" cold="${4:-warm}"
  local band
  case "$level" in
    SIMPLE)  band="under 2 minutes";;
    MEDIUM)  band="2-5 minutes";;
    COMPLEX) band="~5 minutes";;
    EXTREME) band="5-10+ minutes";;
    *)       band="~$(human_dur "$est")";;
  esac
  leader "Build time estimate"
  printf "  Complexity:      ${C_B}%s${C_X}\n" "$level"
  printf "  Per-app:         %s\n" "$band"
  [ "$apps" -gt 1 ] && printf "  Apps:            %d (parallel) -> ~%s total (warm cache)\n" "$apps" "$(human_dur "$est")" || \
    printf "  Estimated build: %s (warm cache)\n" "$(human_dur "$est")"
  [ "$cold" = "cold" ] && printf "  Note: cold cache detected; real time likely longer (~1.5-2x).\n"
  printf "  Elapsed:         0s\n  Remaining:       ~%s\n" "$(human_dur "$est")"
}

start_banner() {
  local level="$1" est="$2" apps="${3:-1}" cold="${4:-warm}"
  printf '{"complexity":"%s","estimated_seconds":%d,"apps":%d,"cache":"%s","start_ms":%d}' \
    "$level" "$est" "$apps" "$cold" "$(now_ms)"
}

compute() {
  local spec_file="$1"
  local level score
  level="$(json_get "$(cat "$spec_file")" '.complexity.level')"
  score="$(json_get "$(cat "$spec_file")" '.complexity.score')"
  local base sec
  base="$(est_seconds_for_level "$level")"
  sec="$(telemetry_adjust "${level,,}" "$base")"
  printf '{"level":"%s","score":%s,"base_seconds":%d,"per_app_seconds":%s}\n' \
    "$level" "${score:-50}" "${base}" "${sec}"
}

case "${1:-}" in
  banner|compute)
    cmd="$1"; shift
    case "$cmd" in
      banner)
        level="$(json_get "$(cat "$1")" '.complexity.level' 2>/dev/null)"
        est="$(json_get "$(cat "$1")" '.estimate.build_time_seconds' 2>/dev/null)"
        banner "${level:-MEDIUM}" "${est:-120}" 1 warm
        ;;
      compute) compute "$@" ;;
    esac
    ;;
  --start-banner) shift; start_banner "$@";;
  *) banner "MEDIUM" 120 1 warm;;
esac