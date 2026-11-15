#!/usr/bin/env python3
"""Э1-04, шаг 1: растры съёмки TheZone 1 пиксель = 1 блок. Только чтение регионов.

    python -I tools/thezone/survey_raster.py "<рабочая копия>" "<папка растров>" [--margin 256] [--jobs 14]

На каждый регион r.X.Z.mca пишет <папка>/r.X.Z.npz (сжато, 512×512, порядок [z, x]):
  top_y    int16  высота верхнего «видимого» блока (трава, цветы, снег-слой, факелы — пропускаются), -999 — пусто
  top_id   uint16 номер имени верхнего блока в names (список имён в этом же файле)
  ground_y int16  высота верхнего блока «земли» (правило — data/stalkerzone/thezone/ground_blocks.json, решение а5)
  art_up   uint8  сколько искусственных блоков в колонке выше ground_y (объём постройки)
  art_down uint8  сколько искусственных блоков ниже ground_y-3 (подвалы, бункеры, туннели)
и <папка>/r.X.Z.json — блок-сущности (сундуки, командные блоки, таблички, спавнеры) с координатами.
Берутся только регионы, задетые контуром границы docs/thezone/border.json плюс margin.
Пропуски (чанка нет) — top_y = -999.
"""
import json, os, re, struct, sys, zlib
from multiprocessing import Pool
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from compare_worlds import NBT, text_of  # noqa: E402

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
GROUND_FILE = os.path.join(REPO, "src/main/resources/data/stalkerzone/thezone/ground_blocks.json")
G = json.load(open(GROUND_FILE, encoding="utf-8"))
GROUND = set(G["ground"])
SKIP = set(G["see_through"])
NATURAL = GROUND | set(G["natural"]) | SKIP | {"air"}
NATURAL_SUFFIX = tuple(G["natural_suffix"])
BE_KEEP = ("chest", "trapped_chest", "command_block", "chain_command_block", "repeating_command_block",
           "sign", "hanging_sign", "spawner", "barrel", "furnace", "dispenser", "hopper")
Y0, H = -64, 384


def is_natural(n):
    return n in NATURAL or n.endswith(NATURAL_SUFFIX)


def unpack(states, n_pal):
    bits = max(4, (n_pal - 1).bit_length())
    per = 64 // bits
    a = np.array(states, dtype=np.int64).view(np.uint64)
    idx = np.arange(4096)
    words = a[idx // per]
    shift = ((idx % per) * bits).astype(np.uint64)
    return ((words >> shift) & np.uint64((1 << bits) - 1)).astype(np.int32)


def chunk_columns(ch, names, nid):
    """-> top_y, top_id, ground_y, art_up, art_down (16×16, [z, x]) и список блок-сущностей."""
    gid = np.zeros((H, 16, 16), np.uint16)  # 0 = воздух
    for s in ch.get("sections", []):
        bs = s.get("block_states", {})
        pal = [p.get("Name", "?").replace("minecraft:", "") for p in bs.get("palette", [])]
        if not pal:
            continue
        g = []
        for n in pal:
            if n not in nid:
                nid[n] = len(names); names.append(n)
            g.append(nid[n])
        g = np.array(g, np.uint16)
        y = (s["Y"] * 16) - Y0
        if not 0 <= y < H:
            continue
        gid[y:y + 16] = g[0] if len(pal) == 1 else g[unpack(bs["data"], len(pal))].reshape(16, 16, 16)
    return gid


def lut(names, pred):
    return np.array([pred(n) for n in names], bool)


def do_region(args):
    path, out = args
    m = re.search(r"r\.(-?\d+)\.(-?\d+)\.mca$", path)
    rx, rz = int(m.group(1)), int(m.group(2))
    dst = os.path.join(out, "r.%d.%d.npz" % (rx, rz))
    if os.path.exists(dst):
        return dst, 0
    names, nid = ["air"], {"air": 0}
    top_y = np.full((512, 512), -999, np.int16); top_id = np.zeros((512, 512), np.uint16)
    ground_y = np.full((512, 512), -999, np.int16)
    art_up = np.zeros((512, 512), np.uint8); art_down = np.zeros((512, 512), np.uint8)
    bes = []
    with open(path, "rb") as f:
        d = f.read()
    n = 0
    ys = np.arange(H)[:, None, None]
    for i in range(1024 if len(d) >= 8192 else 0):
        off = struct.unpack_from(">I", d, 4 * i)[0]
        if not off:
            continue
        s = (off >> 8) * 4096
        ln = struct.unpack_from(">I", d, s)[0]
        try:
            ch = NBT(zlib.decompress(d[s + 5:s + 4 + ln]), False).root()
        except Exception:
            continue
        n += 1
        lx, lz = (i % 32) * 16, (i // 32) * 16
        gid = chunk_columns(ch, names, nid)
        L_vis = lut(names, lambda q: q != "air" and q not in SKIP)
        L_gr = lut(names, lambda q: q in GROUND)
        L_art = lut(names, lambda q: not is_natural(q))
        vis, gr, art = L_vis[gid], L_gr[gid], L_art[gid]
        # верхний индекс по y: argmax по перевёрнутой оси
        def top(mask):
            any_ = mask.any(0)
            yy = H - 1 - np.argmax(mask[::-1], axis=0)
            return np.where(any_, yy, -1)
        ty, gy = top(vis), top(gr)
        tid = np.take_along_axis(gid, np.maximum(ty, 0)[None], 0)[0]
        up = (art & (ys > gy[None])).sum(0)
        down = (art & (ys < gy[None] - 3) & (gy[None] >= 0)).sum(0)
        sl = (slice(lz, lz + 16), slice(lx, lx + 16))
        top_y[sl] = np.where(ty >= 0, ty + Y0, -999); top_id[sl] = np.where(ty >= 0, tid, 0)
        ground_y[sl] = np.where(gy >= 0, gy + Y0, -999)
        art_up[sl] = np.minimum(up, 255); art_down[sl] = np.minimum(down, 255)
        for be in ch.get("block_entities", []):
            t = be.get("id", "").replace("minecraft:", "")
            if t.endswith(BE_KEEP) or t in BE_KEEP:
                e = {"id": t, "x": be.get("x"), "y": be.get("y"), "z": be.get("z")}
                if t.endswith("sign"):
                    e["text"] = " ".join(x for x in (text_of(q) for q in be.get("front_text", {}).get("messages", [])) if x.strip()).strip()
                if "command_block" in t:
                    e["cmd"] = be.get("Command", "")[:120]
                bes.append(e)
    np.savez_compressed(dst, top_y=top_y, top_id=top_id, ground_y=ground_y, art_up=art_up, art_down=art_down,
                        names=np.array(names), chunks=np.int32(n))
    with open(dst[:-4] + ".json", "w", encoding="utf-8") as f:
        json.dump(bes, f, ensure_ascii=False)
    return dst, n


def border_bbox(margin):
    b = json.load(open(os.path.join(REPO, "docs/thezone/border.json"), encoding="utf-8"))["contour"]
    xs, zs = [p[0] for p in b], [p[1] for p in b]
    return min(xs) - margin, max(xs) + margin, min(zs) - margin, max(zs) + margin


def main():
    world, out = sys.argv[1], sys.argv[2]
    margin = int(sys.argv[sys.argv.index("--margin") + 1]) if "--margin" in sys.argv else 256
    jobs = int(sys.argv[sys.argv.index("--jobs") + 1]) if "--jobs" in sys.argv else 14
    os.makedirs(out, exist_ok=True)
    x0, x1, z0, z1 = border_bbox(margin)
    todo = []
    for fn in sorted(os.listdir(os.path.join(world, "region"))):
        m = re.match(r"r\.(-?\d+)\.(-?\d+)\.mca$", fn)
        if not m:
            continue
        rx, rz = int(m.group(1)), int(m.group(2))
        if rx * 512 + 511 < x0 or rx * 512 > x1 or rz * 512 + 511 < z0 or rz * 512 > z1:
            continue
        todo.append((os.path.join(world, "region", fn), out))
    print("регионов:", len(todo), flush=True)
    total = 0
    with Pool(jobs) as p:
        for k, (dst, n) in enumerate(p.imap_unordered(do_region, todo), 1):
            total += n
            if k % 20 == 0:
                print(k, "/", len(todo), flush=True)
    print("готово, чанков прочитано:", total)


if __name__ == "__main__":
    main()
