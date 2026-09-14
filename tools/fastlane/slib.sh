#!/usr/bin/env bash
# slib.sh — shared library for the Fast Lane Android app factory engine.
# Everything here is POSIX-ish bash + python3 only (no node/ruby).
# Sourced by other fastlane scripts; never executed directly.
set -u

FL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$FL_ROOT/../.." && pwd)"
FL_TMP="${FASTLANE_TMPDIR:-$REPO_ROOT/.fastlane/tmp}"
FASTLANE_DATA="$REPO_ROOT/.fastlane"

export FL_ROOT REPO_ROOT FL_TMP FASTLANE_DATA
mkdir -p "$FL_TMP" "$FASTLANE_DATA"

# ---------- date / time helpers ----------------------------------------------
now_ms() { date +%s%3N; }
now_iso() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# ---------- JSON helpers (python3 backed, atomic writes) ----------------------
json_get() {  # json_get <json-doc> <jq-like-path>  (path: .a.b[0])
  python3 -c 'import json,sys
d=json.load(sys.stdin)
for p in sys.argv[1].lstrip(".").replace("[",".[").split("."):
    if not p: continue
    if p.startswith("["):
        d=d[int(p[1:-1])]; continue
    d=d.get(p,{})
    if isinstance(d,dict) and "_missing" in d: d=""
print(d if not isinstance(d,(dict,list)) else json.dumps(d))' "$2" <<<"$1" 2>/dev/null || echo ""
}

# safely merge two JSON docs (a base, b overlay) via python
json_merge() { # json_merge <base-doc-or-file:A> <overlay-doc-or-file:B>
  A="$1"; B="$2"
  if [ -f "$A" ]; then A="$(cat "$A")"; fi
  if [ -f "$B" ]; then B="$(cat "$B")"; fi
  python3 -c 'import json,sys
a=json.loads(sys.argv[1]); b=json.loads(sys.argv[2])
def merge(x,y):
    for k,v in y.items():
        if isinstance(v,dict) and isinstance(x.get(k),dict): merge(x[k],v)
        else: x[k]=v
merge(a,b); print(json.dumps(a,sort_keys=True))' "$A" "$B" 2>/dev/null || { echo "{}"; }
}

# ---------- styling -----------------------------------------------------------
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_B="\e[1m"; C_DIM="\e[2m"; C_R="\e[31m"; C_G="\e[32m"; C_Y="\e[33m"; C_C="\e[36m"; C_X="\e[0m"
else
  C_B=""; C_DIM=""; C_R=""; C_G=""; C_Y=""; C_C=""; C_X=""
fi

log()  { printf "${C_DIM}%s${C_X}\n" "$*" >&2; }
info() { printf "${C_C}%s${C_X}\n" "$*" >&2; }
warn() { printf "${C_Y}%s${C_X}\n" "$*" >&2; }
err()  { printf "${C_R}%s${C_X}\n" "$*" >&2; }
ok()   { printf "${C_G}%s${C_X}\n" "$*" >&2; }
leader() { printf "\n${C_B}== %s ==${C_X}\n" "$*" >&2; }

die() { err "fastlane: $*"; exit 1; }

# ---------- misc --------------------------------------------------------------
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing required tool: $1"; }
require_cmd python3

slug_of() {
  local s="$1"
  s="$(printf '%s' "$s" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9' '-')"
  s="$(printf '%s' "$s" | sed -E 's/-+/-/g;s/^-//;s/-$//')"
  [ -n "$s" ] || s="app"
  printf '%s' "$s"
}

# duration in human form
human_dur() { # seconds
  local s="$1" h m
  h=$((s/3600)); m=$(((s%3600)/60)); s=$((s%60))
  if [ "$h" -gt 0 ]; then printf '%dh %02dm %02ds' "$h" "$m" "$s"
  elif [ "$m" -gt 0 ]; then printf '%dm %02ds' "$m" "$s"
  else printf '%ds' "$s"; fi
}

# ---------- build time estimate model ------------------------------------------
# Fast Lane build-time estimation. Baseline constants are derived from real
# AppFactory GitHub Actions runs; telemetry (.fastlane/telemetry.json) overlays
# them. Estimates are explicitly approximate — never a guarantee.
complexity_of_score() { # <score 0-100> -> SIMPLE|MEDIUM|COMPLEX|EXTREME
  local s="$1"
  if   [ "$s" -lt 25 ]; then echo "SIMPLE"
  elif [ "$s" -lt 55 ]; then echo "MEDIUM"
  elif [ "$s" -lt 80 ]; then echo "COMPLEX"
  else echo "EXTREME"; fi
}

# per-app cached-build seconds estimate for a complexity level
est_seconds_for_level() { # <complexity-level> -> seconds baseline
  case "$1" in
    SIMPLE)  echo 60;;
    MEDIUM)  echo 120;;
    COMPLEX) echo 210;;
    EXTREME) echo 420;;
    *)       echo 120;;
  esac
}

# Adjust for whether this is a cold (no-http) or warm cache run.
est_cache_factor() {
  if [ "${FL_COLD_CACHE:-0}" = "1" ]; then echo 1.8; else echo 1.0; fi
}

# Merge telemetry adjustments into stage seconds.
telemetry_adjust() { # <stage> <base-seconds> -> adjusted seconds
  local stage="$1" base="$2"
  if [ -f "$FASTLANE_DATA/telemetry.json" ]; then
    python3 - "$stage" "$base" <<'PY'
import json,os,sys
stage,b=sys.argv[1],float(sys.argv[2])
try:
    t=json.load(open(os.environ['FASTLANE_DATA']+"/telemetry.json"))
    hist=t.get("stages",{}).get(stage,{}).get("history",[])
    if len(hist)>=3:
        import statistics
        med=statistics.median(hist)
        # trust history only within a sane range (0.4x..2.5x of baseline)
        if 0.4*base < med < 2.5*base:
            b=med
except Exception:
    pass
print(f"{b:.1f}")
PY
  else
    echo "$base"
  fi
}