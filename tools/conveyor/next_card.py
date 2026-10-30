"""Выбор следующей карточки для конвейера по таблице docs/tasks/README.md.
Печатает номер (NNN) или пусто. Пропускает карточки не для Cline, с невыполненными зависимостями и уже начатые (есть ветка task/NNN-*)."""
import re, subprocess, sys
rows = []
for line in open("docs/tasks/README.md", encoding="utf-8"):
    c = [x.strip() for x in line.strip().strip("|").split("|")]
    if len(c) >= 5 and re.fullmatch(r"\d{3}", c[0]):
        rows.append(dict(n=c[0], who=c[2], deps=c[3], status=c[4]))
done = {r["n"] for r in rows if r["status"].startswith("готово")}
branches = subprocess.run(["git", "ls-remote", "--heads", "origin", "task/*"], capture_output=True, text=True).stdout
started = set(re.findall(r"refs/heads/task/(\d{3})", branches))
def deps(s):
    if s in ("—", "-", ""): return set()
    if "всё" in s: return None
    out = set()
    for a, b in re.findall(r"(\d{3})(?:\s*[–-]\s*(\d{3}))?", s):
        out |= {f"{i:03d}" for i in range(int(a), int(b or a) + 1)}
    return out
for r in rows:
    if r["n"] in done or r["n"] in started or "Cline" not in r["who"]: continue
    d = deps(r["deps"])
    if d is None or not d <= done: continue
    print(r["n"]); sys.exit(0)
