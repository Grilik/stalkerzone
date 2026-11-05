"""Картинка площадки: сверху (высоты до/после, контур плато) и сбоку (профиль по линии z). П-02.
python3 -I tools/prefab/site_preview.py <szl> <после.txt> <out.png> x0 z0 x1 z1 z_профиля"""
import gzip, struct, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
try:
    FONT = ImageFont.truetype('DejaVuSans.ttf', 13)
except OSError:
    FONT = ImageFont.load_default()

szl, txt, png = sys.argv[1:4]
x0, z0, x1, z1, zp = map(int, sys.argv[4:9])
d = gzip.open(szl).read(); o = 4
X0, Z0, W, D, _ = struct.unpack_from('<5i', d, o); o += 20
G = np.floor(np.frombuffer(d, '<i2', W * D, o).reshape(D, W) / 8.0)
before = G[z0 - Z0:z1 - Z0 + 1, x0 - X0:x1 - X0 + 1]
after = np.zeros_like(before)
for line in open(txt):
    x, z, y = map(int, line.split()); after[z - z0, x - x0] = y
lo, hi = min(before.min(), after.min()), max(before.max(), after.max())
S = 4

def top(a, title):
    v = ((a - lo) / max(1, hi - lo) * 200 + 40).astype(np.uint8)
    img = Image.fromarray(np.stack([v * 0 + 60, v, v * 0 + 50], -1).astype(np.uint8)).resize((a.shape[1] * S, a.shape[0] * S), Image.NEAREST)
    dr = ImageDraw.Draw(img)
    for z in range(a.shape[0]):           # линии уровня — где меняется высота
        for x in range(a.shape[1] - 1):
            if a[z, x] != a[z, x + 1]: dr.line([((x + 1) * S, z * S), ((x + 1) * S, (z + 1) * S)], fill=(0, 0, 0))
    for z in range(a.shape[0] - 1):
        for x in range(a.shape[1]):
            if a[z, x] != a[z + 1, x]: dr.line([(x * S, (z + 1) * S), ((x + 1) * S, (z + 1) * S)], fill=(0, 0, 0))
    dr.line([(0, (zp - z0) * S), (a.shape[1] * S, (zp - z0) * S)], fill=(255, 60, 60))
    dr.text((4, 4), title, fill=(255, 255, 255), font=FONT)
    return img

a, b = top(before, 'до: исходная земля'), top(after, 'после: плато деревни (AUTO)')
H = 180
side = Image.new('RGB', (a.width * 2 + 10, H), (235, 240, 250))
ds = ImageDraw.Draw(side)
for i in range(before.shape[1]):
    yb = H - 10 - int((before[zp - z0, i] - lo + 2) * 10); ya = H - 10 - int((after[zp - z0, i] - lo + 2) * 10)
    ds.rectangle([i * S, yb, i * S + S - 1, H], fill=(160, 160, 160))
    ds.rectangle([a.width + 10 + i * S, ya, a.width + 10 + i * S + S - 1, H], fill=(70, 140, 60))
ds.text((4, 4), f'сбоку, z={zp}: до', fill=(0, 0, 0), font=FONT); ds.text((a.width + 14, 4), f'сбоку, z={zp}: после (1 блок = 10 px)', fill=(0, 0, 0), font=FONT)
out = Image.new('RGB', (a.width * 2 + 10, a.height + H + 10), (255, 255, 255))
out.paste(a, (0, 0)); out.paste(b, (a.width + 10, 0)); out.paste(side, (0, a.height + 10))
out.save(png); print('ok', png, 'высоты', lo, hi)
