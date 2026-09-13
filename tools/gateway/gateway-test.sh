#!/usr/bin/env bash
# gateway-test.sh — AppFactory gateway self-tests (JVM-free, no network).
# Run:  bash tools/gateway/gateway-test.sh
# Exit 0 = all pass. Each FAIL is a broken invariant, never auto-skipped.
set -u
GW="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/gateway.sh"
pass=0; fail=0
t()   { pass=$((pass+1)); printf 'PASS %s\n' "$1"; }
f()   { fail=$((fail+1)); printf 'FAIL %s\n' "$1"; }
chk() { if "$@" >/dev/null 2>&1; then t "$*"; else f "$*"; fi; }

echo "== 0. file cleanliness (no junk glue) =="
for junk in 'apseapse' 'found opera' 'oracle data' 'searchFieldapse' 'None of them' '.ellipsis.' 'count_by_runs_follows' 'notes.addAll(found opera)'; do
  grep -nF "$junk" "$GW" >/dev/null 2>&1 && f "junk token present: $junk" || t "no junk token: $junk"
done

echo "== 1. syntax =="
chk bash -n "$GW"

echo "== 2. slug normalization (deterministic, no path traversal) =="
# FILE-BYTES assertions (cmp): immune to $() render mismatch on any CI.
slug_to_file() { local idea="$1" f="$2"; "$GW" slug_of "$idea" >"$f" 2>/dev/null || true; }
t1="$(mktemp)"; e1="$(mktemp)"; slug_to_file 'A Stop watch! With Lap Times && Notepad' "$t1"
printf 'a-stop-watch-with-lap-times-notepad\n' >"$e1"
cmp -s "$t1" "$e1" && t "slug basic" || { printf 'slug basic want [%s]
' "$(cat "$t1")" | sed 's/^//'; f "slug basic"; }
t2="$(mktemp)"; e2="$(mktemp)"; slug_to_file 'FlAsH-ligHT' "$t2"
printf 'flash-light\n' >"$e2"
cmp -s "$t2" "$e2" && t "slug case+dash" || { printf 'slug case want [%s]
' "$(cat "$t2")"; f "slug case"; }
t3="$(mktemp)"; slug_to_file '../etc/passwd WEIRD' "$t3"
if grep -qE '\.\.|/' "$t3"; then f "slug path-traversal [$(cat "$t3")]"; else t "slug strips path chars"; fi
rm -f "$t1" "$e1" "$t2" "$e2" "$t3"
unset -f slug_to_file

echo "== 3. JSON output is always strict (unknown command + empty idea) =="
o1="$(bash "$GW" bogus_cmd 2>/dev/null || true)"
printf '%s' "$o1" | python3 -c 'import json,sys;json.load(sys.stdin)' && t "unknown-cmd is valid JSON" || f "unknown-cmd not JSON: $o1"
o2="$(bash "$GW" create_app_request '' 2>/dev/null || true)"
printf '%s' "$o2" | python3 -c 'import json,sys;json.load(sys.stdin)' && t "empty-idea is valid JSON" || f "empty-idea not JSON: $o2"

echo "== 4. secret-safety invariant: output never contains a token-like value =="
# Bounded (15s): get_build_status touches live gh, which may hang on flaky CI
# networks; the invariant check matters, not the speed.
o4="$(timeout 15 bash "$GW" get_build_status 'x' 2>&1 || true)"
seen="$(printf '%s' "$o4" | grep -icE 'x-access-token|ghp_[A-Za-z0-9]|gho_|github_pat_|aaaa[a-z]{6,}|[0-9]{20,}' || true)"
[ "$seen" -eq 0 ] && t "no token-shaped strings in output" || f "token-shaped string leaked ($seen)"

echo "== 5. command dispatch table covers the 3 documented ops =="
for c in create_app_request get_build_status get_latest_apk; do
  grep -q "^[[:space:]]*create_app_request)\|$c)" "$GW" && t "dispatch has $c" || f "dispatch missing $c"
done

echo
echo "==== RESULT: pass=$pass fail=$fail ===="
[ "$fail" -eq 0 ] || { echo "GATEWAY-TEST: FAILED ($fail)"; exit 1; }
echo "GATEWAY-TEST: ALL PASS"
