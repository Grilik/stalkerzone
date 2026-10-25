"""Пропсы и техника, часть 2 (v1.13): лагеря сталкеров, блокпосты, лаборатории, Припять, вертолёты и вагоны.
Всё в блоках: центр основания в (0.5, 0, 0.5), перед смотрит в +x. Подключается из gen_props.py:
дописывает models.PROPS, models.VEHICLES и расширяет models.car."""
import math
import numpy as np
import models
from mesh import Mesh, box, cylinder, lathe, torus, wheel, rrect, circle, ring_x, ring_y, ring_z

C = 0.5


def rot_y(m, deg, about=(C, 0, C)):
    a = math.radians(deg)
    R = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    p = np.asarray(about, float)
    m.transform(R, p - R @ p)
    return m


def rot_x(m, deg, about=(C, 0, C)):
    a = math.radians(deg)
    R = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    p = np.asarray(about, float)
    m.transform(R, p - R @ p)
    return m


def rot_z(m, deg, about=(C, 0, C)):
    a = math.radians(deg)
    R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    p = np.asarray(about, float)
    m.transform(R, p - R @ p)
    return m


def part(fn, *a, **k):
    t = Mesh()
    fn(t, *a, **k)
    return t


# =========================================================================== лагерь сталкеров
def campfire():
    """Костёр: кольцо камней, поленья шалашом, угли (светится)."""
    m = Mesh()
    for k in range(9):
        a = 2 * math.pi * k / 9
        s = part(box, -0.09, 0, -0.07, 0.09, 0.12, 0.07, 'rubble', r=0.04)
        rot_y(s, math.degrees(a), (0, 0, 0)); s.transform(None, (C + 0.42 * math.cos(a), 0, C + 0.42 * math.sin(a)))
        m.merge(s)
    cylinder(m, 'y', (C, C), 0.3, 0.0, 0.05, 'embers', n=12)
    for k in range(5):
        lg = part(cylinder, 'y', (0, 0), 0.045, 0, 0.62, 'pole', n=6)
        rot_z(lg, 28, (0, 0, 0)); rot_y(lg, 72 * k, (0, 0, 0)); lg.transform(None, (C, 0.02, C))
        m.merge(lg)
    return m


def log_seat():
    """Бревно-скамья у костра."""
    m = Mesh()
    cylinder(m, 'x', (C, 0.2), 0.2, C - 0.9, C + 0.9, 'pole', n=10, cap_mat='wood_end')
    for x in (-0.6, 0.6):
        box(m, C + x - 0.06, 0, C - 0.18, C + x + 0.06, 0.08, C + 0.18, 'wood')
    return m


def tent():
    """Брезентовая палатка сталкеров (двускатная), 2×3 блока, вход на +x."""
    m = Mesh()
    L, W, H = 2.6, 1.8, 1.45
    # скаты как лофт треугольника вдоль x
    tri = [(C + W / 2, 0.0), (C, H), (C - W / 2, 0.0)]
    m.loft([ring_x(C - L / 2, tri), ring_x(C + L / 2 - 0.3, tri)], 'tarp', caps=False, smooth=False)
    # задняя стенка и полог входа
    m.quad([(C - L / 2, 0, C + W / 2), (C - L / 2, 0, C - W / 2), (C - L / 2, H, C), (C - L / 2, H, C)], [(0, 0), (1, 0), (0.5, 1), (0.5, 1)], 'tarp')
    m.quad([(C + L / 2 - 0.3, 0, C - W / 2), (C + L / 2 - 0.3, 0, C - 0.05), (C + L / 2 - 0.3, H * 0.95, C - 0.03), (C + L / 2 - 0.3, H, C)],
           [(0, 0), (1, 0), (1, 1), (0, 1)], 'tarp')
    # откинутый полог и растяжки
    m.quad([(C + L / 2 - 0.3, 0, C + 0.05), (C + L / 2 + 0.2, 0, C + W / 2 + 0.2), (C + L / 2 - 0.25, H * 0.9, C + 0.05), (C + L / 2 - 0.25, H * 0.9, C + 0.05)],
           [(0, 0), (1, 0), (0, 1), (0, 1)], 'tarp')
    for x in (C - L / 2 - 0.05, C + L / 2 - 0.25):
        cylinder(m, 'y', (x, C), 0.03, 0, H + 0.08, 'pole', n=5)
    m.loft([ring_x(C - L / 2 - 0.05, circle(0.012, 4, C, H + 0.02)), ring_x(C + L / 2 - 0.25, circle(0.012, 4, C, H + 0.02))], 'black', caps=False)
    # спальник внутри
    box(m, C - 1.1, 0, C - 0.55, C + 0.6, 0.12, C - 0.1, 'mattress', r=0.05)
    return m


def army_tent():
    """Армейская палатка УСБ-56: стены, крыша-шатёр, 4×5 блоков, вход на +x."""
    m = Mesh()
    L, W, h, H = 4.6, 3.6, 1.6, 2.7
    pts_wall = rrect(W, h, 0.05, 1, C, h / 2)
    m.loft([ring_x(C - L / 2, pts_wall), ring_x(C + L / 2, pts_wall)], 'tarp_army', caps=False, smooth=False)
    roof = [(C + W / 2 + 0.1, h - 0.05), (C, H), (C - W / 2 - 0.1, h - 0.05)]
    m.loft([ring_x(C - L / 2 - 0.1, roof), ring_x(C + L / 2 + 0.1, roof)], 'tarp_army', caps=False, smooth=False)
    for x, flip in ((C - L / 2, True), (C + L / 2, False)):
        q = [(x, h, C + W / 2), (x, h, C - W / 2), (x, H - 0.05, C), (x, H - 0.05, C)]
        m.quad(q[::-1] if flip else q, [(0, 0), (1, 0), (0.5, 1), (0.5, 1)], 'tarp_army')
    # задняя стена, вход-проём спереди с откинутым пологом
    m.quad([(C - L / 2, 0, C + W / 2), (C - L / 2, 0, C - W / 2), (C - L / 2, h, C - W / 2), (C - L / 2, h, C + W / 2)], [(0, 0), (1, 0), (1, 1), (0, 1)], 'tarp_army')
    for z0, z1 in ((C - W / 2, C - 0.45), (C + 0.45, C + W / 2)):
        m.quad([(C + L / 2, 0, z0), (C + L / 2, 0, z1), (C + L / 2, h, z1), (C + L / 2, h, z0)][::-1], [(0, 0), (1, 0), (1, 1), (0, 1)], 'tarp_army')
    box(m, C + L / 2, 1.2, C - 0.5, C + L / 2 + 0.12, 1.6, C + 0.5, 'tarp_army', r=0.05)       # скатанный полог
    # окна-клапаны и печная труба
    for x in (-1.2, 0.6):
        for s in (-1, 1):
            box(m, C + x, 0.9, C + s * (W / 2 + 0.01) - 0.01, C + x + 0.6, 1.3, C + s * (W / 2 + 0.01) + 0.01, 'glass')
    cylinder(m, 'y', (C - 1.2, C + 0.9), 0.07, 2.0, 3.1, 'steel_dark', n=8)
    for x in (C - L / 2 - 0.4, C + L / 2 + 0.4):
        for z in (C - W / 2 - 0.4, C + W / 2 + 0.4):
            box(m, x - 0.03, 0, z - 0.03, x + 0.03, 0.25, z + 0.03, 'wood')
    return m


def spotlight():
    """Прожектор на треноге (светится)."""
    m = Mesh()
    for k in range(3):
        lg = part(cylinder, 'y', (0, 0), 0.025, -0.05, 1.25, 'steel_dark', n=5)
        rot_z(lg, 18, (0, 1.25, 0)); rot_y(lg, 120 * k, (0, 0, 0)); lg.transform(None, (C, 0, C))
        m.merge(lg)
    cylinder(m, 'y', (C, C), 0.035, 1.1, 1.35, 'steel_dark', n=6)
    head = Mesh()
    cylinder(head, 'x', (0, 0), 0.2, -0.2, 0.15, 'steel_dark', n=14, r1=0.24)
    cylinder(head, 'x', (0, 0), 0.21, 0.15, 0.17, 'lamp', n=14, cap_mat='lamp')
    rot_z(head, 12, (0, 0, 0)); head.transform(None, (C, 1.5, C))
    m.merge(head)
    return m


def generator():
    """Дизель-генератор на раме: корпус, бак, выхлоп, кабель."""
    m = Mesh()
    box(m, C - 0.75, 0, C - 0.35, C + 0.75, 0.1, C + 0.35, 'rust')
    box(m, C - 0.65, 0.1, C - 0.32, C + 0.3, 0.75, C + 0.32, 'barrel_green', r=0.06)
    cylinder(m, 'x', (C, 0.42), 0.26, C + 0.3, C + 0.7, 'steel_dark', n=14, cap_mat='steel_dark')
    box(m, C - 0.6, 0.75, C - 0.25, C - 0.1, 0.92, C + 0.25, 'barrel_green', r=0.05)          # бак
    cylinder(m, 'y', (C - 0.35, C + 0.2), 0.04, 0.75, 1.25, 'rust', n=6)
    for x in np.linspace(-0.55, 0.15, 6):
        box(m, C + x, 0.25, C + 0.32, C + x + 0.05, 0.65, C + 0.34, 'black')
    box(m, C - 0.2, 0.45, C - 0.34, C + 0.1, 0.62, C - 0.32, 'rad_yellow')
    m.loft([ring_y(0.02, circle(0.025, 5, C + 0.8, C - 0.2))[::-1], ring_y(0.02, circle(0.025, 5, C + 1.4, C - 0.4))[::-1]], 'black', caps=False)
    return m


def ammo_crates():
    """Штабель армейских ящиков с патронами."""
    m = Mesh()
    rng = np.random.default_rng(4)
    for i, (x, y, z, rot) in enumerate([(-0.25, 0, -0.18, 0), (0.25, 0, -0.18, 0), (-0.25, 0, 0.22, 0), (0.25, 0, 0.22, 8),
                                         (-0.05, 0.36, -0.05, 4), (0.05, 0.72, 0.02, -6)]):
        c = part(box, -0.24, 0, -0.18, 0.24, 0.35, 0.18, 'crate_ammo', r=0.015)
        for hx in (-0.25, 0.25):
            box(c, hx - 0.012, 0.12, -0.05, hx + 0.012, 0.2, 0.05, 'steel_dark')     # ручки
        rot_y(c, rot + float(rng.uniform(-3, 3)), (0, 0, 0)); c.transform(None, (C + x, y, C + z))
        m.merge(c)
    return m


def pallet_sacks():
    """Поддон с мешками (цемент, мука)."""
    m = Mesh()
    for z in (-0.4, 0, 0.4):
        box(m, C - 0.55, 0, C + z - 0.05, C + 0.55, 0.06, C + z + 0.05, 'wood')
    for x in np.linspace(-0.5, 0.5, 6):
        box(m, C + x - 0.06, 0.06, C - 0.5, C + x + 0.06, 0.1, C + 0.5, 'wood')
    for layer in range(3):
        for i in range(2):
            for j in range(2):
                x, z = -0.24 + i * 0.5, -0.22 + j * 0.46
                if layer == 2 and (i + j) % 2: continue
                rings = [ring_x(C + x + dx, rrect(0.42 * s, 0.16 * s, 0.06, 2, C + z, 0.18 + layer * 0.17)) for dx, s in ((-0.23, 0.7), (-0.2, 1.0), (0.2, 1.0), (0.23, 0.7))]
                m.loft(rings, 'sack')
    return m


# =========================================================================== блокпост
def barrier():
    """Шлагбаум: стойка с противовесом и полосатая стрела на 4 блока вдоль +z."""
    m = Mesh()
    box(m, C - 0.15, 0, C - 0.15, C + 0.15, 1.05, C + 0.15, 'steel', r=0.03, axis='y')
    box(m, C - 0.18, 1.0, C - 0.18, C + 0.18, 1.12, C + 0.18, 'steel_dark')
    cylinder(m, 'z', (C, 1.0), 0.06, C - 0.7, C + 4.2, 'stripe', n=8)
    box(m, C - 0.14, 0.85, C - 0.75, C + 0.14, 1.15, C - 0.45, 'concrete')                 # противовес
    box(m, C - 0.05, 0, C + 4.05, C + 0.05, 0.95, C + 4.15, 'stripe')                      # опорная стойка
    box(m, C - 0.1, 0.9, C + 4.0, C + 0.1, 1.0, C + 4.2, 'steel_dark')
    return m


def sign_post(kind='rad'):
    """Знак на столбе: радиационная опасность или «Стой! Запретная зона»."""
    m = Mesh()
    cylinder(m, 'y', (C, C), 0.035, 0, 1.9, 'galv', n=6)
    mat = 'sign_rad' if kind == 'rad' else 'sign_stop'
    if kind == 'rad':
        tri = [(C + 0.36, 1.35), (C, 1.98), (C - 0.36, 1.35)]
        m.loft([ring_x(C + 0.04, tri), ring_x(C + 0.06, tri)], mat, caps=True, smooth=False)
    else:
        box(m, C + 0.04, 1.3, C - 0.42, C + 0.06, 1.9, C + 0.42, mat)
    return m


def hedgehog():
    """Противотанковый «ёж» из трёх двутавров."""
    m = Mesh()
    for ax in range(3):
        b = part(box, -0.06, -0.75, -0.035, 0.06, 0.75, 0.035, 'rust')
        b2 = part(box, -0.012, -0.75, -0.09, 0.012, 0.75, 0.09, 'rust')
        b.merge(b2)
        if ax == 0: rot_z(b, 45, (0, 0, 0))
        elif ax == 1: rot_x(b, 45, (0, 0, 0)); rot_y(b, 90, (0, 0, 0))
        else: rot_z(b, -45, (0, 0, 0)); rot_y(b, 90, (0, 0, 0))
        b.transform(None, (C, 0.55, C))
        m.merge(b)
    return m


def concertina():
    """Спираль колючей проволоки (АКЛ) на 3 блока вдоль x."""
    m = Mesh()
    n, R, L = 22, 0.42, 3.0
    pts = []
    for i in range(n * 12 + 1):
        t = i / (n * 12)
        a = t * n * 2 * math.pi
        pts.append((C - L / 2 + t * L, 0.44 + R * math.sin(a), C + R * math.cos(a)))
    for i in range(len(pts) - 1):
        p0, p1 = np.array(pts[i]), np.array(pts[i + 1])
        d = p1 - p0
        nrm = np.cross(d, [1, 0, 0]); nrm = nrm / (np.linalg.norm(nrm) + 1e-9) * 0.012
        b = np.cross(d, nrm); b = b / (np.linalg.norm(b) + 1e-9) * 0.012
        for s0, s1 in ((nrm, b), (b, -nrm)):
            m.quad([p0 + s0, p1 + s0, p1 + s1, p0 + s1], [(0, 0), (1, 0), (1, 1), (0, 1)], 'wire')
    for x in (-1.3, 0.0, 1.3):
        cylinder(m, 'y', (C + x, C), 0.03, 0, 1.0, 'pole', n=5)
    return m


def watch_booth():
    """Будка часового (грибок): столб и жестяная крыша-конус."""
    m = Mesh()
    cylinder(m, 'y', (C, C), 0.07, 0, 2.3, 'stripe', n=8)
    cylinder(m, 'y', (C, C), 0.75, 2.2, 2.25, 'paint_olive', n=16, r1=0.75)
    cylinder(m, 'y', (C, C), 0.75, 2.25, 2.6, 'paint_olive', n=16, r1=0.05)
    return m


# =========================================================================== лаборатории и бункеры
def lab_tank():
    """Капсула с образцом: стеклянный цилиндр с зелёной жидкостью на основании (светится)."""
    m = Mesh()
    lathe(m, (C, C), [(0.42, 0.0), (0.44, 0.04), (0.44, 0.3), (0.38, 0.34)], 'steel', n=18)
    cylinder(m, 'y', (C, C), 0.32, 0.34, 1.8, 'lab_liquid', n=18)
    lathe(m, (C, C), [(0.4, 1.85), (0.42, 1.9), (0.42, 2.05), (0.2, 2.15), (0.08, 2.2)], 'steel', n=18)
    for k in range(4):
        a = math.pi / 2 * k + 0.4
        cylinder(m, 'y', (C + 0.43 * math.cos(a), C + 0.43 * math.sin(a)), 0.025, 0.3, 1.9, 'steel', n=5)
    cylinder(m, 'z', (C - 0.3, 2.1), 0.04, C - 0.05, C + 0.7, 'black', n=6)                 # шланги
    return m


def lab_console():
    """Пульт управления с наклонной панелью, лампочками и тумблерами (светится слабо)."""
    m = Mesh()
    box(m, C - 0.35, 0, C - 0.8, C + 0.25, 0.8, C + 0.8, 'console_body', r=0.03, axis='z')
    panel = [(C + 0.25, 0.8), (C - 0.05, 1.25), (C - 0.35, 1.25), (C - 0.35, 0.8)]
    pts = [(x, y) for x, y in panel]
    m.loft([ring_z(C - 0.8, [(x, y) for x, y in pts]), ring_z(C + 0.8, [(x, y) for x, y in pts])], 'console_body', smooth=False)
    # лицевая панель с приборами — отдельной текстурой поверх наклона
    a = (C + 0.25, 0.8); b = (C - 0.05, 1.25)
    m.quad([(a[0] + 0.005, a[1], C + 0.78), (a[0] + 0.005, a[1], C - 0.78), (b[0] + 0.005, b[1] + 0.003, C - 0.78), (b[0] + 0.005, b[1] + 0.003, C + 0.78)],
           [(0, 0), (1, 0), (1, 1), (0, 1)], 'console_panel')
    box(m, C - 0.35, 1.25, C - 0.8, C - 0.2, 1.65, C + 0.8, 'console_body')                 # стойка с экраном
    box(m, C - 0.2, 1.3, C - 0.5, C - 0.19, 1.6, C + 0.1, 'screen')
    return m


def server_rack():
    """Шкаф ЭВМ (ЕС ЭВМ) с бобинами магнитной ленты."""
    m = Mesh()
    box(m, C - 0.3, 0, C - 0.45, C + 0.3, 1.9, C + 0.45, 'console_body', r=0.02, axis='y')
    box(m, C + 0.3, 0.2, C - 0.4, C + 0.31, 1.75, C + 0.4, 'console_panel')
    for y in (1.3, 0.85):
        for z in (-0.2, 0.2):
            cylinder(m, 'x', (C + z, y), 0.15, C + 0.31, C + 0.34, 'tape', n=14, cap_mat='tape')
            cylinder(m, 'x', (C + z, y), 0.04, C + 0.34, C + 0.36, 'steel', n=6, cap_mat='steel')
    return m


def lab_table():
    """Лабораторный стол с колбами и микроскопом."""
    m = Mesh()
    box(m, C - 0.45, 0.82, C - 0.9, C + 0.45, 0.88, C + 0.9, 'tile_white')
    for x in (-0.4, 0.4):
        for z in (-0.85, 0.85):
            box(m, C + x - 0.03, 0, C + z - 0.03, C + x + 0.03, 0.82, C + z + 0.03, 'steel')
    box(m, C - 0.4, 0.15, C - 0.85, C + 0.4, 0.18, C + 0.85, 'steel')
    for z, h in ((-0.6, 0.18), (-0.45, 0.24), (-0.3, 0.14)):
        lathe(m, (C + 0.1, C + z), [(0.04, 0.88), (0.06, 0.92), (0.025, 0.88 + h), (0.03, 0.9 + h)], 'lab_glass', n=10)
    # микроскоп
    box(m, C - 0.1, 0.88, C + 0.35, C + 0.12, 0.92, C + 0.6, 'black')
    cylinder(m, 'y', (C - 0.05, C + 0.47), 0.03, 0.92, 1.18, 'black', n=6)
    cylinder(m, 'x', (C + 0.47, 1.2), 0.035, C - 0.1, C + 0.12, 'black', n=8)
    return m


def locker():
    """Армейский шкафчик для одежды, слегка помятый."""
    m = Mesh()
    box(m, C - 0.28, 0, C - 0.45, C + 0.28, 1.85, C + 0.45, 'locker', r=0.015, axis='y')
    for z in (-0.22, 0.22):
        for y in np.arange(1.5, 1.72, 0.06):
            box(m, C + 0.28, y, C + z - 0.12, C + 0.285, y + 0.025, C + z + 0.12, 'black')   # вентиляция
        box(m, C + 0.28, 0.95, C + z - 0.02, C + 0.31, 1.05, C + z + 0.02, 'steel')          # ручка
    box(m, C + 0.28, 0.05, C - 0.002, C + 0.285, 1.8, C + 0.002, 'black')
    return m


def army_bed():
    """Панцирная кровать с матрасом и подушкой."""
    m = Mesh()
    for x in (-0.95, 0.95):
        for z in (-0.4, 0.4):
            cylinder(m, 'y', (C + x, C + z), 0.025, 0, 0.6 if x < 0 else 0.75, 'steel', n=6)
        cylinder(m, 'z', (C + x, 0.55 if x < 0 else 0.7), 0.02, C - 0.4, C + 0.4, 'steel', n=6)
    for z in (-0.4, 0.4):
        cylinder(m, 'x', (C + z, 0.4), 0.02, C - 0.95, C + 0.95, 'steel', n=6)
    box(m, C - 0.92, 0.4, C - 0.38, C + 0.92, 0.52, C + 0.38, 'mattress', r=0.04)
    box(m, C + 0.55, 0.52, C - 0.3, C + 0.88, 0.62, C + 0.3, 'pillow', r=0.04)
    return m


def soviet_table():
    """Канцелярский стол с ящиками, лампой и пишущей машинкой."""
    m = Mesh()
    box(m, C - 0.4, 0.74, C - 0.75, C + 0.4, 0.79, C + 0.75, 'wood_vlak')
    box(m, C - 0.38, 0.0, C + 0.25, C + 0.38, 0.74, C + 0.72, 'wood_vlak')              # тумба
    for y in (0.15, 0.4, 0.62):
        box(m, C + 0.38, y, C + 0.47, C + 0.395, y + 0.03, C + 0.52, 'steel')
    for z in (-0.7,):
        for x in (-0.35, 0.35):
            box(m, C + x - 0.03, 0, C + z - 0.03, C + x + 0.03, 0.74, C + z + 0.03, 'wood_vlak')
    box(m, C - 0.15, 0.79, C - 0.4, C + 0.15, 0.89, C - 0.05, 'black', r=0.02)           # пишущая машинка
    cylinder(m, 'z', (C - 0.12, 0.9), 0.03, C - 0.45, C, 'black', n=6)
    cylinder(m, 'y', (C - 0.2, C + 0.45), 0.08, 0.79, 0.81, 'paint_olive', n=10)          # лампа
    lg = part(cylinder, 'y', (0, 0), 0.012, 0, 0.35, 'steel', n=5); rot_z(lg, -20, (0, 0, 0)); lg.transform(None, (C - 0.2, 0.8, C + 0.45)); m.merge(lg)
    cylinder(m, 'y', (C - 0.05, C + 0.45), 0.09, 1.02, 1.12, 'paint_olive', n=10, r1=0.03)
    return m


def tv():
    """Ламповый телевизор «Рубин» на ножках."""
    m = Mesh()
    box(m, C - 0.3, 0.42, C - 0.35, C + 0.25, 0.95, C + 0.35, 'wood_vlak', r=0.03)
    box(m, C + 0.25, 0.48, C - 0.3, C + 0.27, 0.9, C + 0.18, 'screen', r=0.02)
    for z in (0.24, 0.3):
        cylinder(m, 'x', (C + z, 0.8), 0.025, C + 0.25, C + 0.28, 'black', n=6)
    for x in (-0.22, 0.18):
        for z in (-0.28, 0.28):
            cylinder(m, 'y', (C + x, C + z), 0.015, 0, 0.42, 'black', n=5)
    return m


def radio():
    """Армейская радиостанция Р-159 с антенной на ящике."""
    m = Mesh()
    box(m, C - 0.25, 0, C - 0.2, C + 0.25, 0.3, C + 0.2, 'crate_ammo')
    box(m, C - 0.18, 0.3, C - 0.13, C + 0.18, 0.55, C + 0.13, 'paint_olive', r=0.02)
    box(m, C + 0.18, 0.36, C - 0.1, C + 0.19, 0.5, C + 0.1, 'console_panel')
    cylinder(m, 'y', (C - 0.1, C + 0.08), 0.006, 0.55, 1.6, 'steel', n=4)
    m.loft([ring_y(0.4, circle(0.012, 5, C + 0.19, C - 0.05))[::-1], ring_y(0.35, circle(0.012, 5, C + 0.4, C + 0.2))[::-1]], 'black', caps=False)
    return m


# =========================================================================== город
def bus_stop():
    """Бетонная остановка советского образца: задняя стенка-мозаика, козырёк, скамья. 1×3 блока вдоль z."""
    m = Mesh()
    box(m, C - 0.45, 0, C - 1.5, C - 0.3, 2.3, C + 1.5, 'concrete')
    box(m, C - 0.29, 0.6, C - 1.3, C - 0.28, 2.1, C + 1.3, 'mosaic')
    box(m, C - 0.45, 2.3, C - 1.6, C + 0.6, 2.42, C + 1.6, 'concrete', r=0.02)
    for z in (-1.4, 1.4):
        box(m, C - 0.44, 0, C + z - 0.08, C + 0.5, 2.3, C + z + 0.08, 'concrete')
    box(m, C - 0.28, 0.42, C - 1.2, C + 0.05, 0.47, C + 1.2, 'wood')
    for z in (-0.9, 0.9):
        box(m, C - 0.25, 0, C + z - 0.05, C, 0.42, C + z + 0.05, 'concrete')
    return m


def phone_booth():
    """Телефонная будка: каркас, стёкла, красная крыша-шапка."""
    m = Mesh()
    box(m, C - 0.42, 0, C - 0.42, C + 0.42, 0.06, C + 0.42, 'concrete')
    for x in (-0.4, 0.4):
        for z in (-0.4, 0.4):
            box(m, C + x - 0.03, 0.06, C + z - 0.03, C + x + 0.03, 2.2, C + z + 0.03, 'paint_red')
    for (x0, z0, x1, z1) in ((-0.38, -0.4, 0.38, -0.39), (-0.38, 0.39, 0.38, 0.4), (-0.4, -0.38, -0.39, 0.38)):
        box(m, C + x0, 0.9, C + z0, C + x1, 2.1, C + z1, 'glass')
        box(m, C + x0, 0.06, C + z0, C + x1, 0.9, C + z1, 'paint_red')
    box(m, C - 0.46, 2.2, C - 0.46, C + 0.46, 2.36, C + 0.46, 'paint_red', r=0.04)
    box(m, C - 0.38, 1.2, C - 0.1, C - 0.3, 1.5, C + 0.1, 'black')                        # аппарат
    return m


def kiosk():
    """Ларёк «Союзпечать»: стеклянная витрина, двускатная крыша."""
    m = Mesh()
    box(m, C - 0.9, 0, C - 0.9, C + 0.9, 0.9, C + 0.9, 'paint_blue', r=0.02)
    box(m, C - 0.9, 0.9, C - 0.9, C + 0.9, 2.0, C + 0.9, 'glass')
    for x in (-0.9, 0.9):
        for z in (-0.9, 0.9):
            box(m, C + x - 0.04, 0.9, C + z - 0.04, C + x + 0.04, 2.0, C + z + 0.04, 'paint_blue')
    roof = [(C + 1.05, 2.0), (C, 2.45), (C - 1.05, 2.0)]
    m.loft([ring_x(C - 1.05, roof), ring_x(C + 1.05, roof)], 'paint_blue', smooth=False)
    box(m, C + 0.91, 2.0, C - 0.8, C + 0.92, 2.25, C + 0.8, 'sign_kiosk')
    return m


def poster_board():
    """Стенд с советским плакатом на двух стойках."""
    m = Mesh()
    for z in (-0.6, 0.6):
        cylinder(m, 'y', (C, C + z), 0.035, 0, 2.2, 'galv', n=6)
    box(m, C + 0.03, 1.0, C - 0.65, C + 0.06, 2.15, C + 0.65, 'poster')
    box(m, C - 0.0, 2.15, C - 0.7, C + 0.12, 2.2, C + 0.7, 'steel')
    return m


def flower_bed():
    """Бетонная клумба-вазон с сухой травой."""
    m = Mesh()
    lathe(m, (C, C), [(0.3, 0.0), (0.35, 0.05), (0.45, 0.5), (0.48, 0.55), (0.42, 0.55)], 'concrete', n=12)
    cylinder(m, 'y', (C, C), 0.42, 0.45, 0.5, 'rubble', n=12)
    return m


# =========================================================================== техника (v1.13)
def heli_mi8(damage):
    """Ми-8: фюзеляж, хвостовая балка, лопасти; разбитый лежит на боку без хвоста."""
    m = Mesh()
    # фюзеляж — лофт по сечениям вдоль x (нос на +x)
    secs = [(-3.2, 0.55, 2.55, 1.15, 0.5), (-2.4, 0.4, 2.65, 1.2, 0.55), (2.2, 0.4, 2.65, 1.2, 0.55), (3.0, 0.6, 2.4, 1.05, 0.5), (3.6, 0.9, 2.0, 0.8, 0.4)]
    models.body_loft(m, secs)
    # кабина: остекление носа
    models.house_loft(m, [(2.9, 1.5, 1.52, 0.95, 0.8, 0.05), (3.3, 1.5, 2.25, 0.95, 0.7, 0.15), (3.75, 1.5, 1.9, 0.7, 0.45, 0.1)], 'glass' if damage == 0 else 'interior')
    # капот двигателей и редуктор
    models.body_loft(m, [(-1.6, 2.55, 3.1, 0.8, 0.35), (1.8, 2.55, 3.15, 0.8, 0.35), (2.4, 2.55, 2.85, 0.6, 0.25)])
    cylinder(m, 'y', (C - 0.2, C), 0.25, 3.1, 3.4, 'steel_dark', n=12)
    # окна салона
    for x in np.arange(-2.0, 2.0, 0.8):
        for s in (-1, 1):
            cylinder(m, 'z', (C + x, 1.8), 0.22, C + s * 1.2 - 0.01, C + s * 1.2 + 0.01, 'glass' if damage == 0 else 'interior', n=10)
    # хвостовая балка и киль
    if damage == 0:
        cylinder(m, 'x', (C, 2.2), 0.42, C - 9.0, C - 3.1, 'paint', n=12, r1=0.25)
        box(m, C - 9.2, 2.0, C - 0.08, C - 8.2, 3.6, C + 0.08, 'paint', r=0.05)
        box(m, C - 7.4, 2.1, C - 0.9, C - 6.9, 2.18, C + 0.9, 'paint')                     # стабилизатор
        for k in range(3):
            bl = part(box, -0.1, -0.02, -0.9, 0.1, 0.02, 0.9, 'black'); rot_x(bl, 60 * k, (0, 0, 0)); bl.transform(None, (C - 8.9, 3.0, C - 0.3)); m.merge(bl)
    else:
        cylinder(m, 'x', (C, 2.2), 0.42, C - 5.0, C - 3.1, 'paint', n=12, r1=0.36)          # обломок балки
    # лопасти несущего винта
    for k in range(5):
        bl = part(box, 0.2, -0.03, -0.18, 9.0 if damage == 0 else 3.5 + k * 0.6, 0.03, 0.18, 'black')
        if damage: rot_z(bl, -8 - 4 * k, (0, 0, 0))
        rot_y(bl, 72 * k + 10, (0, 0, 0)); bl.transform(None, (C - 0.2, 3.45, C)); m.merge(bl)
    # шасси
    if damage == 0:
        for x, z in ((2.4, 0.0), (-1.2, 1.4), (-1.2, -1.4)):
            wheel(m, (C + x, 0.32, C + z), 0.32, 0.22, 'z')
            cylinder(m, 'y', (C + x, C + z * 0.8), 0.05, 0.3, 0.8, 'steel', n=6)
    # блистеры-подвески (наружные баки)
    for s in (-1, 1):
        cylinder(m, 'x', (C + s * 1.35, 0.95), 0.32, C - 1.6, C + 1.0, 'paint', n=12, cap_mat='paint')
    if damage:
        rot_x(m, 70, (C, 0, C))
        a = np.array(m.v); m.transform(None, (0, -a[:, 1].min(), 0))
    return m


def heli_mi24(damage):
    """Ми-24 «Крокодил»: тандемная кабина, крылья с пилонами."""
    m = Mesh()
    secs = [(-3.0, 0.7, 2.3, 0.95, 0.45), (-2.0, 0.6, 2.5, 1.05, 0.5), (1.2, 0.6, 2.5, 1.05, 0.5), (2.4, 0.7, 2.1, 0.75, 0.4), (3.6, 0.85, 1.55, 0.45, 0.25)]
    models.body_loft(m, secs)
    gl = 'glass' if damage == 0 else 'interior'
    models.house_loft(m, [(1.6, 2.1, 2.12, 0.6, 0.5, 0.05), (2.0, 2.1, 2.7, 0.6, 0.45, 0.15), (2.6, 2.1, 2.5, 0.55, 0.35, 0.12), (3.2, 1.55, 2.2, 0.5, 0.3, 0.1), (3.6, 1.55, 1.7, 0.4, 0.2, 0.05)], gl)
    models.body_loft(m, [(-1.8, 2.45, 3.0, 0.75, 0.3), (1.2, 2.45, 3.0, 0.75, 0.3), (1.6, 2.45, 2.7, 0.5, 0.2)])
    cylinder(m, 'y', (C - 0.3, C), 0.22, 3.0, 3.25, 'steel_dark', n=12)
    for s in (-1, 1):
        w = part(box, -0.7, -0.06, 0, 0.7, 0.06, 2.1, 'paint', r=0.03)
        if s < 0: w.transform(np.diag([1, 1, -1]))
        rot_x(w, -s * 12, (0, 0, 0)); w.transform(None, (C - 0.6, 1.9, C + s * 1.0)); m.merge(w)
        for zz in (1.4, 2.4):
            cylinder(m, 'x', (C + s * zz, 1.55), 0.22, C - 1.1, C + 0.4, 'steel_dark', n=12, cap_mat='black')   # блоки НАР
    cylinder(m, 'x', (C, 0.75), 0.06, C + 3.4, C + 4.1, 'black', n=6)                        # пулемёт в носу
    if damage == 0:
        cylinder(m, 'x', (C, 2.0), 0.36, C - 8.6, C - 2.9, 'paint', n=12, r1=0.22)
        box(m, C - 8.8, 1.8, C - 0.08, C - 7.9, 3.3, C + 0.08, 'paint', r=0.05)
        for k in range(3):
            bl = part(box, -0.1, -0.02, -0.85, 0.1, 0.02, 0.85, 'black'); rot_x(bl, 60 * k, (0, 0, 0)); bl.transform(None, (C - 8.5, 2.7, C + 0.3)); m.merge(bl)
        for x, z in ((2.6, 0.0), (-1.0, 1.1), (-1.0, -1.1)):
            wheel(m, (C + x, 0.3, C + z), 0.3, 0.2, 'z')
    else:
        cylinder(m, 'x', (C, 2.0), 0.36, C - 4.6, C - 2.9, 'paint', n=12, r1=0.3)
    for k in range(5):
        bl = part(box, 0.2, -0.03, -0.17, 8.4 if damage == 0 else 3.0 + k * 0.5, 0.03, 0.17, 'black')
        if damage: rot_z(bl, -10 - 5 * k, (0, 0, 0))
        rot_y(bl, 72 * k + 30, (0, 0, 0)); bl.transform(None, (C - 0.3, 3.3, C)); m.merge(bl)
    if damage:
        rot_x(m, -25, (C, 0, C)); rot_z(m, 8, (C, 0, C))
        a = np.array(m.v); m.transform(None, (0, -a[:, 1].min() - 0.3, 0))
    return m


def wagon(damage):
    """Товарный вагон на тележках, двери приоткрыты; разбитый — без колёс, со вмятинами."""
    m = Mesh()
    L, W, H, y0 = 13.0, 2.8, 3.6, 1.1
    models.body_loft(m, [(-L / 2, y0, y0 + H, W / 2, 0.05), (L / 2, y0, y0 + H, W / 2, 0.05)])
    # крыша полукруглая
    rings = []
    for x in (-L / 2 - 0.05, L / 2 + 0.05):
        pts = [(C + W / 2 * math.cos(math.radians(a)), y0 + H + 0.35 * math.sin(math.radians(a))) for a in np.linspace(0, 180, 9)]
        rings.append(ring_x(C + x, pts))
    m.loft(rings, 'paint', caps=True, smooth=True)
    # рёбра каркаса и двери
    for x in np.arange(-L / 2 + 0.6, L / 2, 1.2):
        for s in (-1, 1):
            box(m, C + x - 0.05, y0, C + s * (W / 2 + 0.02) - 0.03, C + x + 0.05, y0 + H, C + s * (W / 2 + 0.02) + 0.03, 'rust')
    for s in (-1, 1):
        box(m, C - 1.1, y0 + 0.05, C + s * (W / 2 + 0.06) - 0.03, C + 0.4, y0 + H - 0.1, C + s * (W / 2 + 0.06) + 0.03, 'paint2')
        box(m, C - 1.15, y0 + H - 0.15, C + s * (W / 2 + 0.1) - 0.05, C + 1.6, y0 + H - 0.05, C + s * (W / 2 + 0.1) + 0.05, 'rust')   # направляющая двери
    # рама, буфера, тележки
    box(m, C - L / 2 - 0.1, y0 - 0.3, C - W / 2 + 0.2, C + L / 2 + 0.1, y0, C + W / 2 - 0.2, 'black')
    for x in (-L / 2 - 0.35, L / 2 + 0.05):
        for s in (-1, 1):
            cylinder(m, 'x', (C + s * 0.9, y0 - 0.1), 0.17, C + x, C + x + 0.3, 'steel_dark', n=10, cap_mat='steel')
    if damage == 0:
        for bx in (-L / 2 + 2.0, L / 2 - 2.0):
            box(m, C + bx - 1.2, 0.35, C - 1.0, C + bx + 1.2, 0.85, C + 1.0, 'rust')
            for wx in (-0.75, 0.75):
                for s in (-1, 1):
                    cylinder(m, 'z', (C + bx + wx, 0.45), 0.45, C + s * 0.72 - 0.08, C + s * 0.72 + 0.08, 'rim', n=14)
    else:
        box(m, C - L / 2 + 0.5, 0, C - 1.0, C + L / 2 - 0.5, y0 - 0.3, C + 1.0, 'rust')
        rot_x(m, 6, (C, 0, C))
    return m


def gaz66(damage):
    """ГАЗ-66 «шишига» с кунгом: бескапотная кабина, высокий кузов-фургон."""
    m = Mesh()
    hl = 2.9
    models.body_loft(m, [(hl - 1.6, 0.85, 2.3, 1.05, 0.1), (hl - 0.1, 0.85, 2.3, 1.05, 0.12), (hl, 0.9, 2.15, 1.0, 0.1)])
    models.house_loft(m, [(hl - 0.12, 1.65, 1.67, 1.0, 0.95, 0.03), (hl + 0.02, 1.65, 2.25, 1.0, 0.95, 0.05)], 'glass' if damage == 0 else 'interior')
    box(m, C - hl, 1.0, C - 1.15, C + hl - 1.7, 3.0, C + 1.15, 'paint2', r=0.06)             # кунг
    box(m, C - hl - 0.01, 2.2, C - 0.4, C - hl, 2.6, C + 0.4, 'glass' if damage == 0 else 'interior')
    box(m, C - hl + 0.2, 1.4, C + 1.15, C - hl + 1.0, 2.6, C + 1.16, 'rust')                 # дверь кунга
    box(m, C - hl, 0.7, C - 1.0, C + hl, 0.9, C + 1.0, 'black')
    models.wheels(m, [-hl + 1.0, hl - 0.8], 0.95, 0.55, 0.36, damage=damage)
    box(m, C + hl, 1.0, C - 0.55, C + hl + 0.02, 1.45, C + 0.55, 'grille')
    models.lights_front(m, hl + 0.01, 1.3, 0.75, 0.11)
    return m


VEH2 = {
    'mi8': ('Вертолёт Ми-8', 'olive', 'olive', 12.0, 3.0, 3.6, heli_mi8),
    'mi24': ('Вертолёт Ми-24', 'olive', 'olive', 11.5, 4.0, 3.4, heli_mi24),
    'wagon': ('Товарный вагон', 'red', 'cream', 13.5, 2.9, 5.0, wagon),
    'gaz66': ('ГАЗ-66 с кунгом', 'olive', 'olive', 5.9, 2.3, 3.0, gaz66),
}

_car = models.car


def car(kind, damage=0):
    if kind in VEH2:
        return VEH2[kind][6](damage)
    return _car(kind, damage)


models.car = car
for k, v in VEH2.items():
    models.VEHICLES[k] = v[:6]

PROPS2 = {
    'prop_campfire': (campfire, 'Костёр сталкеров', (2, 0, 2, 14, 6, 14), [], 13),
    'prop_log_seat': (log_seat, 'Бревно-скамья', (0, 0, 4, 16, 7, 12), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_tent': (tent, 'Палатка сталкеров', (0, 0, 0, 16, 16, 16), [(1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (-1, 0, 1), (-1, 0, -1)], 0),
    'prop_army_tent': (army_tent, 'Армейская палатка', (0, 0, 0, 16, 16, 16),
                       [(x, y, z) for x in (-2, -1, 0, 1, 2) for z in (-1, 0, 1) for y in (0, 1) if (x, y, z) != (0, 0, 0)], 0),
    'prop_spotlight': (spotlight, 'Прожектор', (4, 0, 4, 12, 16, 12), [(0, 1, 0)], 15),
    'prop_generator': (generator, 'Дизель-генератор', (0, 0, 2, 16, 15, 14), [(1, 0, 0)], 0),
    'prop_ammo_crates': (ammo_crates, 'Ящики с патронами', (0, 0, 0, 16, 16, 16), [], 0),
    'prop_pallet_sacks': (pallet_sacks, 'Поддон с мешками', (0, 0, 0, 16, 10, 16), [], 0),
    'prop_barrier': (barrier, 'Шлагбаум', (5, 0, 5, 11, 16, 11), [(0, 0, 1), (0, 0, 2), (0, 0, 3), (0, 0, 4)], 0),
    'prop_sign_rad': (lambda: sign_post('rad'), 'Знак радиационной опасности', (6, 0, 6, 10, 16, 10), [(0, 1, 0)], 0),
    'prop_sign_stop': (lambda: sign_post('stop'), 'Знак «Стой! Запретная зона»', (6, 0, 6, 10, 16, 10), [(0, 1, 0)], 0),
    'prop_hedgehog': (hedgehog, 'Противотанковый ёж', (0, 0, 0, 16, 16, 16), [], 0),
    'prop_concertina': (concertina, 'Спираль колючей проволоки', (0, 0, 1, 16, 14, 15), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_watch_booth': (watch_booth, 'Грибок часового', (5, 0, 5, 11, 16, 11), [(0, 1, 0), (0, 2, 0)], 0),
    'prop_lab_tank': (lab_tank, 'Капсула с образцом', (1, 0, 1, 15, 16, 15), [(0, 1, 0), (0, 2, 0)], 9),
    'prop_lab_console': (lab_console, 'Пульт управления', (0, 0, 0, 12, 16, 16), [(0, 0, 1), (0, 0, -1), (0, 1, 0), (0, 1, 1), (0, 1, -1)], 5),
    'prop_server_rack': (server_rack, 'Шкаф ЭВМ', (2, 0, 1, 14, 16, 15), [(0, 1, 0)], 3),
    'prop_lab_table': (lab_table, 'Лабораторный стол', (1, 0, 0, 15, 14, 16), [(0, 0, 1), (0, 0, -1)], 0),
    'prop_locker': (locker, 'Шкафчик', (3, 0, 1, 13, 16, 15), [(0, 1, 0)], 0),
    'prop_army_bed': (army_bed, 'Панцирная кровать', (0, 0, 2, 16, 10, 14), [(1, 0, 0), (-1, 0, 0)], 0),
    'prop_soviet_table': (soviet_table, 'Канцелярский стол', (1, 0, 0, 15, 13, 16), [(0, 0, 1), (0, 0, -1)], 0),
    'prop_tv': (tv, 'Телевизор «Рубин»', (3, 0, 2, 13, 15, 14), [], 0),
    'prop_radio': (radio, 'Радиостанция', (4, 0, 4, 12, 9, 12), [], 0),
    'prop_bus_stop': (bus_stop, 'Остановка', (0, 0, 0, 16, 16, 16), [(0, 0, 1), (0, 0, -1), (0, 1, 0), (0, 1, 1), (0, 1, -1), (1, 0, 0), (1, 1, 0)], 0),
    'prop_phone_booth': (phone_booth, 'Телефонная будка', (1, 0, 1, 15, 16, 15), [(0, 1, 0)], 0),
    'prop_kiosk': (kiosk, 'Ларёк «Союзпечать»', (0, 0, 0, 16, 16, 16),
                   [(x, y, z) for x in (-1, 0, 1) for z in (-1, 0, 1) for y in (0, 1) if (x, y, z) != (0, 0, 0)], 0),
    'prop_poster_board': (poster_board, 'Стенд с плакатом', (6, 0, 0, 10, 16, 16), [(0, 1, 0), (0, 0, 1), (0, 0, -1)], 0),
    'prop_flower_bed': (flower_bed, 'Бетонная клумба', (1, 0, 1, 15, 9, 15), [], 0),
}
models.PROPS.update(PROPS2)

# =========================================================================== фасады и крыши
def balcony():
    """Балкон хрущёвки: плита, ржавое ограждение с прутьями, хлам и бельевая верёвка. Перед на +x (наружу)."""
    m = Mesh()
    box(m, C - 0.5, 0, C - 1.5, C + 0.5, 0.15, C + 1.5, 'concrete', r=0.02)
    box(m, C - 0.5, -0.12, C - 1.45, C + 0.45, 0.0, C + 1.45, 'concrete_dark')
    box(m, C + 0.42, 0.15, C - 1.5, C + 0.5, 0.95, C + 1.5, 'balcony_panel')                 # экран ограждения
    box(m, C + 0.4, 0.95, C - 1.52, C + 0.52, 1.0, C + 1.52, 'rust')                          # поручень
    for z in (-1.5, 1.5):
        box(m, C - 0.5, 0.15, C + z - 0.04, C + 0.5, 0.95, C + z + 0.04, 'rust')
    for z in np.arange(-1.3, 1.31, 0.35):
        cylinder(m, 'y', (C + 0.38, C + z), 0.012, 0.15, 0.95, 'rust', n=4)
    box(m, C - 0.3, 0.15, C + 0.7, C + 0.05, 0.5, C + 1.2, 'wood')                            # ящик
    lathe(m, (C - 0.2, C - 1.1), [(0.1, 0.15), (0.12, 0.2), (0.12, 0.45), (0.1, 0.5)], 'rust', n=10)   # бак
    m.loft([ring_x(C - 0.3, circle(0.006, 4, C - 1.45, 1.6)), ring_x(C - 0.3, circle(0.006, 4, C + 1.45, 1.6))], 'black', caps=False)
    return m


def roof_vent():
    """Вентшахта на крыше: кирпичная тумба с жестяным колпаком."""
    m = Mesh()
    box(m, C - 0.4, 0, C - 0.4, C + 0.4, 1.1, C + 0.4, 'brick')
    for x0, z0, x1, z1 in ((-0.42, -0.42, 0.42, -0.38), (-0.42, 0.38, 0.42, 0.42)):
        box(m, C + x0, 0.6, C + z0, C + x1, 0.85, C + z1, 'black')
    box(m, C - 0.5, 1.25, C - 0.5, C + 0.5, 1.3, C + 0.5, 'galv')
    for x in (-0.35, 0.35):
        for z in (-0.35, 0.35):
            cylinder(m, 'y', (C + x, C + z), 0.02, 1.1, 1.25, 'galv', n=4)
    return m


def tv_antenna():
    """Телевизионная антенна «волновой канал» на мачте."""
    m = Mesh()
    cylinder(m, 'y', (C, C), 0.025, 0, 2.6, 'galv', n=5)
    cylinder(m, 'x', (C, 2.4), 0.015, C - 0.7, C + 0.7, 'galv', n=4)
    for x in np.linspace(-0.6, 0.6, 7):
        w = 0.5 - abs(x) * 0.25
        cylinder(m, 'z', (C + x, 2.4), 0.01, C - w, C + w, 'galv', n=4)
    for k in (-1, 1):
        m.loft([ring_y(0.02, circle(0.006, 4, C + k * 0.9, C + 0.2))[::-1], ring_y(2.0, circle(0.006, 4, C, C))[::-1]], 'black', caps=False)
    return m


PROPS2.update({
    'prop_balcony': (balcony, 'Балкон', (0, 0, 0, 16, 16, 16), [(0, 0, 1), (0, 0, -1)], 0),
    'prop_roof_vent': (roof_vent, 'Вентшахта', (1, 0, 1, 15, 16, 15), [(0, 1, 0)], 0),
    'prop_tv_antenna': (tv_antenna, 'Антенна', (6, 0, 6, 10, 16, 10), [(0, 1, 0), (0, 2, 0)], 0),
})
models.PROPS.update(PROPS2)
