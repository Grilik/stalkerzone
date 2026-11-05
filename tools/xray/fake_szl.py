"""Синтетический уровень l01_escape.szl для проверки на сервере без данных игры (П-02).

Холмистый рельеф 80–88 в рамке Кордона, кубики-«воксели» и деревья в деревне (их должна убрать площадка) и у
элеватора (там остаются). Данных GSC нет — файл можно класть в run/stalkerzone/levels/ в облаке и в CI.

python3 -I tools/xray/fake_szl.py run/stalkerzone/levels/l01_escape.szl
"""
import gzip, math, struct, sys

X0, Z0, W, D, YOFF = -83, -858, 689, 1197, 100


def hill(x, z):
    return 83 + 2.2 * math.sin(x / 9.0) + 1.6 * math.cos(z / 7.0) + 0.02 * x


out = bytearray(b'SZL1') + struct.pack('<5i', X0, Z0, W, D, YOFF)
g = bytearray()
for j in range(D):
    for i in range(W):
        g += struct.pack('<h', int(round(hill(X0 + i, Z0 + j) * 8)))
out += g
out += bytes([3]) * (W * D)                       # трава
out += struct.pack('<h', -8) * (W * D)            # воды нет
vox = []
for cx, cz in ((10, 0), (-20, 10), (30, 20), (200, -200)):   # три «дома» в деревне, один у элеватора
    for dx in range(6):
        for dz in range(6):
            for dy in range(4):
                x, z = cx + dx, cz + dz
                vox.append((x, int(math.floor(hill(x, z))) + 1 + dy, z, 18))
out += struct.pack('<i', len(vox))
for v in vox:
    out += struct.pack('<hhhB', *v)
out += struct.pack('<i', 0)                       # вырезов нет
trees = [(x, z) for x in range(-30, 50, 13) for z in range(-20, 30, 11)] + [(210, -180)]
out += struct.pack('<i', len(trees))
for x, z in trees:
    out += struct.pack('<hhhBBB', x, z, int(math.floor(hill(x, z))), 8, 3, 0)
with gzip.open(sys.argv[1], 'wb') as f:
    f.write(bytes(out))
print('ok', sys.argv[1], len(vox), 'вокселей', len(trees), 'деревьев')
