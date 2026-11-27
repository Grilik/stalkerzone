# -*- coding: utf-8 -*-
"""Интерактивная карта Зоны: собирает _site/map/index.html + data.json (+terrain.png при наличии данных)."""
import json, math, datetime, struct, zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DOCS = ROOT / "docs" / "thezone"
TPL = HERE / "zonemap_template.html"


def xy(it):
    if isinstance(it, dict):
        for a, b in (("x", "z"), ("X", "Z"), ("px", "pz")):
            if a in it and b in it:
                try:
                    return float(it[a]), float(it[b])
                except Exception:
                    pass
        p = it.get("point") or it.get("pos")
        if isinstance(p, (list, tuple)) and len(p) >= 3:
            try:
                return float(p[0]), float(p[2])
            except Exception:
                pass
        return None
    if isinstance(it, (list, tuple)):
        try:
            if len(it) == 2:
                return float(it[0]), float(it[1])
            if len(it) >= 3:
                return float(it[0]), float(it[2])
        except Exception:
            return None
    return None


def kind_of(name):
    n = (name or "").lower()
    rules = [
        (("деревня новичков", "бар ", "100 рентген", "скадовск", "янов"), "hub"),
        (("бункер сидоровича", "сидорович", "торгов"), "trader"),
        (("блокпост", "кпп", "периметр", "кордон юж"), "checkpoint"),
        (("застава", "аванпост", "элеватор", "вагончик", "о7", "о12"), "outpost"),
        (("янтарь", "учёны", "учены", "лаборатор", "бункер учен"), "science"),
        (("радар", "припять", "чаэс", "саркофаг", "генератор", "лиманск", "госпиталь"), "north"),
        (("логово", "собач", "лысый холм"), "lair"),
        (("аномал", "свалка техники", "а6"), "anomaly"),
        (("атп", "ферма", "свиноферм", "склады", "армейск", "мёртвый город", "мертвый город",
          "болота", "агропром", "тёмная", "темная", "рыжий", "затон", "юпитер", "полигон"), "base"),
    ]
    for keys, t in rules:
        if any(k in n for k in keys):
            return t
    return "base"


def owner_of(name):
    n = (name or "").lower()
    for k, v in (("блокпост", "Военные"), ("кпп", "Военные"), ("периметр", "Военные"),
                 ("армейск", "Военные"), ("атп", "Бандиты"), ("свалка", "Бандиты"),
                 ("бар", "Долг"), ("росток", "Долг"), ("склады", "Свобода"),
                 ("янтарь", "Учёные"), ("болота", "Чистое небо"),
                 ("мёртвый", "Наёмники"), ("мертвый", "Наёмники"),
                 ("радар", "Монолит"), ("припять", "Монолит"), ("чаэс", "Монолит"),
                 ("генератор", "О-Сознание"), ("деревня новичков", "Вольные сталкеры"),
                 ("сидорович", "Торговцы"), ("ферма", "Одиночки"), ("элеватор", "Одиночки")):
        if k in n:
            return v
    return ""


def mst(pts):
    n = len(pts)
    if n < 2:
        return []
    used = [False] * n
    best = [1e18] * n
    par = [0] * n
    best[0] = 0
    edges = []
    for _ in range(n):
        u, bv = -1, 1e18
        for i in range(n):
            if not used[i] and best[i] < bv:
                bv, u = best[i], i
        if u < 0:
            break
        used[u] = True
        if u:
            edges.append([par[u], u])
        for v in range(n):
            if used[v]:
                continue
            d = math.hypot(pts[u][0] - pts[v][0], pts[u][1] - pts[v][1])
            if d < best[v]:
                best[v], par[v] = d, u
    return edges


def write_png(path, rgb):
    h, w, _ = rgb.shape
    raw = b"".join(b"\x00" + rgb[y].tobytes() for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 6))
           + chunk(b"IEND", b""))
    path.write_bytes(png)


def terrain(out):
    try:
        import numpy as np
    except Exception:
        return None, "Рельеф: numpy недоступен — векторная география"
    cands = []
    for base in (ROOT.parent / "world" / "survey", DOCS / "survey", ROOT.parent / "world"):
        if base.exists():
            cands += sorted(base.glob("*.npz"))
    for f in cands:
        try:
            z = np.load(f, allow_pickle=False)
            keys = list(z.keys())
            arr = next((z[k] for k in keys
                        if getattr(z[k], "ndim", 0) == 2 and min(z[k].shape) > 64), None)
            ext = next(([float(v) for v in z[k].tolist()] for k in keys
                        if k.lower() in ("bbox", "extent", "bounds")), None)
            if arr is None or not ext or len(ext) != 4:
                continue
            a = arr.astype("float32")
            step = max(1, int(max(a.shape) / 1100))
            a = a[::step, ::step]
            mn, mx = float(a.min()), float(a.max())
            t = (a - mn) / max(1e-6, mx - mn)
            gy, gx = np.gradient(a)
            shade = np.clip(0.55 + (-gx * 0.6 - gy * 0.8) / 12.0, 0, 1)
            lo = np.array([26, 32, 22], "float32")
            mid = np.array([74, 84, 52], "float32")
            hi = np.array([176, 168, 128], "float32")
            col = np.where(t[..., None] < .5,
                           lo + (mid - lo) * (t[..., None] * 2),
                           mid + (hi - mid) * ((t[..., None] - .5) * 2))
            rgb = np.clip(col * (0.45 + 0.75 * shade[..., None]), 0, 255).astype("uint8")
            write_png(out / "terrain.png", rgb)
            return {"src": "terrain.png", "bbox": ext, "w": int(rgb.shape[1]), "h": int(rgb.shape[0])}, \
                   "Рельеф: " + f.name + " " + str(arr.shape)
        except Exception:
            continue
    return None, "Рельеф: растр с bbox не найден — векторная география (положите mosaic.npz с bbox в world/survey)"


def build(out_dir):
    out = Path(out_dir) / "map"
    out.mkdir(parents=True, exist_ok=True)
    src = []
    border = []
    marks = []
    b = DOCS / "border.json"
    if b.exists():
        d = json.loads(b.read_text(encoding="utf-8-sig"))
        border = [p for p in (xy(c) for c in d.get("contour", [])) if p]
        for m in d.get("marks", []):
            p = xy(m)
            if p:
                marks.append({"x": p[0], "z": p[1],
                              "name": (m.get("ru") or m.get("name") or "Ориентир") if isinstance(m, dict) else "Ориентир",
                              "kind": (m.get("kind") or m.get("note") or "") if isinstance(m, dict) else ""})
        src.append("docs/thezone/border.json — контур " + str(len(border)) + " точек, метки " + str(len(marks)))
    places = []
    pf = DOCS / "places.json"
    if pf.exists():
        d = json.loads(pf.read_text(encoding="utf-8-sig"))
        for i, it in enumerate(d.get("places", [])):
            p = xy(it)
            if not p:
                continue
            nm = it.get("ru") or it.get("name") or it.get("id") or ("Узел " + str(i))
            places.append({"id": it.get("id") or ("n" + str(i)), "name": it.get("name") or nm, "ru": nm,
                           "x": p[0], "z": p[1], "type": it.get("type") or kind_of(nm),
                           "owner": it.get("owner") or it.get("faction") or owner_of(nm),
                           "status": it.get("status", ""), "task": it.get("task", ""),
                           "note": it.get("note", "")})
        src.append("docs/thezone/places.json — локаций " + str(len(places)))
    structures = []
    surveys = []
    sd = DOCS / "survey"
    if sd.exists():
        for f in sorted(sd.glob("*.json")):
            try:
                d = json.loads(f.read_text(encoding="utf-8-sig"))
            except Exception:
                continue
            poly = [p for p in (xy(c) for c in d.get("area_draft", [])) if p]
            if poly:
                surveys.append({"id": d.get("id", f.stem), "name": d.get("ru") or d.get("name") or f.stem, "polygon": poly})
            for bb in d.get("buildings", []):
                p = xy(bb)
                if not p:
                    continue
                structures.append({"x": p[0], "z": p[1],
                                   "w": float(bb.get("w") or bb.get("width") or 7),
                                   "d": float(bb.get("d") or bb.get("depth") or 7),
                                   "name": bb.get("name") or bb.get("kind") or "",
                                   "loc": d.get("ru") or d.get("name") or f.stem})
            src.append("docs/thezone/survey/" + f.name + " — строений " + str(len(d.get("buildings", []))))
    if not border and places:
        xs = [p["x"] for p in places]
        zs = [p["z"] for p in places]
        border = [[min(xs) - 400, min(zs) - 400], [max(xs) + 400, min(zs) - 400],
                  [max(xs) + 400, max(zs) + 400], [min(xs) - 400, max(zs) + 400]]
    pts = [(p["x"], p["z"]) for p in places]
    allx = [p[0] for p in border] + pts
    allz = [p[1] for p in border] + pts
    bbox = [min(allx), min(allz), max(allx), max(allz)] if allx else [0, 0, 1000, 1000]
    terr, tnote = terrain(out)
    if terr:
        src.append(tnote)
    data = {"meta": {"generated": datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
                     "bbox": bbox, "source": src, "terrain": terr, "terrain_note": tnote},
            "border": border, "places": places, "marks": marks,
            "structures": structures, "survey": surveys,
            "roads": mst(pts)}
    (out / "data.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    tpl = TPL.read_text(encoding="utf-8-sig")
    payload = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")
    (out / "index.html").write_text(tpl.replace("__DATA__", payload), encoding="utf-8")
    print("карта: локаций", len(places), "| контур", len(border), "| строений", len(structures),
          "| рельеф:", "да" if terr else "нет")
    return data


if __name__ == "__main__":
    import sys
    build(sys.argv[1] if len(sys.argv) > 1 else "_site")
