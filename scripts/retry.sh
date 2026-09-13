#!/usr/bin/env bash
# retry.sh — run a command, retrying up to N times on failure.
# Usage: retry.sh <attempts> <command...>
set -u

attempts="${1:?usage: retry.sh <attempts> <command...>}"
shift
max="$attempts"
n=1
while true; do
  echo "[retry] attempt $n/$max: $*"
  if "$@"; then
    echo "[retry] success on attempt $n"
    exit 0
  fi
  if [ "$n" -ge "$max" ]; then
    echo "[retry] FAILED after $max attempts: $*" >&2
    exit 1
  fi
  n=$((n + 1))
  sleep 5
done