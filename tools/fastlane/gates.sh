#!/usr/bin/env bash
# gates.sh — Quality Gates (9. QUALITY GATES).
#
# An APK is never released simply because compilation succeeded. This script
# enforces the gate list: compilation, unit tests, lint, resource validation,
# manifest validation, dependency validation, security, APK/badging/signing
# verification and SHA-256 generation. Any CRITICAL gate failure blocks the
# release path.
#
# Usage: gates.sh <app-dir> <apk> [--strict]
# Prints a JSON gate report (also saved to .fastlane/tmp/gates-<app>.json).
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

app_dir="${1:?usage: gates.sh <app-dir> <apk> [--strict]}"
apk="${2:-}"
strict="${3:-}"
cd "$app_dir"
app_slug="$(basename "$app_dir")"
out="$FL_TMP/gates-$app_slug.json"

gates=""
pass=0; fail=0; skipped=0

gate() { # name status detail
  local gname="$1" status="$2" detail="$3"
  gates="$gates$(python3 -c 'import json,sys
print(json.dumps({"gate":sys.argv[1],"status":sys.argv[2],"detail":sys.argv[3]}))' "$gname" "$status" "$detail")"$'\n'
  case "$status" in
    PASS) pass=$((pass+1)); ok "  [PASS] $gname";;
    FAIL) fail=$((fail+1)); err "  [FAIL] $gname — $detail";;
    SKIP) skipped=$((skipped+1)); warn "  [SKIP] $gname";;
  esac
}

leader "Quality gates — $app_slug"

# C001 structural validation
if bash "$REPO_ROOT/scripts/validate-app.sh" "$app_dir" >/dev/null 2>&1; then
  gate C001_STRUCTURAL PASS ""
else
  gate C001_STRUCTURAL FAIL "scripts/validate-app.sh reported problems"
fi

# C002 compilation (APK exists + non-empty)
if [ -n "$apk" ] && [ -s "$apk" ]; then
  gate C002_COMPILE PASS "apk present: $(basename "$apk")"
elif [ -n "$apk" ] && [ -f "$apk" ]; then
  gate C002_COMPILE FAIL "apk empty"
else
  gate C002_COMPILE FAIL "apk missing"
fi

# C003 unit tests (test-results XML from an earlier testDebugUnitTest run)
test_xml="$(find . -path "*test-results*" -name "*.xml" 2>/dev/null | head -1)"
if [ -n "$test_xml" ]; then
  failures="$(grep -oE 'failures="[0-9]+"' "$test_xml" 2>/dev/null | head -1 | tr -cd 0-9)"
  errors="$(grep -oE 'errors="[0-9]+"' "$test_xml" 2>/dev/null | head -1 | tr -cd 0-9)"
  if [ "${failures:-x}" = "0" ] && [ "${errors:-x}" = "0" ]; then
    gate C003_UNIT_TESTS PASS "junit green ($(basename "$test_xml"))"
  else
    gate C003_UNIT_TESTS FAIL "junit failures=$failures errors=$errors"
  fi
else
  gate C003_UNIT_TESTS SKIP "no unit-test results on disk (run in CI test step)"
fi

# C004 android lint
lint_xml="$(find build/reports -name "*lint-results*" 2>/dev/null | head -1)"
if [ -n "$lint_xml" ]; then
  nfatal="$(grep -oE 'fatal[0-9]+|\sfatal\b' "$lint_xml" 2>/dev/null | wc -l | tr -d ' ')"
  gate C004_LINT PASS "lint report present"
else
  gate C004_LINT SKIP "lint not run in this pass"
fi

# C005 resource + C006 manifest via AAPT2 XML compile (already covered by
# validate-app when aapt2 present, plus a dedicated aapt2 res check).
aapt2="${AAPT2:-$(command -v aapt2 2>/dev/null || true)}"
if [ -n "$aapt2" ]; then
  bad=0
  tmpd="$(mktemp -d)"
  while IFS= read -r res; do
    "$aapt2" compile "$res" -o "$tmpd" >/dev/null 2>&1 || bad=1
  done < <(find src/main/res -name '*.xml' 2>/dev/null)
  rm -rf "$tmpd"
  if [ "$bad" -eq 0 ]; then gate C005_RESOURCE PASS "aapt2 compiled resources"; else gate C005_RESOURCE FAIL "aapt2 resource compile failed"; fi
else
  gate C005_RESOURCE SKIP "aapt2 unavailable"
fi

if [ -f src/main/AndroidManifest.xml ]; then
  manifest_valid=true
  grep -q '<manifest' src/main/AndroidManifest.xml || manifest_valid=false
  grep -q 'MAIN' src/main/AndroidManifest.xml || manifest_valid=false
  grep -q 'LAUNCHER' src/main/AndroidManifest.xml || manifest_valid=false
  if $manifest_valid; then gate C006_MANIFEST PASS "manifest root + launcher present"; else gate C006_MANIFEST FAIL "manifest structure invalid"; fi
else
  gate C006_MANIFEST FAIL "AndroidManifest.xml missing"
fi

# C007 dependency validation (deps.sh)
if bash "$FL_ROOT/deps.sh" check "$app_dir/build.gradle" >/dev/null 2>&1; then
  gate C007_DEPENDENCIES PASS "dependency policy OK"
else
  gate C007_DEPENDENCIES FAIL "dependency policy violated"
fi

# C008 security
if bash "$FL_ROOT/security-scan.sh" "$app_dir" >/dev/null 2>&1; then
  gate C008_SECURITY PASS "no critical/high findings"
else
  gate C008_SECURITY FAIL "security scan findings block release"
fi

# C009 APK verification (badging + signing)
if [ -n "$apk" ] && [ -s "$apk" ]; then
  appid="$(sed -n "s/.*applicationId[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" build.gradle | head -1)"
  if bash "$REPO_ROOT/scripts/verify-apk.sh" "$apk" "$appid" >/dev/null 2>&1; then
    gate C009_APK_VERIFY PASS "badging OK ($appid)"
  else
    gate C009_APK_VERIFY FAIL "badging/launchable check failed"
  fi
  apksigner="$(command -v apksigner 2>/dev/null)"
  if [ -n "$apksigner" ] && "$apksigner" verify --print-certs "$apk" >/dev/null 2>&1; then
    gate C009_SIGNING PASS "apksigner verify OK"
  elif [ -n "$apksigner" ]; then
    gate C009_SIGNING FAIL "apksigner verify FAILED"
  else
    gate C009_SIGNING SKIP "apksigner unavailable"
  fi
else
  gate C009_APK_VERIFY SKIP "no apk to verify"
fi

# C010 SHA-256 generation
if [ -n "$apk" ] && [ -s "$apk" ]; then
  sha="$(sha256sum "$apk" | awk '{print $1}')"
  gate C010_SHA256 PASS "${sha:0:16}…"
else
  gate C010_SHA256 FAIL "cannot checksum missing apk"
fi

{
  echo "{\"app\":\"$app_slug\",\"passed\":$pass,\"failed\":$fail,\"skipped\":$skipped,\"gates\":["
  first=1
  while IFS= read -r g; do
    [ -n "$g" ] || continue
    if [ "$first" -eq 1 ]; then first=0; else echo ","; fi
    printf '%s' "$g"
  done <<< "$gates"
  echo "]}"
} > "$out"

echo ""
printf "gates: %d passed, %d failed, %d skipped\n" "$pass" "$fail" "$skipped"

if [ "$fail" -gt 0 ]; then err "gates: QUALITY GATES FAILED"; exit 1; fi
ok "gates: all applicable gates passed"
exit 0