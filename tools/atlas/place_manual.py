"""Э1-06, решение пользователя 15.11: локация не 1:1 — места с F3 ставятся вручную, остальное — грубым подобием.

  python -I tools/atlas/place_manual.py <loc> <якоря.json> <рельеф mosaic.npz> [--old-offset DX DY DZ]

* якоря.json — как docs/thezone/anchors/<loc>.json: [{id, original, thezone, obj: [id объектов атласа], new?: {...}}];
  подобие (масштаб, поворот, сдвиг) считает fit.py по всем якорям;
* объект из obj якоря сдвигается целиком (pos, points, site) так, чтобы опорная точка якоря легла в F3;
  pos.y = F3 y; точки под землёй (ниже pos оригинала больше чем на 3) — с той же разницей высот от F3;
* остальные координаты (логова, поля, стоянки, дороги, выходы) — подобием; y — по рельефу (земля + 1);
  при постановке в мир точка ещё подгоняется к рельефу (Э1-14/Э2);
* --old-offset: координаты в файле атласа заданы в старой системе «Кордон 1:1» = оригинал + (DX, DY, DZ);
* все объекты — status observed (невязка вне порога), поле placement: f3 | similarity.
"""
import json, math, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fit  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
ATLAS = os.path.join(ROOT, "src/main/resources/data/stalkerzone/atlas")


def dumps(o, ind=0):
    """JSON как в атласе: отступ 2, числовые массивы (и массивы точек) — в одну строку."""
    flat = lambda v: isinstance(v, (int, float)) or (isinstance(v, list) and all(isinstance(x, (int, float)) for x in v))
    sp = "  " * (ind + 1)
    if isinstance(o, dict):
        if not o:
            return "{}"
        return "{\n" + ",\n".join(sp + json.dumps(k, ensure_ascii=False) + ": " + dumps(v, ind + 1)
                                   for k, v in o.items()) + "\n" + "  " * ind + "}"
    if isinstance(o, list):
        if all(flat(v) for v in o):
            return json.dumps(o, ensure_ascii=False)
        return "[\n" + ",\n".join(sp + dumps(v, ind + 1) for v in o) + "\n" + "  " * ind + "]"
    return json.dumps(o, ensure_ascii=False)


def main():
    loc, apath, mpath = sys.argv[1:4]
    off = [0.0, 0.0, 0.0]
    if "--old-offset" in sys.argv:
        i = sys.argv.index("--old-offset")
        off = [float(v) for v in sys.argv[i + 1:i + 4]]
    lpath = os.path.join(ATLAS, "locations", loc + ".json")
    L = json.load(open(lpath, encoding="utf-8"))
    A = json.load(open(apath, encoding="utf-8"))
    r = fit.fit(A)
    T = r["transform"]
    M = np.load(mpath)
    ox, oz = (int(v) for v in M["origin"])
    gy = M["ground_y"]

    def ground(x, z):
        v = int(gy[int(round(z)) - oz, int(round(x)) - ox])
        return None if v == -999 else v + 1

    def orig(p):  # старая система -> оригинал
        return [p[0] - off[0], (p[1] - off[1]) if len(p) == 3 else 0.0, p[-1] - off[2]]

    def sim(p, delta=None, a=None, p0y=None):
        q = fit.apply(T, orig(p))
        if delta:
            q[0] += delta[0]; q[2] += delta[1]
        x, z = round(q[0]), round(q[2])
        if len(p) == 2:
            return [x, z]
        if a is not None and p0y is not None and p[1] < p0y - 3:  # под землёй (бункер): высота от F3
            return [x, round(a["thezone"][1] + orig(p)[1] - a["original"][1]), z]
        y = ground(x, z)
        return [x, y if y is not None else round(q[1]), z]

    by_obj = {}
    for a in A:
        for oid in a.get("obj", []):
            by_obj[oid] = a
    objs = []
    for o in L["objects"]:
        a = by_obj.get(o["id"])
        delta = None
        if a:
            q = fit.apply(T, a["original"])
            delta = (a["thezone"][0] - q[0], a["thezone"][2] - q[2])
        p0y = o["pos"][1] if len(o["pos"]) == 3 else None
        new = dict(o)
        new["pos"] = sim(o["pos"], delta, a, p0y)
        if "points" in o:
            new["points"] = {k: ([sim(p, delta, a, p0y) for p in v] if v and isinstance(v[0], list) else sim(v, delta, a, p0y))
                             for k, v in o["points"].items()}
        if "site" in o:
            b = o["site"]["bbox"]
            c = [sim([x, z], delta) for x in (b[0], b[2]) for z in (b[1], b[3])]
            new["site"] = dict(o["site"], bbox=[min(p[0] for p in c), min(p[1] for p in c), max(p[0] for p in c), max(p[1] for p in c)])
        new["status"] = "observed"
        new["placement"] = "f3" if a else "similarity"
        if a:
            new["source"] = o["source"] + "; место — по F3 пользователя (заход 1, 15.11): " + a["ru"]
        objs.append(new)
    for a in A:  # якоря без объекта атласа — новые места
        if a.get("new"):
            n = dict(a["new"]); n.setdefault("pos", list(a["thezone"]))
            n.update(status="observed", placement="f3")
            objs.append(n)
    L["objects"] = objs
    for k in ("spawn",):
        if k in L:
            L[k] = sim(L[k])
    for k in ("closedPassages",):
        if k in L:
            L[k] = [sim(p) for p in L[k]]
    if "exits" in L:
        L["exits"] = {k: sim(v) for k, v in L["exits"].items()}
    for rd in L.get("roads", []):
        rd["line"] = [sim(p) for p in rd["line"]]
    T2 = dict(T, frame="minecraft", source="original",
              note="грубый пересчёт оригинал -> TheZone для логов, полей и стоянок (ошибка 30–50 блоков); места с F3 — вручную")
    L["transform"] = T2
    L["anchors"] = [{"id": a["id"], "original": [a["original"][0], a["original"][2]], "thezone": [a["thezone"][0], a["thezone"][2]]} for a in A]
    L["residual"] = r["residual"]
    pts = [o["pos"] for o in objs if o["kind"] != "exit"]
    xs = [p[0] for p in pts]; zs = [p[-1] for p in pts]
    return L, (min(xs), min(zs), max(xs), max(zs)), r


if __name__ == "__main__":
    L, ext, r = main()
    print("подобие:", r["transform"], r["residual"], "объекты в", ext)
    out = sys.argv[-1] if sys.argv[-1].endswith(".out.json") else os.path.join(ATLAS, "locations", sys.argv[1] + ".json")
    open(out, "w", encoding="utf-8", newline="\n").write(dumps(L) + "\n")
