import sys
from PIL import Image
import geo, guns
tex = geo.build_atlas()
tex.save('/tmp/claude-0/atlas.png')
ids = sys.argv[1:] or list(guns.GUNS)
rows = []
for gid in ids:
    g = guns.make(gid)
    a = geo.render(g, tex, geo.side_view('left'), (900, 300), bg=(150, 150, 150, 255))
    b = geo.render(g, tex, geo.side_view('right', yaw=25, pitch=-20), (900, 300), bg=(150, 150, 150, 255))
    row = Image.new('RGBA', (1800, 300)); row.paste(a, (0, 0)); row.paste(b, (900, 0)); rows.append(row)
im = Image.new('RGBA', (1800, 300 * len(rows)))
for i, r in enumerate(rows): im.paste(r, (0, 300 * i))
im.save('/tmp/claude-0/guns_preview.png')
