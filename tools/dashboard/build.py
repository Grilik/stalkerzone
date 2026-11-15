"""Страница прогресса Stalker Zone: docs/roadmap.md + docs/requirements.json + PR/CI/nightly с GitHub → один index.html.

python3 -I tools/dashboard/build.py [выходная папка]   (по умолчанию _site)

Данные GitHub — через `gh api` (в Actions — GH_TOKEN). Статус задачи:
  ручной (колонка «статус»: готово / отложено / снято / в работе / ждёт) — главнее;
  иначе слит PR, заголовок которого начинается с номера задачи → готово; открыт такой PR → в работе; иначе ждёт.
Требования (реестр проекта) привязаны к задачам полем task в docs/requirements.json.
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
REQS = "docs/requirements.json"
PROMPTS = "docs/prompts"
MSK = datetime.timezone(datetime.timedelta(hours=3))
MANUAL = {"готово": "done", "в работе": "work", "отложено": "later", "снято": "dropped", "ждёт": "todo"}
LABEL = {"done": "готово", "work": "в работе", "todo": "ждёт", "later": "отложено", "dropped": "снято"}
ORDER = {"work": 0, "todo": 1, "done": 2, "later": 3, "dropped": 4}
RLABEL = {"DONE_TESTED": "проверено в игре", "DONE_UNVERIFIED": "есть, не проверено", "PARTIAL": "частично",
          "DOCUMENTED_ONLY": "только описано", "MISSING": "нет", "BLOCKED": "заблокировано",
          "DEFERRED": "отложено", "CANCELLED": "отменено"}
RORDER = ["DONE_TESTED", "DONE_UNVERIFIED", "PARTIAL", "BLOCKED", "DOCUMENTED_ONLY", "MISSING", "DEFERRED", "CANCELLED"]


def gh(path):
    try:
        out = subprocess.run(["gh", "api", path], capture_output=True, text=True, timeout=60)
        return json.loads(out.stdout) if out.returncode == 0 and out.stdout.strip() else None
    except Exception:
        return None


def esc(s):
    return html.escape(str(s))


def md_inline(s):
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", esc(s))


def md_bold(s):
    return re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", md_inline(s))


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
        if lst:
            out.append("</" + lst + ">")
            lst = None
        para.append(st)
    flush()
    if code:
        out.append("</code></pre>")
    return "".join(out)


def parse_about(text):
    out, on = [], False
    for line in text.splitlines():
        if line.startswith("## "):
            on = line.strip() == "## О моде"
            continue
        if on and line.startswith("- "):
            out.append(line[2:].strip())
    return out


def parse_roadmap(text):
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
                cur["tasks"].append({"id": c[0], "title": c[1], "who": c[2], "manual": (c[3] if len(c) > 3 else "").lower()})
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


def ago(iso):
    if not iso:
        return ""
    t = datetime.datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(MSK)
    return t.strftime("%d.%m %H:%M")


def prompt_of(task_id):
    path = os.path.join(PROMPTS, task_id + ".md")
    if os.path.isfile(path):
        return path, open(path, encoding="utf-8").read()
    return None, None


def req_badges(reqs):
    if not reqs:
        return ""
    cnt = {}
    for r in reqs:
        cnt[r["status"]] = cnt.get(r["status"], 0) + 1
    dots = "".join(f"<i class='rd {k}' title='{RLABEL.get(k, k)}: {v}'></i>" * v
                   for k, v in sorted(cnt.items(), key=lambda kv: RORDER.index(kv[0]) if kv[0] in RORDER else 99))
    return f"<span class='rq' title='требования реестра'>{dots}</span>"


def build(out_dir):
    text = open(ROADMAP, encoding="utf-8").read()
    stages, about = parse_roadmap(text), parse_about(text)
    reqs = json.load(open(REQS, encoding="utf-8"))["requirements"] if os.path.isfile(REQS) else []
    by_task = {}
    for r in reqs:
        by_task.setdefault(r.get("task"), []).append(r)
    task_ids = {t["id"] for s in stages for t in s["tasks"]}
    orphan = [r for r in reqs if r.get("task") not in task_ids and r.get("task") != "снято"]

    prs = load_prs()
    for st in stages:
        for t in st["tasks"]:
            t["status"], t["pr"] = status_of(t, prs)
            t["reqs"] = by_task.get(t["id"], [])
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
    whos = sorted({t["who"] for s in stages for t in s["tasks"]})

    h = []
    a = h.append
    a("<!DOCTYPE html><html lang='ru'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
      "<title>Stalker Zone — прогресс</title><style>" + CSS + "</style></head><body>")
    a("<nav class='top'><b>Stalker Zone</b><a href='#summary'>Сводка</a><a href='#now'>Сейчас</a><a href='#stages'>Этапы</a>"
      "<a href='#reqs'>Требования</a><a href='#merged'>Слияния</a><span class='muted nav-r'>обновлено " + now + " МСК</span></nav><main>")

    # ---------- сводка
    a("<section id='summary'><div class='tiles'>")
    if current:
        pct = round(100 * current["done"] / current["total"]) if current["total"] else 0
        a(f"<a class='tile' href='#{esc(current['id'])}'><div class='k'>Текущий этап</div><div class='v'>{esc(current['id'])} · {esc(current['title'])}</div>"
          f"<div class='bar'><i style='width:{pct}%'></i></div><div class='muted'>{current['done']} из {current['total']} · {pct}%"
          + (f" · в работе {current['work']}" if current["work"] else "") + "</div></a>")
    all_t = sum(s["total"] for s in stages)
    all_d = sum(s["done"] for s in stages)
    allp = round(100 * all_d / all_t) if all_t else 0
    a(f"<a class='tile' href='#stages'><div class='k'>Весь мод</div><div class='v'>{allp}%</div><div class='bar'><i style='width:{allp}%'></i></div>"
      f"<div class='muted'>{all_d} из {all_t} задач · этапов {len(stages)}</div></a>")
    if reqs:
        rt = sum(r["status"] == "DONE_TESTED" for r in reqs)
        ru = sum(r["status"] == "DONE_UNVERIFIED" for r in reqs)
        a(f"<a class='tile' href='#reqs'><div class='k'>Требования (реестр)</div><div class='v'>{rt} из {len(reqs)}</div>"
          f"<div class='bar two'><i style='width:{round(100 * rt / len(reqs))}%'></i><i class='b' style='width:{round(100 * ru / len(reqs))}%'></i></div>"
          f"<div class='muted'>проверено в игре · ещё {ru} есть в коде без проверки</div></a>")
    if run:
        ok = run.get("conclusion")
        cls = "ok" if ok == "success" else ("run" if run.get("status") != "completed" else "bad")
        txt = {"ok": "зелёная", "run": "идёт", "bad": "красная"}[cls]
        a(f"<a class='tile' href='{esc(run['html_url'])}'><div class='k'>Сборка main</div><div class='v {cls}'>{txt}</div>"
          f"<div class='muted'>{esc(run['head_sha'][:7])} · {ago(run.get('updated_at'))}</div></a>")
    if rel:
        asset = next((x["browser_download_url"] for x in rel.get("assets", []) if x["name"].endswith(".jar")), rel.get("html_url"))
        a(f"<a class='tile' href='{esc(asset)}'><div class='k'>Сборка для игры</div><div class='v'>скачать .jar</div>"
          f"<div class='muted'>{esc(str(rel.get('target_commitish', ''))[:7])} · {ago(rel.get('published_at'))} · новый мир</div></a>")
    a(f"<a class='tile' href='#now'><div class='k'>PR на проверке</div><div class='v'>{len(open_prs)}</div><div class='muted'>ждут проверки и слияния</div></a>")
    a("</div>")
    if about:
        a("<details class='about'><summary>О моде</summary><ul>" + "".join(f"<li>{md_bold(x)}</li>" for x in about) + "</ul></details>")
    a("</section>")

    # ---------- сейчас
    a("<section id='now'><h2>Сейчас</h2><div class='cols'>")
    a("<div class='card'><h3>На проверке (открытые PR)</h3>")
    if open_prs:
        a("<ul class='plain'>")
        for p in open_prs:
            ck = p["_checks"]
            cls = "ok" if ck == "зелёные" else ("run" if ck in ("идут", "нет") else "bad")
            a(f"<li><a href='{esc(p['html_url'])}'>#{p['number']}</a> {md_inline(p['title'])}"
              f"{' <span class=chip>черновик</span>' if p.get('draft') else ''} <span class='{cls} small'>· проверки {esc(ck)}</span></li>")
        a("</ul>")
    else:
        a("<p class='muted'>Открытых PR нет.</p>")
    a("</div><div class='card'><h3>Дальше по плану</h3><ol class='plain'>")
    nxt, started = [], False
    for s in stages:
        if s is current:
            started = True
        if started:
            nxt += [t for t in s["tasks"] if t["status"] == "todo"]
    for t in nxt[:7]:
        a(f"<li><a href='#t-{esc(t['id'])}'><b>{esc(t['id'])}</b></a> {md_inline(t['title'])} <span class='muted small'>· {esc(t['who'])}</span></li>")
    a("</ol></div></div></section>")

    # ---------- этапы
    a("<section id='stages'><h2>Этапы</h2><div class='stages'>")
    for s in stages:
        pct = round(100 * s["done"] / s["total"]) if s["total"] else 0
        mark = " cur" if s is current else (" fin" if s["total"] and s["done"] == s["total"] else "")
        a(f"<a class='stage{mark}' href='#{esc(s['id'])}'><b>{esc(s['id'])}</b> {esc(s['title'])}"
          f"<div class='bar'><i style='width:{pct}%'></i></div><span class='muted'>{s['done']}/{s['total']}</span></a>")
    a("</div>")
    a("<div class='toolbar'><input id='q' type='search' placeholder='Поиск по задачам и требованиям…'>"
      "<span class='chips' id='fs'><button data-f='' class='on'>все</button><button data-f='work'>в работе</button>"
      "<button data-f='todo'>ждут</button><button data-f='done'>готово</button><button data-f='later dropped'>отложено/снято</button></span>"
      "<select id='fw'><option value=''>любой исполнитель</option>" + "".join(f"<option>{esc(w)}</option>" for w in whos) + "</select>"
      "<button id='ex'>раскрыть всё</button><button id='co'>свернуть</button></div>")
    for s in stages:
        pct = round(100 * s["done"] / s["total"]) if s["total"] else 0
        op = " open" if s is current else ""
        a(f"<details class='stg' id='{esc(s['id'])}'{op}><summary><b>{esc(s['id'])}</b>&nbsp;· {esc(s['title'])}"
          f"<span class='sum-r'><span class='bar sm'><i style='width:{pct}%'></i></span> {s['done']}/{s['total']}"
          + (f" · в работе {s['work']}" if s["work"] else "") + "</span></summary><div class='stg-body'>")
        if s["goal"]:
            a(f"<p><b>Цель.</b> {md_inline(s['goal'])}</p>")
        if s["done_when"]:
            a(f"<p><b>Готово, когда:</b> {md_inline(s['done_when'])}</p>")
        for t in sorted(s["tasks"], key=lambda t: (ORDER[t["status"]], s["tasks"].index(t))):
            link = f" <a href='{esc(t['pr']['html_url'])}'>#{t['pr']['number']}</a>" if t["pr"] else ""
            when = f" <span class='muted'>{ago(t['pr']['merged_at'])}</span>" if t["pr"] and t["pr"].get("merged_at") else ""
            ppath, ptext = prompt_of(t["id"])
            search = " ".join([t["id"], t["title"], t["who"]] + [r["id"] + " " + r["req"] for r in t["reqs"]]).lower()
            a(f"<details class='task {t['status']}' id='t-{esc(t['id'])}' data-s='{t['status']}' data-w='{esc(t['who'])}' data-q='{esc(search)}'>"
              f"<summary><b class='tid'>{esc(t['id'])}</b><span class='tt'>{md_inline(t['title'])}</span>"
              f"<span class='sum-r'>{req_badges(t['reqs'])}<span class='muted small'>{esc(t['who'])}</span>"
              f"<span class='st {t['status']}'>{LABEL[t['status']]}</span></span></summary><div class='task-body'>")
            a(f"<p><b>Исполнитель:</b> {esc(t['who'])} · <b>Статус:</b> {LABEL[t['status']]}{link}{when}"
              + (" · <span class='muted'>промпт есть</span>" if ptext else "") + "</p>")
            if t["pr"]:
                a(f"<p><b>PR:</b> <a href='{esc(t['pr']['html_url'])}'>#{t['pr']['number']} {md_inline(t['pr']['title'])}</a></p>")
            if t["reqs"]:
                a("<p><b>Требования реестра, которые закрывает задача:</b></p><ul class='rl'>")
                for r in t["reqs"]:
                    a(f"<li><span class='rs {r['status']}'>{RLABEL.get(r['status'], r['status'])}</span> <b>{esc(r['id'])}</b> {md_inline(r['req'])}"
                      + (f"<div class='muted small'>Сейчас: {md_inline(r['proof'])}</div>" if r.get("proof") and r["proof"] != "—" else "")
                      + (f"<div class='muted small'>Следующий шаг (реестр): {md_inline(r['next'])}</div>" if r.get("next") and r["next"] != "—" else "")
                      + "</li>")
                a("</ul>")
            if ptext:
                a(f"<details class='pr-text'><summary>Промпт задачи — {esc(ppath)}</summary><div class='prompt'>{md_block(ptext)}</div></details>")
            else:
                a("<p class='muted small'>Промпта ещё нет — появится в пакете промптов (Э0-21) или его даст штаб.</p>")
            a("</div></details>")
        a("</div></details>")
    a("<p id='empty' class='muted' hidden>Ничего не найдено.</p></section>")

    # ---------- требования
    if reqs:
        a("<section id='reqs'><h2>Требования реестра</h2>"
          "<p class='muted'>Все требования мастер-спецификации из реестра проекта (14.11.2026) и задача, которая их закрывает. "
          "Статус — по реестру: обновляется, когда задача сделана и проверена в игре.</p><div class='legend'>")
        cnt = {}
        for r in reqs:
            cnt[r["status"]] = cnt.get(r["status"], 0) + 1
        for k in RORDER:
            if k in cnt:
                a(f"<span class='rs {k}'>{RLABEL[k]}: {cnt[k]}</span>")
        a("</div>")
        if orphan:
            a("<div class='card warn'><b>Требования без задачи:</b> " + ", ".join(esc(r["id"]) for r in orphan) + "</div>")
        groups = []
        for r in reqs:
            if not groups or groups[-1][0] != r["group"]:
                groups.append((r["group"], []))
            groups[-1][1].append(r)
        for g, rs in groups:
            ok = sum(r["status"] == "DONE_TESTED" for r in rs)
            a(f"<details class='stg'><summary>{esc(g)}<span class='sum-r'>проверено {ok}/{len(rs)}</span></summary><div class='stg-body'><table class='rt'>")
            for r in rs:
                task = r.get("task")
                tl = "снято (15.11)" if task == "снято" else f"<a href='#t-{esc(task)}'>{esc(task)}</a>"
                a(f"<tr><td class='nowrap'><b>{esc(r['id'])}</b></td><td>{md_inline(r['req'])}</td>"
                  f"<td class='nowrap'><span class='rs {r['status']}'>{RLABEL.get(r['status'], r['status'])}</span></td><td class='nowrap'>{tl}</td></tr>")
            a("</table></div></details>")
        a("</section>")

    # ---------- слияния
    a("<section id='merged'><h2>Последние слияния</h2><table>")
    for p in merged:
        a(f"<tr><td class='muted nowrap'>{ago(p['merged_at'])}</td><td><a href='{esc(p['html_url'])}'>#{p['number']}</a> {md_inline(p['title'])}</td></tr>")
    a("</table></section>")
    a(f"<footer class='muted'>Этапы и задачи — <a href='https://github.com/{REPO}/blob/main/{ROADMAP}'>{ROADMAP}</a>, требования — "
      f"<a href='https://github.com/{REPO}/blob/main/{REQS}'>{REQS}</a>, промпты — <a href='https://github.com/{REPO}/tree/main/{PROMPTS}'>{PROMPTS}</a>. "
      "Статус задачи ставится сам: PR с номером задачи в начале заголовка.</footer></main><script>" + JS + "</script></body></html>")

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "index.html"), "w", encoding="utf-8") as f:
        f.write("".join(h))
    print(f"страница: {len(stages)} этапов, {sum(len(s['tasks']) for s in stages)} задач, требований {len(reqs)} (без задачи {len(orphan)}), "
          f"PR {len(prs)}, открытых {len(open_prs)}")


JS = """
var q=document.getElementById('q'),fw=document.getElementById('fw'),fs='';
function apply(){var t=q.value.trim().toLowerCase(),w=fw.value,any=false,act=t||w||fs;
document.querySelectorAll('#stages details.stg').forEach(function(st){var vis=0;
st.querySelectorAll('details.task').forEach(function(d){var ok=(!t||d.dataset.q.indexOf(t)>=0)&&(!w||d.dataset.w==w)&&(!fs||fs.split(' ').indexOf(d.dataset.s)>=0);
d.hidden=!ok;if(ok)vis++;});st.hidden=act&&!vis;if(act&&vis)st.open=true;if(vis)any=true;});
document.getElementById('empty').hidden=any;}
q.addEventListener('input',apply);fw.addEventListener('change',apply);
document.querySelectorAll('#fs button').forEach(function(b){b.onclick=function(){document.querySelectorAll('#fs button').forEach(function(x){x.classList.remove('on')});b.classList.add('on');fs=b.dataset.f;apply();};});
document.getElementById('ex').onclick=function(){document.querySelectorAll('#stages details.stg').forEach(function(d){if(!d.hidden)d.open=true})};
document.getElementById('co').onclick=function(){document.querySelectorAll('#stages details').forEach(function(d){d.open=false})};
function o(){var h=decodeURIComponent(location.hash.slice(1));var e=h&&document.getElementById(h);if(!e)return;
var p=e;while(p){if(p.tagName=='DETAILS')p.open=true;p=p.parentElement;}e.scrollIntoView({block:'start'});}
window.addEventListener('hashchange',o);o();
"""

CSS = """
:root{--bg:#0f1210;--card:#171c18;--line:#262e28;--fg:#dfe6dc;--mut:#8a978b;--acc:#c9a23a;--ok:#5fb36b;--bad:#d9634f;--run:#c9a23a;--work:#4f8fd9}
*{box-sizing:border-box}html{scroll-padding-top:110px}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:1060px;margin:0 auto;padding:12px 14px 40px}a{color:var(--acc);text-decoration:none}a:hover{text-decoration:underline}
nav.top{position:sticky;top:0;z-index:5;background:#0b0e0bee;backdrop-filter:blur(4px);border-bottom:1px solid var(--line);display:flex;gap:16px;align-items:center;padding:10px 16px;flex-wrap:wrap}
nav.top b{font-size:16px}nav.top a{color:var(--fg)}.nav-r{margin-left:auto}
h2{font-size:19px;margin:26px 0 10px}h3{font-size:15px;margin:0 0 8px}.muted{color:var(--mut)}.small{font-size:13px}
code{background:#222a23;padding:0 4px;border-radius:3px;font-size:13px}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px;margin-top:14px}
.tile{display:block;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px;color:var(--fg)}.tile:hover{border-color:var(--acc);text-decoration:none}
.k{color:var(--mut);font-size:12px;text-transform:uppercase;letter-spacing:.04em}.v{font-size:18px;font-weight:600;margin:4px 0 6px}
.v.ok,.ok{color:var(--ok)}.v.bad,.bad{color:var(--bad)}.v.run,.run{color:var(--run)}
.bar{height:6px;background:#26302a;border-radius:3px;overflow:hidden;margin:6px 0 4px;display:flex}.bar i{display:block;height:100%;background:var(--acc)}
.bar.two i{background:var(--ok)}.bar.two i.b{background:var(--work)}.bar.sm{width:80px;margin:0}
details.about{margin-top:12px;background:var(--card);border:1px solid var(--line);border-radius:10px}details.about>summary{cursor:pointer;padding:10px 12px;font-weight:600}
details.about ul{margin:0;padding:0 14px 12px 30px}details.about li{margin:4px 0}
.cols{display:grid;grid-template-columns:1fr 1fr;gap:10px}.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px}
.card.warn{border-color:var(--bad);margin-bottom:10px}ul.plain,ol.plain{margin:0;padding-left:20px}ul.plain li,ol.plain li{margin:5px 0}
.stages{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:8px}
.stage{display:block;background:var(--card);border:1px solid var(--line);border-radius:8px;padding:9px 10px;color:var(--fg);font-size:14px}
.stage:hover{border-color:var(--acc);text-decoration:none}.stage.cur{border-color:var(--acc)}.stage.fin .bar i{background:var(--ok)}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:14px 0 8px;position:sticky;top:44px;z-index:4;background:var(--bg);padding:6px 0}
.toolbar input,.toolbar select,.toolbar button{background:var(--card);color:var(--fg);border:1px solid var(--line);border-radius:8px;padding:6px 10px;font:inherit;font-size:14px}
.toolbar input{flex:1;min-width:200px}.toolbar button{cursor:pointer}.chips{display:flex;gap:4px;flex-wrap:wrap}.chips button.on{border-color:var(--acc);color:var(--acc)}
details summary::-webkit-details-marker{display:none}
details.stg{background:var(--card);border:1px solid var(--line);border-radius:10px;margin:8px 0}
details.stg>summary{cursor:pointer;padding:11px 12px;list-style:none;display:flex;gap:6px;align-items:center;flex-wrap:wrap}
details.stg[open]>summary{border-bottom:1px solid var(--line)}
details.stg>summary:before,details.task>summary:before,details.pr-text>summary:before{content:'▸';color:var(--mut);margin-right:2px}
details[open]>summary:before{content:'▾'!important}
.sum-r{margin-left:auto;display:flex;gap:8px;align-items:center;font-size:13px;color:var(--mut)}
.stg-body{padding:6px 12px 10px}
details.task{border-top:1px solid var(--line)}details.task>summary{cursor:pointer;padding:8px 2px;list-style:none;display:flex;gap:8px;align-items:baseline;flex-wrap:wrap}
details.task.done>summary .tt,details.task.later>summary,details.task.dropped>summary{opacity:.6}
.tid{white-space:nowrap;min-width:62px}.tt{flex:1;min-width:200px}.task-body{padding:2px 4px 12px 24px;font-size:14px}
.st{display:inline-block;padding:1px 8px;border-radius:10px;font-size:12px;border:1px solid var(--line);white-space:nowrap}
.st.done{color:var(--ok);border-color:#2f5a35}.st.work{color:var(--work);border-color:#2b4a72}.st.todo{color:var(--mut)}.st.later,.st.dropped{color:var(--mut);font-style:italic}
.rq{display:inline-flex;gap:2px}.rd{display:inline-block;width:8px;height:8px;border-radius:50%;background:#555}
.rd.DONE_TESTED,.rs.DONE_TESTED{background:#2f5a35}.rd.DONE_UNVERIFIED,.rs.DONE_UNVERIFIED{background:#2b4a72}.rd.PARTIAL,.rs.PARTIAL{background:#6b5a1e}
.rd.BLOCKED,.rs.BLOCKED{background:#7a4a1e}.rd.MISSING,.rs.MISSING{background:#6e2c25}.rd.DOCUMENTED_ONLY,.rs.DOCUMENTED_ONLY{background:#3d4540}
.rd.DEFERRED,.rs.DEFERRED,.rd.CANCELLED,.rs.CANCELLED{background:#2c322e}
.rs{display:inline-block;padding:0 7px;border-radius:9px;font-size:12px;color:#e8eee6;white-space:nowrap}
ul.rl{padding-left:18px;margin:4px 0 8px}ul.rl li{margin:6px 0}
.legend{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px}
table{width:100%;border-collapse:collapse}table.rt td,#merged td{padding:6px 8px;border-top:1px solid var(--line);vertical-align:top;font-size:14px}
#merged table{background:var(--card);border:1px solid var(--line);border-radius:8px}
.nowrap{white-space:nowrap}.chip{font-size:11px;color:var(--mut);border:1px solid var(--line);border-radius:8px;padding:0 6px}
details.pr-text>summary{cursor:pointer;color:var(--acc);list-style:none;margin:6px 0}
.prompt{background:#121612;border:1px solid var(--line);border-radius:8px;padding:8px 12px;max-height:480px;overflow:auto}
.prompt h4{margin:10px 0 4px;font-size:14px;color:var(--acc)}.prompt pre{white-space:pre-wrap;font-size:12px;background:#0b0e0b;padding:8px;border-radius:6px}
footer{margin-top:30px;font-size:13px}
@media(max-width:700px){.cols{grid-template-columns:1fr}.nav-r{display:none}.tid{min-width:0}.sum-r{margin-left:0;width:100%}.task-body{padding-left:8px}table.rt td{font-size:13px}}
"""

if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "_site")
