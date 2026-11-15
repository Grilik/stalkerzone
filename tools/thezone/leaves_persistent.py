#!/usr/bin/env python3
"""Листва TheZone: persistent=false → true во всей рабочей копии (решение пользователя 16.11).

Chunker записал всей листве persistent=false; листья дальше 6 блоков от ствола игра со временем
удаляет (заход 1, 15.11: у листвы пересчиталось distance — кроны автора могли осыпаться).

  python -I tools/thezone/leaves_persistent.py <рабочая копия> --dry   # только посчитать
  python -I tools/thezone/leaves_persistent.py <рабочая копия>         # правка через журнал Э1-03

Как: в разжатом NBT чанка строка свойства `persistent: "false"` заменяется на `"true"` (в палитрах
секций — других блоков с этим свойством в ванили нет). NBT не хранит длин составных тегов, поэтому
замена строки другой длины корректна. Если в палитре уже есть такое же состояние с persistent=true,
получились бы два одинаковых элемента — такие чанки скрипт не трогает и называет. Регион
пересобирается целиком в памяти: неизменённые чанки копируются байт в байт, временные метки сохраняются.
Мир должен быть закрыт.
"""
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
from world_edit_log import Work  # noqa: E402

OLD = b"\x08\x00\x0apersistent\x00\x05false"
NEW = b"\x08\x00\x0apersistent\x00\x04true"


def chunks(path):
    """[(index, timestamp, compression, raw payload)] для существующих чанков."""
    data = open(path, "rb").read()
    out = []
    for i in range(1024):
        loc = struct.unpack(">I", data[4 * i:4 * i + 4])[0]
        if not loc:
            continue
        off = (loc >> 8) * 4096
        n, comp = struct.unpack(">IB", data[off:off + 5])
        ts = struct.unpack(">I", data[4096 + 4 * i:4096 + 4 * i + 4])[0]
        out.append((i, ts, comp, data[off + 5:off + 4 + n]))
    return out


def fix_palettes(root):
    """persistent=false → true в палитрах секций; одинаковые элементы сливаются, индексы перепаковываются."""
    for sec in root["sections"][1][1]:
        bs = sec.get("block_states")
        if not bs:
            continue
        pal = bs[1]["palette"][1][1]
        for st in pal:
            pr = st.get("Properties")
            if pr and pr[1].get("persistent", (0, ""))[1] == "false":
                pr[1]["persistent"] = (A.STR, "true")
        keys = [A.state_str(st) for st in pal]
        if len(set(keys)) == len(keys):
            continue
        first, remap, newpal = {}, [], []
        for k, st in zip(keys, pal):
            if k not in first:
                first[k] = len(newpal); newpal.append(st)
            remap.append(first[k])
        bs[1]["palette"] = (A.LIST, (A.COMP, newpal))
        if "data" in bs[1]:
            idx = [remap[v] for v in A._unpack(bs[1]["data"][1], A._bits(len(pal)))]
            if len(newpal) == 1:
                del bs[1]["data"]
            else:
                bs[1]["data"] = (A.LARR, A._pack(idx, A._bits(len(newpal))))


def fix_region(path, write):
    """-> (чанков изменено, замен, чанков с разбором палитры)."""
    cs = chunks(path)
    changed, reps, skipped, new = 0, 0, [], []
    for i, ts, comp, raw in cs:
        if comp != 2:
            new.append((i, ts, comp, raw)); continue
        d = zlib.decompress(raw)
        k = d.count(OLD)
        if k == 0:
            new.append((i, ts, comp, raw)); continue
        changed += 1; reps += k
        if NEW in d:  # уже есть persistent=true — палитру разбираем и сливаем одинаковые элементы
            skipped.append(i)
            name, root = A.nbt_load(d)
            fix_palettes(root)
            new.append((i, ts, 2, zlib.compress(A.nbt_dump(name, root))))
            continue
        new.append((i, ts, 2, zlib.compress(d.replace(OLD, NEW))))
    if write and changed:
        hdr = bytearray(8192)
        body = bytearray()
        for i, ts, comp, raw in new:
            blob = struct.pack(">IB", len(raw) + 1, comp) + raw
            need = (len(blob) + 4095) // 4096
            if need > 255:
                raise ValueError("чанк больше 1 МБ в " + path)
            off = 2 + len(body) // 4096
            body += blob + b"\0" * (need * 4096 - len(blob))
            hdr[4 * i:4 * i + 4] = struct.pack(">I", (off << 8) | need)
            hdr[4096 + 4 * i:4096 + 4 * i + 4] = struct.pack(">I", ts)
        tmp = path + ".tmp"
        with open(tmp, "wb") as f:
            f.write(hdr); f.write(body)
        os.replace(tmp, path)
    return changed, reps, skipped


def main():
    work = sys.argv[1]
    dry = "--dry" in sys.argv
    rdir = os.path.join(work, "region")
    files = sorted(f for f in os.listdir(rdir) if f.endswith(".mca") and os.path.getsize(os.path.join(rdir, f)) >= 8192)
    plan = []
    tot_c = tot_r = 0
    for f in files:
        c, r, s = fix_region(os.path.join(rdir, f), False)
        if c:
            plan.append(f)
        if s:
            print("  %s: в %d чанках палитра со слиянием (уже была persistent=true)" % (f, len(s)))
        tot_c += c; tot_r += r
    print("регионов с листвой: %d из %d; чанков: %d; элементов палитры: %d" % (len(plan), len(files), tot_c, tot_r))
    if dry or not plan:
        return
    w = Work(work)
    eid = w.begin("листва: persistent=false → true во всех палитрах (%d регионов, %d чанков)" % (len(plan), tot_c),
                  "решение пользователя 16.11: кроны TheZone не должны осыпаться (заход 1: distance листвы пересчитан)",
                  ["region/" + f for f in plan])
    try:
        for f in plan:
            fix_region(os.path.join(rdir, f), True)
        # обратная проверка: ни одной старой строки
        left = sum(fix_region(os.path.join(rdir, f), False)[1] for f in plan)
        if left:
            raise RuntimeError("после правки осталось %d замен" % left)
        for f in plan[:3]:  # чанки читаются
            for i, ts, comp, raw in chunks(os.path.join(rdir, f))[:5]:
                A.nbt_load(zlib.decompress(raw))
    except Exception:
        w.undo(eid)
        raise
    w.commit(eid)
    print("правка", eid, "записана; откат: python -I tools/thezone/world_edit_log.py undo", work, eid)


if __name__ == "__main__":
    main()
