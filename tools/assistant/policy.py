"""Safety / content-policy layer for AppFactory Assistant.

The policy gate runs on every user-supplied prompt and every routed request.
It refuses clearly prohibited categories and flags 18+ material for an age
confirmation instead of silently serving it. It never blocks ordinary coding,
circuit/SDK building, or legitimate assistance; it is a deterministic keyword
heuristic, not a replacement for a provider-level moderation pass.
"""

from __future__ import annotations

import re

# Refusals: these categories are never serviced.
REFUSED_CATEGORIES = {
    "exploitation_minors": [
        r"\b(csam|child\sse[^ ]*\s?abuse)\b",
        r"\b(child|minor|underage)\b.{0,40}\b(sexual|nude|naked|grooming)\b",
        r"\b(sexual|nude|naked)\b.{0,40}\b(child|minor|underage)\b",
        r"\bminor\b.{0,20}\b(erotica|explicit)\b",
    ],
    "nonconsensual_sexual": [
        r"\b(revenge\s?porn|non-?consensual|unsolicited)\b",
        r"\b(deepfake|deepfake\snudes?)\b",
        r"\b(imap|leak) naked\b",
        r"\bspycam\b",
        r"\b(non? ?consensual|coerced|forced)\b.*\b(sex|sexual|nudes?)\b",
    ],
    "exploitation_harassment": [
        r"\b(sextortion|extort)\b",
        r"\bstalk(ing|er)?\b",
        r"\b(harass|harassment|bully)\b",
        r"\bdoxx?ing\b",
        r"\b(force|pressure|blackmail)\b.{0,20}\b(sex|nudes?|explicit)\b",
    ],
    "safety_bypass": [
        r"\b(ignore|bypass|override|disable|turn off|remove|strip)\b.{0,30}\b(your|the)\b.{0,20}\b(instructions|safety|filter|guardrails?|guidelines|rules)\b",
        r"\bjailbreak\b",
        r"\breveal (your|the hidden) (system|prompt|instructions)\b",
        r"\b(dan|do anything now)\b.?$",
    ],
}

# Flagged for age confirmation, not auto-refused.
ADULT_18PLUS = [
    r"\b18\+|adult\scontent|nsfw-sexual|explicit\s(sexual)?(content)?\b",
    r"\bsexually\s?explicit\b",
]


def _any(category_patterns: dict, text: str) -> list[str]:
    low = text.lower()
    hits = []
    for cat, patterns in category_patterns.items():
        for pat in patterns:
            if re.search(pat, low):
                hits.append(cat)
                break
    return hits


def check(text: str) -> dict:
    if not text or not text.strip():
        return {"allowed": True, "refused": [], "reason": "", "age_gate": False}
    refused = _any(REFUSED_CATEGORIES, text)
    if refused:
        return {
            "allowed": False,
            "refused": refused,
            "reason": "request refused by content policy (" + ", ".join(refused) + ")",
            "age_gate": False,
        }
    adult = bool(_any({"18plus": ADULT_18PLUS}, text))
    return {"allowed": True, "refused": [], "reason": "", "age_gate": adult}