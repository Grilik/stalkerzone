#!/usr/bin/env python3
"""Э1-16: особенности территорий атласа (kind=zone) по съёмке Э1-04 — только чтение.

  python -I tools/atlas/zone_features.py <mosaic.npz> <папка атласа> [id локации]

Считает по каждой зоне локации измеряемые особенности для potential{} (TerritoryPotential.derive):
  forest          доля леса (класс 2 съёмки: листва, стволы, sapling), 0..1
  buildingArea    колонок с постройками (класс 4: >= 3 искусственных блоков над землёй)
  undergroundArea колонок с подземными объёмами (art_down >= 3, как в survey_analyze.py)
  roads           дорог атласа, чья ломаная проходит через зону
  lairs, fields   логов и аномальных полей атласа внутри зоны
  area            колонок в зоне (для справки)

Мозаика — вне git (world/survey/mosaic.npz, tools/thezone/survey_raster.py + survey_analyze.py).
Классы поверхности и маска многоугольника берутся из survey_analyze.py, чтобы оценка совпадала со съёмкой.
Инструмент печатает JSON и ничего не записывает: значения features переносятся в атлас вручную
(правка атласа — по правилам Э1-07), potential{} пересчитывается TerritoryPotential.derive и
проверяется TerritoryPotentialTest.
"""
import json
import os
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "thezone"))
import survey_analyze as S  # noqa: E402


def inside(poly, x, z):
    """Точка в многоугольнике (лучевая проба, границы включительно не проверяем)."""
    c = False
    n = len(poly)
    for i in range(n):
        ax, az = poly[i]
        bx, bz = poly[(i + 1) % n]
        if (az > z) != (bz > z) and x < (bx - ax) * (z - az) / (bz - az) + ax:
            c = not c
    return c


def road_through(poly, line):
    return any(inside(poly, p[0], p[-1]) for p in line)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    mosaic, atlas_dir = sys.argv[1], sys.argv[2]
    only = sys.argv[3] if len(sys.argv) > 3 else None
    M = np.load(mosaic)
    cls = S.classify(M)
    ox, oz = (int(v) for v in M["origin"])
    down = M["art_down"]

    out = {}
    for name in sorted(os.listdir(os.path.join(atlas_dir, "locations"))):
        if not name.endswith(".json"):
            continue
        loc = json.load(open(os.path.join(atlas_dir, "locations", name), encoding="utf-8"))
        if only and loc.get("id") != only:
            continue
        objects = loc.get("objects", [])
        roads = loc.get("roads", [])
        for o in objects:
            if o.get("kind") != "zone":
                continue
            poly = o["area"]
            xs = [p[0] for p in poly]
            zs = [p[1] for p in poly]
            x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
            c0, c1, r0, r1 = x0 - ox, x1 - ox + 1, z0 - oz, z1 - oz + 1
            mask = S.poly_mask(poly, x0, z0, c1 - c0, r1 - r0)
            sub = cls[r0:r1, c0:c1]
            area = int(mask.sum())
            if area == 0:
                raise SystemExit("%s: пустая зона (проверь area и origin мозаики)" % o["id"])
            lairs = sum(1 for t in objects if t.get("kind") == "lair"
                        and inside(poly, t["pos"][0], t["pos"][-1]))
            fields = sum(1 for t in objects if t.get("kind") == "field"
                         and inside(poly, t["pos"][0], t["pos"][-1]))
            out[o["id"]] = {
                "type": o.get("type"),
                "area": area,
                "forest": round(float((sub == 2)[mask].sum()) / area, 4),
                "buildingArea": int((sub == 4)[mask].sum()),
                "undergroundArea": int(((down[r0:r1, c0:c1] >= 3) & mask).sum()),
                "roads": sum(1 for r in roads if road_through(poly, r.get("line", []))),
                "lairs": lairs,
                "fields": fields,
            }
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
