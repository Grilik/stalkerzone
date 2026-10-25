"""Лист «уровни развития»: 4 рендера одного здания (0..3) в сетке 2×2.
python3 tools/level_sheet.py <pack> <dump_dir> <kind> <title> <out.png> [scale] [crop]"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw, ImageFont
import iso_tex

pack, dd, kind, title, out = sys.argv[1:6]
scale = int(sys.argv[6]) if len(sys.argv) > 6 else 8
crop = int(sys.argv[7]) if len(sys.argv) > 7 and sys.argv[7] != '-' else None
cut = tuple(map(int, sys.argv[8].split(','))) if len(sys.argv) > 8 else None  # y,rx,rz
iso_tex.VAN = os.path.join(pack, 'assets/minecraft/textures/block')
names = ['0 · руины', '1 · лагерь', '2 · обжитой', '3 · укреплённый']
imgs = [iso_tex.render(os.path.join(dd, f'lvl_{kind}_{i}.txt'), f'/tmp/_lvl{i}.png', scale, f'Уровень {names[i]}', crop, (cut[0], cut[1:]) if cut else None) for i in range(4)]
w = max(i.width for i in imgs); h = max(i.height for i in imgs)
sheet = Image.new('RGB', (w * 2 + 12, h * 2 + 64), (24, 26, 22))
for k, im in enumerate(imgs): sheet.paste(im.convert('RGB'), ((k % 2) * (w + 12), 52 + (k // 2) * (h + 12)))
d = ImageDraw.Draw(sheet)
d.text((14, 12), title, fill=(240, 220, 150), font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26))
sheet.save(out); print(out, sheet.size)
