"""Процедурные текстуры для 3D-пропсов: облезлая краска с ржавчиной, резина, стекло, бетон, дерево, мешковина."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 128


def noise(h, w, scale, rng):
    small = rng.random((max(2, h // scale), max(2, w // scale)))
    im = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im, np.float32) / 255.0


def fbm(h, w, rng, scales=(48, 16, 6, 2), weights=(0.5, 0.25, 0.15, 0.1)):
    return sum(noise(h, w, s, rng) * wt for s, wt in zip(scales, weights))


def to_img(a):
    a = np.clip(a, 0, 255).astype(np.uint8)
    if a.shape[-1] == 3:
        a = np.dstack([a, np.full(a.shape[:2], 255, np.uint8)])
    return Image.fromarray(a, 'RGBA')


RUST = np.array([118, 62, 32], np.float32)
RUST_D = np.array([70, 38, 22], np.float32)


def rusty(base, rng, rust=0.35, chip=0.25, streaks=True, dirt=0.2):
    h = w = S
    col = np.ones((h, w, 3), np.float32) * np.array(base, np.float32)
    n = fbm(h, w, rng)
    col *= (0.85 + 0.25 * n)[..., None]
    # сколы до грунта
    c = noise(h, w, 10, rng) * noise(h, w, 3, rng)
    m = np.clip((c - (0.42 - chip * 0.3)) * 8, 0, 1)[..., None]
    col = col * (1 - m) + np.array([95, 98, 96], np.float32) * m
    # ржавчина пятнами
    r = fbm(h, w, rng, (32, 10, 3), (0.5, 0.35, 0.15))
    rm = np.clip((r - (0.62 - rust * 0.3)) * 5, 0, 1)[..., None]
    rc = RUST * (0.75 + 0.5 * noise(h, w, 2, rng))[..., None]
    col = col * (1 - rm) + rc * rm
    # потёки вниз
    if streaks:
        st = np.zeros((h, w), np.float32)
        for _ in range(int(14 * rust + 3)):
            x = rng.integers(0, w); y = rng.integers(0, h // 2); ln = rng.integers(h // 4, h)
            wd = rng.integers(1, 4)
            st[y:y + ln, max(0, x - wd):x + wd] += np.linspace(0.8, 0, min(ln, h - y))[:, None]
        st = np.asarray(Image.fromarray(np.clip(st * 255, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), np.float32) / 255
        col = col * (1 - st[..., None] * 0.6) + RUST_D * st[..., None] * 0.6
    if dirt:
        d = fbm(h, w, rng, (64, 20), (0.7, 0.3))
        col *= (1 - dirt * d)[..., None]
    return col


def gen_all(seed=5):
    rng = np.random.default_rng(seed)
    T = {}
    paints = {'red': (150, 38, 30), 'white': (196, 196, 186), 'blue': (52, 82, 128), 'cream': (205, 186, 140),
              'green': (66, 104, 66), 'beige': (176, 152, 112), 'orange': (196, 104, 34), 'olive': (84, 92, 58)}
    for k, c in paints.items():
        T['paint_' + k] = rusty(c, rng, 0.2, 0.12)
    # сгоревшая краска: чёрно-рыжая окалина
    b = rusty((40, 34, 30), rng, 0.8, 0.1, dirt=0.1)
    b = b * 0.8 + RUST * 0.2 * fbm(S, S, rng)[..., None]
    T['paint_burnt'] = b
    T['rust'] = rusty((112, 64, 36), rng, 0.9, 0.0)
    T['steel'] = rusty((92, 94, 92), rng, 0.25, 0.0, streaks=False)
    T['steel_dark'] = rusty((46, 48, 48), rng, 0.2, 0.0, streaks=False)
    T['galv'] = rusty((140, 146, 146), rng, 0.15, 0.0)
    T['chrome'] = rusty((170, 172, 172), rng, 0.3, 0.0, streaks=False, dirt=0.1)
    # резина с протектором
    t = np.ones((S, S, 3), np.float32) * 34
    t *= (0.8 + 0.4 * fbm(S, S, rng))[..., None]
    for y in range(0, S, 8): t[y:y + 3] *= 0.6
    T['tire'] = t
    T['rim'] = rusty((70, 72, 70), rng, 0.5, 0.0)
    # стекло: грязное, с бликом; трещины
    g = np.ones((S, S, 3), np.float32) * np.array([58, 72, 78], np.float32)
    g *= (0.8 + 0.4 * fbm(S, S, rng))[..., None]
    gx = np.arange(S)[None, :] + np.arange(S)[:, None]
    g += (np.exp(-((gx - 90) / 10.0) ** 2) * 60)[..., None]
    img = to_img(g); d = ImageDraw.Draw(img)
    for _ in range(5):
        x, y = rng.integers(10, S - 10, 2)
        for _ in range(6):
            a = rng.random() * 6.28; ln = rng.integers(10, 50)
            d.line([(x, y), (x + math.cos(a) * ln, y + math.sin(a) * ln)], fill=(150, 160, 160, 255), width=1)
    T['glass'] = np.asarray(img, np.float32)[..., :3]
    T['interior'] = rusty((36, 32, 30), rng, 0.2, 0.0, streaks=False)
    T['grille'] = rusty((40, 40, 40), rng, 0.4, 0.0)
    for y in range(0, S, 10): T['grille'][y:y + 4] *= 0.35
    T['headlight'] = np.ones((S, S, 3), np.float32) * np.array([200, 200, 180]) * (0.75 + 0.3 * fbm(S, S, rng))[..., None]
    T['taillight'] = np.ones((S, S, 3), np.float32) * np.array([150, 30, 24]) * (0.75 + 0.3 * fbm(S, S, rng))[..., None]
    T['canvas'] = rusty((88, 96, 62), rng, 0.0, 0.0, streaks=False, dirt=0.35)
    # бетон
    cc = np.ones((S, S, 3), np.float32) * np.array([150, 148, 140], np.float32)
    cc *= (0.78 + 0.32 * fbm(S, S, rng))[..., None]
    pores = noise(S, S, 1, rng) > 0.93
    cc[pores] *= 0.6
    moss = np.clip((fbm(S, S, rng, (40, 12), (0.6, 0.4)) - 0.62) * 4, 0, 1)[..., None]
    cc = cc * (1 - moss * 0.7) + np.array([70, 86, 50]) * moss * 0.7
    T['concrete'] = cc
    T['concrete_dark'] = cc * 0.72
    # дерево: доски и торец
    y = np.arange(S)[:, None].astype(np.float32)
    w = (np.sin((y + fbm(S, S, rng) * 30) / 3.0) + 1) / 2
    wood = np.array([96, 70, 44], np.float32) * (0.7 + 0.35 * w)[..., None]
    for x in range(0, S, 32): wood[:, x:x + 2] *= 0.45
    T['wood'] = wood * (1 - 0.25 * fbm(S, S, rng))[..., None]
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    rr = np.hypot(xx - S / 2, yy - S / 2)
    T['wood_end'] = np.array([120, 92, 60], np.float32) * (0.75 + 0.25 * ((np.sin(rr / 2.2) + 1) / 2))[..., None]
    T['pole'] = np.array([62, 48, 36], np.float32) * (0.75 + 0.35 * w.T)[..., None] * (1 - 0.2 * fbm(S, S, rng))[..., None]
    # мешковина
    sb = np.ones((S, S, 3), np.float32) * np.array([140, 124, 88], np.float32)
    weave = ((np.arange(S)[:, None] % 4 < 2) ^ (np.arange(S)[None, :] % 4 < 2)).astype(np.float32)
    sb *= (0.85 + 0.1 * weave + 0.2 * fbm(S, S, rng))[..., None]
    T['sandbag'] = sb
    # баллоны: красный (пропан) с белой надписью-полосой, синий (кислород)
    T['gas_red'] = rusty((160, 40, 34), rng, 0.25, 0.15)
    T['gas_red'][S // 2 - 6:S // 2 + 6] = T['gas_red'][S // 2 - 6:S // 2 + 6] * 0.3 + 170
    T['gas_blue'] = rusty((52, 88, 150), rng, 0.25, 0.15)
    # радиоактивная бочка: жёлтая с чёрной полосой и знаком
    ry = rusty((200, 170, 40), rng, 0.4, 0.2)
    img = to_img(ry); d = ImageDraw.Draw(img)
    cx, cy, R = S * 0.25, S * 0.5, 22
    d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=(20, 20, 20, 255))
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - R, cy - R, cx + R, cy + R], a0, a0 + 60, fill=(20, 20, 20, 255))
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], outline=(200, 170, 40, 255), width=3)
    d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=(20, 20, 20, 255))
    T['rad_yellow'] = np.asarray(img, np.float32)[..., :3]
    T['barrel_blue'] = rusty((40, 70, 120), rng, 0.45, 0.2)
    T['barrel_green'] = rusty((60, 86, 50), rng, 0.45, 0.2)
    T['insulator'] = np.ones((S, S, 3), np.float32) * np.array([70, 140, 110]) * (0.7 + 0.4 * fbm(S, S, rng))[..., None]
    T['lamp'] = np.ones((S, S, 3), np.float32) * np.array([235, 220, 170]) * (0.85 + 0.2 * fbm(S, S, rng))[..., None]
    T['brick'] = brick(rng)
    T['rubble'] = rusty((110, 104, 96), rng, 0.1, 0.0, streaks=False, dirt=0.4)
    T['plate'] = plate(rng)
    T['black'] = rusty((24, 24, 24), rng, 0.15, 0.0, streaks=False)
    T['tank_white'] = rusty((190, 190, 180), rng, 0.5, 0.2)
    return {k: to_img(v) for k, v in T.items()}


def brick(rng):
    a = np.zeros((S, S, 3), np.float32)
    for row in range(0, S, 16):
        off = 0 if (row // 16) % 2 == 0 else 16
        for x in range(-off, S, 32):
            c = np.array([150, 70, 52]) * (0.8 + 0.3 * rng.random())
            a[row + 1:row + 15, max(0, x + 1):min(S, x + 31)] = c
    a[a.sum(-1) == 0] = (130, 124, 112)
    return a * (0.85 + 0.25 * fbm(S, S, rng))[..., None]


def plate(rng):
    img = Image.new('RGBA', (S, S), (225, 225, 215, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, S - 3, S - 3], outline=(20, 20, 20, 255), width=3)
    for i, ch in enumerate(['А', '1', '2', '3', '4', 'Б']):
        pass
    # «номер»: прямоугольники цифр
    for i in range(6):
        x = 10 + i * 19
        d.rectangle([x, 40, x + 12, 88], outline=(20, 20, 20, 255), width=4)
    a = np.asarray(img, np.float32)[..., :3]
    return a * (0.8 + 0.25 * fbm(S, S, rng))[..., None]
