# -*- coding: utf-8 -*-
"""SVG-карта Зоны для дашборда (А44). Данные: docs/thezone/border.json + places.json."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BORDER = ROOT / "docs" / "thezone" / "border.json"
PLACES = ROOT / "docs" / "thezone" / "places.json"

COLORS = {
    "Одиночки": "#8a6d3b", "одиночки": "#8a6d3b", "LONERS": "#8a6d3b",
    "Бандиты": "#4a4a4a", "бандиты": "#4a4a4a", "BANDITS": "#4a4a4a",
    "Долг": "#a03030", "долг": "#a03030", "DUTY": "#a03030",
    "Свобода": "#3f7a3f", "свобода": "#3f7a3f", "FREEDOM": "#3f7a3f",
    "Военные": "#3f5a3f", "военные": "#3f5a3f", "MILITARY": "#3f5a3f",
    "Наёмники": "#3f5a8a", "наёмники": "#3f5a8a", "MERCS": "#3f5a8a",
    "Учёные": "#c9c9c9", "учёные": "#c9c9c9", "SCIENTISTS": "#c9c9c9",
    "Монолит": "#7a7a7a", "монолит": "#7a7a7a", "MONOLITH": "#7a7a7a",
    "Торговцы": "#b08a3f", "торговцы": "#b08a3f", "TRADERS": "#b08a3f",
    "вольные": "#6b7a5a", "нейтральная": "#6b7a5a",
}


def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("'", "&#39;").replace('"', "&quot;"))


def _xy(item):
    if isinstance(item, dict):
        return float(item.get("x", item.get("X", 0))), float(item.get("z", item.get("Z", 0)))
    return float(item[0]), float(item[1])


def render_map():
    b = json.loads(BORDER.read_text(encoding="utf-8"))
    p = json.loads(PLACES.read_text(encoding="utf-8"))
    contour = [_xy(c) for c in b.get("contour", [])]
    places = p.get("places", [])
    marks = b.get("marks", [])
    if not contour:
        return "<p class='muted'>контур границы пуст</p>"
    xs = [x for x, z in contour]
    zs = [z for x, z in contour]
    minx, maxx, minz, maxz = min(xs), max(xs), min(zs), max(zs)
    W = 900
    H = max(300, int(W * (maxz - minz) / max(1.0, (maxx - minx))))

    def sx(x):
        return (x - minx) / max(1.0, (maxx - minx)) * (W - 40) + 20

    def sz(z):
        return (z - minz) / max(1.0, (maxz - minz)) * (H - 40) + 20

    pts = " ".join("%.1f,%.1f" % (sx(x), sz(z)) for x, z in contour)
    out = ["<svg viewBox='0 0 %d %d' style='width:100%%;max-width:900px;background:#141a12;border:1px solid #2c352a'>" % (W, H)]
    out.append("<polygon points='%s' fill='#1d231c' stroke='#5a6b52' stroke-width='2'/>" % pts)
    for m in marks:
        if not isinstance(m, dict):
            continue
        x, z = _xy(m)
        nm = m.get("ru") or m.get("name") or ""
        out.append("<rect x='%.1f' y='%.1f' width='6' height='6' fill='#c9b45a'/>" % (sx(x) - 3, sz(z) - 3))
        out.append("<text x='%.1f' y='%.1f' fill='#c9b45a' font-size='10'>%s</text>" % (sx(x) + 6, sz(z) + 3, esc(nm)))
    for pl in places:
        x, z = _xy(pl)
        nm = pl.get("ru") or pl.get("name") or pl.get("id", "")
        owner = pl.get("owner") or pl.get("faction") or ""
        status = pl.get("status", "")
        task = pl.get("task", "")
        color = COLORS.get(owner, "#8a9484")
        out.append("<circle class='mnode' cx='%.1f' cy='%.1f' r='7' fill='%s' stroke='#0e130c' stroke-width='1.5' "
                   "data-name='%s' data-owner='%s' data-status='%s' data-x='%.0f' data-z='%.0f' data-task='%s' "
                   "style='cursor:pointer'><title>%s</title></circle>"
                   % (sx(x), sz(z), color, esc(nm), esc(owner), esc(status), x, z, esc(task), esc(nm)))
        out.append("<text x='%.1f' y='%.1f' fill='#dfe7da' font-size='11'>%s</text>" % (sx(x) + 9, sz(z) + 4, esc(nm)))
    out.append("</svg>")
    out.append("<div id='mapinfo' class='muted' style='margin:8px 0'>Клик по локации — карточка узла.</div>")
    out.append("<script>document.querySelectorAll('.mnode').forEach(function(c){c.addEventListener('click',function(){"
               "var d=c.dataset;var h='<b>'+d.name+'</b> · владелец: '+(d.owner||'—')+' · статус: '+(d.status||'—')+"
               "' · ('+d.x+', '+d.z+')';if(d.task){h+=' · <a href=\"#t-'+d.task+'\">'+d.task+'</a>';}"
               "document.getElementById('mapinfo').innerHTML=h;});});</script>")
    out.append("<table class='rt'><tr><td><b>Локация</b></td><td><b>Владелец</b></td><td><b>Координаты</b></td><td><b>Задача</b></td></tr>")
    for pl in places:
        x, z = _xy(pl)
        nm = pl.get("ru") or pl.get("name") or pl.get("id", "")
        owner = pl.get("owner") or pl.get("faction") or "—"
        task = pl.get("task", "")
        tl = ("<a href='#t-%s'>%s</a>" % (esc(task), esc(task))) if task else "—"
        out.append("<tr><td>%s</td><td>%s</td><td>%.0f, %.0f</td><td>%s</td></tr>" % (esc(nm), esc(owner), x, z, tl))
    out.append("</table>")
    return "".join(out)
