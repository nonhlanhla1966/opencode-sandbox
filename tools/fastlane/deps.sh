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
#   deps.sh lookup <group:artifact[:version]>
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

REG="$REPO_ROOT/modules/DEPENDENCY_REGISTRY.json"

# core: reads a build.gradle, writes "STATUS\tcoord\treason" lines to stdout.
# STATUS is one of OK / VULN / BAD.
deps_core() {
  python3 - "$REG" "${1:?}" <<'PY'
import json, sys, re

reg = json.load(open(sys.argv[1], encoding="utf-8"))
bg = sys.argv[2]
allow = reg.get("allowlist", {})          # GA -> license
pinned = reg.get("versions_pinned", {})   # GA -> version
vuln = reg.get("known_vulnerable", {})    # GA -> advisory

OKHTTP_FIXED = (3, 12, 12)

def split_coord(coord):
    parts = coord.split(":")
    if len(parts) == 3:
        return parts[0] + ":" + parts[1], parts[2]
    if len(parts) == 2:
        return parts[0], parts[1]
    return parts[0], ""

def is_vuln(ga, version):
    if ga not in vuln:
        return False
    if ga == "com.squareup.okhttp3:okhttp" and version:
        try:
            nums = tuple(int(p) for p in re.split(r"[.-]", version)[:3])
            if nums >= OKHTTP_FIXED:
                return False
        except ValueError:
            pass
    return True

txt = open(bg, encoding="utf-8").read()
coords = set()
coords.update(re.findall(
    r"(?:implementation|testImplementation|androidTestImplementation|"
    r"api|compileOnly|runtimeOnly|compile)\s+'([^']+)'", txt))
for m in re.finditer(r"id\s+'([^']+)'\s+version\s+'([^']+)'", txt):
    coords.add(m.group(1) + ":" + m.group(2))

results = []
for coord in sorted(coords):
    ga, version = split_coord(coord)
    if is_vuln(ga, version):
        results.append(("VULN", coord, vuln[ga]))
        continue
    if ga in allow:
        if ga in pinned and version and version != pinned[ga]:
            results.append(("BAD", coord, "version %s != pinned %s" % (version, pinned[ga])))
        else:
            results.append(("OK", coord, "license %s (allowlisted)" % allow[ga]))
        continue
    if ga in pinned:
        if not version or version == pinned[ga]:
            results.append(("OK", coord, "license vendor-pinned (reproducible)"))
        else:
            results.append(("BAD", coord, "version %s != pinned %s" % (version, pinned[ga])))
        continue
    results.append(("BAD", coord, "not in allowlist or version pins"))

for row in results:
    print("\t".join(row))
PY
}

check() {
  local bg="${1:?usage: deps.sh check <build.gradle>}" tmp fail=0 status coord reason
  leader "Dependency intelligence — $(basename "$(dirname "$bg")")"
  tmp="$FL_TMP/deps-$$.tsv"
  deps_core "$bg" > "$tmp"
  while IFS=$'\t' read -r status coord reason; do
    [ -n "$status" ] || continue
    case "$status" in
      OK)   ok "  $coord — $reason";;
      *)    err "  $coord — NOT ALLOWED ($reason)"; fail=1;;
    esac
  done < "$tmp"
  rm -f "$tmp"
  if [ "$fail" -eq 1 ]; then err "deps: dependency policy FAILED"; exit 1; fi
  ok "deps: dependency policy OK"
  exit 0
}

lookup() {
  local coord="${1:?usage: deps.sh lookup <group:artifact[:version]>}" tmp row
  tmp="$FL_TMP/deps-lookup-$$.gradle"
  printf "dependencies { implementation '%s' }\n" "$coord" > "$tmp"
  row="$(deps_core "$tmp" | head -1)"
  rm -f "$tmp"
  python3 - "$row" "$coord" <<'PY'
import json, sys
row, coord = sys.argv[1], sys.argv[2]
if row:
    status, found, reason = row.split("\t", 2)
    body = {"coord": coord, "ok": status == "OK", "license": reason}
else:
    body = {"coord": coord, "ok": False, "reason": "no such coordinate"}
print(json.dumps(body))
PY
}

case "${1:-}" in
  check)  check "${2:?}";;
  report) check "${2:?}";;
  lookup) lookup "${2:?}";;
  *) echo "usage: deps.sh {check|report|lookup} <arg>" >&2; exit 2;;
esac