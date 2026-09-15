#!/usr/bin/env bash
# device.sh — Fast Lane 3.0 Device Validation Engine (14. DEVICE VALIDATION).
#
# Exercises an APK on a real device/emulator when one is available. If no
# device/emulator is present, reports an honest SKIPPED with a reason —
# it never silently passes.
#
# Usage: device.sh validate <app-dir> <apk> [package]
# Emits JSON report to stdout and saves to .fastlane/tmp/device-<slug>.json
set -u
# shellcheck source=slib.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

app_dir="${1:?usage: device.sh validate <app-dir> <apk> [package]}"
apk="${2:-}"
pkg="${3:-}"
slug="$(basename "$app_dir")"
[ -z "$pkg" ] && pkg="$(sed -n "s/.*applicationId[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$app_dir/build.gradle" | head -1)"
out="$FL_TMP/device-$slug.json"

adb=""
if command -v adb >/dev/null 2>&1; then adb="$(command -v adb)"; fi
[ -z "$adb" ] && [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ] && adb="$ANDROID_HOME/platform-tools/adb"

reason=""
if [ -z "$adb" ]; then
  reason="no adb on runner (ANDROID_HOME unset or missing platform-tools)"
fi

device_connected=""
if [ -n "$adb" ] && "$adb" get-state >/dev/null 2>&1; then
  device_connected="yes"
fi

report() { # status detail
  local status="$1" detail="$2"
  python3 - "$status" "$detail" "$pkg" "$slug" <<'PY' > "$out"
import json,sys
status,detail,pkg,slug=sys.argv[1:]
print(json.dumps({
  "gate":"C011_DEVICE_TEST","engine":"device.sh","status":status,
  "package":pkg,"slug":slug,"detail":detail,
  "device_available": status!="SKIP",
  "spec_version":"3.0",
}))
PY
  cat "$out"
  case "$status" in
    PASS) ok "  [PASS] C011_DEVICE_TEST — $detail";;
    SKIP) warn "  [SKIP] C011_DEVICE_TEST — $detail";;
    FAIL) err "  [FAIL] C011_DEVICE_TEST — $detail";;
  esac
}

leader "Device validation — $slug"
if [ -z "$apk" ] || [ ! -s "$apk" ]; then
  report SKIP "no apk to install"
  exit 0
fi

if [ -z "$adb" ]; then report SKIP "$reason"; exit 0; fi
if [ -z "$device_connected" ]; then report SKIP "no device/emulator connected (adb get-state failed)"; exit 0; fi

# Install + launch + probe resumed activity (real device available)
if ! "$adb" install -r "$apk" >/dev/null 2>&1; then
  report FAIL "apk install on device failed"
  exit 1
fi
if [ -n "$pkg" ]; then
  "$adb" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 2
  focused="$("$adb" shell dumpsys window 2>/dev/null | grep -oE "mCurrentFocus[^}]*$pkg[^}]*" | head -1)"
  if [ -n "$focused" ]; then
    report PASS "installed + launched; app holds focus ($focused)"
  else
    report FAIL "android: interface crashed or failed to launch on device"
    exit 1
  fi
else
  report PASS "installed on device (no package to probe focus)"
fi
exit 0