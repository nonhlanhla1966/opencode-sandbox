#!/usr/bin/env python3
"""scaffold.py — Fast Lane project scaffold generator (3. VERIFIED REUSABLE MODULES + generation).

Reads app-spec.json + architecture.json and emits a complete, standalone,
buildable Android Gradle project under apps/<slug>/. Copies a real Gradle
wrapper, writes deterministically generated Java sources, tests, resources,
manifest and build.gradle. Uses the reusable module library from modules/.

Usage:
  scaffold.py <app-spec.json> <architecture.json> [out_dir]
  # defaults out_dir to apps/<slug> (relative to cwd)
"""

import json
import os
import re
import shutil
import stat
import sys
import textwrap
import hashlib
from pathlib import Path

ROOT     = Path(__file__).resolve().parent / ".." / ".."
SKEL_DIR = ROOT / "tools" / "fastlane" / "skel"
MOD_DIR  = ROOT / "modules"
HELLO    = ROOT / "apps" / "hello-appfactory"

# ---- helpers ---------------------------------------------------------------- #
def slug_to_java(slug):
    return re.sub(r"[^a-z0-9]", "", slug.lower())

def esc(s):
    return str(s).replace("\\","\\\\").replace('"','\\"').replace("\n","\\n")

def hash_palette(seed):
    h = hashlib.md5(seed.encode()).hexdigest()
    return {
        "primary":   "#FF"+h[0:6],
        "secondary": "#FF"+h[6:12],
        "accent":    "#FF"+h[12:18],
        "bg_light":  "#FFFAFAFA",
        "bg_dark":   "#FF121212",
        "text_light":"#FF212121",
        "text_dark": "#FFE0E0E0",
    }

def ensure_dir(p):
    Path(p).mkdir(parents=True, exist_ok=True)

def write_file(path, content):
    ensure_dir(Path(path).parent)
    Path(path).write_text(content)

def chmod_x(path):
    st = os.stat(path)
    os.chmod(path, st.st_mode | stat.S_IRUSR | stat.S_IXUSR)

# ---- module copier ----------------------------------------------------------- #
MODULE_PKG = "com.appfactory.modules"

def module_pkg_segment(mod_id):
    """Java package segment a module actually declares (modules are allowed to
    live in a dir whose name differs from the package, e.g. storage-sqlite ->
    storage_sqlite, notifications -> notify)."""
    for base in ("src", "test", "android"):
        d = MOD_DIR / mod_id / base
        if d.is_dir():
            for f in sorted(d.glob("*.java")):
                m = re.search(r"^package\s+([\w.]+);", f.read_text(), re.M)
                if m:
                    return m.group(1).split(".")[-1]
    return re.sub(r"[^A-Za-z0-9_]", "_", mod_id)

def copy_modules(slug, mods, out_root):
    """Copy module src + tests into the generated app. Files are copied
    verbatim (packages/imports are already authored to be self-consistent) and
    placed under the package they declare; the directory layout is cosmetic."""
    parts = MODULE_PKG.split(".")
    for mod_id in mods:
        src_dir = MOD_DIR / mod_id / "src"
        if not src_dir.is_dir():
            continue
        pkg_seg = module_pkg_segment(mod_id)
        pkg_dir = out_root
        for p in ["src","main","java"] + parts + [pkg_seg]:
            pkg_dir = pkg_dir / p
        pkg_dir.mkdir(parents=True, exist_ok=True)
        for java in src_dir.glob("*.java"):
            shutil.copy2(java, pkg_dir / java.name)
        android_dir = MOD_DIR / mod_id / "android"
        if android_dir.is_dir():
            for java in android_dir.glob("*.java"):
                shutil.copy2(java, pkg_dir / java.name)
        test_dir = MOD_DIR / mod_id / "test"
        if test_dir.is_dir():
            test_pkg = out_root
            for p in ["src","test","java"] + parts + [pkg_seg]:
                test_pkg = test_pkg / p
            test_pkg.mkdir(parents=True, exist_ok=True)
            for java in test_dir.glob("*.java"):
                shutil.copy2(java, test_pkg / java.name)

# ---- manifest --------------------------------------------------------------- #
def manifest_xml(spec, arch):
    perms = ""
    for p in spec.get("permissions", []):
        perms += f'    <uses-permission android:name="{p}" />\n'
    activities = (
        '        <activity\n'
        '            android:name=".MainActivity"\n'
        '            android:exported="true">\n'
        '            <intent-filter>\n'
        '                <action android:name="android.intent.action.MAIN" />\n'
        '                <category android:name="android.intent.category.LAUNCHER" />\n'
        '            </intent-filter>\n'
        '        </activity>\n'
        '        <activity android:name=".AboutActivity" android:exported="false" android:label="@string/about_title" />\n'
        '        <activity android:name=".SettingsActivity" android:exported="false" android:label="@string/settings_title" />\n'
        '        <activity android:name=".DetailActivity" android:exported="false" android:label="@string/app_name" />\n'
        '        <activity android:name=".ItemsActivity" android:exported="false" android:label="@string/app_name" />\n'
    )
    if spec.get("auth") not in (None, "none"):
        pass  # no extra activities for now; settings handles key entry
    return textwrap.dedent(f"""\
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
    {perms}
        <application
            android:label="@string/app_name"
            android:icon="@mipmap/ic_launcher"
            android:theme="@style/Theme.AppFactory"
            android:allowBackup="false">
    {activities}
        </application>
    </manifest>
    """)

# ---- build.gradle template -------------------------------------------------- #
def build_gradle(slug, spec):
    deps = ['    testImplementation \'junit:junit:4.13.2\'']
    # no external deps by default to keep fast; modules are vendored as source
    return textwrap.dedent(f"""\
    plugins {{
        id 'com.android.application' version '8.5.2'
    }}

    android {{
        namespace 'com.appfactory.{slug_to_java(slug)}'
        compileSdk 34

        defaultConfig {{
            applicationId 'com.appfactory.{slug_to_java(slug)}'
            minSdk 21
            targetSdk 34
            versionCode 1
            versionName '0.1.0'
        }}

        compileOptions {{
            sourceCompatibility JavaVersion.VERSION_17
            targetCompatibility JavaVersion.VERSION_17
        }}

        buildTypes {{
            release {{
                minifyEnabled false
            }}
        }}
    }}

    dependencies {{
    {",".join(deps)}
    }}
    """)

def settings_gradle(slug):
    return textwrap.dedent(f"""\
    pluginManagement {{
        repositories {{
            google()
            mavenCentral()
            gradlePluginPortal()
        }}
    }}
    dependencyResolutionManagement {{
        repositories {{
            google()
            mavenCentral()
        }}
    }}
    rootProject.name = "{slug}"
    """)

def gradle_properties():
    return textwrap.dedent("""\
    # Fast Lane Gradle performance settings (open-source compatible)
    org.gradle.caching=true
    org.gradle.parallel=true
    org.gradle.workers.max=2
    org.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC -XX:MaxMetaspaceSize=512m
    android.useAndroidX=false
    android.nonTransitiveRClass=true
    kotlin.code.style=official
    """)

# ---- Java source templates -------------------------------------------------- #
def main_activity_java(slug, title, features):
    feature_list = ", ".join(f'"{esc(f)}"' for f in features)
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import android.app.Activity;
    import android.content.Intent;
    import android.os.Bundle;
    import android.view.Gravity;
    import android.view.View;
    import android.widget.LinearLayout;
    import android.widget.ScrollView;
    import android.widget.TextView;
    import android.widget.Button;
    import android.graphics.Color;
    import android.util.TypedValue;
    import android.util.Pair;

    import java.util.Arrays;
    import java.util.List;

    public final class MainActivity extends Activity {{
        private static final String[] FEATURES = new String[]{{{feature_list}}};

        @Override protected void onCreate(Bundle savedInstanceState) {{
            super.onCreate(savedInstanceState);
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));
            scroll.addView(root);

            TextView title = new TextView(this);
            title.setText("{esc(title)}");
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            title.setPadding(0, 0, 0, dp(16));
            root.addView(title);

            for (String feature : FEATURES) {{
                Button btn = new Button(this);
                btn.setText(feature);
                btn.setOnClickListener(v -> {{
                    Intent intent = new Intent(this, DetailActivity.class);
                    intent.putExtra("title", feature);
                    intent.putExtra("body", "{esc(title)} — " + feature);
                    startActivity(intent);
                }});
                root.addView(btn);
            }}

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            row.setPadding(0, dp(16), 0, 0);
            Button about = new Button(this);
            about.setText("About");
            about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
            row.addView(about);
            Button settings = new Button(this);
            settings.setText("Settings");
            settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
            row.addView(settings);
            root.addView(row);

            setContentView(scroll);
        }}

        private int dp(int v) {{
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v, getResources().getDisplayMetrics());
        }}
    }}
    """)

def detail_activity_java(slug):
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import android.app.Activity;
    import android.os.Bundle;
    import android.view.Gravity;
    import android.widget.LinearLayout;
    import android.widget.ScrollView;
    import android.widget.TextView;
    import android.util.TypedValue;

    public final class DetailActivity extends Activity {{
        @Override protected void onCreate(Bundle savedInstanceState) {{
            super.onCreate(savedInstanceState);
            String title = getIntent().getStringExtra("title");
            String body  = getIntent().getStringExtra("body");
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));

            TextView tv = new TextView(this);
            tv.setText(title != null ? title : "Detail");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tv.setPadding(0, 0, 0, dp(12));
            root.addView(tv);

            TextView bodyTv = new TextView(this);
            bodyTv.setText(body != null ? body : "");
            bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            root.addView(bodyTv);

            scroll.addView(root);
            setContentView(scroll);
        }}
        private int dp(int v) {{
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v, getResources().getDisplayMetrics());
        }}
    }}
    """)

def about_activity_java(slug, title):
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import android.app.Activity;
    import android.os.Bundle;
    import android.widget.LinearLayout;
    import android.widget.ScrollView;
    import android.widget.TextView;
    import android.util.TypedValue;

    public final class AboutActivity extends Activity {{
        @Override protected void onCreate(Bundle savedInstanceState) {{
            super.onCreate(savedInstanceState);
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));

            TextView tv = new TextView(this);
            tv.setText("{esc(title)}\\n\\n"
                + "Built by the OpenCode AppFactory Fast Lane.\\n"
                + "Completely open-source.");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            root.addView(tv);
            scroll.addView(root);
            setContentView(scroll);
        }}
        private int dp(int v) {{
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v, getResources().getDisplayMetrics());
        }}
    }}
    """)

def settings_activity_java(slug, auth):
    extra = ""
    if auth not in (None, "none"):
        extra = textwrap.dedent("""\
            // For user-supplied API keys, store via the encrypted settings
            // module (com.appfactory.modules.settings + crypto). The agent
            // should implement a UI that writes to the preferences below.
            final String KEY_PREF = "api_key";
            prefs = getSharedPreferences("settings", MODE_PRIVATE);
            TextView keyRow = new TextView(this);
            keyRow.setText("API key configured: " + (prefs.contains(KEY_PREF) ? "Yes" : "Not set"));
            root.addView(keyRow);
        """)
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import android.app.Activity;
    import android.content.SharedPreferences;
    import android.os.Bundle;
    import android.widget.LinearLayout;
    import android.widget.ScrollView;
    import android.widget.TextView;
    import android.util.TypedValue;

    public final class SettingsActivity extends Activity {{
        SharedPreferences prefs;
        @Override protected void onCreate(Bundle savedInstanceState) {{
            super.onCreate(savedInstanceState);
            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(24), dp(24), dp(24), dp(24));
            TextView tv = new TextView(this);
            tv.setText("Settings");
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tv.setPadding(0, 0, 0, dp(12));
            root.addView(tv);
            {extra}
            scroll.addView(root);
            setContentView(scroll);
        }}
        private int dp(int v) {{
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v, getResources().getDisplayMetrics());
        }}
    }}
    """)

# ---- resources -------------------------------------------------------------- #
def colors_xml(pal):
    return textwrap.dedent(f"""\
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <color name="primary">{pal['primary']}</color>
        <color name="secondary">{pal['secondary']}</color>
        <color name="accent">{pal['accent']}</color>
        <color name="bg_light">{pal['bg_light']}</color>
        <color name="bg_dark">{pal['bg_dark']}</color>
        <color name="text_light">{pal['text_light']}</color>
        <color name="text_dark">{pal['text_dark']}</color>
        <color name="ic_launcher_background">#FF1B5E20</color>
    </resources>
    """)

def themes_xml():
    return textwrap.dedent("""\
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <style name="Theme.AppFactory" parent="android:Theme.Material.Light.NoActionBar">
            <item name="android:colorPrimary">@color/primary</item>
            <item name="android:colorAccent">@color/accent</item>
            <item name="android:windowBackground">@color/bg_light</item>
            <item name="android:textColorPrimary">@color/text_light</item>
        </style>
    </resources>
    """)

def night_themes_xml():
    return textwrap.dedent("""\
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <style name="Theme.AppFactory" parent="android:Theme.Material.NoActionBar">
            <item name="android:colorPrimary">@color/primary</item>
            <item name="android:colorAccent">@color/accent</item>
            <item name="android:windowBackground">@color/bg_dark</item>
            <item name="android:textColorPrimary">@color/text_dark</item>
        </style>
    </resources>
    """)

def night_colors_xml(pal):
    return textwrap.dedent(f"""\
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <color name="primary">{pal['primary']}</color>
        <color name="secondary">{pal['secondary']}</color>
        <color name="accent">{pal['accent']}</color>
        <color name="bg_light">{pal['bg_dark']}</color>
        <color name="bg_dark">{pal['bg_dark']}</color>
        <color name="text_light">{pal['text_dark']}</color>
        <color name="text_dark">{pal['text_dark']}</color>
    </resources>
    """)

def strings_xml(slug, title):
    return textwrap.dedent(f"""\
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <string name="app_name">{esc(title)}</string>
        <string name="about_title">About</string>
        <string name="settings_title">Settings</string>
        <string name="feature_placeholder">Feature detail view.</string>
    </resources>
    """)

# ---- unit test -------------------------------------------------------------- #
def item_java(slug):
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import java.util.Objects;

    /** A persisted entity generated by the AppFactory scaffold. */
    public final class Item {{
        public final String id;
        public final String title;
        public final String detail;
        public final long createdAt;

        public Item(String id, String title, String detail, long createdAt) {{
            this.id = id;
            this.title = title;
            this.detail = detail;
            this.createdAt = createdAt;
        }}
        @Override public boolean equals(Object o) {{
            if (!(o instanceof Item)) return false;
            return Objects.equals(((Item) o).id, id);
        }}
        @Override public int hashCode() {{ return Objects.hashCode(id); }}
    }}
    """)

def item_store_java(slug):
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import com.appfactory.modules.json.Json;
    import com.appfactory.modules.json.JsonArray;
    import com.appfactory.modules.json.JsonObject;

    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.List;

    /** Offline-first store with deterministic add/remove/search + JSON. */
    public final class ItemStore {{
        private final List<Item> items = new ArrayList<>();
        private int nextId = 1;

        public Item add(String title, String detail) {{
            Item it = new Item(String.valueOf(nextId++), title, detail,
                               System.currentTimeMillis());
            items.add(it);
            return it;
        }}
        public boolean remove(String id) {{
            for (int i = 0; i < items.size(); i++) {{
                if (items.get(i).id.equals(id)) {{
                    items.remove(i);
                    return true;
                }}
            }}
            return false;
        }}
        public List<Item> items() {{ return Collections.unmodifiableList(items); }}
        public int size() {{ return items.size(); }}
        public List<Item> search(String q) {{
            if (q == null || q.isEmpty()) return items();
            List<Item> out = new ArrayList<>();
            String needle = q.toLowerCase();
            for (Item it : items) {{
                if (it.title.toLowerCase().contains(needle)
                        || it.detail.toLowerCase().contains(needle)) out.add(it);
            }}
            return out;
        }}
        public JsonObject toJson() {{
            JsonObject o = new JsonObject();
            o.put("nextId", nextId);
            JsonArray arr = new JsonArray();
            for (Item it : items) {{
                JsonObject e = new JsonObject();
                e.put("id", it.id);
                e.put("title", it.title);
                e.put("detail", it.detail);
                e.put("createdAt", it.createdAt);
                arr.add(e);
            }}
            o.put("items", arr);
            return o;
        }}
        public static ItemStore fromJson(String text) {{
            ItemStore s = new ItemStore();
            if (text == null || text.isEmpty()) return s;
            JsonObject o = Json.parseObject(text);
            s.nextId = o.getInt("nextId", 1);
            JsonArray arr = o.getArray("items");
            for (int i = 0; i < arr.size(); i++) {{
                JsonObject e = arr.getObject(i);
                s.items.add(new Item(e.getString("id"), e.getString("title"),
                        e.getString("detail"), e.getLong("createdAt", 0)));
            }}
            return s;
        }}
    }}
    """)

def items_activity_java(slug):
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import android.app.Activity;
    import android.content.SharedPreferences;
    import android.os.Bundle;
    import android.text.Editable;
    import android.widget.Button;
    import android.widget.EditText;
    import android.widget.LinearLayout;
    import android.widget.ScrollView;
    import android.widget.TextView;
    import android.util.TypedValue;

    /** Offline-first demo list backed by ItemStore + SharedPreferences. */
    public final class ItemsActivity extends Activity {{
        private ItemStore store;
        private LinearLayout list;
        private SharedPreferences prefs;
        private static final String KEY = "item_store.json";

        @Override protected void onCreate(Bundle savedInstanceState) {{
            super.onCreate(savedInstanceState);
            prefs = getSharedPreferences("items", MODE_PRIVATE);
            store = ItemStore.fromJson(prefs.getString(KEY, ""));

            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(20), dp(20), dp(20), dp(20));
            setContentView(scroll);

            TextView h = new TextView(this);
            h.setText("Items (offline first)");
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            root.addView(h);

            final EditText title = new EditText(this);
            title.setHint("Title");
            root.addView(title);
            final EditText detail = new EditText(this);
            detail.setHint("Detail");
            root.addView(detail);
            Button addBtn = new Button(this);
            addBtn.setText("Add item");
            addBtn.setOnClickListener(v -> addItem(title.getText(), detail.getText()));
            root.addView(addBtn);

            list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            root.addView(list);
            refresh();

            scroll.addView(root);
        }}
        private void addItem(Editable t, Editable d) {{
            store.add(t.toString(), d.toString());
            persist();
            refresh();
        }}
        private void removeItem(String id) {{
            store.remove(id);
            persist();
            refresh();
        }}
        private void persist() {{
            prefs.edit().putString(KEY, store.toJson().toString()).apply();
        }}
        private void refresh() {{
            list.removeAllViews();
            for (Item it : store.items()) {{
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView tv = new TextView(this);
                tv.setText(it.title + (it.detail.isEmpty() ? "" : " — " + it.detail));
                tv.setPadding(0, dp(4), dp(12), dp(4));
                row.addView(tv);
                Button del = new Button(this);
                del.setText("Remove");
                del.setOnClickListener(v -> removeItem(it.id));
                row.addView(del);
                list.addView(row);
            }}
            if (store.size() == 0) {{
                TextView empty = new TextView(this);
                empty.setText("No items yet. Add one above.");
                list.addView(empty);
            }}
        }}
        private int dp(int v) {{
            return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v, getResources().getDisplayMetrics());
        }}
    }}
    """)

def item_test_java(slug):
    return textwrap.dedent(f"""\
    package com.appfactory.{slug_to_java(slug)};

    import org.junit.Test;
    import static org.junit.Assert.*;

    public final class ItemTest {{
        @Test public void itemEqualityById() {{
            Item a = new Item("x","a","b",1);
            Item b = new Item("x","c","d",2);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }}
        @Test public void storeAddAndSearch() {{
            ItemStore s = new ItemStore();
            s.add("Grocery", "milk and eggs");
            s.add("Gym", "leg day");
            assertEquals(2, s.size());
            assertEquals(1, s.search("milk").size());
            assertEquals(0, s.search("zzz").size());
        }}
        @Test public void storeRemove() {{
            ItemStore s = new ItemStore();
            s.add("A", "1");
            s.add("B", "2");
            assertTrue(s.remove("1"));
            assertFalse(s.remove("1"));
            assertEquals(1, s.size());
        }}
        @Test public void storeRoundTripsThroughJson() {{
            ItemStore s = new ItemStore();
            s.add("Title", "Detail");
            s.add("Two", "");
            String text = s.toJson().toString();
            ItemStore back = ItemStore.fromJson(text);
            assertEquals(2, back.size());
            assertEquals("Title", back.items().get(0).title);
            assertEquals("Detail", back.items().get(0).detail);
        }}
    }}
    """)

# ---- DESIGN.md placeholder -------------------------------------------------- #
def design_md(spec):
    screens = spec.get("screens", ["Home"])
    lines = [f"# DESIGN.md — {spec.get('name','app')}", "", "### Elevator pitch",
             spec.get("summary",""), "", "## 1. Screens", "| Screen | Purpose |",
             "|--------|---------|"]
    for s in screens:
        lines.append(f"| {s} | generated placeholder — the agent refines this |")
    lines += ["", "### 2. Navigation",
              spec.get("navigation",""), "",
              "### 3. Visual style", "Default generated palette; dark mode enabled.",
              "", "### 4. Interactions", "Placeholder interactions.",
              "", "### 5. App-icon concept", "Monochrome generated launcher icon."]
    return "\n".join(lines)+"\n"


# ---- MAIN -------------------------------------------------------------------- #
def scaffold(spec, arch, out_dir):
    slug    = spec["slug"]
    title   = spec["name"]
    jsafe   = slug_to_java(slug)
    palette = hash_palette(slug)
    features = spec.get("features", ["feature"])
    auth    = spec.get("auth","none")
    out     = Path(out_dir)

    os.makedirs(out, exist_ok=True)

    # 1. copy wrapper from skel (self-contained, deterministic)
    for f in ("gradlew","gradlew.bat"):
        shutil.copy2(SKEL_DIR / f, out / f)
        chmod_x(out / f)
    wrapper = out / "gradle" / "wrapper"
    wrapper.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SKEL_DIR / "gradle" / "wrapper" / "gradle-wrapper.jar", wrapper / "gradle-wrapper.jar")
    shutil.copy2(SKEL_DIR / "gradle" / "wrapper" / "gradle-wrapper.properties", wrapper / "gradle-wrapper.properties")

    # 2. build files
    write_file(out / "build.gradle",       build_gradle(slug, spec))
    write_file(out / "settings.gradle",    settings_gradle(slug))
    write_file(out / "gradle.properties",  gradle_properties())

    # 3. manifest
    write_file(out / "src" / "main" / "AndroidManifest.xml", manifest_xml(spec, arch))

    # 4. resources
    res = out / "src" / "main" / "res"
    write_file(res / "values" / "colors.xml",  colors_xml(palette))
    write_file(res / "values" / "themes.xml",  themes_xml())
    write_file(res / "values" / "strings.xml", strings_xml(slug, title))
    (res / "values-night").mkdir(parents=True, exist_ok=True)
    write_file(res / "values-night" / "colors.xml",  night_colors_xml(palette))
    write_file(res / "values-night" / "themes.xml",  night_themes_xml())
    # Placeholder layout
    (res / "layout").mkdir(parents=True, exist_ok=True)
    write_file(res / "layout" / "activity_main.xml", textwrap.dedent("""\
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@color/bg_light" />
    """))
    write_file(res / "layout" / "activity_about.xml", textwrap.dedent("""\
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    """))
    # Launcher icons: reuse hello-appfactory PNGs (generically valid)
    for density in ("mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"):
        src = HELLO / "src" / "main" / "res" / f"mipmap-{density}" / "ic_launcher.png"
        dst = res / f"mipmap-{density}"
        dst.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst / "ic_launcher.png")
    adaptive = HELLO / "src" / "main" / "res" / "mipmap-anydpi-v26" / "ic_launcher.xml"
    dst_any = res / "mipmap-anydpi-v26"
    dst_any.mkdir(parents=True, exist_ok=True)
    shutil.copy2(adaptive, dst_any / "ic_launcher.xml")
    fg = HELLO / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.xml"
    (res / "drawable").mkdir(parents=True, exist_ok=True)
    shutil.copy2(fg, res / "drawable" / "ic_launcher_foreground.xml")

    # 5. java sources
    java = out / "src" / "main" / "java" / "com" / "appfactory" / jsafe
    write_file(java / "MainActivity.java",      main_activity_java(slug, title, features))
    write_file(java / "DetailActivity.java",    detail_activity_java(slug))
    write_file(java / "AboutActivity.java",     about_activity_java(slug, title))
    write_file(java / "SettingsActivity.java",  settings_activity_java(slug, auth))
    write_file(java / "Item.java",              item_java(slug))
    write_file(java / "ItemStore.java",         item_store_java(slug))
    write_file(java / "ItemsActivity.java",     items_activity_java(slug))

    # 6. copy reusable modules (vendored source)
    mods = list(arch.get("modules", []))
    copy_modules(slug, mods, out)

    # 7. tests: include module tests + app-level tests
    test_java = out / "src" / "test" / "java" / "com" / "appfactory" / jsafe
    write_file(test_java / "ItemTest.java", item_test_java(slug))

    # 8. metadata files
    write_file(out / "app-spec.json",  json.dumps(spec, indent=1, sort_keys=True))
    write_file(out / "architecture.json", json.dumps(arch, indent=1, sort_keys=True))
    write_file(out / "PLAN.md",          plan_md_from_spec(spec, arch))
    write_file(out / "DESIGN.md",        design_md(spec))
    write_file(out / "release.json",     json.dumps({"app": slug}, indent=1))

    return out


def plan_md_from_spec(spec, arch):
    lines = [f"# PLAN — {spec.get('name')} ({spec.get('slug')})"]
    lines.append("")
    cx = spec.get("complexity",{})
    lines.append(f"## Complexity: {cx.get('level')} ({cx.get('score')}/100)\n")
    lines.append(f"## Modules\n{json.dumps(arch.get('modules',[]), indent=1)}\n")
    lines.append(f"## Database\n{json.dumps(arch.get('database',{}), indent=1)}\n")
    lines.append(f"## APIs\n{json.dumps(arch.get('apis',[]), indent=1)}\n")
    lines.append(f"## Permissions\n{json.dumps(arch.get('permissions',[]), indent=1)}\n")
    lines.append(f"## Testing\n{json.dumps(arch.get('testing',{}), indent=1)}\n")
    lines.append("_Generated by fastlane-scaffold; agent-approved._\n")
    return "\n".join(lines)


def main(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: scaffold.py <app-spec.json> <architecture.json> [parent_dir]\n")
        return 2
    spec  = json.loads(Path(argv[1]).read_text())
    arch  = json.loads(Path(argv[2]).read_text())
    parent = argv[3] if len(argv) > 3 else str(ROOT / "apps")
    out   = Path(parent) / spec["slug"]
    os.makedirs(out, exist_ok=True)
    scaffold(spec, arch, str(out))
    print(json.dumps({"status": "ok", "slug": spec["slug"], "out": str(out)}))
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv))