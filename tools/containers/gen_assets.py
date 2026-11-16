#!/usr/bin/env python3
"""Э1-17: ресурсы контейнеров мода по ContainerKind.java — текстуры (из preview.py), модели, состояния блоков,
модели предметов, названия, таблицы выпадения блоков и таблицы добычи по семейству и месту.

  python -I tools/containers/gen_assets.py

Типы берутся из src/main/java/ru/stalkerzone/container/ContainerKind.java (один источник).
Добыча: хлам, детали, патроны, медицина, консервы (еда только привозная — товар «Еда», ванильной еды нет),
рубли — физические и редко. Генерируется в игре один раз при первом открытии и учитывается в Ledger.worldLoot.
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/stalkerzone")
DATA = os.path.join(ROOT, "src/main/resources/data/stalkerzone")


def kinds():
    src = open(os.path.join(ROOT, "src/main/java/ru/stalkerzone/container/ContainerKind.java"), encoding="utf-8").read()
    out = []
    for m in re.finditer(r'^\s+[A-Z_]+\("(\w+)", "(\w+)", "([^"]+)", "([^"]+)", Family\.(\w+), (\d+), (\d+), (\d+), (\d+), (\d+)\)', src, re.M):
        i, code, ru, en, fam, slots, wt, w, h, d = m.groups()
        out.append(dict(id=i, code=code, ru=ru, en=en, family=fam.lower(), slots=int(slots), weight=int(wt), w=int(w), h=int(h), d=int(d)))
    return out


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


def model(k):
    w, h, d = k["w"], k["h"], k["d"]
    a, b = (16 - w) / 2, (16 - d) / 2
    t = "stalkerzone:block/container/" + k["id"]
    # грань по центру текстуры (как в превью); выше блока — текстура растягивается на 2 блока
    fu = [a, 0, 16 - a, 16] if h > 16 else [a, (16 - h) / 2, 16 - a, (16 + h) / 2]
    su = [b, 0, 16 - b, 16] if h > 16 else [b, (16 - h) / 2, 16 - b, (16 + h) / 2]
    tu = [a, b, 16 - a, 16 - b]
    return {"parent": "minecraft:block/block",
            "textures": {"front": t + "_front", "side": t + "_side", "top": t + "_top", "particle": t + "_side"},
            "elements": [{"from": [a, 0, b], "to": [16 - a, h, 16 - b],
                          "faces": {"north": {"uv": fu, "texture": "#front"}, "south": {"uv": fu, "texture": "#side"},
                                    "east": {"uv": su, "texture": "#side"}, "west": {"uv": su, "texture": "#side"},
                                    "up": {"uv": tu, "texture": "#top"}, "down": {"uv": tu, "texture": "#top"}}}],
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2 if h > 16 else 0, 0], "scale": [0.42 if h > 16 else 0.625] * 3}}}


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstate(k):
    v = {}
    for f, r in ROT.items():
        m = {"model": "stalkerzone:block/container/" + k["id"]}
        if r:
            m["y"] = r
        if k["h"] > 16:
            v["facing=%s,half=lower" % f] = m
            v["facing=%s,half=upper" % f] = {"model": "stalkerzone:block/container/empty"}
        else:
            v["facing=" + f] = m
    return {"variants": v}


# ---------------------------------------------------------------- добыча
JUNK = [("minecraft:iron_nugget", 1, 4), ("minecraft:string", 1, 3), ("minecraft:paper", 1, 4), ("minecraft:glass_bottle", 1, 2),
        ("minecraft:bone", 1, 2), ("minecraft:stick", 1, 4), ("minecraft:leather", 1, 2), ("minecraft:coal", 1, 3),
        ("minecraft:flint", 1, 2), ("minecraft:bowl", 1, 1), ("minecraft:rotten_flesh", 1, 2)]
SCRAP = [("minecraft:iron_nugget", 2, 8), ("minecraft:chain", 1, 2), ("minecraft:copper_ingot", 1, 3), ("minecraft:iron_ingot", 1, 1),
         ("minecraft:redstone", 1, 4), ("minecraft:string", 1, 2)]
G = "stalkerzone:good_"

# семейство -> (бросков min, max, [(предмет, мин, макс, вес)])
BASE = {
    "wood": (2, 4, [("junk", 0, 0, 40), (G + "parts", 1, 2, 12), (G + "materials", 1, 2, 10), (G + "food", 1, 2, 8),
                    (G + "ammo", 2, 8, 6), (G + "meds", 1, 1, 3), ("stalkerzone:ruble", 5, 40, 2), ("empty", 0, 0, 30)]),
    "metal": (2, 4, [("scrap", 0, 0, 45), (G + "parts", 1, 3, 20), (G + "materials", 1, 2, 8), (G + "ammo", 2, 6, 4),
                     ("stalkerzone:ruble", 5, 30, 2), ("empty", 0, 0, 25)]),
    "cabinet": (1, 3, [("junk", 0, 0, 35), ("minecraft:book", 1, 1, 5), (G + "meds", 1, 1, 8), (G + "food", 1, 1, 6),
                       (G + "parts", 1, 1, 5), ("stalkerzone:ruble", 10, 60, 4), ("empty", 0, 0, 35)]),
}
# место -> множители весов (по предмету; прочее 1)
PLACE = {
    "home": {G + "food": 1.6, G + "meds": 1.5, "junk": 1.2, "stalkerzone:ruble": 1.3, G + "ammo": 0.5},
    "storage": {G + "parts": 1.6, G + "materials": 1.8, G + "food": 1.3, "scrap": 1.2, "empty": 0.7},
    "base": {G + "ammo": 2.5, G + "meds": 2.0, G + "food": 1.2, "junk": 0.6, "scrap": 0.7},
    "field": {"empty": 1.4},
}
ZINC = (1, 2, [(G + "ammo", 10, 30, 70), (G + "ammo_pro", 5, 20, 25), ("empty", 0, 0, 5)])
SAFE = (1, 3, [("stalkerzone:ruble", 50, 400, 30), ("stalkerzone:ruble_100", 1, 3, 8), (G + "meds", 1, 2, 12), (G + "ammo_pro", 5, 15, 10),
               ("minecraft:paper", 1, 4, 15), ("minecraft:book", 1, 1, 5), ("empty", 0, 0, 20)])


def entry(name, lo, hi, weight):
    if name == "empty":
        return {"type": "minecraft:empty", "weight": weight}
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi > 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def table(spec, mult=None):
    lo, hi, rows = spec
    ents = []
    for name, a, b, wt in rows:
        wt = max(1, round(wt * (mult or {}).get(name, 1)))
        if name in ("junk", "scrap"):  # хлам — делится на предметы поровну
            lst = JUNK if name == "junk" else SCRAP
            for it, x, y in lst:
                ents.append(entry(it, x, y, max(1, round(wt / len(lst) * 2))))
        else:
            ents.append(entry(name, a, b, wt * 2))
    return {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": ents}]}


def main():
    import preview  # текстуры — те же функции, что у превью-листа
    ks = kinds()
    assert len(ks) == 16, "типов %d, ждали 16" % len(ks)
    by_code = {v[0]: v for v in preview.variants()}
    tex = os.path.join(ASSETS, "textures/block/container")
    os.makedirs(tex, exist_ok=True)
    ru = json.load(open(os.path.join(ASSETS, "lang/ru_ru.json"), encoding="utf-8"))
    en = json.load(open(os.path.join(ASSETS, "lang/en_us.json"), encoding="utf-8"))
    dump(os.path.join(ASSETS, "models/block/container/empty.json"),
         {"textures": {"particle": "stalkerzone:block/container/locker_side"}, "elements": []})
    for k in ks:
        code, name, shape, fr, sd, tp, cap = by_code[k["code"]]
        assert shape == (k["w"], k["h"], k["d"]), (k["id"], shape)
        for nm, t in (("front", fr), ("side", sd), ("top", tp)):
            t.save(os.path.join(tex, "%s_%s.png" % (k["id"], nm)))
        dump(os.path.join(ASSETS, "models/block/container/%s.json" % k["id"]), model(k))
        dump(os.path.join(ASSETS, "blockstates/%s.json" % k["id"]), blockstate(k))
        dump(os.path.join(ASSETS, "models/item/%s.json" % k["id"]), {"parent": "stalkerzone:block/container/" + k["id"]})
        ru["block.stalkerzone." + k["id"]] = k["ru"]
        en["block.stalkerzone." + k["id"]] = k["en"]
        dump(os.path.join(DATA, "loot_tables/blocks/%s.json" % k["id"]),
             {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "stalkerzone:" + k["id"]}],
                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for lang, d in (("ru_ru", ru), ("en_us", en)):
        dump(os.path.join(ASSETS, "lang/%s.json" % lang), d)
    n = 0
    for fam in ("wood", "metal", "cabinet"):
        for place, mult in PLACE.items():
            dump(os.path.join(DATA, "loot_tables/containers/%s_%s.json" % (fam, place)), table(BASE[fam], mult)); n += 1
    dump(os.path.join(DATA, "loot_tables/containers/zinc.json"), table(ZINC)); n += 1
    dump(os.path.join(DATA, "loot_tables/containers/safe.json"), table(SAFE)); n += 1
    print("типов:", len(ks), "; таблиц добычи:", n)


if __name__ == "__main__":
    main()
