#!/usr/bin/env python3
"""START-HERE.md из реестра. Руками не править.

    python tools/registry/gen.py          записать
    python tools/registry/gen.py --check  сравнить с диском, exit 1 при расхождении

Ссылка на состояние — meta.updated реестра, не дата генерации: иначе --check
падал бы каждый день без изменения данных.
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REG = ROOT / "docs" / "project-tasks.json"
OUT = ROOT / "docs" / "handoff" / "START-HERE.md"
CLOSED = {"TESTS_PASSED", "GAME_VERIFIED", "ACCEPTED"}
WAIVED = {"REJECTED"}
OPEN = {"PLANNED", "IN_PROGRESS"}


def stage(eid):
    m = re.match(r"^Э(\d+)-", eid)
    return int(m.group(1)) if m else 99


def render():
    data = json.loads(REG.read_text(encoding="utf-8"))
    ents = data["entities"]
    by = {e["id"]: e for e in ents}
    tasks = [e for e in ents if e.get("type") == "task"]
    reqs = [e for e in ents if e.get("type") == "requirement"]
    sc = defaultdict(int)
    for e in ents:
        sc[e.get("status", "?")] += 1

    st = defaultdict(lambda: [0, 0])
    for t in tasks:
        s = stage(t["id"])
        if s == 99 or t.get("status") in WAIVED:
            continue
        st[s][1] += 1
        if t.get("status") in CLOSED:
            st[s][0] += 1

    deps = {e["id"]: (e.get("depends_on") or []) for e in ents}
    unmet = {}
    for t in tasks:
        if t.get("status") not in OPEN:
            continue
        u = [d for d in deps.get(t["id"], [])
             if d in by and by[d].get("status") not in CLOSED | WAIVED]
        if u:
            unmet[t["id"]] = u

    rev = defaultdict(set)
    for k, ds in deps.items():
        for d in ds:
            rev[d].add(k)

    def descend(n):
        seen, stack = set(), list(rev.get(n, ()))
        while stack:
            x = stack.pop()
            if x in seen:
                continue
            seen.add(x)
            stack.extend(rev.get(x, ()))
        return seen

    unlock = {}
    for t in tasks:
        if t.get("status") in OPEN and t["id"] not in unmet:
            unlock[t["id"]] = sum(1 for x in descend(t["id"])
                                  if x in by and by[x].get("status") in OPEN)
    order = sorted(unlock, key=lambda k: (-unlock[k], stage(k), k))
    deferred = [(k, d) for k, u in unmet.items() for d in u
                if by[d].get("status") == "DEFERRED"]
    acards = [e for e in tasks if re.match(r"^А\d+$", e["id"])
              and e.get("status") in OPEN]
    ideas = [e for e in ents if e.get("type") == "idea"]
    ideas_new = [e for e in ideas if e.get("status") == "NEW"]
    ideas_def = [e for e in ideas if e.get("status") == "DEFERRED"]

    L = []
    a = L.append
    a("# ТОЧКА ВХОДА — читать первым")
    a("")
    a("Сгенерировано из docs/project-tasks.json. Руками не править:")
    a("измените реестр и запустите python tools/registry/gen.py")
    a("")
    a("Состояние реестра: " + str(data.get("meta", {}).get("updated", "?")))
    a("")
    a("Вставлять в новый чат вместе с DECISIONS.md и RULES.md.")
    a("При вопросах об ID — ещё docs/01-IDS.md.")
    a("")
    a("## Где правда")
    a("")
    a("| Что | Файл | Править руками |")
    a("|---|---|---|")
    a("| Статусы требований и задач | docs/project-tasks.json | да, единственный источник |")
    a("| Идентификаторы, версии зависимостей | docs/01-IDS.md | да |")
    a("| Решения | docs/handoff/DECISIONS.md | только дописывать |")
    a("| Правила и ловушки | docs/handoff/RULES.md | да |")
    a("| Эта точка входа | docs/handoff/START-HERE.md | нет, генерируется |")
    a("| Этапы и цели | docs/roadmap.md | пока да |")
    a("")
    a("При расхождении реестра и roadmap верен реестр; расхождение — дефект.")
    a("")
    a("## Состояние")
    a("")
    a("Сущностей %d: требований %d, задач %d." % (len(ents), len(reqs), len(tasks)))
    a("")
    a(" · ".join("%s %d" % (k, sc[k]) for k in sorted(sc, key=lambda x: -sc[x])))
    a("")
    a("| Этап | Закрыто | Всего |")
    a("|---|---|---|")
    for s in sorted(st):
        a("| Э%d | %d | %d |" % (s, st[s][0], st[s][1]))
    a("")
    a("Снятые (REJECTED) в счёт этапа не входят.")
    a("")
    a("## Что брать в работу")
    a("")
    a("Отсортировано по числу разблокируемых задач, затем по этапу.")
    a("Полный список готовых: python tools/registry/check_deps.py")
    a("")
    a("| Задача | Открывает | Название |")
    a("|---|---|---|")
    for k in order[:12]:
        ttl = by[k].get("title", "")[:70].replace("|", "/")
        a("| %s | %d | %s |" % (k, unlock[k], ttl))
    a("")
    a("Готовы всего: %d. Заблокировано зависимостями: %d." % (len(order), len(unmet)))
    a("")
    if unmet:
        a("### Ждут")
        a("")
        for k in sorted(unmet):
            a("- %s ← %s" % (k, ", ".join("%s(%s)" % (d, by[d].get("status")) for d in unmet[k])))
        a("")
    if deferred:
        a("### Ждут отложенного — нужно решение штаба")
        a("")
        for k, d in deferred:
            a("- %s ждёт %s (DEFERRED)" % (k, d))
        a("")
    if acards:
        a("## Открытые карточки штаба")
        a("")
        for e in sorted(acards, key=lambda x: int(x["id"][1:])):
            a("- %s [%s] %s" % (e["id"], e["status"], e.get("title", "")))
        a("")
    a("## Чем проверять")
    a("")
    a("    python tools/registry/validate.py")
    a("    python tools/registry/check_links.py")
    a("    python tools/registry/check_deps.py")
    a("    python tools/registry/gen.py --check")
    a("    gradlew.bat build")
    a("")
    a("Обязательные ворота PR: build/build и build/server.")
    a("dashboard/page — витрина, не ворота.")
    a("")
    if ideas_new or ideas_def:
        a("## Inbox идей")
        a("")
        a(f"NEW: {len(ideas_new)}, DEFERRED: {len(ideas_def)}.")
        a("Заведение: python tools/registry/idea.py add \"текст\".")
        a("")
        for e in ideas_new:
            a(f"- {e['id']} [NEW] {e.get('title', '')[:80]}")
        for e in ideas_def:
            note = e.get("notes", "")[:60]
            a(f"- {e['id']} [DEFERRED] {e.get('title', '')[:70]} | {note}")
        a("")
    a("## Правила и решения")
    a("")
    a("Действующие решения — docs/handoff/DECISIONS.md (только дописывать).")
    a("Правила работы и ловушки проекта — docs/handoff/RULES.md.")
    a("Решение без записи в журнале не считается принятым.")
    a("")
    return "\n".join(L)


def main():
    text = render()
    if "--check" in sys.argv:
        old = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if old.replace("\r\n", "\n") != text:
            print("ДРЕЙФ: %s не совпадает с реестром" % OUT.name)
            print("Запустите: python tools/registry/gen.py")
            return 1
        print("Точка входа актуальна: OK")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print("записано: %s (%d байт)" % (OUT, len(text.encode("utf-8"))))
    return 0


if __name__ == "__main__":
    sys.exit(main())