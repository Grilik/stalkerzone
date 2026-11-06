"""Метрики конвейера v2 (П-13): цена одной СЛИТОЙ карточки и доля слитых без переделок.

python3 -I tools/conveyor/metrics.py            — по PR с меткой «conveyor» (нужен gh и GH_TOKEN)
python3 -I tools/conveyor/metrics.py --text     — то же одной строкой (для сводки в Telegram)

Строка метрик в теле PR пишется conveyor.sh: <!-- conveyor-metrics: card=NNN ok=1 cost=0.12 minutes=7 attempts=1 model=… -->.
«Без переделок» — PR слит, и все коммиты в нём от конвейера (никто не дописывал руками).
Правило из 06-conveyor-v2.md: если цена слитой карточки выше, чем сделать то же в чате, — конвейер выключаем.
"""
import json
import re
import subprocess
import sys

REPO = "Grilik/stalkerzone"
BOT = "stalkerzone-conveyor"


def gh(path):
    """Все элементы списка со всех страниц."""
    out = subprocess.run(["gh", "api", "--paginate", path, "--jq", ".[] | tojson"],
                         capture_output=True, text=True, check=True).stdout
    return [json.loads(line) for line in out.splitlines() if line.strip()]


def parse(body):
    m = re.search(r"conveyor-metrics:([^>]*)", body or "")
    return dict(kv.split("=", 1) for kv in m.group(1).split() if "=" in kv) if m else None


def collect():
    rows = []
    for pr in gh(f"repos/{REPO}/pulls?state=all&per_page=100"):
        m = parse(pr.get("body"))
        if not m:
            continue
        merged = bool(pr.get("merged_at"))
        clean = False
        if merged:
            commits = gh(f"repos/{REPO}/pulls/{pr['number']}/commits?per_page=100")
            clean = all((c.get("commit", {}).get("author", {}).get("name") == BOT) for c in commits)
        cost = float(m["cost"]) if re.fullmatch(r"[0-9.]+", m.get("cost", "")) else None
        rows.append(dict(pr=pr["number"], card=m.get("card"), ok=m.get("ok") == "1", merged=merged, clean=clean,
                         cost=cost, minutes=int(m.get("minutes", 0) or 0)))
    return rows


def cost_text(r):
    return "" if r["cost"] is None else "%.2f" % r["cost"]


def summary(rows):
    spent = sum(r["cost"] or 0 for r in rows)
    merged = [r for r in rows if r["merged"]]
    clean = [r for r in merged if r["clean"]]
    per = spent / len(merged) if merged else None
    return dict(cards=len(rows), ok=sum(r["ok"] for r in rows), merged=len(merged), clean=len(clean), spent=spent,
                per_merged=per, clean_share=(len(clean) / len(merged)) if merged else None)


def main():
    rows = collect()
    s = summary(rows)
    per = "—" if s["per_merged"] is None else f"${s['per_merged']:.2f}"
    share = "—" if s["clean_share"] is None else f"{s['clean_share'] * 100:.0f}%"
    line = (f"Конвейер v2: карточек {s['cards']}, тест зелёный {s['ok']}, слито {s['merged']} "
            f"(без переделок {s['clean']}, {share}); потрачено ${s['spent']:.2f}, цена слитой карточки {per}")
    if "--text" in sys.argv:
        print(line)
        return
    print("| PR | Карточка | Тест зелёный | Слит | Без переделок | $ | мин |")
    print("|---|---|---|---|---|---|---|")
    for r in rows:
        print(f"| #{r['pr']} | {r['card']} | {'да' if r['ok'] else 'нет'} | {'да' if r['merged'] else 'нет'} | "
              f"{'да' if r['clean'] else '—'} | {cost_text(r)} | {r['minutes']} |")
    print()
    print(line)


if __name__ == "__main__":
    main()
