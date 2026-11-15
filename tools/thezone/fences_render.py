"""Э2-16: превью «до/после» заборов (изометрия tools/iso_tex.py с текстурами пака).

Вызывается из fences.py render. --pack — папка пака с assets/minecraft/textures/block, где есть все ванильные
текстуры (пак TheZone поверх текстур из jar клиента 1.20.1). Framed-блоки рисуются по камуфляжу, рабица —
коробками (трубы, сетка, колючка), корпус пропса prop_hitbox не рисуется.
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
CAPS = {
    "village": "Деревня новичков — А: серые доски, столбы через 4, 2 дыры и лаз",
    "south": "Южный блокпост — Б: рабица 3x2 на трубах с колючкой",
    "atp": "АТП — В: плиты ПО-2 в 2 блока, 2 выбитые плиты, пролом",
    "far": "Дальний блокпост — В: плиты ПО-2",
    "bridge": "Под мостом — В: плиты ПО-2",
}


def _font(sz, bold=False):
    for f in (r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf" % ("-Bold" if bold else "")):
        if os.path.exists(f):
            return ImageFont.truetype(f, sz)
    return ImageFont.load_default()


def _pane(p, t):
    c0, c1 = 0.5 - t, 0.5 + t
    out = [(c0, 0, c0, c1, 1, c1)]
    for d, b in (("north", (c0, 0, 0, c1, 1, c0)), ("south", (c0, 0, c1, c1, 1, 1)),
                 ("west", (0, 0, c0, c0, 1, c1)), ("east", (c1, 0, c0, 1, 1, c1))):
        if p.get(d) == "true":
            out.append(b)
    return out


def _chain(p):
    """prop_chainlink: секция 3 клетки вдоль оси (facing east — вдоль z, south — вдоль x), 2 блока + колючка."""
    mesh, rust, wire = ("mod:prop/chainlink",) * 3, ("mod:prop/rust",) * 3, ("mod:prop/wire",) * 3
    along_z = p.get("facing", "east") in ("east", "west")
    out = []

    def bx(a0, y0, b0, a1, y1, b1):  # a — поперёк, b — вдоль (от −1.5 до 1.5 относительно центра клетки)
        return (a0, y0, b0 + 0.5, a1, y1, b1 + 0.5) if along_z else (b0 + 0.5, y0, a0, b1 + 0.5, y1, a1)
    out.append((bx(0.49, 0.1, -1.5, 0.51, 1.95, 1.5), mesh))
    for b in (-1.5, 0.0, 1.5):
        out.append((bx(0.455, 0, b - 0.045, 0.545, 2.05, b + 0.045), rust))
    for y in (0.1, 1.95):
        out.append((bx(0.475, y - 0.025, -1.5, 0.525, y + 0.025, 1.5), rust))
    for y in (2.15, 2.25):
        out.append((bx(0.55 + (y - 2.15), y, -1.5, 0.57 + (y - 2.15), y + 0.02, 1.5), wire))
    return out


def render_all(w, allp, outdir, pack):
    sys.argv = [sys.argv[0], pack]
    import iso_tex as it
    it.MODBLOCKS = None
    import fences as F
    os.makedirs(outdir, exist_ok=True)

    def parts(n, p, nb):
        if n == "framed_pane":
            return [(b, it.faces_tex(n, p)) for b in _pane(p, 1 / 16)]
        if n == "zm_grating_pane":
            return [(b, ("mod:zm_grating",) * 3) for b in _pane(p, 1 / 16)]
        if n == "prop_chainlink":
            return _chain(p)
        if n == "prop_hitbox":
            return []
        return [(b, it.faces_tex(n, p)) for b in it.boxes(n, p, nb)]

    def draw(vox, dst, title, sub, s=10):
        info = {}
        for pos, st in vox.items():
            n, props = it.parse(st)
            info[pos] = (n, props, it.opaque(n, props) and not n.startswith("prop_") and not n.endswith("_pane"))

        def proj(x, y, z): return ((x - z) * s, (x + z) * s / 2 - y * s)
        pts = [proj(*k) for k in vox]
        minx, maxx = min(p[0] for p in pts) - 3 * s, max(p[0] for p in pts) + 3 * s
        miny, maxy = min(p[1] for p in pts) - 4 * s, max(p[1] for p in pts) + 2 * s
        top = 64
        W, H = int(maxx - minx), int(maxy - miny) + top
        arr = np.zeros((H, W, 4), np.uint8)
        gr = np.linspace(0, 1, H)[:, None]
        for i, (c0, c1) in enumerate(zip((138, 148, 150), (196, 192, 172))):
            arr[..., i] = (c0 * (1 - gr) + c1 * gr).astype(np.uint8)
        arr[..., 3] = 255
        img = Image.fromarray(arr, "RGBA")
        for (x, y, z) in sorted(vox, key=lambda k: (k[0] + k[1] + k[2], k[1])):
            n, props, opq = info[(x, y, z)]

            def nb(axis):
                for d in (-1, 1):
                    q = (x + d, y, z) if axis == "x" else (x, y, z + d)
                    if q in info and (info[q][2] or info[q][0].endswith(("_pane", "_fence", "_wall", "iron_bars"))):
                        return True
                return False
            pr = parts(n, props, nb)
            if len(pr) == 1 and pr[0][0] == (0, 0, 0, 1, 1, 1) and opq:
                mask = 0
                if not info.get((x, y + 1, z), (0, 0, False))[2]: mask |= 1
                if not info.get((x + 1, y, z), (0, 0, False))[2]: mask |= 2
                if not info.get((x, y, z + 1), (0, 0, False))[2]: mask |= 4
                if mask == 0:
                    continue
            else:
                mask = 7
            X, Y = proj(x, y, z)
            for b, names in sorted(pr, key=lambda q: q[0][0] + q[0][1] + q[0][2]):
                y0 = int(b[1])  # коробки выше клетки (рабица 2+ блока) — со сдвигом
                bb = (b[0], b[1] - y0, b[2], b[3], b[4] - y0, b[5]) if y0 else b
                spr = it.sprite(names, [it.load(t) for t in names], bb, mask, s, n in it.EMISSIVE) if bb[4] <= 1.0001 else None
                if spr is None:  # высокая коробка — по частям в 1 блок
                    for k in range(int(np.ceil(bb[4])) ):
                        lo, hi = max(bb[1], k), min(bb[4], k + 1)
                        if hi <= lo:
                            continue
                        sp = it.sprite(names, [it.load(t) for t in names], (bb[0], lo - k, bb[2], bb[3], hi - k, bb[5]), mask, s, False)
                        img.alpha_composite(sp, (int(X - minx - s - 1), int(Y - (y0 + k) * s - miny + top - s - 1)))
                    continue
                img.alpha_composite(spr, (int(X - minx - s - 1), int(Y - y0 * s - miny + top - s - 1)))
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, W, top - 4], fill=(28, 30, 26, 255))
        d.text((12, 6), title, fill=(236, 214, 140, 255), font=_font(22, True))
        d.text((12, 36), sub, fill=(200, 200, 190, 255), font=_font(16))
        img = img.convert("RGB")
        img.save(dst)
        return img

    pairs = []
    for site, (cells, edits) in allp.items():
        var, x0, x1, z0, z1 = F.SITES[site][:5]
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        before = {}
        for x in range(x0 - 2, x1 + 3):
            for z in range(z0 - 2, z1 + 3):
                g, st = F.column(w, x, z)
                if g is None:
                    continue
                for y in range(g - 1, g + 12):
                    s = w.get(x, y, z)
                    if F.bid(s) not in ("air", "cave_air"):
                        before[(x - cx, y, z - cz)] = s
        after = dict(before)
        for x, y, z, st, camo in edits:
            k = (x - cx, y, z - cz)
            if F.bid(st) == "air":
                after.pop(k, None)
            elif camo:
                after[k] = st + '{camo:{state:{Name:"%s"}}}' % camo.split("[")[0]
            else:
                after[k] = st
        nb = sum(len(v[1]) for v in cells.values())
        ib = draw(before, os.path.join(outdir, "final-%s-before.png" % site), "ДО: " + CAPS[site].split(" — ")[0],
                  "TheZone: стенка из полных блоков, %d колонок, %d блоков" % (len(cells), nb))
        ia = draw(after, os.path.join(outdir, "final-%s-after.png" % site), "ПОСЛЕ: " + CAPS[site],
                  "правок блоков: %d (x %d..%d, z %d..%d)" % (len(edits), x0, x1, z0, z1))
        pairs.append((ib, ia))
        print("png", site)
    w_ = max(a.width + b.width for a, b in pairs) + 30
    h_ = sum(max(a.height, b.height) + 20 for a, b in pairs) + 60
    sheet = Image.new("RGB", (w_, h_), (22, 24, 20))
    d = ImageDraw.Draw(sheet)
    d.text((12, 12), "Э2-16: тонкие заборы Кордона — слева до, справа после", fill=(236, 214, 140), font=_font(26, True))
    yy = 60
    for a, b in pairs:
        sheet.paste(a, (10, yy)); sheet.paste(b, (20 + a.width, yy))
        yy += max(a.height, b.height) + 20
    sheet.save(os.path.join(outdir, "final.png"))
    print("final.png", sheet.size)
