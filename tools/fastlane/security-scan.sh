#!/usr/bin/env bash
# security-scan.sh — Fast Lane security scanner (10. SECURITY).
#
# Static scan of an app dir (source + manifest + resources + dependency pins).
# Emits a JSON report; exits non-zero on CRITICAL/HIGH findings.
# Usage: security-scan.sh <app-dir>
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

app_dir="${1:?usage: security-scan.sh <app-dir>}"
report="$FL_TMP/security-$(basename "$app_dir").json"
findings=()

add() { # severity name evidence where
  findings+=("$(python3 -c 'import json,sys
print(json.dumps({"severity":sys.argv[1],"name":sys.argv[2],
                  "evidence":sys.argv[3],"where":sys.argv[4]}))' "$1" "$2" "$3" "$4")")
}

scan_src() {
  local src_dir="$app_dir/src"
  [ -d "$src_dir" ] || return 0
  local f rel line tmp
  tmp="$FL_TMP/security-files-$$.txt"
  find "$src_dir" -type f \( -name '*.java' -o -name '*.xml' -o -name '*.kt' \) 2>/dev/null > "$tmp"
  while IFS= read -r f; do
    rel="${f#"$app_dir"/}"
    if grep -lE "ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|BEGIN (RSA )?PRIVATE KEY|x-access-token[: ]" "$f" >/dev/null 2>&1; then
      add "CRITICAL" "hardcoded_secret_token" "token-shaped literal" "$rel"
    fi
    if grep -qE "api[_-]?key[[:space:]]*=[[:space:]]*[\"'][^\"']{8,}|password[[:space:]]*=[[:space:]]*[\"'][^\"']{8,}|secret[[:space:]]*=[[:space:]]*[\"'][^\"']{8,}" "$f" 2>/dev/null; then
      add "HIGH" "hardcoded_credential_assignment" "key/password/secret literal" "$rel"
    fi
    # Plaintext http URLs — but whitelist standard schema/namespace declarations
    # (Android XML ns, W3C, Gradle, etc.) which are not cleartext traffic.
    HTTP_WHITELIST='xmlns|schemas\.android\.com|www\.w3\.org|www\.gradle\.org|schemas\.microsoft\.com|apache\.org/licenses|schemas\.xmlsoap\.org'
    if grep -nE "http://" "$f" 2>/dev/null | grep -Ev "$HTTP_WHITELIST" | head -1 | grep -q .; then
      line="$(grep -nE "http://" "$f" 2>/dev/null | grep -Ev "$HTTP_WHITELIST" | head -1)"
      add "MEDIUM" "insecure_http_url" "plaintext http: $line" "$rel"
    fi
    if grep -q "setJavaScriptEnabled(true)" "$f" 2>/dev/null; then
      add "MEDIUM" "webview_javascript" "WebView JS enabled" "$rel"
      if grep -qE "setAllowFileAccess\(true\)|addJavascriptInterface" "$f" 2>/dev/null; then
        add "HIGH" "webview_file_access_or_bridge" "FileAccess or JS bridge exposed" "$rel"
      fi
    fi
    if grep -q "MODE_WORLD_READABLE\|MODE_WORLD_WRITEABLE" "$f" 2>/dev/null; then
      add "CRITICAL" "world_readable_storage" "MODE_WORLD_* usage" "$rel"
    fi
    if grep -qE "Log\.[dviw]\([^,]+,\s*(apiKey|token|password|secret|api_key)" "$f" 2>/dev/null; then
      add "HIGH" "sensitive_logging" "Logging a credential variable" "$rel"
    fi
  done < "$tmp"
  rm -f "$tmp"
}

scan_manifest() {
  local m="$app_dir/src/main/AndroidManifest.xml"
  [ -f "$m" ] || return 0
  grep -q 'usesCleartextTraffic="true"' "$m" 2>/dev/null && add "HIGH" "cleartext_traffic" "usesCleartextTraffic=true" "AndroidManifest.xml"
  grep -q 'allowBackup="true"' "$m" 2>/dev/null && add "LOW" "backup_enabled" "allowBackup=true" "AndroidManifest.xml"
  if grep -q 'networkSecurityConfig' "$m" 2>/dev/null; then
    grep -q 'cleartextTrafficPermitted="true"' "$m" 2>/dev/null && add "HIGH" "cleartext_network_policy" "cleartext permitted in XML" "AndroidManifest.xml"
  fi
  for p in CAMERA RECORD_AUDIO ACCESS_FINE_LOCATION READ_CONTACTS READ_SMS BLUETOOTH_CONNECT; do
    if grep -q "permission.$p" "$m" 2>/dev/null; then
      add "INFO" "sensitive_permission" "declares $p" "AndroidManifest.xml"
    fi
  done
  if grep -qE 'android:exported="true"' "$m" 2>/dev/null && ! grep -qE 'android:permission="' "$m" 2>/dev/null; then
    add "MEDIUM" "exported_without_permission" "exported component lacks android:permission" "AndroidManifest.xml"
  fi
}

scan_deps() {
  local bg="$app_dir/build.gradle" tmp
  [ -f "$bg" ] || return 0
  local reg="$REPO_ROOT/modules/DEPENDENCY_REGISTRY.json"
  tmp="$FL_TMP/security-deps-$$.txt"
  python3 - "$bg" "$reg" > "$tmp" <<'PY'
import json, sys, re
bg, reg_path = sys.argv[1], sys.argv[2]
reg = json.load(open(reg_path, encoding="utf-8"))
vuln = reg.get("known_vulnerable", {})
txt = open(bg, encoding="utf-8").read()
coords = re.findall(
    r"(?:implementation|testImplementation|androidTestImplementation|"
    r"api|compileOnly|runtimeOnly)\s+'([^']+)'", txt)
for coord in coords:
    ga = ":".join(coord.split(":")[:2])
    if ga in vuln:
        print("\t".join(("HIGH", "known_vulnerable_dependency", coord, vuln[ga])))
PY
  while IFS=$'\t' read -r sev name coord note; do
    [ -n "$name" ] || continue
    add "$sev" "$name" "$coord ($note)" "build.gradle"
  done < "$tmp"
  rm -f "$tmp"
}

scan_metadata() {
  local rj="$app_dir/release.json"
  if [ -f "$rj" ] && grep -qE "sk-[A-Za-z0-9]|ghp_[A-Za-z0-9]|BEGIN .*PRIVATE" "$rj" 2>/dev/null; then
    add "CRITICAL" "secret_in_release_json" "release.json contains secret-like material" "release.json"
  fi
  if [ -f "$app_dir/local.properties" ] && grep -q "sdk.dir" "$app_dir/local.properties" 2>/dev/null; then
    add "LOW" "local_properties_committed" "sdk.dir committed" "local.properties"
  fi
}

scan_src
scan_manifest
scan_deps
scan_metadata

critical=0; high=0; medium=0; low=0; info=0
for f in "${findings[@]:-}"; do
  sev="$(python3 -c "import json;print(json.loads('''${f}''')['severity'])")"
  case "$sev" in
    CRITICAL) critical=$((critical+1));;
    HIGH) high=$((high+1));;
    MEDIUM) medium=$((medium+1));;
    LOW) low=$((low+1));;
    *) info=$((info+1));;
  esac
done

{
  echo "{\"scan\":\"security\",\"critical\":$critical,\"high\":$high,"
  echo "\"medium\":$medium,\"low\":$low,\"info\":$info,"
  echo "\"findings\":["
  first=1
  for f in "${findings[@]:-}"; do
    if [ "$first" -eq 1 ]; then first=0; else echo ","; fi
    printf '%s' "$f"
  done
  echo "]}"
} > "$report"

if [ "${#findings[@]:-0}" -gt 0 ]; then
  printf '%-9s %-34s %s\n' "SEV" "FINDING" "WHERE" >&2
  for f in "${findings[@]:-}"; do
    name="$(python3 -c "import json;print(json.loads('''${f}''')['name'])")"
    where="$(python3 -c "import json;print(json.loads('''${f}''')['where'])")"
    sev="$(python3 -c "import json;print(json.loads('''${f}''')['severity'])")"
    printf '%-9s %-34s %s\n' "$sev" "$name" "$where" >&2
  done
fi

if [ "$critical" -gt 0 ] || [ "$high" -gt 0 ]; then
  err "security: CRITICAL($critical)/HIGH($high) findings -> release blocked"
  exit 1
fi
ok "security: clean (medium=$medium low=$low info=$info)"
exit 0