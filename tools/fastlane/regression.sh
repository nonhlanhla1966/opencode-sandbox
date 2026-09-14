#!/usr/bin/env bash
# regression.sh — Regression Lab (15. REGRESSION LAB).
#
# Every AppFactory upgrade must verify the existing applications still build.
# This runner protects: a-one-button-hello-world-app, flashlight,
# hello-appfactory, notes-app, tip-calculator and opencode-chatbot, plus any
# generated representative apps present in apps/.
#
# Usage:
#   regression.sh list                         # protected + found apps
#   regression.sh validate <app-dir>           # structural checks only (no SDK)
#   regression.sh report                       # JSON regression report
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

PROTECTED="a-one-button-hello-world-app flashlight hello-appfactory notes-app tip-calculator opencode-chatbot"

list() {
  info "Protected regression apps:"
  for a in $PROTECTED; do
    if [ -d "$REPO_ROOT/apps/$a" ]; then ok "  $a"; else err "  $a — MISSING"; fi
  done
  info "Additional apps in apps/:"
  for d in "$REPO_ROOT"/apps/*/; do
    a="$(basename "$d")"
    case " $PROTECTED " in
      *" $a "*) ;;
      *) echo "  $a";;
    esac
  done
}

validate() {
  local app_dir="${1:?usage: regression.sh validate <app-dir>}"
  bash "$REPO_ROOT/scripts/validate-app.sh" "$app_dir" >&2
}

report() {
  local out="{}" lines=""
  for d in "$REPO_ROOT"/apps/*/; do
    a="$(basename "$d")"
    if bash "$REPO_ROOT/scripts/validate-app.sh" "$d" >/dev/null 2>&1; then
      lines="$lines{\"app\":\"$a\",\"structural\":\"PASS\"},"
    else
      lines="$lines{\"app\":\"$a\",\"structural\":\"FAIL\"},"
    fi
  done
  lines="${lines%,}"
  python3 -c "import json,sys
print(json.dumps({'scanned':'$REPO_ROOT/apps/*','apps':[${lines}]}))"
}

case "${1:-}" in
  list) list;;
  validate) shift; validate "$@";;
  report) report;;
  *) echo "usage: regression.sh {list|validate <app-dir>|report}" >&2
     echo "protection list: $PROTECTED" >&2; exit 2;;
esac