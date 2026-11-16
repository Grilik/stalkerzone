#!/usr/bin/env python3
"""Э1-17: план замены сундуков TheZone контейнерами мода (только чтение мира) -> правка для set_blocks.py.

  python -I tools/thezone/containers_plan.py <рабочая копия> <правка.json> [--sheet <png>]

Что меняется: все сундуки и сундуки-ловушки внутри контура атласа (world.json bounds). Снаружи — не трогаются.
Соответствие (решение пользователя 16.11):
  - таблица Bedrock chests/boxCrate → деревянные ящики, chests/boxScrap → металлические (на базе — иногда цинк);
  - без таблицы — по месту: жильё → шкафы (канцелярский; шкафчик, если сверху воздух; изредка сейф),
    склад → деревянные и металлические ящики, база → армейские ящики, цинки, шкафчики, поле → простые ящики;
  - вид A/B и вариант внутри семейства — случайно, но воспроизводимо (по координатам; у двойного сундука — одинаково);
  - предметы, положенные автором (60 сундуков), переносятся; тогда таблица добычи не ставится.
Место: жильё — кровать в радиусе 6 или ≥ 3 бытовых блока (горшки, печи, ковры, книжные полки, котлы, деревянные двери);
склад — ≥ 3 других сундуков в радиусе 4; база — у баз и блокпостов атласа (70 блоков) или у военных мест places.json (350).
"""
import json
import os
import random
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
import chests_survey as CS  # noqa: E402
import trial_copy as T  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ATLAS = os.path.join(ROOT, "src/main/resources/data/stalkerzone/atlas")
BE_ID = "stalkerzone:zone_container"
MILITARY = {"Military Warehouses", "Duga-3", "Clear Sky CAMP (Great Swamp)", "100 RADS BAR", "YANOV STATION"}

# семейство -> {место: [виды]} (виды — id из ContainerKind.java)
WOOD = {"base": ["army_crate", "army_crate_red", "marked_crate_ammo"],
        "storage": ["marked_crate_ammo", "marked_crate_store", "wood_crate", "wood_crate_old"],
        "home": ["wood_crate", "wood_crate_old"], "field": ["wood_crate", "wood_crate_old"]}
METAL = ["metal_box", "metal_box_rusty"]
ZINC = ["ammo_zinc", "ammo_zinc_green"]
LOCKER = ["locker", "locker_blue"]
FILE = ["file_cabinet", "file_cabinet_grey"]
SAFE = ["safe", "safe_old"]
SLOTS = {"ammo_zinc": 6, "ammo_zinc_green": 6, "safe": 9, "safe_old": 9, "metal_box": 27, "metal_box_rusty": 27,
         "locker": 27, "locker_blue": 27}  # прочие — 18
FAMILY = {k: "wood" for v in WOOD.values() for k in v}
FAMILY.update({k: "metal" for k in METAL}); FAMILY.update({k: "zinc" for k in ZINC}); FAMILY.update({k: "cabinet" for k in LOCKER + FILE})
FAMILY.update({k: "safe" for k in SAFE})


def inside(poly, x, z):
    c = False
    for i in range(len(poly)):
        (x1, z1), (x2, z2) = poly[i], poly[i - 1]
        if (z1 > z) != (z2 > z) and x < x1 + (z - z1) * (x2 - x1) / (z2 - z1):
            c = not c
    return c


class World:
    def __init__(self, work):
        self.r = os.path.join(work, "region"); self.cache = {}

    def blocks(self, cx, cz):
        k = (cx, cz)
        if k not in self.cache:
            if len(self.cache) > 4000:
                self.cache.clear()
            got = A.read_chunk(os.path.join(self.r, A.region_name(cx, cz)), cx, cz) if os.path.exists(os.path.join(self.r, A.region_name(cx, cz))) else None
            self.cache[k] = T._blocks(got[1]) if got else {}
        return self.cache[k]

    def get(self, x, y, z):
        arr = self.blocks(x >> 4, z >> 4).get(y >> 4)
        return "minecraft:air" if arr is None else arr[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]


HOME = ("flower_pot", "potted_", "furnace", "smoker", "_carpet", "bookshelf", "cauldron")


def place_of(w, pos, chests, bases):
    x, y, z = pos
    near = sum(1 for p in chests if p != pos and abs(p[0] - x) <= 4 and abs(p[1] - y) <= 2 and abs(p[2] - z) <= 4)
    beds, score, seen = 0, 0, set()
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            for dy in range(-2, 4):
                n = w.get(x + dx, y + dy, z + dz)
                if n.endswith("_bed]") or "_bed[" in n:
                    beds += 1
                b = n.split("[")[0]
                for h in HOME:
                    if h in b and (h, b) not in seen:
                        seen.add((h, b)); score += 1
                if b.endswith("_door") and "iron" not in b:
                    score += 0.25
    base = any((bx - x) ** 2 + (bz - z) ** 2 <= r * r for bx, bz, r in bases)
    home = beds > 0 or score >= 3
    if near >= 3:
        return "storage", base
    if home:
        return "home", base
    return ("base" if base else "field"), base


def partner(block, pos, chests):
    """Вторая половина двойного сундука (или сам pos)."""
    if "type=single" in block:
        return pos
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (pos[0] + dx, pos[1], pos[2] + dz)
        if q in chests:
            return min(pos, q)
    return pos


def choose(c, place, base, air_above, rnd):
    loot = (c["loot"] or "").lower()
    n_items = c["items"]
    if "boxcrate" in loot:
        kind = rnd.choice(WOOD[place])
    elif "boxscrap" in loot:
        kind = rnd.choice(ZINC) if base and rnd.random() < 0.2 else rnd.choice(METAL)
    elif place == "home":
        r = rnd.random()
        kind = rnd.choice(SAFE) if r < 0.03 else rnd.choice(LOCKER) if air_above and (base or r < 0.4) else rnd.choice(FILE)
    elif place == "storage":
        kind = rnd.choice(WOOD["storage"]) if rnd.random() < 0.6 else rnd.choice(METAL)
    elif place == "base":
        r = rnd.random()
        kind = rnd.choice(WOOD["base"]) if r < 0.5 else rnd.choice(ZINC) if r < 0.7 else \
            rnd.choice(LOCKER) if air_above and r < 0.9 else rnd.choice(METAL)
    else:
        kind = rnd.choice(WOOD["field"]) if rnd.random() < 0.6 else rnd.choice(METAL)
    if n_items > SLOTS.get(kind, 18):  # содержимое автора должно влезть
        kind = rnd.choice(METAL)
    fam = FAMILY[kind]
    table = "stalkerzone:containers/" + (fam if fam in ("zinc", "safe") else fam + "_" + place)
    return kind, table


def main():
    work, out = sys.argv[1], sys.argv[2]
    poly = json.load(open(os.path.join(ATLAS, "world.json"), encoding="utf-8"))["bounds"]
    bases = []
    cordon = json.load(open(os.path.join(ATLAS, "locations/cordon.json"), encoding="utf-8"))
    for o in cordon["objects"]:
        if o["kind"] in ("base", "checkpoint", "outpost"):
            bases.append((o["pos"][0], o["pos"][-1], 70))
    for p in json.load(open(os.path.join(ROOT, "docs/thezone/places.json"), encoding="utf-8"))["places"]:
        if p["name"] in MILITARY:
            bases.append((p["x"], p["z"], 350))
    tmp = out + ".survey.json"
    sys.argv = ["", work, tmp]
    CS.main()
    allc = json.load(open(tmp, encoding="utf-8"))
    os.remove(tmp)
    cs = [c for c in allc if inside(poly, c["pos"][0], c["pos"][2])]
    chests = {tuple(c["pos"]) for c in cs}
    w = World(work)
    blocks, stat, places = [], Counter(), Counter()
    cs.sort(key=lambda c: (c["pos"][0] >> 9, c["pos"][2] >> 9, c["pos"][0] >> 4, c["pos"][2] >> 4))
    for c in cs:
        pos = tuple(c["pos"])
        facing = c["block"].split("facing=")[1].split(",")[0].rstrip("]")
        place, base = place_of(w, pos, chests, bases)
        up = (pos[0], pos[1] + 1, pos[2])
        air_above = w.get(*up).split("[")[0] == "minecraft:air"
        key = partner(c["block"], pos, chests)
        rnd = random.Random("%d,%d,%d" % key)
        kind, table = choose(c, place, base, air_above, rnd)
        frm = c["block"].split("[")[0]
        if kind in LOCKER:
            blocks.append({"pos": list(pos), "from": frm, "to": "stalkerzone:%s[facing=%s,half=lower]" % (kind, facing),
                           "be": {"id": BE_ID, "LootTable": table, "keepItems": True}})
            blocks.append({"pos": list(up), "from": "minecraft:air", "to": "stalkerzone:%s[facing=%s,half=upper]" % (kind, facing)})
        else:
            blocks.append({"pos": list(pos), "from": frm, "to": "stalkerzone:%s[facing=%s]" % (kind, facing),
                           "be": {"id": BE_ID, "LootTable": table, "keepItems": True}})
        stat[kind] += 1; places[place] += 1
    spec = {"what": "Э1-17: сундуки → контейнеры мода (%d сундуков внутри контура, %d блоков)" % (len(cs), len(blocks)),
            "why": "решение пользователя 16.11: таблицы добычи Bedrock (boxCrate/boxScrap) в Java не грузятся, сундуки пустые; "
                   "контейнеры как в S.T.A.L.K.E.R. с добычей мода по типу и месту",
            "blocks": blocks}
    with open(out, "w", encoding="utf-8") as f:
        json.dump(spec, f, ensure_ascii=False, separators=(",", ":"))
    print("сундуков внутри контура:", len(cs), "; снаружи не трогаются:", len(allc) - len(cs))
    print("места:", dict(places))
    print("виды:", dict(stat.most_common()))
    print("правок блоков:", len(blocks), "->", out)


if __name__ == "__main__":
    main()
