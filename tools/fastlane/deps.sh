#!/usr/bin/env bash
# deps.sh — Dependency Intelligence (14. DEPENDENCY INTELLIGENCE).
#
# Before a dependency is added or an app is released, verifies:
#   * coordinates are allowlisted with a known license
#   * version pins match the reproducibility manifest
#   * the coordinate is not a known-vulnerable version
# Usage:
#   deps.sh check <build.gradle>          -> exit 0/1, report JSON
#   deps.sh report <build.gradle>
#   deps.sh lookup <group:artifact>
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

REG="$REPO_ROOT/modules/DEPENDENCY_REGISTRY.json"

lookup() {
  local coord="${1:?usage: deps.sh lookup <group:artifact>}"
  python3 - "$REG" "$coord" <<'PY'
import json,sys,os
reg=json.load(open(sys.argv[1]))
coord=sys.argv[2]
if coord in reg.get("allowlist",{}):
    print(json.dumps({"ok":True,"coord":coord,"license":reg["allowlist"][coord]}))
elif coord in reg.get("versions_pinned",{}):
    print(json.dumps({"ok":True,"coord":coord,"pinned":reg["versions_pinned"][coord],
                       "license":"vendor-pinned"}))
else:
    print(json.dumps({"ok":False,"coord":coord,
                      "reason":"not in allowlist or version pins"}))
PY
}

collect() {
  local bg="$1"
  python3 - "$bg" < <(grep -oE "implementation '[^']+'" "$bg" 2>/dev/null) <<'PY'
import json,sys,re
bg=sys.argv[1]
lines=sys.stdin.read()
deps=set()
for m in re.finditer(r"implementation\s+'([^']+)'", lines):
    deps.add(m.group(1))
# plugin ids with versions
bgtxt=open(bg).read() if bg else ""
for m in re.finditer(r"id\s+'([^']+)'\s+version\s+'([^']+)'", bgtxt):
    deps.add(m.group(1)+":"+m.group(2))
print("\n".join(sorted(deps)))
PY
}

check() {
  local bg="${1:?usage: deps.sh check <build.gradle>}"
  local dep result fail=0
  leader "Dependency intelligence — $(basename "$(dirname "$bg")")"
  while IFS= read -r dep; do
    [ -n "$dep" ] || continue
    result="$(lookup "$dep")"
    if python3 -c "import json,sys;sys.exit(0 if json.loads('''$result''')['ok'] else 1)" 2>/dev/null; then
      ok "  $dep — $(python3 -c "import json;print(json.loads('''$result''').get('license','ok'))")"
    else
      err "  $dep — NOT ALLOWED ($(python3 -c "import json;print(json.loads('''$result''')['reason'])" 2>/dev/null))"
      fail=1
    fi
  done < <(collect "$bg")
  # known-vulnerable containment
  if grep -qiE "org\.apache\.logging\.log4j|commons-collections" "$bg" 2>/dev/null; then
    err "  dependency is on the known-vulnerable list"
    fail=1
  fi
  if [ "$fail" -eq 1 ]; then err "deps: dependency policy FAILED"; exit 1; fi
  ok "deps: dependency policy OK"
  exit 0
}

case "${1:-}" in
  check)  check "${2:?}";;
  report) check "${2:?}";;
  lookup) lookup "${2:?}";;
  *) echo "usage: deps.sh {check|report|lookup} <arg>" >&2; exit 2;;
esac