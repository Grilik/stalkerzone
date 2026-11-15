#!/usr/bin/env python3
"""Э1-01, шаги 3–4: обзорная карта TheZone сверху и подсчёт материалов. Только чтение регионов.

    python -I tools/thezone/survey_map.py "<папка Java-мира>" <out-dir> [--step 16] [--sample-every 25]

* карта: 1 пиксель = step блоков (верхний непрозрачный блок в одной колонке на клетку), сетка 512 блоков с подписями;
* материалы: точный подсчёт блоков в каждом sample-every-м чанке (оценка на весь мир = × sample-every);
* подписи: таблички с текстом (названия локаций) — кружок и текст.
Пишет overview.png, materials.json, labels.json.
"""
import json, math, os, re, struct, sys, zlib, collections
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compare_worlds import NBT, text_of  # noqa: E402

AIR = {"air", "cave_air", "void_air", "barrier", "light", "structure_void"}
SKIP_TOP = AIR | {"grass", "tall_grass", "fern", "large_fern", "dead_bush", "torch", "wall_torch", "snow",
                  "dandelion", "poppy", "vine", "sunflower", "rose_bush", "lilac", "peony", "cobweb", "rail"}

def colour(n):
    T = [("water", (52, 84, 150)), ("lava", (200, 90, 20)), ("grass_block", (92, 120, 58)), ("leaves", (52, 88, 40)),
         ("log", (88, 66, 42)), ("planks", (140, 108, 66)), ("dirt", (110, 80, 56)), ("podzol", (90, 66, 40)),
         ("sand", (200, 190, 140)), ("gravel", (125, 120, 115)), ("clay", (150, 150, 160)), ("snow", (235, 235, 240)),
         ("terracotta", (150, 100, 80)), ("wool", (170, 160, 150)), ("concrete", (160, 160, 160)), ("bricks", (150, 85, 70)),
         ("quartz", (210, 205, 195)), ("stone_brick", (115, 115, 115)), ("cobblestone", (105, 105, 105)),
         ("andesite", (130, 130, 130)), ("diorite", (190, 190, 190)), ("granite", (150, 105, 85)), ("stone", (120, 120, 120)),
         ("iron", (200, 200, 200)), ("glass", (170, 200, 215)), ("slab", (140, 140, 140)), ("stairs", (130, 130, 130)),
         ("rail", (90, 70, 50)), ("farmland", (95, 65, 40)), ("mycelium", (110, 95, 110)), ("netherrack", (110, 50, 50))]
    for k, c in T:
        if k in n: return c
    return (170, 60, 170)

def unpack(states, n_pal):
    bits = max(4, (n_pal - 1).bit_length())
    per = 64 // bits
    a = np.array(states, dtype=np.int64).view(np.uint64)
    idx = np.arange(4096)
    words = a[idx // per]
    shift = ((idx % per) * bits).astype(np.uint64)
    return ((words >> shift) & np.uint64((1 << bits) - 1)).astype(np.int32)

def chunks(region_dir):
    for fn in sorted(os.listdir(region_dir)):
        m = re.match(r"r\.(-?\d+)\.(-?\d+)\.mca$", fn)
        if not m: continue
        rx, rz = int(m.group(1)), int(m.group(2))
        with open(os.path.join(region_dir, fn), "rb") as f: d = f.read()
        if len(d) < 8192: continue
        for i in range(1024):
            off = struct.unpack_from(">I", d, 4 * i)[0]
            if off == 0: continue
            s = (off >> 8) * 4096
            ln = struct.unpack_from(">I", d, s)[0]
            try:
                nbt = zlib.decompress(d[s + 5:s + 4 + ln])
            except Exception:
                continue
            yield rx * 32 + i % 32, rz * 32 + i // 32, nbt

def main():
    world, out = sys.argv[1], sys.argv[2]
    step = int(sys.argv[sys.argv.index("--step") + 1]) if "--step" in sys.argv else 16
    every = int(sys.argv[sys.argv.index("--sample-every") + 1]) if "--sample-every" in sys.argv else 25
    os.makedirs(out, exist_ok=True)
    per_chunk = max(1, 16 // step)                 # колонок на чанк по стороне
    cols = [(i * step + step // 2) % 16 for i in range(per_chunk)] if step < 16 else [8]
    top = {}; counts = collections.Counter(); sampled = 0; labels = []; n = 0
    for cx, cz, raw in chunks(os.path.join(world, "region")):
        n += 1
        if step > 16 and (cx * 16) % step and (cz * 16) % step: pass
        ch = NBT(raw, False).root()
        for be in ch.get("block_entities", []):
            if be.get("id", "").endswith("sign"):
                t = " ".join(x for x in (text_of(m) for m in be.get("front_text", {}).get("messages", [])) if x.strip())
                if t.strip(): labels.append({"x": be["x"], "y": be["y"], "z": be["z"], "text": t.strip()})
        secs = sorted(ch.get("sections", []), key=lambda s: -s.get("Y", 0))
        do_count = (n % every == 0)
        if do_count: sampled += 1
        need = {(x, z) for x in cols for z in cols}
        for s in secs:
            bs = s.get("block_states", {}); pal = [p.get("Name", "?").replace("minecraft:", "") for p in bs.get("palette", [])]
            if not pal: continue
            if len(pal) == 1:
                if do_count: counts[pal[0]] += 4096
                if pal[0] not in SKIP_TOP:
                    for (x, z) in list(need): top[(cx * 16 + x, cz * 16 + z)] = (pal[0], s["Y"] * 16 + 15); need.discard((x, z))
                continue
            ids = unpack(bs["data"], len(pal)).reshape(16, 16, 16)  # y, z, x
            if do_count:
                for k, c in zip(*np.unique(ids, return_counts=True)): counts[pal[k]] += int(c)
            for (x, z) in list(need):
                col = ids[:, z, x]
                for y in range(15, -1, -1):
                    nm = pal[col[y]]
                    if nm not in SKIP_TOP:
                        top[(cx * 16 + x, cz * 16 + z)] = (nm, s["Y"] * 16 + y); need.discard((x, z)); break
            if not need and not do_count: break
    xs = [k[0] for k in top]; zs = [k[1] for k in top]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    W, H = (x1 - x0) // step + 1, (z1 - z0) // step + 1
    img = Image.new("RGB", (W, H), (20, 20, 24))
    px = img.load()
    for (x, z), (nm, y) in top.items():
        c = colour(nm)
        shade = max(0.6, min(1.3, 0.75 + (y - 60) / 120))
        px[(x - x0) // step, (z - z0) // step] = tuple(int(min(255, v * shade)) for v in c)
    scale = 2 if max(W, H) < 1200 else 1
    img = img.resize((W * scale, H * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    try: font = ImageFont.truetype("arial.ttf", 12)
    except Exception: font = ImageFont.load_default()
    def P(x, z): return ((x - x0) // step * scale, (z - z0) // step * scale)
    g = 512
    for gx in range(math.ceil(x0 / g) * g, x1 + 1, g):
        X = P(gx, z0)[0]; d.line([(X, 0), (X, H * scale)], fill=(255, 255, 255, 80), width=1); d.text((X + 2, 2), str(gx), fill=(255, 255, 0), font=font)
    for gz in range(math.ceil(z0 / g) * g, z1 + 1, g):
        Z = P(x0, gz)[1]; d.line([(0, Z), (W * scale, Z)], fill=(255, 255, 255, 80), width=1); d.text((2, Z + 2), str(gz), fill=(255, 255, 0), font=font)
    seen = set()
    for lb in labels:
        key = (lb["text"].lower(), lb["x"] // 256, lb["z"] // 256)
        if key in seen: continue
        seen.add(key)
        X, Z = P(lb["x"], lb["z"])
        d.ellipse([X - 3, Z - 3, X + 3, Z + 3], outline=(255, 60, 60), width=2)
        d.text((X + 5, Z - 6), lb["text"][:28], fill=(255, 230, 230), font=font)
    img.save(os.path.join(out, "overview.png"))
    total = sum(counts.values())
    with open(os.path.join(out, "materials.json"), "w", encoding="utf-8") as f:
        json.dump({"chunks_total": n, "chunks_sampled": sampled, "sample_every": every,
                   "blocks_sampled": total, "estimate_factor": n / max(1, sampled),
                   "counts": counts.most_common()}, f, ensure_ascii=False, indent=1)
    with open(os.path.join(out, "labels.json"), "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=1)
    print(f"чанков {n}, в выборке {sampled}, карта {W}×{H} (шаг {step}), x {x0}..{x1}, z {z0}..{z1}, подписей {len(labels)}")

if __name__ == "__main__":
    main()
