"""Э1-05: разметка уровней оригинала (Anomaly 1.5.x / CoC) — смарт-террейны с типом и группировкой.

Источник — файлы игры пользователя, распакованные tools/xray/xdb.py вне git (C:\\MineStalker\\stalker_dump\\anomaly):
  spawns/all.spawn                          — объекты ALife: smart_terrain, level_changer, camp_zone, campfire,
                                              anomal_zone и аномалии zone_*, торговцы (имя с «trader»);
  levels/<level>/level.game                 — пути смарт-террейнов: kamp, sleep, guard/grd/sniper, comander,
                                              walker/patrol (первая точка пути);
  configs/misc/simulation_objects_props.ltx — тип смарт-террейна (base/territory/resource/lair/surge) и кто может
                                              занимать (флаги группировок);
  configs/misc/simulation.ltx               — стартовые отряды → стартовая группировка.
Выход — src/main/resources/data/stalkerzone/atlas/original/<level>.json: только ID объектов, типы, группировки и
координаты в ориентации Minecraft (решение б2): original = [xr_x, xr_y, −xr_z]. Данных игры (геометрии, текстов,
конфигов) в выходе нет. Статус всех объектов — observed: перенос на TheZone — Э1-06/Э1-07.

  python -I import_level.py <папка распаковки> <папка выхода> [уровень ...]   (без уровней — все из LEVELS)
"""
import collections, json, math, os, re, struct, sys

# префикс имён объектов -> уровень Anomaly
PREFIX = {
    "esc": "l01_escape", "gar": "l02_garbage", "agr": "l03_agroprom", "val": "l04_darkvalley", "bar": "l05_bar",
    "ros": "l06_rostok", "mil": "l07_military", "yan": "l08_yantar", "cit": "l09_deadcity", "lim": "l10_limansk",
    "rad": "l10_radar", "red": "l10_red_forest", "hos": "l11_hospital", "aes": "l12_stancia", "aes2": "l12_stancia_2",
    "gen": "l13_generators", "mar": "k00_marsh", "ds": "k01_darkscape", "ds2": "k01_darkscape",
    "trc": "k02_trucks_cemetery", "zat": "zaton", "jup": "jupiter", "pol": "y04_pole",
    # имена переходов ЗП и Anomaly без короткого префикса
    "zaton": "zaton", "jupiter": "jupiter", "pripyat": "pripyat", "cop": "pripyat", "katacomb": "l11_hospital",
    "tc": "k02_trucks_cemetery",
}
LEVELS = ["l01_escape", "l02_garbage", "l03_agroprom", "l04_darkvalley", "l05_bar", "l06_rostok", "l07_military",
          "l08_yantar", "l09_deadcity", "l10_limansk", "l10_radar", "l10_red_forest", "l11_hospital", "l11_pripyat",
          "l12_stancia", "l12_stancia_2", "l13_generators", "k00_marsh", "k01_darkscape", "zaton", "jupiter", "pripyat"]
FACTION_TOKENS = [  # порядок важен: первое совпадение
    ("monolith", "monolith"), ("army", "military"), ("military", "military"), ("dolg", "duty"), ("duty", "duty"),
    ("freedom", "freedom"), ("svoboda", "freedom"), ("ecolog", "scientists"), ("scientist", "scientists"),
    ("csky", "clear_sky"), ("killer", "mercs"), ("merc", "mercs"), ("renegade", "renegades"), ("greh", "sin"),
    ("isg", "isg"), ("zombied", "zombied"), ("bandit", "bandits"), ("stalker", "loners"), ("loner", "loners"),
    ("trader", "traders"),
]
MONSTER_TOKENS = ("dog", "boar", "flesh", "bloodsucker", "snork", "controller", "poltergeist", "chimera", "burer",
                  "pseudodog", "tushkano", "cat", "fracture", "zombie", "giant", "mutant", "monster", "rat", "karlik",
                  "psy_dog", "lurker", "psysucker", "bibliotekar", "borya", "simulation_mix")
PROP_FACTIONS = {"stalker": "loners", "bandit": "bandits", "dolg": "duty", "freedom": "freedom", "ecolog": "scientists",
                 "killer": "mercs", "army": "military", "monolith": "monolith", "csky": "clear_sky", "zombied": "zombied",
                 "greh": "sin", "isg": "isg", "renegade": "renegades"}
POINT_KEYS = [  # (ключ points, шаблон хвоста имени пути после «<smart>_»)
    ("campfire", r"kamp_\d+$"), ("beds", r"sleep_\d+$"), ("guards", r"(guard|grd|sniper)(_\d+)?_walk$"),
    ("flag", r"comander_walk$"), ("patrol", r"(walker|wlkr|pat|ptrl|patx|specwalker|camper|patrol)(_\d+)?_walk(_\d+)?$"),
]


def mc(p):
    x, y, z = p
    return [round(x, 1), round(y, 1), round(-z, 1)]


# ---------------- ltx ----------------
def ltx(path):
    """Секции с наследованием «[имя]:родитель1,родитель2» -> {имя: {ключ: значение}} (сначала родители)."""
    raw, order, parents = {}, [], {}
    cur = None
    for line in open(path, encoding="cp1251", errors="replace"):
        line = line.split(";", 1)[0].strip()
        if not line:
            continue
        m = re.match(r"\[([^\]]+)\]\s*(?::\s*(.*))?$", line)
        if m:
            cur = m.group(1).strip()
            raw[cur] = {}
            order.append(cur)
            parents[cur] = [p.strip() for p in (m.group(2) or "").split(",") if p.strip()]
            continue
        if cur is None:
            continue
        if "=" in line:
            k, v = line.split("=", 1)
            raw[cur][k.strip()] = v.strip()
        else:
            raw[cur][line] = ""

    memo = {}

    def resolve(s, depth=0):
        if s in memo:
            return memo[s]
        d = {}
        for p in parents.get(s, []):
            if p in raw and depth < 20:
                d.update(resolve(p, depth + 1))
        d.update(raw.get(s, {}))
        memo[s] = d
        return d

    return {s: resolve(s) for s in order}


def start_faction(squads):
    c = collections.Counter()
    monsters = 0
    for sq in squads:
        low = sq.lower()
        for tok, fac in FACTION_TOKENS:
            if tok in low:
                c[fac] += 1
                break
        else:
            if any(t in low for t in MONSTER_TOKENS):
                monsters += 1
    if c:
        return c.most_common(1)[0][0], dict(c), monsters
    return ("monsters" if monsters else None), dict(c), monsters


# ---------------- all.spawn / level.game ----------------
def spawn_objects(sd):
    """[(section, name, (x, y, z))] — объекты ALife со строкой «M_SPAWN=1, секция, имя»."""
    out = []
    for m in re.finditer(rb"\x01\x00([a-z][a-z0-9_]{2,60})\x00([A-Za-z0-9_]+)\x00", sd):
        try:
            x, y, z = struct.unpack_from("<3f", sd, m.end() + 2)
        except struct.error:
            continue
        if all(math.isfinite(v) and abs(v) < 5000 for v in (x, y, z)):
            out.append((m.group(1).decode(), m.group(2).decode(), (x, y, z)))
    return out


def level_paths(gd):
    """Имя пути -> первая точка (x, y, z). Точка: имя\\0, u32 чанк, u32 размер, u16, vec3."""
    out = {}
    for m in re.finditer(rb"([a-z][a-z0-9_]{5,100})\x00", gd):
        nm = m.group(1).decode()
        if re.fullmatch(r"(name|wp)\d+", nm) or nm in out:
            continue
        try:
            x, y, z = struct.unpack_from("<3f", gd, m.end() + 10)
        except struct.error:
            continue
        if all(math.isfinite(v) for v in (x, y, z)) and abs(x) < 3000 and abs(z) < 3000 and -300 < y < 300 \
                and max(abs(x), abs(z)) > 0.5:
            out[nm] = (x, y, z)
    return out


def level_of(name):
    p = name.split("_", 1)[0]
    if p == "pri":
        return None  # и l11_pripyat (ТЧ), и pripyat (ЗП) — только по путям level.game
    return PREFIX.get(p)


def main():
    src, outdir = sys.argv[1], sys.argv[2]
    want = sys.argv[3:] or LEVELS
    sd = open(os.path.join(src, "spawns", "all.spawn"), "rb").read()
    objs = spawn_objects(sd)
    props = ltx(os.path.join(src, "configs", "misc", "simulation_objects_props.ltx"))
    simul = ltx(os.path.join(src, "configs", "misc", "simulation.ltx"))
    paths = {}
    for lv in LEVELS:
        p = os.path.join(src, "levels", lv, "level.game")
        if os.path.exists(p):
            paths[lv] = level_paths(open(p, "rb").read())

    def smart_level(name):
        hits = [lv for lv, ps in paths.items() if any(k.startswith(name + "_") for k in ps)]
        return hits[0] if len(hits) == 1 else level_of(name)

    by_level = collections.defaultdict(list)
    unplaced = collections.Counter()
    smarts = [(n, p) for s, n, p in objs if s == "smart_terrain"]
    smart_lv = {n: smart_level(n) for n, _ in smarts}
    for n, p in smarts:
        lv = smart_lv[n]
        if lv is None:
            unplaced[n.split("_")[0]] += 1
            continue
        pr = props.get(n, {})
        flags = [k for k in ("base", "territory", "resource", "lair", "surge") if pr.get(k, "0").strip() == "1"]
        allowed = sorted({PROP_FACTIONS[k] for k in PROP_FACTIONS if pr.get(k, "0").strip() == "1"})
        squads = list(simul.get(n, {}).keys())
        fac, fac_count, monsters = start_faction(squads)
        kind = ("base" if "base" in flags else "lair" if "lair" in flags else "field" if "resource" in flags
                else "outpost" if "territory" in flags else "landmark")
        o = {"id": "%s.%s" % (lv, n), "kind": kind, "smart": n, "type": flags, "original": mc(p), "status": "observed"}
        if fac:
            o["faction"] = fac
        if allowed:
            o["allowed"] = allowed
        if squads:
            o["startSquads"] = len(squads)
        pts = {}
        for key, pat in POINT_KEYS:
            got = [mc(v) for k, v in sorted(paths.get(lv, {}).items())
                   if k.startswith(n + "_") and re.search(pat, k[len(n) + 1:])]
            if got:
                pts[key] = got[0] if key == "flag" else got
        if pts:
            o["points"] = pts
        by_level[lv].append(o)

    # смарт-террейны, которых нет в all.spawn, но есть их пути в level.game (на Кордоне: 5_10, 6_7, 6_3)
    tails = "|".join("(?:%s)" % pat for _, pat in POINT_KEYS)
    for lv, ps in paths.items():
        names = collections.defaultdict(dict)
        for k, v in ps.items():
            m = re.match(r"([a-z0-9]+_smart_terrain(?:_\d+)+)_(%s)" % tails, k)  # без маршрутов «_to_»
            if m and m.group(1) not in smart_lv:
                names[m.group(1)][k] = v
        for n, own in sorted(names.items()):
            pts = {}
            for key, pat in POINT_KEYS:
                got = [mc(v) for k, v in sorted(own.items()) if re.search(pat, k[len(n) + 1:])]
                if got:
                    pts[key] = got[0] if key == "flag" else got
            allp = [mc(v) for v in own.values()]
            c = [round(sum(p[i] for p in allp) / len(allp), 1) for i in range(3)]
            smart_lv[n] = lv
            by_level[lv].append({"id": "%s.%s" % (lv, n), "kind": "landmark", "smart": n, "type": ["paths_only"],
                                 "original": c, "points": pts, "status": "observed",
                                 "note": "нет в all.spawn — только пути level.game; центр — среднее точек"})

    # стоянки ЗП (camp_zone, campfire) — к ближайшему смарт-террейну своего уровня (≤ 60 м)
    for s, n, p in objs:
        if s not in ("camp_zone", "campfire") or n.startswith("campfire_dinamic"):
            continue
        lv = level_of(n) or next((smart_lv[k] for k in smart_lv if n.startswith(k + "_")), None)
        if lv is None:
            continue
        best = None
        for o in by_level[lv]:
            ox, oy, oz = o["original"]
            d = math.dist((ox, oz), (p[0], -p[2]))
            if d <= 60 and (best is None or d < best[0]):
                best = (d, o)
        if best:
            best[1].setdefault("points", {}).setdefault("camps" if s == "camp_zone" else "campfires", []).append(mc(p))

    # переходы, торговцы, аномальные поля
    zones = collections.defaultdict(list)
    for s, n, p in objs:
        lv = level_of(n)
        if s == "level_changer" and lv:
            to = re.search(r"_to_([a-z0-9_]+?)(?:_\d+)?$", n)
            by_level[lv].append({"id": "%s.%s" % (lv, n), "kind": "exit", "to": to.group(1) if to else None,
                                 "original": mc(p), "status": "observed"})
        elif "trader" in n and lv and s not in ("smart_terrain", "space_restrictor") and "door" not in n:
            by_level[lv].append({"id": "%s.%s" % (lv, n), "kind": "trader", "original": mc(p), "status": "observed"})
        elif s == "anomal_zone" and lv:
            by_level[lv].append({"id": "%s.%s" % (lv, n), "kind": "field", "type": ["anomal_zone"],
                                 "original": mc(p), "status": "observed"})
        elif s.startswith("zone_") and lv:
            zones[lv].append((s, p))
    for lv, zs in zones.items():  # статичные аномалии — по клеткам 32 м
        cells = collections.defaultdict(list)
        for s, p in zs:
            cells[(int(p[0] // 32), int(-p[2] // 32))].append((s, p))
        for (cx, cz), items in sorted(cells.items()):
            xs = [p[0] for _, p in items]; ys = [p[1] for _, p in items]; zs2 = [p[2] for _, p in items]
            c = (sum(xs) / len(xs), sum(ys) / len(ys), sum(zs2) / len(zs2))
            by_level[lv].append({"id": "%s.anomalies_%d_%d" % (lv, cx, cz), "kind": "field", "type": ["static_anomalies"],
                                 "anomalies": dict(collections.Counter(s for s, _ in items)), "original": mc(c),
                                 "status": "observed"})

    os.makedirs(outdir, exist_ok=True)
    summary = {}
    for lv in want:
        items = by_level.get(lv, [])
        ids = [o["id"] for o in items]
        dup = [i for i, k in collections.Counter(ids).items() if k > 1]
        for o in items:  # повторы имён (например, два перехода с одним именем) — с номером
            if o["id"] in dup:
                o["id"] += "_%d" % (sum(1 for x in items[:items.index(o)] if x["id"].startswith(o["id"])))
        doc = {"level": lv, "frame": "original (xr_x, xr_y, -xr_z) — Minecraft-ориентация, до преобразования на TheZone",
               "source": "Anomaly 1.5.x: all.spawn, level.game, simulation_objects_props.ltx, simulation.ltx (tools/atlas/import_level.py)",
               "status": "observed", "objects": items}
        with open(os.path.join(outdir, lv + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(doc, f, ensure_ascii=False, indent=1)
        summary[lv] = dict(collections.Counter(o["kind"] for o in items))
        print(lv, summary[lv])
    if unplaced:
        print("смарт-террейны без уровня (подземелья и пр.):", dict(unplaced))


if __name__ == "__main__":
    main()
