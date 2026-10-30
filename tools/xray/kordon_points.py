"""Точки узлов Кордона (карточка 004) → src/main/resources/data/stalkerzone/kordon_points.json.

python3 -I kordon_points.py <level.game> <all.spawn> <l01_escape.szl> <bld.json> <levels.json> <out.json>

Берёт из Anomaly (level_l01_escape) пути смарт-террейнов (kamp — костры, sleep — места сна, guard/grd/sniper — часовые,
comander — флаг, walker/pat — патруль), место Сидоровича и level_changer-ы; укрытия — крытые места построек уровня;
дороги — кратчайший путь по рельефу с предпочтением асфальта/грунтовки. На выходе только координаты Minecraft
(x = xr_x+DX, z = -xr_z+DZ, y = xr_y+YOFF), данных игры в файле нет."""
import sys, re, struct, json, math, gzip, numpy as np
from scipy import ndimage
from skimage.graph import route_through_array

game, spawn, szlp, bldp, levp, outp = sys.argv[1:7]
LV = json.load(open(levp))['l01_escape']; DX, DZ, YOFF = LV['dx'], LV['dz'], LV.get('yoff', 100); B = LV['bound']

# --- рельеф, покрытие, воксели
d = gzip.open(szlp).read(); o = 4
X0, Z0, W, D, _ = struct.unpack_from('<5i', d, o); o += 20; n = W * D
G = np.frombuffer(d, '<i2', n, o).reshape(D, W) / 8.0; o += 2 * n
S = np.frombuffer(d, 'u1', n, o).reshape(D, W); o += n
WT = np.frombuffer(d, '<i2', n, o).reshape(D, W) / 8.0; o += 2 * n
nv = struct.unpack_from('<i', d, o)[0]; o += 4
V = np.frombuffer(d, np.dtype([('x', '<i2'), ('y', '<i2'), ('z', '<i2'), ('m', 'u1')]), nv, o)

def gy(x, z):
    x = min(max(int(x), X0), X0 + W - 1); z = min(max(int(z), Z0), Z0 + D - 1)
    return int(math.floor(G[z - Z0, x - X0])) + 1

def mc(p, y=True):
    x, yy, z = p
    x, z = int(round(x + DX)), int(round(-z + DZ))
    return [x, int(round(yy + YOFF)) if y else gy(x, z), z]

# --- точки путей level.game (первая точка каждого пути)
gd = open(game, 'rb').read(); P = {}
for m in re.finditer(rb'([a-z][a-z0-9_]{5,80})\x00', gd):
    nm = m.group(1).decode()
    if re.fullmatch(r'(name|wp)\d+', nm): continue
    for q in range(m.end(), m.end() + 48):
        x, y, z = struct.unpack_from('<3f', gd, q)
        if abs(x) < 1000 and -60 < y < 120 and abs(z) < 1000 and min(abs(x), abs(z)) > 0.3 and abs(y) > 0.01:
            P.setdefault(nm, (x, y, z)); break

# --- all.spawn: Сидорович и переходы уровня
sd = open(spawn, 'rb').read()
def spawn_pos(name):
    m = re.search(re.escape(name.encode()) + rb'\x00', sd)
    for q in range(m.end(), m.end() + 40):
        x, y, z = struct.unpack_from('<3f', sd, q)
        if abs(x) < 1000 and abs(z) < 1000 and abs(y) < 120 and min(abs(x), abs(z)) > 1: return (x, y, z)
CH = {}
for m in re.finditer(rb'\x01\x00level_changer\x00(esc[A-Za-z_0-9]*)\x00', sd):
    CH[m.group(1).decode()] = struct.unpack_from('<3f', sd, m.end() + 2)

# --- крытые места: колонки с перекрытием на 3..12 блоков над землёй и свободным полом
vx = V['x'].astype(int) - X0; vz = V['z'].astype(int) - Z0
ok = (vx >= 0) & (vx < W) & (vz >= 0) & (vz < D); vx, vz, vy = vx[ok], vz[ok], V['y'][ok].astype(int)
h = vy - np.floor(G[vz, vx]).astype(int)
roof = np.zeros((D, W), bool); floor_ = np.zeros((D, W), bool)
roof[vz[(h >= 3) & (h <= 12)], vx[(h >= 3) & (h <= 12)]] = True
floor_[vz[(h >= 1) & (h <= 2)], vx[(h >= 1) & (h <= 2)]] = True
covered = ndimage.binary_opening(roof & ~floor_, np.ones((3, 3)))
lab, nl = ndimage.label(covered)
SHEL = []
for i, sl in enumerate(ndimage.find_objects(lab)):
    area = int((lab[sl] == i + 1).sum())
    if area < 12: continue
    cz, cx = ndimage.center_of_mass(lab[sl] == i + 1)
    zz, xx = int(cz) + sl[0].start, int(cx) + sl[1].start
    if lab[zz, xx] != i + 1:   # центр вне укрытия (буква Г) — ближайшая клетка укрытия
        ys, xs = np.nonzero(lab == i + 1); k = np.argmin((ys - zz) ** 2 + (xs - xx) ** 2); zz, xx = ys[k], xs[k]
    SHEL.append((xx + X0, zz + Z0, area))
print('укрытий', len(SHEL))

# --- узлы: порядок и число — как в KordonScenario (19)
NODES = [
    ('Деревня новичков', 'esc_smart_terrain_2_12'), ('Бункер Сидоровича', None), ('Южный блокпост', 'esc_smart_terrain_3_16'),
    ('Выход: Большая земля', None), ('Элеватор', 'esc_smart_terrain_5_9'), ('АТП', 'esc_smart_terrain_7_11'),
    ('Выход: Тёмная долина', 'esc_smart_terrain_9_10'), ('Туннель с электрами', 'esc_smart_terrain_4_9'),
    ('Разрушенный мост', 'esc_smart_terrain_6_8'), ('Дыра в заборе', None), ('Свиноферма', 'esc_smart_terrain_5_7'),
    ('Дальний блокпост', 'esc_smart_terrain_5_2'), ('Выход: Свалка', None), ('Холм за элеватором', 'esc_smart_terrain_5_10'),
    ('Поля у свинофермы', 'esc_smart_terrain_3_7'), ('Лес у дальнего блокпоста', 'esc_smart_terrain_5_6'),
    ('Овраг у АТП', 'esc_smart_terrain_8_10'), ('Луг у фермы', 'esc_smart_terrain_6_7'), ('Опушка у пути на Свалку', 'esc_smart_terrain_6_3')]
sid = spawn_pos('esc_m_trader')
FIX = {  # центры узлов без смарт-террейна, координаты MC
    1: [int(round(sid[0] + DX)), int(round(-sid[2] + DZ))],
    3: [85, 330],                                    # асфальт на южном краю уровня
    6: [int(round(CH['esc_level_changer_to_darkscape_1'][0] + DX)) - 12, int(round(-CH['esc_level_changer_to_darkscape_1'][2] + DZ))],
    9: [430, -350],                                  # насыпь между мостом и путевым лагерем
    12: [int(round(CH['esc_level_changer_to_garbage_1'][0] + DX)), int(round(-CH['esc_level_changer_to_garbage_1'][2] + DZ)) - 6]}
# проходы: бокс входа (x0, z0, x1, z1) вокруг узла
GATE_R = {3: 10, 6: 10, 7: 8, 8: 12, 9: 6, 12: 10}

def pts(smart, keys):
    out = []
    for k, p in sorted(P.items()):
        if smart and k.startswith(smart + '_') and re.search(keys, k[len(smart) + 1:]): out.append(mc(p))
    return out

nodes = []
for i, (name, smart) in enumerate(NODES):
    camp = pts(smart, r'^kamp_\d+$'); beds = pts(smart, r'^sleep_\d+$')
    guards = pts(smart, r'^(guard|grd|sniper)_\d+_walk$'); flag = pts(smart, r'^comander_walk$')
    patrol = pts(smart, r'(walker|wlkr|pat|ptrl|patx|specwalker|camper)(_\d+)?_walk(_\d+)?$')
    if i in FIX: c = FIX[i]
    else:
        al = camp + flag + guards
        c = [int(round(sum(p[0] for p in al) / len(al))), int(round(sum(p[2] for p in al) / len(al)))]
    center = [c[0], gy(*c), c[1]]
    e = {'id': i, 'name': name, 'center': center,
         'campfire': camp[0] if camp else None, 'beds': beds, 'guards': guards,
         'flag': flag[0] if flag else [center[0] + 2, center[1], center[2] + 2], 'patrol': patrol}
    if smart: e['anomalySmart'] = smart
    if i == 1:
        e['trader'] = mc(sid); e['shelter'] = [mc(sid)]
    else:
        sh = sorted(((math.hypot(x - c[0], z - c[1]), x, z) for x, z, a in SHEL), key=lambda t: t[0])
        e['shelter'] = [[x, gy(x, z), z] for dd, x, z in sh[:4] if dd < 90]
    if i in GATE_R:
        r = GATE_R[i]; e['gate'] = [c[0] - r, c[1] - r, c[0] + r, c[1] + r]
    nodes.append(e)

# --- дороги по рёбрам графа KordonScenario
EDGES = [(0, 1), (0, 2), (2, 3), (2, 4), (1, 4), (4, 5), (5, 6), (10, 11), (11, 12), (0, 7), (7, 10), (4, 8), (8, 10),
         (5, 9), (9, 11), (13, 4), (16, 5), (14, 10), (15, 11), (17, 10), (18, 11)]
gyd, gxd = np.gradient(G); slope = np.hypot(gxd, gyd)
cost = 3.0 + 6.0 * np.minimum(slope, 3.0) ** 2
cost[np.isin(S, (6,))] = 1.0; cost[np.isin(S, (2, 4))] = np.minimum(cost[np.isin(S, (2, 4))], 1.6)
cost[WT > G + 0.5] += 40.0     # вода
bx0, bz0, bx1, bz1 = math.ceil(B[0] + DX), math.ceil(-B[3] + DZ), math.floor(B[2] + DX), math.floor(-B[1] + DZ)
outside = np.ones((D, W), bool); outside[bz0 - Z0 + 4:bz1 - Z0 - 3, bx0 - X0 + 4:bx1 - X0 - 3] = False
cost[outside] = 1e6
roads = {}
for a, b in EDGES:
    pa, pb = nodes[a]['center'], nodes[b]['center']
    path, _ = route_through_array(cost, (pa[2] - Z0, pa[0] - X0), (pb[2] - Z0, pb[0] - X0), fully_connected=True, geometric=True)
    keep = path[::20] + [path[-1]]
    roads['%d-%d' % (a, b)] = [[int(x) + X0, gy(int(x) + X0, int(z) + Z0), int(z) + Z0] for z, x in keep]

closed = [mc(v, y=False) for k, v in sorted(CH.items()) if k in (
    'esc_level_changer_to_marsh_2', 'esc_level_changer_to_garbage_2', 'esc_level_changer_to_pole_1')]
out = {
    'level': 'l01_escape', 'source': 'S.T.A.L.K.E.R. Anomaly 1.5.x, level_l01_escape (tools/xray/kordon_points.py)',
    'shift': {'dx': DX, 'dz': DZ, 'yoff': YOFF},
    'bounds': {'minX': int(math.ceil(bx0)), 'maxX': int(math.floor(bx1)), 'minZ': int(math.ceil(bz0)), 'maxZ': int(math.floor(bz1))},
    'spawn': nodes[0]['campfire'] and [nodes[0]['campfire'][0] + 3, nodes[0]['campfire'][1], nodes[0]['campfire'][2] + 3],
    'exits': {'marsh': mc(CH['esc_level_changer_to_marsh_1'], y=False), 'darkscape': mc(CH['esc_level_changer_to_darkscape_1'], y=False),
              'garbage': mc(CH['esc_level_changer_to_garbage_1'], y=False)},
    'closedPassages': closed,
    'nodes': nodes, 'roads': roads}
json.dump(out, open(outp, 'w'), ensure_ascii=False, indent=1, default=int)
for e in nodes:
    print('%2d %-26s center %-18s scen (%d,%d) camp %s beds %d guards %d patrol %d shelter %d' % (
        e['id'], e['name'], e['center'], round(e['center'][0] / 10), round(e['center'][2] / 10), 'да' if e['campfire'] else '-',
        len(e['beds']), len(e['guards']), len(e['patrol']), len(e['shelter'])))
print('bounds', out['bounds'], 'spawn', out['spawn'])
