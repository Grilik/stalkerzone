"""Статус «готово (конвейер)» карточке NNN в таблице docs/tasks/README.md — после слияния её PR.

Ставит конвейер (job `mark` в .github/workflows/conveyor.yml), а не исполнитель: если каждая ветка карточки
правит свою строку таблицы, соседние карточки конфликтуют при слиянии (PR #109, #115).

python3 -I tools/conveyor/mark_done.py NNN [путь к таблице]   — 0: поставлено или уже было; 1: строки нет.
"""
import re
import sys

DONE = "готово (конвейер)"


def mark(text, n):
    """(новый текст, найдена ли строка)."""
    out, found = [], False
    for line in text.split("\n"):
        c = line.split("|")
        # | NNN | название | исполнитель | зависимости | статус |  → c[0] == "" и c[-1] == ""
        if len(c) >= 7 and c[1].strip() == n and re.fullmatch(r"\d{3}", n):
            found = True
            if not c[5].strip().startswith("готово"):
                c[5] = " " + DONE + " "
                line = "|".join(c)
        out.append(line)
    return "\n".join(out), found


def main(argv):
    n = argv[1]
    path = argv[2] if len(argv) > 2 else "docs/tasks/README.md"
    text = open(path, encoding="utf-8").read()
    new, found = mark(text, n)
    if not found:
        print("нет строки карточки %s в %s" % (n, path), file=sys.stderr)
        return 1
    if new != text:
        open(path, "w", encoding="utf-8").write(new)
        print("карточка %s: %s" % (n, DONE))
    else:
        print("карточка %s уже готова" % n)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
