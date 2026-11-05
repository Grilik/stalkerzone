"""Ортогональные виды постройки по дампу PrefabPreview: план сверху и вертикальный разрез.
python3 -I tools/prefab/ortho.py <dump.txt> <out.png> <title> [разрез z=N | x=N ...]
План: верхний блок колонки (цвет текстуры, тень по высоте), точки рабочих мест из <dump>.marks подписаны.
Разрез: срез на линии z=N (смотрим на север: x вправо, y вверх) или x=N (смотрим на запад: z вправо).
Координаты на осях — мировые."""
import sys, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import iso_tex as T  # noqa: E402
T.VAN = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../resourcepack/StalkerZone-Textures/assets/minecraft/textures/block')

S = 16  # пикселей на блок
COL = {'trader': (230, 60, 60), 'duty': (240, 160, 40), 'shelter': (70, 160, 240), 'guard': (250, 240, 80),
       'bed': (180, 120, 240), 'rest': (120, 220, 120), 'cook': (240, 120, 40), 'work': (200, 200, 200), 'patrol': (250, 250, 250)}


def color(st):
    n, props = T.parse(st)
    if n == 'air': return None
    top = T.faces_tex(n, props)[0]
    a = T.load(top)
    m = a[..., 3] > 10
    if not m.any(): return None
    c = a[..., :3][m].mean(axis=0)
    return tuple(int(v) for v in c)


def font(sz):
    try: return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', sz)
    except Exception: return None


def main():
    dump, out, title = sys.argv[1], sys.argv[2], sys.argv[3]
    cuts = sys.argv[4:]
    vox = {}
    for line in open(dump, encoding='utf-8'):
        p = line.split(' ', 3)
        if len(p) == 4: vox[(int(p[0]), int(p[1]), int(p[2]))] = p[3].strip()
    ox = oy = oz = 0
    marks = []
    if os.path.exists(dump + '.marks'):
        for line in open(dump + '.marks', encoding='utf-8'):
            p = line.split()
            if p[0] == '#': ox, oy, oz = int(p[2]), int(p[3]), int(p[4]); continue
            marks.append((p[0], int(p[1]) - ox, int(p[2]) - oy, int(p[3]) - oz))
    cache = {}

    def col(st):
        if st not in cache: cache[st] = color(st)
        return cache[st]

    xs = [k[0] for k in vox]; ys = [k[1] for k in vox]; zs = [k[2] for k in vox]
    X0, X1, Y0, Y1, Z0, Z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    panels = []
    # план
    W, H = (X1 - X0 + 1) * S, (Z1 - Z0 + 1) * S
    img = Image.new('RGB', (W, H), (40, 40, 40))
    px = img.load()
    topmap = {}
    for (x, y, z), st in vox.items():
        if col(st) is None: continue
        if (x, z) not in topmap or y > topmap[(x, z)][0]: topmap[(x, z)] = (y, st)
    for (x, z), (y, st) in topmap.items():
        c = col(st)
        k = 0.55 + 0.45 * (y - Y0) / max(1, Y1 - Y0)
        c = tuple(min(255, int(v * k)) for v in c)
        for i in range(S):
            for j in range(S): px[(x - X0) * S + i, (z - Z0) * S + j] = c
    d = ImageDraw.Draw(img)
    f = font(11)
    for kind, x, y, z in marks:
        cx, cz = (x - X0) * S + S // 2, (z - Z0) * S + S // 2
        d.ellipse([cx - 5, cz - 5, cx + 5, cz + 5], fill=COL.get(kind, (255, 255, 255)), outline=(0, 0, 0))
        d.text((cx + 7, cz - 7), kind, fill=(255, 255, 255), font=f, stroke_width=2, stroke_fill=(0, 0, 0))
    cuts = [c for c in cuts if not c.startswith('y=')]
    for cut in cuts:
        ax, v = cut.split('='); v = int(v) - (oz if ax == 'z' else ox)
        if ax == 'z': d.line([0, (v - Z0) * S + S // 2, W, (v - Z0) * S + S // 2], fill=(255, 80, 80), width=2)
        else: d.line([(v - X0) * S + S // 2, 0, (v - X0) * S + S // 2, H], fill=(255, 80, 80), width=2)
    panels.append(('план сверху (север вверху)', img, (X0 + ox, Z0 + oz), 'xz'))
    # разрезы
    for cut in cuts:
        ax, v = cut.split('=')
        wv = int(v)
        v = wv - (oz if ax == 'z' else ox)
        if ax == 'z':
            A0, A1 = X0, X1
            get = lambda a, y: vox.get((a, y, v))
        else:
            A0, A1 = Z0, Z1
            get = lambda a, y: vox.get((v, y, A1 - (a - A0)))
        W, H = (A1 - A0 + 1) * S, (Y1 - Y0 + 1) * S
        im = Image.new('RGB', (W, H), (150, 170, 190))
        p2 = im.load()
        for a in range(A0, A1 + 1):
            for y in range(Y0, Y1 + 1):
                st = get(a, y)
                if st is None: continue
                c = col(st)
                if c is None: continue
                for i in range(S):
                    for j in range(S): p2[(a - A0) * S + i, (Y1 - y) * S + j] = c
        dd = ImageDraw.Draw(im)
        for kind, x, y, z in marks:
            if (ax == 'z' and z == v) or (ax == 'x' and x == v):
                a = x if ax == 'z' else A1 - (z - A0)
                cx, cy = (a - A0) * S + S // 2, (Y1 - y) * S + S // 2
                dd.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=COL.get(kind, (255, 255, 255)), outline=(0, 0, 0))
                dd.text((cx + 7, cy - 7), kind, fill=(255, 255, 255), font=f, stroke_width=2, stroke_fill=(0, 0, 0))
        name = f'разрез {ax}={wv} (' + ('смотрим на север, восток справа' if ax == 'z' else 'смотрим на запад, север справа') + ')'
        panels.append((name, im, None, ax))
    # сборка листа
    pad, head = 12, 26
    Wt = max(p[1].width for p in panels) + 2 * pad
    Ht = 40 + sum(p[1].height + head + pad for p in panels)
    sheet = Image.new('RGB', (Wt, Ht), (28, 30, 26))
    dd = ImageDraw.Draw(sheet)
    dd.text((pad, 8), title, fill=(236, 214, 140), font=font(20))
    yy = 40
    for name, im, org, kind in panels:
        dd.text((pad, yy), name, fill=(220, 220, 200), font=font(14))
        sheet.paste(im, (pad, yy + head))
        if org:  # подписи мировых координат по краю плана
            for x in range(X0, X1 + 1):
                wx = x + ox
                if wx % 5 == 0: dd.text((pad + (x - X0) * S, yy + head - 12), str(wx), fill=(160, 160, 150), font=font(9))
            for z in range(Z0, Z1 + 1):
                wz = z + oz
                if wz % 5 == 0: dd.text((pad + 2, yy + head + (z - Z0) * S + 2), str(wz), fill=(255, 255, 255), font=font(9), stroke_width=1, stroke_fill=(0, 0, 0))
        yy += im.height + head + pad
    sheet.save(out)


if __name__ == '__main__':
    main()
