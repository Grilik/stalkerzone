#!/usr/bin/env python3
"""Э1-17: обзор контейнеров мира (только чтение) — сундуки, бочки, их таблицы добычи и содержимое.

  python -I tools/thezone/chests_survey.py <рабочая копия> <out.json>

В out.json — список {pos, block, be, loot, items}: be — id блок-сущности (null — блока-сущности нет),
loot — LootTable из блок-сущности (Bedrock: chests/boxScrap и т.п.), items — сколько стопок внутри.
"""
import json
import os
import struct
import sys
import zlib
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
import trial_copy as T  # noqa: E402

NAMES = ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")


def region_chunks(path):
    data = open(path, "rb").read()
    for i in range(1024):
        loc = struct.unpack(">I", data[4 * i:4 * i + 4])[0]
        if not loc:
            continue
        off = (loc >> 8) * 4096
        n, comp = struct.unpack(">IB", data[off:off + 5])
        if comp != 2:
            continue
        yield zlib.decompress(data[off + 5:off + 4 + n])


def main():
    work, out = sys.argv[1], sys.argv[2]
    rdir = os.path.join(work, "region")
    res = []
    for f in sorted(os.listdir(rdir)):
        p = os.path.join(rdir, f)
        if not f.endswith(".mca") or os.path.getsize(p) < 8192:
            continue
        for d in region_chunks(p):
            if b"chest" not in d and b"barrel" not in d:
                continue
            _, ch = A.nbt_load(d)
            cx, cz = ch["xPos"][1], ch["zPos"][1]
            bes = {}
            for e in ch.get("block_entities", (0, (0, [])))[1][1]:
                bes[(e["x"][1], e["y"][1], e["z"][1])] = e
            for sy, names in T._blocks(ch).items():
                for i, nm in enumerate(names):
                    base = nm.split("[")[0]
                    if base not in NAMES:
                        continue
                    pos = (cx * 16 + (i & 15), sy * 16 + (i >> 8), cz * 16 + ((i >> 4) & 15))
                    e = bes.get(pos)
                    res.append({"pos": list(pos), "block": nm, "be": e["id"][1] if e else None,
                                "loot": e["LootTable"][1] if e and "LootTable" in e else None,
                                "items": len(e["Items"][1][1]) if e and "Items" in e else 0})
    json.dump(res, open(out, "w", encoding="utf-8"), ensure_ascii=False)
    print("контейнеров:", len(res))
    print("по блокам:", Counter(r["block"].split("[")[0] for r in res))
    print("по таблицам:", Counter(r["loot"] for r in res).most_common(20))
    print("без блок-сущности:", sum(1 for r in res if r["be"] is None), "; с предметами:", sum(1 for r in res if r["items"]))


if __name__ == "__main__":
    main()
