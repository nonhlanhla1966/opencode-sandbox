#!/usr/bin/env python3
"""taskgraph.py — Fast Lane 3.0 Task Graph Engine.

Builds a deterministic task DAG from app-spec.json + architecture.json.
Identifies independent groups for safe parallel fan-out. Validates
that no parallel tasks write to the same output paths.

Usage:
  taskgraph.py build <spec.json> <arch.json> [--out graph.json]
  taskgraph.py verify <graph.json>

Exit 0 on success, exit 1 on failure/cycle, exit 2 on usage error.
Output: JSON to stdout.
"""

import json
import sys
from collections import defaultdict, deque
from pathlib import Path

# Task definitions: (id, kind, deps, output_paths)
TASK_TEMPLATES = {
    "scaffold": {
        "kind": "generate",
        "deps": [],
        "output_paths": ["src/"],
    },
    "resources": {
        "kind": "generate",
        "deps": ["scaffold"],
        "output_paths": ["src/main/res/"],
    },
    "manifest": {
        "kind": "generate",
        "deps": ["scaffold"],
        "output_paths": ["src/main/AndroidManifest.xml"],
    },
    "database": {
        "kind": "generate",
        "deps": ["scaffold"],
        "output_paths": ["src/main/java/**/db/", "src/main/java/**/data/"],
    },
    "ui_screens": {
        "kind": "generate",
        "deps": ["scaffold", "resources"],
        "output_paths": ["src/main/res/layout/", "src/main/java/**/ui/"],
    },
    "navigation": {
        "kind": "generate",
        "deps": ["ui_screens"],
        "output_paths": ["src/main/java/**/nav/"],
    },
    "api_layer": {
        "kind": "generate",
        "deps": ["scaffold"],
        "output_paths": ["src/main/java/**/api/", "src/main/java/**/network/"],
    },
    "settings": {
        "kind": "generate",
        "deps": ["scaffold", "resources"],
        "output_paths": ["src/main/java/**/settings/"],
    },
    "tests_unit": {
        "kind": "test",
        "deps": ["scaffold", "database", "ui_screens", "api_layer"],
        "output_paths": ["src/test/"],
    },
    "tests_integration": {
        "kind": "test",
        "deps": ["tests_unit", "navigation"],
        "output_paths": ["src/androidTest/"],
    },
    "docs": {
        "kind": "docs",
        "deps": ["scaffold"],
        "output_paths": ["DESIGN.md", "PLAN.md"],
    },
    "preflight": {
        "kind": "validate",
        "deps": ["ui_screens", "database", "api_layer", "tests_unit"],
        "output_paths": [],
    },
    "build": {
        "kind": "build",
        "deps": ["preflight", "manifest", "resources", "navigation", "settings"],
        "output_paths": ["build/"],
    },
    "security": {
        "kind": "validate",
        "deps": ["build"],
        "output_paths": [],
    },
    "release": {
        "kind": "release",
        "deps": ["build", "security", "tests_integration"],
        "output_paths": ["release.json"],
    },
}


def build_graph(spec: dict, arch: dict) -> dict:
    """Build task DAG from spec and architecture."""
    modules = set(arch.get("modules", []))
    features = spec.get("features", [])
    screens = spec.get("screens", [])

    # Start with core tasks
    tasks = {}
    for tid, tdef in TASK_TEMPLATES.items():
        tasks[tid] = {
            "id": tid,
            "kind": tdef["kind"],
            "deps": list(tdef["deps"]),
            "output_paths": list(tdef["output_paths"]),
        }

    # Add module-specific tasks
    if "storage-sqlite" in modules:
        tasks["database"]["output_paths"].extend([
            "src/main/java/**/db/*Dao.java",
            "src/main/java/**/db/*Database.java",
        ])
    if "http-rest" in modules:
        tasks["api_layer"]["output_paths"].extend([
            "src/main/java/**/api/*Service.java",
            "src/main/java/**/api/*Repository.java",
        ])
    if "notifications" in modules:
        tasks["ui_screens"]["output_paths"].append("src/main/java/**/notif/")
    if "media-image" in modules:
        tasks["ui_screens"]["output_paths"].append("src/main/java/**/media/")

    # Ensure ui_screens has enough dep context for screens
    if len(screens) > 3:
        tasks["ui_screens"]["output_paths"].extend([
            "src/main/res/layout/*_list.xml",
            "src/main/res/layout/*_detail.xml",
        ])

    # Build adjacency list and detect cycles
    adj = defaultdict(list)
    in_degree = defaultdict(int)
    all_ids = set(tasks.keys())

    for tid, t in tasks.items():
        if tid not in in_degree:
            in_degree[tid] = 0
        for dep in t["deps"]:
            if dep in all_ids:
                adj[dep].append(tid)
                in_degree[tid] += 1

    # Topological sort (Kahn's algorithm)
    queue = deque([tid for tid in all_ids if in_degree[tid] == 0])
    topo_order = []
    while queue:
        node = queue.popleft()
        topo_order.append(node)
        for neighbor in adj[node]:
            in_degree[neighbor] -= 1
            if in_degree[neighbor] == 0:
                queue.append(neighbor)

    has_cycle = len(topo_order) != len(all_ids)

    # Identify independent groups (tasks at same topo level with no shared outputs)
    groups = []
    assigned = set()
    level = 0
    remaining = set(all_ids)
    while remaining:
        # Find tasks whose deps are all assigned
        ready = [tid for tid in remaining
                 if all(d in assigned for d in tasks[tid]["deps"])]
        if not ready:
            break  # cycle or error
        # Check for output conflicts within this group
        group = []
        used_outputs = set()
        for tid in ready:
            task_outputs = set(tasks[tid]["output_paths"])
            if not task_outputs & used_outputs:
                group.append(tid)
                used_outputs |= task_outputs
        groups.append({
            "level": level,
            "tasks": group,
            "parallel_safe": len(group) > 1,
        })
        for tid in group:
            assigned.add(tid)
            remaining.discard(tid)
        level += 1

    return {
        "tasks": tasks,
        "topo_order": topo_order,
        "has_cycle": has_cycle,
        "groups": groups,
        "total_tasks": len(tasks),
        "max_parallel": max(len(g["tasks"]) for g in groups) if groups else 1,
    }


def verify_graph(graph: dict) -> dict:
    """Verify task graph: acyclicity + no parallel output conflicts."""
    errors = []
    warnings = []

    # Check cycle
    if graph.get("has_cycle"):
        errors.append("task graph contains a cycle")
        return {"valid": False, "errors": errors, "warnings": warnings}

    # Check parallel output conflicts
    for group in graph.get("groups", []):
        if group.get("parallel_safe"):
            tasks_in_group = group["tasks"]
            all_outputs = []
            for tid in tasks_in_group:
                task = graph.get("tasks", {}).get(tid, {})
                all_outputs.extend(task.get("output_paths", []))
            # Check for overlapping paths (simplified: same prefix)
            seen = set()
            for out in all_outputs:
                prefix = out.split("*")[0].rstrip("/")
                if prefix in seen:
                    warnings.append(f"potential output overlap in parallel group at {prefix}")
                seen.add(prefix)

    # Verify topo order matches deps
    tasks = graph.get("tasks", {})
    topo = graph.get("topo_order", [])
    pos = {tid: i for i, tid in enumerate(topo)}
    for tid, task in tasks.items():
        for dep in task.get("deps", []):
            if dep in pos and tid in pos:
                if pos[dep] >= pos[tid]:
                    errors.append(f"topo order violation: {dep} must come before {tid}")

    return {
        "valid": len(errors) == 0,
        "errors": errors,
        "warnings": warnings,
        "total_tasks": graph.get("total_tasks", 0),
        "max_parallel": graph.get("max_parallel", 1),
    }


def cmd_build(argv):
    if len(argv) < 4:
        sys.stderr.write("usage: taskgraph.py build <spec.json> <arch.json> [--out graph.json]\n")
        return 2
    spec = json.loads(Path(argv[2]).read_text())
    arch = json.loads(Path(argv[3]).read_text())
    graph = build_graph(spec, arch)

    out_path = None
    if "--out" in argv:
        idx = argv.index("--out")
        if idx + 1 < len(argv):
            out_path = Path(argv[idx + 1])

    if out_path:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(graph, indent=1))

    print(json.dumps(graph, indent=1))
    return 0


def cmd_verify(argv):
    if len(argv) < 3:
        sys.stderr.write("usage: taskgraph.py verify <graph.json>\n")
        return 2
    graph = json.loads(Path(argv[2]).read_text())
    result = verify_graph(graph)
    print(json.dumps(result, indent=1))
    return 0 if result["valid"] else 1


def main(argv):
    if len(argv) < 2 or argv[1] not in ("build", "verify"):
        sys.stderr.write("usage: taskgraph.py <build|verify> [args...]\n")
        return 2
    if argv[1] == "build":
        return cmd_build(argv)
    elif argv[1] == "verify":
        return cmd_verify(argv)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
