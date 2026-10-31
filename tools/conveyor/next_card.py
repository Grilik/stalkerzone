"""Выбор следующей карточки для конвейера по таблице docs/tasks/README.md.
Печатает номер (NNN) или пусто. Пропускает карточки не для Cline, с невыполненными зависимостями и уже начатые (есть ветка task/NNN-*).
Линия «sim» без своих карточек берёт карточки мира с конца очереди."""
import glob, os, re, subprocess, sys
LANE = os.environ.get("LANE", "")  # sim | world | пусто — любая
# карточки, которые уже пытались застолбить в этом запуске (их заняла другая линия)
SKIP = set(os.environ.get("SKIP_CARDS", "").split())
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
ready = {"sim": [], "world": []}
for r in rows:
    if r["n"] in done or r["n"] in started or r["n"] in SKIP or "Cline" not in r["who"]: continue
    f = glob.glob(f"docs/tasks/{r['n']}-*.md")
    lane = "world" if f and "**Линия: мир" in open(f[0], encoding="utf-8").read() else "sim"
    d = deps(r["deps"])
    if d is None or not d <= done: continue
    ready[lane].append(r["n"])
if not LANE:
    pick = (ready["sim"] + ready["world"])[:1]
elif ready[LANE]:
    pick = ready[LANE][:1]
elif LANE == "sim":
    # своих карточек у «sim» нет — помогает миру, но берёт с КОНЦА очереди,
    # чтобы не столкнуться с линией «world», которая берёт с начала
    pick = ready["world"][-1:]
else:
    pick = []
if pick: print(pick[0])
