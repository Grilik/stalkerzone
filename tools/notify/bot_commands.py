"""Команды Telegram-боту без сервера (карточка 018): workflow bot-commands раз в ~30 минут забирает новые
сообщения (getUpdates), выполняет команды и подтверждает их. Принимаются только сообщения из TELEGRAM_CHAT_ID.
/баг текст, /идея текст — issue на GitHub; /статус; /сводка; /запуск [NNN] — конвейер; /помощь."""
import json, os, re, subprocess, sys, urllib.parse, urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tg  # noqa: E402
from balance import balance  # noqa: E402

TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
CHAT = os.environ.get("TELEGRAM_CHAT_ID", "").strip()
REPO = os.environ.get("GITHUB_REPOSITORY", "Grilik/stalkerzone")
HELP = """Команды (ответ приходит в течение ~30 минут):
/баг текст — записать баг (issue на GitHub)
/идея текст — записать идею
/статус — что делает конвейер, открытые PR, баланс
/сводка — сводка за сутки сейчас
/запуск — запустить конвейер (следующие карточки); /запуск 012 — конкретную карточку
/помощь — этот список"""


def api(method, **params):
    url = f"https://api.telegram.org/bot{TOKEN}/{method}?" + urllib.parse.urlencode(params)
    return json.load(urllib.request.urlopen(url, timeout=30))


def gh(*args):
    r = subprocess.run(["gh", *args], capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        raise RuntimeError((r.stderr or r.stdout).strip()[:300])
    return r.stdout.strip()


def issue(kind, text):
    if not text:
        return f"Напишите текст после команды, например: /{'баг' if kind == 'bug' else 'идея'} мост: военные не берут пошлину"
    label = "bug" if kind == "bug" else "idea"
    subprocess.run(["gh", "label", "create", label, "--color", "D73A4A" if label == "bug" else "0E8A16"],
                   capture_output=True, text=True)
    title = text.splitlines()[0][:80]
    url = gh("issue", "create", "--title", ("Баг: " if label == "bug" else "Идея: ") + title,
             "--body", text + "\n\n_Из Telegram._", "--label", label)
    return f"📝 Записал: {url}"


def status():
    lines = ["📡 Статус"]
    runs = json.loads(gh("api", f"repos/{REPO}/actions/workflows/conveyor.yml/runs?per_page=10"))["workflow_runs"]
    active = [r for r in runs if r["status"] != "completed"]
    if active:
        for r in active:
            jobs = json.loads(gh("api", f"repos/{REPO}/actions/runs/{r['id']}/jobs"))["jobs"]
            names = {"run (sim)": "симуляция", "run (world)": "мир"}
            state = lambda j: "идёт" if j["status"] == "in_progress" else "ждёт" if j["status"] != "completed" else j["conclusion"]
            js = ", ".join(f"{names.get(j['name'], j['name'])}: {state(j)}" for j in jobs)
            lines.append(f"Конвейер: {js}")
    else:
        lines.append("Конвейер: не работает")
    prs = json.loads(gh("api", f"repos/{REPO}/pulls?state=open&per_page=20"))
    lines.append(f"Открытые PR: {len(prs)}" + "".join(
        f"\n{'⚠️ ' if any(l['name'] == 'needs-human' for l in p['labels']) else ''}#{p['number']} {p['title'][:60]} — {p['html_url']}" for p in prs))
    _, left = balance()
    lines.append(f"OpenRouter: остаток {'—' if left is None else f'${left:.2f}'}")
    return "\n".join(lines)


def report():
    import daily_report
    lines = [f"📊 Сводка по запросу"]
    for fn in (daily_report.work, daily_report.runs, daily_report.cards, daily_report.openrouter, daily_report.proxyapi):
        lines += daily_report.block(fn)
    return "\n".join(lines)


def run(card):
    args = ["workflow", "run", "conveyor.yml", "--ref", "main"]
    if card:
        if not re.fullmatch(r"\d{3}", card):
            return "Номер карточки — три цифры, например /запуск 012"
        args += ["-f", f"card={card}"]
    gh(*args)
    return f"▶️ Конвейер запущен{' для карточки ' + card if card else ''}. Не запускайте повторно, пока он не закончит."


def handle(text):
    m = re.match(r"^/(\S+)\s*(.*)$", text.strip(), re.S)
    if not m:
        return None
    cmd, arg = m.group(1).split("@")[0].lower(), m.group(2).strip()
    if cmd in ("баг", "bug"):
        return issue("bug", arg)
    if cmd in ("идея", "idea"):
        return issue("idea", arg)
    if cmd in ("статус", "status"):
        return status()
    if cmd in ("сводка", "report"):
        return report()
    if cmd in ("запуск", "run"):
        return run(arg)
    return HELP


def main():
    if not TOKEN or not CHAT:
        print("Telegram не настроен, пропуск")
        return
    try:
        ups = api("getUpdates", timeout=0).get("result", [])
    except Exception as e:
        print(f"getUpdates: {e}")
        return
    last = None
    for u in ups:
        last = u["update_id"]
        msg = u.get("message") or {}
        if str((msg.get("chat") or {}).get("id")) != CHAT or not msg.get("text"):
            continue
        try:
            reply = handle(msg["text"])
        except Exception as e:
            reply = f"❌ Не получилось: {e}"
        if reply:
            print(f"> {msg['text'][:60]}")
            tg.send(reply)
    if last is not None:
        try:
            api("getUpdates", offset=last + 1, timeout=0)  # подтвердить обработанные
        except Exception as e:
            print(f"ack: {e}")


if __name__ == "__main__":
    main()
