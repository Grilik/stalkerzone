"""Следующая карточка для конвейера v2 (П-13) по таблице docs/tasks/README.md. Печатает номер (NNN) или пусто.
Берёт только карточки с паспортом «**Конвейер:** да» (tools/conveyor/card.py), не готовые, с выполненными
зависимостями и без ветки task/NNN-* (уже в работе или ждёт слияния). Одна линия — по порядку таблицы."""
import glob
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import card  # noqa: E402

rows = []
for line in open("docs/tasks/README.md", encoding="utf-8"):
    c = [x.strip() for x in line.strip().strip("|").split("|")]
    if len(c) >= 5 and re.fullmatch(r"\d{3}", c[0]):
        rows.append(dict(n=c[0], deps=c[3], status=c[4]))
done = {r["n"] for r in rows if r["status"].startswith("готово")}
branches = subprocess.run(["git", "ls-remote", "--heads", "origin", "task/*"], capture_output=True, text=True).stdout
started = set(re.findall(r"refs/heads/task/(\d{3})", branches))


def deps(s):
    if s in ("—", "-", ""):
        return set()
    out = set()
    for a, b in re.findall(r"(\d{3})(?:\s*[–-]\s*(\d{3}))?", s):
        out |= {f"{i:03d}" for i in range(int(a), int(b or a) + 1)}
    return out


for r in rows:
    if r["n"] in done or r["n"] in started or r["status"].startswith(("не вышло", "отменена")):
        continue
    f = glob.glob(f"docs/tasks/{r['n']}-*.md")
    if not f or card.passport(f[0])[1] or not deps(r["deps"]) <= done:
        continue
    print(r["n"])
    break
