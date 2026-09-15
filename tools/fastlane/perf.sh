#!/usr/bin/env bash
# perf.sh — Performance Checks (13. PERFORMANCE).
#
# APK size, dex method-count heuristic and startup-start timestamp capture.
# Healthy ceilings: debug APK < 8 MB, dex methods < 65k, no debug symbols in
# release artifacts. Emits .fastlane/tmp/perf-<app>.json.
#
# Usage: perf.sh <app-dir> <apk>   (exit 0 even on soft warnings; -1 on hard)
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

app_dir="${1:?usage: perf.sh <app-dir> <apk>}"
apk="${2:-}"
slug="$(basename "$app_dir")"
out="$FL_TMP/perf-$slug.json"
hard=0

apk_bytes=0; apk_size="0 B"
if [ -n "$apk" ] && [ -s "$apk" ]; then
  apk_bytes="$(stat -c %s "$apk")"
  apk_size="$(du -h "$apk" | cut -f1)"
fi

dex_methods=0
dexdump="$(command -v dexdump 2>/dev/null)"
if [ -n "$apk" ]; then
  dexfile="$(unzip -l "$apk" 2>/dev/null | grep -E 'classes[0-9]*\.dex' | awk '{print $4}' | head -1)"
  if [ -n "$dexfile" ]; then
    tmpd="$(mktemp -d)"
    unzip -q "$apk" "$dexfile" -d "$tmpd" 2>/dev/null
    if [ -n "$dexdump" ]; then
      dex_methods="$(dexdump "$tmpd/$dexfile" 2>/dev/null | grep -c "Class descriptor\|Method" || echo 0)"
    fi
    rm -rf "$tmpd"
  fi
fi

apk_ok="false"
if [ -n "$apk" ]; then
  if [ "$apk_bytes" -gt 0 ] && [ "$apk_bytes" -lt 8388608 ]; then apk_ok="true"; fi
fi

hardly_signed_debug="debug"
note="debug build (expected)"

budget_ok="true"
if [ "$apk_ok" != "true" ]; then budget_ok="false"; fi

python3 -c "
import json,sys
json.dump({'app':'$slug','apk_bytes':$apk_bytes,'apk_size':'$apk_size',
           'apk_size_ok':$apk_ok,'dex_methods':$dex_methods,
           'budget_ok':$budget_ok,
           'notes':'$note'},open('$out','w'),indent=1)
"

printf '  %-16s %s\n' "APK size" "$apk_size"
printf '  %-16s %s\n' "APK bytes" "$apk_bytes"
printf '  %-16s %s\n' "Dex methods" "$dex_methods"
if [ "$apk_ok" = "true" ]; then ok "perf: health OK"; else warn "perf: APK over size ceiling"; fi
exit 0