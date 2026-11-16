#!/usr/bin/env python3
"""Э1-17: ресурсы контейнеров мода по ContainerKind.java — текстуры (из preview.py), модели, состояния блоков,
модели предметов, названия, таблицы выпадения блоков. Добыча — data/stalkerzone/container_loot/loot.json (ContainerLoot).

  python -I tools/containers/gen_assets.py

Типы берутся из src/main/java/ru/stalkerzone/container/ContainerKind.java (один источник).
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
    print("типов:", len(ks))


if __name__ == "__main__":
    main()
