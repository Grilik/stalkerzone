"""Текстуры костюмов (слой брони 128×64, вдвое детальнее ванильных 64×32) и иконки 32×32 (v1.13).

Каждый костюм: базовая ткань (цвет, камуфляж), броневставки, разгрузка с подсумками, ремень, наплечники,
шеврон группировки, грязь и потёртости. Раскладка развёртки как у ванильного нагрудника (тело и руки).

Запуск: python3 tools/gen_gear.py"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'stalkerzone', 'textures')
S = 2  # масштаб к ванильной развёртке 64×32

rng = np.random.default_rng(13)


def noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    small = r.random((max(2, h // scale + 1), max(2, w // scale + 1)))
    im = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im, np.float32) / 255


def fabric(h, w, base, seed, weave=0.06):
    n = noise(h, w, 6, seed) * 0.5 + noise(h, w, 2, seed + 1) * 0.5
    col = np.array(base, np.float32)[None, None, :] * (0.86 + 0.26 * n)[..., None]
    # переплетение ткани
    yy, xx = np.mgrid[0:h, 0:w]
    col *= (1 - weave * ((xx + yy) % 2))[..., None]
    return col


def camo(h, w, cols, seed, scale=10):
    """Пятнистый камуфляж из нескольких цветов (порядок — от фона к пятнам)."""
    out = fabric(h, w, cols[0], seed)
    for i, c in enumerate(cols[1:]):
        m = noise(h, w, scale, seed + 10 + i) * 0.7 + noise(h, w, scale // 3 + 1, seed + 20 + i) * 0.3
        mask = (m > 0.58 - 0.04 * i)[..., None]
        out = np.where(mask, fabric(h, w, c, seed + 30 + i), out)
    return out


def digital(h, w, cols, seed, px=2):
    """«Цифра» (армейская): квадратные пиксели."""
    out = fabric(h, w, cols[0], seed)
    r = np.random.default_rng(seed)
    for y in range(0, h, px):
        for x in range(0, w, px):
            v = r.random()
            k = 0 if v < 0.45 else 1 if v < 0.75 else 2 if v < 0.92 else 3
            if k and k < len(cols):
                out[y:y + px, x:x + px] = np.array(cols[k], np.float32) * (0.92 + 0.12 * r.random())
    return out


def grime(col, seed, amount=0.35):
    h, w = col.shape[:2]
    d = noise(h, w, 8, seed) * noise(h, w, 3, seed + 5)
    m = np.clip((d - (0.45 - amount * 0.3)) * 3, 0, 1)[..., None] * amount
    dirt = np.array([70, 58, 40], np.float32)
    return col * (1 - m) + dirt * m


# ---------------------------------------------------------------- развёртка
# (имя, x, y, w, h) в единицах ванильной текстуры 64×32
BODY = {'top': (20, 16, 8, 4), 'bottom': (28, 16, 8, 4), 'right': (16, 20, 4, 12), 'front': (20, 20, 8, 12),
        'left': (28, 20, 4, 12), 'back': (32, 20, 8, 12)}
ARM = {'top': (44, 16, 4, 4), 'bottom': (48, 16, 4, 4), 'out': (40, 20, 4, 12), 'front': (44, 20, 4, 12),
       'in': (48, 20, 4, 12), 'back': (52, 20, 4, 12)}


def R(rect):
    x, y, w, h = rect
    return x * S, y * S, (x + w) * S, (y + h) * S


class Suit:
    def __init__(self, sid, ru, base, pattern='plain', pcols=None, plate=None, plate_style='chest', vest=None,
                 belt=(52, 42, 30), patch=None, hood=False, exo=False, stripe=None, grime_amt=0.3, seed=1, collar=None,
                 sleeve=None, icon_kind='suit'):
        self.__dict__.update(locals())
        del self.__dict__['self']


SUITS = [
    Suit('jacket', 'Кожаная куртка', (64, 46, 34), 'leather', belt=None, collar=(48, 34, 26), seed=1),
    Suit('zarya', 'Заря', (92, 104, 66), 'plain', plate=(78, 82, 76), vest=(70, 78, 50), patch=(200, 180, 60), seed=2),
    Suit('ecolog', 'Эколог', (214, 156, 40), 'plain', plate=(190, 190, 186), plate_style='science', stripe=(230, 230, 230),
         hood=True, patch=(60, 120, 200), grime_amt=0.2, seed=3),
    Suit('bulat', 'Булат', (58, 64, 74), 'plain', plate=(44, 48, 56), plate_style='heavy', vest=(40, 44, 50), patch=(150, 30, 30), seed=4),
    Suit('exo', 'Экзоскелет', (50, 52, 46), 'plain', plate=(70, 72, 70), plate_style='heavy', exo=True, vest=(36, 38, 34), seed=5),
    Suit('bandit', 'Бандит', (32, 30, 30), 'leather', belt=(20, 20, 20), collar=(28, 26, 26), stripe=(160, 160, 160), seed=6,
         sleeve=(36, 34, 34)),
    Suit('seva', 'СЕВА', (96, 100, 72), 'plain', plate=(120, 120, 110), plate_style='science', vest=(84, 86, 60), hood=True,
         stripe=(190, 120, 40), patch=(200, 180, 60), seed=7),
    Suit('duty', 'Броня Долга', (40, 40, 42), 'camo', pcols=[(40, 40, 42), (88, 30, 26), (62, 58, 54)], plate=(48, 46, 46),
         vest=(36, 36, 36), patch=(170, 30, 24), seed=8),
    Suit('duty_heavy', 'Универсальная защита Долга', (36, 36, 38), 'camo', pcols=[(36, 36, 38), (96, 28, 24)], plate=(58, 54, 52),
         plate_style='heavy', vest=(30, 30, 30), patch=(190, 30, 24), seed=9),
    Suit('freedom', 'Ветер свободы', (88, 96, 58), 'camo', pcols=[(88, 96, 58), (60, 72, 40), (130, 120, 80)], vest=(70, 80, 46),
         patch=(70, 160, 60), seed=10),
    Suit('freedom_guard', 'Страж свободы', (74, 88, 52), 'camo', pcols=[(74, 88, 52), (46, 60, 36), (120, 116, 74)], plate=(66, 74, 50),
         plate_style='heavy', vest=(56, 66, 40), patch=(70, 170, 60), seed=11),
    Suit('monolith', 'Монолит', (150, 150, 140), 'camo', pcols=[(150, 150, 140), (96, 98, 92), (190, 190, 184)], plate=(120, 118, 110),
         vest=(110, 108, 100), hood=True, patch=(200, 200, 200), seed=12),
    Suit('ecolog_m', 'Эколог-М', (220, 120, 30), 'plain', plate=(200, 200, 196), plate_style='science', stripe=(240, 240, 240),
         hood=True, patch=(60, 120, 200), grime_amt=0.18, seed=13),
    Suit('proto_scout', 'Следопыт', (84, 88, 92), 'plain', plate=(64, 70, 84), vest=(70, 74, 80), stripe=(80, 140, 200), patch=(120, 90, 200), seed=14),
    Suit('proto_guard', 'Гарант', (70, 76, 84), 'plain', plate=(58, 64, 80), plate_style='heavy', vest=(60, 64, 72), stripe=(80, 140, 200),
         patch=(120, 90, 200), seed=15),
    Suit('proto_exo', 'Атлант', (62, 66, 76), 'plain', plate=(84, 90, 104), plate_style='heavy', exo=True, vest=(50, 54, 62),
         stripe=(80, 140, 200), patch=(120, 90, 200), seed=16),
    Suit('berill', 'Берилл-5М', (96, 104, 76), 'digital', pcols=[(96, 104, 76), (70, 80, 56), (130, 128, 98), (50, 56, 40)],
         plate=(80, 88, 64), vest=(72, 80, 56), patch=(60, 60, 140), seed=17),
    Suit('skat', 'Скат-9', (52, 58, 48), 'digital', pcols=[(52, 58, 48), (40, 46, 38), (80, 84, 66)], plate=(42, 46, 40),
         plate_style='heavy', vest=(36, 40, 34), patch=(60, 60, 140), seed=18),
    Suit('merc', 'Наёмник', (70, 78, 92), 'camo', pcols=[(70, 78, 92), (46, 52, 64), (120, 126, 136)], vest=(52, 58, 68),
         patch=(220, 220, 220), seed=19),
]


def base_cloth(s, h, w, seed):
    if s.pattern == 'camo':
        c = camo(h, w, s.pcols, seed)
    elif s.pattern == 'digital':
        c = digital(h, w, s.pcols, seed)
    elif s.pattern == 'leather':
        c = fabric(h, w, s.base, seed, weave=0.0)
        cr = noise(h, w, 2, seed + 3)
        c *= (0.9 + 0.2 * cr)[..., None]
        # морщины и потёртости кожи
        sh = noise(h, w, 4, seed + 9)
        c += ((sh > 0.72) * 28)[..., None]
    else:
        c = fabric(h, w, s.base, seed)
    return c


def armor_texture(s):
    W, H = 64 * S, 32 * S
    img = np.zeros((H, W, 4), np.float32)
    cloth = base_cloth(s, H, W, s.seed)
    cloth = grime(cloth, s.seed + 100, s.grime_amt)
    img[..., :3] = cloth
    # непрозрачны только области развёртки тела и рук
    alpha = np.zeros((H, W), np.float32)
    for rect in list(BODY.values()) + list(ARM.values()):
        x0, y0, x1, y1 = R(rect)
        alpha[y0:y1, x0:x1] = 255
    img[..., 3] = alpha
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')
    d = ImageDraw.Draw(im)

    def shade(c, k):
        return tuple(int(max(0, min(255, v * k))) for v in c) + (255,)

    fx0, fy0, fx1, fy1 = R(BODY['front'])
    bx0, by0, bx1, by1 = R(BODY['back'])
    # воротник / капюшон (верх тела)
    if s.collar:
        d.rectangle([fx0, fy0, fx1 - 1, fy0 + 2], fill=shade(s.collar, 1.0))
        d.polygon([(fx0 + 5, fy0), (fx0 + 8, fy0 + 7), (fx0 + 11, fy0)], fill=shade(s.collar, 0.7))
    if s.hood:
        d.rectangle([bx0, by0, bx1 - 1, by0 + 5], fill=shade(s.base, 0.8))
        d.rectangle([bx0 + 2, by0 + 1, bx1 - 3, by0 + 4], fill=shade(s.base, 0.65))
    # застёжка-молния по центру груди
    zx = (fx0 + fx1) // 2
    d.line([(zx, fy0 + 2), (zx, fy1 - 1)], fill=shade(s.base, 0.55), width=1)
    for yy in range(fy0 + 3, fy1 - 1, 3):
        d.point((zx, yy), fill=(150, 150, 150, 255))
    # бронепластины / вставки
    if s.plate:
        if s.plate_style == 'heavy':
            d.rectangle([fx0 + 1, fy0 + 3, fx1 - 2, fy0 + 12], fill=shade(s.plate, 1.0), outline=shade(s.plate, 0.6))
            d.rectangle([fx0 + 2, fy0 + 13, fx1 - 3, fy0 + 17], fill=shade(s.plate, 0.92), outline=shade(s.plate, 0.6))
            for yy in (fy0 + 6, fy0 + 9):
                d.line([(fx0 + 2, yy), (fx1 - 3, yy)], fill=shade(s.plate, 0.75))
            d.rectangle([bx0 + 1, by0 + 6, bx1 - 2, by0 + 16], fill=shade(s.plate, 0.95), outline=shade(s.plate, 0.6))
            # наплечники
            for arm in (ARM['top'],):
                ax0, ay0, ax1, ay1 = R(arm)
                d.rectangle([ax0, ay0, ax1 - 1, ay1 - 1], fill=shade(s.plate, 1.05), outline=shade(s.plate, 0.6))
            ox0, oy0, ox1, oy1 = R(ARM['out'])
            d.rectangle([ox0, oy0, ox1 - 1, oy0 + 6], fill=shade(s.plate, 1.0), outline=shade(s.plate, 0.6))
            fr0, _, fr1, _ = R(ARM['front'])
            d.rectangle([fr0, oy0, fr1 - 1, oy0 + 6], fill=shade(s.plate, 0.95), outline=shade(s.plate, 0.6))
        elif s.plate_style == 'science':
            # прорезиненные вставки и шланг дыхательной системы
            d.rectangle([fx0 + 2, fy0 + 3, fx1 - 3, fy0 + 10], fill=shade(s.plate, 1.0), outline=shade(s.plate, 0.7))
            d.arc([fx0 + 1, fy0 + 6, fx0 + 9, fy0 + 20], 90, 270, fill=(40, 40, 40, 255), width=2)
            d.rectangle([bx0 + 3, by0 + 5, bx1 - 4, by0 + 16], fill=(170, 170, 166, 255), outline=(90, 90, 90, 255))  # баллон
            d.rectangle([bx0 + 5, by0 + 6, bx1 - 6, by0 + 15], fill=(200, 200, 196, 255))
        else:
            d.rectangle([fx0 + 2, fy0 + 3, fx1 - 3, fy0 + 11], fill=shade(s.plate, 1.0), outline=shade(s.plate, 0.65))
            d.rectangle([bx0 + 2, by0 + 4, bx1 - 3, by0 + 12], fill=shade(s.plate, 0.95), outline=shade(s.plate, 0.65))
    # разгрузка с подсумками
    if s.vest:
        for i in range(3):
            px = fx0 + 1 + i * 5
            d.rectangle([px, fy0 + 12, px + 3, fy0 + 17], fill=shade(s.vest, 1.0), outline=shade(s.vest, 0.6))
            d.line([(px, fy0 + 13), (px + 3, fy0 + 13)], fill=shade(s.vest, 0.7))
        d.line([(fx0 + 2, fy0), (fx0 + 4, fy0 + 12)], fill=shade(s.vest, 0.8), width=2)
        d.line([(fx1 - 3, fy0), (fx1 - 5, fy0 + 12)], fill=shade(s.vest, 0.8), width=2)
    # ремень
    if s.belt:
        for rect in (BODY['front'], BODY['back'], BODY['right'], BODY['left']):
            x0, y0, x1, y1 = R(rect)
            d.rectangle([x0, y1 - 5, x1 - 1, y1 - 3], fill=shade(s.belt, 1.0))
        d.rectangle([zx - 2, fy1 - 6, zx + 1, fy1 - 2], fill=(150, 140, 110, 255))
    # полосы (светоотражающие у научных, лампасы у бандитов, синие у прототипов)
    if s.stripe:
        for rect in (ARM['out'], ARM['front'], ARM['back'], ARM['in']):
            x0, y0, x1, y1 = R(rect)
            d.rectangle([x0, y0 + 12, x1 - 1, y0 + 13], fill=shade(s.stripe, 1.0))
        d.rectangle([fx0, fy0 + 19, fx1 - 1, fy0 + 20], fill=shade(s.stripe, 0.95))
    # рукава другого цвета (у курток)
    if s.sleeve:
        for rect in ARM.values():
            x0, y0, x1, y1 = R(rect)
            sub = Image.fromarray(np.clip(fabric(y1 - y0, x1 - x0, s.sleeve, s.seed + 50, 0.0), 0, 255).astype(np.uint8))
            im.paste(sub, (x0, y0))
    # экзоскелет: металлическая рама поверх
    if s.exo:
        frame = (110, 112, 108, 255)
        for rect in (BODY['front'], BODY['back']):
            x0, y0, x1, y1 = R(rect)
            d.rectangle([x0, y0, x0 + 1, y1 - 1], fill=frame)
            d.rectangle([x1 - 2, y0, x1 - 1, y1 - 1], fill=frame)
            d.rectangle([x0, y0 + 11, x1 - 1, y0 + 12], fill=frame)
        for rect in (ARM['out'], ARM['front'], ARM['back']):
            x0, y0, x1, y1 = R(rect)
            d.rectangle([x0 + 3, y0, x0 + 4, y1 - 1], fill=frame)
            d.rectangle([x0, y0 + 10, x1 - 1, y0 + 11], fill=(90, 92, 88, 255))
        # приводы-цилиндры на спине
        d.rectangle([bx0 + 4, by0 + 2, bx0 + 7, by1 - 3], fill=(80, 82, 78, 255), outline=(50, 50, 50, 255))
        d.rectangle([bx1 - 8, by0 + 2, bx1 - 5, by1 - 3], fill=(80, 82, 78, 255), outline=(50, 50, 50, 255))
    # шеврон группировки на левом плече
    if s.patch:
        x0, y0, x1, y1 = R(ARM['out'])
        d.rectangle([x0 + 2, y0 + 3, x1 - 3, y0 + 7], fill=shade(s.patch, 1.0), outline=shade(s.patch, 0.6))
    # потёртости по краям и мелкие царапины
    arr = np.asarray(im, np.float32)
    sc = noise(H, W, 1, s.seed + 300)
    edge = (sc > 0.93)[..., None] * 25
    arr[..., :3] = np.clip(arr[..., :3] + edge, 0, 255)
    return Image.fromarray(arr.astype(np.uint8), 'RGBA')


def icon(s, armor):
    """Иконка 32×32: куртка спереди (корпус и рукава из развёртки), с тенью и контуром."""
    W = 32
    im = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    fx0, fy0, fx1, fy1 = R(BODY['front'])
    front = armor.crop((fx0, fy0, fx1, fy1)).resize((14, 20), Image.LANCZOS)
    ax0, ay0, ax1, ay1 = R(ARM['front'])
    arm = armor.crop((ax0, ay0, ax1, ay1)).resize((6, 18), Image.LANCZOS)
    # рукава под углом
    la = arm.rotate(-14, expand=True, resample=Image.BICUBIC)
    ra = arm.transpose(Image.FLIP_LEFT_RIGHT).rotate(14, expand=True, resample=Image.BICUBIC)
    im.alpha_composite(la, (2, 6))
    im.alpha_composite(ra, (W - ra.width - 2, 6))
    im.alpha_composite(front, (9, 6))
    # горловина
    d = ImageDraw.Draw(im)
    d.polygon([(13, 6), (16, 10), (19, 6)], fill=(0, 0, 0, 0))
    if s.hood:
        d.ellipse([10, 1, 22, 9], fill=tuple(int(v * 0.8) for v in s.base) + (255,))
        d.ellipse([12, 3, 20, 9], fill=(30, 30, 30, 255))
    if s.exo:
        d.rectangle([9, 6, 10, 25], fill=(120, 122, 118, 255))
        d.rectangle([21, 6, 22, 25], fill=(120, 122, 118, 255))
    # контур
    a = np.asarray(im)[..., 3] > 0
    out = np.zeros_like(a)
    out[1:] |= a[:-1]; out[:-1] |= a[1:]; out[:, 1:] |= a[:, :-1]; out[:, :-1] |= a[:, 1:]
    edge = out & ~a
    arr = np.asarray(im).copy()
    arr[edge] = (20, 18, 16, 255)
    return Image.fromarray(arr, 'RGBA')


def goods_icons():
    """Иконки новых товаров: заводские патроны (цинк), заводское снаряжение (ящик), прототипы (кейс)."""
    out = {}
    # цинк заводских патронов
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.polygon([(4, 12), (16, 6), (28, 12), (16, 18)], fill=(120, 126, 110, 255))
    d.polygon([(4, 12), (16, 18), (16, 28), (4, 22)], fill=(84, 90, 74, 255))
    d.polygon([(28, 12), (16, 18), (16, 28), (28, 22)], fill=(100, 106, 90, 255))
    d.line([(6, 17), (14, 21)], fill=(220, 220, 200, 255)); d.line([(6, 19), (12, 22)], fill=(220, 220, 200, 255))
    for x in (18, 21, 24):
        d.rectangle([x, 9 - (x - 18) // 3, x + 1, 13], fill=(200, 160, 70, 255))
    out['good_ammo_pro'] = im
    # ящик заводского снаряжения (зелёный армейский с трафаретом)
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.polygon([(2, 12), (14, 6), (30, 12), (18, 18)], fill=(86, 98, 60, 255))
    d.polygon([(2, 12), (18, 18), (18, 29), (2, 23)], fill=(60, 70, 40, 255))
    d.polygon([(30, 12), (18, 18), (18, 29), (30, 23)], fill=(72, 84, 50, 255))
    for y in (19, 25):
        d.line([(2, y - 6 + (y - 19) // 6), (18, y)], fill=(40, 46, 28, 255))
    d.text((5, 15), "6Б", fill=(220, 220, 200, 255))
    out['good_gear_pro'] = im
    # кейс прототипа (серебристый с синей полосой)
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([3, 9, 29, 27], 3, fill=(170, 174, 182, 255), outline=(70, 72, 80, 255))
    d.rectangle([3, 16, 29, 18], fill=(70, 130, 200, 255))
    d.rounded_rectangle([12, 5, 20, 10], 2, outline=(70, 72, 80, 255), width=2)
    d.rectangle([14, 15, 18, 19], fill=(220, 200, 80, 255))
    out['good_proto'] = im
    return out


def main():
    arm_dir = os.path.join(TEX, 'models', 'armor')
    item_dir = os.path.join(TEX, 'item')
    os.makedirs(arm_dir, exist_ok=True)
    sheet = Image.new('RGBA', (64 * S * 4 + 30, 32 * S * 5 + 40), (70, 70, 70, 255))
    icons = Image.new('RGBA', (len(SUITS) * 70, 70), (70, 70, 70, 255))
    for i, s in enumerate(SUITS):
        a = armor_texture(s)
        a.save(os.path.join(arm_dir, s.sid + '_layer_1.png'))
        ic = icon(s, a)
        ic.save(os.path.join(item_dir, 'suit_' + s.sid + '.png'))
        sheet.alpha_composite(a, ((i % 4) * (64 * S + 8), (i // 4) * (32 * S + 8)))
        icons.alpha_composite(ic.resize((64, 64), Image.NEAREST), (i * 70 + 3, 3))
    for k, im in goods_icons().items():
        im.save(os.path.join(item_dir, k + '.png'))
    os.makedirs('/tmp/claude-0', exist_ok=True)
    sheet.save('/tmp/claude-0/gear_sheet.png')
    icons.save('/tmp/claude-0/gear_icons.png')
    print('костюмов', len(SUITS))


if __name__ == '__main__':
    main()
