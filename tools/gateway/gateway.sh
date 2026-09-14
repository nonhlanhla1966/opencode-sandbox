#!/usr/bin/env bash
# gateway.sh — AppFactory Remote Gateway (ChatGPT/MCP control plane).
#
# Thin JSON front-end over the EXISTING, already-proven AppFactory pipeline
# (issue -> /oc -> opencode.yml engine -> apps/<slug> -> Fast Lane build in
# build.yml -> app-<slug>-latest release with APK + SHA256SUMS ->
# DOWNLOAD_READY comment).
#
# Commands (JSON in/out, nothing secret ever printed):
#   create_app_request  "<one-line app idea>"   -> creates factory issue (+/oc)
#   get_build_status    <issue-number|slug>     -> pipeline position + apk status
#   get_latest_apk      <slug>                  -> verified APK url/version/sha256
#
# Security rules (never broken):
#   * Never reads or echoes any secret (OPENCODE_API_KEY / PAT). Auth only via
#     `gh` + GITHUB_TOKEN/GH_TOKEN env (the same supported mechanism the
#     existing repo scripts use). Never prints/echoes the token value.
#   * Never embeds credentials in any URL (only public github.com release URLs).
#   * stdout = strict JSON only; diagnostics to stderr. Never logs token.
#   * No sudo, no filesystem permission prompts, no chmod-on-system, no
#     weakening OS/permission controls. Creates no new repository, no branches,
#     no API keys. Work stays inside this workspace (script + DESIGN.md).
set -euo pipefail
GW_TMPDIR=""   # scratch dir for get_latest_apk; global so the EXIT trap survives function return

# -------- helpers ------------------------------------------------------------
die()  { echo "{\"ok\":false,\"command\":\"${CMD:-}\",\"error\":$(json_esc "$1")}" >&2; exit 1; }
json_esc() { python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"; }

repo() { # returns owner/repo, never any token-bearing string
  local r="${GITHUB_REPOSITORY:-}"
  if [ -z "$r" ]; then
    r="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
  fi
  [ -n "$r" ] || return 1
  printf '%s' "$r"
  return 0
}

slug_of() { # slugify an app idea into <lowercase-dashes>
  local s="$1"
  s="$(printf '%s' "$s" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9' '-')"
  s="$(printf '%s' "$s" | sed -E 's/-+/-/g;s/^-//;s/-$//')"
  [ -n "$s" ] || s="app"
  printf '%s' "$s"
}

slug_by_issue() { # map issue number -> app slug via apps/*/release.json
  local issue="$1" slug="" js
  for js in apps/*/release.json; do
    [ -f "$js" ] || continue
    if [ "$(python3 -c "import json,sys;print(json.load(open('$js')).get('issue',0))" 2>/dev/null || echo 0)" = "$issue" ]; then
      slug="$(basename "$(dirname "$js")")"
      break
    fi
  done
  printf '%s' "$slug"
}

# emit {ok:true, ...fields}
ok() { printf '{"ok":true,"command":"%s"%s}\n' "${CMD:-}" "${1:+,$1}"; }

# run_comment_trigger <repository> <issue> <idea>: the opencode.yml engine reacts
# to an ISSUE COMMENT carrying a trigger token, not to issue bodies, so the
# gateway posts the /oc token as a real comment (same action a human /app
# poster performs today). Never echoes any secret.
run_comment_trigger() {
  gh issue comment "$2" -R "$1" --body "/oc $3" >/dev/null 2>&1 \
    || { die "issue #$2 created but /oc trigger comment failed"; }
}

# -------- 1. create_app_request ---------------------------------------------
# Input : plain-language Android app idea (one line).
# Action: create a GitHub issue in THIS repo carrying the idea + the /oc
#         trigger so the existing opencode.yml engine picks it up. Returns the
#         issue number + status. Never touches any secret/creds.
create_app_request() {
  local idea="${1:-}"; CMD=create_app_request
  [ -n "$idea" ] || { echo "{\"ok\":false,\"command\":\"create_app_request\",\"error\":$(json_esc 'idea is required')}"; exit 2; }
  local r slug title body issue status
  r="$(repo)" || { die "cannot resolve repository"; }
  slug="$(slug_of "$idea")"
  # Slug in the title so get_build_status can look the issue up by slug later.
  title="[AppFactory] ${slug}: ${idea}"

  # Idempotent-ish: reuse an already-open factory issue for the same slug
  # rather than spamming the backlog.
  issue="$(gh issue list -R "$r" --state open --search "in:title ${slug}" --json number --jq '.[0].number' 2>/dev/null || true)"
  if [ -n "$issue" ]; then
    ok "\"slug\":$(json_esc "$slug"),\"application_id\":$(json_esc "com.appfactory.$slug"),\"issue\":$issue,\"status\":\"request_reused\",\"issue_url\":$(json_esc "https://github.com/$r/issues/$issue")"
    return 0
  fi

  body="$(cat <<EOF
AppFactory - build me: ${idea}

Slug: ${slug}
EOF
)"
  issue="$(gh issue create -R "$r" --title "$title" --body "$body" 2>&1 \
    | grep -oE 'https://github.com/[^/]+/[^/]+/issues/[0-9]+' \
    | grep -oE '[0-9]+$' | head -1 || true)"
  [ -n "$issue" ] || die "failed to create issue (check GITHUB_TOKEN auth)"
  run_comment_trigger "$r" "$issue" "$idea"
  ok "\"slug\":$(json_esc "$slug"),\"application_id\":$(json_esc "com.appfactory.$slug"),\"issue\":$issue,\"status\":\"request_created\",\"issue_url\":$(json_esc "https://github.com/$r/issues/$issue")"
}

# -------- 2. get_build_status -----------------------------------------------
# Input : issue number OR app slug.
# Output: AppFactory status, CI status, release status, APK availability.
get_build_status() {
  local arg="${1:-}"; CMD=get_build_status
  [ -n "$arg" ] || { echo "{\"ok\":false,\"command\":\"get_build_status\",\"error\":$(json_esc 'issue number or slug required')}"; exit 2; }
  local r slug issue ci_st apk_st
  r="$(repo)" || die "cannot resolve repository"

  if printf '%s' "$arg" | grep -qE '^[0-9]+$'; then
    issue="$arg"; slug="$(slug_by_issue "$issue")"
  else
    slug="$(slug_of "$arg")"
    # find recent issue whose title mentions the slug (factory issues)
    issue="$(gh issue list -R "$r" --search "in:title ${slug}" --limit 5 --json number --jq '.[0].number' 2>/dev/null || echo "")"
  fi

  # CI status (latest build.yml run for this app)
  ci_st="$(gh run list -R "$r" --workflow=build.yml --limit 8 --json displayTitle,status,conclusion,headBranch --jq '.[] | select(.displayTitle|test("'${slug}'";"i")) | .status+"/"+(.conclusion//"pending")' 2>/dev/null | head -1 || echo "none")"
  [ -n "$ci_st" ] || ci_st="none"

  # Release + APK availability (rolling app-<slug>-latest)
  local tag="app-${slug}-latest" rel="false"
  if gh release view "$tag" -R "$r" --json tagName --jq .tagName >/dev/null 2>&1; then
    rel="true"
    apk_st="$(gh release view "$tag" -R "$r" --json assets --jq '[.assets[].name] | any(endswith(".apk"))' 2>/dev/null || echo "false")"
  else
    apk_st="false"
  fi

  ok "\"slug\":$(json_esc "$slug"),\"issue\":${issue:-0},\"appfactory_status\":$(json_esc "$([ "$rel" = "true" ] && echo download_ready || echo building)"),\"ci_status\":$(json_esc "$ci_st"),\"release_status\":$(json_esc "$([ "$rel" = "true" ] && echo published || echo not_published)"),\"apk_available\":$apk_st"
}

# -------- 3. get_latest_apk -------------------------------------------------
# Input : app slug.
# Output: public release URL, direct APK URL, version, SHA-256 checksum,
#         build status. Downloads the real SHA256SUMS + APK and recomputes the
#         hash locally; only reports verified:true on an exact match.
get_latest_apk() {
  local slug="${1:-}"; CMD=get_latest_apk
  [ -n "$slug" ] || { echo "{\"ok\":false,\"command\":\"get_latest_apk\",\"error\":$(json_esc 'slug required')}"; exit 2; }
  local r tag apk_url sha_url ver sha calc verified="false"
  r="$(repo)" || die "cannot resolve repository"
  tag="app-${slug}-latest"

  local apk_name
  apk_name="$(gh release view "$tag" -R "$r" --json assets --jq '[.assets[].name] | map(select(endswith(".apk"))) | .[0]' 2>/dev/null || echo "")"
  [ -n "$apk_name" ] || die "no APK asset on $tag"
  apk_url="https://github.com/$r/releases/download/$tag/$apk_name"
  sha_url="https://github.com/$r/releases/download/$tag/SHA256SUMS"

  ver="$(gh release view "$tag" -R "$r" --json tagName --jq .tagName 2>/dev/null || echo "$tag")"

  GW_TMPDIR="$(mktemp -d)"; trap 'rm -rf "${GW_TMPDIR:-}"' EXIT
  if curl -fsSL "$apk_url" -o "$GW_TMPDIR/$apk_name" 2>/dev/null && curl -fsSL "$sha_url" -o "$GW_TMPDIR/SHA256SUMS" 2>/dev/null; then
    sha="$(sed -n 's/^\([0-9a-fA-F]\{64\}\)  .*/\1/p' "$GW_TMPDIR/SHA256SUMS" | head -1 || true)"
    calc="$(sha256sum "$GW_TMPDIR/$apk_name" | awk '{print $1}')"
    [ -n "$sha" ] && [ "$sha" = "$calc" ] && verified="true"
  fi

  ok "\"slug\":$(json_esc "$slug"),\"version\":$(json_esc "$ver"),\"checksum\":$(json_esc "${sha:-}"),\"verified\":$verified,\"release_url\":$(json_esc "https://github.com/$r/releases/tag/$tag"),\"apk_url\":$(json_esc "$apk_url"),\"build_status\":$(json_esc "$( [ "$verified" = "true" ] && echo ready || echo building)")"
}

# -------- dispatch -----------------------------------------------------------
CMD="${1:-create_app_request}"; [ $# -ge 1 ] && { case "$1" in
  create_app_request) create_app_request "${2:-}";;
  get_build_status)   get_build_status "${2:-}";;
  get_latest_apk)     get_latest_apk "${2:-}";;
  slug_of)            [ $# -ge 2 ] && printf '%s\n' "$(slug_of "${2:-}")" || echo "{\"ok\":false,\"command\":\"slug_of\",\"error\":$(json_esc 'idea required')}";;
  *) echo "{\"ok\":false,\"command\":\"$1\",\"error\":$(json_esc 'unknown command')}"; exit 2;;
esac; } || true