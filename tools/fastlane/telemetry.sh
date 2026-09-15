#!/usr/bin/env bash
# telemetry.sh — Fast Lane build telemetry (18. BUILD TELEMETRY).
#
# Records stage durations, cache hits, repair attempts, failure categories and
# total build time into a rolling aggregate (`.fastlane/telemetry.json`) plus a
# per-run stream (JSONL). Aggregates drive the build-time estimator
# (`estimate.sh`) so every completed run improves the next estimate.
#
# Usage:
#   telemetry.sh record <run-json>            append one run + update aggregate
#   telemetry.sh report                       human-readable telemetry summary
#   telemetry.sh estimate <complexity> <cold|warm> [n_apps]
#   telemetry.sh stage-start <id>; telemetry.sh stage-stop <id>
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

AGG="$FASTLANE_DATA/telemetry.json"
RUN_STREAM="$FASTLANE_DATA/tmp/runs.jsonl"
mkdir -p "$(dirname "$RUN_STREAM")"
STAGE_FILE="$FL_TMP/.stages.$$"

stage_start() {
  [ -f "$STAGE_FILE" ] || echo "{}" > "$STAGE_FILE"
  python3 - "$STAGE_FILE" "$1" "$(now_ms)" <<'PY'
import json,sys,os
p,k,v=sys.argv[1],sys.argv[2],int(sys.argv[3])
d=json.load(open(p)); d[k]=v
json.dump(d,open(p,"w"))
PY
}
stage_stop() {
  local id="$1"
  python3 - "$STAGE_FILE" "$id" "$(now_ms)" <<'PY'
import json,sys,os
p,k,now=sys.argv[1],sys.argv[2],int(sys.argv[3])
try:
    d=json.load(open(p))
except Exception:
    d={}
if k in d:
    d["_durations_"+k]=now-d[k]
json.dump(d,open(p,"w"))
PY
}

record() {
  local run_file="${1:?usage: telemetry.sh record <run-json>}"
  [ -f "$run_file" ] || die "run file missing: $run_file"
  local run_id run
  run_id="$(python3 -c "import json;print(json.load(open('$run_file')).get('run_id','run'))" 2>/dev/null)"
  # timeboxed dedupe on the JSONL stream (allow repeats later, keep history sane)
  python3 - "$RUN_STREAM" "$run_id" "$run_file" <<'PY'
import json,sys,os,time
stream,run_id,run_file=sys.argv[1:4]
try:
    os.makedirs(os.path.dirname(stream),exist_ok=True)
    open(stream,"a").write(json.dumps(json.load(open(run_file)))+"\n")
except Exception as e:
    print("telemetry: append failed",e,file=sys.stderr)
PY
  merge_aggregate "$run_file"
  echo "telemetry: recorded run '$run_id'"
}

merge_aggregate() {
  local run_file="$1"
  python3 - "$AGG" "$run_file" <<'PY'
import json,sys,os
agg_f,run_f=sys.argv[1:3]
agg={}
if os.path.exists(agg_f):
    try: agg=json.load(open(agg_f))
    except Exception: agg={}
agg.setdefault("stages",{})
try:
    run=json.load(open(run_f))
except Exception:
    sys.exit(0)
stage_map={
  "planning":"planning","analyze":"analyzing","scaffold":"scaffolding",
  "compile":"compiling","tests":"testing","lint":"linting",
  "gates":"gates","security":"security","verify":"verifying",
  "release":"releasing","total":"total","preflight":"preflighting",
}
for st in ("planning","analyzing","scaffolding","compiling","preflighting",
           "testing","linting","gates","security","verifying","releasing","total"):
    key=stage_map[st]
    if key not in run: continue
    s=agg["stages"].setdefault(st,{"count":0,"history":[]})
    # keep rolling max 40 most-recent samples
    s["history"].append(run[key])
    s["history"]=s["history"][-40:]
    s["count"]=len(s["history"])
agg["last_run"]={"run_id":run.get("run_id"),"complexity":run.get("complexity"),
                 "cold":run.get("cold_cache",False),"total":run.get("total"),
                 "apps":run.get("apps"),"at":run.get("recorded_at")}
tmp=agg_f+".tmp"
json.dump(agg,open(tmp,"w"),sort_keys=True,indent=1)
os.replace(tmp,agg_f)
PY
}

report() {
  [ -f "$AGG" ] || { echo "telemetry: no aggregate data yet"; exit 0; }
  python3 - "$AGG" <<'PY'
import json,sys
agg=json.load(open(sys.argv[1]))
print("Fast Lane telemetry (committed aggregate)\n")
for st in ("planning","analyzing","scaffolding","compiling","preflighting",
           "testing","linting","gates","security","verifying","releasing","total"):
    s=agg["stages"].get(st)
    if not s: continue
    h=s["history"]
    import statistics
    print(f"  {st:<11} runs={s['count']:<3} median={statistics.median(h):>7.0f}s "
          f"min={min(h):>5.0f}s max={max(h):>6.0f}s")
if "last_run" in agg:
    lr=agg["last_run"]
    print(f"\n  last run: {lr.get('run_id','?')} complexity={lr.get('complexity')} "
          f"cold={lr.get('cold')} total={lr.get('total')}s apps={lr.get('apps')}")
PY
}

estimate() {
  local complexity="${1:?usage: telemetry.sh estimate <complexity> <cold|warm> [n_apps]}"
  local cache="${2:-warm}"
  local apps="${3:-1}"
  local per_base total cold total_dev
  per_base="$(est_seconds_for_level "$complexity")"
  per_base="$(telemetry_adjust "${complexity,,}" "$per_base")"
  total="$(( per_base * apps ))"
  if [ "$cache" = "cold" ]; then
    cold=1.8
    total_dev="$(python3 -c "print(int(round($total*$cold)))")"
  else
    total_dev="$total"
  fi
  printf '{"complexity":"%s","per_app_seconds":%d,"apps":%d,"cache":"%s","estimated_total_seconds":%d}\n' \
    "$complexity" "${per_base%.*}" "$apps" "$cache" "$total_dev"
}

case "${1:-}" in
  record)        shift; record "$@";;
  report)        report;;
  stage-start)   stage_start "${2:?}";;
  stage-stop)    stage_stop "${2:?}";;
  estimate)      shift; estimate "$@";;
  *) echo "usage: telemetry.sh {record|report|estimate|stage-start|stage-stop}" >&2; exit 2;;
esac