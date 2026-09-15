#!/usr/bin/env bash
# benchmark.sh — Fast Lane 3.0 Benchmark + Speed Score Engine.
#
# Compares run telemetry across pipeline generations (FL1/FL2/FL3) from
# .fastlane/tmp/runs.jsonl and computes per-generation median totals,
# per-complexity speed, and the Speed Score (relative improvement to Fast Lane 1).
#
# Usage:
#   benchmark.sh compare            run aggregate comparison + speed score
#   benchmark.sh report             human summary
# Env:  FL_BENCHMARK_WINDOW (default 200 most-recent runs)
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

RUN_STREAM="$FASTLANE_DATA/tmp/runs.jsonl"
WINDOW="${FL_BENCHMARK_WINDOW:-200}"

require_cmd python3

compare() {
  [ -f "$RUN_STREAM" ] || { echo '{"benchmark":"no runs yet","generations":{}}'; exit 0; }
  python3 - "$RUN_STREAM" "$WINDOW" <<'PY'
import json,sys,statistics
stream,window=sys.argv[1],int(sys.argv[2])
runs=[]
try:
    with open(stream) as fh:
        for line in fh:
            line=line.strip()
            if not line: continue
            try: runs.append(json.loads(line))
            except Exception: pass
except Exception as e:
    print({"benchmark":f"error reading telemetry: {e}"}); sys.exit(0)
runs=runs[-window:]

def generation_of(r):
    stages=set(r.keys())
    # FL3 runs carry spec_version markers or explicit gen field
    if r.get("generation")=="3.0" or "accuracy_score" in r or "checkpoint" in stages or "preflight" in stages:
        return "fl3"
    if r.get("generation")=="1.0":
        return "fl1"
    return "fl2"

gens={}
for r in runs:
    g=generation_of(r)
    total=r.get("total")
    if not isinstance(total,(int,float)) or total<=0: continue
    gens.setdefault(g,[]).append({"total":total,"complexity":r.get("complexity","UNKNOWN"),
                                   "slug":r.get("slug"),"cold":r.get("cold_cache",False)})

out={"benchmark":"FL1/FL2/FL3 speed comparison","window":len(runs),"generations":{}}
baseline=None
for g in ("fl1","fl2","fl3"):
    rs=gens.get(g,[])
    if not rs: continue
    totals=[x["total"] for x in rs]
    by_complexity={}
    for c in ("SIMPLE","MEDIUM","COMPLEX","EXTREME"):
        cs=[x["total"] for x in rs if x.get("complexity")==c]
        if cs: by_complexity[c]={"runs":len(cs),"median_s":round(statistics.median(cs),1)}
    gen={"runs":len(rs),"median_total_s":round(statistics.median(totals),1),
         "min_s":round(min(totals),1),"max_s":round(max(totals),1),
         "mean_s":round(statistics.mean(totals),1),"by_complexity":by_complexity}
    if g=="fl1" and totals: baseline=statistics.median(totals)
    out["generations"][g]=gen

# speed score relative to FL1 baseline
fl1=out["generations"].get("fl1")
fl3=out["generations"].get("fl3")
if fl1 and fl3 and fl1["median_total_s"]:
    speed_score=round(100.0*fl1["median_total_s"]/fl3["median_total_s"],1)
    out["speed_score_vs_fl1"]=speed_score
    out["improvement_pct"]=round(100.0*(fl1["median_total_s"]-fl3["median_total_s"])/fl1["median_total_s"],1)
else:
    out["speed_score_vs_fl1"]=None
    out["improvement_pct"]=None
print(json.dumps(out,indent=1))
PY
}

report() {
  compare | python3 -c '
import json,sys
d=json.load(sys.stdin)
print("Fast Lane benchmark (from real run telemetry)\n")
for g in ("fl1","fl2","fl3"):
    info=d.get("generations",{}).get(g)
    if not info: continue
    bc={k:v["median_s"] for k,v in info.get("by_complexity",{}).items()}
    print("  %-4s runs=%-4d median=%7.0fs  mean=%7.0fs  by-complexity=%s" % (
        g.upper(), info["runs"], info["median_total_s"], info["mean_s"], bc))
print("\n  Speed score (FL3 vs FL1): %s" % (d.get("speed_score_vs_fl1")))
print("  Improvement vs FL1:      %s%%" % (d.get("improvement_pct")))
'
}

case "${1:-}" in
  compare) compare;;
  report)  report;;
  *) echo "usage: benchmark.sh {compare|report}" >&2; exit 2;;
esac