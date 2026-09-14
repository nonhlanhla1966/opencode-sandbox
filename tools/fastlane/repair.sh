#!/usr/bin/env bash
# repair.sh — Automatic Error-Fix Loop (7. AUTOMATIC ERROR-FIX LOOP).
#
# Never just returns a compiler error to the user. Classifies the error,
# locates the root cause, applies a minimal deterministic fix and reruns,
# until the repair budget is exhausted.
#
# Usage:
#   repair.sh <budget> <workdir> <log-file> -- <command...>
#   repair.sh classify <log-file>            # print error category json
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

FIXES_DIR="$(dirname "${BASH_SOURCE[0]}")/fixes"

classify() {
  local log="${1:?usage: repair.sh classify <log-file>}"
  python3 - "$log" <<'PY'
import json,re,sys
log=open(sys.argv[1],encoding="utf-8",errors="replace").read()
cats=[]
def has(pat):
    return bool(re.search(pat, log, re.I|re.M))
if has(r"(?:error|unresolved reference)[^\n]*Kotlin|\.kt:\d+") or has(r"[a-zA-Z/]+\.kt:"):
    cats.append("kotlin")
if has(r"error:\s+[^\n]+\.java|\.java:\d+:"):
    cats.append("java")
if has(r"error:\s*resource|No resource found that matches the given name|aapt2.*error|XML\s*(?:parse|malform)"):
    cats.append("resource")
if has(r"Configuration cache|problems were found storing the configuration cache|configuration-cache"):
    cats.append("configcache")
if has(r"Could not (?:find|resolve)|Could not GET|Failed to (?:query|resolve)|unknown tag.*dependency"):
    cats.append("dependency")
if has(r"Invalid manifest|Manifest (?:error|merger)|provider.*not found|no intent filter"):
    cats.append("manifest")
if has(r"Compose|@Composable|Modifier\."):
    cats.append("compose")
if has(r"(?:SQL|sqlite).*(?:Caused by|Exception)|no such table"):
    cats.append("database")
if has(r"test(?:s)? failed|x\.html?|FAILED.*Test|There were failing tests"):
    cats.append("test")
if has(r"FAILED\b|error:"):
    cats.append("compile")
if not cats:
    cats.append("unknown")
print(json.dumps({"categories": cats}))
PY
}

# Extract a missing resource name from an aapt comment.
missing_resource() {
  local log="$1"
  grep -oE "(?:@(string|color|dimen|drawable|style)/[A-Za-z0-9_]+|No resource found[^']*[*']?[A-Za-z0-9_/]+)" "$log" \
    | grep -oE "[A-Za-z0-9_]+$" | sort -u | head -5 || true
}

run_with_repair() {
  local budget="$1" wd="$2" log="$3"; shift 3
  [ "$1" = "--" ] && shift
  local attempt=1 failures=""
  while [ "$attempt" -le "$budget" ]; do
    info "repair: attempt $attempt/$budget — $*"
    : > "$log.pidlock" 2>/dev/null || true
    if ( cd "$wd" && "$@" ) >"$log" 2>&1; then
      echo "{\"ok\":true,\"attempts\":$attempt}" > "$log.result"
      ok "repair: success on attempt $attempt"
      return 0
    fi
    cat="$(bash "$FL_ROOT/repair.sh" classify "$log" | python3 -c 'import json,sys;print(",".join(json.load(sys.stdin)["categories"]))')"
    warn "repair: failed (categories=$cat). Applying fixes..."
    apply_fixes "$log" "$wd" "$cat"
    failures="$failures $cat"
    attempt=$((attempt + 1))
  done
  err "repair: FAILED after $budget attempts (categories:$failures)"
  echo "{\"ok\":false,\"attempts\":$((attempt-1)),\"categories\":\"$failures\"}" > "$log.result"
  return 1
}

apply_fixes() {
  local log="$1" wd="$2" cats="$3"
  local applied=0
  for c in $(echo "$cats" | tr ',' ' '); do
    local fix="$FIXES_DIR/fix-$c.sh"
    if [ -x "$fix" ]; then
      if "$fix" "$log" "$wd"; then
        info "repair: applied fix-$c"
        applied=$((applied + 1))
      fi
    fi
  done
  # generic compile error: report the first few error lines for diagnosis
  if [ "$applied" -eq 0 ]; then
    echo "--- error context (for repair diagnosis) ---" >&2
    grep -E -m 5 "error:|FAILED|Exception|Caused by" "$log" >&2 || true
  fi
}

case "${1:-}" in
  classify) shift; classify "$@";;
  *)
    budget="$1"; shift
    wd="$1"; shift
    log="$1"; shift
    run_with_repair "$budget" "$wd" "$log" "$@"
    ;;
esac