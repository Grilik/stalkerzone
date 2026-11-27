# -*- coding: utf-8 -*-
"""Карта Зоны: локации (places + метки border + wishes), границы bbox, дороги и объекты атласа, строения съёмки."""
import json, math, datetime, struct, zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DOCS = ROOT / "docs" / "thezone"
ATLAS = ROOT / "src" / "main" / "resources" / "data" / "stalkerzone"
TPL = HERE / "zonemap_template.html"

MARK_TASK = {"тёмная лощина": "Э5-15", "радар": "Э5-16", "госпиталь": "Э5-17",
             "генераторы": "Э5-18", "полигон": "Э5-20", "рыжий лес": "Э5-19"}


def jload(p):
    try:
        return json.loads(Path(p).read_text(encoding="utf-8-sig"))
    except Exception:
        return None


def xy(it):
    if isinstance(it, dict):
        for a, b in (("x", "z"), ("X", "Z")):
            if a in it and b in it:
                try:
                    return float(it[a]), float(it[b])
                except Exception:
                    pass
        for k in ("thezone", "pos", "center", "point", "at"):
            v = it.get(k)
            if isinstance(v, (list, tuple)) and len(v) >= 2:
                try:
                    return (float(v[0]), float(v[2])) if len(v) >= 3 else (float(v[0]), float(v[1]))
                except Exception:
                    pass
        b = it.get("bbox")
        if isinstance(b, (list, tuple)) and len(b) == 4:
            return (float(b[0]) + float(b[2])) / 2.0, (float(b[1]) + float(b[3])) / 2.0
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


def rect(b):
    return [[b[0], b[1]], [b[2], b[1]], [b[2], b[3]], [b[0], b[3]]]


def kind_of(n):
    n = (n or "").lower()
    for keys, t in ((("деревня новичков", "бар ", "100 рентген", "скадовск", "янов"), "hub"),
                    (("сидорович", "торгов"), "trader"),
                    (("блокпост", "кпп", "периметр"), "checkpoint"),
                    (("застава", "аванпост", "элеватор", "вагончик"), "outpost"),
                    (("янтарь", "учёны", "учены", "лаборатор"), "science"),
                    (("радар", "припять", "чаэс", "саркофаг", "генератор", "госпиталь", "лиманск"), "north"),
                    (("логово", "собач", "лысый холм"), "lair"),
                    (("аномал",), "anomaly"),
                    (("рыжий лес",), "forest"),
                    (("атп", "ферма", "склады", "армейск", "мёртвый", "мертвый", "болота",
                      "агропром", "тёмная", "темная", "затон", "юпитер", "полигон", "дикая"), "base")):
        if any(k in n for k in keys):
            return t
    return "base"


def owner_of(n):
    n = (n or "").lower()
    for k, v in (("блокпост", "Военные"), ("кпп", "Военные"), ("периметр", "Военные"),
                 ("армейск", "Военные"), ("атп", "Бандиты"), ("свалка", "Бандиты"),
                 ("бар", "Долг"), ("дикая", "Долг"), ("склады", "Свобода"),
                 ("янтарь", "Учёные"), ("болота", "Чистое небо"),
                 ("мёртвый", "Наёмники"), ("мертвый", "Наёмники"),
                 ("радар", "Монолит"), ("припять", "Монолит"), ("чаэс", "Монолит"),
                 ("генератор", "О-Сознание"), ("деревня новичков", "Вольные сталкеры"),
                 ("сидорович", "Торговцы"), ("ферма", "Одиночки"), ("элеватор", "Одиночки")):
        if k in n:
            return v
    return ""


def task_of(n):
    n = (n or "").lower()
    for k, v in MARK_TASK.items():
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
            if not used[v]:
                d = math.hypot(pts[u][0] - pts[v][0], pts[u][1] - pts[v][1])
                if d < best[v]:
                    best[v], par[v] = d, u
    return edges


def write_png(path, rgb):
    h, w, _ = rgb.shape
    raw = b"".join(b"\x00" + rgb[y].tobytes() for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def terrain(out):
    try:
        import numpy as np
    except Exception:
        return None, "Рельеф: numpy недоступен — векторная география"
    cands = []
    for base in (ROOT.parent / "world" / "survey", DOCS / "survey"):
        if base.exists():
            cands += sorted(base.glob("*.npz"))
    for f in cands:
        try:
            z = np.load(f, allow_pickle=False)
            keys = list(z.keys())
            arr = next((z[k] for k in keys if getattr(z[k], "ndim", 0) == 2 and min(z[k].shape) > 64), None)
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
            lo, mid, hi = (np.array(c, "float32") for c in ([26, 32, 22], [74, 84, 52], [176, 168, 128]))
            col = np.where(t[..., None] < .5, lo + (mid - lo) * (t[..., None] * 2),
                           mid + (hi - mid) * ((t[..., None] - .5) * 2))
            rgb = np.clip(col * (0.45 + 0.75 * shade[..., None]), 0, 255).astype("uint8")
            write_png(out / "terrain.png", rgb)
            return {"src": "terrain.png", "bbox": ext}, "Рельеф: " + f.name
        except Exception:
            continue
    return None, "Рельеф: растр с bbox не найден — векторная география"


def build(out_dir):
    out = Path(out_dir) / "map"
    out.mkdir(parents=True, exist_ok=True)
    src = []
    places = []
    seen = set()

    def add(name, p, bbox=None, note="", task="", typ=None, owner=None, area=None, source=""):
        key = (name or "").strip().lower()
        if not p or key in seen:
            return False
        seen.add(key)
        places.append({"id": "n" + str(len(places)), "name": name, "ru": name,
                       "x": p[0], "z": p[1], "bbox": bbox, "area": area,
                       "type": typ or kind_of(name), "owner": owner if owner is not None else owner_of(name),
                       "status": "", "task": task or task_of(name), "note": note, "source": source})
        return True

    b = jload(DOCS / "border.json") or {}
    border = [q for q in (xy(c) for c in b.get("contour", [])) if q]
    src.append("border.json: контур " + str(len(border)))
    pj = jload(DOCS / "places.json") or {}
    for it in pj.get("places", []):
        add(it.get("ru") or it.get("name"), xy(it), source="places.json")
    n0 = len(places)
    for m in b.get("marks", []):
        bb = m.get("bbox")
        add(m.get("ru"), xy(m), bbox=bb, note=m.get("note", ""), source="border.json метка " + str(m.get("n")))
    src.append("places.json " + str(n0) + " + метки " + str(len(places) - n0))
    for w in b.get("wishes", []):
        note = w.get("note", "")
        p = None
        import re
        mm = re.search(r"\((-?\d+),\s*(-?\d+)\)", note)
        if mm:
            p = (float(mm.group(1)), float(mm.group(2)))
        add(w.get("ru"), p, note=note, source="border.json wishes")
    src.append("wishes: Рыжий лес и др.")

    atlas_roads, objects, exits = [], [], []
    w = jload(ATLAS / "atlas" / "world.json") or {}
    if not border:
        border = [q for q in (xy(c) for c in w.get("bounds", [])) if q]
    kp = jload(ATLAS / "kordon_points.json") or {}
    shift = kp.get("shift") or {}
    dx, dz = float(shift.get("dx", 0)), float(shift.get("dz", 0))
    nodes = {n.get("id"): n for n in kp.get("nodes", [])}
    loc_dir = ATLAS / "atlas" / "locations"
    if loc_dir.exists():
        for f in sorted(loc_dir.glob("*.json")):
            d = jload(f) or {}
            for r in d.get("roads", []):
                line = [q for q in (xy(c) for c in r.get("line", [])) if q]
                if len(line) > 1:
                    atlas_roads.append({"line": line, "passable": bool(r.get("passable", True)),
                                        "surface": r.get("surface", ""), "loc": d.get("name", f.stem)})
            for o in d.get("objects", []):
                p = xy(o)
                if not p and isinstance(o.get("node"), int):
                    nd = nodes.get(o["node"])
                    if nd and isinstance(nd.get("center"), list):
                        p = (float(nd["center"][0]) + dx, float(nd["center"][2]) + dz)
                if not p:
                    continue
                k = (o.get("kind") or "base").lower()
                typ = {"base": "base", "outpost": "outpost", "lair": "lair", "trader": "trader",
                       "checkpoint": "checkpoint", "anomaly": "anomaly", "camp": "outpost"}.get(k, "landmark")
                objects.append({"x": p[0], "z": p[1], "name": o.get("name") or o.get("id"),
                                "type": typ, "note": (o.get("source") or "")[:220], "kind": k})
            for nm, v in (d.get("exits") or {}).items():
                p = xy(v)
                if p:
                    exits.append({"x": p[0], "z": p[1], "name": "Переход → " + nm,
                                  "note": "атлас " + d.get("name", f.stem)})
            for i, v in enumerate(d.get("closedPassages") or []):
                p = xy(v)
                if p:
                    exits.append({"x": p[0], "z": p[1], "name": "Закрытый проход " + str(i + 1),
                                  "note": "атлас: проход закрыт"})
            src.append("атлас " + f.name + ": дорог " + str(len(d.get("roads", []))) +
                       ", объектов " + str(len(d.get("objects", []))))

    structures, surveys = [], []
    sd = DOCS / "survey"
    if sd.exists():
        for f in sorted(sd.glob("*.json")):
            d = jload(f) or {}
            poly = [q for q in (xy(c) for c in d.get("area_draft", [])) if q]
            if poly:
                surveys.append({"id": d.get("id", f.stem), "name": d.get("ru") or f.stem, "polygon": poly})
            for bb in d.get("buildings", []):
                box = bb.get("bbox")
                if not box or len(box) != 4:
                    continue
                structures.append({"x": (box[0] + box[2]) / 2.0, "z": (box[1] + box[3]) / 2.0,
                                   "w": abs(box[2] - box[0]), "d": abs(box[3] - box[1]),
                                   "name": bb.get("kind", ""), "loc": d.get("ru") or f.stem,
                                   "h": bb.get("height", 0)})
            src.append("съёмка " + f.name + ": строений " + str(len(d.get("buildings", []))))

    pts = [(p["x"], p["z"]) for p in places]
    allx = [q[0] for q in border] + [q[0] for q in pts]
    allz = [q[1] for q in border] + [q[1] for q in pts]
    bbox = [min(allx), min(allz), max(allx), max(allz)] if allx else [0, 0, 1000, 1000]
    terr, tnote = terrain(out)
    data = {"meta": {"generated": datetime.datetime.now().strftime("%Y-%m-%d %H:%M"),
                     "bbox": bbox, "source": src, "terrain": terr, "terrain_note": tnote},
            "border": border, "places": places, "marks": [], "objects": objects, "exits": exits,
            "atlasroads": atlas_roads, "structures": structures, "survey": surveys,
            "links": w.get("links", []), "roads": mst(pts)}
    (out / "data.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    payload = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")
    (out / "index.html").write_text(TPL.read_text(encoding="utf-8-sig").replace("__DATA__", payload), encoding="utf-8")
    print("карта: локаций", len(places), "| объектов атласа", len(objects), "| дорог", len(atlas_roads),
          "| переходов", len(exits), "| строений", len(structures), "| контур", len(border))
    return data


if __name__ == "__main__":
    import sys
    build(sys.argv[1] if len(sys.argv) > 1 else "_site")
