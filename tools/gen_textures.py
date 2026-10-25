"""Текстуры Stalker Zone, 64×64 (v1.11).

python3 tools/gen_textures.py <папка_пака> <папка_текстур_мода>

* ванильные блоки перекрашиваются в текстурпаке «Stalker Zone: текстуры» (assets/minecraft/textures/block);
* блоки мода (шифер, побелка, забор ПО-2, краска кузовов, колёса, фары, бочки, ящики) кладутся прямо в мод.
Всё процедурное и детерминированное: одинаковый результат при каждом запуске.
"""
import sys, os, json, math
import numpy as np
from PIL import Image, ImageDraw

N = 64
PACK = sys.argv[1] if len(sys.argv) > 1 else 'resourcepack/StalkerZone-Textures'
MODTEX = sys.argv[2] if len(sys.argv) > 2 else 'src/main/resources/assets/stalkerzone/textures/block'
TEX = os.path.join(PACK, 'assets/minecraft/textures/block')
os.makedirs(TEX, exist_ok=True)
os.makedirs(MODTEX, exist_ok=True)


def rng(name):
    return np.random.default_rng(sum(ord(c) * (i + 7) for i, c in enumerate(name)) + 1000)


def vnoise(r, cells, n=N):
    g = r.random((cells + 1, cells + 1))
    g[-1, :] = g[0, :]
    g[:, -1] = g[:, 0]
    xs = np.linspace(0, cells, n, endpoint=False)
    i = xs.astype(int)
    f = xs - i
    f = f * f * (3 - 2 * f)
    a = g[np.ix_(i, i)]; b = g[np.ix_(i, i + 1)]; c = g[np.ix_(i + 1, i)]; d = g[np.ix_(i + 1, i + 1)]
    fy = f[:, None]; fx = f[None, :]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(r, o=(2, 4, 8, 16), w=(0.45, 0.28, 0.17, 0.10)):
    return sum(wi * vnoise(r, oi) for oi, wi in zip(o, w))


def base(r, color, var=16, grain=0.6):
    c = np.array(color, float)[None, None, :]
    n = fbm(r) - 0.5
    g = r.normal(0, 1, (N, N))
    return c + (n * var * 2 + g * var * grain * 0.4)[:, :, None]


def grime(r, a, k=0.25):
    return a * (1 - k * fbm(r)[:, :, None])


def mix(a, col, t):
    t = np.clip(t, 0, 1)
    if np.ndim(t) == 2: t = t[:, :, None]
    return a * (1 - t) + np.array(col, float) * t


def streaks(r, a, color, count=6, strength=0.5, length=(12, 50), width=(1, 4)):
    col = np.array(color, float)
    for _ in range(count):
        x = r.integers(0, N); y0 = r.integers(0, N // 2); L = r.integers(*length); w = r.integers(*width)
        for y in range(y0, min(N, y0 + L)):
            t = strength * (1 - (y - y0) / L) ** 0.7
            for dx in range(w):
                xx = (x + dx + (y - y0) // 25) % N
                a[y, xx] = a[y, xx] * (1 - t) + col * t
    return a


def cracks(r, a, count=3, dark=0.68, length=(10, 34)):
    for _ in range(count):
        x, y = r.integers(0, N), r.integers(0, N)
        for _ in range(r.integers(*length)):
            a[y % N, x % N] *= dark
            if r.random() < 0.3: a[(y + 1) % N, x % N] *= (1 + dark) / 2
            x += r.integers(-1, 2); y += r.integers(0, 2)
    return a


def blobs(r, a, color, count=8, rad=(1, 5), k=0.7, rough=0.85):
    col = np.array(color, float)
    yy, xx = np.mgrid[0:N, 0:N]
    for _ in range(count):
        cx, cy, rr = r.integers(0, N), r.integers(0, N), r.integers(*rad)
        dx = np.minimum(np.abs(xx - cx), N - np.abs(xx - cx)); dy = np.minimum(np.abs(yy - cy), N - np.abs(yy - cy))
        m = (dx * dx + dy * dy <= rr * rr) & (r.random((N, N)) < rough)
        a[m] = a[m] * (1 - k) + col * k
    return a


def rust_mask(r, cover=0.25, scale=(4, 8, 16)):
    n = fbm(r, scale, (0.5, 0.3, 0.2))
    return n > np.quantile(n, 1 - cover)


def bevel(a, x0, y0, x1, y1, hi=1.12, lo=0.78):
    """Объём панели: светлая кромка сверху-слева, тень снизу-справа."""
    a[y0, x0:x1 + 1] *= hi; a[y0:y1 + 1, x0] *= hi
    a[y1, x0:x1 + 1] *= lo; a[y0:y1 + 1, x1] *= lo
    return a


def save(name, a, alpha=None, folder=TEX):
    arr = np.clip(a, 0, 255).astype(np.uint8)
    if alpha is None:
        im = Image.fromarray(arr, 'RGB').convert('RGBA')
    else:
        im = Image.fromarray(np.dstack([arr, np.clip(alpha, 0, 255).astype(np.uint8)]), 'RGBA')
    im.save(os.path.join(folder, name + '.png'))


def gray(a):
    return a.mean(axis=2, keepdims=True).repeat(3, axis=2)


S = N // 32  # масштаб к прежним 32 px

# ======================================================================== земля
r = rng('grass')
g = base(r, (150, 150, 150), 20, 1.0)
for _ in range(420):
    x, y = r.integers(0, N), r.integers(0, N); L = r.integers(2, 5)
    v = 0.72 if r.random() < 0.5 else 1.18
    for k in range(L): g[(y - k) % N, (x + (k // 2 if r.random() < 0.5 else 0)) % N] *= v
g = blobs(r, g, (118, 118, 118), 10, (2, 4), 0.45)
save('grass_block_top', gray(g))

r = rng('dirt')
dirt = base(r, (90, 68, 48), 14, 1.0)
dirt = blobs(r, dirt, (68, 52, 40), 18, (1, 3), 0.6)
dirt = blobs(r, dirt, (118, 108, 96), 30, (0, 2), 0.7)
save('dirt', dirt)
side = dirt.copy()
fr = base(rng('fringe'), (100, 108, 62), 10)
edge = (10 + vnoise(rng('edge'), 8, N)[0] * 10).astype(int)
ov_a = np.zeros((N, N)); ov = np.full((N, N, 3), 150.0)
for x in range(N):
    for y in range(edge[x]):
        side[y, x] = fr[y, x]; ov_a[y, x] = 255; ov[y, x] = 150 + r.normal(0, 18)
    if edge[x] < N: side[edge[x], x] *= 0.7
save('grass_block_side', side)
save('grass_block_side_overlay', ov, ov_a)
r = rng('coarse'); c = base(r, (86, 64, 46), 16, 1.0); c = blobs(r, c, (126, 116, 104), 70, (0, 2), 0.85); save('coarse_dirt', c)
r = rng('path'); p = base(r, (126, 106, 74), 12, 1.0); p = blobs(r, p, (98, 82, 58), 20, (1, 4), 0.5); p = blobs(r, p, (146, 140, 128), 40, (0, 1), 0.7)
for x in range(N): p[:, x] *= 1 - 0.08 * (abs(x - N / 4) < 3 or abs(x - 3 * N / 4) < 3)  # колеи
save('dirt_path_top', p); ps = dirt.copy(); ps[:6] = p[:6]; save('dirt_path_side', ps)
r = rng('gravel'); gv = base(r, (116, 112, 106), 8)
for _ in range(260):
    x, y = r.integers(0, N), r.integers(0, N); v = r.integers(64, 176); rr = r.integers(1, 3)
    gv[y:y + rr, x:x + rr + 1] = (v, v - 4, v - 9)
    gv[min(N - 1, y + rr), x:x + rr + 1] *= 0.6
save('gravel', gv)
r = rng('mud'); m = base(r, (60, 50, 44), 10, 0.8); m = blobs(r, m, (78, 70, 62), 12, (2, 6), 0.5); m = blobs(r, m, (44, 38, 34), 10, (3, 7), 0.6)
m = blobs(r, m, (70, 80, 84), 6, (1, 3), 0.5); save('mud', m)
r = rng('podzol'); pz = base(r, (92, 64, 38), 14, 1.0); pz = blobs(r, pz, (140, 74, 32), 60, (0, 2), 0.8); save('podzol_top', pz)
r = rng('moss'); ms = base(r, (86, 102, 52), 14, 1.0); ms = blobs(r, ms, (70, 84, 44), 20, (1, 3), 0.5); save('moss_block', ms)
r = rng('stone'); st = base(r, (110, 110, 106), 12); st = cracks(r, st, 4, 0.8); save('stone', st)
r = rng('andesite'); an = base(r, (116, 116, 112), 12); save('andesite', cracks(r, an, 5, 0.75))


def cobble(name, moss=False):
    r = rng(name)
    a = base(r, (62, 62, 60), 6)
    yy, xx = np.mgrid[0:N, 0:N]
    pts = r.random((24, 2)) * N
    d = np.full((N, N), 1e9); d2 = np.full((N, N), 1e9); idx = np.zeros((N, N), int)
    for k, (px, py) in enumerate(pts):
        dx = np.minimum(np.abs(xx - px), N - np.abs(xx - px)); dy = np.minimum(np.abs(yy - py), N - np.abs(yy - py))
        dd = np.sqrt(dx * dx + dy * dy)
        m = dd < d
        d2 = np.where(m, d, np.minimum(d2, dd)); idx = np.where(m, k, idx); d = np.minimum(d, dd)
    vals = r.integers(88, 150, 24)
    stone = vals[idx][:, :, None] * np.array([1, 1, 0.97])[None, None, :]
    edge = np.clip((d2 - d) / 3, 0, 1)[:, :, None]
    a = a * (1 - edge) + (stone * (0.8 + 0.25 * edge)) * edge
    a += r.normal(0, 6, (N, N, 1))
    if moss: a = blobs(r, a, (74, 92, 46), 16, (2, 5), 0.7)
    return a


save('cobblestone', cobble('cobble'))
save('mossy_cobblestone', cobble('cobble', True))


# ======================================================================== кирпич, бетон, штукатурка
def bricks(name, brick, mortar, bw=16, bh=8, var=20, soot=0.22, chip=0.25):
    r = rng(name)
    a = base(r, mortar, 8)
    for row in range(N // bh):
        off = (bw // 2) * (row % 2)
        for col in range(-1, N // bw + 1):
            c = np.array(brick, float) + r.normal(0, var * 0.6, 3)
            x0 = col * bw + off; y0 = row * bh
            for y in range(y0, y0 + bh - 2):
                for x in range(x0, x0 + bw - 2):
                    a[y % N, x % N] = c * (0.92 + 0.16 * r.random())
            # объём кирпича
            for x in range(x0, x0 + bw - 2): a[y0 % N, x % N] *= 1.1; a[(y0 + bh - 3) % N, x % N] *= 0.82
            if r.random() < chip:  # скол угла
                cx, cy = (x0 + (bw - 3 if r.random() < 0.5 else 0)) % N, (y0 + (bh - 3 if r.random() < 0.5 else 0)) % N
                a[cy:cy + 2, cx:cx + 2] = np.array(mortar) * 0.85
    a = grime(r, a, soot)
    return a


save('bricks', streaks(rng('br2'), bricks('bricks', (124, 54, 40), (126, 118, 108)), (58, 48, 44), 5, 0.35))
save('white_terracotta', streaks(rng('sil2'), bricks('silicate', (194, 190, 178), (150, 146, 138), var=8, soot=0.18), (112, 100, 80), 7, 0.3))


def panel(name, col, seam=32, var=9, stain=(92, 86, 74), bolts=True):
    r = rng(name)
    a = base(r, col, var)
    a = blobs(r, a, (col[0] * 0.86, col[1] * 0.86, col[2] * 0.84), 14, (2, 6), 0.35)
    for k in range(0, N, seam):
        a[:, k] *= 0.62; a[k, :] *= 0.62
        a[:, (k + 1) % N] *= 1.08; a[(k + 1) % N, :] *= 1.08
    if bolts:
        for k in range(0, N, seam):
            for j in range(0, N, seam):
                for (bx, by) in ((k + 5, j + 5), (k + seam - 6, j + 5)):
                    a[by % N, bx % N] = (70, 52, 40)
                    a = streaks_at(a, bx % N, by % N + 1, (110, 64, 40), 0.45, 14)
    a = streaks(r, a, stain, 7, 0.3)
    a = cracks(r, a, 3, 0.7)
    return a


def streaks_at(a, x, y, col, s, L):
    for k in range(L):
        yy = (y + k) % N
        t = s * (1 - k / L)
        a[yy, x] = a[yy, x] * (1 - t) + np.array(col, float) * t
    return a


save('light_gray_concrete', panel('lgc', (150, 148, 140)))
save('gray_concrete', panel('gc', (98, 98, 96), 64, bolts=False))
save('white_concrete', panel('wc', (196, 194, 186), 64, stain=(132, 120, 100), bolts=False))
r = rng('asphalt'); asp = base(r, (50, 50, 52), 7, 1.6)
asp = blobs(r, asp, (82, 82, 80), 80, (0, 1), 0.6)
asp = blobs(r, asp, (40, 40, 42), 6, (4, 9), 0.5)   # заплатки
asp = cracks(r, asp, 6, 0.55, (14, 40))
save('black_concrete', asp)


def paint(name, col, under=(112, 82, 60), chip=0.18, rust=(108, 58, 32)):
    r = rng(name)
    a = base(r, col, 8)
    n = fbm(r, (8, 16, 32), (0.5, 0.3, 0.2))
    mask = n < np.quantile(n, chip * 0.75)
    a[mask] = a[mask] * 0.35 + np.array(under) * 0.65
    edge = mask ^ np.roll(mask, 1, 0)
    a[edge] *= 0.75
    return streaks(r, a, rust, 5, 0.3)


save('red_concrete', paint('redc', (140, 42, 36)))
save('yellow_concrete', paint('yelc', (196, 160, 48)))
save('green_concrete', paint('grnc', (78, 90, 46)))
save('cyan_terracotta', paint('cyt', (82, 108, 104), (150, 140, 120)))
save('blue_terracotta', paint('blt', (62, 82, 112), (150, 140, 120)))
save('red_terracotta', paint('rdt', (128, 58, 44)))
save('yellow_terracotta', paint('ylt', (176, 140, 58)))
save('orange_terracotta', paint('ort', (164, 96, 48)))
save('light_blue_terracotta', paint('lbt', (104, 136, 156), (160, 150, 130)))
save('lime_terracotta', paint('lmt', (98, 110, 56), (110, 90, 60)))
r = rng('plaster'); pl = base(r, (154, 112, 84), 10); pl = blobs(r, pl, (122, 112, 100), 10, (3, 7), 0.6); save('terracotta', streaks(r, pl, (90, 70, 60), 6, 0.3))


def corrugated(name, col, rust, cover=0.25, period=8):
    r = rng(name)
    a = base(r, col, 6)
    for x in range(N):
        ph = (x % period) / period * 2 * math.pi
        a[:, x] *= 0.8 + 0.34 * (0.5 + 0.5 * math.sin(ph))
    m = rust_mask(r, cover)
    a[m] = a[m] * 0.25 + np.array(rust) * 0.75 * (0.85 + 0.3 * r.random((m.sum(), 1)))
    a = streaks(r, a, rust, 12, 0.6, (10, 60), (1, 3))
    for y in (0, N // 2):  # стык листов и заклёпки
        a[y] *= 0.7
        for x in range(4, N, 16): a[y + 2, x] = (60, 44, 36)
    return a


save('iron_block', corrugated('iron', (148, 150, 148), (118, 66, 36), 0.18))
save('raw_iron_block', corrugated('rawiron', (122, 82, 58), (88, 48, 28), 0.6))
save('brown_terracotta', corrugated('rustsheet', (104, 66, 44), (70, 40, 26), 0.5, 16))
save('green_terracotta', paint('olive', (78, 86, 48), (92, 70, 50), 0.12))
r = rng('plate'); sm = base(r, (136, 134, 128), 8)
for (x, y) in [(10, 10), (N - 11, 10), (10, N - 11), (N - 11, N - 11)]:
    sm[y - 2:y + 3, x - 2:x + 3] *= 0.6; sm[y - 1, x - 1] *= 1.4
sm = bevel(sm, 0, 0, N - 1, N - 1, 1.1, 0.7)
sm = cracks(r, sm, 3, 0.7); sm = streaks(r, sm, (96, 92, 84), 4, 0.25)
save('smooth_stone', sm)
sms = sm.copy(); sms[N // 2 - 1:N // 2 + 1] *= 0.65; save('smooth_stone_slab_side', sms)
r = rng('tile'); tl = base(r, (126, 124, 116), 5)
for y in range(N):
    for x in range(N):
        if x % 16 == 0 or y % 16 == 0: tl[y, x] *= 0.62
        elif x % 16 == 1 or y % 16 == 1: tl[y, x] *= 1.08
tl = blobs(r, tl, (88, 84, 78), 10, (1, 3), 0.5); tl = cracks(r, tl, 2, 0.7)
save('polished_andesite', tl)


def blocks(name, col, crack=0, h=32, w=32):
    r = rng(name)
    a = base(r, col, 8)
    for row in range(N // h):
        off = (w // 2) * (row % 2)
        for x in range(N):
            a[row * h, x] *= 0.6; a[(row * h + 1) % N, x] *= 1.08
            if (x + off) % w == 0:
                for y in range(row * h, row * h + h): a[y, x] *= 0.6; a[y, (x + 1) % N] *= 1.08
    a = streaks(r, a, (80, 76, 70), 6, 0.3)
    if crack: a = cracks(r, a, crack, 0.5, (16, 44))
    return a


save('stone_bricks', blocks('sb', (128, 126, 120)))
save('cracked_stone_bricks', blocks('csb', (120, 118, 112), 9))
save('mossy_stone_bricks', blobs(rng('msb'), blocks('msb0', (122, 120, 114)), (76, 94, 48), 16, (2, 6), 0.7))


# ======================================================================== дерево
def boards(name, col, w=16, var=10, dark=0.6, horiz=False):
    r = rng(name)
    a = base(r, col, var)
    grain = vnoise(r, 32)[:, :1].repeat(N, 1) if not horiz else vnoise(r, 32)[:1, :].repeat(N, 0)
    for x in range(N): a[:, x] *= 1 - 0.06 * math.sin(x * 0.6 + r.random())
    for i in range(N // w):
        x0 = i * w
        a[:, x0] *= dark; a[:, (x0 + 1) % N] *= 1.08
        y = r.integers(0, N); a[y, x0:x0 + w] *= 0.7  # стык досок
        for (nx, ny) in ((x0 + 3, y - 3), (x0 + w - 4, y - 3), (x0 + 3, y + 3), (x0 + w - 4, y + 3)):
            a[ny % N, nx % N] = (52, 44, 38)  # гвозди
    a = blobs(r, a, (col[0] * 0.6, col[1] * 0.58, col[2] * 0.55), 8, (1, 2), 0.7)  # сучки
    return grime(r, a, 0.28)


save('spruce_planks', boards('spr', (110, 102, 90)))
save('dark_oak_planks', boards('dop', (64, 50, 38)))
save('oak_planks', boards('oak', (118, 94, 64)))
r = rng('timber'); tb = base(r, (104, 90, 72), 8)
for x in range(N): tb[:, x] *= 0.84 + 0.16 * math.sin(x * 0.9)
tb = cracks(r, tb, 4, 0.7)
save('stripped_spruce_log', grime(r, tb, 0.3))
tt = base(rng('timbertop'), (118, 102, 80), 6)
for y in range(N):
    for x in range(N):
        d = math.hypot(x - N / 2 + 0.5, y - N / 2 + 0.5)
        if int(d) % 5 == 0: tt[y, x] *= 0.82
        if d > N / 2 - 1: tt[y, x] *= 0.55
save('stripped_spruce_log_top', tt)
r = rng('birch'); bk = base(r, (192, 190, 180), 8)
for _ in range(36):
    x, y, L = r.integers(0, N), r.integers(0, N), r.integers(4, 16)
    bk[y:y + 2, x:x + L] = (42, 40, 38)
save('birch_log', bk)
# ставни и люки
r = rng('shutter'); sh = boards('shut', (92, 118, 128), 16, 8)
sh = bevel(sh, 0, 0, N - 1, N - 1, 1.1, 0.6)
for y in (N // 4, 3 * N // 4): sh[y - 1:y + 2, 4:N - 4] *= 0.75
save('spruce_trapdoor', sh, np.full((N, N), 255.0))
it = corrugated('itrap', (120, 122, 120), (110, 62, 34), 0.3, 64)
it = bevel(it, 0, 0, N - 1, N - 1, 1.15, 0.6)
for (x, y) in ((6, 6), (N - 7, 6), (6, N - 7), (N - 7, N - 7)): it[y - 1:y + 2, x - 1:x + 2] = (70, 70, 70)
save('iron_trapdoor', it, np.full((N, N), 255.0))

# ======================================================================== металл, стекло, свет
a = np.zeros((N, N, 3)); al = np.zeros((N, N))
r = rng('bars')
for x in (6, 22, 38, 54):
    for y in range(N):
        a[y, x:x + 3] = (98, 72, 54) + r.normal(0, 10, 3); al[y, x:x + 3] = 255
        a[y, x] *= 1.2; a[y, x + 2] *= 0.7
for y in (14, 46):
    for x in range(N):
        a[y:y + 2, x] = (112, 80, 58) + r.normal(0, 10, 3); al[y:y + 2, x] = 255
        if x % 8 == 4:  # колючки
            for d in (-3, -2, 2, 3): a[y + d, x + (1 if d > 0 else -1)] = (124, 92, 66); al[y + d, x + (1 if d > 0 else -1)] = 255
save('iron_bars', a, al)
a = np.zeros((N, N, 3)); al = np.zeros((N, N))
for y in range(N):
    link = (y // 8) % 2
    for x in (28, 29, 34, 35) if link == 0 else (30, 31, 32, 33):
        if link == 0 or y % 8 in (0, 7): a[y, x] = (94, 72, 56); al[y, x] = 255
    if link == 1:
        a[y, 30] = a[y, 33] = (94, 72, 56); al[y, 30] = al[y, 33] = 255
save('chain', a, al)
r = rng('glass')
dg = fbm(r)
gl = np.full((N, N, 3), 166.0) * (0.72 + 0.32 * dg[:, :, None])
gla = 50 + 150 * dg ** 2
gl[:2] = gl[-2:] = gl[:, :2] = gl[:, -2:] = (86, 82, 76); gla[:2] = gla[-2:] = gla[:, :2] = gla[:, -2:] = 255
for start in ((14, 6), (40, 20)):
    x, y = start
    for _ in range(46):
        gl[y % N, x % N] = (196, 198, 196); gla[y % N, x % N] = 200; x += r.integers(-1, 2); y += 1
save('glass', gl, gla)
save('glass_pane_top', np.full((N, N, 3), 86.0))
r = rng('rail'); rl = np.zeros((N, N, 3)); rla = np.zeros((N, N))
for y in range(N):
    if y % 16 < 8:
        rl[y, 4:60] = (70, 56, 44) + r.normal(0, 6, 3); rla[y, 4:60] = 255
    for x in (12, 13, 14, 48, 49, 50):
        rl[y, x] = (112, 86, 64) + r.normal(0, 8, 3); rla[y, x] = 255
    rl[y, 13] *= 1.3; rl[y, 49] *= 1.3
save('rail', rl, rla)
r = rng('lamp'); lp = base(r, (196, 202, 196), 5)
lp[:, :6] = lp[:, -6:] = lp[:6] = lp[-6:] = (78, 78, 74)
for x in range(10, 56, 12): lp[8:56, x:x + 2] *= 0.82
lp = blobs(r, lp, (150, 150, 120), 5, (1, 3), 0.4)  # мухи и пыль
save('sea_lantern', lp)
r = rng('blamp'); bl = base(r, (88, 88, 84), 6); bl[12:52, 12:52] = (58, 60, 56); bl = cracks(r, bl, 3, 0.6); save('redstone_lamp', bl)


# ======================================================================== ткани, сено, мешки
def cloth(name, col):
    r = rng(name)
    a = base(r, col, 7, 1.2)
    for y in range(0, N, 2): a[y] *= 0.94
    for x in range(0, N, 3): a[:, x] *= 0.97
    a = blobs(r, a, (col[0] * 0.7, col[1] * 0.7, col[2] * 0.7), 8, (2, 5), 0.5)
    for y in (N // 3, 2 * N // 3): a[y] *= 0.8  # швы брезента
    return grime(r, a, 0.3)


save('green_wool', cloth('gw', (80, 90, 54)))
save('brown_wool', cloth('bw', (102, 84, 60)))
save('white_wool', cloth('ww', (168, 164, 148)))
r = rng('sand'); sb = base(r, (152, 134, 98), 8)
for row in range(4):
    y0 = row * 16; off = 16 * (row % 2)
    for y in range(y0, y0 + 16):
        for x in range(N):
            xx = (x + off) % 32
            if y == y0 or y == y0 + 1 or xx == 0: sb[y, x] *= 0.55
            elif y >= y0 + 13: sb[y, x] *= 0.8
            elif xx in (1, 31): sb[y, x] *= 0.85
            if xx in (8, 24) and y0 + 3 < y < y0 + 12 and y % 2 == 0: sb[y, x] *= 0.85  # строчка
save('sandstone_top', sb)
r = rng('hay'); hy = base(r, (148, 126, 66), 14)
for _ in range(260):
    x, y = r.integers(0, N), r.integers(0, N); hy[y, x:x + 6] *= 0.75 if r.random() < 0.5 else 1.15
save('hay_block_side', hy); save('hay_block_top', hy)

# ======================================================================== БЛОКИ МОДА
M = MODTEX

# шифер: волнистые асбоцементные листы внахлёст, лишайник, грязь
r = rng('slate')
sl = base(r, (128, 128, 122), 7)
for x in range(N):
    sl[:, x] *= 0.78 + 0.32 * (0.5 + 0.5 * math.sin(x / 8 * 2 * math.pi))
for y0 in (0, N // 2):
    sl[y0:y0 + 3] *= 0.6
    sl[(y0 + 3) % N] *= 1.15
sl = blobs(r, sl, (150, 140, 70), 8, (1, 3), 0.55)   # жёлтый лишайник
sl = blobs(r, sl, (76, 94, 50), 10, (1, 4), 0.55)    # мох
sl = streaks(r, sl, (80, 78, 72), 8, 0.35)
sl = cracks(r, sl, 3, 0.6)
save('slate_roof', sl, folder=M)

# побелка: известь с потёками и облупками
r = rng('whitewash')
ww = base(r, (200, 198, 186), 7)
ww = streaks(r, ww, (150, 140, 116), 8, 0.3)
peel = rust_mask(r, 0.12, (6, 12, 24))
ww[peel] = ww[peel] * 0.3 + np.array([150, 146, 138]) * 0.7
ww[peel ^ np.roll(peel, 1, 0)] *= 0.8
ww = blobs(r, ww, (176, 176, 160), 10, (2, 6), 0.3)
save('whitewash', ww, folder=M)
wd = ww.copy()
br = bricks('wdbr', (124, 56, 42), (128, 120, 108))
holes = rust_mask(rng('wdh'), 0.35, (3, 6, 12))
wd[holes] = br[holes]
wd[holes ^ np.roll(holes, 1, 0)] *= 0.7
save('whitewash_damaged', wd, folder=M)

# бетонный забор ПО-2: ромбы рельефом
r = rng('po2')
pf = base(r, (140, 138, 130), 8)
yy, xx = np.mgrid[0:N, 0:N]
u = (xx + yy) % 16; v = (xx - yy) % 16
pf[(u == 0) | (v == 0)] *= 0.72
pf[(u == 1) | (v == 1)] *= 1.12
pf = bevel(pf, 0, 0, N - 1, N - 1, 1.08, 0.7)
pf = streaks(r, pf, (96, 88, 74), 8, 0.35)
pf = blobs(r, pf, (100, 112, 70), 6, (1, 3), 0.4)
pf = cracks(r, pf, 3, 0.6)
save('concrete_fence', pf, folder=M)

# краска кузовов: крашеный металл со вздутиями ржавчины, царапинами и вмятинами
PAINTS = {'red': (164, 38, 30), 'white': (196, 194, 184), 'blue': (58, 86, 136), 'cream': (206, 186, 128),
          'olive': (88, 98, 58), 'orange': (198, 108, 38), 'green': (58, 108, 78), 'beige': (176, 160, 128)}
for nm, col in PAINTS.items():
    r = rng('car' + nm)
    a = base(r, col, 7)
    a = blobs(r, a, (col[0] * 0.88, col[1] * 0.88, col[2] * 0.86), 10, (3, 8), 0.4)  # выгоревшие пятна
    m = rust_mask(r, 0.16, (4, 8, 16))
    rust = base(rng('crust' + nm), (112, 60, 32), 16)
    a[m] = rust[m]
    ring = m ^ np.roll(m, 1, 1)
    a[ring] *= 0.7
    for _ in range(5):  # царапины до грунта
        x, y = r.integers(0, N), r.integers(0, N); L = r.integers(4, 12)
        for k in range(L): a[(y + k // 3) % N, (x + k) % N] = a[(y + k // 3) % N, (x + k) % N] * 0.5 + np.array((128, 120, 110)) * 0.5
    a = streaks(r, a, (104, 56, 30), 6, 0.45)
    a[N // 2] *= 0.82  # шов панели
    save('car_paint_' + nm, a, folder=M)

# стекло кабины: тёмное, пыльное, с паутиной трещин
r = rng('carglass')
cg = np.full((N, N, 3), 60.0) + fbm(r)[:, :, None] * 60
cga = 150 + 80 * fbm(rng('cga'))
cx, cy = 40, 22
for k in range(8):
    ang = k * math.pi / 4 + r.random() * 0.4
    for t in range(30):
        x, y = int(cx + math.cos(ang) * t), int(cy + math.sin(ang) * t)
        if 0 <= x < N and 0 <= y < N: cg[y, x] = (150, 154, 154); cga[y, x] = 230
cg[:3] = cg[-3:] = cg[:, :3] = cg[:, -3:] = (34, 34, 34); cga[:3] = cga[-3:] = cga[:, :3] = cga[:, -3:] = 255
save('car_glass', cg, cga, folder=M)

# колесо: покрышка с ободом
r = rng('wheel')
wh = np.zeros((N, N, 3))
for y in range(N):
    for x in range(N):
        d = math.hypot(x - N / 2 + 0.5, y - N / 2 + 0.5)
        if d < 9: wh[y, x] = (96, 94, 90)
        elif d < 20: wh[y, x] = (128, 124, 118) if (int(math.degrees(math.atan2(y - 32, x - 32))) // 30) % 2 else (104, 100, 96)
        elif d < 22: wh[y, x] = (70, 68, 64)
        elif d < 31: wh[y, x] = (30, 30, 32)
        else: wh[y, x] = (22, 22, 24)
wh += r.normal(0, 5, (N, N, 1))
wh = blobs(r, wh, (110, 60, 32), 8, (1, 3), 0.6)  # ржавый диск
for k in range(6):
    ang = k * math.pi / 3
    x, y = int(32 + math.cos(ang) * 14), int(32 + math.sin(ang) * 14)
    wh[y - 1:y + 2, x - 1:x + 2] = (50, 46, 42)
save('car_wheel', wh, folder=M)
tr = base(rng('tread'), (32, 32, 34), 4)
for y in range(0, N, 8):
    for x in range(N): tr[(y + (x // 16) * 2) % N:(y + (x // 16) * 2) % N + 3, x] = (18, 18, 20)
tr = blobs(rng('mudt'), tr, (82, 66, 50), 10, (1, 4), 0.6)
save('car_wheel_tread', tr, folder=M)

# фара и решётка радиатора
r = rng('headlight')
hl = base(r, (70, 70, 68), 5)
for y in range(N):
    for x in range(N):
        d = math.hypot(x - 31.5, y - 31.5)
        if d < 20: hl[y, x] = np.array((206, 196, 150)) * (1 - d / 60)
        elif d < 25: hl[y, x] = np.array((176, 176, 170)) * (1.15 if y < x else 0.8)
hl = cracks(r, hl, 3, 0.6, (8, 20))
save('car_headlight', hl, folder=M)
r = rng('grille')
gr = base(r, (36, 36, 36), 4)
for y in range(4, N - 4):
    if (y // 4) % 2 == 0: gr[y, 4:N - 4] = np.array((160, 160, 156)) * (1.15 if y % 4 == 0 else 0.9)
gr[:4] = gr[-4:] = gr[:, :4] = gr[:, -4:] = (120, 118, 112)
gr = blobs(r, gr, (112, 62, 34), 10, (1, 3), 0.6)
save('car_grille', gr, folder=M)


# бочки
def drum(name, col, mark=None):
    r = rng(name)
    a = base(r, col, 8)
    for x in range(N): a[:, x] *= 0.75 + 0.35 * math.sin(math.pi * x / N)  # цилиндр
    for y in (6, 7, 8, N // 2 - 1, N // 2, N - 9, N - 8, N - 7): a[y] *= 0.6
    for y in (5, N // 2 - 2, N - 10): a[y] *= 1.15
    m = rust_mask(r, 0.2)
    a[m] = a[m] * 0.3 + np.array([110, 58, 30]) * 0.7
    a = streaks(r, a, (90, 46, 24), 10, 0.55)
    if mark == 'rad':
        # знак радиации: рисуем в 4× и уменьшаем, чтобы края были ровными
        big = Image.new('L', (N * 4, N * 4), 0)
        dd = ImageDraw.Draw(big)
        cx, cy, R = N * 2, (N // 2 + 10) * 4, 14 * 4
        for st in (-120, 0, 120):
            dd.pieslice([cx - R, cy - R, cx + R, cy + R], st - 30 - 90, st + 30 - 90, fill=255)
        dd.ellipse([cx - 22, cy - 22, cx + 22, cy + 22], fill=0)
        dd.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], fill=255)
        mk = np.asarray(big.resize((N, N), Image.LANCZOS), float) / 255
        a = mix(a, (22, 20, 16), mk * 0.92)
        a[14:20, 12:52] = a[14:20, 12:52] * 0.15 + np.array((22, 20, 16)) * 0.85  # полоса маркировки
    return a


save('rusty_barrel_side', drum('rbar', (128, 64, 30)), folder=M)
save('radioactive_barrel_side', drum('radbar', (200, 166, 40), 'rad'), folder=M)
for nm, col in (('rusty_barrel_top', (116, 58, 28)), ('radioactive_barrel_top', (186, 154, 40))):
    r = rng(nm)
    bt = base(r, col, 8)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - 31.5, y - 31.5)
            if 27 < d < 30: bt[y, x] *= 0.6
            if d >= 31: bt[y, x] *= 0.4
    bt[16:21, 40:45] = (28, 24, 20)
    m = rust_mask(r, 0.3)
    bt[m] = bt[m] * 0.3 + np.array([104, 54, 28]) * 0.7
    save(nm, bt, folder=M)
# покрышки
r = rng('tires')
ts = base(r, (30, 30, 32), 4)
for y in range(0, N, 16): ts[y:y + 2] = (14, 14, 16)
for y in range(N):
    for x in range(0, N, 6): ts[y, (x + (y // 16) * 3) % N] *= 0.7
ts = blobs(r, ts, (84, 68, 52), 10, (1, 4), 0.55)
save('tires_side', ts, folder=M)
tt = np.zeros((N, N, 3))
for y in range(N):
    for x in range(N):
        d = math.hypot(x - 31.5, y - 31.5)
        tt[y, x] = (30, 30, 32) if 12 < d < 31 else (12, 12, 12) if d <= 12 else (0, 0, 0)
save('tires_top', tt + rng('tt').normal(0, 4, (N, N, 1)), folder=M)


# ящики
def crate(name, col, frame, mark=None):
    r = rng(name)
    a = boards(name + 'b', col, 16, 8) if mark != 'military' else base(r, col, 8)
    if mark == 'military':
        for y in range(0, N, 16): a[y] *= 0.7
    a[:6] = a[-6:] = frame; a[:, :6] = a[:, -6:] = frame
    a = bevel(a, 0, 0, N - 1, N - 1, 1.15, 0.6)
    a = bevel(a, 6, 6, N - 7, N - 7, 0.75, 1.1)
    if mark == 'wood':
        for t in range(6, N - 6):  # диагональная распорка
            a[t, t - 2:t + 3] = np.array(frame) * 1.05
    if mark == 'military':
        a[26:38, 14:50] = (200, 196, 170)  # трафарет
        for x in range(16, 48, 5): a[29:35, x:x + 3] = (60, 64, 40)
        a[30:34, 2:6] = a[30:34, N - 6:N - 2] = (60, 56, 50)  # ручки
    if mark == 'med':
        a[22:42, 28:36] = (176, 30, 28); a[28:36, 22:42] = (176, 30, 28)
    a = grime(r, a, 0.25)
    return a


save('wooden_crate', crate('wcrate', (126, 100, 66), (92, 72, 48), 'wood'), folder=M)
save('military_crate', crate('mcrate', (78, 88, 52), (62, 70, 42), 'military'), folder=M)
save('medical_crate', crate('medcrate', (188, 186, 176), (150, 148, 140), 'med'), folder=M)

# ======================================================================== пак
json.dump({"pack": {"pack_format": 15, "description": "Stalker Zone: текстуры 64×64 в стиле S.T.A.L.K.E.R."}},
          open(os.path.join(PACK, 'pack.mcmeta'), 'w', encoding='utf-8'), ensure_ascii=False)
icon = Image.new('RGB', (128, 128), (40, 44, 36))
d = ImageDraw.Draw(icon)
d.polygon([(64, 10), (118, 110), (10, 110)], outline=(220, 180, 40), width=8)
d.ellipse([52, 62, 76, 86], fill=(220, 180, 40))
icon.save(os.path.join(PACK, 'pack.png'))
print('pack:', len(os.listdir(TEX)), 'mod:', len(os.listdir(MODTEX)))
