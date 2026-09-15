#!/usr/bin/env bash
# knowledge.sh — Fast Lane 3.0 Knowledge/Cache Engine (17. KNOWLEDGE/CACHE).
#
# Persistent cross-run knowledge: successful module integrations, repair
# history, build-time history, and selectively reusable caches. All state
# lives under .fastlane/ (never committed as secrets).
#
# Usage:
#   knowledge.sh record <kind> <json-file>       append knowledge record
#   knowledge.sh lookup <kind> <key>            print matching records (JSON)
#   knowledge.sh report                          summary of stored knowledge
# Kinds: build        (successful build TE data)
#        module       (module integration success)
#        reset
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

KIND_DIR="$FASTLANE_DATA/knowledge"
mkdir -p "$KIND_DIR"

record() {
  local kind="${1:?usage: knowledge.sh record <kind> <json-file>}"
  local file="${2:?}"
  [ -f "$file" ] || die "knowledge payload missing: $file"
  python3 - "$kind" "$file" "$KIND_DIR" <<'PY'
import json,sys,os,time
kind,file,base=sys.argv[1:4]
try:
    rec=json.load(open(file))
except Exception as e:
    print(f"knowledge: bad payload {e}",file=sys.stderr); sys.exit(1)
stream=os.path.join(base,f"{kind}.jsonl")
rec.setdefault("_at",time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()))
with open(stream,"a") as fh:
    fh.write(json.dumps(rec)+"\n")
print(f"knowledge: recorded {kind} record")
PY
}

lookup() {
  local kind="${1:?}" key="${2:?}"
  python3 - "$kind" "$key" "$KIND_DIR" <<'PY'
import json,sys,os
kind,key,base=sys.argv[1:4]
fpath=os.path.join(base,f"{kind}.jsonl")
out=[]
if os.path.exists(fpath):
    for line in open(fpath):
        line=line.strip()
        if not line: continue
        try:
            r=json.loads(line)
        except Exception: continue
        if key in str(r).lower():
            out.append(r)
out=out[-50:]
print(json.dumps(out,indent=1))
PY
}

report() {
  python3 - "$KIND_DIR" <<'PY'
import json,sys,os,glob
base=sys.argv[1]
kinds=[]
for f in sorted(glob.glob(os.path.join(base,"*.jsonl"))):
    kind=os.path.basename(f).replace(".jsonl","")
    n=sum(1 for _ in open(f))
    kinds.append({"kind":kind,"records":n})
print(json.dumps({"knowledge":kinds,"dir":base},indent=1))
PY
}

case "${1:-}" in
  record) shift; record "$@";;
  lookup) shift; lookup "$@";;
  report) report;;
  *) echo "usage: knowledge.sh {record|lookup|report}" >&2; exit 2;;
esac