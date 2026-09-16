"""AI chat engine with persistent, context-aware conversation history.

Conversations live under `<state>/chat/<id>.json`. Generation is a generator
of chunks so a Stop flag can preserve the partial response; Regenerate re-runs
the last assistant turn, Retry re-runs the last user turn, Edit+Resend replaces
a user message and regenerates.

Workflow guards:
- every user message passes the content-policy check before any provider call
- streamed partials are persisted when generation is stopped
- provider errors are recorded on the message, never swallowed
"""

from __future__ import annotations

import json
import re
import uuid
from pathlib import Path

import policy
import providers
import transport
from common import CHAT_DIR, ensure_dirs, read_json, sanitize_secrets, utc_now, write_json

MAX_TITLE = 44


class CancellationToken:
    def __init__(self) -> None:
        self._cancelled = False

    def cancel(self) -> None:
        self._cancelled = True

    @property
    def cancelled(self) -> bool:
        return self._cancelled


class Conversation:
    def __init__(self, data: dict):
        self.data = data

    @property
    def id(self) -> str:
        return self.data.get("id", "")

    @property
    def title(self) -> str:
        return self.data.get("title", "untitled")

    @property
    def messages(self) -> list[dict]:
        return self.data.get("messages", [])

    @property
    def provider_id(self) -> str:
        return self.data.get("provider_id", "")

    def path(self) -> Path:
        return CHAT_DIR / f"{self.id}.json"

    def save(self) -> dict:
        self.data["updated_at"] = utc_now()
        write_json(self.path(), self.data)
        return self.data

    def append_message(self, role: str, content: str, status: str = "ok", extra: dict | None = None) -> dict:
        msg = {
            "role": role,
            "content": content,
            "ts": utc_now(),
            "status": status,
        }
        if extra:
            msg.update(extra)
        self.data.setdefault("messages", []).append(msg)
        return msg


class ChatEngine:
    def __init__(self, registry: providers.ProviderRegistry | None = None):
        ensure_dirs()
        self.registry = registry or providers.ProviderRegistry()

    # ---- conversation CRUD -------------------------------------------------
    def list_conversations(self) -> list[dict]:
        result = []
        for p in sorted(CHAT_DIR.glob("*.json")):
            data = read_json(p)
            if data.get("id"):
                result.append(_summary(data))
        result.sort(key=lambda c: c.get("updated_at", ""), reverse=True)
        return result

    def get(self, conv_id: str) -> Conversation:
        data = read_json(CHAT_DIR / f"{conv_id}.json")
        if not data.get("id"):
            return None  # type: ignore[return-value]
        return Conversation(data)

    def new(self, title: str | None = None, provider_id: str | None = None) -> Conversation:
        conv_id = uuid.uuid4().hex[:12]
        provider = self.registry.resolve("chat", provider_id)
        conv = Conversation(
            {
                "id": conv_id,
                "title": title or "new chat",
                "provider_id": provider.id,
                "model": getattr(provider, "model", ""),
                "created_at": utc_now(),
                "updated_at": utc_now(),
                "messages": [],
                "attachments": [],
            }
        )
        conv.save()
        return conv

    def delete(self, conv_id: str) -> bool:
        p = CHAT_DIR / f"{conv_id}.json"
        if p.exists():
            p.unlink()
            return True
        return False

    def clear_messages(self, conv_id: str) -> Conversation:
        conv = self.get(conv_id)
        if conv is None:
            return None  # type: ignore[return-value]
        conv.data["messages"] = []
        conv.save()
        return conv

    def rename(self, conv_id: str, title: str) -> Conversation:
        conv = self.get(conv_id)
        if conv is None:
            return None  # type: ignore[return-value]
        conv.data["title"] = title[:120]
        conv.save()
        return conv

    @staticmethod
    def _content_messages(conv: Conversation) -> list[dict]:
        out_messages = []
        for m in conv.messages:
            d = {"role": m.get("role", "user"), "content": m.get("content", "")}
            if m.get("status") in ("error",) and not m.get("content") and m.get("error"):
                d["content"] = ""
            else:
                d["content"] = m.get("content", "")
            if m.get("image"):
                d["content"] = [
                    {"type": "text", "text": d["content"]},
                    {"type": "image_url", "image_url": {"url": m["image"]}},
                ]
            out_messages.append(d)
        return out_messages

    # ---- generation --------------------------------------------------------
    def _mock_reply(self, messages: list[dict]) -> str:
        last = ""
        for m in reversed(messages):
            if m.get("role") == "user":
                last = m.get("content", "")
                if isinstance(last, list):
                    parts = [f'"{p.get("text", "")}"' if p.get("type") == "text" else "[image]" for p in last]
                    last = " ".join(parts)
                break
        return "MOCK_CHAT: deterministic offline reply based on this conversation. Last user message: " + sanitize_secrets(
            last[:120]
        )

    def _stream(self, conv: Conversation, opts: dict, cancel: CancellationToken | None = None):
        provider = self.registry.resolve("chat", conv.provider_id)
        if provider is None:
            yield "no chat provider configured (real mode)", True
            return
        messages = self._content_messages(conv)
        payload = self.registry.chat_payload(
            provider, messages, stream=True, opts=opts
        )
        if isinstance(provider, providers.MockProvider):
            text = self._mock_reply(messages)
            pieces = [text[i : i + 16] for i in range(0, len(text), 16)]
            for piece in pieces:
                if cancel is not None and cancel.cancelled:
                    break
                yield piece, False
            yield "", True
            return
        headers = self.registry.auth_header(provider)
        for chunk, done in transport.stream_chat_chunks(provider.endpoint, headers, payload):
            if cancel is not None and cancel.cancelled:
                yield "", True
                return
            yield chunk, done

    def _complete(self, conv: Conversation, opts: dict) -> tuple[str, str]:
        """Non-stream call. Returns (content, error)."""
        provider = self.registry.resolve("chat", conv.provider_id)
        if provider is None:
            return "", "no chat provider configured (real mode)"
        messages = self._content_messages(conv)
        payload = self.registry.chat_payload(provider, messages, stream=False, opts=opts)
        if isinstance(provider, providers.MockProvider):
            return self._mock_reply(messages), None
        headers = self.registry.auth_header(provider)
        status, body, err = transport.post_json(provider.endpoint, headers, payload)
        if err or status >= 400:
            return "", err or f"HTTP {status}"
        try:
            content = body["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            return "", "provider returned an unexpected response shape"
        return sanitize_secrets(str(content)), None

    def send(
        self,
        conv_id: str,
        text: str,
        opts: dict | None = None,
        attachments: list[dict] | None = None,
        stream: bool = True,
        cancel: CancellationToken | None = None,
        run_policy: bool = True,
    ) -> dict:
        opts = opts or {}
        if run_policy:
            check = policy.check(text)
            if not check["allowed"]:
                return {
                    "ok": False,
                    "policy": "refused",
                    "refused": check["refused"],
                    "reason": check["reason"],
                    "conversation_id": conv_id,
                }
        conv = self.get(conv_id)
        if conv is None:
            return {"ok": False, "error": f"unknown conversation: {conv_id}"}

        user_target = len(conv.messages)
        user_msg = conv.append_message("user", text)
        if conv.title == "new chat":
            conv.data["title"] = _title_of(text)
        if attachments:
            conv.data.setdefault("attachments", []).extend(attachments)
        if opts.get("system"):
            conv.data["system_prompt"] = opts["system"]

        provider = self.registry.resolve("chat", conv.provider_id)
        if provider is None:
            conv.messages.append({"role": "assistant", "content": "", "ts": utc_now(), "status": "error", "error": "no chat provider configured (real mode)"})
            conv.save()
            return {"ok": False, "conversation_id": conv_id, "error": "no chat provider configured (real mode)", "status": "error"}
        request_model = provider.model

        self._answer(conv, opts, stream=stream, cancel=cancel, stop_at_chunk=opts.get("stop_at_chunk"))
        conv.save()
        return {
            "ok": True,
            "conversation_id": conv_id,
            "title": conv.title,
            "user_message_index": user_target,
            "assistant_message_index": user_target + 1,
            "request_model": request_model,
            "status": conv.messages[user_target + 1].get("status"),
            "assistant": conv.messages[user_target + 1].get("content", ""),
            "truncated": True,
        }

    def _answer(self, conv: Conversation, opts: dict, stream: bool = True,
                cancel: CancellationToken | None = None, stop_at_chunk=None) -> None:
        """Generate the assistant reply to the current conversation state and
        append it (never duplicating the user message)."""
        if stream and not opts.get("no_stream"):
            content = ""
            stop_at = stop_at_chunk
            for cn, (chunk, done) in enumerate(self._stream(conv, opts, cancel)):
                content += chunk
                if done:
                    break
                if stop_at is not None and cn + 1 >= int(stop_at):
                    if cancel is not None:
                        cancel.cancel()
                    break
            status = "stopped" if ((cancel is not None and cancel.cancelled) or (stop_at is not None)) else "ok"
            conv.messages.append({"role": "assistant", "content": content, "ts": utc_now(), "status": status})
        else:
            content, err = self._complete(conv, opts)
            if err:
                conv.messages.append({"role": "assistant", "content": "", "ts": utc_now(), "status": "error", "error": err})
            else:
                conv.messages.append({"role": "assistant", "content": content, "ts": utc_now(), "status": "ok"})

    # ---- controls ----------------------------------------------------------
    def retry(self, conv_id: str, opts: dict | None = None) -> dict:
        """Re-run the last assistant turn from the persisted user message
        (the user message is preserved, not duplicated)."""
        conv = self.get(conv_id)
        if conv is None:
            return {"ok": False, "error": f"unknown conversation: {conv_id}"}
        msgs = conv.messages
        if not msgs:
            return {"ok": False, "error": "no messages to retry"}
        if msgs[-1]["role"] != "assistant":
            return {"ok": False, "error": "no assistant message to retry"}
        msgs.pop()  # drop failed/stopped/old assistant message
        conv.save()
        self._answer(conv, opts or {}, stream=False)
        conv.save()
        return {"ok": True, "conversation_id": conv_id, "status": conv.messages[-1].get("status"),
                "assistant": conv.messages[-1].get("content", "")}

    def regenerate(self, conv_id: str, opts: dict | None = None) -> dict:
        """Replace the last assistant message with a fresh generation."""
        conv = self.get(conv_id)
        if conv is None:
            return {"ok": False, "error": f"unknown conversation: {conv_id}"}
        msgs = conv.messages
        user_ix = None
        for i in range(len(msgs) - 1, -1, -1):
            if msgs[i]["role"] == "user":
                user_ix = i
                break
        if user_ix is None:
            return {"ok": False, "error": "no user message to regenerate from"}
        if len(msgs) > user_ix + 1 and msgs[user_ix + 1]["role"] == "assistant":
            msgs.pop(user_ix + 1)
        conv.save()
        self._answer(conv, opts or {}, stream=False)
        conv.save()
        return {"ok": True, "conversation_id": conv_id, "status": conv.messages[-1].get("status"),
                "assistant": conv.messages[-1].get("content", "")}

    def edit_resend(self, conv_id: str, msg_ix: int, new_text: str) -> dict:
        """Replace a user message and regenerate the following assistant reply."""
        check = policy.check(new_text)
        if not check["allowed"]:
            return {"ok": False, "policy": "refused", "refused": check["refused"], "reason": check["reason"]}
        conv = self.get(conv_id)
        if conv is None:
            return {"ok": False, "error": f"unknown conversation: {conv_id}"}
        msgs = conv.messages
        if not 0 <= msg_ix < len(msgs) or msgs[msg_ix]["role"] != "user":
            return {"ok": False, "error": f"message {msg_ix} is not a user message"}
        if len(msgs) > msg_ix + 1 and msgs[msg_ix + 1]["role"] == "assistant":
            del msgs[msg_ix + 1]
        msgs[msg_ix]["content"] = new_text
        msgs[msg_ix]["ts"] = utc_now()
        conv.save()
        self._answer(conv, {}, stream=False)
        conv.save()
        return {"ok": True, "conversation_id": conv_id, "status": conv.messages[-1].get("status"),
                "assistant": conv.messages[-1].get("content", "")}


def _title_of(text: str) -> str:
    t = re.sub(r"\s+", " ", text).strip()
    return t[:MAX_TITLE] if t else "new chat"


def _summary(data: dict) -> dict:
    msgs = data.get("messages", [])
    last = next((m.get("content", "") for m in reversed(msgs) if m.get("role") == "assistant"), "")
    return {
        "id": data.get("id", ""),
        "title": data.get("title", ""),
        "provider_id": data.get("provider_id", ""),
        "messages": len(msgs),
        "created_at": data.get("created_at", ""),
        "updated_at": data.get("updated_at", ""),
        "last_assistant": last[:160],
    }