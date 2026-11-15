"""Э1-06: подобие «уровень → TheZone» по якорям (без отражения), наименьшие квадраты.

  python -I tools/atlas/fit.py <loc>          — якоря из atlas/locations/<loc>.json (поле anchors)
  python -I tools/atlas/fit.py --file a.json  — список якорей [{"id", "original": [x,y,z], "thezone": [x,y,z]}]
Печатает масштаб, угол, сдвиг, rms, max, loo.max (ошибка каждого якоря при подгонке без него).
Из Python: fit(pairs) -> dict(transform, residual); apply(t, [x, y, z]) -> [x, y, z].
Плоскость XZ — подобие; высота — отдельный сдвиг dy (медиана разностей).
"""
import json, math, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
LOC = os.path.join(ROOT, "src/main/resources/data/stalkerzone/atlas/locations")
RMS_MAX, LOO_MAX = 8.0, 16.0


def _similarity(P, Q):
    """P, Q — списки (x, z). Возвращает a, b, tx, tz: q = [[a, -b], [b, a]] p + t (определитель a²+b² > 0)."""
    n = len(P)
    if n < 2:
        raise ValueError("нужно ≥ 2 якорей")
    px = sum(p[0] for p in P) / n; pz = sum(p[1] for p in P) / n
    qx = sum(q[0] for q in Q) / n; qz = sum(q[1] for q in Q) / n
    sxx = sab = sba = 0.0
    for (x, z), (u, v) in zip(P, Q):
        x -= px; z -= pz; u -= qx; v -= qz
        sxx += x * x + z * z
        sab += x * u + z * v
        sba += x * v - z * u
    if sxx == 0:
        raise ValueError("якоря совпадают")
    a, b = sab / sxx, sba / sxx
    return a, b, qx - (a * px - b * pz), qz - (b * px + a * pz)


def apply(t, p):
    a, b = t["scale"] * math.cos(math.radians(t["angle"])), t["scale"] * math.sin(math.radians(t["angle"]))
    x, y, z = p
    return [a * x - b * z + t["dx"], y + t["dy"], b * x + a * z + t["dz"]]


def _t(a, b, tx, tz, dy):
    return {"kind": "similarity", "scale": round(math.hypot(a, b), 5), "angle": round(math.degrees(math.atan2(b, a)), 3),
            "dx": round(tx, 2), "dy": round(dy, 2), "dz": round(tz, 2)}


def fit(pairs):
    for p in pairs:
        if len(p["original"]) != 3 or len(p["thezone"]) != 3:
            raise ValueError("якорь %s: нужны три координаты" % p.get("id"))
    P = [(p["original"][0], p["original"][2]) for p in pairs]
    Q = [(p["thezone"][0], p["thezone"][2]) for p in pairs]
    a, b, tx, tz = _similarity(P, Q)
    dys = sorted(p["thezone"][1] - p["original"][1] for p in pairs)
    t = _t(a, b, tx, tz, dys[len(dys) // 2])
    err = [math.hypot(*(u - w for u, w in zip(apply(t, [x, 0, z])[::2], q))) for (x, z), q in zip(P, Q)]
    loo = []
    if len(pairs) >= 3:
        for i in range(len(pairs)):
            a2, b2, tx2, tz2 = _similarity(P[:i] + P[i + 1:], Q[:i] + Q[i + 1:])
            u = a2 * P[i][0] - b2 * P[i][1] + tx2; v = b2 * P[i][0] + a2 * P[i][1] + tz2
            loo.append(math.hypot(u - Q[i][0], v - Q[i][1]))
    rms = math.sqrt(sum(e * e for e in err) / len(err))
    res = {"rms": round(rms, 2), "max": round(max(err), 2), "loo": {"max": round(max(loo), 2) if loo else None}}
    per = [{"id": p.get("id"), "err": round(e, 2), "loo": round(l, 2) if loo else None}
           for p, e, l in zip(pairs, err, loo or [None] * len(err))]
    ok = len(pairs) >= 5 and rms <= RMS_MAX and loo and max(loo) <= LOO_MAX
    return {"transform": t, "residual": res, "per_anchor": per, "ok": bool(ok)}


def main():
    if sys.argv[1] == "--file":
        pairs = json.load(open(sys.argv[2], encoding="utf-8"))
    else:
        pairs = json.load(open(os.path.join(LOC, sys.argv[1] + ".json"), encoding="utf-8"))["anchors"]
    r = fit(pairs)
    t = r["transform"]
    print("масштаб %.4f, угол %.2f°, сдвиг (%.1f, %.1f, %.1f)" % (t["scale"], t["angle"], t["dx"], t["dy"], t["dz"]))
    for p in r["per_anchor"]:
        print("  %-28s ошибка %6.2f  без него %s" % (p["id"], p["err"], p["loo"]))
    print("rms %.2f, max %.2f, loo.max %s — %s" % (r["residual"]["rms"], r["residual"]["max"], r["residual"]["loo"]["max"],
          "сходится" if r["ok"] else "НЕ сходится (нужно ≥5 якорей, rms ≤ 8, loo.max ≤ 16)"))


if __name__ == "__main__":
    main()
