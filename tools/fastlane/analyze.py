#!/usr/bin/env python3
"""analyze.py — Fast Lane App Idea Analyzer (1. APP IDEA ANALYZER).

Takes ONE plain-language app idea and produces a structured, machine-readable
App Specification (app-spec.json) plus an automatic complexity + build-time
estimate. Fully deterministic: the same idea always yields the same spec.

Output schema (app-spec.json):
  {
    "name": str, "slug": str, "application_id": str,
    "summary": str, "features": [str], "screens": [str],
    "navigation": str, "data": {...}, "apis": [str],
    "permissions": [str], "auth": str, "storage": [str],
    "notifications": bool, "media": [str], "testing": [str],
    "modules": [str], "archetype": str,
    "complexity": {"level": str, "score": int, "factors": [str]},
    "estimate": {"build_time_seconds": int, "band": str},
    "generated_by": "fastlane-analyze", "final": true
  }
"""

import json
import re
import sys
from pathlib import Path

MODULES_DIR = Path(__file__).resolve().parent / ".." / ".." / "modules"


# --------------------------------------------------------------------------- #
# deterministic "intelligence" tables
# --------------------------------------------------------------------------- #
FEATURE_KEYWORDS = {
    "habit tracker": ["habits", "streak", "reminder", "routine"],
    "notes": ["note", "notepad", "scratchpad", "memo", "journal"],
    "todo list": ["todo", "tasks", "to-do", "checklist", "kanban", "task list"],
    "calorie counter": ["calorie", "calories", "food log"],
    "fitness/workout": ["workout", "exercise", "step counter", "fitness", "reps", "sets"],
    "budget/finance": ["budget", "expense", "income", "finance", "money", "spend"],
    "flashlight/torch": ["flashlight", "torch", "led light"],
    "chat/messaging": ["chat", "messaging", "message", "conversation", "ai chat", "assistant", "chatbot"],
    "timer/pomodoro": ["timer", "pomodoro", "stopwatch", "countdown"],
    "weather": ["weather", "forecast"],
    "location/maps": ["location", "gps", "map", "nearby", "places"],
    "shopping/inventory": ["shopping", "inventory", "stock", "list price", "products"],
    "scanner": ["scan", "barcode", "qr", "code"],
    "crypto/secure": ["encrypt", "encrypted", "secure", "vault", "password manager"],
    "offline-first": ["offline", "works offline", "no internet", "local"],
}

PERMISSION_KEYWORDS = {
    "android.permission.CAMERA": ["camera", "photo", "scan"],
    "android.permission.INTERNET": ["online", "api", "web", "weather", "chat", "sync", "server"],
    "android.permission.ACCESS_FINE_LOCATION": ["location", "gps", "nearby", "map"],
    "android.permission.POST_NOTIFICATIONS": ["notification", "remind", "alert", "alarm"],
    "android.permission.RECORD_AUDIO": ["record audio", "voice note", "mic"],
    "android.permission.BLUETOOTH_CONNECT": ["bluetooth"],
}

STORAGE_KEYWORDS = {
    "sqlite": ["note", "todo", "offline", "store", "history", "save", "journal", "habit", "inventory", "expense", "budget"],
    "datastore/prefs": ["settings", "preference", "config"],
}

API_HINTS = {
    "openai-compatible": ["chat", "assistant", "llm", "ai", "gpt", "model"],
    "weather": ["weather", "forecast"],
    "maps": ["map", "location", "nearby"],
    "currency": ["exchange rate", "currency", "forex"],
    "generic rest": ["rest", "api", "sync", "web service"],
}

SCREEN_KEYWORDS = {
    "Home": ["home", "dashboard", "main", "landing", "today"],
    "Settings": ["settings", "preferences", "configure"],
    "Login": ["login", "sign in", "signin", "account", "auth"],
    "List": ["list", "history", "library", "inbox", "catalog", "browse"],
    "Detail": ["detail", "view", "profile"],
    "Editor/Form": ["add", "create", "edit", "new", "form", "log"],
    "Stats": ["stat", "chart", "graph", "streak", "trend", "progress", "report"],
    "Notifications": ["notification", "alert", "reminder"],
}

AUTH_HINTS = [
    ("cloud account", ["login", "sign in", "account", "user profile", "auth"]),
    ("api key (user-supplied)", ["api key", "api token"]),
    ("none", []),
]

TESTING_KEYWORDS = {
    "unit": ["logic", "calculator", "business", "validation"],
    "database": ["storage", "sqlite", "offline", "history", "save"],
    "api": ["rest", "api", "sync"],
    "ui/smoke": ["screen", "navigation", "menu", "workflow"],
}

MEDIA_HINTS = {
    "camera": ["camera", "photo", "picture", "capture"],
    "audio": ["audio", "voice", "sound"],
    "video": ["video"],
    "image-processing": ["gallery", "image", "picture", "photo"],
    "pdf": ["pdf", "document", "print"],
}


def slug_of(idea: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", idea.lower()).strip("-")
    return s or "app"


def _collect(idea_l: str, table: dict) -> list:
    hits = []
    for key, kws in table.items():
        for kw in kws:
            if kw in idea_l:
                hits.append((key, kw))
                break
    return hits


def analyze(idea: str):
    l = idea.lower()

    features = [k for k, _ in _collect(l, FEATURE_KEYWORDS)]
    if not features:
        features = ["core functionality"]

    screens = []
    for key in ("Home",):
        screens.append(key)
    for key, _ in _collect(l, SCREEN_KEYWORDS):
        if key not in screens:
            screens.append(key)

    permissions = []
    for perm, kws in PERMISSION_KEYWORDS.items():
        if any(kw in l for kw in kws) and perm not in permissions:
            permissions.append(perm)

    storage = []
    for st, kws in STORAGE_KEYWORDS.items():
        if any(kw in l for kw in kws) and st not in storage:
            storage.append(st)
    if not storage:
        storage.append("datastore/prefs")

    apis = [k for k, kws in API_HINTS.items() if any(kw in l for kw in kws)]
    media = [k for k, kws in MEDIA_HINTS.items() if any(kw in l for kw in kws)]
    notifications = any(kw in l for kw in PERMISSION_KEYWORDS["android.permission.POST_NOTIFICATIONS"])

    auth = "none"
    for label, kws in AUTH_HINTS:
        if any(kw in l for kw in kws):
            auth = label
            break

    testing = [k for k, kws in TESTING_KEYWORDS.items() if any(kw in l for kw in kws)]
    if not testing:
        testing = ["unit"]

    modules = []
    if "sqlite" in storage:
        modules.append("storage-sqlite")
    if "openai-compatible" in apis or "ai" in features:
        modules.append("http-rest")
    if notifications:
        modules.append("notifications")
    if "datastore/prefs" in storage or "Settings" in screens:
        modules.append("settings")
    if any(kw in l for kw in ("camera", "photo", "gallery", "image", "scan", "qr")):
        modules.append("media-image")

    # ------ complexity scoring (deterministic, weighted) -------------------- #
    score = 0
    factors = []
    n_screens = len(screens)
    score += min(n_screens, 6) * 6
    if n_screens >= 4:
        factors.append(f"{n_screens} screens")
    if len(permissions) > 1:
        score += 8
        factors.append("multiple permissions")
    if "android.permission.CAMERA" in permissions:
        score += 6
        factors.append("camera")
    if apis:
        score += 12 if "openai-compatible" in apis else 8
        factors.append("network API")
    if auth != "none":
        score += 8
        factors.append("authentication")
    if notifications:
        score += 5
        factors.append("notifications")
    if media:
        score += 6
        factors.append("media handling")
    if "offline-first" in features or "sqlite" in storage:
        score += 6
        factors.append("offline/database")
    if any(kw in l for kw in ("sync", "realtime", "websocket", "streaming")):
        score += 10
        factors.append("realtime/streaming")
    if any(kw in l for kw in ("chat", "messaging", "assistant")):
        score += 8
        factors.append("messaging")
    if any(kw in l for kw in ("encrypt", "secure", "vault")):
        score += 8
        factors.append("security/crypto")
    if "Stats" in screens:
        score += 5
        factors.append("statistics")
    # normalize into 0..100
    score = min(100, max(5, score))
    if score < 25:
        level = "SIMPLE"
    elif score < 55:
        level = "MEDIUM"
    elif score < 80:
        level = "COMPLEX"
    else:
        level = "EXTREME"

    # -------- build-time estimate (baseline, tuned by telemetry) ----------- #
    per_app = {"SIMPLE": 60, "MEDIUM": 120, "COMPLEX": 210, "EXTREME": 420}[level]
    # cold-cache multiplier is applied at build time when caches are known;
    # here we report the warm estimate band only.
    band = {"SIMPLE": "under 2 minutes", "MEDIUM": "2-5 minutes",
            "COMPLEX": "~5 minutes", "EXTREME": "5-10+ minutes"}[level]

    spec = {
        "name": idea,
        "slug": slug_of(idea),
        "application_id": f"com.appfactory.{slug_of(idea)}",
        "summary": f"A {' '.join(features)} app: {idea}",
        "features": features,
        "screens": screens,
        "navigation": "Single-activity flow with back navigation between screens."
                      if len(screens) <= 2 else
                      "List->Detail navigation with a Settings screen reachable from the toolbar; system back returns Home.",
        "data": {
            "entities": [
                {"name": e, "fields": "text, timestamp"}
                for e in features[:2]
            ],
        },
        "apis": apis,
        "permissions": permissions,
        "auth": auth,
        "storage": storage,
        "notifications": notifications,
        "media": media,
        "testing": testing,
        "modules": modules,
        "archetype": archetype_for(features, screens),
        "complexity": {"level": level, "score": score, "factors": factors},
        "estimate": {"build_time_seconds": per_app, "band": band},
        "generated_by": "fastlane-analyze-v1",
        "final": True,
    }
    return spec


def archetype_for(features, screens):
    if "chat/messaging" in features:
        return "conversation"
    if any(f in features for f in ("notes", "todo list", "habit tracker",
                                    "budget/finance", "shopping/inventory")):
        return "catalog" if "Detail" in screens else "list-form"
    if "timer/pomodoro" in features or "flashlight/torch" in features:
        return "single-purpose"
    if "settings" in [s.lower() for s in screens]:
        return "settings-shell"
    return "display"


def resolve_available_modules():
    mods = {}
    if MODULES_DIR.is_dir():
        for d in MODULES_DIR.iterdir():
            mj = d / "module.json"
            if mj.is_file():
                try:
                    meta = json.loads(mj.read_text())
                    mods[meta["id"]] = meta
                except Exception:
                    pass
    return mods


def main(argv):
    idea = " ".join(argv[1:]).strip()
    if not idea:
        sys.stderr.write("usage: analyze.py '<one-line app idea>'\n")
        return 2
    spec = analyze(idea)
    print(json.dumps(spec, indent=1, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))