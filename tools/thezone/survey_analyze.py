#!/usr/bin/env python3
"""Э1-04, шаги 2–5: разбор растров съёмки (survey_raster.py) — классы поверхности, постройки, локации,
подземелья, дефекты, оценка, превью. Только чтение; мир не открывается.

    python -I tools/thezone/survey_analyze.py "<папка растров>" <repo>/docs/thezone

Пишет docs/thezone/survey/<локация>.json, survey/_world.json, img/survey/*.png
и <папка растров>/../mosaic.npz (полный растр 1 px = 1 блок, вне git).

Классы поверхности (по верхнему видимому блоку, текстуры пака TheZone — 02-materials.md):
  1 земля/трава, 2 лес (листва, стволы), 3 вода, 4 постройка (≥3 искусственных блоков над землёй),
  5 асфальт (soul_sand — тёмный асфальт пака), 6 гравий/грунтовка, 7 рельсы, 8 прочее искусственное низкое
  (площадки, бордюры, заборы), 9 песок. Это эвристика: дороги-гравий и русла рек не различаются.
"""
import collections, glob, json, math, os, re, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from survey_map import colour  # noqa: E402

CELL = 4  # клетка для связности, блоков
CLS_COL = {0: (16, 16, 20), 1: (96, 122, 64), 2: (42, 74, 36), 3: (52, 84, 150), 4: (220, 70, 60),
           5: (40, 40, 44), 6: (150, 140, 120), 7: (230, 200, 40), 8: (200, 150, 200), 9: (205, 190, 140)}
CLS_RU = {1: "земля", 2: "лес", 3: "вода", 4: "постройки", 5: "асфальт", 6: "гравий/грунтовка", 7: "рельсы",
          8: "искусственное низкое", 9: "песок"}

SLUG = {"Cordon": "cordon", "Garbage": "garbage", "Agroprom": "agroprom", "Dark Valley": "dark_valley",
        "Clear Sky CAMP (Great Swamp)": "swamps", "100 RADS BAR": "bar", "YANTAR": "yantar",
        "Military Warehouses": "army_warehouses", "Dead CITY": "dead_city", "LIMANSK": "limansk", "ZATON": "zaton",
        "YANOV STATION": "yanov", "Chernobyl Nuclear Power Plant": "chnpp", "Duga-3": "duga",
        "Pripyat North": "pripyat_north", "Pripyat South": "pripyat_south", "Pripyat Center": "pripyat_center",
        "Pripyat East": "pripyat_east", "Pripyat West": "pripyat_west"}
MARK_SLUG = {1: "dark_hollow", 2: "radar", 3: "hospital", 4: "mark4_ring", 5: "generators"}
NEAR = 700  # постройка дальше от всех точек локаций — «вне локаций»


# ---------------- сборка мозаики ----------------
def load_mosaic(rdir):
    cache = os.path.join(os.path.dirname(rdir.rstrip("/\\")), "mosaic.npz")
    if os.path.exists(cache):
        z = np.load(cache)
        return {k: z[k] for k in z.files}
    files = glob.glob(os.path.join(rdir, "r.*.npz"))
    rc = [tuple(map(int, re.search(r"r\.(-?\d+)\.(-?\d+)\.npz", f).groups())) for f in files]
    rx0, rx1 = min(r[0] for r in rc), max(r[0] for r in rc)
    rz0, rz1 = min(r[1] for r in rc), max(r[1] for r in rc)
    W, H = (rx1 - rx0 + 1) * 512, (rz1 - rz0 + 1) * 512
    M = {"top_y": np.full((H, W), -999, np.int16), "ground_y": np.full((H, W), -999, np.int16),
         "top_id": np.zeros((H, W), np.uint16), "art_up": np.zeros((H, W), np.uint8),
         "art_down": np.zeros((H, W), np.uint8)}
    names, nid = ["air"], {"air": 0}
    for f, (rx, rz) in zip(files, rc):
        z = np.load(f)
        remap = np.array([nid.setdefault(n, len(nid)) for n in z["names"]], np.uint16)
        for n in z["names"]:
            if n not in names:
                names.append(n)
        sl = (slice((rz - rz0) * 512, (rz - rz0 + 1) * 512), slice((rx - rx0) * 512, (rx - rx0 + 1) * 512))
        for k in ("top_y", "ground_y", "art_up", "art_down"):
            M[k][sl] = z[k]
        M["top_id"][sl] = remap[z["top_id"]]
    names = [None] * len(nid)
    for n, i in nid.items():
        names[i] = n
    M["names"] = np.array(names)
    M["origin"] = np.array([rx0 * 512, rz0 * 512])
    np.savez_compressed(cache, **M)
    return M


def classify(M):
    names = M["names"]
    def ids(pred):
        return np.array([pred(str(n)) for n in names], bool)
    is_leaf = ids(lambda n: n.endswith(("_leaves", "_log", "_wood", "_sapling")))
    is_water = ids(lambda n: n == "water")
    is_asph = ids(lambda n: n == "soul_sand")
    is_grav = ids(lambda n: n in ("gravel", "dirt_path", "coarse_dirt"))
    is_rail = ids(lambda n: n.endswith("rail"))
    is_sand = ids(lambda n: n == "sand")
    t, up, ok = M["top_id"], M["art_up"], M["top_y"] > -999
    c = np.where(ok, 1, 0).astype(np.uint8)
    c[ok & is_sand[t]] = 9
    c[ok & is_grav[t]] = 6
    c[ok & (up >= 1) & (up < 3)] = 8
    c[ok & is_leaf[t]] = 2
    c[ok & (up >= 3)] = 4
    c[ok & is_asph[t]] = 5
    c[ok & is_rail[t]] = 7
    c[ok & is_water[t]] = 3
    return c


def _np(o):
    if isinstance(o, np.integer):
        return int(o)
    if isinstance(o, np.floating):
        return float(o)
    if isinstance(o, np.ndarray):
        return o.tolist()
    raise TypeError(type(o))


# ---------------- геометрия ----------------
def poly_mask(poly, ox, oz, W, H, step=1):
    im = Image.new("1", (W // step, H // step), 0)
    ImageDraw.Draw(im).polygon([((x - ox) / step, (z - oz) / step) for x, z in poly], fill=1)
    return np.array(im, bool)


def components(mask):
    """8-связные компоненты булевой сетки -> список массивов (r, c)."""
    seen = np.zeros(mask.shape, bool)
    H, W = mask.shape
    out = []
    for r0, c0 in zip(*np.nonzero(mask)):
        if seen[r0, c0]:
            continue
        stack, comp = [(r0, c0)], []
        seen[r0, c0] = True
        while stack:
            r, c = stack.pop()
            comp.append((r, c))
            for dr in (-1, 0, 1):
                for dc in (-1, 0, 1):
                    rr, cc = r + dr, c + dc
                    if 0 <= rr < H and 0 <= cc < W and mask[rr, cc] and not seen[rr, cc]:
                        seen[rr, cc] = True
                        stack.append((rr, cc))
        out.append(np.array(comp))
    return out


def hull(pts):
    pts = sorted(set(map(tuple, pts)))
    if len(pts) < 3:
        return [list(p) for p in pts]
    def half(seq):
        h = []
        for p in seq:
            while len(h) >= 2 and (h[-1][0] - h[-2][0]) * (p[1] - h[-2][1]) - (h[-1][1] - h[-2][1]) * (p[0] - h[-2][0]) <= 0:
                h.pop()
            h.append(p)
        return h
    lo, up = half(pts), half(pts[::-1])
    return [list(p) for p in lo[:-1] + up[:-1]]


def cell_any(mask, k=CELL, frac=0.25):
    H, W = mask.shape
    m = mask[:H - H % k, :W - W % k].reshape(H // k, k, W // k, k).mean((1, 3))
    return m >= frac


# ---------------- main ----------------
def main():
    rdir, docs = sys.argv[1], sys.argv[2]
    repo = os.path.abspath(os.path.join(docs, "..", ".."))
    M = load_mosaic(rdir)
    ox, oz = (int(v) for v in M["origin"])
    H, W = M["top_y"].shape
    names = M["names"]
    cls = classify(M)
    border = json.load(open(os.path.join(docs, "border.json"), encoding="utf-8"))
    places = json.load(open(os.path.join(docs, "places.json"), encoding="utf-8"))["places"]
    inside = poly_mask(border["contour"], ox, oz, W, H)
    have = M["top_y"] > -999

    # точки локаций и метки пользователя
    locs = []
    for p in places:
        locs.append({"id": SLUG[p["name"]], "name": p["name"], "ru": p["ru"], "point": [p["x"], round(p["y"]), p["z"]],
                     "source": "places.json (телепорт автора)"})
    for m in border["marks"]:
        x0, z0, x1, z1 = m["bbox"]
        locs.append({"id": MARK_SLUG[m["n"]], "name": MARK_SLUG[m["n"]], "ru": m["ru"],
                     "point": [(x0 + x1) // 2, None, (z0 + z1) // 2], "mark_bbox": m["bbox"],
                     "source": "border.json, метка %d пользователя" % m["n"]})

    # постройки
    bmask = cls == 4
    bc = cell_any(bmask)
    comps = components(bc)
    blds = []
    for comp in comps:
        if len(comp) < 2:
            continue
        r0, c0 = comp.min(0) * CELL
        r1, c1 = comp.max(0) * CELL + CELL
        sub = bmask[r0:r1, c0:c1]
        if sub.sum() < 24:
            continue
        ty, gy = M["top_y"][r0:r1, c0:c1], M["ground_y"][r0:r1, c0:c1]
        g = int(np.median(gy[sub & (gy > -999)])) if (sub & (gy > -999)).any() else None
        mats = collections.Counter(names[M["top_id"][r0:r1, c0:c1][sub]].tolist()).most_common(3)
        w_, h_ = c1 - c0, r1 - r0
        linear = max(w_, h_) >= 5 * min(w_, h_) and sub.sum() < 0.4 * w_ * h_
        blds.append({"bbox": [c0 + ox, r0 + oz, c1 + ox - 1, r1 + oz - 1], "footprint": int(sub.sum()),
                     "kind": "linear" if linear else "building",
                     "ground_y": g, "top_y": int(ty[sub].max()), "height": int(ty[sub].max()) - g if g is not None else None,
                     "roof": [m for m, _ in mats], "inside_border": bool(inside[(r0 + r1) // 2, (c0 + c1) // 2])})

    # подземелья
    dmask = M["art_down"] >= 3
    under = []
    for comp in components(cell_any(dmask, frac=0.2)):
        if len(comp) < 2:
            continue
        r0, c0 = comp.min(0) * CELL
        r1, c1 = comp.max(0) * CELL + CELL
        sub = dmask[r0:r1, c0:c1]
        under.append({"bbox": [c0 + ox, r0 + oz, c1 + ox - 1, r1 + oz - 1], "columns": int(sub.sum()),
                      "blocks_below_ground": int(M["art_down"][r0:r1, c0:c1][sub].astype(int).sum())})

    # блок-сущности
    bes = []
    for f in glob.glob(os.path.join(rdir, "r.*.json")):
        bes += json.load(open(f, encoding="utf-8"))

    # назначение локациям: сначала рамки меток, затем ближайшая точка ≤ NEAR
    def assign(x, z):
        for L in locs:
            b = L.get("mark_bbox")
            if b and b[0] <= x <= b[2] and b[1] <= z <= b[3]:
                return L["id"]
        best, bd = None, NEAR
        for L in locs:
            if L.get("mark_bbox"):
                continue
            d = math.hypot(L["point"][0] - x, L["point"][2] - z)
            if d < bd:
                best, bd = L["id"], d
        return best or "_outside"

    by = collections.defaultdict(lambda: {"buildings": [], "underground": [], "be": collections.Counter(), "signs": []})
    for b in blds:
        x, z = (b["bbox"][0] + b["bbox"][2]) / 2, (b["bbox"][1] + b["bbox"][3]) / 2
        by[assign(x, z)]["buildings"].append(b)
    for u in under:
        by[assign((u["bbox"][0] + u["bbox"][2]) / 2, (u["bbox"][1] + u["bbox"][3]) / 2)]["underground"].append(u)
    for e in bes:
        if e["x"] is None:
            continue
        k = by[assign(e["x"], e["z"])]
        k["be"]["command_block" if "command_block" in e["id"] else e["id"]] += 1
        if e.get("text"):
            k["signs"].append([e["x"], e["y"], e["z"], e["text"][:60]])

    os.makedirs(os.path.join(docs, "survey"), exist_ok=True)
    os.makedirs(os.path.join(docs, "img", "survey"), exist_ok=True)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except Exception:
        font = ImageFont.load_default()

    # пологость земли (клетка 4×4): перепад ≤ 1
    gy = M["ground_y"].astype(np.int32)
    k = CELL
    g4 = gy[:H - H % k, :W - W % k].reshape(H // k, k, W // k, k)
    flat4 = (g4.max((1, 3)) - g4.min((1, 3))) <= 1
    cls4 = cls[:H - H % k, :W - W % k].reshape(H // k, k, W // k, k)

    # отрисовка колонки по верхнему блоку с тенью
    col_lut = np.array([colour(str(n)) for n in names], np.float32)
    def render(x0, z0, x1, z1, maxside=560, outline=None):
        c0, r0, c1, r1 = max(0, x0 - ox), max(0, z0 - oz), min(W, x1 - ox + 1), min(H, z1 - oz + 1)
        ty = M["top_y"][r0:r1, c0:c1].astype(np.float32)
        rgb = col_lut[M["top_id"][r0:r1, c0:c1]]
        sh = np.zeros_like(ty)
        sh[1:, 1:] = (ty[1:, 1:] - ty[:-1, :-1])
        rgb = rgb * np.clip(1 + sh[..., None] * 0.08, 0.6, 1.4)
        rgb[M["top_y"][r0:r1, c0:c1] == -999] = (16, 16, 20)
        im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
        sc = min(1.0, maxside / max(im.size))
        if sc < 1:
            im = im.resize((max(1, int(im.size[0] * sc)), max(1, int(im.size[1] * sc))), Image.LANCZOS)
        d = ImageDraw.Draw(im)
        P = lambda x, z: ((x - ox - c0) * sc, (z - oz - r0) * sc)
        bp = [P(x, z) for x, z in border["contour"]]
        d.line(bp + [bp[0]], fill=(255, 255, 255), width=2)
        for b in outline or []:
            d.rectangle([P(b["bbox"][0], b["bbox"][1]), P(b["bbox"][2], b["bbox"][3])], outline=(255, 60, 60))
        step = 64 if (x1 - x0) < 900 else 128 if (x1 - x0) < 2000 else 512
        for gx in range(math.ceil(x0 / step) * step, x1, step):
            d.text((P(gx, z0)[0] + 2, 2), str(gx), fill=(255, 255, 0), font=font)
        for gz in range(math.ceil(z0 / step) * step, z1, step):
            d.text((2, P(x0, gz)[1] + 2), str(gz), fill=(255, 255, 0), font=font)
        return im, sc, P

    # ---------------- по локациям ----------------
    summary = []
    for L in locs:
        rec = by.get(L["id"], {"buildings": [], "underground": [], "be": collections.Counter(), "signs": []})
        bl = rec["buildings"]
        pts = [[b["bbox"][i], b["bbox"][j]] for b in bl for i in (0, 2) for j in (1, 3)]
        px, _, pz = L["point"]
        if L.get("mark_bbox"):
            bx0, bz0, bx1, bz1 = L["mark_bbox"]
        elif pts:
            xs, zs = [p[0] for p in pts] + [px], [p[1] for p in pts] + [pz]
            bx0, bz0, bx1, bz1 = min(xs) - 32, min(zs) - 32, max(xs) + 32, max(zs) + 32
        else:
            bx0, bz0, bx1, bz1 = px - 128, pz - 128, px + 128, pz + 128
        area = hull(pts + [[px, pz]]) if len(pts) >= 3 else [[bx0, bz0], [bx1, bz0], [bx1, bz1], [bx0, bz1]]
        r0, r1, c0, c1 = max(0, bz0 - oz), min(H, bz1 - oz), max(0, bx0 - ox), min(W, bx1 - ox)
        cl = cls[r0:r1, c0:c1]
        share = {CLS_RU[c]: round(float((cl == c).mean()), 4) for c in CLS_RU}
        holes = int(((~have[r0:r1, c0:c1]) & inside[r0:r1, c0:c1]).sum())
        ty, gyy, up = M["top_y"][r0:r1, c0:c1], M["ground_y"][r0:r1, c0:c1], M["art_up"][r0:r1, c0:c1]
        floating = int(((ty - gyy >= 6) & (up == 1) & (cl == 8)).sum())
        f4 = flat4[r0 // k:r1 // k, c0 // k:c1 // k]
        q = cls4[r0 // k:r1 // k, :, c0 // k:c1 // k, :]
        free4 = ~(q == 4).any((1, 3)) & ~(q == 3).any((1, 3))
        flat_free = float((f4 & free4).mean()) if f4.size else 0.0
        road = share["асфальт"] + share["гравий/грунтовка"] + share["рельсы"]
        # оценка 0–5 автоматом только по трём критериям; 1 и 2 — оценивают люди по скриншотам
        s3 = sum(road >= t for t in (0.002, 0.005, 0.01, 0.015, 0.02))  # доля дорог в рамке
        s4 = min(5, int(flat_free * 6))
        s5 = max(0, 5 - min(3, holes // 4096) - min(2, floating // 200))
        if L["point"][1] is None:
            gv = gyy[(gyy > -999)]
            L["point"][1] = int(np.median(gv)) if gv.size else None
        out = dict(L, bbox=[bx0, bz0, bx1, bz1], area_draft=area, status="observed",
                   surface_share=share, buildings_count=sum(b["kind"] == "building" for b in bl),
                   linear_count=sum(b["kind"] == "linear" for b in bl),
                   buildings=sorted(bl, key=lambda b: -b["footprint"]),
                   underground=sorted(rec["underground"], key=lambda u: -u["columns"]),
                   block_entities=dict(rec["be"]), signs=rec["signs"][:40],
                   defects={"holes_inside_border_columns": holes, "floating_candidates": floating},
                   flat_free_share=round(flat_free, 3),
                   score={"1_geography": None, "2_canon": None, "3_roads": s3, "4_building": s4, "5_quality": s5,
                          "note": "3–5 — автоматическая прикидка по растру; 1–2 и итог 0–25 — по скриншотам (штаб/пользователь)"},
                   preview="img/survey/%s.png" % L["id"])
        im, sc, P = render(bx0, bz0, bx1, bz1, outline=bl if len(bl) < 400 else None)
        d = ImageDraw.Draw(im)
        X, Z = P(px, pz)
        d.ellipse([X - 5, Z - 5, X + 5, Z + 5], outline=(0, 255, 255), width=2)
        d.text((X + 7, Z - 7), L["ru"], fill=(0, 255, 255), font=font)
        im.quantize(96, method=Image.MEDIANCUT).save(os.path.join(docs, "img", "survey", L["id"] + ".png"), optimize=True)
        with open(os.path.join(docs, "survey", L["id"] + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(out, f, ensure_ascii=False, indent=1, default=_np)
        summary.append({"id": L["id"], "ru": L["ru"], "point": L["point"], "bbox": out["bbox"],
                        "buildings": sum(b["kind"] == "building" for b in bl), "linear": sum(b["kind"] == "linear" for b in bl),
                        "big_buildings": sum(1 for b in bl if b["footprint"] >= 400 and b["kind"] == "building"), "underground": len(rec["underground"]),
                        "command_blocks": rec["be"].get("command_block", 0), "chests": rec["be"].get("chest", 0) + rec["be"].get("trapped_chest", 0),
                        "holes": holes, "floating": floating, "score_auto_3_4_5": [s3, s4, s5]})

    # ---------------- мир целиком ----------------
    tot = {CLS_RU[c]: int(((cls == c) & inside).sum()) for c in CLS_RU}
    # край данных относительно контура: для каждой вершины — расстояние до ближайшей пустой колонки (по осям)
    edge = []
    for x, z in border["contour"]:
        c, r = x - ox, z - oz
        dist = {}
        for name, (dr, dc) in {"N": (-1, 0), "S": (1, 0), "W": (0, -1), "E": (0, 1)}.items():
            n = 0
            rr, cc = r, c
            while 0 <= rr < H and 0 <= cc < W and have[rr, cc] and n < 2000:
                rr += dr; cc += dc; n += 1
            dist[name] = n
        edge.append({"x": x, "z": z, "to_data_edge": dist})
    near_edge = [e for e in edge if min(e["to_data_edge"].values()) < 192]
    # дыры данных внутри контура, по чанкам
    hm = ((~have) & inside)
    hc = hm[:H - H % 16, :W - W % 16].reshape(H // 16, 16, W // 16, 16).any((1, 3))
    holes = []
    for comp in components(hc):
        r0, c0 = comp.min(0) * 16
        r1, c1 = comp.max(0) * 16 + 15
        holes.append({"bbox": [int(c0 + ox), int(r0 + oz), int(c1 + ox), int(r1 + oz)], "chunks": len(comp)})
    # 5 точек Кордона для сверки по F3: ровная земля (перепад ≤ 1 в клетке 4×4, не постройка, не вода)
    f3 = []
    cx, cz = -4118, 3486
    for dx, dz in ((0, 0), (160, 0), (-160, 0), (0, 160), (0, -160)):
        best = None
        for rr in range(0, 40, 2):
            for ex in range(-rr, rr + 1, 2):
                for ez in (-rr, rr):
                    for x, z in ((cx + dx + ex, cz + dz + ez), (cx + dx + ez, cz + dz + ex)):
                        r, c = z - oz, x - ox
                        if best is None and flat4[r // k, c // k] and cls[r, c] in (1, 6, 9) and M["art_up"][r, c] == 0:
                            best = (x, z)
            if best:
                break
        if best:
            x, z = best
            g = int(M["ground_y"][z - oz, x - ox])
            f3.append({"x": x, "z": z, "ground_y": g, "f3_feet_y": g + 1, "surface": str(names[M["top_id"][z - oz, x - ox]])})
    world = {"origin": [ox, oz], "size": [W, H], "classes_inside_border_columns": tot,
             "holes_inside_border": int(((~have) & inside).sum()),
             "buildings_total": len(blds), "buildings_inside_border": sum(b["inside_border"] for b in blds),
             "outside_locations": len(by["_outside"]["buildings"]),
             "holes": holes, "cordon_f3_points": f3,
             "contour_vertices_near_data_edge_lt192": near_edge, "locations": summary}
    with open(os.path.join(docs, "survey", "_world.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(world, f, ensure_ascii=False, indent=1, default=_np)

    # обзор классов 1 px = 8 блоков
    s = 8
    c8 = cls[::s, ::s]
    im = Image.fromarray(np.array([CLS_COL[i] for i in range(10)], np.uint8)[c8])
    # постройки поверх (максимум по клетке, чтобы мелкие не пропали)
    b8 = cell_any(bmask, s, 0.15)
    arr = np.array(im)
    arr[:b8.shape[0], :b8.shape[1]][b8] = CLS_COL[4]
    im = Image.fromarray(arr)
    d = ImageDraw.Draw(im)
    bp = [((x - ox) / s, (z - oz) / s) for x, z in border["contour"]]
    d.line(bp + [bp[0]], fill=(255, 255, 255), width=2)
    for L in summary:
        X, Z = (L["point"][0] - ox) / s, (L["point"][2] - oz) / s
        d.ellipse([X - 3, Z - 3, X + 3, Z + 3], outline=(0, 255, 255), width=2)
        d.text((X + 5, Z - 6), L["ru"], fill=(255, 255, 255), font=font)
    for gx in range(math.ceil(ox / 1024) * 1024, ox + W, 1024):
        d.text(((gx - ox) / s + 2, 2), str(gx), fill=(255, 255, 0), font=font)
    for gz in range(math.ceil(oz / 1024) * 1024, oz + H, 1024):
        d.text((2, (gz - oz) / s + 2), str(gz), fill=(255, 255, 0), font=font)
    y = 20
    for c in CLS_RU:
        d.rectangle([W / s - 190, y, W / s - 176, y + 12], fill=CLS_COL[c])
        d.text((W / s - 170, y - 1), CLS_RU[c], fill=(255, 255, 255), font=font)
        y += 16
    im.quantize(64, method=Image.MEDIANCUT).save(os.path.join(docs, "img", "survey", "_classes.png"), optimize=True)
    print(json.dumps({k: v for k, v in world.items() if k != "locations" and k != "contour_vertices_near_data_edge_lt192"}, ensure_ascii=False, default=_np))
    print("вершин контура ближе 192 к краю данных:", len(near_edge))
    for L in summary:
        print(L)


if __name__ == "__main__":
    main()
