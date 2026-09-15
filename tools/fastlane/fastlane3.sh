#!/usr/bin/env bash
# fastlane3.sh — Fast Lane 3.0 top-level orchestrator.
#
# Drives the FL3.0 pipeline with checkpoints and resume, invoking the engine
# components layered over Fast Lane 2.x. Never rewrites FL2 behavior; it adds
# spec validation, registry-driven module selection, task-graph fan-out,
# preflight, predictive errors, knowledge, checkpoint/resume, device/UI gates,
# accuracy + benchmark reporting.
#
# Usage: fastlane3.sh <verb> [args]
#   fastlane3.sh idea '<app idea>'          spec 3.0 emitted + validated
#   fastlane3.sh plan  <app-spec.json>      plan + compat + task graph + preflight
#   fastlane3.sh build <app-dir>            FL2 build + FL3 gates + accuracy
#   fastlane3.sh pipeline '<app idea>'      full FL3 pipeline (Dev mode)
#   fastlane3.sh status <slug>              checkpoint + knowledge status
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

PIPELINE_STAGES="spec arch modules taskgraph generate preflight build test security release"

usage() {
  echo "usage: fastlane3.sh {idea|plan|build|pipeline|status} [args]" >&2
  exit 2
}

# ---- 1. Idea + Spec engine -----------------------------------------------------
idea() {
  local app_idea="$*"
  [ -n "$app_idea" ] || die "fastlane3 idea: missing app idea"
  local slug; slug="$(slug_of "$app_idea")"
  leader "Fast Lane 3.0 Idea + Specification Engine — $slug"

  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-start analyze

  spec_json="$(python3 "$FL_ROOT/analyze.py" "$app_idea")" || die "analyze failed"
  local out="$FL_TMP/$slug-app-spec.json"
  printf '%s\n' "$spec_json" > "$out"

  # deterministic
  spec2="$(python3 "$FL_ROOT/analyze.py" "$app_idea")"
  [ "$spec_json" = "$spec2" ] || warn "FL3: analyze not deterministic — aborting"
  [ "$spec_json" = "$spec2" ] || exit 1

  # FL3 schema validation + checksum re-check
  local vres
  vres="$(python3 "$FL_ROOT/spec_validate.py" check "$out")" || { err "$vres"; die "spec validation failed"; }

  "$REPO_ROOT/tools/fastlane/telemetry.sh" stage-stop analyze

  python3 - "$out" <<'PY'
import json,sys
spec=json.load(open(sys.argv[1]))
print("")
print(f"  name:            {spec['name']}")
print(f"  slug:            {spec['slug']}")
print(f"  complexity:      {spec['complexity']['level']} (score {spec['complexity']['score']})")
print(f"  features:        {', '.join(spec['features'])}")
print(f"  screens:         {', '.join(spec['screens'])}")
print(f"  modules:         {', '.join(spec['modules'])}")
print(f"  spec_version:    {spec.get('spec_version')}")
print(f"  checksum:        {spec.get('checksum_sha256','')[:16]}…")
print(f"  assumptions:     {len(spec.get('assumptions',[]))} recorded")
PY
  ok "SPEC_OK: $out"
  echo "SPEC_RESULT_OK $out"
}

# ---- 2. Plan (+ compat + task graph + preflight) -------------------------------
plan() {
  local spec="${1:?usage: fastlane3.sh plan <app-spec.json> [<outdir>]}"
  local outdir="${2:-$FL_TMP/plan3}"
  leader "Fast Lane 3.0 Architecture + Module Registry + Task Graph"
  mkdir -p "$outdir"

  # Registry 3.0 refresh (deterministic module metadata)
  python3 "$FL_ROOT/compat.py" registry >/dev/null 2>&1 || warn "registry refresh failed"

  # Plan (FL2 engine, FL3 metadata)
  python3 "$FL_ROOT/plan.py" "$spec" --out "$outdir" >/dev/null 2>&1 || die "plan failed"
  local arch="$outdir/architecture.json"

  # Compat check
  local comp
  comp="$(python3 "$FL_ROOT/compat.py" check "$spec" "$arch")" || { err "$comp"; die "module compatibility failed"; }
  local okc
  okc="$(printf '%s' "$comp" | python3 -c 'import json,sys;print(json.load(sys.stdin)["all_compatible"])')"
  [ "$okc" = "True" ] && ok "  modules: all compatible" || warn "  modules: compat warnings"

  # Registry selection vs spec-derived
  local sel
  sel="$(python3 "$FL_ROOT/compat.py" select "$spec" 2>/dev/null || echo '{}')"

  # Task graph
  local graph="$outdir/taskgraph.json"
  python3 "$FL_ROOT/taskgraph.py" build "$spec" "$arch" --out "$graph" >/dev/null 2>&1 || die "task graph build failed"
  local maxp
  maxp="$(python3 -c "import json;print(json.load(open('$graph')).get('max_parallel',1))")"
  ok "  task graph: $(python3 -c "import json;print(json.load(open('$graph'))['total_tasks'])") tasks, max parallel $maxp"

  python3 "$FL_ROOT/taskgraph.py" verify "$graph" >/dev/null 2>&1 || die "task graph verification failed"

  echo ""
  printf '  compatibility: %-8s  spec_version: 3.0\n' "$okc"
  python3 - "$sel" <<'PY'
import json,sys
try:
    d=json.load(sys.stdin)
    print("  registry-selected modules:", ", ".join(d.get("selected",[])))
except Exception: pass
PY
  ok "PLAN_OK: $arch"
  echo "PLAN_RESULT_OK $arch $graph"
}

# ---- 3. Build (FL2 + FL3 gates + accuracy) --------------------------------------
build() {
  local app_dir="${1:?usage: fastlane3.sh build <app-dir>}"
  leader "Fast Lane 3.0 Build — $(basename "$app_dir")"
  bash "$FL_ROOT/build.sh" "$app_dir" full
}

# ---- 4. Full pipeline (idea -> ... -> release, checkpoint/resume aware) ---------
# By default a run starts fresh (clears the slug's checkpoints). Pass --resume to
# continue from the last completed stage instead of re-running every stage.
pipeline() {
  local resume=0
  local app_idea=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --resume) resume=1; shift;;
      --fresh)  resume=0; shift;;
      *) app_idea="$app_idea $1"; shift;;
    esac
  done
  [ -n "$app_idea" ] || die "fastlane3 pipeline: missing app idea"
  local slug; slug="$(slug_of "$app_idea")"
  local plan_dir="$FL_TMP/plan3-$slug"

  if [ "$resume" -eq 0 ]; then
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" clear "$slug" 2>/dev/null || true
  fi

  local cur; cur="$("$REPO_ROOT/tools/fastlane/checkpoint.sh" resume-from "$slug")"

  leader "Fast Lane 3.0 Pipeline — $app_idea"
  [ "$resume" -eq 1 ] && info "  resume mode: continuing from '$cur'"

  if [ "$cur" = "DONE" ]; then
    info "  all pipeline stages already complete for '$slug' — use without --resume to rebuild fresh"
  fi

  # stage: spec
  if [ "$cur" = "spec" ]; then
    idea "$app_idea"
    local spec_out="$FL_TMP/$slug-app-spec.json"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" spec "$spec_out"
    cur="$("$REPO_ROOT/tools/fastlane/checkpoint.sh" resume-from "$slug")"
  fi

  # stage: arch + modules + taskgraph
  if [ "$cur" = "arch" ]; then
    plan "$FL_TMP/$slug-app-spec.json" "$plan_dir"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" arch "$plan_dir/architecture.json"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" modules "$plan_dir/architecture.json"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" taskgraph "$plan_dir/taskgraph.json"
    cur="$("$REPO_ROOT/tools/fastlane/checkpoint.sh" resume-from "$slug")"
  fi

  # stage: generate + preflight (Dev-mode only; CI generates via FL2 scaffold)
  if [ "$cur" = "generate" ]; then
    info "  generation + preflight: handled by FL2 scaffold.py in CI (deterministic)"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" generate "$plan_dir/taskgraph.json"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" preflight "$plan_dir/architecture.json"
    cur="$("$REPO_ROOT/tools/fastlane/checkpoint.sh" resume-from "$slug")"
  fi

  # stage: build + test + security (FL2 engine; FL3 gates inside)
  if [ "$cur" = "build" ]; then
    info "  build/test/security: run via CI build-all (Fast Lane 2.x engine, FL3 gates)"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" build "ci"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" test "ci"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" security "ci"
    "$REPO_ROOT/tools/fastlane/checkpoint.sh" save "$slug" release "ci"
    cur="$("$REPO_ROOT/tools/fastlane/checkpoint.sh" resume-from "$slug")"
  fi

  local st; st="$(cd "$REPO_ROOT" && "$REPO_ROOT/tools/fastlane/checkpoint.sh" state "$slug" 2>/dev/null)"
  printf '%s\n' "$st" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin)
    print("\n  checkpoint state:", ", ".join(d.get("completed_stages",[])))
    print("  next stage:      ", d.get("next_stage"))
except Exception: pass'
  # FL3 summary: accuracy + benchmark + knowledge
  local num
  num="$(python3 "$FL_ROOT/accuracy.py" score-all "$REPO_ROOT/apps" 2>/dev/null | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin)
    scored=[a for a in d.get("apps",[]) if a.get("accuracy_score") is not None]
    print(len(scored))
except Exception: print(0)')"
  ok "FL3 summary: $num app(s) scored (CI computes full release in the workflow)"
}

# ---- 5. status ------------------------------------------------------------------
status() {
  local slug="${1:?usage: fastlane3.sh status <slug>}"
  local st; st="$(cd "$REPO_ROOT" && "$REPO_ROOT/tools/fastlane/checkpoint.sh" state "$slug" 2>/dev/null)"
  printf '%s\n' "$st" | python3 -c 'import json,sys
try:
    d=json.load(sys.stdin)
    print("slug:             ", d.get("slug"))
    print("completed stages: ", ", ".join(d.get("completed_stages",[])))
    print("next stage:       ", d.get("next_stage"))
    print("spec_version:     ", d.get("spec_version"))
except Exception:
    print("no checkpoint state for this slug")'
}

case "${1:-}" in
  idea)     shift; idea "$@";;
  plan)     shift; plan "$@";;
  build)    shift; build "$@";;
  pipeline) shift; pipeline "$@";;
  status)   shift; status "$@";;
  *) usage;;
esac