#!/usr/bin/env bash
# note.sh — DOWNLOAD_READY / failure comment on an issue.
# Usage: note.sh <issue-number> <body-file-or-message>
# Env: GH_TOKEN (or GITHUB_TOKEN), GITHUB_REPOSITORY
set -u

issue="${1:?usage: note.sh <issue-number> <message>}"
message="${2:-}"

repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}"
[ "$issue" != "null" ] && [ -n "$issue" ] && [ "$issue" -gt 0 ] 2>/dev/null || { echo "note: no valid issue number, skipping"; exit 0; }

if [ -f "$message" ]; then
  body="$(cat "$message")"
else
  body="$message"
fi

gh api "repos/$repo/issues/$issue/comments" -f body="$body" >/dev/null \
  && echo "note: comment posted on issue #$issue" \
  || { echo "note: failed to post comment on issue #$issue" >&2; exit 1; }