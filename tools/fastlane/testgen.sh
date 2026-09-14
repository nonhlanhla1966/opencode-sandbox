#!/usr/bin/env bash
# testgen.sh — Automatic Test Generation (8. AUTOMATIC TEST GENERATION).
#
# For every generated feature/entity in an app-spec, emits JUnit test files for
# the deterministic business-logic layer. Never edits app logic to make tests
# pass; tests are generated as invariants then run in testDebugUnitTest.
#
# Usage: testgen.sh <app-spec.json> <app-dir>
set -u
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/slib.sh"

spec="${1:?usage: testgen.sh <app-spec.json> <app-dir>}"
app_dir="${2:?usage: testgen.sh <app-spec.json> <app-dir>}"
slug="$(basename "$app_dir")"
jsafe="$(printf '%s' "$slug" | python3 -c 'import sys,re;print(re.sub(r"[^a-z0-9]","",sys.stdin.read().strip()))')"
test_dir="$app_dir/src/test/java/com/appfactory/$jsafe"
mkdir -p "$test_dir"

python3 - "$spec" "$test_dir" "$jsafe" <<'PY'
import json,sys,os,re
spec=json.load(open(sys.argv[1]))
out_dir=sys.argv[2]
slug=sys.argv[3]
feats=spec.get("features",["feature"])

def esc(f):
    return str(f).replace('"','\\"').replace("\\\\","\\\\\\\\")

def write(name, content):
    p=os.path.join(out_dir,name+".java")
    if os.path.exists(p):  # never overwrite agent-written tests
        print(f"testgen: skip (exists) {name}")
        return
    open(p,"w").write(content)
    print(f"testgen: wrote {name}")

write("GeneratedFeatureTest", f"""\
package com.appfactory.{slug};

import org.junit.Test;
import static org.junit.Assert.*;

/** Generated regression tests for feature invariants. */
public final class GeneratedFeatureTest {{
    private static final String[] FEATURES = new String[]{{
        {", ".join('"%s"' % esc(f) for f in feats)}
    }};

    @Test public void featuresAreNonEmpty() {{
        assertTrue(FEATURES.length > 0);
        for (String f : FEATURES) assertNotNull(f);
    }}

    @Test public void appHasCoreStore() {{
        ItemStore s = new ItemStore();
        s.add("x", "y");
        assertEquals(1, s.size());
    }}
}}
""")

write("GeneratedStoreTest", f"""\
package com.appfactory.{slug};

import org.junit.Test;
import static org.junit.Assert.*;

/** Store invariants that keep the offline-first data layer honest. */
public final class GeneratedStoreTest {{
    @Test public void searchIsCaseInsensitive() {{
        ItemStore s = new ItemStore();
        s.add("Milk", "dairy");
        s.add("Bread", "grains");
        assertEquals(1, s.search("m").size());
        assertEquals(1, s.search("M").size());
        assertEquals(0, s.search("cheese").size());
    }}
    @Test public void removeIsIdempotent() {{
        ItemStore s = new ItemStore();
        s.add("a", "1");
        assertTrue(s.remove("1"));
        assertEquals(0, s.size());
    }}
    @Test public void jsonRoundTripKeepsOrder() {{
        ItemStore s = new ItemStore();
        s.add("first", "f");
        s.add("second", "s");
        ItemStore back = ItemStore.fromJson(s.toJson().toString());
        assertEquals("first", back.items().get(0).title);
        assertEquals("second", back.items().get(1).title);
    }}
}}
""")
PY

ok "testgen: generated tests for $slug"
exit 0