"""Сборка bedrock-моделей оружия для TaCZ: скелет по образцу (имена костей совпадают, чтобы работали
стандартные анимации TaCZ), свои кубы, атлас материалов и простой растеризатор для иконок."""
import json, math, random
import numpy as np
from PIL import Image, ImageFilter

HERE = __file__.rsplit('/', 1)[0]
SKEL = json.load(open(HERE + '/skeletons.json'))

# ------------------------------------------------------------------ атлас материалов
TEX_UNITS = 128          # размер текстуры в единицах модели
TEX_PX = 1024            # реальный размер PNG (в 8 раз детальнее)
K = TEX_PX // TEX_UNITS
REG = 42                 # сторона области материала в единицах
MATS = ['blued', 'black', 'walnut', 'plum', 'polymer', 'birch', 'bright', 'brass', 'olive']


def _noise(h, w, scale, rng):
    """Сглаженный шум 0..1."""
    small = rng.random((max(2, h // scale), max(2, w // scale)))
    im = Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im, dtype=np.float32) / 255.0


def _grain(h, w, rng, base, dark, light, streak=6):
    """Древесина: волокна вдоль u, годичные полосы, сучки."""
    y = np.arange(h)[:, None].astype(np.float32)
    x = np.arange(w)[None, :].astype(np.float32)
    warp = _noise(h, w, 60, rng) * 40 + _noise(h, w, 18, rng) * 8
    rings = (np.sin((y + warp) / streak) + 1) / 2
    fine = _noise(h, w, 2, rng) * 0.25
    t = np.clip(rings * 0.75 + fine, 0, 1)[..., None]
    col = np.array(dark, np.float32) * (1 - t) + np.array(light, np.float32) * t
    col = col * (0.92 + 0.16 * _noise(h, w, 90, rng))[..., None]
    return col


def _metal(h, w, rng, base, wear=0.15, rust=0.0):
    n = _noise(h, w, 40, rng) * 0.5 + _noise(h, w, 6, rng) * 0.35 + _noise(h, w, 1, rng) * 0.15
    col = np.array(base, np.float32)[None, None, :] * (0.82 + 0.3 * n)[..., None]
    # потёртости: светлые царапины
    scr = np.zeros((h, w), np.float32)
    for _ in range(int(h * w * 0.0004)):
        x0, y0 = rng.integers(0, w), rng.integers(0, h)
        ln = rng.integers(10, 60); ang = rng.random() * math.pi
        for t in range(ln):
            xx = int(x0 + math.cos(ang) * t); yy = int(y0 + math.sin(ang) * t)
            if 0 <= xx < w and 0 <= yy < h: scr[yy, xx] = 1
    scr = np.asarray(Image.fromarray((scr * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)), np.float32) / 255
    col = col + scr[..., None] * wear * 255
    if rust > 0:
        r = _noise(h, w, 24, rng) * _noise(h, w, 5, rng)
        m = np.clip((r - (0.55 - rust)) * 6, 0, 1)[..., None]
        col = col * (1 - m) + np.array([120, 62, 30], np.float32) * m * (0.7 + 0.6 * _noise(h, w, 2, rng))[..., None]
    return col


def build_atlas(seed=7):
    rng = np.random.default_rng(seed)
    img = np.zeros((TEX_PX, TEX_PX, 4), np.uint8)
    s = REG * K
    for i, m in enumerate(MATS):
        gx, gy = (i % 3) * REG * K, (i // 3) * REG * K
        if m == 'blued': c = _metal(s, s, rng, (50, 54, 62), 0.16, 0.0)
        elif m == 'black': c = _metal(s, s, rng, (34, 35, 37), 0.10, 0.0)
        elif m == 'walnut': c = _grain(s, s, rng, None, (70, 34, 18), (128, 70, 38))
        elif m == 'plum': c = _metal(s, s, rng, (92, 42, 36), 0.05) * 0.95
        elif m == 'polymer': c = _metal(s, s, rng, (30, 30, 30), 0.04)
        elif m == 'birch': c = _grain(s, s, rng, None, (122, 74, 40), (176, 118, 66), 4)
        elif m == 'bright': c = _metal(s, s, rng, (150, 152, 156), 0.25)
        elif m == 'brass': c = _metal(s, s, rng, (186, 140, 62), 0.2)
        elif m == 'olive': c = _metal(s, s, rng, (72, 78, 50), 0.12, 0.06)
        img[gy:gy + s, gx:gx + s, :3] = np.clip(c, 0, 255).astype(np.uint8)
        img[gy:gy + s, gx:gx + s, 3] = 255
    return Image.fromarray(img, 'RGBA')


def mat_origin(m):
    i = MATS.index(m)
    return (i % 3) * REG, (i // 3) * REG


# ------------------------------------------------------------------ модель
class Gun:
    def __init__(self, skeleton, ident, seed=1):
        sk = SKEL[skeleton]
        self.ident = ident
        self.desc = sk['desc']
        self.bones = {}
        self.order = []
        for b in sk['bones']:
            e = {k: (list(v) if isinstance(v, list) else v) for k, v in b.items()}
            e['cubes'] = []
            self.bones[b['name']] = e
            self.order.append(b['name'])
        self.rng = random.Random(seed)
        self.keep_cubes = {}

    # --- скелет
    def pivot(self, bone, p):
        self.bones[bone]['pivot'] = list(p)

    def bone(self, name, parent, pivot=(0, 0, 0), rotation=None):
        if name not in self.bones:
            self.order.append(name)
        e = {'name': name, 'parent': parent, 'pivot': list(pivot), 'cubes': []}
        if rotation: e['rotation'] = list(rotation)
        self.bones[name] = e
        return name

    def marker(self, bone, origin, size):
        """Кубы-метки (позиции рук) как в формате TaCZ."""
        self.bones[bone]['cubes'].append({'origin': list(origin), 'size': list(size), 'uv': [0, 0]})

    # --- кубы
    def _uv(self, m, w, h):
        ox, oy = mat_origin(m)
        w = max(0.01, min(w, REG - 2)); h = max(0.01, min(h, REG - 2))
        u = ox + 1 + self.rng.random() * (REG - 2 - w)
        v = oy + 1 + self.rng.random() * (REG - 2 - h)
        return {'uv': [round(u, 4), round(v, 4)], 'uv_size': [round(w, 4), round(h, 4)]}

    def box(self, bone, x0, y0, z0, x1, y1, z1, m, rot=None, piv=None, inflate=None):
        x0, x1 = min(x0, x1), max(x0, x1)
        y0, y1 = min(y0, y1), max(y0, y1)
        z0, z1 = min(z0, z1), max(z0, z1)
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        c = {'origin': [round(x0, 4), round(y0, 4), round(z0, 4)], 'size': [round(sx, 4), round(sy, 4), round(sz, 4)],
             'uv': {'north': self._uv(m, sx, sy), 'south': self._uv(m, sx, sy), 'east': self._uv(m, sz, sy),
                    'west': self._uv(m, sz, sy), 'up': self._uv(m, sx, sz), 'down': self._uv(m, sx, sz)}}
        if rot and any(rot):
            c['rotation'] = [round(r, 4) for r in rot]
            c['pivot'] = [round(p, 4) for p in (piv if piv else ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))]
        if inflate: c['inflate'] = inflate
        self.bones[bone]['cubes'].append(c)
        c['_mat'] = m
        return c

    def cyl(self, bone, cx, cy, r, z0, z1, m, n=8, rx=0.0):
        """Цилиндр вдоль оси z: n/2 повёрнутых брусков дают n-угольник."""
        a = 2 * r * math.tan(math.pi / n)
        for k in range(n // 2):
            ang = k * 180.0 / n
            self.box(bone, cx - a / 2, cy - r, z0, cx + a / 2, cy + r, z1, m, rot=[rx, 0, ang], piv=[cx, cy, (z0 + z1) / 2])

    def cyl_y(self, bone, cx, cz, r, y0, y1, m, n=8):
        """Цилиндр вдоль оси y."""
        a = 2 * r * math.tan(math.pi / n)
        for k in range(n // 2):
            ang = k * 180.0 / n
            self.box(bone, cx - a / 2, y0, cz - r, cx + a / 2, y1, cz + r, m, rot=[0, ang, 0], piv=[cx, (y0 + y1) / 2, cz])

    def cyl_x(self, bone, cy, cz, r, x0, x1, m, n=8):
        a = 2 * r * math.tan(math.pi / n)
        for k in range(n // 2):
            ang = k * 180.0 / n
            self.box(bone, x0, cy - a / 2, cz - r, x1, cy + a / 2, cz + r, m, rot=[ang, 0, 0], piv=[(x0 + x1) / 2, cy, cz])

    def rbox(self, bone, x0, y0, z0, x1, y1, z1, m, ch=0.25, rot=None, piv=None, top=True, bottom=True):
        """Брусок со скруглёнными продольными рёбрами (фаски по 45° вдоль z)."""
        if piv is None and rot: piv = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        self.box(bone, x0 + ch, y0, z0, x1 - ch, y1, z1, m, rot, piv)
        self.box(bone, x0, y0 + (ch if bottom else 0), z0, x1, y1 - (ch if top else 0), z1, m, rot, piv)
        # угловые фаски: квадрат, повёрнутый на 45° вокруг z, вписанный в угол
        d = ch * math.sqrt(2)
        corners = []
        if top: corners += [(x0 + ch, y1 - ch), (x1 - ch, y1 - ch)]
        if bottom: corners += [(x0 + ch, y0 + ch), (x1 - ch, y0 + ch)]
        for cx, cy in corners:
            if rot and any(rot):
                continue  # на повёрнутых деталях фаски дают щели, обходимся без них
            self.box(bone, cx - d / 2, cy - d / 2, z0, cx + d / 2, cy + d / 2, z1, m, rot=[0, 0, 45], piv=[cx, cy, (z0 + z1) / 2])

    # --- вывод
    def geo(self):
        bones = []
        for n in self.order:
            b = self.bones[n]
            e = {'name': n}
            if b.get('parent'): e['parent'] = b['parent']
            e['pivot'] = b.get('pivot', [0, 0, 0])
            if b.get('rotation'): e['rotation'] = b['rotation']
            if b['cubes']:
                e['cubes'] = [{k: v for k, v in c.items() if not k.startswith('_')} for c in b['cubes']]
            bones.append(e)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + self.ident, 'texture_width': TEX_UNITS, 'texture_height': TEX_UNITS,
                            **self.desc}, 'bones': bones}]}


# ------------------------------------------------------------------ растеризатор для иконок и превью
HIDE_PREFIX = ('camera', 'views', 'positioning', 'refit', 'mag_extended', 'additional_magazine',
               'attachment_adapter', 'mount')
HIDE_EXACT = {'view', 'lefthand', 'righthand', 'leftHand', 'rightHand', 'constraint'}


def _rot(rx, ry, rz):
    # bedrock: x и y повёрнуты в обратную сторону
    ax, ay, az = math.radians(-rx), math.radians(-ry), math.radians(rz)
    Rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    Ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    Rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def faces(gun):
    """Все видимые грани модели в координатах оружия: (4 угла, uv-прямоугольник)."""
    out = []
    bones = gun.bones

    def hidden(n):
        while n:
            if n.startswith(HIDE_PREFIX) or n in HIDE_EXACT or n.endswith('_pos') or n.endswith('_view'):
                return True
            n = bones[n].get('parent')
        return False

    def chain(n):
        res = []
        while n:
            res.append(bones[n]); n = bones[n].get('parent')
        return res

    for n in gun.order:
        b = bones[n]
        if not b['cubes'] or hidden(n):
            continue
        for c in b['cubes']:
            if not isinstance(c.get('uv'), dict):
                continue
            o = np.array(c['origin'], float); s = np.array(c['size'], float)
            x0, y0, z0 = o; x1, y1, z1 = o + s
            quads = {
                'north': [(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)],
                'south': [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
                'east': [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)],
                'west': [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
                'up': [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
                'down': [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
            }
            for f, q in quads.items():
                P = np.array(q, float)
                if 'rotation' in c:
                    pv = np.array(c['pivot'], float)
                    P = (P - pv) @ _rot(*c['rotation']).T + pv
                for bb in chain(n):
                    if bb.get('rotation'):
                        pv = np.array(bb['pivot'], float)
                        P = (P - pv) @ _rot(*bb['rotation']).T + pv
                uv = c['uv'][f]
                out.append((P, uv))
    return out


def render(gun, tex, view, size=(512, 512), margin=0.06, bg=(0, 0, 0, 0), silhouette=None):
    """view: матрица 3x3, переводящая координаты оружия в (экран x, экран y вверх, глубина к камере)."""
    T = np.asarray(tex.convert('RGBA'), np.float32)
    th, tw = T.shape[:2]
    F = faces(gun)
    pts = np.concatenate([f[0] for f in F]) @ view.T
    mn, mx = pts.min(0), pts.max(0)
    W, H = size
    sc = min(W * (1 - 2 * margin) / (mx[0] - mn[0]), H * (1 - 2 * margin) / (mx[1] - mn[1]))
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    img = np.zeros((H, W, 4), np.float32); img[:] = bg
    zb = np.full((H, W), -1e9, np.float32)
    light = np.array([0.35, 0.8, 0.5]); light /= np.linalg.norm(light)
    for P, uv in F:
        Q = P @ view.T
        nrm = np.cross(Q[1] - Q[0], Q[3] - Q[0])
        ln = np.linalg.norm(nrm)
        if ln < 1e-9: continue
        nrm /= ln
        shade = 0.5 + 0.5 * abs(float(nrm @ light))
        sx = (Q[:, 0] - cx) * sc + W / 2
        sy = H / 2 - (Q[:, 1] - cy) * sc
        u0, v0 = uv['uv']; uw, vh = uv['uv_size']
        UV = np.array([[u0, v0 + vh], [u0 + uw, v0 + vh], [u0 + uw, v0], [u0, v0]]) * (tw / TEX_UNITS)
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], Q[list(tri), 2]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0: continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-12: continue
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            l2 = 1 - l0 - l1
            inside = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
            if not inside.any(): continue
            z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            m = inside & (z > sub)
            if not m.any(): continue
            uu = l0 * UV[tri[0], 0] + l1 * UV[tri[1], 0] + l2 * UV[tri[2], 0]
            vv = l0 * UV[tri[0], 1] + l1 * UV[tri[1], 1] + l2 * UV[tri[2], 1]
            col = T[np.clip(vv.astype(int), 0, th - 1), np.clip(uu.astype(int), 0, tw - 1)]
            sub[m] = z[m]
            if silhouette is not None:
                img[y0:y1 + 1, x0:x1 + 1][m] = silhouette
            else:
                c = col.copy(); c[..., :3] *= shade
                img[y0:y1 + 1, x0:x1 + 1][m] = c[m]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')


MIRROR = np.diag([-1.0, 1.0, 1.0])  # в bedrock ось x зеркальна: в игре правый бок оружия это -x


def side_view(side='left', yaw=0.0, pitch=0.0, roll=0.0):
    """Вид сбоку: left — дулом влево (как в HUD), right — дулом вправо (как иконка в слоте)."""
    base = np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]], float) if side == 'left' else \
        np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]], float)
    return view_matrix(yaw, pitch, roll) @ base @ MIRROR


def view_matrix(yaw, pitch, roll=0.0):
    """Поворот камеры: вокруг y (yaw), затем наклон (pitch), затем поворот экрана (roll)."""
    a, b, c = math.radians(yaw), math.radians(pitch), math.radians(roll)
    Ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    Rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    return Rz @ Rx @ Ry
