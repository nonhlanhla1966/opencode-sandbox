#!/usr/bin/env bash
# find-apk.sh — locate the built debug APK for an app dir.
# Usage: find-apk.sh <app-dir>
set -u
app_dir="${1:?usage: find-apk.sh <app-dir>}"
find "$app_dir/build/outputs/apk" -name '*.apk' -type f 2>/dev/null | sort | head -1