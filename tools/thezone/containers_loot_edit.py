#!/usr/bin/env python3
"""Э1-17 (часть 2): контейнеры мода в мире — с ванильной LootTable на добычу мода (SzPlace + loot.json).

  python -I tools/thezone/containers_loot_edit.py <рабочая копия> <правка.json> <сводка.json> [раскладка.json]

раскладка.json — выход tools/containers/LootSummary.java: оружие, артефакты, рубли бюджета локаций по контейнерам
(кладутся в блок-сущность заранее, учитываются в Ledger при первом открытии).

Читает блок-сущности stalkerzone:zone_container (правка e0012). Место — из прежней таблицы
containers/<семейство>_<место> (цинк — база, сейф — жильё); чердаки Припяти (y 94) — место «attic» (решение 16.11:
оставить как декор, бедное место). Контейнеры с предметами автора (без таблицы) не трогаются.
правка.json — для set_blocks.py (блок тот же, блок-сущность: SzPlace вместо LootTable);
сводка.json — [{kind, place, x, y, z, loc}] для tools/containers/LootSummary.java (loc — ближайшее место places.json).
"""
import json
import os
import struct
import sys
import zlib
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
BE_ID = "stalkerzone:zone_container"
PRIPYAT_ATTIC = (-3700, -2700, -3300, -2200, 94)  # x0, x1, z0, z1, y — чердаки типовых домов (y 94 — и в любой части Припяти)


def main():
    work, out, summ = sys.argv[1], sys.argv[2], sys.argv[3]
    stash = {}
    if len(sys.argv) > 4:
        for e in json.load(open(sys.argv[4], encoding="utf-8")):
            its = []
            for it in e["items"]:
                if "gun" in it:
                    its.append({"id": "tacz:modern_kinetic_gun", "count": 1, "tag": {"GunId": it["gun"], "GunFireMode": it["mode"], "GunCurrentAmmoCount": 0}})
                else:
                    its.append({"id": it["id"], "count": it["count"]})
            stash[(e["x"], e["y"], e["z"])] = its
    places = json.load(open(os.path.join(ROOT, "docs/thezone/places.json"), encoding="utf-8"))["places"]
    rdir = os.path.join(work, "region")
    blocks, rows, stat, skipped = [], [], Counter(), 0
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
            if b"zone_container" not in raw:
                continue
            _, ch = A.nbt_load(raw)
            for e in ch.get("block_entities", (0, (0, [])))[1][1]:
                if e["id"][1] != BE_ID:
                    continue
                x, y, z = e["x"][1], e["y"][1], e["z"][1]
                if "LootTable" not in e:
                    skipped += 1
                    continue
                table = e["LootTable"][1].split("/")[-1]
                place = "base" if table == "zinc" else "home" if table == "safe" else table.split("_")[-1]
                state = A.get_block(ch, x, y, z)
                kind = state.split("[")[0].split(":")[1]
                near = min(places, key=lambda q: (q["x"] - x) ** 2 + (q["z"] - z) ** 2)
                x0, x1, z0, z1, ya = PRIPYAT_ATTIC
                if y == ya and (x0 <= x < x1 and z0 <= z < z1 or near["ru"].startswith("Припять")):
                    place = "attic"
                blocks.append({"pos": [x, y, z], "from": state.split("[")[0], "to": state,
                               "be": dict({"id": BE_ID, "SzPlace": place, "SzLoc": near["ru"], "keepItems": True},
                                          **({"items": stash[(x, y, z)]} if (x, y, z) in stash else {}))})
                rows.append({"kind": kind, "place": place, "x": x, "y": y, "z": z, "loc": near["ru"]})
                stat[place] += 1
    spec = {"what": "Э1-17: добыча контейнеров — ванильная LootTable → SzPlace (loot.json мода), %d контейнеров" % len(blocks),
            "why": "решение пользователя 16.11: случайная добыча с уровнями редкости, глубиной Зоны, типом и местом; чердаки Припяти — бедные",
            "blocks": blocks}
    json.dump(spec, open(out, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(rows, open(summ, "w", encoding="utf-8"), ensure_ascii=False)
    print("с раскладкой бюджета:", sum(1 for b in blocks if "items" in b["be"]))
    print("контейнеров с таблицей:", len(blocks), "; с предметами автора (не трогаются):", skipped)
    print("места:", dict(stat))


if __name__ == "__main__":
    main()
