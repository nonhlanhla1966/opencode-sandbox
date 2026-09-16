"""Data-analysis capability for AppFactory Assistant.

Deterministic, local, provider-free data analysis for common tabular
formats (CSV, TSV, JSON, JSONL). Produces machine-checkable numeric
summaries — counts, min/max/mean/median/stdev, missing-value counts,
top-N categorical counts, and numeric pair correlations — plus a short
`insight` line. The analysis is computed here, never hallucinated.

Golden rules:
- binary / unknown formats are rejected with a reason (never guessed)
- sensitive values are redacted before the excerpt leaves the boundary
- a provider is never required: output is fully local and repeatable
- when a row/column cannot be parsed numerically it is reported as such
"""

from __future__ import annotations

import csv
import io
import json
import math
import statistics
from pathlib import Path

from common import sanitize_secrets

DATA_EXTENSIONS = {".csv", ".tsv", ".json", ".jsonl"}
MAX_ROWS = 10000
MAX_CELL_CHARS = 2000


def _resolve_delimiter(p: Path) -> str:
    return "\t" if p.suffix.lower() == ".tsv" else ","


def _looks_like_text(p: Path) -> bool:
    sample = p.read_bytes()[:4096]
    if not sample:
        return True
    if b"\x00" in sample:
        return False
    printable = sum(1 for b in sample if 9 <= b <= 13 or 32 <= b <= 126)
    return printable / len(sample) >= 0.5


def _rows_from_csv(p: Path) -> tuple[list[dict], str | None]:
    text = p.read_text(encoding="utf-8", errors="replace")
    try:
        reader = csv.DictReader(io.StringIO(text), delimiter=_resolve_delimiter(p))
        if not reader.fieldnames:
            return [], "no header row found in tabular data"
        rows = []
        for i, row in enumerate(reader):
            if i >= MAX_ROWS:
                break
            if row is None:
                continue
            cleaned = {}
            for k in reader.fieldnames:
                val = row.get(k)
                cleaned[k] = "" if val is None else str(val).strip()
            rows.append(cleaned)
        if not rows:
            return [], "tabular data has a header but zero data rows"
        return rows, None
    except (csv.Error, ValueError) as e:
        return [], f"could not parse tabular data: {e}"


def _rows_from_json(p: Path) -> tuple[list[dict], str | None]:
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (ValueError, OSError) as e:
        return [], f"could not parse JSON data: {e}"
    if isinstance(data, list):
        rows = [r for r in data if isinstance(r, dict)]
        if not rows and data:
            return [], "JSON data is a list of non-object values"
        return rows[:MAX_ROWS], None
    if isinstance(data, dict):
        if "rows" in data and isinstance(data["rows"], list):
            return [r for r in data["rows"] if isinstance(r, dict)][:MAX_ROWS], None
        if "data" in data and isinstance(data["data"], list):
            return [r for r in data["data"] if isinstance(r, dict)][:MAX_ROWS], None
        if all(isinstance(v, list) for v in data.values()) and data:
            # columnar form {"col":[v...]}; transpose to row dicts
            keys = list(data.keys())
            lengths = {len(data[k]) for k in keys}
            n = min(lengths, default=0)
            rows = []
            for i in range(n):
                rows.append({k: str(data[k][i]) for k in keys})
            return rows, None
        return [], "JSON data is neither a list of objects nor a recognized tabular shape"
    return [], f"unexpected JSON shape ({type(data).__name__}); expected list of objects"


def _load(p: Path) -> tuple[list[dict], list[str], str | None]:
    ext = p.suffix.lower()
    if ext == ".json":
        rows, err = _rows_from_json(p)
    elif ext == ".jsonl":
        rows = []
        err = None
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines()):
            if i >= MAX_ROWS:
                break
            if not line.strip():
                continue
            try:
                obj = json.loads(line)
            except (ValueError, OSError):
                err = f"malformed JSON on line {i + 1}"
                continue
            if isinstance(obj, dict):
                rows.append({str(k): ("" if v is None else str(v)) for k, v in obj.items()})
        if err and not rows:
            return [], [], err
    else:
        rows, err = _rows_from_csv(p)
    if err:
        return [], [], err
    cols: list[str] = []
    for r in rows:
        for k in r:
            if k not in cols:
                cols.append(k)
    return rows, cols, None


def _to_float(v: str) -> float | None:
    v = (v or "").strip()
    if v == "":
        return None
    try:
        return float(v)
    except ValueError:
        return None


def _numeric_columns(rows: list[dict], cols: list[str]) -> dict[str, list[float]]:
    out: dict[str, list[float]] = {}
    for col in cols:
        vals: list[float] = []
        for r in rows:
            f = _to_float(r.get(col, ""))
            if f is not None:
                vals.append(f)
        if vals:
            out[col] = vals
    return out


def _describe(vals: list[float]) -> dict:
    n = len(vals)
    s = sorted(vals)
    return {
        "count": n,
        "min": round(s[0], 6),
        "max": round(s[-1], 6),
        "mean": round(statistics.mean(vals), 6),
        "median": round(statistics.median(vals), 6),
        "stdev": round(statistics.pstdev(vals), 6),
        "sum": round(sum(vals), 6),
    }


def _top_counts(rows: list[dict], col: str, limit: int) -> list[dict]:
    counts: dict[str, int] = {}
    for r in rows:
        v = (r.get(col, "") or "").strip() or "(empty)"
        counts[v] = counts.get(v, 0) + 1
    order = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    return [{"value": sanitize_secrets(k)[:MAX_CELL_CHARS], "count": c} for k, c in order[:limit]]


def _correlate(a: list[float], b: list[float]) -> float | None:
    """Pearson correlation over the overlapping sample."""
    pairs = [(x, y) for x, y in zip(a, b)]
    n = len(pairs)
    if n < 3:
        return None
    mx = statistics.mean(x for x, _ in pairs)
    my = statistics.mean(y for _, y in pairs)
    num = sum((x - mx) * (y - my) for x, y in pairs)
    dx = math.sqrt(sum((x - mx) ** 2 for x, _ in pairs)) or 0.0
    dy = math.sqrt(sum((y - my) ** 2 for _, y in pairs)) or 0.0
    if dx == 0 or dy == 0:
        return None
    return round(num / (dx * dy), 4)


def _insight(rows: list[dict], cols: list[str], numerics: dict[str, list[float]]) -> str:
    if not rows:
        return "the data has a header but no analyzable rows"
    parts = []
    parts.append(f"{len(rows)} rows across {len(cols)} columns")
    if not numerics:
        parts.append("no numeric columns found for statistical summaries")
        return "; ".join(parts)
    top_col = max(numerics, key=lambda c: len(numerics[c]))
    d = _describe(numerics[top_col])
    parts.append(f"'{top_col}' has {d['count']} numeric values ranging {d['min']} to {d['max']}")
    if len(numerics) >= 2:
        cols_l = list(numerics)
        r = _correlate(numerics[cols_l[0]], numerics[cols_l[1]])
        if r is not None:
            strength = abs(r)
            label = "strong" if strength >= 0.7 else "moderate" if strength >= 0.4 else "weak"
            parts.append(f"correlation between '{cols_l[0]}' and '{cols_l[1]}' is {r:.2f} ({label})")
    return ". ".join(parts)


def analyze_data(path: str, method: str = "auto", question: str | None = None) -> dict:
    """Deterministic local data analysis. Never requires a provider."""
    p = Path(path)
    base = {"command": "data_analysis", "path": str(p), "name": p.name}
    if not p.is_file():
        return {"ok": False, **base, "error": f"data file not found: {path}"}
    if p.stat().st_size == 0:
        return {"ok": False, **base, "error": "data file is empty"}
    if p.suffix.lower() not in DATA_EXTENSIONS:
        ext = p.suffix.lower() or "unknown"
        return {
            "ok": False,
            **base,
            "error": f"unsupported data format '{ext}'; supported: {sorted(DATA_EXTENSIONS)}",
        }
    if not _looks_like_text(p):
        return {"ok": False, **base, "error": "binary content cannot be analyzed as tabular data"}

    rows, cols, err = _load(p)
    if err:
        return {"ok": False, **base, "error": err}
    numerics = _numeric_columns(rows, cols)

    result: dict = {
        "ok": True,
        "bytes": p.stat().st_size,
        "format": p.suffix.lstrip(".") or "text",
        "rows": len(rows),
        "columns": len(cols),
        "column_names": cols,
    }
    result["summary"] = {c: _describe(v) for c, v in numerics.items()}
    result["counts"] = {c: _top_counts(rows, c, 5) for c in cols[:8]}
    result["null_counts"] = {c: sum(1 for r in rows if (r.get(c) or "").strip() == "") for c in cols}

    if len(numerics) >= 2:
        pairs: list[dict] = []
        keys = list(numerics)
        for i in range(len(keys)):
            for j in range(i + 1, len(keys)):
                rho = _correlate(numerics[keys[i]], numerics[keys[j]])
                if rho is not None:
                    pairs.append({"x": keys[i], "y": keys[j], "pearson": rho})
        result["correlations"] = pairs

    result["insight"] = _insight(rows, cols, numerics)
    if question:
        result["question"] = sanitize_secrets(question)[:1000]
        result["answer_note"] = (
            "numeric summaries above answer the question from the data; "
            "free-text explanation can be produced by the chat capability when a provider is configured"
        )

    if method in ("summary", "describe"):
        result["method"] = method
    else:
        result["method"] = "auto"
    return result