#!/usr/bin/env bash
# verify-apk.sh — APK_VERIFY step. Validates a built APK with aapt/aapt2.
# Usage: verify-apk.sh <apk> <expected-application-id> [<expected-version-name>]
set -u

apk="${1:?usage: verify-apk.sh <apk> <applicationId> [versionName]}"
expected_id="${2:?applicationId required}"
expected_ver="${3:-}"

aapt=$(command -v aapt || command -v aapt2)
[ -n "$aapt" ] || { echo "verify-apk: aapt/aapt2 not found"; exit 1; }

[ -f "$apk" ] || { echo "verify-apk: missing APK: $apk"; exit 1; }
[ -s "$apk" ] || { echo "verify-apk: empty APK: $apk"; exit 1; }

echo "verify-apk: badging $apk"
if ! badging=$("$aapt" dump badging "$apk" 2>/dev/null); then
  echo "verify-apk: FAILED to read badging (corrupt APK?)" >&2
  "$aapt" dump badging "$apk" >&2
  exit 1
fi

echo "$badging" | grep -E "^package:|launchable-activity|application-label|sdkVersion|targetSdkVersion" || true

pkg=$(echo "$badging" | sed -n "s/.*name='\([^']*\)'.*/\1/p" | head -1)
ver=$(echo "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
launch=$(echo "$badging" | grep -c "launchable-activity:")

fail=0
[ "$pkg" = "$expected_id" ] || { echo "verify-apk: FAIL package=$pkg expected=$expected_id"; fail=1; }
[ -n "${ver:-}" ] && [ "$ver" = "$expected_ver" ] || { echo "verify-apk: WARN version=$ver expected=$expected_ver"; }
[ "$launch" -ge 1 ] || { echo "verify-apk: FAIL no launchable-activity"; fail=1; }

if [ "$fail" -eq 1 ]; then
  echo "verify-apk: FAILED" >&2
  exit 1
fi
echo "verify-apk: OK ($pkg v$ver)"
exit 0