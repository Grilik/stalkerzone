"""Э1-06, шаг 2: кандидаты якорей «объект оригинала ↔ постройка TheZone» для каждой локации.

  python -I tools/atlas/anchors.py [docs/thezone/anchor-candidates.json]

Для локации из съёмки Э1-04 (docs/thezone/survey/<loc>.json, постройки) и уровня(ей) Anomaly из Э1-05
(atlas/original/<level>.json: базы, торговцы, заставы, места) перебирает подобие без поворота
(масштаб 0,6…1,4, сдвиг по сетке 8 → 2 блока) и выбирает то, при котором больше всего объектов попадает
в постройки. Совпавшие пары, разнесённые по площади, — кандидаты якорей (не более 6). Это только
кандидаты: настоящие координаты TheZone даёт пользователь по F3, подобие считает fit.py.
"""
import json, math, os, sys
import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ORIG = os.path.join(ROOT, "src/main/resources/data/stalkerzone/atlas/original")
SURV = os.path.join(ROOT, "docs/thezone/survey")
LEVELS = {  # локация съёмки → уровни Anomaly (docs/thezone/06-original-markup.md)
    "cordon": ["l01_escape"], "garbage": ["l02_garbage"], "agroprom": ["l03_agroprom"],
    "dark_valley": ["l04_darkvalley"], "swamps": ["k00_marsh"], "bar": ["l05_bar"],
    "army_warehouses": ["l07_military"], "yantar": ["l08_yantar"], "dead_city": ["l09_deadcity"],
    "limansk": ["l10_limansk"], "radar": ["l10_radar"], "hospital": ["l11_hospital"],
    "chnpp": ["l12_stancia", "l12_stancia_2"], "generators": ["l13_generators"], "dark_hollow": ["k01_darkscape"],
    "zaton": ["zaton"], "yanov": ["jupiter"],
    "pripyat_north": ["l11_pripyat", "pripyat"], "pripyat_south": ["l11_pripyat", "pripyat"],
    "pripyat_center": ["l11_pripyat", "pripyat"], "pripyat_east": ["l11_pripyat", "pripyat"],
    "pripyat_west": ["l11_pripyat", "pripyat"],
}
KINDS = ("base", "trader", "outpost", "landmark")
SIGMA, HIT = 30.0, 25.0


def score(px, pz, R):
    """px, pz: (..., N); R: (M, 4). Каждая постройка засчитывается один раз — по ближайшему объекту,
    иначе сжатие уровня в одну точку «выигрывает». → (...)"""
    dx = np.maximum(np.maximum(R[:, 0] - px[..., None], 0), px[..., None] - R[:, 2])
    dz = np.maximum(np.maximum(R[:, 1] - pz[..., None], 0), pz[..., None] - R[:, 3])
    w = np.exp(-(dx * dx + dz * dz) / SIGMA ** 2)  # (..., N, M)
    return w.max(-2).sum(-1)


def search(O, R, centre, half):
    best = (-1, None)
    ox, oz = O[:, 0], O[:, 1]
    cx, cz = ox.mean(), oz.mean()
    for s in np.arange(0.6, 1.41, 0.05):
        g = np.arange(-half, half + 1, 8.0)
        tx = centre[0] - s * cx + g[:, None, None]
        tz = centre[1] - s * cz + g[None, :, None]
        sc = score(s * ox + tx, s * oz + tz, R)
        i, j = np.unravel_index(sc.argmax(), sc.shape)
        if sc[i, j] > best[0]:
            best = (sc[i, j], (s, float(tx[i, 0, 0]), float(tz[0, j, 0])))
    s0, tx0, tz0 = best[1]
    for s in np.arange(s0 - 0.05, s0 + 0.051, 0.01):
        g = np.arange(-16, 17, 2.0)
        tx = tx0 + g[:, None, None]; tz = tz0 + g[None, :, None]
        sc = score(s * ox + tx, s * oz + tz, R)
        i, j = np.unravel_index(sc.argmax(), sc.shape)
        if sc[i, j] > best[0]:
            best = (sc[i, j], (float(s), float(tx[i, 0, 0]), float(tz[0, j, 0])))
    return best


def spread(c, k=6):
    out = [max(c, key=lambda a: a["building_area"])]
    while len(out) < min(k, len(c)):
        out.append(max((a for a in c if a not in out),
                       key=lambda a: min(math.dist(a["thezone_guess"][::2], b["thezone_guess"][::2]) for b in out)))
    return out


def run(loc):
    S = json.load(open(os.path.join(SURV, loc + ".json"), encoding="utf-8"))
    B = [b for b in S["buildings"] if b.get("inside_border", True)]
    if not B:
        return None
    R = np.array([b["bbox"] for b in B], float)
    bx = S["bbox"]
    centre = ((bx[0] + bx[2]) / 2, (bx[1] + bx[3]) / 2)
    half = max(bx[2] - bx[0], bx[3] - bx[1]) / 2
    res = []
    for lv in LEVELS.get(loc, []):
        objs = [o for o in json.load(open(os.path.join(ORIG, lv + ".json"), encoding="utf-8"))["objects"] if o["kind"] in KINDS]
        if len(objs) < 3:
            continue
        O = np.array([[o["original"][0], o["original"][2]] for o in objs], float)
        score, (s, tx, tz) = search(O, R, centre, half)
        P = np.c_[s * O[:, 0] + tx, s * O[:, 1] + tz]
        cand = []
        for o, (x, z) in zip(objs, P):
            d = np.sqrt(np.maximum(np.maximum(R[:, 0] - x, 0), x - R[:, 2]) ** 2 +
                        np.maximum(np.maximum(R[:, 1] - z, 0), z - R[:, 3]) ** 2)
            k = int(d.argmin())
            if d[k] <= HIT:
                b = B[k]
                cand.append({"original_id": o["id"], "kind": o["kind"], "faction": o.get("faction"),
                             "original": o["original"], "thezone_guess": [round(float(x)), b["ground_y"] + 1, round(float(z))],
                             "building_bbox": b["bbox"], "building_area": (b["bbox"][2] - b["bbox"][0]) * (b["bbox"][3] - b["bbox"][1]),
                             "building_top_y": b["top_y"], "dist": round(float(d[k]), 1)})
        # одна постройка — один якорь
        seen, uniq = set(), []
        for a in sorted(cand, key=lambda a: a["dist"]):
            if tuple(a["building_bbox"]) not in seen:
                seen.add(tuple(a["building_bbox"])); uniq.append(a)
        res.append({"level": lv, "score": round(float(score), 2), "objects": len(objs), "matched": len(uniq),
                    "guess": {"scale": round(s, 3), "angle": 0, "dx": round(tx, 1), "dz": round(tz, 1)},
                    "anchors": spread(uniq) if uniq else []})
    res.sort(key=lambda r: -r["matched"] - r["score"] / 100)
    return {"location": loc, "ru": S.get("ru"), "point": S["point"], "buildings": len(B), "fits": res}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs/thezone/anchor-candidates.json")
    allr = []
    for loc in LEVELS:
        r = run(loc)
        if r:
            allr.append(r)
            f = r["fits"][0] if r["fits"] else None
            print("%-16s построек %3d  %s" % (loc, r["buildings"], "%s: совпало %d из %d, масштаб %.2f, сдвиг (%.0f, %.0f)" % (
                f["level"], f["matched"], f["objects"], f["guess"]["scale"], f["guess"]["dx"], f["guess"]["dz"]) if f else "—"))
    json.dump(allr, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
