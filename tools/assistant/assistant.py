#!/usr/bin/env python3
"""assistant.py — AppFactory Assistant CLI (AI chat + vision/OCR + imagegen +
web search + file/document + data analysis + AI->App builder + sessions).

Fast Lane conventions: deterministic CLIs, JSON on stdout, human notes on
stderr, the user's API key only ever in the environment, HTTPS/TLS only, and
never-fake rules for unconfigured capabilities.

Usage:
  assistant.py providers list|init|test [id]|models [id]
  assistant.py chat new|list|get|rename|delete|clear|context
  assistant.py chat send <id> --text "..." [--image p] [--file p] [--stream]
  assistant.py chat retry|regenerate <id>
  assistant.py chat edit <id> <msg-index> --text "..."
  assistant.py route <text> [--attach a,b]
  assistant.py vision <image> [--question "..."]           (visual Q&A / description)
  assistant.py vision ocr <image>
  assistant.py vision identify <image> [--kind plant|object|animal|auto]
  assistant.py imagegen <prompt> --out out.png [--size 1024x1024]
  assistant.py web <query> [--limit n]
  assistant.py files extract <path>
  assistant.py data analyze <path> [--question "..."]
  assistant.py session <conversation-id> <text> [--image p] [--file p]
  assistant.py build <idea> [--modify <slug>] [--release] [--staging <dir>]
  assistant.py policy <text>
  assistant.py models [provider-id]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import builder  # noqa: E402
import chat as chatmod  # noqa: E402
import data  # noqa: E402
import files  # noqa: E402
import imagegen  # noqa: E402
import policy  # noqa: E402
import providers  # noqa: E402
import router  # noqa: E402
import session  # noqa: E402
import vision  # noqa: E402
import websearch  # noqa: E402
from common import out, read_json, sanitize_secrets  # noqa: E402


def _registry() -> providers.ProviderRegistry:
    return providers.ProviderRegistry()


def _guard(text: str, label: str = "request") -> dict | None:
    check = policy.check(text)
    if not check["allowed"]:
        out({"ok": False, "command": label, "policy": "refused", "refused": check["refused"], "reason": check["reason"]})
        return check
    return None


# ---- providers -------------------------------------------------------------
def _providers(args) -> None:
    reg = _registry()
    sub = args.provider_sub
    if sub == "list":
        providers.report_listing(reg)
    elif sub == "init":
        p = providers.init_example_config()
        providers.report_init(p)
    elif sub == "test":
        providers.report_test(reg, args.provider_id)
    elif sub == "models":
        _models(reg, args.provider_id)
    else:
        out({"ok": False, "error": f"unknown providers subcommand: {sub}"})


def _models(reg: providers.ProviderRegistry, provider_id: str | None) -> None:
    if provider_id:
        p = reg.get(provider_id)
        if p is None:
            out({"ok": False, "command": "models", "error": f"unknown provider: {provider_id}"})
            return
    else:
        p = reg.resolve("chat")
    provider = p
    if isinstance(provider, providers.MockProvider):
        out(
            {
                "ok": True,
                "command": "models",
                "mode": "mock",
                "provider": provider.id,
                "models": [{"id": "mock", "name": "deterministic offline mock"}],
                "note": "mock mode: actual provider model lists are only returned when a real provider is configured",
            }
        )
        return
    models_endpoint = provider.extra.get("models_endpoint") or _base_of(provider.endpoint)
    out(
        {
            "ok": True,
            "command": "models",
            "mode": "provider",
            "provider": provider.id,
            "models_endpoint": models_endpoint,
            "models": None,
            "note": "configure provider.extra['models_endpoint'] for auto-discovery; never auto-query unauthenticated endpoints in mock mode",
        }
    )


def _base_of(endpoint: str) -> str:
    if "/chat/completions" in endpoint:
        return endpoint[: endpoint.index("/chat/completions")] + "/models"
    if "/responses" in endpoint:
        return endpoint[: endpoint.index("/responses")] + "/models"
    return endpoint


# ---- chat ------------------------------------------------------------------
def _chat(args) -> None:
    engine = chatmod.ChatEngine(_registry())
    sub = args.chat_sub
    if sub == "new":
        conv = engine.new(title=args.title, provider_id=args.provider)
        out({"ok": True, "command": "chat new", "conversation_id": conv.id, "title": conv.title, "provider_id": conv.data["provider_id"]})
    elif sub == "list":
        out({"ok": True, "command": "chat list", "conversations": engine.list_conversations()})
    elif sub == "get":
        conv = engine.get(args.conversation_id)
        if conv is None:
            out({"ok": False, "command": "chat get", "error": f"unknown conversation: {args.conversation_id}"})
            return
        out({"ok": True, "command": "chat get", "conversation": conv.data})
    elif sub == "rename":
        conv = engine.rename(args.conversation_id, args.title)
        if conv is None:
            out({"ok": False, "command": "chat rename", "error": f"unknown conversation: {args.conversation_id}"})
            return
        out({"ok": True, "command": "chat rename", "conversation_id": conv.id, "title": conv.title})
    elif sub == "delete":
        ok = engine.delete(args.conversation_id)
        out({"ok": ok, "command": "chat delete", "conversation_id": args.conversation_id})
    elif sub == "clear":
        conv = engine.clear_messages(args.conversation_id)
        if conv is None:
            out({"ok": False, "command": "chat clear", "error": f"unknown conversation: {args.conversation_id}"})
            return
        out({"ok": True, "command": "chat clear", "conversation_id": conv.id, "messages": 0})
    elif sub == "context":
        ctx = engine.context(args.conversation_id, turns=args.turns)
        out(ctx)
    elif sub == "send":
        guard = _guard(args.text, "chat send")
        if guard:
            return
        attachments = []
        if args.image:
            attachments.append({"type": "image", "path": args.image})
        if args.file:
            attachments.append({"type": "file", "path": args.file})
            extracted = files.extract(args.file)
            if extracted.get("ok"):
                attachments.append({"type": "text", "text": extracted["excerpt"]})
            elif not args.image:
                out({"ok": False, "command": "chat send", "error": extracted.get("error", "cannot read attachment")})
                return
        opts = {}
        if args.system:
            opts["system"] = args.system
        if args.stop_after:
            opts["stop_at_chunk"] = args.stop_after
        result = engine.send(
            args.conversation_id,
            args.text,
            opts=opts,
            attachments=attachments,
            stream=not args.no_stream,
        )
        out(result)
    elif sub == "retry":
        out(engine.retry(args.conversation_id))
    elif sub == "regenerate":
        out(engine.regenerate(args.conversation_id))
    elif sub == "edit":
        out(engine.edit_resend(args.conversation_id, args.message_index, args.text))
    else:
        out({"ok": False, "error": f"unknown chat subcommand: {sub}"})


# ---- router ----------------------------------------------------------------
def _route(args) -> None:
    guard = _guard(args.text, "route")
    if guard:
        return
    attaches = [a for a in (args.attach or "").split(",") if a]
    out(router.route(args.text, attaches))


# ---- vision ----------------------------------------------------------------
def _vision(args) -> None:
    if getattr(args, "ocr", False):
        out(vision.ocr(_registry(), args.image))
    elif getattr(args, "identify", False):
        out(vision.identify(_registry(), args.image, args.kind))
    else:
        out(vision.describe(_registry(), args.image, args.question))


# ---- imagegen --------------------------------------------------------------
def _imagegen(args) -> None:
    out(imagegen.generate(_registry(), args.prompt, args.out, args.size))


# ---- web -------------------------------------------------------------------
def _web(args) -> None:
    out(websearch.search(_registry(), args.query, args.limit))


# ---- files -----------------------------------------------------------------
def _files(args) -> None:
    if args.file_sub == "extract":
        out(files.extract(args.path))
    else:
        out({"ok": False, "error": f"unknown files subcommand: {args.file_sub}"})


# ---- data ------------------------------------------------------------------
def _data(args) -> None:
    if args.data_sub == "analyze":
        out(data.analyze_data(args.path, method=args.method, question=args.question))
    else:
        out({"ok": False, "error": f"unknown data subcommand: {args.data_sub}"})


# ---- session ---------------------------------------------------------------
def _session(args) -> None:
    guard = _guard(args.text, "session")
    if guard:
        return
    attachments = []
    if args.image:
        attachments.append({"type": "image", "path": args.image})
    if args.file:
        ext = Path(args.file).suffix.lower()
        attachments.append({"type": "data" if ext in (".csv", ".tsv", ".json", ".jsonl") else "file", "path": args.file})
    engine = chatmod.ChatEngine(_registry())
    out(session.run(engine, args.conversation_id, args.text, attachments))


# ---- build -----------------------------------------------------------------
def _build(args) -> None:
    out(builder.build_app(args.idea, release=args.release, modify=args.modify, staging=args.staging))


# ---- policy ----------------------------------------------------------------
def _policy(args) -> None:
    out(policy.check(args.text))


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="assistant.py", description="AppFactory AI Assistant engine")
    subs = p.add_subparsers(dest="cmd", required=True)

    pp = subs.add_parser("providers")
    pp.add_argument("provider_sub", choices=["list", "init", "test", "models"], nargs="?", default="list")
    pp.add_argument("provider_id", nargs="?")
    pp.set_defaults(fn=_providers)

    cp = subs.add_parser("chat")
    csub = cp.add_subparsers(dest="chat_sub", required=True)
    cnew = csub.add_parser("new")
    cnew.add_argument("--title")
    cnew.add_argument("--provider")
    csub.add_parser("list")
    cget = csub.add_parser("get")
    cget.add_argument("conversation_id")
    cren = csub.add_parser("rename")
    cren.add_argument("conversation_id")
    cren.add_argument("--title", required=True)
    cd = csub.add_parser("delete")
    cd.add_argument("conversation_id")
    ccl = csub.add_parser("clear")
    ccl.add_argument("conversation_id")
    cctx = csub.add_parser("context")
    cctx.add_argument("conversation_id")
    cctx.add_argument("--turns", type=int, default=8)
    cs = csub.add_parser("send")
    cs.add_argument("conversation_id")
    cs.add_argument("--text", required=True)
    cs.add_argument("--image")
    cs.add_argument("--file")
    cs.add_argument("--system")
    cs.add_argument("--no-stream", action="store_true")
    cs.add_argument("--stop-after", type=int, default=0)
    cr = csub.add_parser("retry")
    cr.add_argument("conversation_id")
    crg = csub.add_parser("regenerate")
    crg.add_argument("conversation_id")
    ce = csub.add_parser("edit")
    ce.add_argument("conversation_id")
    ce.add_argument("message_index", type=int)
    ce.add_argument("--text", required=True)
    cp.set_defaults(fn=_chat)

    rt = subs.add_parser("route")
    rt.add_argument("text")
    rt.add_argument("--attach")
    rt.set_defaults(fn=_route)

    vs = subs.add_parser("vision")
    vs.add_argument("image")
    vs.add_argument("--question")
    vs.add_argument("--ocr", action="store_true")
    vs.add_argument("--identify", action="store_true")
    vs.add_argument("--kind", default="auto")
    vs.set_defaults(fn=_vision)

    ig = subs.add_parser("imagegen")
    ig.add_argument("prompt")
    ig.add_argument("--out", required=True)
    ig.add_argument("--size", default="1024x1024")
    ig.set_defaults(fn=_imagegen)

    ws = subs.add_parser("web")
    ws.add_argument("query")
    ws.add_argument("--limit", type=int, default=5)
    ws.set_defaults(fn=_web)

    fs = subs.add_parser("files")
    fs.add_argument("file_sub", choices=["extract"])
    fs.add_argument("path")
    fs.set_defaults(fn=_files)

    ds = subs.add_parser("data")
    dsub = ds.add_subparsers(dest="data_sub", required=True)
    da = dsub.add_parser("analyze")
    da.add_argument("path")
    da.add_argument("--method", default="auto")
    da.add_argument("--question")
    ds.set_defaults(fn=_data)

    ss = subs.add_parser("session")
    ss.add_argument("conversation_id")
    ss.add_argument("text")
    ss.add_argument("--image")
    ss.add_argument("--file")
    ss.set_defaults(fn=_session)

    bd = subs.add_parser("build")
    bd.add_argument("idea")
    bd.add_argument("--modify")
    bd.add_argument("--release", action="store_true")
    bd.add_argument("--staging")
    bd.set_defaults(fn=_build)

    pl = subs.add_parser("policy")
    pl.add_argument("text")
    pl.set_defaults(fn=_policy)

    args = p.parse_args(argv)
    try:
        args.fn(args)
    except KeyboardInterrupt:
        out({"ok": False, "error": "interrupted"})
    except Exception as e:  # noqa: BLE001
        out({"ok": False, "error": sanitize_secrets(str(e))[:2000]})
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())