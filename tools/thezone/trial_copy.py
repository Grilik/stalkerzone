#!/usr/bin/env python3
"""Э1-08: опытная копия TheZone для захода с модом и проверка «блоки не изменились».

  python -I tools/thezone/trial_copy.py make  <рабочая копия> <папка новой копии> [X Y Z]
  python -I tools/thezone/trial_copy.py check <рабочая копия> <опытная копия> [--regions r.-9.7,...]
  python -I tools/thezone/trial_copy.py spawn <опытная копия> [X Y Z]   # только level.dat: точка появления и игрок

make  — копия рабочей копии (без backup/ и журнала stalkerzone/), манифест stalkerzone/world.json
        (режим thezone, решение а1), level.dat: команды включены, точка появления (по умолчанию —
        деревня новичков Кордона из атласа), игрок перенесён туда же, spawnRadius 0, randomTickSpeed 0, имя мира.
        Рабочая копия только читается.
check — после захода: регионы, у которых изменились байты, разбираются по чанкам; блоки (палитра →
        имена состояний по позициям) и число блок-сущностей сравниваются с рабочей копией.
        Minecraft пересохраняет загруженные чанки (карты высот, свет), поэтому байты меняются — важны блоки.
Пишет отчёт в stdout; код выхода 1, если блоки изменились.
"""
import gzip
import hashlib
import json
import os
import shutil
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402

SPAWN = (-4404, 70, 3813)  # атлас world.json: у костра деревни новичков (Э2-19; костёр −4405 71 3812)


def make(work, dst, spawn=SPAWN):
    if os.path.exists(dst):
        raise SystemExit("папка уже есть: %s — выберите другую (существующую не перезаписываю)" % dst)
    if not os.path.exists(os.path.join(work, "stalkerzone", "WORKCOPY")):
        raise SystemExit("это не рабочая копия (нет stalkerzone/WORKCOPY): " + work)
    shutil.copytree(work, dst, ignore=lambda d, names: [n for n in names if os.path.samefile(d, work) and n in ("backup", "stalkerzone")])
    os.makedirs(os.path.join(dst, "stalkerzone"))
    with open(os.path.join(dst, "stalkerzone", "world.json"), "w", encoding="utf-8") as f:
        json.dump({"mode": "thezone", "atlas": 1, "source": "world/work (Э1-03), опытная копия Э1-08"}, f, ensure_ascii=False, indent=1)
    level_dat(dst, spawn, name=True)
    print("готово:", dst, "— точка появления", spawn)


def level_dat(dst, spawn=SPAWN, name=False):
    """level.dat копии: команды, точка появления и игрок в ней, spawnRadius 0, randomTickSpeed 0 (и имя мира)."""
    p = os.path.join(dst, "level.dat")
    nm, root = A.nbt_load(gzip.decompress(open(p, "rb").read()))
    d = root["Data"][1]
    x, y, z = spawn
    d["allowCommands"] = (A.BYTE, 1)
    d["SpawnX"], d["SpawnY"], d["SpawnZ"] = (A.INT, x), (A.INT, y), (A.INT, z)
    if name:
        d["LevelName"] = (A.STR, os.path.basename(os.path.normpath(dst)))
    d["GameRules"][1]["spawnRadius"] = (A.STR, "0")
    # без случайных тиков: листва TheZone (persistent=false) не осыпается, саженцы не растут, трава не гибнет —
    # иначе сверка «блоки не изменились» ловит природу, а не мод (заход 1, 15.11: 4 622 блока, все — природа)
    d["GameRules"][1]["randomTickSpeed"] = (A.STR, "0")
    if "Player" in d:
        pl = d["Player"][1]
        pl["Pos"] = (A.LIST, (A.DOUBLE, [x + 0.5, float(y), z + 0.5]))
        pl["Motion"] = (A.LIST, (A.DOUBLE, [0.0, 0.0, 0.0]))
        pl["Dimension"] = (A.STR, "minecraft:overworld")
    open(p, "wb").write(gzip.compress(A.nbt_dump(nm, root)))


def _md5(p):
    h = hashlib.md5()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def _unpack_np(data, bits):
    a = np.array(data, dtype=np.int64).view(np.uint64)
    per = 64 // bits
    idx = np.arange(4096)
    w = a[idx // per]
    return ((w >> ((idx % per) * bits).astype(np.uint64)) & np.uint64((1 << bits) - 1)).astype(np.int64)


def _blocks(chunk):
    """{секция Y: массив 4096 имён состояний} — только непустые секции."""
    out = {}
    for sec in chunk.get("sections", (0, (0, [])))[1][1]:
        bs = sec.get("block_states")
        if not bs:
            continue
        pal = [A.state_str(s) for s in bs[1]["palette"][1][1]]
        if "data" in bs[1]:
            ids = _unpack_np(bs[1]["data"][1], A._bits(len(pal)))
            names = np.array(pal, dtype=object)[ids]
        else:
            names = np.array(pal * 4096, dtype=object)[:4096] if len(pal) == 1 else None
        if names is not None and not (len(pal) == 1 and pal[0] == "minecraft:air"):
            out[sec["Y"][1]] = names
    return out


def check(work, trial, regions=None):
    R0, R1 = os.path.join(work, "region"), os.path.join(trial, "region")
    files = sorted(f for f in os.listdir(R1) if f.endswith(".mca") and os.path.getsize(os.path.join(R1, f)) >= 8192)
    if regions:
        files = [f for f in files if f[:-4] in regions]
    changed_regions = [f for f in files if not os.path.exists(os.path.join(R0, f)) or _md5(os.path.join(R0, f)) != _md5(os.path.join(R1, f))]
    print("регионов: %d, изменились байты: %d" % (len(files), len(changed_regions)))
    bad_blocks, bad_be, chunks, new_chunks = 0, 0, 0, 0
    for f in changed_regions:
        rx, rz = map(int, f.split(".")[1:3])
        for cz in range(rz * 32, rz * 32 + 32):
            for cx in range(rx * 32, rx * 32 + 32):
                b = A.read_chunk(os.path.join(R1, f), cx, cz)
                if b is None:
                    continue
                a = A.read_chunk(os.path.join(R0, f), cx, cz) if os.path.exists(os.path.join(R0, f)) else None
                if a is None:
                    new_chunks += 1
                    continue
                chunks += 1
                ba, bb = _blocks(a[1]), _blocks(b[1])
                n = 0
                for y in set(ba) | set(bb):
                    va, vb = ba.get(y), bb.get(y)
                    if va is None or vb is None:
                        n += 4096 if (va if va is not None else vb) is not None else 0
                    else:
                        n += int((va != vb).sum())
                if n:
                    bad_blocks += n
                    print("  чанк (%d, %d): изменилось блоков %d" % (cx, cz, n))
                ea = len(a[1].get("block_entities", (0, (0, [])))[1][1])
                eb = len(b[1].get("block_entities", (0, (0, [])))[1][1])
                if ea != eb:
                    bad_be += 1
                    print("  чанк (%d, %d): блок-сущностей %d → %d" % (cx, cz, ea, eb))
    print("чанков сверено: %d, новых чанков: %d; изменённых блоков: %d; чанков с другим числом блок-сущностей: %d"
          % (chunks, new_chunks, bad_blocks, bad_be))
    print("ИТОГ:", "блоки не изменились" if bad_blocks == 0 and bad_be == 0 else "ЕСТЬ ИЗМЕНЕНИЯ")
    return 0 if bad_blocks == 0 and bad_be == 0 else 1


def main():
    cmd = sys.argv[1]
    if cmd == "make":
        sp = tuple(int(v) for v in sys.argv[4:7]) if len(sys.argv) >= 7 else SPAWN
        make(sys.argv[2], sys.argv[3], sp)
    elif cmd == "check":
        regs = None
        if "--regions" in sys.argv:
            regs = set(sys.argv[sys.argv.index("--regions") + 1].split(","))
        sys.exit(check(sys.argv[2], sys.argv[3], regs))
    elif cmd == "spawn":
        sp = tuple(int(v) for v in sys.argv[3:6]) if len(sys.argv) >= 6 else SPAWN
        level_dat(sys.argv[2], sp)
        print("level.dat:", sys.argv[2], "— точка появления и игрок", sp)
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
