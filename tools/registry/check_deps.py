#!/usr/bin/env python3
"""Целостность depends_on: висячие, самозависимости, циклы. Плюс очередь готовых задач."""
import json
import sys
from pathlib import Path

REG = Path(__file__).resolve().parents[2] / "docs" / "project-tasks.json"
CLOSED = {"TESTS_PASSED", "GAME_VERIFIED", "ACCEPTED"}
WAIVED = {"REJECTED"}


def main():
    data = json.loads(REG.read_text(encoding="utf-8"))
    ents = {e["id"]: e for e in data["entities"]}
    deps = {e["id"]: (e.get("depends_on") or []) for e in data["entities"]}
    bad = 0
    for k, ds in deps.items():
        for t in ds:
            if t not in ents:
                print("ВИСЯЧАЯ зависимость: %s -> %s" % (k, t)); bad += 1
            elif t == k:
                print("САМОЗАВИСИМОСТЬ:", k); bad += 1

    color = {}

    def dfs(n, stack):
        nonlocal bad
        c = color.get(n, 0)
        if c == 1:
            print("ЦИКЛ:", " -> ".join(stack + [n])); bad += 1; return
        if c == 2:
            return
        color[n] = 1
        for m in deps.get(n, []):
            if m in ents:
                dfs(m, stack + [n])
        color[n] = 2

    for n in list(deps):
        dfs(n, [])

    ready, blocked = [], []
    for e in data["entities"]:
        if e.get("type") == "requirement":
            continue
        if e.get("status") not in ("PLANNED", "IN_PROGRESS"):
            continue
        unmet = [t for t in deps[e["id"]]
                 if t in ents and ents[t]["status"] not in CLOSED | WAIVED]
        if unmet:
            blocked.append((e["id"], unmet))
        else:
            ready.append(e["id"])

    print("Зависимостей объявлено: %d" % sum(len(v) for v in deps.values()))
    print("Заблокировано зависимостями: %d" % len(blocked))
    for k, u in blocked:
        print("   %-8s ждёт %s" % (k, ", ".join("%s(%s)" % (t, ents[t]["status"]) for t in u)))
    print("Готовы к работе (PLANNED/IN_PROGRESS, зависимости закрыты): %d" % len(ready))
    if bad:
        print("ПРОВАЛ: ошибок %d" % bad)
        return 1
    print("Зависимости корректны: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())