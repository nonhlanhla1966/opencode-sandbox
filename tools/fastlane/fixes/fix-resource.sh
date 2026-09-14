#!/usr/bin/env bash
# fix-resource.sh — deterministic minimal fix for missing Android resources.
# Usage: fix-resource.sh <build-log> <app-dir>
# Adds placeholder entries for missing colors/strings/drawables/dimens so the
# app can build; the agent then replaces placeholders with real values.
set -u

log="$1"; wd="$2"
values_dir="$wd/src/main/res/values"
drawable_dir="$wd/src/main/res/drawable"
fixed=0

[ -d "$values_dir" ] || return 1
[ -d "$drawable_dir" ] || mkdir -p "$drawable_dir"

# aapt2/AGP style:  resource color/foo_bar ... not found
mapfile -t missing < <(grep -oE "resource (color|string|dimen|drawable)/[A-Za-z0-9_]+" "$log" 2>/dev/null | sort -u)

if [ "${#missing[@]}" -eq 0 ]; then
  # R.style / @style reference style missing -> add to themes
  grep -qE "style/[A-Za-z0-9_]+\s+not found|@style/[A-Za-z0-9_]+.*not found" "$log" 2>/dev/null \
    && { printf '\n    <style name="AppFactoryPlaceholder" />\n' >> "$values_dir/themes.xml"; fixed=$((fixed+1)); }
  exit 0
fi

for entry in "${missing[@]}"; do
  type="${entry%%/*}"; name="${entry##*/}"
  [ -n "$name" ] || continue
  case "$type" in
    color)
      if ! grep -q "\"$name\"" "$values_dir/colors.xml" 2>/dev/null; then
        sed -i "/<\/resources>/i\\    <color name=\"$name\">#FF607D8B</color>" "$values_dir/colors.xml" 2>/dev/null || true
        fixed=$((fixed+1))
      fi
      ;;
    string)
      if ! grep -q "\"$name\"" "$values_dir/strings.xml" 2>/dev/null; then
        sed -i "/<\/resources>/i\\    <string name=\"$name\">$name</string>" "$values_dir/strings.xml" 2>/dev/null || true
        fixed=$((fixed+1))
      fi
      ;;
    dimen)
      dims="$values_dir/dimens.xml"
      [ -f "$dims" ] || printf '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n' > "$dims"
      if ! grep -q "\"$name\"" "$dims" 2>/dev/null; then
        sed -i "/<\/resources>/i\\    <dimen name=\"$name\">16dp</dimen>" "$dims" 2>/dev/null || true
        fixed=$((fixed+1))
      fi
      ;;
    drawable)
      if [ ! -f "$drawable_dir/$name.xml" ]; then
        printf '<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android">\n    <solid android:color="#22000000" />\n</shape>\n' > "$drawable_dir/$name.xml"
        fixed=$((fixed+1))
      fi
      ;;
  esac
done

# XML well-formedness guard: if an edit broke the file, restore by removing the
# newly inserted line (a single common failure mode: <resources> already closed).
for f in "$values_dir/colors.xml" "$values_dir/strings.xml" "$values_dir/dimens.xml"; do
  [ -f "$f" ] || continue
  if ! python3 -c "import xml.etree.ElementTree as E; E.parse('$f')" 2>/dev/null; then
    # remove placeholder entries appended after </resources> — sed line restore
    sed -i '/AppFactoryPlaceholder\|#FF607D8B/d' "$f" 2>/dev/null || true
  fi
done

echo "fix-resource: added $fixed resource placeholder(s)"
[ "$fixed" -gt 0 ]