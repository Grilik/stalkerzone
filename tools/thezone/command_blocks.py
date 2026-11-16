#!/usr/bin/env python3
"""Э1-10: командные блоки TheZone (только чтение) -> правка для set_blocks.py.

  python -I tools/thezone/command_blocks.py <рабочая копия> <правка.json> <список.json>

Решения пользователя 16.11: скрытый блок (все шесть соседей — твёрдые или командные) — камень (ниже y 0 — глубинный
сланец), не ценные блоки механизмов автора; блок на виду — блок стены (самый частый твёрдый сосед, кроме ценных);
воздух — только если блок открыт со всех сторон (и цепочка не упирается в твёрдое).
Соседи-кандидаты: полные непрозрачные блоки без блок-сущности (не плиты, ступени, стекло, двери, растения, жидкости,
не командные блоки). Зал телепортов на Генераторах (0, 34…41, 0) сохраняется — меняются только его командные блоки.
Блоки вызова жителей (/summon Villager: Сидорович, бармен, учёный) тоже убираются — жители восстановлены (решение 16.11).
список.json — [{pos, kind, to, open, loc, cmd}] для разбивки и рендеров.
"""
import json
import os
import struct
import sys
import zlib
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
import containers_plan as P  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CMD = ("minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block")
NOT_SOLID = ("slab", "stairs", "glass", "pane", "door", "fence", "wall", "torch", "sign", "button", "lever", "carpet",
             "leaves", "water", "lava", "air", "command", "flower", "grass", "fern", "vine", "rail", "pressure", "ladder",
             "bars", "chain", "lantern", "bed", "banner", "head", "skull", "pot", "snow", "web", "sapling", "mushroom",
             "redstone", "repeater", "comparator", "hopper", "chest", "barrel", "furnace", "piston", "observer",
             "dispenser", "dropper", "sculk", "candle", "coral", "kelp", "seagrass", "light", "barrier", "structure",
             "jigsaw", "spawner", "beacon", "conduit", "bell", "anvil", "cauldron", "lectern", "campfire", "trapdoor",
             "scaffolding", "cake", "plate", "dirt_path", "farmland", "soul_sand", "honey", "slime", "ice", "shulker",
             "frame", "rod", "pickle", "egg", "bamboo", "cactus", "sugar", "crop", "wheat", "stem", "nether_wart", "tripwire",
             "string", "end_portal", "portal", "fire", "bubble", "moss_carpet", "pointed", "amethyst_cluster", "bud")


VALUABLE = ("gold", "diamond", "emerald", "iron_block", "glowstone", "sea_lantern", "redstone_block", "lapis_block",
            "netherite", "beacon", "shroomlight", "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "copper_block")


def solid(name):
    b = name.split("[")[0]
    return b.startswith("minecraft:") and not any(t in b for t in NOT_SOLID)


def main():
    work, out, lst = sys.argv[1], sys.argv[2], sys.argv[3]
    poly = json.load(open(os.path.join(ROOT, "src/main/resources/data/stalkerzone/atlas/world.json"), encoding="utf-8"))["bounds"]
    places = json.load(open(os.path.join(ROOT, "docs/thezone/places.json"), encoding="utf-8"))["places"]
    places = places + [{"ru": "Генераторы", "x": 0, "z": 0}]
    rdir = os.path.join(work, "region")
    found = []
    for f in sorted(os.listdir(rdir)):
        p = os.path.join(rdir, f)
        if not f.endswith(".mca") or os.path.getsize(p) < 8192:
            continue
        data = open(p, "rb").read()
        for i in range(1024):
            loc = struct.unpack(">I", data[4 * i:4 * i + 4])[0]
            if not loc:
                continue
            off = (loc >> 8) * 4096
            n, comp = struct.unpack(">IB", data[off:off + 5])
            if comp != 2:
                continue
            raw = zlib.decompress(data[off + 5:off + 4 + n])
            if b"command_block" not in raw:
                continue
            _, ch = A.nbt_load(raw)
            for e in ch.get("block_entities", (0, (0, [])))[1][1]:
                if e["id"][1] != "minecraft:command_block":
                    continue
                x, y, z = e["x"][1], e["y"][1], e["z"][1]
                if not P.inside(poly, x, z):
                    continue
                st = A.get_block(ch, x, y, z)
                if st.split("[")[0] not in CMD:
                    continue
                cmd = e["Command"][1] if "Command" in e else ""
                found.append((x, y, z, st, cmd, e.get("auto", (0, 0))[1]))
    w = P.World(work)
    D6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    keep = set()
    todo = {(x, y, z) for x, y, z, *_ in found} - keep
    pick = {}
    for q in todo:
        cand = Counter(s for s in (w.get(q[0] + d[0], q[1] + d[1], q[2] + d[2]) for d in D6) if solid(s))
        if cand:
            pick[q] = cand.most_common(1)[0][0]
    # цепочки командных блоков: соседний командный блок, уже получивший блок окружения, считается твёрдым соседом
    changed = True
    while changed:
        changed = False
        for q in todo - set(pick):
            cand = Counter(pick[(q[0] + d[0], q[1] + d[1], q[2] + d[2])] for d in D6 if (q[0] + d[0], q[1] + d[1], q[2] + d[2]) in pick)
            if cand:
                pick[q] = cand.most_common(1)[0][0]; changed = True
    blocks, rows, stat = [], [], Counter()
    for x, y, z, st, cmd, auto in found:
        if (x, y, z) in keep:
            continue
        is_open = (x, y, z) not in pick
        nb = [w.get(x + d[0], y + d[1], z + d[2]) for d in D6]
        cmdn = [(x + d[0], y + d[1], z + d[2]) in todo for d in D6]
        visible = any(not solid(n) and not c for n, c in zip(nb, cmdn))
        if is_open:
            to = "minecraft:air"
        elif not visible:
            to = "minecraft:deepslate" if y < 0 else "minecraft:stone"
        else:
            cand = Counter(n for n in nb if solid(n) and not any(v in n for v in VALUABLE))
            to = cand.most_common(1)[0][0] if cand else ("minecraft:deepslate" if y < 0 else "minecraft:stone")
        near = min(places, key=lambda q: (q["x"] - x) ** 2 + (q["z"] - z) ** 2)
        blocks.append({"pos": [x, y, z], "from": st.split("[")[0], "to": to})
        rows.append({"pos": [x, y, z], "kind": st.split("[")[0].split(":")[1], "to": to, "open": is_open,
                     "loc": near["ru"], "cmd": cmd[:80], "auto": auto})
        stat["открытые → воздух" if is_open else "на виду → блок стены" if visible else "скрытые → камень"] += 1
    spec = {"what": "Э1-10: командные блоки TheZone → блок окружения (%d), открытые → воздух" % len(blocks),
            "why": "решение 15.11 (Э0-21-notes а4) и 16.11: механики автора не восстанавливаем; замена по самому частому твёрдому соседу",
            "blocks": blocks}
    json.dump(spec, open(out, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(rows, open(lst, "w", encoding="utf-8"), ensure_ascii=False)
    print("командных блоков внутри контура:", len(found))
    for k, v in stat.items():
        print(" ", k, v)
    by = Counter((r["loc"], r["open"]) for r in rows)
    for loc in sorted({r["loc"] for r in rows}):
        print("  %-30s встроено %5d, открыто %4d" % (loc, by[(loc, False)], by[(loc, True)]))
    print("  вызов жителей (убираются):", sum(1 for r in rows if "summon villager" in r["cmd"].lower()))
    print("  во что:", Counter(r["to"].split("[")[0] for r in rows).most_common(10))
    print("  высоты:", sorted(Counter(r["pos"][1] // 10 * 10 for r in rows).items()))
    print("  команды:", Counter(r["cmd"].split(" ")[0] for r in rows).most_common(8))


if __name__ == "__main__":
    main()
