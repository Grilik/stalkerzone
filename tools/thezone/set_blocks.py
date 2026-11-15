#!/usr/bin/env python3
"""Набор блоков одной правкой рабочей копии через журнал Э1-03 (резерв, md5, откат).

  python -I tools/thezone/set_blocks.py <рабочая копия> <правка.json> [--dry]

правка.json: {"what": "...", "why": "...", "blocks": [{"pos": [x, y, z], "from": "minecraft:hopper", "to": "minecraft:stone"}, ...]}
«from» — ожидаемый блок (имя без свойств): если на месте другое, правка не начинается.
Блок-сущность на месте заменённого блока удаляется (anvil.set_block). Блоки с блок-сущностью так не ставятся.
Мир должен быть закрыт.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
from world_edit_log import Work  # noqa: E402


def main():
    work, spec = sys.argv[1], json.load(open(sys.argv[2], encoding="utf-8"))
    dry = "--dry" in sys.argv
    rdir = os.path.join(work, "region")
    by_chunk = {}
    for b in spec["blocks"]:
        x, y, z = b["pos"]
        by_chunk.setdefault((x >> 4, z >> 4), []).append(b)
    chunks = {}
    for (cx, cz), bl in by_chunk.items():
        f = "r.%d.%d.mca" % (cx >> 5, cz >> 5)
        name, root = A.read_chunk(os.path.join(rdir, f), cx, cz)
        chunks[(cx, cz)] = (f, name, root)
        for b in bl:
            cur = A.get_block(root, *b["pos"]).split("[")[0]
            if cur != b["from"]:
                sys.exit("(%d, %d, %d): ожидался %s, стоит %s — правка не начата" % (*b["pos"], b["from"], cur))
    print("проверено блоков: %d, чанков: %d" % (len(spec["blocks"]), len(chunks)))
    if dry:
        return
    w = Work(work)
    files = sorted({"region/" + f for f, _, _ in chunks.values()})
    eid = w.begin(spec["what"], spec["why"], files, where=[b["pos"] for b in spec["blocks"]])
    try:
        for (cx, cz), (f, name, root) in chunks.items():
            for b in by_chunk[(cx, cz)]:
                A.set_block(root, *b["pos"], b["to"])
            A.write_chunk(os.path.join(rdir, f), cx, cz, name, root)
        for (cx, cz), (f, _, _) in chunks.items():  # обратное чтение
            _, r2 = A.read_chunk(os.path.join(rdir, f), cx, cz)
            for b in by_chunk[(cx, cz)]:
                if A.get_block(r2, *b["pos"]).split("[")[0] != b["to"].split("[")[0]:
                    raise RuntimeError("не записалось: %s" % b["pos"])
    except Exception:
        w.undo(eid)
        raise
    w.commit(eid)
    print("правка", eid, "записана; откат: python -I tools/thezone/world_edit_log.py undo", work, eid)


if __name__ == "__main__":
    main()
