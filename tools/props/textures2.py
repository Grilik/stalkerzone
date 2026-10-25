"""Текстуры пропсов части 2 (v1.13): брезент, ящики, знаки, приборы, лабораторное стекло, плакаты, мозаика."""
import math
import numpy as np
from PIL import Image, ImageDraw
from textures import S, noise, fbm, to_img, rusty


def _fabric(base, rng, weave=0.08, dirt=0.3):
    a = np.ones((S, S, 3), np.float32) * np.array(base, np.float32)
    yy, xx = np.mgrid[0:S, 0:S]
    a *= (1 - weave * ((xx + yy) % 2))[..., None]
    a *= (0.82 + 0.3 * fbm(S, S, rng))[..., None]
    d = fbm(S, S, rng, (40, 12), (0.7, 0.3))
    a *= (1 - dirt * np.clip(d - 0.3, 0, 1))[..., None]
    return a


def _text(img, xy, text, fill, size=1):
    d = ImageDraw.Draw(img)
    for dx in range(size):
        for dy in range(size):
            d.text((xy[0] + dx, xy[1] + dy), text, fill=fill)


def gen_extra(seed=11):
    rng = np.random.default_rng(seed)
    T = {}
    T['tarp'] = _fabric((92, 98, 70), rng)
    T['tarp_army'] = _fabric((78, 86, 58), rng, 0.1, 0.35)
    for x in range(0, S, 32):
        T['tarp_army'][:, x:x + 2] *= 0.7  # швы полотнищ
    T['mattress'] = _fabric((120, 112, 96), rng, 0.05, 0.5)
    for y in range(0, S, 12):
        T['mattress'][y:y + 2] = T['mattress'][y:y + 2] * 0.6 + np.array([60, 70, 110]) * 0.4  # полоски матраса
    T['pillow'] = _fabric((170, 166, 150), rng, 0.04, 0.4)
    T['sack'] = _fabric((176, 166, 140), rng, 0.12, 0.25)
    # тлеющие угли
    e = np.zeros((S, S, 3), np.float32)
    n = fbm(S, S, rng, (16, 6, 2), (0.5, 0.3, 0.2))
    e[..., 0] = 60 + 195 * np.clip(n * 1.4 - 0.2, 0, 1)
    e[..., 1] = 20 + 120 * np.clip(n * 1.6 - 0.6, 0, 1)
    e[..., 2] = 10
    T['embers'] = e
    # армейский ящик патронов: зелёный, с трафаретом
    img = to_img(rusty((74, 86, 56), rng, 0.02, 0.15, streaks=False, dirt=0.25))
    d = ImageDraw.Draw(img)
    for x in (0, S // 2 - 1, S - 3):
        d.rectangle([x, 0, x + 2, S], fill=(50, 58, 38, 255))
    _text(img, (14, 40), "7,62x39", (230, 230, 210, 255), 2)
    _text(img, (14, 70), "880 ШТ", (230, 230, 210, 255), 2)
    T['crate_ammo'] = np.asarray(img, np.float32)[..., :3]
    # полосатая (красно-белая) краска
    st = np.zeros((S, S, 3), np.float32)
    for i in range(S):
        for j in range(S):
            st[i, j] = (180, 34, 28) if ((i + j) // 22) % 2 == 0 else (215, 215, 205)
    T['stripe'] = st * (0.85 + 0.2 * fbm(S, S, rng))[..., None]
    # знак радиационной опасности (жёлтый треугольник с трилистником)
    img = Image.new('RGBA', (S, S), (60, 60, 60, 255))
    d = ImageDraw.Draw(img)
    d.polygon([(S / 2, 6), (S - 6, S - 6), (6, S - 6)], fill=(220, 180, 30, 255), outline=(200, 30, 20, 255))
    d.polygon([(S / 2, 18), (S - 18, S - 14), (18, S - 14)], outline=(20, 20, 20, 255))
    cx, cy, R = S / 2, S * 0.64, 22
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - R, cy - R, cx + R, cy + R], a0, a0 + 60, fill=(20, 20, 20, 255))
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], fill=(220, 180, 30, 255))
    d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=(20, 20, 20, 255))
    a = np.asarray(img, np.float32)[..., :3]
    T['sign_rad'] = a * (0.8 + 0.25 * fbm(S, S, rng))[..., None]
    # «СТОЙ! ЗАПРЕТНАЯ ЗОНА»
    img = Image.new('RGBA', (S, S), (225, 222, 210, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, S - 3, S - 3], outline=(190, 30, 24, 255), width=5)
    _text(img, (36, 22), "СТОЙ!", (190, 30, 24, 255), 3)
    _text(img, (16, 56), "ЗАПРЕТНАЯ", (30, 30, 30, 255), 2)
    _text(img, (36, 82), "ЗОНА", (30, 30, 30, 255), 2)
    a = np.asarray(img, np.float32)[..., :3]
    rr = noise(S, S, 3, rng) * noise(S, S, 10, rng)
    rust = np.clip((rr - 0.35) * 6, 0, 1)[..., None]
    T['sign_stop'] = a * (1 - rust) + np.array([118, 62, 32]) * rust
    T['wire'] = rusty((120, 116, 110), rng, 0.6, 0.0, streaks=False)
    # лабораторное: светящаяся жидкость, стекло, корпус приборов, панель, экран, лента
    lq = np.ones((S, S, 3), np.float32) * np.array([60, 220, 120], np.float32)
    lq *= (0.7 + 0.4 * fbm(S, S, rng, (24, 8, 3), (0.5, 0.3, 0.2)))[..., None]
    for _ in range(40):
        x, y, r = rng.integers(0, S), rng.integers(0, S), rng.integers(1, 4)
        yy, xx = np.ogrid[0:S, 0:S]
        lq[(xx - x) ** 2 + (yy - y) ** 2 <= r * r] = (190, 255, 210)
    T['lab_liquid'] = lq
    T['lab_glass'] = np.ones((S, S, 3), np.float32) * np.array([150, 190, 190]) * (0.8 + 0.3 * fbm(S, S, rng))[..., None]
    T['console_body'] = rusty((120, 128, 118), rng, 0.12, 0.25, streaks=False, dirt=0.2)
    img = to_img(rusty((70, 76, 72), rng, 0.05, 0.1, streaks=False, dirt=0.1))
    d = ImageDraw.Draw(img)
    for i in range(6):
        for j in range(4):
            x, y = 10 + i * 19, 10 + j * 28
            if (i + j) % 3 == 0:
                d.ellipse([x, y, x + 12, y + 12], fill=(30, 30, 30, 255), outline=(200, 200, 200, 255))   # стрелочный прибор
                d.line([(x + 6, y + 6), (x + 10, y + 3)], fill=(220, 40, 30, 255))
            else:
                col = [(220, 50, 40), (60, 200, 80), (230, 190, 50), (200, 200, 200)][(i * 3 + j) % 4]
                d.rectangle([x + 2, y + 2, x + 8, y + 8], fill=col + (255,))
                d.rectangle([x + 2, y + 14, x + 4, y + 22], fill=(190, 190, 190, 255))                   # тумблер
    T['console_panel'] = np.asarray(img, np.float32)[..., :3]
    sc = np.ones((S, S, 3), np.float32) * np.array([30, 46, 40], np.float32)
    for y in range(0, S, 4): sc[y] *= 1.25
    sc[30:34, 10:90] = (120, 220, 140); sc[50:54, 10:60] = (120, 220, 140)
    T['screen'] = sc * (0.85 + 0.2 * fbm(S, S, rng))[..., None]
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    rr = np.hypot(xx - S / 2, yy - S / 2)
    tp = np.where(rr < S * 0.16, 200, 40 + 20 * np.sin(rr / 1.2))
    T['tape'] = np.dstack([tp, tp * 0.9, tp * 0.8]).astype(np.float32)
    tw = np.ones((S, S, 3), np.float32) * 210
    tw[(np.arange(S) % 32) < 2] = 140
    tw[:, (np.arange(S) % 32) < 2] = 140
    T['tile_white'] = tw * (0.85 + 0.2 * fbm(S, S, rng))[..., None]
    T['locker'] = rusty((86, 104, 92), rng, 0.25, 0.3)
    # шпон (лакированное дерево мебели)
    y = np.arange(S)[:, None].astype(np.float32)
    w = (np.sin((y + fbm(S, S, rng) * 40) / 2.5) + 1) / 2
    T['wood_vlak'] = np.array([112, 66, 36], np.float32) * (0.7 + 0.3 * w)[..., None] * np.ones((1, S, 1))
    # мозаика на остановке: стилизованное солнце и колосья (без надписей и персонажей)
    img = Image.new('RGBA', (S, S), (60, 110, 150, 255))
    d = ImageDraw.Draw(img)
    d.ellipse([34, 14, 94, 74], fill=(230, 170, 40, 255))
    for k in range(12):
        a = 2 * math.pi * k / 12
        d.line([(64 + 34 * math.cos(a), 44 + 34 * math.sin(a)), (64 + 52 * math.cos(a), 44 + 52 * math.sin(a))], fill=(230, 170, 40, 255), width=4)
    for x in (16, 40, 88, 112):
        d.line([(x, S - 6), (x + 6, 80)], fill=(200, 150, 40, 255), width=4)
        for k in range(4):
            d.ellipse([x + 2 + k, 82 + k * 8, x + 10 + k, 88 + k * 8], fill=(220, 180, 60, 255))
    a = np.asarray(img, np.float32)[..., :3]
    tiles = (np.arange(S)[:, None] % 6 == 0) | (np.arange(S)[None, :] % 6 == 0)
    a[tiles] *= 0.7
    miss = noise(S, S, 4, rng) > 0.82
    a[miss] = (140, 136, 128)
    T['mosaic'] = a
    img = Image.new('RGBA', (S, S), (40, 70, 150, 255))
    _text(img, (8, 50), "СОЮЗПЕЧАТЬ", (240, 240, 240, 255), 2)
    T['sign_kiosk'] = np.asarray(img, np.float32)[..., :3]
    # плакат (стилизованный советский): серп-молот-шестерня как абстракция, лозунг о технике безопасности
    img = Image.new('RGBA', (S, S), (214, 196, 160, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, S, 40], fill=(180, 36, 30, 255))
    _text(img, (10, 10), "БЕРЕГИ СЕБЯ!", (240, 230, 200, 255), 2)
    d.ellipse([30, 50, 98, 118], outline=(40, 40, 40, 255), width=6)                     # шестерня
    for k in range(10):
        a = 2 * math.pi * k / 10
        d.rectangle([64 + 38 * math.cos(a) - 4, 84 + 38 * math.sin(a) - 4, 64 + 38 * math.cos(a) + 4, 84 + 38 * math.sin(a) + 4], fill=(40, 40, 40, 255))
    d.polygon([(54, 104), (64, 64), (74, 104)], fill=(180, 36, 30, 255))                  # молния
    a = np.asarray(img, np.float32)[..., :3]
    torn = noise(S, S, 6, rng) * noise(S, S, 2, rng)
    a *= (0.75 + 0.35 * fbm(S, S, rng))[..., None]
    a[torn > 0.55] = (110, 104, 96)
    T['poster'] = a
    # экран балконного ограждения: волнистый шифер/профлист с облезшей краской
    bp = rusty((150, 160, 150), rng, 0.25, 0.3)
    for x in range(0, S, 10): bp[:, x:x + 4] *= 0.82
    T['balcony_panel'] = bp
    return {k: to_img(v) for k, v in T.items()}
