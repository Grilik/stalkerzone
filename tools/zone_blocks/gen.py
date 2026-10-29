"""Генератор блоков Зоны: Java-перечень, состояния/модели, названия, запасные текстуры 32×32 (свои, процедурные).
Запуск из корня репозитория: python3 tools/zone_blocks/gen.py"""
import json, os, sys, random, math, zlib
sys.path.insert(0, os.path.dirname(__file__))
from materials import M
from PIL import Image, ImageDraw
R = 'src/main/resources/assets/stalkerzone'
NS = 'stalkerzone'
RT = {}
def w(path, obj):
    if isinstance(obj, dict) and 'parent' in obj and RT.get('cur') and '/models/block/' in path: obj = dict(obj, render_type=RT['cur'])
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        if isinstance(obj, str): f.write(obj)
        else: json.dump(obj, f, ensure_ascii=False, indent=1)

# ---------- запасные текстуры
def clamp(v): return max(0, min(255, int(v)))
def tex(pattern, col, seed, size=32):
    rnd = random.Random(seed); im = Image.new('RGBA', (size, size)); px = im.load()
    r0, g0, b0 = col
    def put(x, y, k, a=255):
        px[x % size, y % size] = (clamp(r0 * k), clamp(g0 * k), clamp(b0 * k), a)
    for y in range(size):
        for x in range(size):
            n = 1 + rnd.uniform(-0.07, 0.07)
            if pattern == 'planks_h':
                row = y // 6; n *= 0.92 + 0.12 * ((row * 7) % 3) / 2
                if y % 6 == 5: n *= 0.6
                if (x + row * 11) % 32 == 0: n *= 0.65
                n *= 1 + 0.05 * math.sin(x * 0.7 + row)
            elif pattern == 'planks_v':
                col_ = x // 6; n *= 0.92 + 0.12 * ((col_ * 5) % 3) / 2
                if x % 6 == 5: n *= 0.6
                n *= 1 + 0.05 * math.sin(y * 0.6 + col_)
            elif pattern == 'bark':
                n *= 0.85 + 0.25 * abs(math.sin(x * 1.3 + rnd.random() * 0.4)); 
                if rnd.random() < 0.05: n *= 0.7
            elif pattern == 'bricks':
                row = y // 4; off = 4 if row % 2 else 0
                if y % 4 == 3 or (x + off) % 8 == 7: n *= 1.18 if col[0] < 150 else 0.85
                else: n *= 0.9 + 0.2 * (((x + off) // 8 * 13 + row * 7) % 5) / 4
            elif pattern == 'corrugated':
                n *= 0.82 + 0.3 * (0.5 + 0.5 * math.sin(x * 2 * math.pi / 4))
                if rnd.random() < 0.015: n *= 0.6
            elif pattern == 'plaster':
                n *= 1 + 0.08 * math.sin(x * 0.3) * math.cos(y * 0.25)
                if rnd.random() < 0.03: n *= 0.75
            elif pattern == 'concrete':
                if rnd.random() < 0.04: n *= 0.8
                if y == 31 or x == 31: n *= 0.8
            elif pattern == 'panels':
                if x % 16 == 15 or y % 16 == 15: n *= 0.72
                n *= 1 - 0.1 * (y % 16) / 16
            elif pattern == 'rust':
                n *= 0.85 + 0.3 * rnd.random() * (0.5 + 0.5 * math.sin(x * 0.4 + y * 0.2))
            elif pattern == 'metal':
                if rnd.random() < 0.03: n *= 1.3
                if y % 16 == 15: n *= 0.75
            elif pattern == 'tiles':
                if x % 8 == 7 or y % 8 == 7: n *= 0.75
            elif pattern == 'grid':
                a = 255 if (x % 8 in (0, 1) or y % 8 in (0, 1)) else 0
                put(x, y, n, a); continue
            elif pattern in ('glass', 'window'):
                frame = pattern == 'window' and (x < 2 or y < 2 or x > 29 or y > 29 or x in (15, 16))
                if frame: put(x, y, 0.55, 255)
                else: put(x, y, n * (0.9 + 0.2 * rnd.random()), 110 + rnd.randint(0, 40))
                continue
            elif pattern == 'door':
                if x < 2 or x > 29 or y < 2 or y > 29 or (y % 16 in (0, 1)): n *= 0.7
                if 24 <= x <= 25 and 15 <= y <= 17: n *= 1.8
            elif pattern == 'bags':
                row = y // 8; off = 6 if row % 2 else 0
                if y % 8 == 7 or (x + off) % 12 == 11: n *= 0.65
                else: n *= 1 - 0.1 * abs((y % 8) - 3.5) / 3.5
            put(x, y, n)
    return im

# ---------- модели и состояния
def m_cube(t): return {"parent": "minecraft:block/cube_all", "textures": {"all": t}}
def stairs_models(base, t):
    out = {}
    for suf, par in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
        out[base + suf] = {"parent": "minecraft:block/" + par, "textures": {"bottom": t, "top": t, "side": t}}
    return out
def stairs_state(name):
    v = {}
    for facing, rot in (('east', 0), ('south', 90), ('west', 180), ('north', 270)):
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                model = name + ('' if shape == 'straight' else '_inner' if 'inner' in shape else '_outer')
                y = rot
                if shape in ('inner_left', 'outer_left'): y = (rot + 270) % 360
                if half == 'top':
                    x = 180
                    if shape in ('inner_left', 'outer_left'): y = rot
                    elif shape in ('inner_right', 'outer_right'): y = (rot + 90) % 360
                else: x = 0
                e = {"model": f"{NS}:block/{model}"}
                if x: e["x"] = x
                if y: e["y"] = y
                if x or y: e["uvlock"] = True
                v[f"facing={facing},half={half},shape={shape}"] = e
    return {"variants": v}
def slab_state(name, full):
    return {"variants": {"type=bottom": {"model": f"{NS}:block/{name}"}, "type=top": {"model": f"{NS}:block/{name}_top"},
                         "type=double": {"model": f"{NS}:block/{full}"}}}
def trapdoor(name, t):
    md = {name + '_bottom': {"parent": "minecraft:block/template_orientable_trapdoor_bottom", "textures": {"texture": t}},
          name + '_top': {"parent": "minecraft:block/template_orientable_trapdoor_top", "textures": {"texture": t}},
          name + '_open': {"parent": "minecraft:block/template_orientable_trapdoor_open", "textures": {"texture": t}}}
    v = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for half in ('bottom', 'top'):
            for op in ('false', 'true'):
                if op == 'true':
                    e = {"model": f"{NS}:block/{name}_open"}; yy = y
                    if half == 'top': e["x"] = 180; yy = (y + 180) % 360
                    if yy: e["y"] = yy
                else:
                    e = {"model": f"{NS}:block/{name}_{half}"}
                    if y: e["y"] = y
                v[f"facing={f},half={half},open={op}"] = e
    return md, {"variants": v}
def fence(name, t):
    md = {name + '_post': {"parent": "minecraft:block/fence_post", "textures": {"texture": t}},
          name + '_side': {"parent": "minecraft:block/fence_side", "textures": {"texture": t}},
          name + '_inventory': {"parent": "minecraft:block/fence_inventory", "textures": {"texture": t}}}
    mp = [{"apply": {"model": f"{NS}:block/{name}_post"}}]
    for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        a = {"model": f"{NS}:block/{name}_side", "uvlock": True}
        if y: a["y"] = y
        mp.append({"when": {d: "true"}, "apply": a})
    return md, {"multipart": mp}
def pane(name, t):
    md = {name + '_post': {"parent": "minecraft:block/template_glass_pane_post", "textures": {"pane": t, "edge": t}},
          name + '_side': {"parent": "minecraft:block/template_glass_pane_side", "textures": {"pane": t, "edge": t}},
          name + '_side_alt': {"parent": "minecraft:block/template_glass_pane_side_alt", "textures": {"pane": t, "edge": t}},
          name + '_noside': {"parent": "minecraft:block/template_glass_pane_noside", "textures": {"pane": t}},
          name + '_noside_alt': {"parent": "minecraft:block/template_glass_pane_noside_alt", "textures": {"pane": t}}}
    mp = [{"apply": {"model": f"{NS}:block/{name}_post"}},
          {"when": {"north": "true"}, "apply": {"model": f"{NS}:block/{name}_side"}},
          {"when": {"east": "true"}, "apply": {"model": f"{NS}:block/{name}_side", "y": 90}},
          {"when": {"south": "true"}, "apply": {"model": f"{NS}:block/{name}_side_alt"}},
          {"when": {"west": "true"}, "apply": {"model": f"{NS}:block/{name}_side_alt", "y": 90}},
          {"when": {"north": "false"}, "apply": {"model": f"{NS}:block/{name}_noside"}},
          {"when": {"east": "false"}, "apply": {"model": f"{NS}:block/{name}_noside_alt"}},
          {"when": {"south": "false"}, "apply": {"model": f"{NS}:block/{name}_noside_alt", "y": 90}},
          {"when": {"west": "false"}, "apply": {"model": f"{NS}:block/{name}_noside", "y": 270}}]
    return md, {"multipart": mp}
def door(name, tb, tt):
    md = {}
    for part, tex_ in (('bottom', tb), ('top', tt)):
        for hinge in ('left', 'right'):
            for op in ('', '_open'):
                par = f"minecraft:block/door_{part}_{hinge}{op}"
                md[f"{name}_{part}_{hinge}{op}"] = {"parent": par, "textures": {"bottom": tb, "top": tt}}
    v = {}
    rots = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    for f, y in rots.items():
        for half, part in (('lower', 'bottom'), ('upper', 'top')):
            for hinge in ('left', 'right'):
                for op in ('false', 'true'):
                    yy = y
                    if op == 'true': yy = (y + (90 if hinge == 'left' else 270)) % 360
                    e = {"model": f"{NS}:block/{name}_{part}_{hinge}{'_open' if op == 'true' else ''}"}
                    if yy: e["y"] = yy
                    v[f"facing={f},half={half},hinge={hinge},open={op}"] = e
    return md, {"variants": v}

lang_ru = {}; lang_en = {}
names = []
for key, ru, kind, pat, col, gtex, extra in M:
    t = f"{NS}:block/zm_{key}"
    RT['cur'] = 'minecraft:translucent' if kind == 'glass' else 'minecraft:cutout' if pat == 'grid' else None
    tex(pat, col, zlib.crc32(key.encode())).save(f"{R}/textures/block/zm_{key}.png")
    full = f"zm_{key}"
    w(f"{R}/models/block/{full}.json", m_cube(t)); w(f"{R}/blockstates/{full}.json", {"variants": {"": {"model": f"{NS}:block/{full}"}}})
    w(f"{R}/models/item/{full}.json", {"parent": f"{NS}:block/{full}"})
    for n_, md in stairs_models(full + "_stairs", t).items(): w(f"{R}/models/block/{n_}.json", md)
    w(f"{R}/blockstates/{full}_stairs.json", stairs_state(full + "_stairs")); w(f"{R}/models/item/{full}_stairs.json", {"parent": f"{NS}:block/{full}_stairs"})
    w(f"{R}/models/block/{full}_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": t, "top": t, "side": t}})
    w(f"{R}/models/block/{full}_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": t, "top": t, "side": t}})
    w(f"{R}/blockstates/{full}_slab.json", slab_state(full + "_slab", full)); w(f"{R}/models/item/{full}_slab.json", {"parent": f"{NS}:block/{full}_slab"})
    lang_ru[f"block.{NS}.{full}"] = ru; lang_ru[f"block.{NS}.{full}_stairs"] = ru + ": ступени"; lang_ru[f"block.{NS}.{full}_slab"] = ru + ": плита"
    en = key.replace('_', ' ').capitalize()
    lang_en[f"block.{NS}.{full}"] = en; lang_en[f"block.{NS}.{full}_stairs"] = en + " stairs"; lang_en[f"block.{NS}.{full}_slab"] = en + " slab"
    for e in extra:
        nm = f"{full}_{e}"
        RT['cur'] = 'minecraft:translucent' if kind == 'glass' else 'minecraft:cutout' if e in ('door', 'trapdoor', 'pane') or pat == 'grid' else None
        if e == 'trapdoor': md, st = trapdoor(nm, t); w(f"{R}/models/item/{nm}.json", {"parent": f"{NS}:block/{nm}_bottom"})
        elif e == 'fence': md, st = fence(nm, t); w(f"{R}/models/item/{nm}.json", {"parent": f"{NS}:block/{nm}_inventory"})
        elif e == 'pane': md, st = pane(nm, t); w(f"{R}/models/item/{nm}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": t}})
        elif e == 'door':
            tb, tt = f"{NS}:block/zm_{key}_door_bottom", f"{NS}:block/zm_{key}_door_top"
            im = tex(pat, col, zlib.crc32(key.encode()))
            im.save(f"{R}/textures/block/zm_{key}_door_bottom.png"); im.save(f"{R}/textures/block/zm_{key}_door_top.png")
            md, st = door(nm, tb, tt); w(f"{R}/models/item/{nm}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": tt}})
        for n_, mdd in md.items(): w(f"{R}/models/block/{n_}.json", mdd)
        w(f"{R}/blockstates/{nm}.json", st)
        sfx = {'trapdoor': 'люк/щит', 'fence': 'ограда', 'pane': 'панель', 'door': 'дверь'}[e]
        lang_ru[f"block.{NS}.{nm}"] = ru + ": " + sfx; lang_en[f"block.{NS}.{nm}"] = en + " " + e
    names.append((key, kind, extra))
# лут: блок выпадает сам собой (дверь — только с нижней половины)
D = 'src/main/resources/data/stalkerzone/loot_tables/blocks'
for key, kind, ex in names:
    for nm in [f'zm_{key}', f'zm_{key}_stairs', f'zm_{key}_slab'] + [f'zm_{key}_{e}' for e in ex]:
        entry = {"type": "minecraft:item", "name": f"{NS}:{nm}"}
        pool = {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
        if nm.endswith('_door'):
            pool["conditions"].append({"condition": "minecraft:block_state_property", "block": f"{NS}:{nm}", "properties": {"half": "lower"}})
        if kind == 'glass': pool["conditions"] = [{"condition": "minecraft:match_tool", "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}]
        w(f"{D}/{nm}.json", {"type": "minecraft:block", "pools": [pool]})
# названия: дописать к существующим
for lang, d in (('ru_ru', lang_ru), ('en_us', lang_en)):
    p = f"{R}/lang/{lang}.json"; cur = json.load(open(p, encoding='utf-8')); cur.update(d); w(p, cur)
# Java-перечень
rows = ",\n".join(f'    {k.upper()}("{k}", Kind.{kind.upper()}, {"true" if "trapdoor" in ex else "false"}, {"true" if "fence" in ex else "false"}, {"true" if "pane" in ex else "false"}, {"true" if "door" in ex else "false"})' for k, kind, ex in names)
java = f'''package ru.stalkerzone.block;

/** Материалы Зоны (сгенерировано tools/zone_blocks/gen.py из materials.py — правьте там). */
public enum ZoneMat {{
{rows};

    public enum Kind {{ WOOD, STONE, METAL, GLASS }}

    public final String key;
    public final Kind kind;
    public final boolean trapdoor, fence, pane, door;

    ZoneMat(String key, Kind kind, boolean trapdoor, boolean fence, boolean pane, boolean door) {{
        this.key = key; this.kind = kind; this.trapdoor = trapdoor; this.fence = fence; this.pane = pane; this.door = door;
    }}

    public static ZoneMat of(String key) {{
        for (ZoneMat m : values()) if (m.key.equals(key)) return m;
        return null;
    }}
}}
'''
w('src/main/java/ru/stalkerzone/block/ZoneMat.java', java)
print(len(names), 'материалов')
