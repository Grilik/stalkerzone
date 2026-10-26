"""v1.14: текстуры новых мутантов и их частей перекраской имеющихся (сохраняя развёртку моделей)."""
from PIL import Image, ImageEnhance, ImageOps
import os
B = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/stalkerzone/textures')
E, I = os.path.join(B, 'entity/mutant'), os.path.join(B, 'item')

def tint(src, rgb, k=0.55, bright=1.0, contrast=1.0):
    im = Image.open(src).convert('RGBA')
    a = im.split()[3]
    g = ImageOps.grayscale(im.convert('RGB'))
    col = ImageOps.colorize(g, (0, 0, 0), rgb).convert('RGB')
    out = Image.blend(im.convert('RGB'), col, k)
    out = ImageEnhance.Brightness(out).enhance(bright)
    out = ImageEnhance.Contrast(out).enhance(contrast)
    out.putalpha(a)
    return out

jobs = [
    ('entity', 'blind_dog', 'pseudodog', (205, 150, 95), 0.6, 1.1, 1.0),
    ('entity', 'blind_dog', 'psydog', (150, 140, 190), 0.7, 0.95, 1.1),
    ('entity', 'blind_dog', 'tushkan', (110, 100, 90), 0.6, 0.8, 1.2),
    ('entity', 'snork', 'burer', (150, 105, 70), 0.65, 0.85, 1.15),
    ('entity', 'controller', 'poltergeist', (190, 215, 255), 0.7, 1.2, 0.9),
    ('entity', 'flesh', 'pseudogiant', (180, 140, 130), 0.55, 0.85, 1.25),
    ('item', 'dog_tail', 'pseudodog_tail', (205, 150, 95), 0.6, 1.1, 1.0),
    ('item', 'dog_tail', 'psydog_tail', (150, 140, 190), 0.7, 1.0, 1.1),
    ('item', 'snork_foot', 'tushkan_paw', (120, 105, 95), 0.6, 0.9, 1.1),
    ('item', 'controller_hand', 'burer_hand', (150, 105, 70), 0.65, 0.9, 1.1),
    ('item', 'flesh_eye', 'poltergeist_core', (150, 200, 255), 0.75, 1.2, 1.1),
    ('item', 'boar_hoof', 'pseudogiant_hand', (180, 140, 130), 0.55, 0.9, 1.2),
]
for kind, src, dst, rgb, k, br, ct in jobs:
    d = E if kind == 'entity' else I
    tint(os.path.join(d, src + '.png'), rgb, k, br, ct).save(os.path.join(d, dst + '.png'))
print(len(jobs))
