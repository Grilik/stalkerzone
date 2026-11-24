#!/usr/bin/env python3
"""Inbox идей: add / list / take / reject / defer.

Идея → запись в реестр → решение штаба → задача/требование или отклонение.
Без записи здесь идея не считается зафиксированной.

Команды:
    python tools/registry/idea.py add "текст" [--source "чат 24.11"]
    python tools/registry/idea.py list
    python tools/registry/idea.py take И-01 Э2-24 [--title "Новая задача"]
    python tools/registry/idea.py reject И-01 "причина"
    python tools/registry/idea.py defer И-01 "причина"
"""
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

REG = Path(__file__).resolve().parents[2] / "docs" / "project-tasks.json"


def load():
    return json.loads(REG.read_text(encoding="utf-8"))


def save(data):
    data["meta"]["updated"] = datetime.now().astimezone().isoformat(timespec="seconds")
    REG.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def next_id(data):
    nums = [int(e["id"][2:]) for e in data["entities"] if e.get("type") == "idea" and e["id"].startswith("И-")]
    return f"И-{(max(nums) + 1) if nums else 1:02d}"


def cmd_add(args):
    data = load()
    iid = next_id(data)
    now = datetime.now().astimezone().isoformat(timespec="seconds")
    ent = {
        "id": iid,
        "type": "idea",
        "title": args.text,
        "status": "NEW",
        "source": args.source or "не указан",
        "converted_to": None,
        "notes": "",
        "created": now,
        "updated": now,
    }
    data["entities"].append(ent)
    save(data)
    print(f"создана идея {iid}: {args.text[:60]}")


def cmd_list(args):
    data = load()
    ideas = [e for e in data["entities"] if e.get("type") == "idea"]
    if not ideas:
        print("идей нет"); return
    for status in ("NEW", "DEFERRED", "TAKEN", "REJECTED"):
        group = [e for e in ideas if e.get("status") == status]
        if not group:
            continue
        print(f"\n{status} ({len(group)}):")
        for e in group:
            extra = ""
            if status == "TAKEN" and e.get("converted_to"):
                extra = f" → {e['converted_to']}"
            elif status in ("REJECTED", "DEFERRED") and e.get("notes"):
                extra = f" | {e['notes'][:50]}"
            print(f"  {e['id']}: {e.get('title', '')[:70]}{extra}")


def find(data, iid):
    for e in data["entities"]:
        if e["id"] == iid:
            return e
    return None


def cmd_take(args):
    data = load()
    idea = find(data, args.idea_id)
    if not idea:
        print(f"идея {args.idea_id} не найдена"); sys.exit(1)
    if idea.get("type") != "idea":
        print(f"{args.idea_id} не идея"); sys.exit(1)
    target = find(data, args.target_id)
    if not target:
        print(f"задача {args.target_id} не найдена"); sys.exit(1)
    idea["status"] = "TAKEN"
    idea["converted_to"] = args.target_id
    idea["updated"] = datetime.now().astimezone().isoformat(timespec="seconds")
    note = f"24.11 конвертировано из идеи {args.idea_id}"
    target["notes"] = (target.get("notes") or "") + ("\n" if target.get("notes") else "") + note
    save(data)
    print(f"{args.idea_id} → {args.target_id}")


def cmd_reject(args):
    data = load()
    idea = find(data, args.idea_id)
    if not idea or idea.get("type") != "idea":
        print(f"идея не найдена"); sys.exit(1)
    idea["status"] = "REJECTED"
    idea["notes"] = args.reason
    idea["updated"] = datetime.now().astimezone().isoformat(timespec="seconds")
    save(data)
    print(f"{args.idea_id} отклонена: {args.reason[:60]}")


def cmd_defer(args):
    data = load()
    idea = find(data, args.idea_id)
    if not idea or idea.get("type") != "idea":
        print(f"идея не найдена"); sys.exit(1)
    idea["status"] = "DEFERRED"
    idea["notes"] = args.reason
    idea["updated"] = datetime.now().astimezone().isoformat(timespec="seconds")
    save(data)
    print(f"{args.idea_id} отложена: {args.reason[:60]}")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    sp = p.add_subparsers(dest="cmd", required=True)
    a = sp.add_parser("add"); a.add_argument("text"); a.add_argument("--source", default="")
    sp.add_parser("list")
    t = sp.add_parser("take"); t.add_argument("idea_id"); t.add_argument("target_id")
    r = sp.add_parser("reject"); r.add_argument("idea_id"); r.add_argument("reason")
    d = sp.add_parser("defer"); d.add_argument("idea_id"); d.add_argument("reason")
    args = p.parse_args()
    {"add": cmd_add, "list": cmd_list, "take": cmd_take, "reject": cmd_reject, "defer": cmd_defer}[args.cmd](args)