#!/usr/bin/env python3
"""Э1-14: граница базы и раскладка секторов по постройкам TheZone (только чтение мира) -> предложение + превью.

  python -I tools/thezone/base_layout.py <рабочая копия> <out-папка> [id объекта атласа ...]

1. Растр вокруг базы (радиус R): земля, ограды (заборы, стены, рабица, решётки, framed-панели), постройки.
2. Граница: ограда «замыкается» (дыры до 4 блоков — ворота), заливка от центра. Если заливка утекает к краю — ограды
   нет: оболочка построек у центра с отступом 6.
3. Здания внутри: связные области колонок, где постройка выше земли на ≥ 3; этажи — по сплошным слоям перекрытий;
   входы — двери и проёмы на уровне земли по периметру; тип — по форме (корпус, ангар, барак, вышка, сарай).
4. Сектора — по роли базы (набор из мастер-проекта §17): каждое пригодное здание получает хотя бы один сектор,
   крупное — несколько (по этажам); склад и гараж — в ангар; казарма — барак или этаж корпуса; штаб — главный корпус;
   оборона — вышки и ворота; медпункт — ближе к центру. Места NPC — по вместимости сектора.
Выход: <id>.json (area, gates, buildings[], sectors[]) и <id>.png (превью) в out-папке. В атлас — после утверждения.
"""
import json
import math
import os
import sys
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402
import trial_copy as T  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
Y0, Y1 = 40, 140
TERRAIN = ("grass_block", "dirt", "coarse_dirt", "podzol", "stone", "sand", "gravel", "dirt_path", "water", "clay",
           "mud", "rooted_dirt", "andesite", "diorite", "granite", "farmland", "mycelium", "snow_block", "red_sand", "moss_block")
FENCE = ("fence", "wall", "pane", "bars", "chainlink", "grating", "framed_pane", "prop_hitbox")
SOFT = ("air", "leaves", "grass", "fern", "flower", "sapling", "vine", "snow", "torch", "carpet", "dead_bush", "mushroom",
        "tall_grass", "large_fern", "rose", "poppy", "dandelion", "lily", "seagrass", "kelp", "cobweb", "sign", "banner", "lantern",
        "rail", "button", "lever", "pressure_plate", "tripwire", "string", "log", "wood")
# роли баз по группировке: (роль, вес)
ROLES = {
    "loners": ["жильё", "кухня", "склад", "мастерская", "медпункт", "оборона", "штаб", "торговля"],
    "military": ["штаб", "казарма", "оборона", "склад", "медпункт", "гараж", "связь"],
    "bandits": ["штаб", "жильё", "склад", "мастерская", "оборона", "кухня"],
    "default": ["жильё", "склад", "оборона", "кухня"],
    "local": ["склад", "жильё", "оборона", "кухня"],  # малая база (склад/перевалка)
}
COL = {"штаб": (220, 60, 60), "казарма": (230, 140, 50), "жильё": (230, 140, 50), "кухня": (240, 210, 60), "склад": (120, 160, 230),
       "гараж": (90, 120, 200), "мастерская": (160, 110, 220), "медпункт": (240, 240, 240), "оборона": (80, 200, 90),
       "торговля": (230, 100, 200), "связь": (60, 210, 210)}


def cat(name):
    b = name.split("[")[0].split(":")[-1]
    if b in ("air", "cave_air", "void_air"):
        return 0
    if any(t in b for t in FENCE):
        return 2
    if b.endswith("_door") and "trap" not in b:
        return 4
    if b in TERRAIN or b.endswith("_ore"):
        return 1
    if any(t in b for t in SOFT):
        return 0
    return 3  # постройка


def raster(work, cx, cz, R):
    """-> ground (N,N), vol (H,N,N) категорий; N = 2R+1, H = Y1-Y0."""
    N = 2 * R + 1
    H = Y1 - Y0
    vol = np.zeros((H, N, N), np.uint8)
    rdir = os.path.join(work, "region")
    memo = {}
    for chx in range((cx - R) >> 4, ((cx + R) >> 4) + 1):
        for chz in range((cz - R) >> 4, ((cz + R) >> 4) + 1):
            got = A.read_chunk(os.path.join(rdir, A.region_name(chx, chz)), chx, chz)
            if not got:
                continue
            for sy, names in T._blocks(got[1]).items():
                if sy * 16 + 15 < Y0 or sy * 16 >= Y1:
                    continue
                u, inv = np.unique(names, return_inverse=True)
                cats = np.array([memo.setdefault(n, cat(n)) for n in u], np.uint8)[inv].reshape(16, 16, 16)  # y, z, x
                for ly in range(16):
                    y = sy * 16 + ly
                    if not Y0 <= y < Y1:
                        continue
                    x0, z0 = chx * 16 - (cx - R), chz * 16 - (cz - R)
                    xs, zs = max(0, x0), max(0, z0)
                    xe, ze = min(N, x0 + 16), min(N, z0 + 16)
                    if xs >= xe or zs >= ze:
                        continue
                    vol[y - Y0, zs:ze, xs:xe] = cats[ly, zs - z0:ze - z0, xs - x0:xe - x0]
    terr = vol == 1
    ground = np.where(terr.any(0), H - 1 - np.argmax(terr[::-1], 0), 0)  # верхний блок земли
    return ground, vol


def dilate(m, r):
    out = m.copy()
    for dz in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dz * dz <= r * r:
                out |= np.roll(np.roll(m, dz, 0), dx, 1)
    return out


def flood(block, start):
    N = block.shape[0]
    seen = np.zeros_like(block)
    q = deque([start])
    seen[start] = True
    leak = False
    while q:
        z, x = q.popleft()
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = z + dz, x + dx
            if not (0 <= a < N and 0 <= b < N):
                leak = True
                continue
            if not seen[a, b] and not block[a, b]:
                seen[a, b] = True
                q.append((a, b))
    return seen, leak


def components(mask, minsize):
    lab = np.zeros(mask.shape, np.int32)
    out = []
    n = 0
    for z, x in zip(*np.nonzero(mask)):
        if lab[z, x]:
            continue
        n += 1
        cells = []
        q = deque([(z, x)])
        lab[z, x] = n
        while q:
            a, b = q.popleft()
            cells.append((a, b))
            for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c, d = a + dz, b + dx
                if 0 <= c < mask.shape[0] and 0 <= d < mask.shape[1] and mask[c, d] and not lab[c, d]:
                    lab[c, d] = n
                    q.append((c, d))
        if len(cells) >= minsize:
            out.append(cells)
    return out


def hull(pts):
    pts = sorted(set(pts))
    if len(pts) < 3:
        return pts
    def cr(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cr(lo[-2], lo[-1], p) <= 0: lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cr(up[-2], up[-1], p) <= 0: up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


def outline(mask):
    """Многоугольник области: выпуклая оболочка граничных клеток (для превью и атласа достаточно)."""
    z, x = np.nonzero(mask)
    return hull(list(zip(x.tolist(), z.tolist())))


def layout(work, obj, owner, R=90, opt=None):
    opt = opt or {}
    cx, cz = obj["pos"][0], obj["pos"][-1]
    ground, vol = raster(work, cx, cz, R)
    N = 2 * R + 1
    H = vol.shape[0]
    zz, xx = np.mgrid[0:N, 0:N]
    rel = np.arange(H)[:, None, None] - ground[None]
    # ограда — у земли (0…3 над ней)
    fence = ((vol == 2) & (rel >= 1) & (rel <= 3)).any(0)
    build_col = ((vol == 3) | (vol == 4)) & (rel >= 1)
    tall = (build_col & (rel >= 3)).any(0)
    height = np.where(build_col.any(0), H - 1 - np.argmax(build_col[::-1], 0) - ground, 0)
    blds = []
    for c in components(tall, 12):  # столбы, фонари, ряды пропсов — не здания (уже 3 блоков)
        zs_, xs_ = [p_[0] for p_ in c], [p_[1] for p_ in c]
        if min(max(zs_) - min(zs_), max(xs_) - min(xs_)) + 1 >= 4 and max(height[p_] for p_ in c) >= 4 and len(c) >= 0.25 * (max(zs_) - min(zs_) + 1) * (max(xs_) - min(xs_) + 1):
            blds.append(c)
    # граница по ограде: группа ограды (куски, сшитые через разрывы до 6 блоков), чья оболочка охватывает центр
    area, how, ring = None, None, None
    joined = dilate(fence, 3)
    best = None
    # точка базы: центр построек у отметки атласа (отметка бывает у края базы)
    near0 = [c for c in blds if min(math.hypot(a_ - R, b_ - R) for a_, b_ in c) < 45]
    if near0:
        allc = [p_ for c in near0 for p_ in c]
        PZ, PX = int(np.mean([p_[0] for p_ in allc])), int(np.mean([p_[1] for p_ in allc]))
    else:
        PZ, PX = R, R
    for comp in components(joined, 30):
        # длинные линии (пограничный забор) обрезаются радиусом 55 от построек базы
        fc = [(b_, a_) for a_, b_ in comp if fence[a_, b_] and math.hypot(a_ - PZ, b_ - PX) <= 55]
        if len(fc) < 20:
            continue
        hp = hull(fc)
        if len(hp) < 3:
            continue
        img = Image.new("1", (N, N), 0)
        ImageDraw.Draw(img).polygon(hp, fill=1)
        m = np.array(img, bool)
        inside_b = sum(1 for c in near0 for a_, b_ in c if m[a_, b_])
        ok = (m[PZ, PX] or m[R, R] or inside_b > 0) and 300 <= m.sum() <= 0.6 * N * N
        if ok and (best is None or (inside_b, -m.sum()) > (best[2], -best[0].sum())):
            # постройки, наполовину внутри ограды или вплотную к ней, — в границу базы
            near = dilate(m, opt.get("extend", 8))  # АТП: постройки к северу от ограды — тоже база
            pts = list(fc)
            for c in blds:
                if sum(1 for a_, b_ in c if near[a_, b_]) >= 0.5 * len(c):
                    pts += [(b_, a_) for a_, b_ in c]
            hp = hull(pts)
            img = Image.new("1", (N, N), 0)
            ImageDraw.Draw(img).polygon(hp, fill=1)
            m = np.array(img, bool)
            line = Image.new("1", (N, N), 0)
            ImageDraw.Draw(line).line(hp + [hp[0]], fill=1, width=1)
            best = (m, np.array(line, bool), inside_b)
    if best is not None:
        area, ring = best[0], best[1]
        how = "по ограде"
    else:
        near = [c for c in blds if min(math.hypot(a_ - R, b_ - R) for a_, b_ in c) < 45]
        pts = [(b_, a_) for c in near for a_, b_ in c] or [(R, R)]
        img = Image.new("1", (N, N), 0)
        hp = hull(pts)
        if len(hp) >= 3:
            ImageDraw.Draw(img).polygon(hp, fill=1)
        area, how = dilate(np.array(img, bool), 6), "оболочка построек +6 (ограды нет)"
    # ворота: участки контура ограды без ограды и без стен построек (шире 1)
    gates = []
    if ring is not None:
        gap = ring & joined & ~dilate(fence, 1) & ~dilate(tall, 1)  # разрыв между кусками ограды (сшито через ≤ 6 блоков)
        if opt.get("open_gates"):  # свиноферма: проходы между коровниками и оградой — тоже ворота
            gap = ring & ~dilate(fence, 1) & ~tall
        for c in components(gap, 2):
            if len(c) > opt.get("gate_max", 8):  # длинный участок без ограды — открытая сторона, не ворота
                continue
            a_, b_ = c[len(c) // 2]
            gates.append({"pos": [int(b_ + cx - R), int(ground[a_, b_] + Y0 + 1), int(a_ + cz - R)], "width": len(c)})
    # здания внутри области
    out_b = []
    for i, c in enumerate(blds):
        inside = sum(1 for a, b in c if area[a, b]) / len(c)
        if inside < 0.6:
            continue
        zs = [a for a, _ in c]; xs = [b for _, b in c]
        g = int(np.median([ground[a, b] for a, b in c]))
        top = int(max(height[a, b] for a, b in c))
        mask = np.zeros((N, N), bool)
        for a, b in c: mask[a, b] = True
        # этажи: слои над землёй, где перекрытие закрывает ≥ 60 % площади
        layers = [h for h in range(1, top + 1) if g + h < H and ((vol[g + h] == 3) & mask).sum() >= 0.6 * len(c)]
        floors = max(1, sum(1 for k, h in enumerate(layers) if k == 0 or h - layers[k - 1] > 2) if layers else 1)
        doors = [(b, a) for a, b in c if ((vol[g + 1:g + 3, a, b] == 4).any() if g + 3 <= H else False)]
        fp = len(c)
        w, d = max(xs) - min(xs) + 1, max(zs) - min(zs) + 1
        kind = ("вышка" if fp <= 25 and top >= 8 else "ангар" if fp >= 250 and floors == 1 and top >= 6
                else "корпус" if fp >= 150 or floors >= 2 else "барак" if max(w, d) >= 2.5 * min(w, d) else "сарай" if fp < 40 else "дом")
        out_b.append({"id": "b%d" % (len(out_b) + 1), "type": kind, "footprint": fp, "floors": floors, "height": top,
                      "bbox": [min(xs) + cx - R, min(zs) + cz - R, max(xs) + cx - R, max(zs) + cz - R],
                      "center": [int(np.mean(xs)) + cx - R, g + Y0 + 1, int(np.mean(zs)) + cz - R],
                      "entrances": [[x + cx - R, g + Y0 + 1, z + cz - R] for x, z in doors[:6]],
                      "dist": math.hypot(np.mean(xs) - R, np.mean(zs) - R), "_cells": c})
    sectors = [] if opt.get("zone") else assign(out_b, ROLES.get(owner, ROLES["default"]), gates)
    return {"id": obj["id"], "name": obj.get("name"), "owner": owner, "method": how, "R": R, "center": [cx, cz],
            "area": [[x + cx - R, z + cz - R] for x, z in outline(area)], "area_cells": int(area.sum()), "gates": gates,
            "buildings": [{k: v for k, v in b.items() if not k.startswith("_") and k != "dist"} for b in out_b],
            "sectors": sectors}, (ground, area, fence, tall, out_b, gates)


def assign(blds, roles, gates):
    """Сектора по правилам Э1-14: каждое пригодное здание — хотя бы один сектор; крупное — несколько."""
    if not blds:
        return [{"id": "s1", "role": r, "building": None, "note": "без здания — на территории"} for r in roles[:3]]
    sec, used = [], {b["id"]: 0 for b in blds}
    by = sorted(blds, key=lambda b: -b["footprint"] * b["floors"])
    def put(role, b, why):
        used[b["id"]] += 1
        cap = max(2, b["footprint"] * b["floors"] // (12 * max(1, used[b["id"]])))
        sec.append({"id": "%s.s%d" % (b["id"], used[b["id"]]), "role": role, "building": b["id"], "capacity": cap, "why": why,
                    "points": {"work": b["center"], "entrances": b["entrances"][:2]}})
    rest = list(roles)
    def take(role, pred, why):
        if role not in rest: return
        cands = [b for b in by if pred(b) and used[b["id"]] == 0] or [b for b in by if pred(b)]
        if cands:
            put(role, cands[0], why); rest.remove(role)
    take("штаб", lambda b: b["type"] in ("корпус", "дом", "барак"), "главное здание")
    for r in ("склад", "гараж"):
        take(r, lambda b: b["type"] in ("ангар", "корпус"), "ангар/корпус")
    for r in ("казарма", "жильё"):
        take(r, lambda b: b["type"] in ("барак", "дом", "корпус"), "барак или этаж корпуса")
    take("оборона", lambda b: b["type"] == "вышка", "вышка")
    take("медпункт", lambda b: True, "ближе к центру") if False else None
    if "медпункт" in rest:
        c = sorted([b for b in blds if used[b["id"]] == 0] or blds, key=lambda b: b["dist"])
        put("медпункт", c[0], "ближе к центру"); rest.remove("медпункт")
    # пустые здания — оставшиеся роли (каждому зданию хотя бы одна функция)
    for b in sorted(blds, key=lambda b: b["dist"]):
        if used[b["id"]] == 0:
            r = rest.pop(0) if rest else ("склад" if b["type"] in ("сарай", "ангар") else "жильё")
            put(r, b, "свободное здание")
    # оставшиеся роли — вторыми секторами в здания с запасом места (этажи × площадь/120), иначе — на территории
    def room(b): return max(1, b["floors"] * math.ceil(b["footprint"] / 120)) - used[b["id"]]
    for r in list(rest):
        cand = sorted([b for b in blds if room(b) > 0], key=lambda b: -room(b))
        if cand:
            put(r, cand[0], "второй сектор здания с запасом места")
        else:
            sec.append({"id": "yard.s%d" % (len(sec) + 1), "role": r, "building": None, "capacity": 2,
                        "why": "нет свободного здания — на территории (палатка, навес, костёр)"})
        rest.remove(r)
    if gates and "оборона" in roles and not any(s["role"] == "оборона" for s in sec):
        sec.append({"id": "gate.s1", "role": "оборона", "building": None, "capacity": 2 * len(gates), "why": "посты у ворот",
                    "points": {"guard": [g["pos"] for g in gates]}})
    return sec


def preview(res, dbg, dst):
    ground, area, fence, tall, blds, gates = dbg
    N = ground.shape[0]
    S = 4
    g = ground.astype(float)
    g = (g - g.min()) / max(1, g.max() - g.min())
    rgb = np.stack([60 + 60 * g, 80 + 70 * g, 50 + 40 * g], -1)
    rgb[area] = rgb[area] * 0.7 + np.array([200, 190, 120]) * 0.3
    rgb[tall] = [110, 110, 110]
    rgb[fence] = [30, 30, 30]
    secs = {}
    for s in res["sectors"]:
        if s["building"]:
            secs.setdefault(s["building"], []).append(s["role"])
    for b in blds:
        roles = secs.get(b["id"], [])
        c = COL.get(roles[0], (150, 150, 150)) if roles else (150, 150, 150)
        for a, x in b["_cells"]:
            rgb[a, x] = np.array(c) * 0.75
    im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).resize((N * S, N * S), Image.NEAREST)
    pad = 60
    canvas = Image.new("RGB", (N * S + 300, N * S + pad), (25, 25, 25))
    canvas.paste(im, (0, pad))
    d = ImageDraw.Draw(canvas)
    F = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 15)
    f2 = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 13)
    cx, cz, R = res["center"][0], res["center"][1], res["R"]
    poly = [((x - cx + R) * S + S // 2, (z - cz + R) * S + S // 2 + pad) for x, z in res["area"]]
    if len(poly) >= 3:
        d.line(poly + [poly[0]], fill=(255, 230, 80), width=2)
    for gt in gates:
        x, z = (gt["pos"][0] - cx + R) * S, (gt["pos"][2] - cz + R) * S + pad
        d.rectangle((x - 6, z - 6, x + 6, z + 6), outline=(255, 80, 80), width=3)
    for b in blds:
        x, z = (b["center"][0] - cx + R) * S, (b["center"][2] - cz + R) * S + pad
        d.text((x - 10, z - 8), b["id"], font=F, fill=(255, 255, 255))
    d.text((6, 6), "%s — граница: %s; зданий %d, ворот %d" % (res["name"], res["method"], len(blds), len(gates)), font=F, fill=(240, 240, 230))
    d.text((6, 30), "жёлтая линия — граница базы, красные квадраты — ворота, тёмное — ограда, цвет здания — главный сектор", font=f2, fill=(200, 200, 190))
    y = pad + 4
    for b in res["buildings"]:
        roles = ", ".join(s["role"] for s in res["sectors"] if s["building"] == b["id"])
        d.text((N * S + 10, y), "%s %s, %d эт., %d м²" % (b["id"], b["type"], b["floors"], b["footprint"]), font=F, fill=(240, 240, 230))
        d.text((N * S + 10, y + 18), roles or "—", font=f2, fill=COL.get(roles.split(", ")[0], (200, 200, 200)) if roles else (200, 200, 200))
        y += 42
    for s in res["sectors"]:
        if not s["building"]:
            d.text((N * S + 10, y), "территория: " + s["role"], font=f2, fill=(200, 200, 190)); y += 18
    canvas.save(dst)


OWNER = {"cordon.base.village": "loners", "cordon.base.south_post": "military", "cordon.outpost.atp": "bandits",
         "cordon.base.far_post": "bandits", "cordon.outpost.farm": "local", "cordon.outpost.elevator": "default"}
# роли мест Кордона (Э1-07, утверждено 16.11): АТП и свиноферма — базы, элеватор — зона потенциала без секторов
OPT = {"cordon.outpost.atp": {"extend": 26}, "cordon.outpost.farm": {"open_gates": True, "gate_max": 12},
       "cordon.outpost.elevator": {"zone": True}}


def main():
    work, out = sys.argv[1], sys.argv[2]
    ids = sys.argv[3:] or list(OWNER)
    os.makedirs(out, exist_ok=True)
    atlas = json.load(open(os.path.join(ROOT, "src/main/resources/data/stalkerzone/atlas/locations/cordon.json"), encoding="utf-8"))
    objs = {o["id"]: o for o in atlas["objects"]}
    allres = []
    for i in ids:
        o = objs[i]
        owner = o.get("owner") if isinstance(o.get("owner"), str) and o.get("owner") in ROLES else OWNER.get(i, "default")
        res, dbg = layout(work, o, owner, opt=OPT.get(i))
        json.dump(res, open(os.path.join(out, i + ".json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, default=int)
        preview(res, dbg, os.path.join(out, i + ".png"))
        allres.append(res)
        print("%-28s %-34s зданий %2d, ворот %d, секторов %d" % (i, res["method"], len(res["buildings"]), len(res["gates"]), len(res["sectors"])))
    return allres


if __name__ == "__main__":
    main()
