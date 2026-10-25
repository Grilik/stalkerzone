"""Изометрический рендер построек с текстурами из текстурпака Stalker Zone.
Вход: дамп «x y z состояние_блока» (MapPreview levels). Выход: PNG.
python3 tools/iso_tex.py <pack_dir> <dump.txt> <out.png> [scale] [title]"""
import sys, os, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

PACK = sys.argv[1] if len(sys.argv) > 1 else 'resourcepack/StalkerZone-Textures'
VAN = os.path.join(PACK, 'assets/minecraft/textures/block')
MOD = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src/main/resources/assets/stalkerzone/textures/block')
T = 64
_cache = {}

FLAT = {  # блоки без своей текстуры в паке: цвет
    'furnace': 0x6c6c6a, 'smoker': 0x5a5048, 'blast_furnace': 0x5e5e60, 'barrel': 0x7a5a36, 'chest': 0x8a6430,
    'crafting_table': 0x80603c, 'cartography_table': 0x6a5a44, 'lectern': 0x7a5a36, 'smithing_table': 0x3a3438,
    'anvil': 0x48484a, 'grindstone': 0x8a8a86, 'note_block': 0x5a3a2a, 'composter': 0x6a4a2a, 'dispenser': 0x6a6a6a,
    'campfire': 0x7a4a22, 'lantern': 0xe8b85a, 'water': 0x34505c, 'farmland': 0x4e3622, 'potatoes': 0x5a7432,
    'carrots': 0x6a7a30, 'grass': 0x7a8448, 'fern': 0x56703a, 'dead_bush': 0x7a5a30, 'cobweb': 0xdedede,
    'moss_carpet': 0x5e6c34, 'coal_block': 0x1c1c1c, 'blackstone': 0x2a2626, 'bone_block': 0xcfc9b4,
    'lime_terracotta': 0x6a7438, 'green_banner': 0x3a5228, 'skeleton_skull': 0xcfcfc4, 'stone_button': 0x777775,
    'lightning_rod': 0x9a6a4a, 'ladder': 0x7a5a36, 'lever': 0x6a6a6a, 'tires': 0x222222, 'spruce_door': 0x6a5a48,
    'iron_door': 0xa8a8a4, 'spruce_trapdoor': 0x6a5a48, 'iron_trapdoor': 0x9a9a96, 'green_carpet': 0x52603a,
    'brown_carpet': 0x6a5238, 'red_concrete': 0x8c2c26, 'spruce_pressure_plate': 0x70604e,
}
MAT = {  # материал ступеней, полублоков, стен, заборов
    'smooth_stone_slab': ('smooth_stone', 'smooth_stone_slab_side'), 'spruce_slab': 'spruce_planks', 'dark_oak_slab': 'dark_oak_planks',
    'cobblestone_slab': 'cobblestone', 'stone_brick_slab': 'stone_bricks', 'smooth_sandstone_slab': 'sandstone_top',
    'spruce_stairs': 'spruce_planks', 'dark_oak_stairs': 'dark_oak_planks', 'stone_stairs': 'stone', 'stone_brick_stairs': 'stone_bricks',
    'cobblestone_stairs': 'cobblestone', 'stone_brick_wall': 'stone_bricks', 'cobblestone_wall': 'cobblestone',
    'spruce_fence': 'spruce_planks', 'smooth_sandstone': 'sandstone_top', 'glass_pane': 'glass', 'dirt_path': ('dirt_path_top', 'dirt_path_side'),
    'podzol': ('podzol_top', 'dirt'), 'hay_block': ('hay_block_top', 'hay_block_side'), 'sandstone': 'sandstone_top',
    'rusty_barrel': ('mod:rusty_barrel_top', 'mod:rusty_barrel_side'), 'grass_block': ('grass_block_top', 'grass_block_side'),
    'radioactive_barrel': ('mod:radioactive_barrel_top', 'mod:radioactive_barrel_side'), 'tires': ('mod:tires_top', 'mod:tires_side'),
    'blackstone_slab': 'blackstone', 'cobblestone_slab': 'cobblestone', 'spruce_fence': 'spruce_planks',
}
EMISSIVE = {'lantern', 'sea_lantern', 'campfire'}
PLANTS = {'grass', 'fern', 'dead_bush', 'potatoes', 'carrots', 'cobweb'}


def load(name):
    if name in _cache: return _cache[name]
    folder, n = (MOD, name[4:]) if name.startswith('mod:') else (VAN, name)
    p = os.path.join(folder, n + '.png')
    if os.path.exists(p):
        a = np.asarray(Image.open(p).convert('RGBA').resize((T, T), Image.NEAREST), dtype=np.float32)
    else:
        c = FLAT.get(n, 0x888888)
        rng = np.random.default_rng(sum(map(ord, n)))
        a = np.zeros((T, T, 4), np.float32)
        a[..., 0], a[..., 1], a[..., 2] = (c >> 16) & 255, (c >> 8) & 255, c & 255
        a[..., :3] *= (0.9 + 0.2 * rng.random((T, T)))[..., None]
        a[..., 3] = 255
        if n in PLANTS:
            yy, xx = np.mgrid[0:T, 0:T]
            mask = (np.abs(xx - yy) < 3) | (np.abs(xx + yy - T) < 3) | (rng.random((T, T)) < 0.25)
            a[..., 3] = np.where(mask, 255 if n != 'cobweb' else 150, 0)
        if n == 'water': a[..., 3] = 190
    if name == 'grass_block_top':
        a = a.copy(); a[..., :3] = a[..., :3] / 150.0 * np.array([118, 128, 70], np.float32)
    _cache[name] = a
    return a


def parse(state):
    sid = state.split('[')[0]
    ns, n = sid.split(':') if ':' in sid else ('minecraft', sid)
    props = {}
    if '[' in state:
        for kv in state[state.index('[') + 1:-1].split(','):
            if '=' in kv: k, v = kv.split('='); props[k] = v
    return n, props


MODBLOCKS = None


def mod_name(n):
    """Блок мода: car_paint_red_stairs → mod:car_paint_red, slate_roof_slab → mod:slate_roof."""
    global MODBLOCKS
    if MODBLOCKS is None: MODBLOCKS = {f[:-4] for f in os.listdir(MOD)} if os.path.isdir(MOD) else set()
    for suf in ('_stairs', '_slab', ''):
        if n.endswith(suf) and n[:len(n) - len(suf)] in MODBLOCKS: return 'mod:' + n[:len(n) - len(suf)]
    return None


def faces_tex(n, props):
    """Текстуры граней: top, px (+x), pz (+z)."""
    if n == 'car_wheel':
        ax = props.get('axis', 'y'); e, sd = 'mod:car_wheel', 'mod:car_wheel_tread'
        return {'y': (e, sd, sd), 'x': (sd, e, sd), 'z': (sd, sd, e)}[ax]
    if n in MAT: pass
    elif mod_name(n): return (mod_name(n),) * 3
    m = MAT.get(n, n)
    if isinstance(m, tuple):
        top, side = m
        if m[0].startswith('mod:') or n in ('grass_block', 'hay_block', 'podzol', 'dirt_path'): return top, side, side
        return top, side, side
    if n.endswith('_log'):
        ax = props.get('axis', 'y'); side, end = n, n + '_top'
        return {'y': (end, side, side), 'x': (side, end, side), 'z': (side, side, end)}[ax]
    return m, m, m


def boxes(n, props, nb):
    """Форма блока: список коробок (x0,y0,z0,x1,y1,z1) в долях блока."""
    full = [(0, 0, 0, 1, 1, 1)]
    if n.endswith('_slab'):
        t = props.get('type', 'bottom')
        return full if t == 'double' else [(0, 0.5, 0, 1, 1, 1)] if t == 'top' else [(0, 0, 0, 1, 0.5, 1)]
    if n.endswith('_stairs'):
        if props.get('half') == 'top': return full
        f = props.get('facing', 'north')
        hb = {'north': (0, 0.5, 0, 1, 1, 0.5), 'south': (0, 0.5, 0.5, 1, 1, 1), 'east': (0.5, 0.5, 0, 1, 1, 1), 'west': (0, 0.5, 0, 0.5, 1, 1)}[f]
        return [(0, 0, 0, 1, 0.5, 1), hb]
    if n in ('iron_bars', 'glass_pane'):
        t = 0.06 if n == 'iron_bars' else 0.07
        ax, az = nb('x'), nb('z')
        if ax and not az: return [(0, 0, 0.5 - t, 1, 1, 0.5 + t)]
        if az and not ax: return [(0.5 - t, 0, 0, 0.5 + t, 1, 1)]
        if ax and az: return [(0, 0, 0.5 - t, 1, 1, 0.5 + t), (0.5 - t, 0, 0, 0.5 + t, 1, 1)]
        return [(0.5 - t, 0, 0.5 - t, 0.5 + t, 1, 0.5 + t)]
    if n.endswith('_fence'): return [(0.375, 0, 0.375, 0.625, 1, 0.625)] + ([(0, 0.4, 0.44, 1, 0.8, 0.56)] if nb('x') else []) + ([(0.44, 0.4, 0, 0.56, 0.8, 1)] if nb('z') else [])
    if n.endswith('_wall'): return [(0.25, 0, 0.25, 0.75, 1, 0.75)]
    if n in ('lightning_rod', 'chain'):
        ax = props.get('axis', 'y')
        if ax == 'x': return [(0, 0.44, 0.44, 1, 0.56, 0.56)]
        if ax == 'z': return [(0.44, 0.44, 0, 0.56, 0.56, 1)]
        return [(0.44, 0, 0.44, 0.56, 1, 0.56)]
    if n.endswith('_carpet') or n.endswith('pressure_plate') or n == 'rail': return [(0, 0, 0, 1, 0.07, 1)]
    if n.endswith('_trapdoor'):
        if props.get('open') == 'true':
            f = props.get('facing', 'north')
            return [{'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1), 'west': (0.81, 0, 0, 1, 1, 1)}[f]]
        return [(0, 0.81, 0, 1, 1, 1)] if props.get('half') == 'top' else [(0, 0, 0, 1, 0.19, 1)]
    if n.endswith('_door'):
        f = props.get('facing', 'north')
        return [{'north': (0, 0, 0.81, 1, 1, 1), 'south': (0, 0, 0, 1, 1, 0.19), 'east': (0, 0, 0, 0.19, 1, 1), 'west': (0.81, 0, 0, 1, 1, 1)}[f]]
    if n == 'ladder': return [(0.4, 0, 0.4, 0.6, 1, 0.6)]
    if n == 'lantern': return [(0.31, 0.1, 0.31, 0.69, 0.6, 0.69)] if props.get('hanging') == 'true' else [(0.31, 0, 0.31, 0.69, 0.56, 0.69)]
    if n == 'campfire': return [(0, 0, 0, 1, 0.44, 1)]
    if n == 'skeleton_skull': return [(0.25, 0, 0.25, 0.75, 0.5, 0.75)]
    if n == 'stone_button' or n == 'lever': return [(0.35, 0, 0.35, 0.65, 0.15, 0.65)]
    if n == 'anvil': return [(0.15, 0, 0.1, 0.85, 1, 0.9)]
    if n in ('rusty_barrel', 'radioactive_barrel'): return [(0.125, 0, 0.125, 0.875, 0.94, 0.875)]
    if n == 'tires': return [(0.06, 0, 0.06, 0.94, 0.5, 0.94)]
    if n == 'water': return [(0, 0, 0, 1, 0.875, 1)]
    if n == 'farmland': return [(0, 0, 0, 1, 0.94, 1)]
    if n in PLANTS: return [(0.15, 0, 0.15, 0.85, 0.75, 0.85)]
    if n == 'green_banner': return [(0.45, 0, 0.2, 0.55, 1.6, 0.8)]
    return full


def opaque(n, props):
    if n in PLANTS or n in ('glass', 'glass_pane', 'iron_bars', 'water', 'cobweb', 'lantern', 'chain', 'ladder', 'rail'): return False
    if any(n.endswith(s) for s in ('_slab', '_stairs', '_fence', '_wall', '_trapdoor', '_door', '_carpet', 'pressure_plate')): return n.endswith('_slab') and props.get('type') == 'double'
    if n in ('lightning_rod', 'campfire', 'skeleton_skull', 'stone_button', 'lever', 'anvil', 'farmland', 'green_banner',
             'rusty_barrel', 'radioactive_barrel', 'tires', 'car_glass'): return False
    return True


_spr = {}


def sprite(key, texs, box, mask, s, emissive):
    k = (key, box, mask, s)
    if k in _spr: return _spr[k]
    x0, y0, z0, x1, y1, z1 = box
    W = 2 * s + 2
    out = np.zeros((W, W, 4), np.float32)
    py, px = np.mgrid[0:W, 0:W].astype(np.float32)
    dx = (px + 0.5 - (s + 1)) / s
    dy = (py + 0.5 - (s + 1)) / s
    shade = (1.0, 0.8, 0.62) if not emissive else (1.0, 1.0, 1.0)

    def put(sel, tex, tu, tv, f):
        ti = np.clip((tu * T).astype(int), 0, T - 1); tj = np.clip((tv * T).astype(int), 0, T - 1)
        c = tex[tj, ti]
        a = c[..., 3] >= 100
        sel = sel & a
        out[sel, :3] = c[sel, :3] * f
        out[sel, 3] = np.where(c[sel, 3] < 250, c[sel, 3], 255)
    # +x грань (правая)
    if mask & 2:
        v = x1 - dx
        w = (x1 + v) / 2 - dy
        sel = (v >= z0) & (v < z1) & (w >= y0) & (w < y1)
        put(sel, texs[1], v, 1 - w, shade[2])
    # +z грань (левая)
    if mask & 4:
        u = dx + z1
        w = (u + z1) / 2 - dy
        sel = (u >= x0) & (u < x1) & (w >= y0) & (w < y1)
        put(sel, texs[2], u, 1 - w, shade[1])
    # верх
    if mask & 1:
        a = dx; b = 2 * (dy + y1)
        u = (a + b) / 2; v = (b - a) / 2
        sel = (u >= x0) & (u < x1) & (v >= z0) & (v < z1)
        put(sel, texs[0], u, v, shade[0])
    img = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')
    _spr[k] = img
    return img


def render(dump, dst, s=8, title=None, crop=None, cut=None):
    vox = {}
    for line in open(dump, encoding='utf-8'):
        p = line.split(' ', 3)
        if len(p) < 4: continue
        vox[(int(p[0]), int(p[1]), int(p[2]))] = p[3].strip()
    info = {}
    for pos, st in vox.items():
        n, props = parse(st)
        info[pos] = (n, props, opaque(n, props))
    if cut is not None:  # разрез: убрать всё выше уровня cut в пределах cut_r (показать интерьер)
        cy, cr = cut
        vox = {k: v for k, v in vox.items() if not (k[1] >= cy and abs(k[0]) <= cr[0] and abs(k[2]) <= cr[1])}
        info = {k: info[k] for k in vox}
    if crop:
        r = crop
        vox = {k: v for k, v in vox.items() if abs(k[0]) <= r and abs(k[2]) <= r}

    def proj(x, y, z): return ((x - z) * s, (x + z) * s / 2 - y * s)
    xs = [proj(*k) for k in vox]
    minx = min(p[0] for p in xs) - 2 * s; maxx = max(p[0] for p in xs) + 2 * s
    miny = min(p[1] for p in xs) - 2 * s; maxy = max(p[1] for p in xs) + 2 * s
    top = 40 if title else 0
    W, H = int(maxx - minx), int(maxy - miny) + top
    # небо Зоны: серо-жёлтый градиент
    arr = np.zeros((H, W, 4), np.uint8)
    g = np.linspace(0, 1, H)[:, None]
    for i, (c0, c1) in enumerate(zip((138, 148, 150), (196, 192, 172))): arr[..., i] = (c0 * (1 - g) + c1 * g).astype(np.uint8)
    arr[..., 3] = 255
    img = Image.fromarray(arr, 'RGBA')
    order = sorted(vox.keys(), key=lambda k: (k[0] + k[1] + k[2], k[1]))
    for (x, y, z) in order:
        n, props, opq = info[(x, y, z)]

        def nb(axis):
            for d in (-1, 1):
                q = (x + d, y, z) if axis == 'x' else (x, y, z + d)
                if q in info:
                    nn = info[q][0]
                    if info[q][2] or nn in ('iron_bars', 'glass_pane') or nn.endswith('_fence') or nn.endswith('_wall'): return True
            return False
        bx = boxes(n, props, nb)
        if bx == [(0, 0, 0, 1, 1, 1)] and opq:
            mask = 0
            if not info.get((x, y + 1, z), (0, 0, False))[2]: mask |= 1
            if not info.get((x + 1, y, z), (0, 0, False))[2]: mask |= 2
            if not info.get((x, y, z + 1), (0, 0, False))[2]: mask |= 4
            if mask == 0: continue
        else:
            mask = 7
        names = faces_tex(n, props)
        texs = [load(t) for t in names]
        X, Y = proj(x, y, z)
        for b in sorted(bx, key=lambda b: b[0] + b[1] + b[2]):
            spr = sprite(names, texs, b, mask, s, n in EMISSIVE)
            img.alpha_composite(spr, (int(X - minx - s - 1), int(Y - miny + top - s - 1)))
    if title:
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, W, top - 6], fill=(28, 30, 26, 255))
        try: f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
        except Exception: f = None
        d.text((12, 7), title, fill=(236, 214, 140, 255), font=f)
    img.convert('RGB').save(dst)
    return img


if __name__ == '__main__':
    render(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 8, sys.argv[5] if len(sys.argv) > 5 else None)
