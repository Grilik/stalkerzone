#!/usr/bin/env python3
"""Э2-16: тонкие заборы Кордона в мире TheZone вместо стенок из полных блоков.

Варианты (принял пользователь 16.11):
  А  деревня новичков — серые старые доски: framed_pane с камуфляжем birch_planks (в паке TheZone — серые
     выветренные доски), 2 блока; столб каждые 4 клетки и на углах — framed_pane с камуфляжем oak_log, 3 блока;
     2 дыры во весь рост и 1 лаз (нет нижней доски).
  Б  Южный блокпост — сетка-рабица: stalkerzone:prop_chainlink (секция 3×2 на трубах, колючка поверху),
     корпус секции — stalkerzone:prop_hitbox (как ObjPropBlock.placeInChunk); остатки линии и углы —
     zm_grating_pane в 2 блока и колючка поверху — ванильная цепь вдоль линии (minecraft:chain).
  В  АТП, Дальний блокпост, стенка под мостом — плиты ПО-2: framed_pane с камуфляжем concrete_fence, 2 блока;
     на АТП 2 выбитые плиты (нет верхнего ряда на 2 клетках) и 1 пролом (2 клетки пусто).
Проходы и ворота (iron_bars) не трогаются: меняются только колонки сплошной стенки.

  python -I tools/thezone/fences.py plan   <мир> [--sites village,south,atp,far,bridge] [--map папка]
  python -I tools/thezone/fences.py apply  <мир> [--sites …] [--dry] [--probe X0,Z0,X1,Z1]
  python -I tools/thezone/fences.py render <мир> <папка PNG> --pack <папка пака с assets/minecraft/textures/block>

plan   — список колонок и блоков по местам, карта сверху для ручной проверки (--map).
apply  — запись. В рабочую копию (есть stalkerzone/WORKCOPY) — только через журнал world_edit_log
         (резерв регионов, md5, откат); иначе (опытная копия) — прямо. --dry: только числа, мир не трогается.
         --probe — только колонки в прямоугольнике (проба одной секции).
Мир при записи должен быть закрыт. Колонки ищутся по самому миру (до правки): земля — верхний природный блок.
"""
import argparse, collections, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import anvil as A  # noqa: E402

# место: (вариант, x0, x1, z0, z1, материалы стенки, исключённые колонки (x, z))
SITES = {
    "village": ("A", -4428, -4355, 3793, 3849, {"oak_planks", "spruce_planks", "birch_planks", "oak_slab", "oak_fence"}, set()),
    "south": ("B", -4353, -4306, 3983, 4019, {"oak_planks", "quartz_block", "quartz_slab"}, set()),
    "atp": ("C", -4062, -4012, 3653, 3702, {"oak_fence", "smooth_stone_slab"}, set()),
    "far": ("C", -4158, -4130, 2888, 2914, {"smooth_stone_slab"}, set()),
    "bridge": ("C", -4168, -4146, 3478, 3482, {"quartz_block", "quartz_slab"}, set()),
}
# Э2-19: оставшиеся ванильные заборы Кордона (поиск по всему атласу cordon, заход 2). Колонки — find_simple:
# от земли вверх подряд блоки из материалов места, первый — из них же (над забором может висеть крона).
SITES.update({
    "farm_w": ("A", -4204, -4201, 3315, 3337, {"oak_fence"}, set()),       # свиноферма, западная линия
    "farm_c": ("A", -4189, -4189, 3320, 3332, {"oak_fence"}, set()),       # свиноферма, между коровниками (ворота 3325-3327)
    "lake": ("A", -4702, -4648, 3536, 3580, {"oak_fence"}, set()),         # хутор у озера (запад Кордона)
    "south_e": ("B", -4322, -4170, 3983, 3987, {"oak_fence"}, set()),      # линия Южного блокпоста на восток
    "south_w": ("B", -4476, -4375, 3976, 3988, {"oak_fence"}, set()),      # линия Южного блокпоста на запад
    "atp_tree": ("C", -4059, -4055, 3696, 3700, {"oak_fence", "smooth_stone_slab"}, set()),  # АТП у дерева
    "far_e": ("C", -4131, -4116, 2909, 2909, {"oak_fence"}, set()),        # Дальний блокпост, южная линия на восток
    "bridge_w": ("C", -4173, -4169, 3480, 3480, {"quartz_block", "quartz_slab"}, set()),  # под мостом, западный конец
    "south_fix": ("B", -4352, -4320, 3983, 4019, set(), set()),             # углы рабицы Южного блокпоста
})
SIMPLE = {"farm_w", "farm_c", "lake", "south_e", "south_w", "atp_tree", "far_e", "bridge_w"}
FIX = {"south_fix"}
ATTACHED = ("wall_sign", "wall_torch", "wall_banner")  # висят на снимаемой стенке — снять вместе с ней
# просмотрено вручную (Э2-16, этап 3): куски стенки, которые автопоиск отсекает (короткий кусок, примыкание к дому),
# но это забор, а не стена дома
INCL = {
    "far": {(-4155, 2912), (-4154, 2912), (-4153, 2912), (-4146, 2892), (-4145, 2892), (-4144, 2892),
            (-4136, 2892), (-4135, 2892)},
}
GATE = {"iron_bars"}
SOFT = {"air", "cave_air", "grass", "fern", "tall_grass", "large_fern", "dead_bush", "poppy", "dandelion", "snow",
        "spruce_sapling", "oak_sapling", "birch_sapling", "sweet_berry_bush", "brown_mushroom", "red_mushroom"}
GROUND = {"grass_block", "dirt", "coarse_dirt", "podzol", "gravel", "stone", "sand", "clay", "mud", "rooted_dirt",
          "dirt_path", "cobblestone", "mossy_cobblestone", "andesite", "granite", "diorite", "red_sand", "farmland"}
THIN = ("slab", "stairs", "fence", "pane", "bars", "door", "trapdoor", "carpet", "wall", "sign", "ladder", "torch",
        "button", "plate", "rail", "lantern", "chain", "rod", "head", "skull", "banner", "bed", "flower_pot")

PANE = "framedblocks:framed_pane[east=%s,glowing=false,locked=false,north=%s,propagates_skylight=false,south=%s,waterlogged=false,west=%s]"
GRATING = "stalkerzone:zm_grating_pane[east=%s,north=%s,south=%s,waterlogged=false,west=%s]"
CHAIN = "stalkerzone:prop_chainlink[facing=%s]"
HITBOX = "stalkerzone:prop_hitbox"
WIRE = "minecraft:chain[axis=%s,waterlogged=false]"
BOARDS, POST, PO2 = "minecraft:birch_planks", "minecraft:oak_log[axis=y]", "stalkerzone:concrete_fence"
DIRS = {"east": (1, 0), "north": (0, -1), "south": (0, 1), "west": (-1, 0)}


def bid(s):
    return s.split("[")[0].replace("minecraft:", "")


class World:
    """Чтение блоков мира по чанкам (кэш)."""

    def __init__(self, root):
        self.root, self.cache = root, {}

    def camo(self, x, y, z):
        """Камуфляж framed-блока в точке ('minecraft:…') или None (для превью)."""
        ch = self.chunk(x >> 4, z >> 4)
        be = A.block_entity(ch, x, y, z) if ch else None
        try:
            return be["camo"][1]["state"][1]["Name"][1]
        except (TypeError, KeyError):
            return None

    def chunk(self, cx, cz):
        k = (cx, cz)
        if k not in self.cache:
            p = os.path.join(self.root, "region", A.region_name(cx, cz))
            r = A.read_chunk(p, cx, cz) if os.path.exists(p) else None
            self.cache[k] = r[1] if r else None
        return self.cache[k]

    def get(self, x, y, z):
        k = (x >> 4, z >> 4, y >> 4)
        if k not in self.cache:
            ch = self.chunk(x >> 4, z >> 4)
            sec = A._section(ch, y) if ch else None
            if sec is None or "block_states" not in sec:
                self.cache[k] = (["minecraft:air"], None)
            else:
                bs = sec["block_states"][1]
                pal = [A.state_str(s) for s in bs["palette"][1][1]]
                self.cache[k] = (pal, A._unpack(bs["data"][1], A._bits(len(pal))) if "data" in bs else None)
        pal, idx = self.cache[k]
        return pal[idx[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]] if idx else pal[0]


def column(w, x, z, top=110, bottom=50):
    """-> (земля y, [состояния над землёй до первого воздушного разрыва вверх на 8])."""
    ys = [(y, w.get(x, y, z)) for y in range(top, bottom, -1)]
    g = next((y for y, s in ys if bid(s) in GROUND), None)
    if g is None:
        return None, []
    st = [w.get(x, y, z) for y in range(g + 1, g + 10)]
    return g, st


def find(w, site):
    """Колонки сплошной стенки места: {(x, z): (земля, [состояния стенки])}."""
    var, x0, x1, z0, z1, mats, excl = SITES[site]
    out, tall = {}, set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g, st = column(w, x, z)
            if g is None:
                continue
            h = 0
            while h < len(st) and bid(st[h]) not in SOFT:
                h += 1
            if sum(bid(s) not in SOFT for s in st) >= 4:
                tall.add((x, z))
            if not (2 <= h <= 3 or h == 1 and bid(st[0]) in ("oak_fence", "iron_bars")):
                continue
            if any(bid(s) not in SOFT for s in st[h:]):
                continue
            if not all(bid(s) in mats | GATE for s in st[:h]):
                continue
            out[(x, z)] = (g, st[:h])
    # стена дома, прижатая к высоким колонкам, и «пятна» (площадки, руины) — не забор
    out = {k: v for k, v in out.items()
           if not any((k[0] + dx, k[1] + dz) in tall for dx in (-1, 0, 1) for dz in (-1, 0, 1))
           and not any(all((k[0] + sx * a, k[1] + sz * b) in out for a in (0, 1) for b in (0, 1))
                       for sx in (-1, 1) for sz in (-1, 1))}  # клетка квадрата 2×2 — площадка, не забор
    out = {k: v for k, v in out.items() if k not in excl}
    seen, keep = set(), {}
    for k in out:  # связные куски меньше 4 колонок — не забор
        if k in seen:
            continue
        comp, stack = [k], [k]
        seen.add(k)
        while stack:
            x, z = stack.pop()
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    q = (x + dx, z + dz)
                    if q in out and q not in seen:
                        seen.add(q); comp.append(q); stack.append(q)
        if len(comp) >= 4:
            keep.update({q: out[q] for q in comp})
    for k in INCL.get(site, ()):
        g, st = column(w, *k)
        h = 0
        while h < len(st) and bid(st[h]) not in SOFT:
            h += 1
        if g is not None and 1 <= h <= 3 and all(bid(s) in mats for s in st[:h]):
            keep[k] = (g, st[:h])
    return keep


def find_simple(w, site):
    """Э2-19: колонки забора из материалов места: подряд от земли вверх, первый блок — из материалов."""
    var, x0, x1, z0, z1, mats, excl = SITES[site]
    out = {}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g, st = column(w, x, z)
            if g is None or (x, z) in excl:
                continue
            h = 0
            while h < len(st) and bid(st[h]) in mats:
                h += 1
            if h:
                out[(x, z)] = (g, st[:h])
    return out


def _joins(b):
    """Соединяется ли панель/решётка с соседним блоком b (уже стоящим в мире)."""
    return b == "iron_bars" or b.endswith("_pane") or "prop_" in b or (b not in SOFT and not any(t in b for t in THIN))


def _props(s):
    return dict(p.split("=", 1) for p in s[:-1].split("[", 1)[1].split(",")) if "[" in s else {}


def _with(s, props):
    return s.split("[")[0] + "[" + ",".join("%s=%s" % kv for kv in sorted(props.items())) + "]"


def fix_grating(w, site):
    """Э2-19: решётки zm_grating_pane места — соединения заново (с рабицей и корпусом пропса тоже),
    колючка над углом — вертикальная цепь (столб), а не поперёк линии."""
    var, x0, x1, z0, z1 = SITES[site][:5]
    edits = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g, st = column(w, x, z)
            if g is None:
                continue
            for dy, s in enumerate(st, 1):
                y = g + dy
                if bid(s) != "stalkerzone:zm_grating_pane":
                    continue
                pr = _props(s)
                con = {d: _joins(bid(w.get(x + dx, y, z + dz))) for d, (dx, dz) in DIRS.items()}
                pr.update({d: str(v).lower() for d, v in con.items()})
                ns = _with(s, pr)
                if A.state_str(A._state(ns)) != A.state_str(A._state(s)):
                    edits.append((x, y, z, ns, None))
                up = w.get(x, y + 1, z)
                if bid(up) == "chain" and (con["east"] or con["west"]) and (con["north"] or con["south"]):
                    pu = _props(up)
                    if pu.get("axis") != "y":
                        pu["axis"] = "y"
                        edits.append((x, y + 1, z, _with(up, pu), None))
    return {}, edits, ["решёток и цепей поправлено %d" % len(edits)]


def nbrs(cells, k):
    return {d: (k[0] + dx, k[1] + dz) in cells for d, (dx, dz) in DIRS.items()}


def runs(cells, wall):
    """Прямые пролёты: [(ось 'x'|'z', [клетки по порядку])] из клеток wall, у которых соседи только вдоль оси."""
    out = []
    for ax in ("x", "z"):
        line = sorted(k for k in wall if (lambda n: (n["east"] or n["west"]) and not (n["north"] or n["south"]) if ax == "x"
                                            else (n["north"] or n["south"]) and not (n["east"] or n["west"]))(nbrs(cells, k)))
        line.sort(key=lambda k: (k[1], k[0]) if ax == "x" else (k[0], k[1]))
        cur = []
        for k in line:
            if cur and (k[0] - cur[-1][0], k[1] - cur[-1][1]) == ((1, 0) if ax == "x" else (0, 1)):
                cur.append(k)
            else:
                if cur: out.append((ax, cur))
                cur = [k]
        if cur: out.append((ax, cur))
    return out


def pick(run_list, n, margin=2, width=1):
    """n мест для дыр: в длинных пролётах, не ближе margin к концам; равномерно по списку (детерминированно)."""
    cand = []
    for ax, r in run_list:
        if len(r) >= 2 * margin + width + 2:
            cand += [r[i:i + width] for i in range(margin, len(r) - margin - width + 1)]
    if not cand:
        return []
    return [cand[int((i + 0.5) * len(cand) / n)] for i in range(n)]


def plan(w, site):
    """-> (колонки, правки [(x, y, z, состояние, камуфляж|None)], заметки)."""
    var = SITES[site][0]
    if site in FIX:
        return fix_grating(w, site)
    cells = find_simple(w, site) if site in SIMPLE else find(w, site)
    wall = {k for k, (g, st) in cells.items() if not any(bid(s) in GATE for s in st)}
    rl = runs(cells, wall)
    new = {}  # (x, y, z) -> (состояние-шаблон, камуфляж); шаблон PANE/GRATING — соединения потом
    notes = []
    holes, crawl, knocked = set(), set(), set()
    if var == "A":
        posts = set()
        for ax, r in rl:
            for i, k in enumerate(r):
                if (k[0] if ax == "x" else k[1]) % 4 == 0:
                    posts.add(k)
        posts |= {k for k in wall if not any(k in r for _, r in rl)}  # углы, тройники, концы
        hp = pick(rl, 3, margin=3)
        for i, c in enumerate(hp):
            (crawl if i == 1 else holes).update(c)
        posts -= holes | crawl
        for k in wall:
            g = cells[k][0]
            if k in holes:
                continue
            for dy in (1, 2, 3) if k in posts else (1, 2):
                if k in crawl and dy == 1:
                    continue
                new[(k[0], g + dy, k[1])] = ("PANE", POST if k in posts else BOARDS)
        notes.append("дыры %s, лаз %s, столбов %d" % (sorted(holes), sorted(crawl), len(posts)))
    elif var == "C":
        if site == "atp":
            for i, c in enumerate(pick(rl, 3, margin=3, width=2)):
                (holes if i == 1 else knocked).update(c)
        for k in wall:
            g = cells[k][0]
            if k in holes:
                continue
            for dy in (1,) if k in knocked else (1, 2):
                new[(k[0], g + dy, k[1])] = ("PANE", PO2)
        if site == "atp":
            notes.append("пролом %s, выбитые плиты %s" % (sorted(holes), sorted(knocked)))
    elif var == "B":
        used = set()
        for ax, r in rl:
            for i in range(0, len(r) - 2, 3):
                a, c, b = r[i], r[i + 1], r[i + 2]
                g = cells[c][0]
                if not (cells[a][0] == g == cells[b][0]):
                    continue  # секция только на ровной земле
                new[(c[0], g + 1, c[1])] = (CHAIN % ("south" if ax == "x" else "east"), None)
                new[(c[0], g + 2, c[1])] = (HITBOX, None)
                for e in (a, b):
                    new[(e[0], g + 1, e[1])] = (HITBOX, None)
                    new[(e[0], g + 2, e[1])] = (HITBOX, None)
                used |= {a, b, c}
        rest = wall - used
        for k in rest:
            g = cells[k][0]
            for dy in (1, 2):
                new[(k[0], g + dy, k[1])] = ("GRATING", None)
            n = nbrs(cells, k)  # колючка поверху: ванильная цепь вдоль линии забора (своей колючки в моде нет)
            corner = (n["east"] or n["west"]) and (n["north"] or n["south"])  # угол — столб (Э2-19)
            new[(k[0], g + 3, k[1])] = (WIRE % ("y" if corner else "x" if n["east"] or n["west"] else "z"), None)
        notes.append("секций рабицы %d, клеток решётки %d" % (len(used) // 3, len(rest)))
    # снять старую стенку
    edits = []
    for k in wall:
        g, st = cells[k]
        for dy in range(1, len(st) + 1):
            if (k[0], g + dy, k[1]) not in new:
                edits.append((k[0], g + dy, k[1], "minecraft:air", None))
            for dx, dz in DIRS.values():  # таблички и факелы на снимаемой стенке (Э2-19)
                q = (k[0] + dx, g + dy, k[1] + dz)
                if (q[0], q[2]) not in wall and any(t in bid(w.get(*q)) for t in ATTACHED):
                    edits.append((q[0], q[1], q[2], "minecraft:air", None))
    # соединения панелей и решёток
    for (x, y, z), (tmpl, camo) in new.items():
        if tmpl in ("PANE", "GRATING"):
            con = {}
            for d, (dx, dz) in DIRS.items():
                q = (x + dx, y, z + dz)
                if q in new:
                    con[d] = new[q][0] in ("PANE", "GRATING") or tmpl == "GRATING"  # решётка цепляется и за рабицу (Э2-19)
                else:
                    gone = (q[0], q[2]) in wall
                    b = bid("minecraft:air" if gone else w.get(*q))
                    con[d] = _joins(b) and (tmpl == "GRATING" or "prop_" not in b)
            v = [str(con[d]).lower() for d in ("east", "north", "south", "west")]
            st = (PANE if tmpl == "PANE" else GRATING) % tuple(v)
            edits.append((x, y, z, st, camo))
        else:
            edits.append((x, y, z, tmpl, camo))
    # соседние уже стоящие панели и решётки — соединение в сторону новых (Э2-19); камуфляж сохраняется (KEEP)
    fix = {}
    for (x, y, z), (tmpl, camo) in new.items():
        if tmpl not in ("PANE", "GRATING") and not (bid(tmpl).startswith("stalkerzone:prop_") and y <= cells[(x, z)][0] + 2):
            continue
        for d, (dx, dz) in DIRS.items():
            q = (x + dx, y, z + dz)
            if q in new or (q[0], q[2]) in wall:
                continue
            s0 = fix.get(q, (w.get(*q),))[0]
            b = bid(s0)
            if b not in ("framedblocks:framed_pane", "stalkerzone:zm_grating_pane") or (
                    tmpl not in ("PANE", "GRATING") and b != "stalkerzone:zm_grating_pane"):
                continue  # к рабице подключается только решётка
            back = {"east": "west", "west": "east", "north": "south", "south": "north"}[d]
            pr = _props(s0)
            if pr.get(back) != "true":
                pr[back] = "true"
                fix[q] = (_with(s0, pr), "KEEP" if b.startswith("framedblocks") else None)
    edits += [(q[0], q[1], q[2], s1, c) for q, (s1, c) in fix.items()]
    edits = list({(e[0], e[1], e[2]): e for e in edits}.values())  # одна правка на блок, последняя главнее
    if fix:
        notes.append("соседних панелей подключено %d" % len(fix))
    return cells, edits, notes


def apply(world, edits, why, dry=False):
    """Запись правок. Рабочая копия — через журнал Э1-03."""
    by = collections.defaultdict(list)
    for e in edits:
        by[(e[0] >> 4, e[2] >> 4)].append(e)
    files = sorted({"region/" + A.region_name(cx, cz) for cx, cz in by})
    stat = collections.Counter(bid(e[3]) for e in edits)
    print("правок %d, чанков %d, регионов %d %s" % (len(edits), len(by), len(files), files))
    print("  по блокам:", dict(stat))
    if dry:
        return
    work = os.path.exists(os.path.join(world, "stalkerzone", "WORKCOPY"))
    eid = None
    if work:
        import world_edit_log as L
        wl = L.Work(world)
        eid = wl.begin("%s тонкие заборы: %d блоков" % (why.split(":")[0], len(edits)), why, files)
    try:
        for (cx, cz), es in sorted(by.items()):
            p = os.path.join(world, "region", A.region_name(cx, cz))
            name, ch = A.read_chunk(p, cx, cz)
            for x, y, z, st, camo in es:
                if A.block_entity(ch, x, y, z) is not None and camo is None and not (
                        bid(st) == "air" and any(t in A.get_block(ch, x, y, z) for t in ATTACHED)):
                    raise SystemExit("в (%d %d %d) блок-сущность — не трогаю" % (x, y, z))
                if camo == "KEEP":  # та же блок-сущность (камуфляж), другое состояние
                    be = A.block_entity(ch, x, y, z)
                    A.set_block(ch, x, y, z, st)
                    if be is not None:
                        ch["block_entities"][1][1].append(be)
                elif camo:
                    A.set_framed(ch, x, y, z, st, camo)
                else:
                    A.set_block(ch, x, y, z, st)
            A.relight(ch)
            A.write_chunk(p, cx, cz, name, ch)
            ch2 = A.read_chunk(p, cx, cz)[1]  # сверка
            for x, y, z, st, camo in es:
                got = A.get_block(ch2, x, y, z)
                if got != A.state_str(A._state(st)):
                    raise RuntimeError("(%d %d %d): %s вместо %s" % (x, y, z, got, st))
                if camo and A.block_entity(ch2, x, y, z) is None:
                    raise RuntimeError("(%d %d %d): нет блок-сущности" % (x, y, z))
    except BaseException:
        if work:
            wl.undo(eid)
            print("откат", eid)
        raise
    if work:
        wl.commit(eid)
        print("журнал:", eid)
    print("записано")


def top_map(w, site, cells, edits, dst):
    """Карта сверху: серое — стенка до правки, цвет — что встанет. Для ручной проверки списка колонок."""
    from PIL import Image, ImageDraw
    var, x0, x1, z0, z1 = SITES[site][:5]
    S = 10
    img = Image.new("RGB", ((x1 - x0 + 1) * S, (z1 - z0 + 1) * S), (40, 52, 36))
    d = ImageDraw.Draw(img)
    top = {}
    for x, y, z, st, camo in edits:
        cur = top.get((x, z), ("", -999))
        if (bid(st) != "air", y) > (bid(cur[0]) not in ("air", ""), cur[1]):
            top[(x, z)] = (st if not camo else camo, y)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g, st = column(w, x, z)
            n = sum(bid(s) not in SOFT for s in st)
            c = (40, 52, 36) if n == 0 else (90 + 20 * min(n, 6), 80 + 15 * min(n, 6), 70)
            if (x, z) in cells:
                c = (150, 150, 150)
            if (x, z) in top:
                t = top[(x, z)][0]
                c = {"air": (230, 40, 40)}.get(bid(t), (200, 200, 90) if "oak_log" in t else (120, 200, 230) if "birch" in t else
                                                  (240, 240, 240) if "concrete" in t else (230, 140, 60) if "chain" in t else
                                                  (60, 60, 200) if "hitbox" in t else (160, 90, 200))
            d.rectangle([(x - x0) * S, (z - z0) * S, (x - x0) * S + S - 2, (z - z0) * S + S - 2], fill=c)
    for i in range(0, x1 - x0 + 1, 8):
        d.text((i * S + 1, 1), str(x0 + i), fill=(255, 255, 0))
    for i in range(0, z1 - z0 + 1, 8):
        d.text((1, i * S + 1), str(z0 + i), fill=(255, 255, 0))
    img.save(dst)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["plan", "apply", "render"])
    ap.add_argument("world")
    ap.add_argument("out", nargs="?")
    ap.add_argument("--sites", default=",".join(SITES))
    ap.add_argument("--map")
    ap.add_argument("--dry", action="store_true")
    ap.add_argument("--probe")
    ap.add_argument("--pack")
    ap.add_argument("--why", default="Э2-16: заборы Кордона тоньше, как в оригинале")
    o = ap.parse_args()
    w = World(o.world)
    allp = {}
    for s in o.sites.split(","):
        cells, edits, notes = plan(w, s)
        if o.probe:
            px0, pz0, px1, pz1 = map(int, o.probe.split(","))
            edits = [e for e in edits if px0 <= e[0] <= px1 and pz0 <= e[2] <= pz1]
        allp[s] = (cells, edits)
        blocks = sum(len(v[1]) for v in cells.values())
        print("%-8s вариант %s: колонок %d, блоков стенки %d, правок %d; %s" % (s, SITES[s][0], len(cells), blocks, len(edits), "; ".join(notes)))
        if o.map:
            os.makedirs(o.map, exist_ok=True)
            top_map(w, s, cells, edits, os.path.join(o.map, "map-%s.png" % s))
    if o.cmd == "apply":
        merged = {}  # одна правка на блок; две правки соединений одной решётки (разные места) — объединить
        for c, es in allp.values():
            for e in es:
                k = e[:3]
                if k in merged and bid(e[3]) == bid(merged[k][3]) and bid(e[3]).endswith('_pane'):
                    p0, p1 = _props(merged[k][3]), _props(e[3])
                    p0.update({d: 'true' for d in DIRS if p1.get(d) == 'true'})
                    e = e[:3] + (_with(e[3], p0),) + e[4:]
                merged[k] = e
        apply(o.world, list(merged.values()), o.why, o.dry)
    elif o.cmd == "render":
        import fences_render
        fences_render.render_all(w, allp, o.out, o.pack, o.why.split(":")[0] + ": " + fences_render.TITLE)


if __name__ == "__main__":
    main()
