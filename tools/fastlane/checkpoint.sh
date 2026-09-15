#!/usr/bin/env bash
# checkpoint.sh — Fast Lane 3.0 Checkpoint/Resume Engine (20. Recovery/Checkpoint).
#
# Persists pipeline stage state per app in .fastlane/checkpoints/<slug>/ and
# lets a failed run resume from the last completed stage instead of restarting.
#
# Usage:
#   checkpoint.sh save <slug> <stage> <payload-file-or-dir>   mark stage complete + store artifacts
#   checkpoint.sh check <slug> <stage>                        exit 0 if stage complete
#   checkpoint.sh resume-from <slug>                          echo next stage to run (first incomplete in order)
#   checkpoint.sh state <slug>                                JSON of completed stages + metadata
#   checkpoint.sh clear <slug>                                reset progress for an app
# Env: stage order from FASTLANE_STAGES or default below.
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

DEFAULT_STAGES="spec arch modules taskgraph generate preflight build test security release"
STAGES="${FASTLANE_STAGES:-$DEFAULT_STAGES}"
STAGE_DIR="$FASTLANE_DATA/checkpoints"

require_cmd python3

stages_list() { IFS=' ' read -r -a s <<< "$STAGES"; printf '%s\n' "${s[@]}"; }

stage_dir() { printf '%s/%s' "$STAGE_DIR" "$1"; }

stage_file() { printf '%s/%s/%s.json' "$STAGE_DIR" "$1" "$2"; }

cmd_save() {
  local slug="${1:?usage: checkpoint.sh save <slug> <stage> <payload>}"
  local stage="${2:?}"
  local payload="${3:-}"
  local dir; dir="$(stage_dir "$slug")"
  mkdir -p "$dir"
  local rec="{\"stage\":\"$stage\",\"completed\":true,\"at\":\"$(now_iso)\",\"payload\":\"$payload\"}"
  printf '%s' "$rec" > "$(stage_file "$slug" "$stage")"
  # keep ordering metadata by copying stage list
  printf '%s' "$STAGES" > "$dir/stages.txt"
  ok "checkpoint: $slug stage '$stage' saved"
}

cmd_check() {
  local slug="${1:?}" stage="${2:?}"
  [ -f "$(stage_file "$slug" "$stage")" ] && exit 0 || exit 1
}

cmd_resume_from() {
  local slug="${1:?}"
  local dir; dir="$(stage_dir "$slug")"
  local order="$DEFAULT_STAGES"
  [ -f "$dir/stages.txt" ] && order="$(cat "$dir/stages.txt")"
  local found=""
  for s in $order; do
    if [ ! -f "$(stage_file "$slug" "$s")" ]; then found="$s"; break; fi
  done
  if [ -n "$found" ]; then printf '%s\n' "$found"; else printf 'DONE\n'; fi
}

cmd_state() {
  local slug="${1:?}"
  local dir; dir="$(stage_dir "$slug")"
  local order="$DEFAULT_STAGES"
  [ -f "$dir/stages.txt" ] && order="$(cat "$dir/stages.txt")"
  local completed=""
  for s in $order; do
    if [ -f "$(stage_file "$slug" "$s")" ]; then
      completed="$completed $(basename "$s")"
    fi
  done
  local next; next="$(cmd_resume_from "$slug")"
  python3 - "$slug" "$completed" "$next" "$dir" <<'PY'
import json,sys
slug,completed,next,dir=sys.argv[1:]
print(json.dumps({
  "slug":slug,
  "completed_stages": completed.split(),
  "next_stage": next,
  "checkpoint_dir": dir,
  "spec_version":"3.0",
}))
PY
}

cmd_clear() {
  local slug="${1:?}"
  rm -rf "$(stage_dir "$slug")"
  info "checkpoint: cleared $slug"
}

case "${1:-}" in
  save)        shift; cmd_save "$@";;
  check)       shift; cmd_check "$@";;
  resume-from) shift; cmd_resume_from "$@";;
  state)       shift; cmd_state "$@";;
  clear)       shift; cmd_clear "$@";;
  *) echo "usage: checkpoint.sh {save|check|resume-from|state|clear}" >&2; exit 2;;
esac