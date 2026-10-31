"""Ежедневная сводка в Telegram (карточка 017): работа за сутки, карточки, расходы. Без моделей ИИ.
DRY_RUN=1 — напечатать сводку вместо отправки. Любой блок может упасть — тогда «—», остальное уходит."""
import datetime as dt, json, os, re, subprocess, sys, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tg  # noqa: E402

REPO = os.environ.get("GITHUB_REPOSITORY", "Grilik/stalkerzone")
NOW = dt.datetime.now(dt.timezone.utc)
SINCE = NOW - dt.timedelta(hours=24)
MSK = dt.timezone(dt.timedelta(hours=3))


def gh(path):
    out = subprocess.run(["gh", "api", path], capture_output=True, text=True, timeout=60)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:200])
    return json.loads(out.stdout)


def ts(s):
    return dt.datetime.fromisoformat(s.replace("Z", "+00:00")) if s else None


def http_json(url, key):
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {key}"})
    return json.load(urllib.request.urlopen(req, timeout=20))


def money(v):
    return "—" if v is None else f"${float(v):.2f}"


def block(fn):
    try:
        return fn()
    except Exception as e:
        print(f"блок {fn.__name__}: {e}", file=sys.stderr)
        return [f"{fn.__doc__}: —"]


def work():
    """Работа"""
    closed = gh(f"repos/{REPO}/pulls?state=closed&sort=updated&direction=desc&per_page=50")
    merged = [p for p in closed if p.get("merged_at") and ts(p["merged_at"]) >= SINCE]
    opened = gh(f"repos/{REPO}/pulls?state=open&per_page=50")
    def pr(p):
        human = any(l["name"] == "needs-human" for l in p.get("labels", []))
        return f"{'⚠️ ' if human else ''}#{p['number']} {p['title'][:60]}"
    lines = [f"Слито PR: {len(merged)}" + (" — " + "; ".join(pr(p) for p in merged) if merged else "")]
    lines.append(f"Открыто PR: {len(opened)}" + (" — " + "; ".join(pr(p) for p in opened) if opened else ""))
    return lines


def runs():
    """Запуски"""
    since = SINCE.strftime("%Y-%m-%dT%H:%M:%SZ")
    data = gh(f"repos/{REPO}/actions/runs?per_page=100&created=%3E%3D{since}")["workflow_runs"]
    out = []
    for name in ("conveyor", "build"):
        rs = [r for r in data if r["name"] == name and r.get("conclusion") not in ("skipped", "cancelled")]
        ok = sum(r.get("conclusion") == "success" for r in rs)
        bad = sum(r.get("conclusion") == "failure" for r in rs)
        run = sum(r.get("status") != "completed" for r in rs)
        out.append(f"{'Конвейер' if name == 'conveyor' else 'Сборка'}: {len(rs)} (✅{ok} ❌{bad}" + (f" ⏳{run}" if run else "") + ")")
    if not [r for r in data if r["name"] == "conveyor" and r.get("conclusion") != "skipped"]:
        out.append("💤 Конвейер простаивал")
    return out


def cards():
    """Карточки"""
    rows = []
    for line in open("docs/tasks/README.md", encoding="utf-8"):
        c = [x.strip() for x in line.strip().strip("|").split("|")]
        if len(c) >= 5 and re.fullmatch(r"\d{3}", c[0]):
            rows.append((c[0], c[4]))
    done = [n for n, s in rows if s.startswith("готово")]
    wait = [n for n, s in rows if not s.startswith("готово")]
    return [f"Карточки: {len(done)}/{len(rows)} готово" + (f"; дальше: {', '.join(wait[:5])}" if wait else "")]


def openrouter():
    """OpenRouter"""
    key = os.environ.get("OPENROUTER_API_KEY", "")
    if not key:
        return ["OpenRouter: —"]
    d = http_json("https://openrouter.ai/api/v1/key", key).get("data", {})
    s = (f"OpenRouter: сегодня {money(d.get('usage_daily'))}, неделя {money(d.get('usage_weekly'))}, "
         f"месяц {money(d.get('usage_monthly'))}")
    if d.get("limit_remaining") is not None:
        s += f", лимит ключа: осталось {money(d['limit_remaining'])}"
    try:
        c = http_json("https://openrouter.ai/api/v1/credits", key).get("data", {})
        s += f", счёт {money(float(c['total_credits']) - float(c['total_usage']))}"
    except Exception:
        pass
    return [s]


def proxyapi():
    """ProxyAPI"""
    key = os.environ.get("PROXYAPI_KEY", "")
    if not key:
        return ["ProxyAPI: —"]
    try:
        d = http_json("https://api.proxyapi.ru/proxyapi/balance", key)
        v = d.get("balance") if isinstance(d, dict) else None
        if isinstance(v, (int, float, str)) and str(v).replace(".", "", 1).lstrip("-").isdigit():
            return [f"ProxyAPI: {float(v):.0f} ₽"]
    except Exception as e:
        print(f"proxyapi: {e}", file=sys.stderr)
    return ["ProxyAPI: баланс по API недоступен"]


def main():
    lines = [f"📊 Сводка за {NOW.astimezone(MSK):%d.%m %H:%M} МСК"]
    for fn in (work, runs, cards, openrouter, proxyapi):
        lines += block(fn)
    text = "\n".join(lines)
    if os.environ.get("DRY_RUN"):
        print(text)
    else:
        print(text)
        tg.send(text)


if __name__ == "__main__":
    main()
