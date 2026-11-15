"""Страница прогресса Stalker Zone: docs/roadmap.md + PR/CI/nightly с GitHub → один index.html.

python3 -I tools/dashboard/build.py [выходная папка]   (по умолчанию _site)

Данные GitHub берутся через `gh api` (в Actions — GH_TOKEN). Статус задачи:
  ручной (колонка «статус»: готово / отложено / снято / в работе) — главнее;
  иначе слит PR, заголовок которого начинается с номера задачи → готово; открыт такой PR → в работе; иначе ждёт.
"""
import datetime
import html
import json
import os
import re
import subprocess
import sys

REPO = os.environ.get("GITHUB_REPOSITORY", "Grilik/stalkerzone")
ROADMAP = "docs/roadmap.md"
MSK = datetime.timezone(datetime.timedelta(hours=3))
MANUAL = {"готово": "done", "в работе": "work", "отложено": "later", "снято": "dropped", "ждёт": "todo"}
LABEL = {"done": "готово", "work": "в работе", "todo": "ждёт", "later": "отложено", "dropped": "снято"}


def gh(path):
    try:
        out = subprocess.run(["gh", "api", path], capture_output=True, text=True, timeout=60)
        return json.loads(out.stdout) if out.returncode == 0 and out.stdout.strip() else None
    except Exception:
        return None


def parse_about(text):
    """Пункты раздела «## О моде» (строки «- …»)."""
    out, on = [], False
    for line in text.splitlines():
        if line.startswith("## "):
            on = line.strip() == "## О моде"
            continue
        if on and line.startswith("- "):
            out.append(line[2:].strip())
    return out


def md_bold(s):
    return re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", md_inline(s))


def parse_roadmap(text):
    """[{id, title, goal, tasks:[{id, title, who, manual}]}]."""
    stages, cur = [], None
    for line in text.splitlines():
        m = re.match(r"^##\s+(Э\d+)\s*·\s*(.+)$", line)
        if m:
            cur = {"id": m.group(1), "title": m.group(2).strip(), "goal": "", "done_when": "", "tasks": []}
            stages.append(cur)
            continue
        if cur is None:
            continue
        if line.startswith("Цель:"):
            cur["goal"] = line[len("Цель:"):].strip()
            continue
        if line.startswith("Готово, когда:"):
            cur["done_when"] = line[len("Готово, когда:"):].strip()
            continue
        if line.startswith("|") and not re.match(r"^\|\s*[-№]", line):
            c = [x.strip() for x in line.strip().strip("|").split("|")]
            if len(c) >= 3 and c[0]:
                cur["tasks"].append({"id": c[0], "title": c[1], "who": c[2],
                                     "manual": (c[3] if len(c) > 3 else "").lower()})
    return stages


def pr_matches(task_id, title):
    return re.match(r"^\s*" + re.escape(task_id) + r"(?=[\s:.,—–-]|$)", title) is not None


def load_prs():
    prs = []
    for page in range(1, 6):
        batch = gh(f"repos/{REPO}/pulls?state=all&per_page=100&page={page}") or []
        prs += batch
        if len(batch) < 100:
            break
    return prs


def checks_of(sha):
    runs = (gh(f"repos/{REPO}/commits/{sha}/check-runs?per_page=50") or {}).get("check_runs", [])
    if not runs:
        return "нет"
    if any(r["status"] != "completed" for r in runs):
        return "идут"
    bad = [r["name"] for r in runs if r.get("conclusion") not in ("success", "skipped", "neutral")]
    return "красные: " + ", ".join(bad) if bad else "зелёные"


def status_of(task, prs):
    if task["manual"] in MANUAL:
        return MANUAL[task["manual"]], None
    mine = [p for p in prs if pr_matches(task["id"], p["title"])]
    merged = [p for p in mine if p.get("merged_at")]
    if merged:
        return "done", max(merged, key=lambda p: p["merged_at"])
    opened = [p for p in mine if p["state"] == "open"]
    if opened:
        return "work", opened[0]
    return "todo", None


def esc(s):
    return html.escape(str(s))


def md_inline(s):
    s = esc(s)
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", s)


PROMPTS = "docs/prompts"


def md_block(text):
    """Простой markdown → HTML: заголовки, списки, код, абзацы."""
    out, code, para, lst = [], False, [], None

    def flush():
        nonlocal para, lst
        if para:
            out.append("<p>" + md_bold(" ".join(para)) + "</p>")
            para = []
        if lst:
            out.append("</" + lst + ">")
            lst = None

    for line in text.splitlines():
        if line.startswith("```"):
            flush()
            out.append("</code></pre>" if code else "<pre><code>")
            code = not code
            continue
        if code:
            out.append(esc(line) + "\n")
            continue
        st = line.strip()
        if not st:
            flush()
            continue
        h = re.match(r"^(#{1,4})\s+(.*)", st)
        if h:
            flush()
            out.append(f"<h4>{md_bold(h.group(2))}</h4>")
            continue
        b = re.match(r"^([-*]|\d+[.)])\s+(.*)", st)
        if b:
            if para:
                out.append("<p>" + md_bold(" ".join(para)) + "</p>")
                para = []
            kind = "ol" if b.group(1)[0].isdigit() else "ul"
            if lst != kind:
                if lst:
                    out.append("</" + lst + ">")
                out.append("<" + kind + ">")
                lst = kind
            out.append("<li>" + md_bold(b.group(2)) + "</li>")
            continue
        if st.startswith("|"):
            flush()
            out.append("<div class='mono'>" + md_bold(st) + "</div>")
            continue
        if lst:
            out.append("</" + lst + ">")
            lst = None
        para.append(st)
    flush()
    if code:
        out.append("</code></pre>")
    return "".join(out)


def prompt_of(task_id):
    path = os.path.join(PROMPTS, task_id + ".md")
    if os.path.isfile(path):
        return path, open(path, encoding="utf-8").read()
    return None, None


def ago(iso):
    if not iso:
        return ""
    t = datetime.datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(MSK)
    return t.strftime("%d.%m %H:%M")


def build(out_dir):
    text = open(ROADMAP, encoding="utf-8").read()
    stages, about = parse_roadmap(text), parse_about(text)
    prs = load_prs()
    for st in stages:
        for t in st["tasks"]:
            t["status"], t["pr"] = status_of(t, prs)
        counted = [t for t in st["tasks"] if t["status"] not in ("later", "dropped")]
        st["done"] = sum(t["status"] == "done" for t in counted)
        st["total"] = len(counted)
        st["work"] = sum(t["status"] == "work" for t in counted)
    current = next((s for s in stages if s["total"] and s["done"] < s["total"]), stages[-1] if stages else None)

    run = ((gh(f"repos/{REPO}/actions/workflows/build.yml/runs?branch=main&per_page=1") or {}).get("workflow_runs") or [None])[0]
    rel = gh(f"repos/{REPO}/releases/tags/nightly") or {}
    open_prs = sorted([p for p in prs if p["state"] == "open"], key=lambda p: p["number"])
    for p in open_prs:
        p["_checks"] = checks_of(p["head"]["sha"])
    merged = sorted([p for p in prs if p.get("merged_at")], key=lambda p: p["merged_at"], reverse=True)[:12]
    now = datetime.datetime.now(MSK).strftime("%d.%m.%Y %H:%M")

    # ---------- HTML
    h = []
    a = h.append
    a("<!DOCTYPE html><html lang='ru'><head><meta charset='utf-8'>"
      "<meta name='viewport' content='width=device-width,initial-scale=1'>"
      "<title>Stalker Zone — прогресс</title><style>" + CSS + "</style></head><body><main>")
    a("<header><h1>Stalker Zone <span>прогресс разработки</span></h1>"
      f"<p class='muted'>обновлено {now} МСК · <a href='https://github.com/{REPO}'>репозиторий</a></p></header>")

    # сводка
    a("<section class='tiles'>")
    if current:
        pct = round(100 * current["done"] / current["total"]) if current["total"] else 0
        a(f"<div class='tile'><div class='k'>Текущий этап</div><div class='v'>{esc(current['id'])} · {esc(current['title'])}</div>"
          f"<div class='bar'><i style='width:{pct}%'></i></div><div class='muted'>{current['done']} из {current['total']} · {pct}%"
          + (f" · в работе {current['work']}" if current["work"] else "") + "</div></div>")
    if run:
        ok = run.get("conclusion")
        cls = "ok" if ok == "success" else ("run" if run.get("status") != "completed" else "bad")
        txt = {"ok": "зелёная", "run": "идёт", "bad": "красная"}[cls]
        a(f"<div class='tile'><div class='k'>Сборка main</div><div class='v {cls}'>{txt}</div>"
          f"<div class='muted'><a href='{esc(run['html_url'])}'>{esc(run['head_sha'][:7])}</a> · {ago(run.get('updated_at'))}</div></div>")
    if rel:
        asset = next((x["browser_download_url"] for x in rel.get("assets", []) if x["name"].endswith(".jar")), rel.get("html_url"))
        a(f"<div class='tile'><div class='k'>Сборка для игры (nightly)</div><div class='v'><a href='{esc(asset)}'>скачать .jar</a></div>"
          f"<div class='muted'>{esc(str(rel.get('target_commitish', ''))[:7])} · {ago(rel.get('published_at'))} · новый мир</div></div>")
    all_t = sum(s["total"] for s in stages)
    all_d = sum(s["done"] for s in stages)
    allp = round(100 * all_d / all_t) if all_t else 0
    a(f"<div class='tile'><div class='k'>Весь мод</div><div class='v'>{allp}%</div>"
      f"<div class='bar'><i style='width:{allp}%'></i></div><div class='muted'>{all_d} из {all_t} задач · этапов {len(stages)}</div></div>")
    a(f"<div class='tile'><div class='k'>Открытые PR</div><div class='v'>{len(open_prs)}</div>"
      "<div class='muted'>ждут проверки и слияния</div></div>")
    a("</section>")

    if about:
        a("<section class='about'><h2>О моде</h2><ul>" + "".join(f"<li>{md_bold(x)}</li>" for x in about) + "</ul></section>")

    # этапы — полосы
    a("<section><h2>Этапы</h2><div class='stages'>")
    for s in stages:
        pct = round(100 * s["done"] / s["total"]) if s["total"] else 0
        mark = " cur" if s is current else (" fin" if s["total"] and s["done"] == s["total"] else "")
        a(f"<a class='stage{mark}' href='#{esc(s['id'])}'><b>{esc(s['id'])}</b> {esc(s['title'])}"
          f"<div class='bar'><i style='width:{pct}%'></i></div><span class='muted'>{s['done']}/{s['total']}</span></a>")
    a("</div></section>")

    # открытые PR
    if open_prs:
        a("<section><h2>Сейчас на проверке</h2><table>")
        for p in open_prs:
            ck = p["_checks"]
            cls = "ok" if ck == "зелёные" else ("run" if ck in ("идут", "нет") else "bad")
            a(f"<tr><td><a href='{esc(p['html_url'])}'>#{p['number']}</a></td><td>{md_inline(p['title'])}"
              f"{' <span class=chip>черновик</span>' if p.get('draft') else ''}</td>"
              f"<td class='{cls} nowrap'>проверки {esc(ck)}</td></tr>")
        a("</table></section>")

    # задачи по этапам — раскрывающиеся
    a("<section><h2>Этапы подробно</h2><p class='muted'>Нажмите на этап, чтобы раскрыть: цель, когда этап считается готовым, все задачи. Нажмите на задачу — её описание из промпта и PR.</p>")
    order = {"work": 0, "todo": 1, "done": 2, "later": 3, "dropped": 4}
    for s in stages:
        pct = round(100 * s["done"] / s["total"]) if s["total"] else 0
        op = " open" if s is current else ""
        a(f"<details class='stg' id='{esc(s['id'])}'{op}><summary><b>{esc(s['id'])}</b> · {esc(s['title'])}"
          f"<span class='sum-r'><span class='bar sm'><i style='width:{pct}%'></i></span> {s['done']}/{s['total']}"
          + (f" · в работе {s['work']}" if s["work"] else "") + "</span></summary><div class='stg-body'>")
        if s["goal"]:
            a(f"<p><b>Цель.</b> {md_inline(s['goal'])}</p>")
        if s["done_when"]:
            a(f"<p><b>Готово, когда:</b> {md_inline(s['done_when'])}</p>")
        counts = {}
        for t in s["tasks"]:
            counts[t["status"]] = counts.get(t["status"], 0) + 1
        a("<p class='muted'>" + " · ".join(f"{LABEL[k]}: {v}" for k, v in sorted(counts.items(), key=lambda kv: order[kv[0]])) + "</p>")
        for t in sorted(s["tasks"], key=lambda t: (order[t["status"]], s["tasks"].index(t))):
            link = f" <a href='{esc(t['pr']['html_url'])}'>#{t['pr']['number']}</a>" if t["pr"] else ""
            when = f" <span class='muted'>{ago(t['pr']['merged_at'])}</span>" if t["pr"] and t["pr"].get("merged_at") else ""
            ppath, ptext = prompt_of(t["id"])
            a(f"<details class='task {t['status']}'><summary><b class='tid'>{esc(t['id'])}</b> {md_inline(t['title'])}"
              f"<span class='sum-r'><span class='muted'>{esc(t['who'])}</span> <span class='st {t['status']}'>{LABEL[t['status']]}</span></span></summary><div class='task-body'>")
            a(f"<p><b>Исполнитель:</b> {esc(t['who'])} · <b>Статус:</b> {LABEL[t['status']]}{link}{when}</p>")
            if t["pr"]:
                a(f"<p><b>PR:</b> <a href='{esc(t['pr']['html_url'])}'>#{t['pr']['number']} {md_inline(t['pr']['title'])}</a></p>")
            if ptext:
                a(f"<p class='muted'>Промпт: <a href='https://github.com/{REPO}/blob/main/{esc(ppath)}'>{esc(ppath)}</a></p><div class='prompt'>{md_block(ptext)}</div>")
            else:
                a("<p class='muted'>Промпта ещё нет — появится в пакете промптов (Э0-21) или его даст штаб.</p>")
            a("</div></details>")
        a("</div></details>")
    a("</section>")
    a("<script>function o(){var h=decodeURIComponent(location.hash.slice(1));var e=h&&document.getElementById(h);if(e&&e.tagName=='DETAILS'){e.open=true;e.scrollIntoView();}}window.addEventListener('hashchange',o);o();</script>")

    # последние слияния
    a("<section><h2>Последние слияния</h2><table>")
    for p in merged:
        a(f"<tr><td class='muted nowrap'>{ago(p['merged_at'])}</td><td><a href='{esc(p['html_url'])}'>#{p['number']}</a> {md_inline(p['title'])}</td></tr>")
    a("</table></section>")
    a(f"<footer class='muted'>Задачи и этапы — <a href='https://github.com/{REPO}/blob/main/{ROADMAP}'>{ROADMAP}</a>. "
      "Статус ставится сам: PR с номером задачи в начале заголовка.</footer></main></body></html>")

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "index.html"), "w", encoding="utf-8") as f:
        f.write("".join(h))
    print(f"страница: {len(stages)} этапов, {sum(len(s['tasks']) for s in stages)} задач, PR {len(prs)}, открытых {len(open_prs)}")


CSS = """
:root{--bg:#0f1210;--card:#171c18;--line:#262e28;--fg:#dfe6dc;--mut:#8a978b;--acc:#c9a23a;--ok:#5fb36b;--bad:#d9634f;--run:#c9a23a;--work:#4f8fd9}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:1000px;margin:0 auto;padding:20px 14px 40px}a{color:var(--acc);text-decoration:none}a:hover{text-decoration:underline}
h1{margin:0;font-size:24px}h1 span{font-weight:400;color:var(--mut);font-size:16px;margin-left:6px}h2{font-size:18px;margin:28px 0 10px}
.muted{color:var(--mut);font-size:13px}code{background:#222a23;padding:0 4px;border-radius:3px;font-size:13px}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:10px;margin-top:16px}
.tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px}.k{color:var(--mut);font-size:12px;text-transform:uppercase;letter-spacing:.04em}
.v{font-size:18px;font-weight:600;margin:4px 0 6px}.v.ok,.ok{color:var(--ok)}.v.bad,.bad{color:var(--bad)}.v.run,.run{color:var(--run)}
.bar{height:6px;background:#26302a;border-radius:3px;overflow:hidden;margin:6px 0 4px}.bar i{display:block;height:100%;background:var(--acc)}
.stages{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:8px}
.stage{display:block;background:var(--card);border:1px solid var(--line);border-radius:8px;padding:10px;color:var(--fg)}
.stage.cur{border-color:var(--acc)}.stage.fin .bar i{background:var(--ok)}
table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);border-radius:8px;overflow:hidden}
td{padding:8px 10px;border-top:1px solid var(--line);vertical-align:top}tr:first-child td{border-top:0}.nowrap{white-space:nowrap}
tr.done td{opacity:.6}tr.later td,tr.dropped td{opacity:.4}
.st{display:inline-block;padding:1px 8px;border-radius:10px;font-size:12px;border:1px solid var(--line)}
.st.done{color:var(--ok);border-color:#2f5a35}.st.work{color:var(--work);border-color:#2b4a72}.st.todo{color:var(--mut)}.st.later,.st.dropped{color:var(--mut);font-style:italic}
.chip{font-size:11px;color:var(--mut);border:1px solid var(--line);border-radius:8px;padding:0 6px}
.goal{color:var(--mut);margin:-4px 0 10px}
details.stg{background:var(--card);border:1px solid var(--line);border-radius:10px;margin:8px 0}
details.stg>summary{cursor:pointer;padding:12px;list-style:none;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
details.stg[open]>summary{border-bottom:1px solid var(--line)}
details summary::-webkit-details-marker{display:none}
details.stg>summary:before,details.task>summary:before{content:'▸';color:var(--mut)}details[open]>summary:before{content:'▾'}
.sum-r{margin-left:auto;display:flex;gap:8px;align-items:center;font-size:13px;color:var(--mut)}
.bar.sm{width:90px;margin:0}
.stg-body{padding:6px 14px 12px}
details.task{border-top:1px solid var(--line)}details.task>summary{cursor:pointer;padding:8px 2px;list-style:none;display:flex;gap:8px;align-items:baseline;flex-wrap:wrap}
details.task.done>summary,details.task.later>summary,details.task.dropped>summary{opacity:.6}
.tid{white-space:nowrap}.task-body{padding:4px 4px 12px 22px;font-size:14px}
.prompt{background:#121612;border:1px solid var(--line);border-radius:8px;padding:8px 12px;max-height:420px;overflow:auto}
.prompt h4{margin:10px 0 4px;font-size:14px;color:var(--acc)}.prompt pre{white-space:pre-wrap;font-size:12px;background:#0b0e0b;padding:8px;border-radius:6px}
.mono{font-family:ui-monospace,monospace;font-size:12px;white-space:pre-wrap}.about ul{background:var(--card);border:1px solid var(--line);border-radius:10px;margin:0;padding:12px 14px 12px 30px}.about li{margin:4px 0}footer{margin-top:30px}
@media(max-width:600px){td{padding:7px 6px;font-size:14px}.nowrap{white-space:normal}}
"""

if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "_site")
