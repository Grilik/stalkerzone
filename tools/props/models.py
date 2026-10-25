"""Определения 3D-пропсов и техники. Каждая функция возвращает Mesh в блоках:
центр основания в (0.5, 0, 0.5), перед (нос машины) смотрит в +x."""
import math
import numpy as np
from mesh import Mesh, box, cylinder, lathe, torus, wheel, rrect, circle, ring_x, ring_y, ring_z

C = 0.5  # центр блока


# =========================================================================== техника
def body_loft(m, secs, mat='paint', segs=3, mats=None):
    """secs: [(x, y0, y1, half_w, r)] вдоль x (от задка к носу)."""
    rings = [ring_x(C + x, rrect(2 * hw, y1 - y0, r, segs, C, (y0 + y1) / 2)) for x, y0, y1, hw, r in secs]
    m.loft(rings, mat, mats=mats)


def house_loft(m, secs, mat='glass', segs=2):
    """Остеклённая часть: трапеция (низ шире), secs: [(x, y0, y1, hw_bottom, hw_top, r)]."""
    rings = []
    for x, y0, y1, hb, ht, r in secs:
        pts = []
        # обход против часовой в плоскости (z, y): низ справа -> верх справа -> верх слева -> низ слева
        top = [(C + ht - r + r * math.cos(math.radians(a)), y1 - r + r * math.sin(math.radians(a))) for a in np.linspace(0, 90, segs + 1)]
        top += [(C - ht + r + r * math.cos(math.radians(a)), y1 - r + r * math.sin(math.radians(a))) for a in np.linspace(90, 180, segs + 1)]
        pts = [(C + hb, y0)] + top + [(C - hb, y0)]
        rings.append(ring_x(C + x, pts))
    m.loft(rings, mat)


def wheels(m, xs, z_half, R, width, y=None, damage=0, rim='rim'):
    for x in xs:
        for side in (-1, 1):
            c = (C + x, (R if y is None else y) - (0.12 if damage else 0), C + side * z_half)
            if damage:
                cylinder(m, 'z', (c[0], c[1]), R * 0.72, c[2] - width * 0.35, c[2] + width * 0.35, rim, n=12)
            else:
                wheel(m, c, R, width, 'z', 'tire', rim)


def lights_front(m, x, y, z_off, r=0.09, mat='headlight', n=12):
    for s in (-1, 1):
        cylinder(m, 'x', (C + s * z_off, y), r, C + x - 0.03, C + x + 0.03, mat, n=n, cap_mat=mat)


def plate(m, x, y, front=True):
    d = 0.02 if front else -0.02
    box(m, C + x, y - 0.06, C - 0.2, C + x + d, y + 0.06, C + 0.2, 'plate')


def pillars(m, xs, y0, y1, hw, t=0.07, mat='paint', lean=None):
    for i, x in enumerate(xs):
        for s in (-1, 1):
            box(m, C + x - t, y0, C + s * hw - t, C + x + t, y1, C + s * hw + t, mat)


def car(kind, damage=0):
    m = Mesh()
    gl = damage == 0
    if kind in ('zhiguli', 'moskvich'):
        L = 4.1 if kind == 'zhiguli' else 4.25
        hl = L / 2
        body_loft(m, [(-hl, 0.33, 0.92, 0.78, 0.12), (-hl + 0.12, 0.3, 0.96, 0.8, 0.14), (hl - 0.15, 0.3, 0.95, 0.8, 0.14), (hl, 0.36, 0.86, 0.77, 0.12)])
        y0, y1 = 0.95, 1.42
        fx, rx = 0.62 if kind == 'zhiguli' else 0.55, -1.05
        if gl:
            house_loft(m, [(rx - 0.25, y0, y0 + 0.02, 0.74, 0.6, 0.05), (rx, y0, y1, 0.74, 0.6, 0.08), (fx, y0, y1, 0.74, 0.6, 0.08), (fx + 0.45, y0, y0 + 0.02, 0.74, 0.6, 0.05)])
        box(m, C + rx, y1 - 0.04, C - 0.6, C + fx, y1 + 0.02, C + 0.6, 'paint', r=0.03)
        pass
        wheels(m, [-hl + 0.85, hl - 0.8], 0.72, 0.31, 0.2, damage=damage)
        box(m, C + hl - 0.02, 0.32, C - 0.82, C + hl + 0.08, 0.42, C + 0.82, 'chrome', r=0.03)
        box(m, C - hl - 0.08, 0.32, C - 0.82, C - hl + 0.02, 0.42, C + 0.82, 'chrome', r=0.03)
        box(m, C + hl, 0.52, C - 0.36, C + hl + 0.02, 0.8, C + 0.36, 'grille')
        lights_front(m, hl, 0.68, 0.58, 0.1)
        for s in (-1, 1): box(m, C - hl - 0.02, 0.62, C + s * 0.62 - 0.14, C - hl, 0.78, C + s * 0.62 + 0.14, 'taillight')
        plate(m, hl + 0.08, 0.37); plate(m, -hl - 0.08, 0.37, False)
    elif kind == 'zaporozhets':
        L = 3.73; hl = L / 2
        body_loft(m, [(-hl, 0.32, 0.86, 0.72, 0.2), (-hl + 0.25, 0.28, 0.94, 0.76, 0.22), (hl - 0.3, 0.28, 0.92, 0.76, 0.22), (hl, 0.34, 0.78, 0.7, 0.2)])
        if gl:
            house_loft(m, [(-1.05, 0.92, 0.94, 0.7, 0.56, 0.05), (-0.85, 0.92, 1.38, 0.7, 0.56, 0.12), (0.55, 0.92, 1.38, 0.7, 0.56, 0.12), (0.95, 0.92, 0.94, 0.7, 0.56, 0.05)])
        box(m, C - 0.85, 1.33, C - 0.57, C + 0.55, 1.4, C + 0.57, 'paint', r=0.04)
        pass
        for s in (-1, 1):  # «уши» воздухозаборников
            box(m, C - 1.35, 0.75, C + s * 0.78 - 0.08, C - 0.7, 0.98, C + s * 0.78 + 0.08, 'paint', r=0.05)
        wheels(m, [-hl + 0.75, hl - 0.72], 0.68, 0.29, 0.18, damage=damage)
        lights_front(m, hl, 0.62, 0.5, 0.08)
        box(m, C + hl - 0.02, 0.3, C - 0.75, C + hl + 0.06, 0.38, C + 0.75, 'chrome')
        plate(m, hl + 0.06, 0.35)
    elif kind == 'niva':
        L = 3.74; hl = L / 2
        body_loft(m, [(-hl, 0.42, 1.05, 0.8, 0.1), (hl - 0.1, 0.4, 1.05, 0.82, 0.12), (hl, 0.45, 0.95, 0.8, 0.1)])
        if gl:
            house_loft(m, [(-hl + 0.02, 1.05, 1.62, 0.78, 0.68, 0.06), (0.45, 1.05, 1.62, 0.78, 0.68, 0.06), (0.85, 1.05, 1.07, 0.78, 0.68, 0.05)])
        box(m, C - hl, 1.58, C - 0.68, C + 0.45, 1.66, C + 0.68, 'paint', r=0.03)
        pass
        wheels(m, [-hl + 0.7, hl - 0.75], 0.76, 0.36, 0.24, damage=damage)
        box(m, C + hl, 0.6, C - 0.5, C + hl + 0.02, 0.88, C + 0.5, 'grille')
        lights_front(m, hl + 0.01, 0.75, 0.62, 0.1)
        box(m, C + hl - 0.02, 0.4, C - 0.82, C + hl + 0.09, 0.52, C + 0.82, 'black', r=0.03)
        plate(m, hl + 0.09, 0.46)
    elif kind == 'uaz':
        L = 4.03; hl = L / 2
        body_loft(m, [(-hl, 0.48, 1.12, 0.86, 0.06), (hl - 0.05, 0.46, 1.12, 0.86, 0.08), (hl, 0.52, 1.02, 0.84, 0.06)])
        box(m, C + 0.45, 1.12, C - 0.8, C + 0.62, 1.62, C + 0.8, 'glass' if gl else 'paint', r=0.02)  # лобовое
        box(m, C + 0.45, 1.58, C - 0.82, C + 0.62, 1.64, C + 0.82, 'paint')
        # брезентовый тент
        rings = [ring_x(C + x, rrect(1.68, 0.86, 0.18, 3, C, 1.12 + 0.43)) for x in (-hl + 0.05, 0.45)]
        m.loft(rings, 'canvas' if damage < 2 else 'black')
        wheels(m, [-hl + 0.75, hl - 0.8], 0.82, 0.38, 0.26, damage=damage)
        cylinder(m, 'x', (C, 0.92), 0.36, C - hl - 0.25, C - hl - 0.02, 'tire', n=14, cap_mat='rim')  # запаска
        box(m, C + hl, 0.62, C - 0.45, C + hl + 0.02, 0.98, C + 0.45, 'grille')
        lights_front(m, hl + 0.02, 0.85, 0.6, 0.11)
        box(m, C + hl, 0.45, C - 0.86, C + hl + 0.1, 0.56, C + 0.86, 'black')
    elif kind == 'raf':
        L = 4.98; hl = L / 2
        body_loft(m, [(-hl, 0.36, 2.0, 0.88, 0.16), (hl - 0.7, 0.34, 2.02, 0.9, 0.18), (hl - 0.15, 0.36, 1.65, 0.88, 0.2), (hl, 0.42, 1.05, 0.84, 0.16)],
                  mats=lambda i, j: 'paint2' if False else 'paint')
        # окна-полоса и красная полоса
        for s in (-1, 1):
            box(m, C - hl + 0.3, 1.3, C + s * 0.905 - 0.01, C + hl - 0.85, 1.75, C + s * 0.905 + 0.01, 'glass' if gl else 'interior')
            box(m, C - hl + 0.05, 0.85, C + s * 0.905 - 0.012, C + hl - 0.2, 1.0, C + s * 0.905 + 0.012, 'paint2')
        box(m, C + hl - 0.66, 1.2, C - 0.78, C + hl - 0.12, 1.85, C + 0.78, 'glass' if gl else 'interior')
        cylinder(m, 'y', (C + hl - 0.9, C), 0.12, 2.02, 2.18, 'taillight' if damage < 2 else 'black', n=10)  # маячок
        wheels(m, [-hl + 0.9, hl - 1.0], 0.78, 0.33, 0.22, damage=damage)
        lights_front(m, hl + 0.01, 0.7, 0.6, 0.1)
        box(m, C + hl - 0.02, 0.38, C - 0.86, C + hl + 0.07, 0.5, C + 0.86, 'chrome')
    elif kind in ('laz', 'paz'):
        L, W, H = (9.2, 2.5, 2.95) if kind == 'laz' else (7.1, 2.4, 2.8)
        hl, hw = L / 2, W / 2
        body_loft(m, [(-hl, 0.42, H - 0.15, hw - 0.05, 0.35), (-hl + 0.3, 0.4, H, hw, 0.42), (hl - 0.35, 0.4, H, hw, 0.42), (hl, 0.42, H - 0.2, hw - 0.05, 0.35)])
        for s in (-1, 1):
            box(m, C - hl + 0.6, 1.45, C + s * hw - 0.012, C + hl - 0.6, 2.35, C + s * hw + 0.012, 'glass' if gl else 'interior')
            box(m, C - hl + 0.2, 1.12, C + s * hw - 0.014, C + hl - 0.2, 1.32, C + s * hw + 0.014, 'paint2')
            for x in np.arange(-hl + 1.6, hl - 0.6, 1.25):
                box(m, C + x - 0.05, 1.42, C + s * hw - 0.02, C + x + 0.05, 2.38, C + s * hw + 0.02, 'paint')
        box(m, C + hl - 0.01, 1.35, C - hw + 0.25, C + hl + 0.01, 2.45, C + hw - 0.25, 'glass' if gl else 'interior')
        box(m, C - hl - 0.01, 1.6, C - hw + 0.4, C - hl + 0.01, 2.3, C + hw - 0.4, 'glass' if gl else 'interior')
        wheels(m, [-hl + 1.8, hl - 1.9], hw - 0.18, 0.5, 0.3, damage=damage)
        lights_front(m, hl + 0.01, 0.78, hw - 0.35, 0.12)
        box(m, C + hl, 0.4, C - hw + 0.05, C + hl + 0.1, 0.55, C + hw - 0.05, 'chrome')
        box(m, C + hl, 0.6, C - 0.5, C + hl + 0.02, 0.95, C + 0.5, 'grille')
    elif kind in ('zil', 'zil_fire'):
        L = 6.7 if kind == 'zil' else 7.6; hl = L / 2
        # капот и кабина
        body_loft(m, [(hl - 1.9, 0.8, 1.55, 0.85, 0.12), (hl - 0.15, 0.8, 1.45, 0.72, 0.25), (hl, 0.85, 1.3, 0.7, 0.2)])
        body_loft(m, [(hl - 3.0, 0.75, 2.5, 1.12, 0.18), (hl - 1.9, 0.75, 2.5, 1.12, 0.2)])
        box(m, C + hl - 1.92, 1.65, C - 1.0, C + hl - 1.86, 2.35, C + 1.0, 'glass' if gl else 'interior')
        for s in (-1, 1):
            box(m, C + hl - 2.75, 1.7, C + s * 1.125 - 0.01, C + hl - 2.1, 2.3, C + s * 1.125 + 0.01, 'glass' if gl else 'interior')
            # крылья над передними колёсами
            box(m, C + hl - 1.6, 1.0, C + s * 0.95 - 0.22, C + hl - 0.35, 1.12, C + s * 0.95 + 0.22, 'paint', r=0.08)
        box(m, C + hl, 0.85, C - 0.55, C + hl + 0.03, 1.3, C + 0.55, 'grille')
        lights_front(m, hl - 0.4, 1.1, 0.95, 0.12)
        box(m, C + hl - 0.05, 0.55, C - 1.15, C + hl + 0.1, 0.72, C + 1.15, 'black')
        box(m, C - hl, 0.7, C - 0.4, C + hl - 1.0, 0.85, C + 0.4, 'black')  # рама
        if kind == 'zil':
            box(m, C - hl, 0.95, C - 1.2, C + hl - 3.1, 1.05, C + 1.2, 'wood')  # платформа
            for s in (-1, 1):
                box(m, C - hl, 1.05, C + s * 1.2 - 0.04, C + hl - 3.1, 1.6, C + s * 1.2 + 0.04, 'wood')
            box(m, C - hl - 0.04, 1.05, C - 1.2, C - hl + 0.04, 1.6, C + 1.2, 'wood')
            wheels(m, [hl - 1.0, -hl + 1.4, -hl + 2.4], 0.95, 0.5, 0.32, damage=damage)
        else:
            cylinder(m, 'x', (C, 1.75), 0.95, C - hl + 0.1, C + hl - 3.2, 'paint', n=20, cap_mat='paint')  # цистерна
            body_loft(m, [(-hl + 0.1, 0.85, 1.1, 1.2, 0.05), (hl - 3.2, 0.85, 1.1, 1.2, 0.05)], 'paint2')
            for z in (-0.35, 0.35):  # лестница
                box(m, C - hl + 0.4, 2.72, C + z - 0.03, C + hl - 3.4, 2.78, C + z + 0.03, 'chrome')
            for x in np.arange(-hl + 0.5, hl - 3.4, 0.35):
                box(m, C + x - 0.02, 2.72, C - 0.35, C + x + 0.02, 2.76, C + 0.35, 'chrome')
            cylinder(m, 'y', (C + hl - 2.4, C), 0.1, 2.5, 2.66, 'taillight' if damage < 2 else 'black', n=10)
            wheels(m, [hl - 1.0, -hl + 1.6, -hl + 2.6], 0.95, 0.5, 0.32, damage=damage)
    elif kind == 'kamaz':
        L = 7.4; hl = L / 2
        body_loft(m, [(hl - 2.2, 0.95, 3.0, 1.22, 0.15), (hl - 0.1, 0.95, 3.0, 1.22, 0.22), (hl, 1.0, 2.9, 1.2, 0.2)])
        box(m, C + hl - 0.02, 1.95, C - 1.05, C + hl + 0.01, 2.75, C + 1.05, 'glass' if gl else 'interior')
        box(m, C + hl, 1.05, C - 0.7, C + hl + 0.03, 1.6, C + 0.7, 'grille')
        lights_front(m, hl + 0.02, 1.2, 0.95, 0.12)
        box(m, C - hl, 0.85, C - 0.45, C + hl - 2.2, 1.0, C + 0.45, 'black')
        # кузов-самосвал
        body_loft(m, [(-hl, 1.1, 2.4, 1.2, 0.08), (hl - 2.4, 1.1, 2.4, 1.2, 0.08)], 'paint2')
        wheels(m, [hl - 1.2, -hl + 1.2, -hl + 2.5], 1.0, 0.55, 0.36, damage=damage)
    elif kind == 'btr':
        L = 7.5; hl = L / 2
        # корпус-«лодка»: низ уже, борта наклонены, нос клином
        def sec(x, yb, yt, wb, wt, wm):
            pts = [(C + wb, yb), (C + wm, (yb + yt) * 0.5), (C + wt, yt), (C - wt, yt), (C - wm, (yb + yt) * 0.5), (C - wb, yb)]
            return ring_x(C + x, pts)
        rings = [sec(-hl, 0.75, 1.75, 0.95, 1.0, 1.3), sec(-hl + 0.5, 0.55, 2.0, 1.0, 1.05, 1.4), sec(hl - 1.6, 0.55, 2.0, 1.0, 1.05, 1.4),
                 sec(hl - 0.5, 0.7, 1.85, 0.9, 0.9, 1.2), sec(hl, 1.1, 1.4, 0.6, 0.65, 0.8)]
        m.loft(rings, 'paint', smooth=False)
        cylinder(m, 'y', (C - 0.2 + 1.1, C), 0.55, 2.0, 2.3, 'paint', n=14)
        cylinder(m, 'y', (C - 0.2 + 1.1, C), 0.55, 2.3, 2.62, 'paint', n=14, r1=0.32)
        cylinder(m, 'x', (C, 2.42), 0.07, C + 1.2, C + 3.4, 'steel_dark', n=8)   # КПВТ
        cylinder(m, 'x', (C + 0.12, 2.4), 0.04, C + 1.2, C + 2.1, 'steel_dark', n=6)
        for s in (-1, 1):
            box(m, C + hl - 1.2, 1.85, C + s * 0.6 - 0.2, C + hl - 0.9, 2.0, C + s * 0.6 + 0.2, 'glass' if gl else 'black')
        wheels(m, [-hl + 1.0, -hl + 2.4, hl - 2.9, hl - 1.5], 1.25, 0.6, 0.38, y=0.6, damage=damage)
    elif kind == 'tractor':
        body_loft(m, [(0.2, 0.75, 1.35, 0.38, 0.1), (1.7, 0.8, 1.3, 0.35, 0.12)])
        box(m, C - 1.0, 1.0, C - 0.62, C + 0.25, 2.7, C + 0.62, 'glass' if gl else 'interior')
        box(m, C - 1.05, 2.65, C - 0.68, C + 0.3, 2.75, C + 0.68, 'paint', r=0.03)
        for x in (-1.0, 0.25):
            for s in (-1, 1): box(m, C + x - 0.04, 1.0, C + s * 0.62 - 0.04, C + x + 0.04, 2.68, C + s * 0.62 + 0.04, 'paint')
        cylinder(m, 'y', (C + 1.2, C + 0.2), 0.05, 1.3, 2.3, 'black', n=6)   # выхлопная труба
        for s in (-1, 1):
            c = (C - 0.6, 0.78 - (0.12 if damage else 0), C + s * 0.85)
            if damage: cylinder(m, 'z', (c[0], c[1]), 0.5, c[2] - 0.15, c[2] + 0.15, 'rim', n=14)
            else: wheel(m, c, 0.78, 0.4, 'z', 'tire', 'rim')
            c2 = (C + 1.45, 0.42 - (0.1 if damage else 0), C + s * 0.62)
            if damage: cylinder(m, 'z', (c2[0], c2[1]), 0.28, c2[2] - 0.1, c2[2] + 0.1, 'rim', n=12)
            else: wheel(m, c2, 0.42, 0.24, 'z', 'tire', 'rim')
        lights_front(m, 1.71, 1.1, 0.22, 0.07)
    return m


VEHICLES = {
    # вид: (название, краска по умолчанию, вторая краска, длина, ширина, высота)
    'zhiguli': ('ВАЗ-2101 «Жигули»', 'red', 'white', 4.1, 1.7, 1.45),
    'moskvich': ('«Москвич-412»', 'blue', 'white', 4.3, 1.7, 1.45),
    'zaporozhets': ('ЗАЗ-968 «Запорожец»', 'beige', 'white', 3.8, 1.6, 1.4),
    'niva': ('ВАЗ-2121 «Нива»', 'green', 'white', 3.8, 1.7, 1.66),
    'uaz': ('УАЗ-469', 'olive', 'olive', 4.3, 1.8, 2.0),
    'raf': ('РАФ-2203 «Латвия» (скорая)', 'white', 'red', 5.0, 1.8, 2.2),
    'laz': ('Автобус ЛАЗ-695', 'cream', 'red', 9.3, 2.5, 3.0),
    'paz': ('Автобус ПАЗ-672', 'orange', 'white', 7.2, 2.4, 2.85),
    'zil_fire': ('Пожарная АЦ-40 (ЗИЛ-130)', 'red', 'white', 7.7, 2.4, 2.8),
    'zil': ('Грузовик ЗИЛ-130', 'blue', 'white', 6.8, 2.4, 2.5),
    'kamaz': ('Самосвал КамАЗ-5511', 'orange', 'olive', 7.5, 2.5, 3.0),
    'btr': ('БТР-70', 'olive', 'olive', 7.6, 2.8, 2.7),
    'tractor': ('Трактор «Беларусь»', 'blue', 'white', 3.8, 1.9, 2.8),
}

PAINTS = ['red', 'white', 'blue', 'cream', 'green', 'beige', 'orange', 'olive', 'burnt']


# =========================================================================== пропсы (декор)
def barrel(mat='rust', lying=False):
    m = Mesh()
    prof = [(0.27, 0.0), (0.29, 0.02), (0.29, 0.28), (0.305, 0.3), (0.29, 0.32), (0.29, 0.58), (0.305, 0.6), (0.29, 0.62), (0.29, 0.86), (0.27, 0.88)]
    lathe(m, (C, C), prof, mat, n=20)
    cylinder(m, 'y', (C + 0.15, C), 0.04, 0.88, 0.9, 'steel', n=8)   # пробка
    if lying:
        R = np.array([[0, -1, 0], [1, 0, 0], [0, 0, 1]], float)  # повернуть набок: ось y -> x
        m.transform(R, (0, 0, 0))
        a = np.array(m.v); mn = a.min(0); mx = a.max(0)
        m.transform(None, (C - (mn[0] + mx[0]) / 2, -mn[1], C - (mn[2] + mx[2]) / 2))
    return m


def gas_cylinder(mat='gas_red', lying=False):
    m = Mesh()
    prof = [(0.1, 0.0), (0.14, 0.02), (0.15, 0.06), (0.15, 0.95), (0.13, 1.05), (0.08, 1.1), (0.05, 1.12)]
    lathe(m, (C, C), prof, mat, n=16)
    cylinder(m, 'y', (C, C), 0.035, 1.12, 1.22, 'chrome', n=8)
    cylinder(m, 'x', (C, 1.18), 0.025, C - 0.05, C + 0.1, 'chrome', n=6)
    if lying:
        R = np.array([[0, -1, 0], [1, 0, 0], [0, 0, 1]], float)
        m.transform(R)
        a = np.array(m.v); mn = a.min(0); mx = a.max(0)
        m.transform(None, (C - (mn[0] + mx[0]) / 2, -mn[1], C - (mn[2] + mx[2]) / 2))
    return m


def gas_rack():
    m = Mesh()
    for i, (x, z, mat) in enumerate([(-0.25, -0.2, 'gas_red'), (0.05, -0.2, 'gas_blue'), (0.32, -0.15, 'gas_red'), (-0.15, 0.2, 'gas_blue')]):
        g = gas_cylinder(mat); g.transform(None, (x, 0, z)); m.merge(g)
    return m


def cable_spool():
    m = Mesh()
    for z in (0.08, 0.92):
        cylinder(m, 'z', (C, 0.48), 0.48, z - 0.04, z + 0.04, 'wood', n=20, cap_mat='wood_end')
    cylinder(m, 'z', (C, 0.48), 0.3, 0.12, 0.88, 'black', n=16)   # намотанный кабель
    return m


def tire_single():
    m = Mesh(); torus(m, 'y' if False else 'x', (C, 0.12, C), 0.28, 0.12, 'tire', n=18, k=8)
    # лежащая покрышка: тор с осью y — повернём тор с осью x
    m = Mesh()
    t = Mesh(); torus(t, 'z', (0, 0, 0), 0.28, 0.12, 'tire', n=18, k=8)
    t.transform(np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float), (C, 0.12, C))
    m.merge(t)
    return m


def tire_stack(n=3):
    m = Mesh()
    for i in range(n):
        t = Mesh(); torus(t, 'z', (0, 0, 0), 0.28, 0.12, 'tire', n=18, k=8)
        t.transform(np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float), (C + (i % 2) * 0.04, 0.12 + i * 0.24, C - (i % 2) * 0.03))
        m.merge(t)
    return m


def concrete_pipe():
    """Бетонная труба диаметром 1,5 м, лежит вдоль x (2 блока)."""
    m = Mesh()
    R, r, L = 0.78, 0.64, 2.0
    x0, x1 = C - L / 2 + 0.5, C + L / 2 + 0.5
    m.loft([ring_x(x0, circle(R, 24, C, R)), ring_x(x1, circle(R, 24, C, R))], 'concrete', caps=False)
    m.loft([ring_x(x1, circle(r, 24, C, R)), ring_x(x0, circle(r, 24, C, R))], 'concrete_dark', caps=False)
    for x, flip in ((x0, True), (x1, False)):
        oc = circle(R, 24, C, R); ic = circle(r, 24, C, R)
        for j in range(24):
            a, b = oc[j], oc[(j + 1) % 24]; c, d = ic[(j + 1) % 24], ic[j]
            pts = [(x, a[1], a[0]), (x, b[1], b[0]), (x, c[1], c[0]), (x, d[1], d[0])]
            if flip: pts = pts[::-1]
            m.quad(pts, [(0, 0), (1, 0), (1, 0.2), (0, 0.2)], 'concrete')
    return m


def pipe_run(length=4):
    """Надземная теплотрасса: две трубы в изоляции на П-образных опорах."""
    m = Mesh()
    for z, r in ((C - 0.28, 0.2), (C + 0.28, 0.16)):
        cylinder(m, 'x', (z, 1.25), r, C - length / 2, C + length / 2, 'galv', n=14)
        for x in np.arange(-length / 2 + 0.25, length / 2, 1.0):
            cylinder(m, 'x', (z, 1.25), r + 0.02, C + x, C + x + 0.06, 'rust', n=14)
    for x in (-length / 2 + 0.5, length / 2 - 0.5):
        for z in (-0.5, 0.5):
            box(m, C + x - 0.06, 0, C + z - 0.06, C + x + 0.06, 1.05, C + z + 0.06, 'concrete')
        box(m, C + x - 0.08, 1.0, C - 0.6, C + x + 0.08, 1.08, C + 0.6, 'rust')
    return m


def sandbags(rows=3, width=1.0, curve=False):
    m = Mesh()
    for row in range(rows):
        n = 3 if row % 2 == 0 else 2
        y = row * 0.22
        for i in range(n):
            x = -width / 2 + (i + (0.5 if n == 3 else 1.0)) * width / 3
            for zz in (-0.18, 0.18):
                rings = [ring_x(C + x + dx, rrect(0.34 * s, 0.22 * s, 0.09 * s, 2, C + zz, y + 0.11)) for dx, s in ((-0.17, 0.55), (-0.14, 0.92), (0.14, 0.95), (0.17, 0.6))]
                m.loft(rings, 'sandbag')
    return m


def jersey():
    """Бетонный блок-ограждение «Нью-Джерси», 2 блока вдоль x."""
    m = Mesh()
    pts = [(C + 0.3, 0.0), (C + 0.3, 0.08), (C + 0.18, 0.3), (C + 0.09, 0.82), (C - 0.09, 0.82), (C - 0.18, 0.3), (C - 0.3, 0.08), (C - 0.3, 0.0)]
    m.loft([ring_x(C - 1.0, pts[::-1]), ring_x(C + 1.0, pts[::-1])], 'concrete', smooth=False)
    return m


def fbs_block():
    m = Mesh(); box(m, C - 1.2, 0, C - 0.3, C + 1.2, 0.58, C + 0.3, 'concrete', r=0.03)
    box(m, C - 0.7, 0.58, C - 0.05, C - 0.6, 0.66, C + 0.05, 'rust'); box(m, C + 0.6, 0.58, C - 0.05, C + 0.7, 0.66, C + 0.05, 'rust')
    return m


def street_lamp():
    """Советский фонарь-«кобра»: бетонная опора с изогнутым кронштейном."""
    m = Mesh()
    cylinder(m, 'y', (C, C), 0.12, 0, 5.6, 'concrete', n=10, r1=0.08)
    # кронштейн: наклонная труба и горизонтальная консоль, головка-«кобра»
    t = Mesh(); cylinder(t, 'y', (0, 0), 0.04, 0, 0.9, 'galv', n=6)
    a = math.radians(-55); Rz = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    t.transform(Rz, (C, 5.3, C)); m.merge(t)
    hx, hy = C + 0.74, 5.82
    cylinder(m, 'x', (C, hy), 0.04, hx - 0.05, hx + 0.35, 'galv', n=6)
    rings = [ring_x(hx + 0.3 + d, rrect(0.26 * s, 0.13 * s, 0.05, 2, C, hy - 0.02)) for d, s in ((0, 0.6), (0.1, 1.0), (0.5, 1.0), (0.65, 0.5))]
    m.loft(rings, 'galv')
    hx = hx + 0.3
    box(m, hx + 0.02, hy - 0.09, C - 0.09, hx + 0.48, hy - 0.07, C + 0.09, 'lamp')
    return m


def power_pole():
    """Деревянная опора ЛЭП с траверсой и стеклянными изоляторами, на бетонном пасынке."""
    m = Mesh()
    box(m, C - 0.12, 0, C - 0.1, C + 0.12, 1.6, C + 0.1, 'concrete')
    cylinder(m, 'y', (C, C + 0.18), 0.13, 0.4, 8.0, 'pole', n=10, r1=0.11)
    box(m, C - 0.07, 7.4, C - 0.9, C + 0.07, 7.55, C + 1.1, 'pole')
    for z in (-0.8, 0.0, 0.95):
        cylinder(m, 'y', (C, C + z + 0.18 - 0.18), 0.015, 7.55, 7.7, 'steel', n=6)
        lathe(m, (C, C + z), [(0.04, 7.7), (0.08, 7.72), (0.06, 7.8), (0.08, 7.82), (0.04, 7.9)], 'insulator', n=10)
    return m


def transformer():
    m = Mesh()
    box(m, C - 0.7, 0, C - 0.5, C + 0.7, 0.2, C + 0.5, 'concrete')
    box(m, C - 0.55, 0.2, C - 0.35, C + 0.55, 1.4, C + 0.35, 'steel', r=0.04)
    for x in np.linspace(-0.45, 0.45, 7):
        box(m, C + x - 0.02, 0.3, C + 0.35, C + x + 0.02, 1.2, C + 0.5, 'steel')   # рёбра радиатора
    for x in (-0.3, 0.0, 0.3):
        lathe(m, (C + x, C - 0.1), [(0.05, 1.4), (0.08, 1.45), (0.06, 1.55), (0.08, 1.6), (0.05, 1.72)], 'insulator', n=10)
    box(m, C - 0.15, 0.7, C - 0.37, C + 0.15, 1.0, C - 0.35, 'rad_yellow')
    return m


def rubble(seed=3):
    rng = np.random.default_rng(seed)
    m = Mesh()
    for _ in range(16):
        x, z = rng.uniform(-0.9, 0.9, 2); s = rng.uniform(0.15, 0.4)
        y = rng.uniform(0, 0.25)
        mat = ['concrete', 'brick', 'rubble', 'concrete_dark'][rng.integers(0, 4)]
        t = Mesh(); box(t, -s, 0, -s * 0.7, s, s * 0.6, s * 0.7, mat)
        a = rng.uniform(0, math.pi); b = rng.uniform(-0.4, 0.4)
        Ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        Rz = np.array([[math.cos(b), -math.sin(b), 0], [math.sin(b), math.cos(b), 0], [0, 0, 1]])
        t.transform(Ry @ Rz, (C + x, y, C + z)); m.merge(t)
    for _ in range(3):  # арматура
        x, z = rng.uniform(-0.7, 0.7, 2)
        t = Mesh(); cylinder(t, 'x', (0, 0), 0.02, -0.6, 0.6, 'rust', n=5)
        a = rng.uniform(0, math.pi); Ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        Rz = np.array([[math.cos(0.5), -math.sin(0.5), 0], [math.sin(0.5), math.cos(0.5), 0], [0, 0, 1]])
        t.transform(Ry @ Rz, (C + x, 0.4, C + z)); m.merge(t)
    return m


def trash_bin():
    m = Mesh()
    rings = [ring_y(0.08, rrect(1.1, 0.75, 0.08, 2, C, C)), ring_y(1.0, rrect(1.25, 0.85, 0.08, 2, C, C))]
    m.loft(rings, 'barrel_green')
    for x in (-0.45, 0.45):
        for z in (-0.28, 0.28):
            cylinder(m, 'z', (C + x, 0.07), 0.07, C + z - 0.03, C + z + 0.03, 'black', n=8)
    box(m, C - 0.65, 1.0, C - 0.45, C + 0.65, 1.05, C + 0.45, 'barrel_green')
    return m


def el_cabinet():
    m = Mesh(); box(m, C - 0.4, 0, C - 0.2, C + 0.4, 1.5, C + 0.2, 'galv', r=0.02, axis='y')
    box(m, C - 0.12, 1.0, C - 0.21, C + 0.12, 1.2, C - 0.2, 'rad_yellow')
    return m


def bench():
    m = Mesh()
    for x in (-0.7, 0.7):
        box(m, C + x - 0.05, 0, C - 0.25, C + x + 0.05, 0.45, C + 0.25, 'concrete')
    for z in (-0.18, -0.02, 0.14):
        box(m, C - 0.95, 0.45, C + z - 0.06, C + 0.95, 0.5, C + z + 0.06, 'wood')
    for y in (0.6, 0.78):
        box(m, C - 0.95, y, C + 0.24, C + 0.95, y + 0.1, C + 0.28, 'wood')
    return m


def stove():
    """Буржуйка с трубой."""
    m = Mesh()
    cylinder(m, 'x', (C, 0.42), 0.3, C - 0.4, C + 0.4, 'steel_dark', n=14, cap_mat='steel_dark')
    for x in (-0.3, 0.3):
        for z in (-0.2, 0.2): box(m, C + x - 0.03, 0, C + z - 0.03, C + x + 0.03, 0.15, C + z + 0.03, 'black')
    cylinder(m, 'y', (C - 0.2, C), 0.07, 0.7, 1.9, 'steel_dark', n=8)
    return m


PROPS = {
    # имя: (функция, название, коллизия (x0,y0,z0,x1,y1,z1 в 1/16), занятые клетки помимо своей, светится)
    'prop_barrel': (lambda: barrel('rust'), 'Ржавая бочка', (2, 0, 2, 14, 14, 14), [], 0),
    'prop_barrel_blue': (lambda: barrel('barrel_blue'), 'Синяя бочка', (2, 0, 2, 14, 14, 14), [], 0),
    'prop_barrel_green': (lambda: barrel('barrel_green'), 'Зелёная бочка', (2, 0, 2, 14, 14, 14), [], 0),
    'prop_barrel_rad': (lambda: barrel('rad_yellow'), 'Бочка с отходами', (2, 0, 2, 14, 14, 14), [], 3),
    'prop_barrel_lying': (lambda: barrel('rust', True), 'Лежащая бочка', (0, 0, 2, 16, 10, 14), [], 0),
    'prop_gas_cylinder': (lambda: gas_cylinder('gas_red'), 'Газовый баллон', (5, 0, 5, 11, 16, 11), [], 0),
    'prop_gas_rack': (gas_rack, 'Газовые баллоны', (1, 0, 1, 15, 16, 15), [], 0),
    'prop_gas_lying': (lambda: gas_cylinder('gas_blue', True), 'Лежащий баллон', (0, 0, 4, 16, 6, 12), [(1, 0, 0)], 0),
    'prop_cable_spool': (cable_spool, 'Кабельная катушка', (0, 0, 0, 16, 16, 16), [], 0),
    'prop_tire': (tire_single, 'Покрышка', (1, 0, 1, 15, 4, 15), [], 0),
    'prop_tire_stack': (lambda: tire_stack(3), 'Стопка покрышек', (1, 0, 1, 15, 12, 15), [], 0),
    'prop_concrete_pipe': (concrete_pipe, 'Бетонная труба', (0, 0, 0, 16, 16, 16), [(1, 0, 0), (0, 1, 0), (1, 1, 0), (0, 0, 1), (1, 0, 1)], 0),
    'prop_pipe_run': (lambda: pipe_run(4), 'Теплотрасса', (0, 0, 0, 16, 16, 16), [(-1, 0, 0), (1, 0, 0), (2, 0, 0), (-1, 1, 0), (1, 1, 0), (2, 1, 0), (0, 1, 0)], 0),
    'prop_sandbags': (lambda: sandbags(3), 'Мешки с песком', (0, 0, 2, 16, 11, 14), [], 0),
    'prop_sandbags_high': (lambda: sandbags(6), 'Стенка из мешков', (0, 0, 2, 16, 16, 14), [(0, 1, 0)], 0),
    'prop_jersey': (jersey, 'Бетонное ограждение', (0, 0, 3, 16, 13, 13), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_fbs': (fbs_block, 'Блок ФБС', (0, 0, 3, 16, 10, 13), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_street_lamp': (street_lamp, 'Уличный фонарь', (5, 0, 5, 11, 16, 11), [(0, 1, 0), (0, 2, 0), (0, 3, 0), (0, 4, 0), (0, 5, 0)], 0),
    'prop_power_pole': (power_pole, 'Опора ЛЭП', (5, 0, 5, 11, 16, 11), [(0, k, 0) for k in range(1, 8)], 0),
    'prop_transformer': (transformer, 'Трансформатор', (0, 0, 0, 16, 16, 16), [(0, 1, 0)], 0),
    'prop_rubble': (rubble, 'Груда обломков', (0, 0, 0, 16, 6, 16), [], 0),
    'prop_trash_bin': (trash_bin, 'Мусорный бак', (0, 0, 0, 16, 16, 16), [], 0),
    'prop_el_cabinet': (el_cabinet, 'Электрошкаф', (2, 0, 4, 14, 16, 12), [], 0),
    'prop_bench': (bench, 'Скамейка', (0, 0, 3, 16, 12, 13), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_stove': (stove, 'Буржуйка', (2, 0, 2, 14, 12, 14), [(0, 1, 0)], 0),
}
