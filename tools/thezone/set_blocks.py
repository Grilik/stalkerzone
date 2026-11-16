#!/usr/bin/env python3
"""Набор блоков одной правкой рабочей копии через журнал Э1-03 (резерв, md5, откат).

  python -I tools/thezone/set_blocks.py <рабочая копия> <правка.json> [--dry]

правка.json: {"what": "...", "why": "...", "blocks": [{"pos": [x, y, z], "from": "minecraft:hopper", "to": "minecraft:stone"}, ...]}
«from» — ожидаемый блок (имя без свойств): если на месте другое, правка не начинается.
Блок-сущность на месте заменённого блока удаляется (anvil.set_block).
Э1-17: «be» — блок-сущность нового блока: {"id": "stalkerzone:zone_container", "LootTable": "...", "keepItems": true};
строковые поля пишутся как есть, keepItems — перенести предметы старой блок-сущности (ячейки по порядку с 0;
если предметы были, таблица добычи не ставится — содержимое автора важнее).
Мир должен быть закрыт.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
from world_edit_log import Work  # noqa: E402


def _be(old, spec):
    """Поля новой блок-сущности (compound без x/y/z/id) по спецификации и старой блок-сущности."""
    data = {}
    items = old["Items"][1][1] if old is not None and "Items" in old else []
    for k, v in spec.items():
        if k in ("id", "keepItems"):
            continue
        if k == "LootTable" and spec.get("keepItems") and items:
            continue
        data[k] = (A.STR, v)
    if spec.get("keepItems") and items:
        moved = []
        for i, it in enumerate(sorted(items, key=lambda t: t["Slot"][1])):
            it = dict(it)
            it["Slot"] = (A.BYTE, i)
            moved.append(it)
        data["Items"] = (A.LIST, (A.COMP, moved))
    return data


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
                old = A.block_entity(root, *b["pos"]) if "be" in b else None
                A.set_block(root, *b["pos"], b["to"])
                if "be" in b:
                    A.set_block_entity(root, *b["pos"], b["be"]["id"], _be(old, b["be"]))
            A.write_chunk(os.path.join(rdir, f), cx, cz, name, root)
        for (cx, cz), (f, _, _) in chunks.items():  # обратное чтение
            _, r2 = A.read_chunk(os.path.join(rdir, f), cx, cz)
            for b in by_chunk[(cx, cz)]:
                if A.get_block(r2, *b["pos"]).split("[")[0] != b["to"].split("[")[0]:
                    raise RuntimeError("не записалось: %s" % b["pos"])
                if "be" in b:
                    be = A.block_entity(r2, *b["pos"])
                    if be is None or be["id"][1] != b["be"]["id"]:
                        raise RuntimeError("нет блок-сущности: %s" % b["pos"])
    except Exception:
        w.undo(eid)
        raise
    w.commit(eid)
    print("правка", eid, "записана; откат: python -I tools/thezone/world_edit_log.py undo", work, eid)


if __name__ == "__main__":
    main()
