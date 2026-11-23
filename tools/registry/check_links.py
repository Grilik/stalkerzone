#!/usr/bin/env python3
"""Симметрия связей реестра: requirement.linked_tasks <-> task.linked_requirements.

Валидатор проверяет висячие ссылки, но не согласованность направлений.
Асимметрия найдена вручную 24.11: 7 задач, из них ПТ-1/ПТ-2 жили с 18.11.
Прямая ссылка (у требования) считается авторитетной.
"""
import json
import sys
from collections import defaultdict
from pathlib import Path

REG = Path(__file__).resolve().parents[2] / "docs" / "project-tasks.json"


def main():
    data = json.loads(REG.read_text(encoding="utf-8"))
    ents = {e["id"]: e for e in data["entities"]}
    want = defaultdict(set)
    dangling = 0
    for e in data["entities"]:
        if e.get("type") != "requirement":
            continue
        for t in e.get("linked_tasks") or []:
            if t in ents:
                want[t].add(e["id"])
            else:
                print("ВИСЯЧАЯ ссылка: %s -> %s" % (e["id"], t))
                dangling += 1
    bad = 0
    tasks = 0
    for e in data["entities"]:
        if e.get("type") == "requirement":
            continue
        tasks += 1
        have = set(e.get("linked_requirements") or [])
        exp = want.get(e["id"], set())
        if have != exp:
            bad += 1
            print("АСИММЕТРИЯ %s: +%s  -%s" % (
                e["id"],
                ",".join(sorted(exp - have)) or "-",
                ",".join(sorted(have - exp)) or "-"))
    print("Задач проверено: %d" % tasks)
    if dangling or bad:
        print("ПРОВАЛ: висячих %d, асимметрий %d" % (dangling, bad))
        return 1
    print("Связи симметричны: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())