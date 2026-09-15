#!/usr/bin/env bash
# fastlane.sh — Fast Lane engine dispatcher.
#
# ONE APP IDEA -> Analyze -> App Specification -> Complexity estimate ->
# Architecture plan -> Select verified modules -> Scaffold -> Parallel
# cached build -> Error-fix loop -> Unit tests -> Gates -> Security ->
# APK verify -> telemetry. (Release + GitHub publish happen in CI.)
#
# Commands:
#   fastlane.sh idea "<one-line idea>"            -> print app-spec.json
#   fastlane.sh plan <app-spec.json>              -> architecture.json + PLAN.md
#   fastlane.sh scaffold <app-spec.json> <arch.json>
#   fastlane.sh testgen <app-spec.json> <app-dir>
#   fastlane.sh build <app-dir> [--assemble-only]
#   fastlane.sh full "<one-line idea>" [--out apps/<slug>]
#   fastlane.sh full3 "<one-line idea>"       -> FL3.0 pipeline (checkpoints + all engines)
#   fastlane.sh estimate <app-spec.json>
#   fastlane.sh selftest
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

FL="$FL_ROOT"

idea() {
  local idea="${1:?usage: fastlane.sh idea \"<one-line idea>\"}"
  python3 "$FL/analyze.py" "$idea"
}

plan() {
  local spec="${1:?usage: fastlane.sh plan <app-spec.json> [--out dir]}"
  local out="$REPO_ROOT/apps"
  if [ "${2:-}" = "--out" ]; then out="${3:?}"; fi
  python3 "$FL/plan.py" "$spec" --out "$out" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps(d, indent=1, sort_keys=True))'
}

scaffold() {
  local spec="${1:?usage: fastlane.sh scaffold <app-spec.json> <arch.json>}"
  python3 "$FL/scaffold.py" "$spec" "${2:?}"
}

testgen() {
  bash "$FL/testgen.sh" "${1:?}" "${2:?}"
}

build() {
  bash "$FL/build.sh" "${1:?}" "${2:-full}"
}

full() {
  local idea="${1:?usage: fastlane.sh full \"<one-line idea>\"}"
  local tmp="$FL_TMP/full"
  mkdir -p "$tmp"
  local spec="$REPO_ROOT/apps/spec.json"
  info "Fast Lane: analyzing idea"
  python3 "$FL/analyze.py" "$idea" > "$tmp/spec.json" || die "analysis failed"
  info "Fast Lane: planning architecture"
  python3 "$FL/plan.py" "$tmp/spec.json" --out "$tmp" > "$tmp/arch.json" || die "plan failed"
  info "Fast Lane: scaffolding project"
  python3 "$FL/scaffold.py" "$tmp/spec.json" "$tmp/arch.json" "$REPO_ROOT/apps" || die "scaffold failed"
  local slug
  slug="$(cat "$tmp/spec.json" | python3 -c 'import json,sys;print(json.load(sys.stdin)["slug"])')"
  info "Fast Lane: generating tests"
  bash "$FL/testgen.sh" "$tmp/spec.json" "$REPO_ROOT/apps/$slug" || true
  info "Fast Lane: building $slug"
  bash "$FL/build.sh" "$REPO_ROOT/apps/$slug" "${2:-full}"
}

estimate() {
  bash "$FL/estimate.sh" compute "${1:?usage: fastlane.sh estimate <app-spec.json>}"
}

full3() {
  bash "$FL/fastlane3.sh" pipeline "$@"
}

selftest() {
  bash "$FL/selftest.sh" "$@"
}

cmd="${1:-selftest}"
case "$cmd" in
  idea) shift; idea "$@";;
  plan) shift; plan "$@";;
  scaffold) shift; scaffold "$@";;
  testgen) shift; testgen "$@";;
  build) shift; build "$@";;
  full) shift; full "$@";;
  full3) shift; full3 "$@";;
  estimate) shift; estimate "$@";;
  selftest) selftest "${2:-}";;
  *) echo "fastlane: unknown command '$cmd'"; exit 2;;
esac