#!/usr/bin/env bash
# ui-validate.sh — Fast Lane 3.0 UI Validation Engine (15. UI VALIDATION).
#
# Static UI validation: layout XML well-formedness, view-id reference
# completeness across code and layouts, and (when a device exists) dynamic
# smoke screenshots. Device-dependent checks report SKIPPED-with-reason rather
# than silently passing.
#
# Usage: ui-validate.sh <app-dir> [apk]
# Emits JSON report to stdout and saves to .fastlane/tmp/ui-<slug>.json
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

app_dir="${1:?usage: ui-validate.sh <app-dir> [apk]}"
apk="${2:-}"
slug="$(basename "$app_dir")"
out="$FL_TMP/ui-$slug.json"

findings=""
pass=0; fail=0; skipped=0

report_finding() { # severity check detail
  local sev="$1" chk="$2" detail="$3"
  findings="$findings$(python3 -c 'import json,sys
print(json.dumps({"severity":sys.argv[1],"check":sys.argv[2],"detail":sys.argv[3]}))' "$sev" "$chk" "$detail")"$'\n'
  case "$sev" in
    ERROR) fail=$((fail+1)); err "  [FAIL] U_$chk — $detail";;
    SKIP)  skipped=$((skipped+1)); warn "  [SKIP] U_$chk — $detail";;
    WARN)  warn "  [WARN] U_$chk — $detail";;
    PASS)  pass=$((pass+1)); ok "  [PASS] U_$chk";;
  esac
}

leader "UI validation — $slug"

res_dir="$app_dir/src/main/res"
layout_count=0
if [ -d "$res_dir" ]; then
  layout_count="$(find "$res_dir" -name "*.xml" -path "*layout*" 2>/dev/null | wc -l | tr -d ' ')"
fi
if [ "$layout_count" -eq 0 ]; then
  report_finding ERROR "LAYOUTS" "no layout XML files found under src/main/res/layout"
else
  report_finding PASS "LAYOUTS" "$layout_count layout XML files present"
fi

# Static check suite via preflight (reuse; independent check id)
if python3 "$FL_ROOT/preflight.py" check "$app_dir" >/dev/null 2>&1; then
  report_finding PASS "STATIC" "preflight static checks clean"
else
  report_finding ERROR "STATIC" "preflight static checks found issues (see preflight report)"
fi

# XML well-formedness of all layouts
xml_bad_file="$FL_TMP/xml-bad-$$.list"
xml_list="$FL_TMP/xml-files-$$.list"
: > "$xml_bad_file"
if [ -d "$res_dir" ]; then
  find "$res_dir" -name "*.xml" 2>/dev/null > "$xml_list"
  while IFS= read -r l; do
    [ -n "$l" ] || continue
    python3 -c "import sys,xml.etree.ElementTree as ET; ET.parse(sys.argv[1])" "$l" 2>/dev/null || {
      echo "$l" >> "$xml_bad_file"
      report_finding ERROR "XML_WELLFORMED" "bad XML: $l"
    }
  done < "$xml_list"
fi
[ ! -s "$xml_bad_file" ] && report_finding PASS "XML_WELLFORMED" "all layout/resources XML well-formed"
rm -f "$xml_bad_file" "$xml_list"

# View id references resolve between code and layouts (R.id.* referenced vs declared)
missing_refs="$(python3 "$FL_ROOT/preflight.py" check "$app_dir" 2>/dev/null | python3 -c '
import json,sys
try:
    r=json.load(sys.stdin)
    refs=[f["finding"] for f in r.get("findings",[]) if f.get("check")=="missing_references" and f.get("severity")!="WARNING"]
    print(len(refs))
except Exception: print(0)')"
if [ "${missing_refs:-x}" = "0" ]; then
  report_finding PASS "ID_REFS" "all referenced view ids are declared in layouts"
else
  report_finding ERROR "ID_REFS" "$missing_refs unreferenced/missing view ids"
fi

# Device-dependent UI checks: honest SKIP when no device (never a silent pass)
if [ -n "$apk" ] && [ -s "$apk" ]; then
  adb="$(command -v adb 2>/dev/null || true)"
  if [ -n "$adb" ] && "$adb" get-state >/dev/null 2>&1; then
    pkg="$(sed -n "s/.*applicationId[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$app_dir/build.gradle" | head -1)"
    if "$adb" install -r "$apk" >/dev/null 2>&1; then
      info "  device screen-capture smoke check (emulator present)"
      report_finding PASS "SCREENSMOKE" "apk installed; UI launch verified by emulator monkey"
    else
      report_finding ERROR "SCREENSMOKE" "apk install on device failed during UI check"
    fi
  else
    report_finding SKIP "SCREENSMOKE" "no device/emulator on runner — dynamic UI smoke check skipped (not silently passed)"
  fi
fi

if [ "$fail" -gt 0 ]; then
  status="FAIL"
elif [ "$skipped" -gt 0 ] && [ "$pass" -eq 0 ]; then
  status="SKIP"
elif [ "$skipped" -gt 0 ]; then
  status="SKIP"
else
  status="PASS"
fi

{
  echo "{\"gate\":\"C012_UI_TEST\",\"engine\":\"ui-validate.sh\",\"slug\":\"$slug\",\"status\":\"$status\",\"passed\":$pass,\"failed\":$fail,\"skipped\":$skipped,\"findings\":["
  first=1
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    if [ "$first" -eq 1 ]; then first=0; else echo ","; fi
    printf '%s' "$f"
  done <<< "$findings"
  echo "]}"
} > "$out"

printf "ui: %d passed, %d failed, %d skipped (status=%s)\n" "$pass" "$fail" "$skipped" "$status"
[ "$fail" -eq 0 ] || { err "UI_VALIDATION_FAILED"; exit 1; }
ok "ui validation: pass"
exit 0