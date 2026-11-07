"""Паспорт карточки конвейера v2 (П-13). Строки в начале карточки docs/tasks/NNN-*.md:

    **Конвейер:** да
    **Тест:** `docs/tasks/tests/XxxTest.java`          — падающий тест; конвейер кладёт его в src/test/java/
    **Файлы:** `src/main/.../A.java`, `src/main/.../B.java`   — что исполнитель может читать и менять
    **Чек-лист:** нет                                    — или `docs/checklist/NNN-имя.md`

python3 -I tools/conveyor/card.py <карточка> --check     — 0, если паспорт верный (иначе печатает, что не так)
python3 -I tools/conveyor/card.py <карточка> --shell     — переменные CARD_TEST_SRC, CARD_TEST, CARD_FILES, CARD_CHECKLIST
python3 -I tools/conveyor/card.py <карточка> --stray "<изменённые пути>"  — пути вне разрешённых (по одному в строке)
"""
import os
import re
import shlex
import sys


def field(text, name):
    m = re.search(r"^\*\*" + name + r":\*\*\s*(.+)$", text, re.M)
    return m.group(1).strip() if m else ""


def paths(value):
    return re.findall(r"`([^`]+)`", value)


def passport(path):
    """(паспорт, ошибки)."""
    text = open(path, encoding="utf-8").read()
    errs = []
    if not field(text, "Конвейер").lower().startswith("да"):
        errs.append("нет строки «**Конвейер:** да»")
    tests = paths(field(text, "Тест"))
    if len(tests) != 1 or not re.fullmatch(r"docs/tasks/tests/[A-Z]\w*Test\.java", tests[0] if tests else ""):
        errs.append("«**Тест:**» — ровно один файл `docs/tasks/tests/<Имя>Test.java`")
    elif not os.path.exists(tests[0]):
        errs.append("нет файла теста " + tests[0])
    files = paths(field(text, "Файлы"))
    if not files:
        errs.append("«**Файлы:**» — список разрешённых файлов в `…`")
    for f in files:
        if f.startswith((".github/", "docs/claude/", "tools/xray/")) or "*" in f:
            errs.append("файл вне зоны конвейера: " + f)
    check = paths(field(text, "Чек-лист"))
    if check and not re.fullmatch(r"docs/checklist/\d{3}-[\w-]+\.md", check[0]):
        errs.append("«**Чек-лист:**» — `docs/checklist/NNN-имя.md` или «нет»")
    test = tests[0] if tests else ""
    return dict(test_src=test, test=os.path.basename(test)[:-5] if test else "", files=files,
                checklist=check[0] if check else ""), errs


def allowed(p):
    return set(p["files"]) | {"src/test/java/%s.java" % p["test"]} | ({p["checklist"]} if p["checklist"] else set())


def main(argv):
    p, errs = passport(argv[1])
    mode = argv[2] if len(argv) > 2 else "--check"
    if mode == "--stray":
        ok = allowed(p)
        for line in (argv[3] if len(argv) > 3 else "").splitlines():
            path = line.strip().split(" -> ")[-1].strip('"')
            if path and path not in ok:
                print(path)
        return 0
    if errs:
        for e in errs:
            print("карточка %s: %s" % (argv[1], e), file=sys.stderr)
        return 1
    if mode == "--shell":
        print("CARD_TEST_SRC=%s" % shlex.quote(p["test_src"]))
        print("CARD_TEST=%s" % shlex.quote(p["test"]))
        print("CARD_FILES=%s" % shlex.quote(" ".join(p["files"])))
        print("CARD_CHECKLIST=%s" % shlex.quote(p["checklist"]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
