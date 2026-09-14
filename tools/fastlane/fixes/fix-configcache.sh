#!/usr/bin/env bash
# fix-configcache.sh — AGP/plugin that cannot store the Gradle configuration
# cache: re-run the build with the feature disabled by tagging the build dir.
# Usage: fix-configcache.sh <build-log> <app-dir>
set -u
log="$1"; wd="$2"
if grep -qiE "configuration cache|problems were found storing" "$log" 2>/dev/null; then
  mkdir -p "$wd/build"
  touch "$wd/build/fastlane.disable-config-cache"
  echo "fix-configcache: disabled configuration cache for next run"
  exit 0
fi
exit 1