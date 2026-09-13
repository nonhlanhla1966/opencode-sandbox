#!/usr/bin/env bash
# validate-app.sh — LOCAL_VALIDATION structural checks for apps/<slug>.
# Usage: validate-app.sh <app-dir>
# Emits one FAIL line per problem and exits non-zero if any check fails.
set -u

app_dir="${1:?usage: validate-app.sh <app-dir>}"
fail=0

fail_check() { echo "FAIL: $1"; fail=1; }
pass_check() { echo "OK:   $1"; }

[ -d "$app_dir" ] || { echo "FAIL: app dir $app_dir does not exist"; exit 1; }

# -- gradle wrapper ---------------------------------------------------------
[ -f "$app_dir/gradlew" ] && pass_check "gradlew script" || fail_check "$app_dir/gradlew missing"
[ -f "$app_dir/gradle/wrapper/gradle-wrapper.properties" ] && pass_check "wrapper properties" || fail_check "gradle/wrapper/gradle-wrapper.properties missing"
grep -q "distributionUrl" "$app_dir/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null \
  && pass_check "wrapper distributionUrl" || fail_check "wrapper distributionUrl line missing"
[ -f "$app_dir/gradle/wrapper/gradle-wrapper.jar" ] && pass_check "wrapper jar" || fail_check "gradle-wrapper.jar missing"

# -- gradle build files -----------------------------------------------------
[ -f "$app_dir/settings.gradle" ] && pass_check "settings.gradle" || fail_check "settings.gradle missing"
[ -f "$app_dir/build.gradle" ] && grep -qE "com.android.application" "$app_dir/build.gradle" \
  && pass_check "build.gradle has android application plugin" \
  || fail_check "build.gradle missing com.android.application plugin"

# -- manifest ---------------------------------------------------------------
manifest="$app_dir/src/main/AndroidManifest.xml"
[ -f "$manifest" ] && pass_check "AndroidManifest.xml" || fail_check "AndroidManifest.xml missing"
if [ -f "$manifest" ]; then
  grep -q '<manifest' "$manifest" && pass_check "manifest root" || fail_check "manifest root element"
  grep -q 'android:name=".MainActivity"\|<activity' "$manifest" && pass_check "activity declared" || fail_check "no activity declared"
  grep -q 'LAUNCHER' "$manifest" && pass_check "launcher intent" || fail_check "no MAIN/LAUNCHER intent"

  appid=$(sed -n "s/.*applicationId[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$app_dir/build.gradle" | head -1)
  ns=$(sed -n "s/.*namespace[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$app_dir/build.gradle" | head -1)
  if [ -n "$appid" ]; then
    if grep -q 'package=' "$manifest"; then
      grep -q "package=\"$appid\"" "$manifest" && pass_check "manifest package matches applicationId ($appid)" \
        || fail_check "manifest package does not match applicationId ($appid)"
    else
      pass_check "manifest package-less (AGP8); applicationId=$appid via build.gradle"
    fi
  else
    fail_check "applicationId missing from build.gradle"
  fi
  if [ -n "$ns" ]; then
    grep -q "\"$ns\"" "$manifest" && fail_check "namespace should not appear as literal in manifest (use package-less manifest)" \
      || pass_check "manifest is package-less (namespace via gradle)"
  fi
fi

# -- source & resources -----------------------------------------------------
[ -n "$(find "$app_dir/src/main" -name "*.java" 2>/dev/null | head -1)" ] \
  && pass_check "java sources present" || fail_check "no java sources under src/main"
[ -f "$app_dir/src/main/res/layout/activity_main.xml" ] \
  && pass_check "activity_main.xml layout" || fail_check "activity_main.xml missing"
[ -f "$app_dir/src/main/res/values/strings.xml" ] \
  && pass_check "strings.xml" || fail_check "strings.xml missing"
grep -q 'android:icon=' "$manifest" 2>/dev/null && pass_check "manifest icon reference" || fail_check "manifest icon reference missing"

# -- resources well-formedness (aapt2 XML compile) --------------------------
aapt2="${AAPT2:-$(command -v aapt2 || true)}"
if [ -x "$aapt2" ]; then
  tmpdir="$(mktemp -d)"
  reslist="$(mktemp)"
  bad=0
  find "$app_dir/src/main/res" -name '*.xml' -print0 > "$reslist"
  while IFS= read -r -d '' res; do
    rel="${res#"$app_dir"/}"
    if ! "$aapt2" compile "$res" -o "$tmpdir" >/dev/null 2>&1; then
      echo "FAIL: resource XML fails aapt2 compile: $rel"
      bad=1
    fi
  done < "$reslist"
  [ "$bad" -eq 0 ] && pass_check "all resource XML compile with aapt2"
  rm -rf "$tmpdir" "$reslist"
else
  echo "WARN: aapt2 not found; skipping resource compile check"
fi

# -- design-before-code & release info --------------------------------------
[ -f "$app_dir/DESIGN.md" ] && grep -qE "## 1\. Screens|Screens" "$app_dir/DESIGN.md" 2>/dev/null \
  && pass_check "DESIGN.md present with screens section" || fail_check "DESIGN.md missing/without screen design"
[ -f "$app_dir/release.json" ] && pass_check "release.json present" || fail_check "release.json missing"

# -- unit tests -------------------------------------------------------------
[ -n "$(find "$app_dir/src/test" -name "*.java" 2>/dev/null | head -1)" ] \
  && pass_check "unit tests present" || fail_check "no src/test tests"

if [ "$fail" -eq 1 ]; then
  echo "validate-app: FAILED" >&2
  exit 1
fi
echo "validate-app: $app_dir OK"
exit 0