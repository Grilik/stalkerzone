import sys, numpy as np
from PIL import Image
import mesh, models, textures, textures2, models2, models3, textures3
T = textures.gen_all(); T.update(textures2.gen_extra()); T.update(textures3.gen_more())
TX = {k: np.asarray(v) for k, v in T.items()}
def mats(paint='red', paint2='white'):
    d = dict(TX); d['paint'] = TX['paint_' + paint]; d['paint2'] = TX['paint_' + paint2]; return d
names = sys.argv[1:]
tiles = []
for n in names:
    if n in models.VEHICLES:
        v = models.VEHICLES[n]; m = models.car(n, 0); M = mats(v[1], v[2])
    else:
        dmg = 0
        m = models.PROPS[n][0](); M = mats()
    tiles.append(mesh.render(m, M, mesh.view(-35, 22), (420, 300)))
W = 3; H = (len(tiles) + W - 1) // W
im = Image.new('RGBA', (420 * W, 300 * H), (90, 90, 90, 255))
for i, t in enumerate(tiles): im.paste(t, ((i % W) * 420, (i // W) * 300))
im.save('/tmp/claude-0/props_preview.png')
