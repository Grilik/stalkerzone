"""Простые сетки для OBJ-моделей Forge: лофты (кузова), цилиндры, торы, скруглённые бруски.
Координаты в блоках, y вверх. Каждый треугольник/четырёхугольник несёт материал и uv в пределах [0,1]
(материал — это переменная текстуры в json-модели или область общего атласа)."""
import math
import numpy as np

ATLAS_GRID = 8  # атлас props.png: 8×8 областей


class Mesh:
    def __init__(self):
        self.v = []      # (x,y,z)
        self.vt = []     # (u,v)
        self.vn = []     # (x,y,z)
        self.faces = []  # (material, [(vi, ti, ni), ...])

    # ---------------------------------------------------------------- низкоуровневое
    def _add(self, P, UV, N, mat):
        idx = []
        for p, t, n in zip(P, UV, N):
            self.v.append(tuple(map(float, p)))
            self.vt.append((float(t[0]), float(t[1])))
            nn = np.asarray(n, float)
            l = np.linalg.norm(nn)
            self.vn.append(tuple(nn / l) if l > 1e-9 else (0.0, 1.0, 0.0))
            idx.append((len(self.v), len(self.vt), len(self.vn)))
        self.faces.append((mat, idx))

    def quad(self, P, UV, mat, N=None):
        P = [np.asarray(p, float) for p in P]
        if N is None:
            n = np.cross(P[1] - P[0], P[2] - P[0])
            if np.linalg.norm(n) < 1e-12:
                n = np.cross(P[2] - P[0], P[3] - P[0])
            N = [n] * len(P)
        self._add(P, UV, N, mat)

    def merge(self, other):
        ov, ot, on = len(self.v), len(self.vt), len(self.vn)
        self.v += other.v; self.vt += other.vt; self.vn += other.vn
        for mat, idx in other.faces:
            self.faces.append((mat, [(a + ov, b + ot, c + on) for a, b, c in idx]))

    def _volume(self, f0=0):
        s = 0.0
        for mat, idx in self.faces[f0:]:
            P = [np.asarray(self.v[i[0] - 1]) for i in idx]
            for k in range(1, len(P) - 1):
                s += np.dot(P[0], np.cross(P[k], P[k + 1])) / 6
        return s

    def transform(self, M=None, t=(0, 0, 0)):
        M = np.eye(3) if M is None else np.asarray(M, float)
        t = np.asarray(t, float)
        if np.linalg.det(M) < 0:  # зеркало переворачивает обход граней
            self.faces = [(mt, idx[::-1]) for mt, idx in self.faces]
        self.v = [tuple(M @ np.asarray(p) + t) for p in self.v]
        self.vn = [tuple(M @ np.asarray(n)) for n in self.vn]
        return self

    # ---------------------------------------------------------------- примитивы
    def loft(self, rings, mat, uscale=None, vscale=None, caps=True, smooth=True, cap_mat=None, mats=None):
        """rings: список колец одинаковой длины (N точек (x,y,z), обход против часовой при взгляде вдоль оси).
        mats: необязательная функция (i_ring, j_point) -> материал полосы."""
        R = [np.asarray(r, float) for r in rings]
        n = len(R[0])
        # v1.14: обход колец приводим к одному правилу (нормаль Ньюэлла против направления лофта), иначе
        # грани смотрят внутрь и Minecraft отсекает их как задние (вертикальные цилиндры были вывернуты).
        if len(R) > 1 and not caps:
            d = R[1].mean(0) - R[0].mean(0)
            nw = sum(np.cross(R[0][j], R[0][(j + 1) % n]) for j in range(n))
            if np.linalg.norm(d) > 1e-9 and np.dot(nw, d) > 0:
                R = [r[::-1].copy() for r in R]
        # длины для uv
        per = [np.r_[0, np.cumsum(np.linalg.norm(np.diff(np.vstack([r, r[:1]]), axis=0), axis=1))] for r in R]
        P = max(p[-1] for p in per)
        cen = [r.mean(0) for r in R]
        along = np.r_[0, np.cumsum([np.linalg.norm(cen[i + 1] - cen[i]) for i in range(len(R) - 1)])]
        L = max(along[-1], 1e-6)
        us = uscale or max(P, 1.0)
        vs = vscale or max(L, 1.0)
        # сглаженные нормали
        normals = []
        for i, r in enumerate(R):
            prev_r = R[max(0, i - 1)]; next_r = R[min(len(R) - 1, i + 1)]
            ax = next_r.mean(0) - prev_r.mean(0)
            if np.linalg.norm(ax) < 1e-9: ax = np.array([1.0, 0, 0])
            nn = []
            for j in range(n):
                tang = r[(j + 1) % n] - r[(j - 1) % n]
                v = np.cross(tang, ax)
                nn.append(v)
            normals.append(nn)
        for i in range(len(R) - 1):
            for j in range(n):
                j2 = (j + 1) % n
                p = [R[i][j], R[i + 1][j], R[i + 1][j2], R[i][j2]]
                u0, u1 = per[i][j] / us, (per[i][j] + np.linalg.norm(R[i][j2] - R[i][j])) / us
                v0, v1 = along[i] / vs, along[i + 1] / vs
                uv = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
                uv = [(min(max(a, 0), 1), min(max(b, 0), 1)) for a, b in uv]
                m = mats(i, j) if mats else mat
                if smooth:
                    N = [normals[i][j], normals[i + 1][j], normals[i + 1][j2], normals[i][j2]]
                    # нормали наружу: сверим с геометрической
                    g = np.cross(p[1] - p[0], p[3] - p[0])
                    if np.dot(g, N[0]) < 0: N = [-x for x in N]
                    self.quad(p, uv, m, N)
                else:
                    self.quad(p, uv, m)
        f0 = len(self.faces) - (len(R) - 1) * n
        if caps:
            cm = cap_mat or mat
            for r, flip in ((R[0], True), (R[-1], False)):
                c = r.mean(0)
                ext = np.ptp(r, axis=0); s = max(ext.max(), 1e-6)
                # проекция на плоскость кольца для uv
                for j in range(n):
                    a, b = r[j], r[(j + 1) % n]
                    tri = [c, a, b, b] if not flip else [c, b, a, a]
                    uv = []
                    for q in tri:
                        d = (q - c) / s + 0.5
                        idx = np.argsort(ext)[::-1][:2]
                        uv.append((min(max(d[idx[0]], 0), 1), min(max(d[idx[1]], 0), 1)))
                    self.quad(tri, uv, cm)
            # замкнутое тело: если объём со знаком отрицательный, грани вывернуты — переворачиваем обход
            if self._volume(f0) < 0:
                for k in range(f0, len(self.faces)):
                    mt, idx = self.faces[k]
                    self.faces[k] = (mt, idx[::-1])
        return self


def rrect(w, h, r, segs=3, cx=0.0, cy=0.0):
    """Скруглённый прямоугольник в плоскости (a,b): точки против часовой."""
    r = min(r, w / 2 - 1e-4, h / 2 - 1e-4)
    pts = []
    corners = [(w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)]
    for x, y, a0 in corners:
        for k in range(segs + 1):
            a = math.radians(a0 + 90 * k / segs)
            pts.append((cx + x + r * math.cos(a), cy + y + r * math.sin(a)))
    return pts


def circle(r, n=16, cx=0.0, cy=0.0, a0=0.0):
    return [(cx + r * math.cos(a0 + 2 * math.pi * k / n), cy + r * math.sin(a0 + 2 * math.pi * k / n)) for k in range(n)]


def ring_x(x, pts2d):
    """Кольцо в плоскости x = const: точки (z, y)."""
    return [(x, b, a) for a, b in pts2d]


def ring_y(y, pts2d):
    return [(a, y, b) for a, b in pts2d][::-1]


def ring_z(z, pts2d):
    return [(a, b, z) for a, b in pts2d][::-1]


def box(m, x0, y0, z0, x1, y1, z1, mat, r=0.0, axis='x', segs=2):
    """Брусок; при r>0 рёбра вдоль оси axis скруглены."""
    if axis == 'x':
        sec = rrect(z1 - z0, y1 - y0, r, segs, (z0 + z1) / 2, (y0 + y1) / 2) if r > 0 else \
            [(z1, y1), (z0, y1), (z0, y0), (z1, y0)]
        m.loft([ring_x(x0, sec), ring_x(x1, sec)], mat, smooth=r > 0)
    elif axis == 'y':
        sec = rrect(x1 - x0, z1 - z0, r, segs, (x0 + x1) / 2, (z0 + z1) / 2) if r > 0 else \
            [(x1, z1), (x0, z1), (x0, z0), (x1, z0)]
        m.loft([ring_y(y0, sec), ring_y(y1, sec)], mat, smooth=r > 0)
    else:
        sec = rrect(x1 - x0, y1 - y0, r, segs, (x0 + x1) / 2, (y0 + y1) / 2) if r > 0 else \
            [(x1, y1), (x0, y1), (x0, y0), (x1, y0)]
        m.loft([ring_z(z0, sec), ring_z(z1, sec)], mat, smooth=r > 0)
    return m


def cylinder(m, axis, c, r, a0, a1, mat, n=16, cap_mat=None, r1=None):
    """Цилиндр (или усечённый конус, если задан r1) вдоль axis от a0 до a1; c — центр в двух других осях."""
    r1 = r if r1 is None else r1
    if axis == 'y':
        m.loft([ring_y(a0, circle(r, n, c[0], c[1])), ring_y(a1, circle(r1, n, c[0], c[1]))], mat, cap_mat=cap_mat)
    elif axis == 'x':
        m.loft([ring_x(a0, circle(r, n, c[0], c[1])), ring_x(a1, circle(r1, n, c[0], c[1]))], mat, cap_mat=cap_mat)
    else:
        m.loft([ring_z(a0, circle(r, n, c[0], c[1])), ring_z(a1, circle(r1, n, c[0], c[1]))], mat, cap_mat=cap_mat)
    return m


def lathe(m, axis_c, profile, mat, n=20, mats=None):
    """Тело вращения вокруг вертикали: profile — [(радиус, y)], снизу вверх."""
    cx, cz = axis_c
    rings = [ring_y(y, circle(max(r, 1e-4), n, cx, cz)) for r, y in profile]
    m.loft(rings, mat, caps=True, mats=mats)
    return m


def torus(m, axis, c, R, r, mat, n=20, k=8):
    """Тор (покрышка): ось axis ('x' или 'z'), центр c=(x,y,z)."""
    c = np.asarray(c, float)
    rings = []
    for i in range(n + 1):
        a = 2 * math.pi * i / n
        ring = []
        for j in range(k):
            b = 2 * math.pi * j / k
            rr = R + r * math.cos(b)
            if axis == 'z':
                p = c + np.array([rr * math.cos(a), rr * math.sin(a), r * math.sin(b)])
            else:
                p = c + np.array([r * math.sin(b), rr * math.sin(a), rr * math.cos(a)])
            ring.append(p)
        rings.append(ring)
    m.loft(rings, mat, caps=False)
    return m


def wheel(m, c, R, width, axis='z', tire='tire', rim='rim', hub_r=None):
    """Колесо: покрышка-тор + диск."""
    r = width / 2
    torus(m, axis, c, R - r, r, tire, n=18, k=8)
    cc = np.asarray(c, float)
    hr = hub_r or (R - width * 0.95)
    if axis == 'z':
        cylinder(m, 'z', (cc[0], cc[1]), hr, cc[2] - r * 0.7, cc[2] + r * 0.7, rim, n=14)
    else:
        cylinder(m, 'x', (cc[2], cc[1]), hr, cc[0] - r * 0.7, cc[0] + r * 0.7, rim, n=14)
    return m


# ---------------------------------------------------------------- вывод
def fix_normals(mesh):
    """Нормаль каждой вершины грани — на ту же сторону, что и обход грани (вершины у граней не общие)."""
    for mat, idx in mesh.faces:
        P = [np.asarray(mesh.v[i[0] - 1]) for i in idx]
        g = np.cross(P[1] - P[0], P[2] - P[0])
        if np.linalg.norm(g) < 1e-12 and len(P) > 3:
            g = np.cross(P[2] - P[0], P[3] - P[0])
        for i in idx:
            n = np.asarray(mesh.vn[i[2] - 1])
            if np.dot(n, g) < 0:
                mesh.vn[i[2] - 1] = tuple(-n)


def write_obj(mesh, path, mtl_name):
    fix_normals(mesh)
    with open(path, 'w') as f:
        f.write('# Stalker Zone prop\nmtllib %s\n' % mtl_name)
        for p in mesh.v: f.write('v %.5f %.5f %.5f\n' % p)
        for t in mesh.vt: f.write('vt %.5f %.5f\n' % t)
        for n in mesh.vn: f.write('vn %.5f %.5f %.5f\n' % n)
        cur = None
        for mat, idx in sorted(mesh.faces, key=lambda fc: fc[0]):
            if mat != cur:
                f.write('usemtl %s\n' % mat); cur = mat
            f.write('f ' + ' '.join('%d/%d/%d' % i for i in idx) + '\n')


def write_mtl(path, materials):
    with open(path, 'w') as f:
        for m in materials:
            f.write('newmtl %s\nKd 1 1 1\nmap_Kd #%s\n\n' % (m, m))


def bounds(mesh):
    a = np.array(mesh.v)
    return a.min(0), a.max(0)


# ---------------------------------------------------------------- превью
def render(mesh, textures, view, size=(600, 400), bg=(160, 170, 175, 255), margin=0.08):
    """textures: материал -> numpy RGBA. Перспективы нет, только поворот."""
    from PIL import Image
    W, H = size
    img = np.zeros((H, W, 4), np.float32); img[:] = bg
    zb = np.full((H, W), -1e9, np.float32)
    V = np.array(mesh.v) @ view.T
    mn, mx = V.min(0), V.max(0)
    sc = min(W * (1 - 2 * margin) / max(mx[0] - mn[0], 1e-6), H * (1 - 2 * margin) / max(mx[1] - mn[1], 1e-6))
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
    VN = np.array(mesh.vn) @ view.T
    VT = np.array(mesh.vt)
    for mat, idx in mesh.faces:
        T = textures.get(mat)
        if T is None: continue
        th, tw = T.shape[:2]
        vi = [i[0] - 1 for i in idx]; ti = [i[1] - 1 for i in idx]; ni = [i[2] - 1 for i in idx]
        for tri in ((0, 1, 2), (0, 2, 3)) if len(idx) == 4 else ((0, 1, 2),):
            a = [vi[t] for t in tri]
            xs = (V[a, 0] - cx) * sc + W / 2; ys = H / 2 - (V[a, 1] - cy) * sc; zs = V[a, 2]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0: continue
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-12: continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            l2 = 1 - l0 - l1
            ins = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
            if not ins.any(): continue
            z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            m = ins & (z > sub)
            if not m.any(): continue
            tt = [ti[t] for t in tri]
            uu = l0 * VT[tt[0], 0] + l1 * VT[tt[1], 0] + l2 * VT[tt[2], 0]
            vv = l0 * VT[tt[0], 1] + l1 * VT[tt[1], 1] + l2 * VT[tt[2], 1]
            nn = [ni[t] for t in tri]
            nx = l0[..., None] * VN[nn[0]] + l1[..., None] * VN[nn[1]] + l2[..., None] * VN[nn[2]]
            nx /= np.linalg.norm(nx, axis=-1, keepdims=True) + 1e-9
            shade = 0.45 + 0.55 * np.abs(nx @ light)
            col = T[np.clip(((1 - vv) * th).astype(int), 0, th - 1), np.clip((uu * tw).astype(int), 0, tw - 1)].astype(np.float32)
            alpha = col[..., 3] > 20
            m = m & alpha
            sub[m] = z[m]
            c = col.copy(); c[..., :3] *= shade[..., None]
            img[y0:y1 + 1, x0:x1 + 1][m] = c[m]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')


def view(yaw, pitch):
    a, b = math.radians(yaw), math.radians(pitch)
    Ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    return Rx @ Ry
