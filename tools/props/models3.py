"""Пропсы и техника, часть 3 (v1.14): ближе к настоящей Зоне.
Техника: УАЗ-452 «буханка», ЗИЛ-131, БРДМ-2, танк Т-72, гусеничный экскаватор, железнодорожная цистерна.
Крупные объекты: водонапорная башня Рожновского, металлическая опора ЛЭП, провода между опорами, надземный
газопровод с П-образным компенсатором над дорогой, арочный ангар (внутрь можно зайти).
Мелочь: качели, карусель, горка, холодильник, ванна, батарея, мусорные мешки, сетка-рабица, дорожный указатель.
Центр основания в (0.5, 0, 0.5), перед смотрит в +x. Подключается из gen_props.py после models2."""
import math
import numpy as np
import models
from mesh import Mesh, box, cylinder, lathe, wheel, rrect, circle, ring_x, ring_y, ring_z
from models2 import rot_x, rot_y, rot_z, part

C = 0.5


# =========================================================================== вспомогательное
def _frame(d):
    d = np.asarray(d, float); d = d / (np.linalg.norm(d) + 1e-12)
    up = np.array([0, 1.0, 0]) if abs(d[1]) < 0.95 else np.array([1.0, 0, 0])
    u = np.cross(d, up); u /= np.linalg.norm(u) + 1e-12
    v = np.cross(u, d)
    return u, v


def tube(m, pts, r, mat, n=6):
    """Труба/провод по ломаной: кольца перпендикулярно направлению (обход выравнивает сам лофт)."""
    P = [np.asarray(p, float) for p in pts]
    rings = []
    for i, p in enumerate(P):
        d = (P[min(i + 1, len(P) - 1)] - P[max(i - 1, 0)])
        u, v = _frame(d)
        rings.append([p + r * (math.cos(2 * math.pi * k / n) * u + math.sin(2 * math.pi * k / n) * v) for k in range(n)])
    # кольца должны быть согласованы между собой: берём рамку первого и переносим вдоль (без скручивания)
    for i in range(1, len(rings)):
        a = np.asarray(rings[i - 1]); b = np.asarray(rings[i])
        best, bs = 0, 1e9
        for s in range(n):
            dd = np.linalg.norm(a - np.roll(b, -s, axis=0))
            if dd < bs: bs, best = dd, s
        rings[i] = list(np.roll(b, -best, axis=0))
    m.loft(rings, mat, caps=False)
    return m


def beam(m, p0, p1, w, mat):
    """Брус (уголок фермы) между двумя точками."""
    return tube(m, [p0, p1], w, mat, n=4)


def wire(m, p0, p1, sag, mat='black', r=0.022, k=14):
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    pts = []
    for i in range(k + 1):
        t = i / k
        q = p0 + (p1 - p0) * t
        q[1] -= sag * 4 * t * (1 - t)
        pts.append(q)
    return tube(m, pts, r, mat, n=4)


def arc_pts(c, r, a0, a1, n, plane='xy'):
    out = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        if plane == 'xy': out.append((c[0] + r * math.cos(a), c[1] + r * math.sin(a), c[2]))
        else: out.append((c[0], c[1] + r * math.sin(a), c[2] + r * math.cos(a)))
    return out


def ground(m):
    a = np.array(m.v); m.transform(None, (0, -a[:, 1].min(), 0)); return m


def tracks(m, x0, x1, z_half, width, y0=0.0, h=0.9, wheels_n=6, R=0.36):
    """Гусеницы: стадион из траков и опорные катки (с двух сторон)."""
    L = x1 - x0
    for s in (-1, 1):
        zc = C + s * z_half
        sec = rrect(L, h, h / 2 - 0.02, 4, C + (x0 + x1) / 2, y0 + h / 2)
        m.loft([ring_z(zc - width / 2, sec), ring_z(zc + width / 2, sec)], 'track', smooth=True)
        for i in range(wheels_n):
            x = x0 + h / 2 + (L - h) * i / max(wheels_n - 1, 1)
            cylinder(m, 'z', (C + x, y0 + h / 2 - 0.02), R, zc + s * width / 2 - 0.02 * s - (0.04 if s > 0 else -0.04),
                     zc + s * width / 2 + 0.03 * s, 'steel_dark', n=12)


# =========================================================================== техника
def uaz452(damage):
    """УАЗ-452 «буханка»: округлый вагонный кузов, окна полосой, круглые фары."""
    m = Mesh()
    hl = 2.18
    models.body_loft(m, [(-hl, 0.48, 1.98, 0.95, 0.32), (-hl + 0.18, 0.44, 2.06, 0.97, 0.42), (1.55, 0.44, 2.06, 0.97, 0.42),
                         (1.95, 0.47, 1.92, 0.95, 0.46), (hl, 0.58, 1.38, 0.9, 0.3)], segs=4)
    gl = 'glass' if damage == 0 else 'interior'
    for s in (-1, 1):
        for x0, x1 in ((-1.9, -0.95), (-0.85, 0.1), (0.25, 1.05), (1.2, 1.7)):
            box(m, C + x0, 1.22, C + s * 0.975 - 0.012, C + x1, 1.66, C + s * 0.975 + 0.012, gl)
    box(m, C + 1.98, 1.32, C - 0.72, C + 2.1, 1.74, C + 0.72, gl, r=0.04)          # лобовое
    box(m, C - hl - 0.01, 1.3, C - 0.6, C - hl + 0.05, 1.7, C + 0.6, gl)          # заднее
    box(m, C + hl - 0.02, 0.62, C - 0.38, C + hl + 0.03, 0.95, C + 0.38, 'grille')
    models.lights_front(m, hl + 0.01, 0.92, 0.62, 0.11)
    box(m, C + hl - 0.02, 0.45, C - 0.95, C + hl + 0.1, 0.56, C + 0.95, 'black', r=0.03)
    box(m, C - hl - 0.1, 0.45, C - 0.95, C - hl + 0.02, 0.56, C + 0.95, 'black', r=0.03)
    box(m, C - hl - 0.06, 0.6, C + 0.3, C - hl - 0.02, 1.95, C + 0.4, 'rust')     # лесенка
    models.wheels(m, [-hl + 0.8, hl - 0.92], 0.82, 0.38, 0.24, damage=damage)
    models.plate(m, hl + 0.1, 0.5)
    return m


def zil131(damage):
    """ЗИЛ-131: капотный армейский грузовик, три моста, кузов с тентом."""
    m = Mesh()
    gl = 'glass' if damage == 0 else 'interior'
    # капот и крылья
    models.body_loft(m, [(1.95, 0.95, 1.75, 0.62, 0.16), (3.3, 0.95, 1.7, 0.6, 0.2), (3.45, 1.0, 1.55, 0.56, 0.12)])
    for s in (-1, 1):
        rings = [ring_x(C + x, rrect(0.5, 0.36, 0.14, 3, C + s * 0.92, 1.12)) for x in (1.9, 3.35)]
        m.loft(rings, 'paint')
    # кабина
    box(m, C + 0.6, 0.95, C - 1.15, C + 1.95, 2.35, C + 1.15, 'paint', r=0.14, axis='x')
    box(m, C + 1.96, 1.75, C - 1.02, C + 1.99, 2.2, C - 0.08, gl)
    box(m, C + 1.96, 1.75, C + 0.08, C + 1.99, 2.2, C + 1.02, gl)
    for s in (-1, 1):
        box(m, C + 1.05, 1.75, C + s * 1.155 - 0.01, C + 1.8, 2.15, C + s * 1.155 + 0.01, gl)
    box(m, C + 3.45, 1.05, C - 0.45, C + 3.48, 1.5, C + 0.45, 'grille')
    models.lights_front(m, 3.42, 1.32, 0.88, 0.11)
    box(m, C + 3.45, 0.72, C - 1.15, C + 3.62, 0.9, C + 1.15, 'black')              # бампер
    # рама и кузов
    box(m, C - 3.45, 0.72, C - 0.55, C + 3.45, 0.92, C + 0.55, 'black')
    box(m, C - 3.4, 1.05, C - 1.22, C + 0.5, 1.2, C + 1.22, 'wood')
    for s in (-1, 1):
        box(m, C - 3.4, 1.2, C + s * 1.2 - 0.04, C + 0.5, 1.72, C + s * 1.2 + 0.04, 'wood')
    box(m, C - 3.44, 1.2, C - 1.22, C - 3.36, 1.72, C + 1.22, 'wood')
    if damage == 0:
        rings = [ring_x(C + x, rrect(2.42, 1.35, 0.45, 4, C, 1.72 + 0.62)) for x in (-3.42, 0.48)]
        m.loft(rings, 'tarp_army')
    else:
        for x in (-3.0, -1.8, -0.6):   # дуги без тента
            pts = arc_pts((C + x, 1.72, C), 1.18, 0, 180, 10, plane='zy')
            tube(m, pts, 0.03, 'rust', n=4)
    models.wheels(m, [-2.75, -1.45, 2.6], 1.0, 0.54, 0.38, damage=damage)
    return m


def brdm2(damage):
    """БРДМ-2: корпус-«лодка», башня с КПВТ, четыре колеса."""
    m = Mesh()

    def sec(x, yb, yt, wb, wt, wm):
        pts = [(C + wb, yb), (C + wm, (yb + yt) * 0.5), (C + wt, yt), (C - wt, yt), (C - wm, (yb + yt) * 0.5), (C - wb, yb)]
        return ring_x(C + x, pts)
    rings = [sec(-2.85, 0.6, 1.6, 0.95, 0.95, 1.15), sec(-2.35, 0.45, 1.85, 1.0, 1.0, 1.17), sec(1.0, 0.45, 1.85, 1.0, 1.0, 1.17),
             sec(2.0, 0.6, 1.78, 0.95, 0.85, 1.1), sec(2.87, 1.0, 1.4, 0.72, 0.6, 0.82)]
    m.loft(rings, 'paint', smooth=False)
    cylinder(m, 'y', (C - 0.3, C), 0.5, 1.85, 2.05, 'paint', n=14)
    lathe(m, (C - 0.3, C), [(0.5, 2.05), (0.46, 2.2), (0.32, 2.32), (0.05, 2.34)], 'paint', n=14)
    cylinder(m, 'x', (C, 2.13), 0.06, C - 0.1, C + 2.0, 'steel_dark', n=8)
    for s in (-1, 1):
        box(m, C + 1.7, 1.6, C + s * 0.45 - 0.2, C + 2.0, 1.75, C + s * 0.45 + 0.2, 'glass' if damage == 0 else 'black')
    box(m, C - 2.0, 1.85, C - 0.5, C - 1.0, 1.9, C + 0.5, 'steel_dark')            # люки
    models.wheels(m, [-1.55, 1.45], 1.02, 0.55, 0.36, y=0.52, damage=damage)
    return m


def t72(damage):
    """Танк Т-72: корпус с наклонным лобовым листом, гусеницы с катками, литая башня, пушка 125 мм."""
    m = Mesh()
    box(m, C - 3.35, 0.5, C - 1.15, C + 2.6, 1.3, C + 1.15, 'paint')
    rings = [ring_x(C + 2.55, rrect(2.3, 0.8, 0.05, 1, C, 0.9)), ring_x(C + 3.45, rrect(2.3, 0.35, 0.05, 1, C, 0.68))]
    m.loft(rings, 'paint')
    for s in (-1, 1):   # надгусеничные полки и бортовые экраны
        box(m, C - 3.4, 1.28, C + s * 1.45 - 0.35, C + 3.2, 1.34, C + s * 1.45 + 0.35, 'paint')
        box(m, C - 1.0, 0.85, C + s * 1.8 - 0.02, C + 2.9, 1.3, C + s * 1.8 + 0.02, 'paint')
    tracks(m, -3.35, 3.2, 1.45, 0.7, y0=0.0, h=0.95, wheels_n=6, R=0.36)
    for s in (-1, 1):   # бочки на корме
        cylinder(m, 'z', (C - 3.55, 1.0), 0.28, C + s * 0.3 - 0.4 if s < 0 else C + 0.1, C + s * 0.3 + 0.4 if s > 0 else C - 0.1, 'rust', n=12)
    tur = Mesh()
    lathe(tur, (C - 0.4, C), [(1.18, 1.3), (1.24, 1.48), (1.18, 1.72), (0.92, 1.92), (0.5, 2.03), (0.05, 2.05)], 'paint', n=20)
    cylinder(tur, 'y', (C - 0.9, C + 0.45), 0.26, 1.95, 2.15, 'paint', n=10)       # командирская башенка
    cylinder(tur, 'x', (C, 1.7), 0.095, C + 0.6, C + 5.0, 'paint', n=10)            # пушка
    cylinder(tur, 'x', (C, 1.7), 0.14, C + 2.4, C + 3.0, 'paint', n=10)             # эжектор
    cylinder(tur, 'x', (C, 1.7), 0.18, C + 0.55, C + 0.9, 'paint', n=10)            # маска
    box(tur, C - 0.95, 2.15, C + 0.4, C - 0.65, 2.25, C + 0.6, 'steel_dark')
    for s in (-1, 1):  # блоки динамической защиты
        for k in range(3):
            bx = part(box, -0.12, 0, -0.18, 0.12, 0.22, 0.18, 'paint2')
            rot_y(bx, s * (28 + 16 * k), (0, 0, 0)); bx.transform(None, (C - 0.4 + 1.1 * math.cos(math.radians(s * (28 + 16 * k))), 1.75,
                                                                       C + 1.1 * math.sin(math.radians(s * (28 + 16 * k)))))
            tur.merge(bx)
    if damage:
        rot_z(tur, 18, (C - 0.4, 1.3, C)); rot_y(tur, 35, (C - 0.4, 0, C)); tur.transform(None, (-0.7, 0.25, 0.5))
    m.merge(tur)
    return m


def excavator(damage):
    """Гусеничный экскаватор: поворотная платформа, кабина, стрела с ковшом, опущенным на землю."""
    m = Mesh()
    tracks(m, -1.8, 1.8, 1.05, 0.6, y0=0.0, h=0.75, wheels_n=5, R=0.28)
    box(m, C - 0.7, 0.75, C - 0.7, C + 0.7, 0.95, C + 0.7, 'black')
    box(m, C - 2.0, 0.95, C - 1.25, C + 1.0, 1.95, C + 1.25, 'paint', r=0.08)           # платформа и капот
    box(m, C + 0.1, 1.95, C + 0.05, C + 1.3, 3.25, C + 1.2, 'paint', r=0.05)           # кабина
    gl = 'glass' if damage == 0 else 'interior'
    box(m, C + 1.3, 2.25, C + 0.15, C + 1.32, 3.1, C + 1.1, gl)
    box(m, C + 0.3, 2.25, C + 1.2, C + 1.2, 3.1, C + 1.22, gl)
    cylinder(m, 'y', (C - 1.4, C - 0.8), 0.07, 1.95, 2.6, 'black', n=6)                # выхлоп
    box(m, C - 2.05, 1.0, C - 1.2, C - 1.95, 1.9, C + 1.2, 'steel_dark')               # противовес
    # стрела: подъём от платформы, рукоять вниз, ковш
    p0 = np.array([C + 1.0, 1.6, C - 0.45]); p1 = np.array([C + 3.4, 3.6, C - 0.45]); p2 = np.array([C + 5.1, 0.7, C - 0.45])
    for a, b in ((p0, p1), (p1, p2)):
        beam(m, a + [0, 0, -0.12], b + [0, 0, -0.12], 0.13, 'paint')
        beam(m, a + [0, 0, 0.12], b + [0, 0, 0.12], 0.13, 'paint')
    beam(m, np.array([C + 1.1, 1.3, C - 0.45]), (p0 + p1) / 2 - [0, 0.15, 0], 0.07, 'chrome')   # гидроцилиндр
    bk = Mesh()
    box(bk, -0.45, 0, -0.5, 0.45, 0.6, 0.5, 'rust')
    for z in np.linspace(-0.4, 0.4, 5):
        box(bk, 0.45, 0, z - 0.05, 0.62, 0.08, z + 0.05, 'steel_dark')
    bk.transform(None, (p2[0], 0.0, p2[2])); m.merge(bk)
    return m


def tank_wagon(damage):
    """Железнодорожная цистерна: котёл с днищами, колпак, лестницы, тележки."""
    m = Mesh()
    L, y0 = 11.0, 1.1
    yc, R = y0 + 1.45, 1.35
    xs = [-L / 2 - 0.35, -L / 2 - 0.2, -L / 2, L / 2, L / 2 + 0.2, L / 2 + 0.35]
    rs = [0.35, R * 0.8, R, R, R * 0.8, 0.35]
    m.loft([ring_x(C + x, circle(r, 20, C, yc)) for x, r in zip(xs, rs)], 'paint')
    for x in (-L / 2 + 0.6, L / 2 - 0.6):
        cylinder(m, 'x', (C, yc), R + 0.03, C + x - 0.06, C + x + 0.06, 'black', n=20)    # бандажи
    cylinder(m, 'y', (C, C), 0.45, yc + R - 0.1, yc + R + 0.35, 'paint', n=14)          # колпак
    box(m, C - 0.9, yc + R + 0.05, C - 0.5, C + 0.9, yc + R + 0.1, C + 0.5, 'steel')
    box(m, C - L / 2 - 0.4, y0 - 0.25, C - 1.0, C + L / 2 + 0.4, y0 + 0.05, C + 1.0, 'black')
    for s in (-1, 1):
        for k in range(6):
            box(m, C + s * 0.1 - 0.02, y0 + 0.2 + k * 0.38, C + 1.38 - 0.02, C + s * 0.1 + 0.02, y0 + 0.24 + k * 0.38, C + 1.38 + 0.25, 'rust')
    for x in (-L / 2 - 0.55, L / 2 + 0.25):
        for s in (-1, 1):
            cylinder(m, 'x', (C + s * 0.9, y0 - 0.1), 0.17, C + x, C + x + 0.3, 'steel_dark', n=10, cap_mat='steel')
    if damage == 0:
        for bx in (-L / 2 + 1.6, L / 2 - 1.6):
            box(m, C + bx - 1.2, 0.35, C - 1.0, C + bx + 1.2, 0.85, C + 1.0, 'rust')
            for wx in (-0.75, 0.75):
                for s in (-1, 1):
                    cylinder(m, 'z', (C + bx + wx, 0.45), 0.45, C + s * 0.72 - 0.08, C + s * 0.72 + 0.08, 'rim', n=14)
    else:
        box(m, C - L / 2 + 0.5, 0, C - 1.0, C + L / 2 - 0.5, y0 - 0.25, C + 1.0, 'rust')
        rot_x(m, -7, (C, 0, C)); ground(m)
    return m


VEH3 = {
    'uaz452': ('УАЗ-452 «буханка»', 'olive', 'cream', 4.4, 2.0, 2.1, uaz452),
    'zil131': ('ЗИЛ-131', 'olive', 'olive', 7.2, 2.5, 3.0, zil131),
    'brdm2': ('БРДМ-2', 'olive', 'olive', 5.8, 2.4, 2.4, brdm2),
    't72': ('Танк Т-72', 'olive', 'green', 7.0, 3.6, 2.3, t72),
    'excavator': ('Экскаватор', 'orange', 'white', 7.4, 2.6, 3.7, excavator),
    'tank_wagon': ('Цистерна', 'green', 'white', 12.0, 2.9, 4.1, tank_wagon),
}

_car = models.car


def car(kind, damage=0):
    if kind in VEH3:
        return VEH3[kind][6](damage)
    return _car(kind, damage)


models.car = car
for k, v in VEH3.items():
    models.VEHICLES[k] = v[:6]


# =========================================================================== крупные объекты
def water_tower():
    """Водонапорная башня Рожновского: кирпичный ствол, стальной бак с конической крышей, лестница."""
    m = Mesh()
    lathe(m, (C, C), [(1.25, 0.0), (1.2, 0.3), (1.05, 6.6), (1.1, 6.9)], 'brick_red', n=20)
    cylinder(m, 'y', (C, C), 1.6, 6.9, 9.3, 'rust', n=24)
    lathe(m, (C, C), [(1.66, 9.3), (1.7, 9.35), (0.12, 10.3), (0.02, 10.35)], 'rust', n=24)
    for y in (7.4, 8.2, 9.0):
        cylinder(m, 'y', (C, C), 1.63, y, y + 0.06, 'steel_dark', n=24)            # пояса бака
    box(m, C + 1.08, 0.0, C - 0.4, C + 1.2, 1.9, C + 0.4, 'wood')                       # дверь
    for z in (-0.18, 0.18):
        cylinder(m, 'y', (C + 1.72, C + z), 0.025, 6.9, 10.0, 'rust', n=4)
        cylinder(m, 'y', (C + 1.13, C + z), 0.025, 2.0, 6.9, 'rust', n=4)
    for y in np.arange(2.2, 6.8, 0.35):
        cylinder(m, 'z', (C + 1.14, y), 0.015, C - 0.18, C + 0.18, 'rust', n=4)
    for y in np.arange(7.1, 9.9, 0.35):
        cylinder(m, 'z', (C + 1.73, y), 0.015, C - 0.18, C + 0.18, 'rust', n=4)
    return m


def _water_tower_cells():
    cells = []
    for dy in range(0, 7):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cells.append((dx, dy, dz))
        if dy: cells.append((0, dy, 0))
    for dy in range(7, 11):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dy == 10 and (dx or dz): continue
                cells.append((dx, dy, dz))
    return cells


# опора ЛЭП: точки подвеса проводов (z, y) и тросостойка
PYLON_PTS = [(-4.0, 13.4), (4.0, 13.4), (-3.2, 10.4), (3.2, 10.4), (0.0, 17.6)]


def lep_pylon():
    """Металлическая опора ЛЭП: четыре сходящиеся стойки, решётка раскосов, две траверсы, гирлянды изоляторов."""
    m = Mesh()
    H = 15.0
    base, top = 1.6, 0.4

    def leg(sx, sz, y):
        w = base + (top - base) * min(y, H) / H
        return np.array([C + sx * w, y, C + sz * w])
    corners = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
    for sx, sz in corners:
        beam(m, leg(sx, sz, 0), leg(sx, sz, H), 0.07, 'galv')
        box(m, C + sx * base - 0.35, 0, C + sz * base - 0.35, C + sx * base + 0.35, 0.3, C + sz * base + 0.35, 'concrete')
    levels = [0.3, 3.0, 5.8, 8.4, 10.4, 12.4, 13.4, 15.0]
    for i, y in enumerate(levels):
        for k in range(4):
            a, b = corners[k], corners[(k + 1) % 4]
            beam(m, leg(a[0], a[1], y), leg(b[0], b[1], y), 0.04, 'galv')
            if i + 1 < len(levels):
                y2 = levels[i + 1]
                beam(m, leg(a[0], a[1], y), leg(b[0], b[1], y2), 0.03, 'galv')
                beam(m, leg(b[0], b[1], y), leg(a[0], a[1], y2), 0.03, 'galv')
    # траверсы вдоль z (провода идут вдоль x)
    for y, half in ((13.4, 4.0), (10.4, 3.2)):
        for sx in (-1, 1):
            beam(m, np.array([C + sx * 0.4, y + 0.05, C - half]), np.array([C + sx * 0.4, y + 0.05, C + half]), 0.05, 'galv')
            beam(m, np.array([C + sx * 0.4, y + 0.9, C]), np.array([C + sx * 0.25, y + 0.05, C - half]), 0.035, 'galv')
            beam(m, np.array([C + sx * 0.4, y + 0.9, C]), np.array([C + sx * 0.25, y + 0.05, C + half]), 0.035, 'galv')
    beam(m, np.array([C, H, C]), np.array([C, 17.8, C]), 0.06, 'galv')                    # тросостойка
    for z, y in PYLON_PTS[:4]:
        for k in range(6):
            lathe(m, (C, C + z), [(0.03, y - 0.15 * k - 0.15), (0.1, y - 0.15 * k - 0.1), (0.03, y - 0.15 * k)], 'insulator', n=10)
    return m


def lep_wires(dy):
    """Провода пролёта 32 блока между опорами ЛЭП (dy — разница высот земли у следующей опоры)."""
    m = Mesh()
    for z, y in PYLON_PTS:
        yy = y - (0.95 if z else 0.0)
        wire(m, (C - 1, yy, C + z), (C + 31, yy + dy, C + z), 1.6 if z else 1.1, 'black', r=0.03)
    return m


POLE_PTS = [(-0.8, 7.86), (0.0, 7.86), (0.95, 7.86)]   # изоляторы деревянной опоры (models.power_pole)


def pole_wires(dy):
    """Провода пролёта 16 блоков между деревянными опорами."""
    m = Mesh()
    for z, y in POLE_PTS:
        wire(m, (C - 1, y, C + z), (C + 15, y + dy, C + z), 0.55, 'black', r=0.018)
    return m


def gas_pipe():
    """Надземный газопровод: жёлтая труба на П-образных опорах, участок 4 блока."""
    m = Mesh()
    cylinder(m, 'x', (C, 1.3), 0.26, C - 0.5, C + 3.5, 'gas_yellow', n=14)
    cylinder(m, 'x', (C, 1.3), 0.29, C + 1.45, C + 1.55, 'gas_yellow', n=14)           # стык
    for x in (0.0, 2.0):
        for s in (-1, 1):
            box(m, C + x - 0.05, 0, C + s * 0.32 - 0.05, C + x + 0.05, 1.05, C + s * 0.32 + 0.05, 'rust')
        box(m, C + x - 0.07, 1.0, C - 0.4, C + x + 0.07, 1.06, C + 0.4, 'rust')
        box(m, C + x - 0.15, 0, C - 0.5, C + x + 0.15, 0.12, C + 0.5, 'concrete')
    return m


def gas_loop():
    """П-образный компенсатор газопровода над дорогой: подъём, пролёт на высоте 5, спуск (9 блоков)."""
    m = Mesh()
    r, y0, y1, xa, xb = 0.26, 1.3, 5.6, 1.5, 7.5
    pts = [(C - 0.5, y0, C)]
    pts += arc_pts((C + xa - 0.6, y0 + 0.6, C), 0.6, -90, 0, 5)
    pts += arc_pts((C + xa + 0.6, y1 - 0.6, C), 0.6, 180, 90, 5)
    pts += arc_pts((C + xb - 0.6, y1 - 0.6, C), 0.6, 90, 0, 5)
    pts += arc_pts((C + xb + 0.6, y0 + 0.6, C), 0.6, 180, 270, 5)
    pts += [(C + 8.5, y0, C)]
    # убрать совпадающие точки
    clean = [pts[0]]
    for p in pts[1:]:
        if np.linalg.norm(np.asarray(p) - np.asarray(clean[-1])) > 1e-3: clean.append(p)
    tube(m, clean, r, 'gas_yellow', n=14)
    for x in (0.0, 8.0):
        for s in (-1, 1):
            box(m, C + x - 0.05, 0, C + s * 0.32 - 0.05, C + x + 0.05, 1.05, C + s * 0.32 + 0.05, 'rust')
        box(m, C + x - 0.07, 1.0, C - 0.4, C + x + 0.07, 1.06, C + 0.4, 'rust')
    for x in (xa, xb):   # опоры-стойки у колен
        box(m, C + x - 0.1, 0, C - 0.1, C + x + 0.1, y1 - 0.3, C + 0.1, 'rust')
        box(m, C + x - 0.25, 0, C - 0.25, C + x + 0.25, 0.15, C + 0.25, 'concrete')
    return m


HANGAR_R, HANGAR_HALF = 5.0, 7


def hangar():
    """Арочный ангар из профлиста: внутрь можно зайти через приоткрытые ворота (перед на +x)."""
    m = Mesh()
    R, t, hx = HANGAR_R, 0.14, HANGAR_HALF
    outer = [(C + R * math.cos(math.radians(a)), R * math.sin(math.radians(a))) for a in np.linspace(0, 180, 25)]
    inner = [(C + (R - t) * math.cos(math.radians(a)), (R - t) * math.sin(math.radians(a))) for a in np.linspace(180, 0, 25)]
    sec = outer + inner
    m.loft([ring_x(C - hx, sec), ring_x(C + hx, sec)], 'corrugated', caps=False)
    for x in np.arange(-hx + 0.5, hx, 2.0):       # рёбра каркаса изнутри
        rib = [(C + (R - t - 0.12) * math.cos(math.radians(a)), (R - t - 0.12) * math.sin(math.radians(a))) for a in np.linspace(0, 180, 19)]
        rib += [(C + (R - t) * math.cos(math.radians(a)), (R - t) * math.sin(math.radians(a))) for a in np.linspace(180, 0, 19)]
        m.loft([ring_x(C + x - 0.06, rib), ring_x(C + x + 0.06, rib)], 'rust', caps=False)
    # торцы: вертикальные полосы профлиста (бруски видны с обеих сторон)
    zs = np.arange(-R, R, 0.5)
    for xw, door in ((-hx, False), (hx - 0.1, True)):
        for z in zs:
            zc = z + 0.25
            h = math.sqrt(max(R * R - max(abs(z), abs(z + 0.5)) ** 2, 0)) if abs(zc) < R else 0
            if h <= 0.05: continue
            y0 = 4.0 if door and abs(zc) < 2.5 else 0.0
            if h - y0 > 0.05:
                box(m, C + xw, y0, C + z, C + xw + 0.1, h, C + z + 0.5, 'corrugated')
    # ворота: левая створка закрыта, правая распахнута наружу
    box(m, C + hx - 0.05, 0.0, C - 2.5, C + hx + 0.05, 4.0, C - 0.02, 'rust')
    lf = part(box, 0, 0, 0, 0.1, 4.0, 2.48, 'rust'); rot_y(lf, -70, (0, 0, 0)); lf.transform(None, (C + hx, 0, C + 2.5)); m.merge(lf)
    box(m, C + hx - 0.12, 4.0, C - 2.6, C + hx + 0.12, 4.15, C + 2.6, 'rust')          # балка над воротами
    lathe(m, (C, C), [(0.25, R + 0.02), (0.18, R + 0.25), (0.02, R + 0.3)], 'galv', n=10)   # дефлектор
    return m


def _hangar_cells():
    R, hx = HANGAR_R, HANGAR_HALF
    cells = []
    for dx in range(-hx, hx):
        for dz in range(-6, 6):
            for dy in range(0, 6):
                dist = math.hypot(dz, dy + 0.5)
                wall = dx == -hx and dist < R - 0.3
                gate = dx == hx - 1 and dist < R - 0.3 and not (abs(dz) <= 2 and dy <= 3)
                shell = R - 1.0 < dist < R + 0.2
                if (shell or wall or gate) and (dx, dy, dz) != (0, 0, 0):
                    cells.append((dx, dy, dz))
    return cells


# =========================================================================== двор, Припять, свалка
def swing():
    """Качели: А-образные стойки, перекладина, сиденье на цепях."""
    m = Mesh()
    for s in (-1, 1):
        for d in (-1, 1):
            beam(m, np.array([C + d * 0.7, 0, C + s * 1.2]), np.array([C, 2.45, C + s * 1.2]), 0.05, 'play_blue')
    cylinder(m, 'z', (C, 2.45), 0.05, C - 1.3, C + 1.3, 'play_blue', n=8)
    for s in (-1, 1):
        cylinder(m, 'y', (C, C + s * 0.3), 0.012, 0.55, 2.45, 'steel', n=4)
    box(m, C - 0.18, 0.5, C - 0.38, C + 0.18, 0.56, C + 0.38, 'wood')
    return m


def carousel():
    """Карусель: круглый настил с поручнями на оси."""
    m = Mesh()
    cylinder(m, 'y', (C, C), 0.1, 0, 0.35, 'steel_dark', n=8)
    cylinder(m, 'y', (C, C), 1.05, 0.3, 0.38, 'play_red', n=24)
    cylinder(m, 'y', (C, C), 0.06, 0.38, 1.05, 'play_yellow', n=8)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        p = np.array([C + 0.9 * math.cos(a), 0.38, C + 0.9 * math.sin(a)])
        beam(m, p, p + [0, 0.55, 0], 0.025, 'play_yellow')
        beam(m, p + [0, 0.55, 0], np.array([C, 0.95, C]), 0.025, 'play_yellow')
    return m


def slide():
    """Детская горка: лесенка, площадка, жёлоб вниз по +x."""
    m = Mesh()
    for s in (-1, 1):
        beam(m, np.array([C - 1.2, 0, C + s * 0.35]), np.array([C - 0.5, 1.5, C + s * 0.35]), 0.04, 'play_blue')
        cylinder(m, 'y', (C - 0.1, C + s * 0.35), 0.04, 0, 2.3, 'play_blue', n=6)
    for k in range(5):
        t = (k + 1) / 6
        cylinder(m, 'z', (C - 1.2 + 0.7 * t, 1.5 * t), 0.02, C - 0.35, C + 0.35, 'steel', n=4)
    box(m, C - 0.5, 1.5, C - 0.38, C - 0.05, 1.56, C + 0.38, 'wood')
    for s in (-1, 1):
        beam(m, np.array([C - 0.5, 2.2, C + s * 0.35]), np.array([C - 0.1, 2.2, C + s * 0.35]), 0.025, 'play_blue')
    ch = Mesh()
    box(ch, 0, -0.03, -0.32, 2.6, 0.03, 0.32, 'play_yellow')
    for s in (-1, 1): box(ch, 0, 0.03, s * 0.32 - 0.03, 2.6, 0.16, s * 0.32 + 0.03, 'play_yellow')
    rot_z(ch, -33, (0, 0, 0)); ch.transform(None, (C - 0.05, 1.53, C)); m.merge(ch)
    return m


def fridge(lying=False):
    """Холодильник «ЗИЛ»: скруглённый эмалированный шкаф с хромированной ручкой."""
    m = Mesh()
    box(m, C - 0.32, 0.05, C - 0.33, C + 0.32, 1.55, C + 0.33, 'enamel', r=0.12, axis='z')
    box(m, C + 0.32, 0.3, C - 0.3, C + 0.335, 1.5, C + 0.3, 'enamel')
    box(m, C + 0.335, 0.85, C + 0.18, C + 0.38, 1.25, C + 0.24, 'chrome', r=0.015)
    cylinder(m, 'y', (C + 0.34, C - 0.05), 0.05, 1.42, 1.48, 'chrome', n=8)
    box(m, C - 0.28, 0, C - 0.28, C + 0.28, 0.06, C + 0.28, 'black')
    if lying:
        rot_z(m, -90, (C, 0, C)); rot_y(m, 20); ground(m)
    return m


def bathtub():
    """Чугунная ванна на лапах, брошенная на улице."""
    m = Mesh()
    secs = [(-0.8, 0.86), (-0.75, 0.95), (0.75, 0.95), (0.8, 0.86)]
    m.loft([ring_x(C + x, rrect(0.75 * s, 0.5 * s, 0.18, 3, C, 0.45)) for x, s in secs], 'enamel')
    box(m, C - 0.66, 0.66, C - 0.28, C + 0.66, 0.71, C + 0.28, 'cast_iron', r=0.1, axis='y')
    for x in (-0.6, 0.6):
        for s in (-1, 1):
            cylinder(m, 'y', (C + x, C + s * 0.28), 0.05, 0, 0.22, 'cast_iron', n=6, r1=0.07)
    return m


def radiator():
    """Чугунная батарея МС-140, прислонённая к стене."""
    m = Mesh()
    for k in range(9):
        z = -0.48 + 0.12 * k
        box(m, C - 0.05, 0.08, C + z - 0.045, C + 0.05, 0.6, C + z + 0.045, 'cast_iron', r=0.03, axis='z')
    for y in (0.14, 0.54):
        cylinder(m, 'z', (C, y), 0.03, C - 0.52, C + 0.52, 'cast_iron', n=6)
    rot_z(m, 12, (C, 0, C)); ground(m)
    return m


def trash_bags():
    """Куча мусорных мешков."""
    m = Mesh()
    rng = np.random.default_rng(5)
    for k in range(6):
        x, z = rng.uniform(-0.45, 0.45), rng.uniform(-0.45, 0.45)
        r = rng.uniform(0.2, 0.3); y = 0 if k < 4 else 0.25
        lathe(m, (C + x, C + z), [(0.05, y), (r * 0.9, y + 0.05), (r, y + r * 0.6), (r * 0.7, y + r * 1.2), (0.06, y + r * 1.45), (0.04, y + r * 1.6)], 'trash_bag', n=10)
    return m


def chainlink():
    """Секция забора из сетки-рабицы на ржавых трубах, 3 блока вдоль z."""
    m = Mesh()
    for z in (-1.5, 0.0, 1.5):
        cylinder(m, 'y', (C, C + z), 0.045, 0, 2.05, 'rust', n=6)
    for y in (0.1, 1.95):
        cylinder(m, 'z', (C, y), 0.025, C - 1.5, C + 1.5, 'rust', n=4)
    box(m, C - 0.01, 0.1, C - 1.5, C + 0.01, 1.95, C + 1.5, 'chainlink')
    for s in (-1, 1):   # колючка поверху
        beam(m, np.array([C, 2.05, C + s * 1.5]), np.array([C + 0.25, 2.3, C + s * 1.5]), 0.015, 'rust')
    for y in (2.15, 2.25):
        cylinder(m, 'z', (C + 0.12 + (y - 2.15), y), 0.008, C - 1.5, C + 1.5, 'wire', n=4)
    return m


def panel(m, P, UV, mat, outward):
    """Плоская грань с заданными uv; обход разворачивается так, чтобы лицо смотрело в outward."""
    P = [np.asarray(p, float) for p in P]
    g = np.cross(P[1] - P[0], P[2] - P[0])
    if np.dot(g, outward) < 0:
        P, UV = P[::-1], UV[::-1]
    m.quad(P, UV, mat)


def road_sign():
    """Синий дорожный указатель на двух стойках (надпись на лицевой стороне, +x)."""
    m = Mesh()
    for z in (-0.7, 0.7):
        cylinder(m, 'y', (C - 0.05, C + z), 0.04, 0, 2.6, 'galv', n=6)
    x, y0, y1 = C + 0.02, 1.6, 2.5
    panel(m, [(x, y0, C + 1.0), (x, y0, C - 1.0), (x, y1, C - 1.0), (x, y1, C + 1.0)], [(0, 0), (1, 0), (1, 1), (0, 1)], 'sign_road', (1, 0, 0))
    xb = C - 0.02
    panel(m, [(xb, y0, C + 1.0), (xb, y0, C - 1.0), (xb, y1, C - 1.0), (xb, y1, C + 1.0)], [(0, 0), (1, 0), (1, 1), (0, 1)], 'galv', (-1, 0, 0))
    for yy in (y0, y1):
        box(m, xb, yy - 0.02, C - 1.0, x, yy + 0.02, C + 1.0, 'galv')
    return m


PROPS3 = {
    'prop_water_tower': (water_tower, 'Водонапорная башня', (0, 0, 0, 16, 16, 16), _water_tower_cells(), 0),
    'prop_lep_pylon': (lep_pylon, 'Опора ЛЭП', (0, 0, 0, 16, 16, 16),
                       [(dx, dy, dz) for dx in (-1, 1) for dz in (-1, 1) for dy in (0, 1)] + [(0, dy, 0) for dy in range(1, 17)], 0),
    'prop_gas_pipe': (gas_pipe, 'Газопровод', (0, 0, 0, 16, 16, 16), [(1, 0, 0), (2, 0, 0), (3, 0, 0), (0, 1, 0), (1, 1, 0), (2, 1, 0), (3, 1, 0)], 0),
    'prop_gas_loop': (gas_loop, 'Компенсатор газопровода', (0, 0, 0, 16, 16, 16),
                      [(1, 0, 0), (1, 1, 0), (8, 0, 0), (8, 1, 0), (0, 1, 0)] + [(x, y, 0) for x in (2, 7) for y in range(0, 6)] +
                      [(x, 5, 0) for x in range(3, 7)], 0),
    'prop_hangar': (hangar, 'Арочный ангар', (0, 0, 0, 16, 1, 16), _hangar_cells(), 0),
    'prop_swing': (swing, 'Качели', (5, 0, 0, 11, 16, 16), [(0, 0, 1), (0, 0, -1), (0, 1, 1), (0, 1, -1)], 0),
    'prop_carousel': (carousel, 'Карусель', (0, 0, 0, 16, 10, 16), [], 0),
    'prop_slide': (slide, 'Горка', (0, 0, 2, 16, 16, 14), [(-1, 0, 0), (1, 0, 0), (2, 0, 0), (0, 1, 0)], 0),
    'prop_fridge': (fridge, 'Холодильник «ЗИЛ»', (3, 0, 3, 13, 16, 13), [(0, 1, 0)], 0),
    'prop_fridge_lying': (lambda: fridge(True), 'Брошенный холодильник', (0, 0, 0, 16, 11, 16), [(1, 0, 0)], 0),
    'prop_bathtub': (bathtub, 'Чугунная ванна', (0, 0, 2, 16, 12, 14), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_radiator': (radiator, 'Батарея', (4, 0, 0, 12, 10, 16), [], 0),
    'prop_trash_bags': (trash_bags, 'Мусорные мешки', (0, 0, 0, 16, 10, 16), [], 0),
    'prop_chainlink': (chainlink, 'Сетка-рабица', (7, 0, 0, 9, 16, 16), [(0, 0, 1), (0, 0, -1), (0, 1, 0), (0, 1, 1), (0, 1, -1)], 0),
    'prop_road_sign': (road_sign, 'Дорожный указатель', (6, 0, 4, 10, 16, 12), [(0, 1, 0), (0, 1, 1), (0, 1, -1)], 0),
}
# провода: блок стоит в соседней от опоры клетке по ходу линии, модель тянется до следующей опоры
for d in range(-3, 4):
    PROPS3[f'prop_pole_wires_{d + 3}'] = ((lambda dd=d: pole_wires(dd)), 'Провода', (7, 0, 7, 9, 1, 9), [], 0)
    PROPS3[f'prop_lep_wires_{d + 3}'] = ((lambda dd=d: lep_wires(dd)), 'Провода ЛЭП', (7, 0, 7, 9, 1, 9), [], 0)
models.PROPS.update(PROPS3)
