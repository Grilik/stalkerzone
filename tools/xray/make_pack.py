"""Личный ресурспак «Зона: оригинал»: текстуры блоков Зоны из копии игры игрока (только для себя, не публиковать).
Запуск: python3 tools/xray/make_pack.py <папка с .dds (stalker_dump/textures)> <выход.zip> [размер, 64]"""
import sys, os, io, zipfile, json
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'zone_blocks'))
from materials import M
from PIL import Image
src, out = sys.argv[1], sys.argv[2]; S = int(sys.argv[3]) if len(sys.argv) > 3 else 64
z = zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED)
z.writestr('pack.mcmeta', json.dumps({"pack": {"pack_format": 15, "description": "Зона: текстуры из вашей копии S.T.A.L.K.E.R. (личное, не для публикации)"}}, ensure_ascii=False))
def png(im):
    b = io.BytesIO(); im.save(b, 'PNG'); return b.getvalue()
n = 0
for key, ru, kind, pat, col, gtex, extra in M:
    p = os.path.join(src, gtex + '.dds')
    if not os.path.exists(p): print('нет', gtex); continue
    im = Image.open(p).convert('RGBA')
    w, h = im.size
    base = f'assets/stalkerzone/textures/block/zm_{key}'
    if 'door' in extra:
        # дверь: верх и низ из двух половин высокой текстуры
        half = im.crop((0, 0, w, h // 2)).resize((S, S), Image.LANCZOS), im.crop((0, h // 2, w, h)).resize((S, S), Image.LANCZOS)
        z.writestr(base + '_door_top.png', png(half[0])); z.writestr(base + '_door_bottom.png', png(half[1]))
        sq = im.resize((S, S), Image.LANCZOS)
    else:
        m = min(w, h); sq = im.crop((0, 0, m, m)).resize((S, S), Image.LANCZOS)
    if kind != 'glass' and 'pane' not in extra: sq.putalpha(255)
    z.writestr(base + '.png', png(sq)); n += 1
z.close(); print(n, 'текстур ->', out)
