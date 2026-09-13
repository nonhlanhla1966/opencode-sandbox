#!/usr/bin/env bash
# release.sh — RELEASE step. Publishes a rolling "app-<slug>-latest" GitHub
# Release with the built APK and SHA256SUMS, idempotently.
# Usage: release.sh <app-dir>
# Env: GH_TOKEN (or GITHUB_TOKEN), GITHUB_REPOSITORY, GITHUB_SHA
set -u

app_dir="${1:?usage: release.sh <app-dir>}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}"
sha="${GITHUB_SHA:?GITHUB_SHA required}"

slug="$(basename "$app_dir")"
apk="$app_dir/build/outputs/apk/debug/app-debug.apk"
tag="app-$slug-latest"
title="[AppFactory] $slug — latest debug build"

[ -f "$apk" ] || { echo "release: APK not found: $apk" >&2; exit 1; }

apk_name="$(basename "$apk")"
checksums="$(mktemp)"
sha256sum "$apk" | tee "$checksums"

echo "release: publishing release $tag"
# Idempotent: remove any previous run of this rolling release (tag included).
if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
  gh release delete "$tag" --repo "$repo" --yes --cleanup-tag || { echo "release: delete failed" >&2; exit 1; }
fi

notes="Built and verified by the AppFactory pipeline for **$slug**. Debug-signed APK (installable), SHA-256 in *SHA256SUMS*."
if ! gh release create "$tag" --repo "$repo" --target "$sha" \
     --title "$title" --notes "$notes" \
     -- "$apk" "$checksums"; then
  echo "release: create failed" >&2
  exit 1
fi

url="https://github.com/$repo/releases/download/$tag/$apk_name"
echo "release-url=$url"
echo "release: OK — $url"
exit 0