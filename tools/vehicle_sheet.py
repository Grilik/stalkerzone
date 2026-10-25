"""Лист техники: каждая машина крупно (целая / без стёкол и колёс / сгоревшая)."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw, ImageFont
import iso_tex
pack, dd, out = sys.argv[1:4]
iso_tex.VAN = os.path.join(pack, 'assets/minecraft/textures/block')
names = [('zhiguli', 'ВАЗ-2101 «Жигули»'), ('moskvich', '«Москвич-412»'), ('zaporozhets', 'ЗАЗ-968 «Запорожец»'), ('niva', 'ВАЗ-2121 «Нива»'),
         ('uaz', 'УАЗ-469'), ('raf', 'РАФ «Латвия», скорая'), ('laz', 'Автобус ЛАЗ-695'), ('paz', 'Автобус ПАЗ-672'),
         ('zil_fire', 'Пожарная АЦ-40 (ЗИЛ-130)'), ('zil', 'ЗИЛ-130 бортовой'), ('kamaz', 'КамАЗ-5320'), ('btr', 'БТР-70'), ('tractor', 'МТЗ-80 «Беларус»')]
dmg = int(sys.argv[4]) if len(sys.argv) > 4 else 0
cells = []
for k, ru in names:
    im = iso_tex.render(os.path.join(dd, f'veh_{k}_{dmg}.txt'), '/tmp/_v.png', 13, ru)
    cells.append(im.convert('RGB'))
W = max(c.width for c in cells); H = max(c.height for c in cells); cols = 4
sheet = Image.new('RGB', (cols * W, ((len(cells) + cols - 1) // cols) * H + 50), (24, 26, 22))
for i, c in enumerate(cells): sheet.paste(c, ((i % cols) * W, 50 + (i // cols) * H))
d = ImageDraw.Draw(sheet)
t = ['Советская техника Зоны: ржавая, но целая', 'Разграбленная: без стёкол и колёс', 'Сгоревшая'][dmg]
d.text((14, 10), t, fill=(240, 220, 150), font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26))
sheet.save(out); print(out, sheet.size)
