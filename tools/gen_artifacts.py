"""v1.14: иконки 18 новых артефактов и двух контейнеров (16x16, в стиле прежних)."""
from PIL import Image, ImageDraw
import math, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/stalkerzone/textures/item')

def new(): return Image.new('RGBA', (16, 16), (0, 0, 0, 0))
def dk(c, k=0.55): return tuple(int(v * k) for v in c[:3]) + (255,)
def lt(c, k=0.5): return tuple(int(v + (255 - v) * k) for v in c[:3]) + (255,)

def orb(c, r=5, cx=8, cy=8, ring=None):
    im = new(); d = ImageDraw.Draw(im)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c + (255,), outline=dk(c))
    d.ellipse([cx - r + 2, cy - r + 2, cx - r + 4, cy - r + 4], fill=lt(c, 0.6))
    if ring: d.ellipse([cx - r - 2, cy - 2, cx + r + 2, cy + 2], outline=ring + (255,))
    return im

def star(c, core, n=4, R=7, r=2):
    im = new(); d = ImageDraw.Draw(im)
    pts = []
    for i in range(2 * n):
        a = math.pi * i / n - math.pi / 2; rr = R if i % 2 == 0 else r
        pts.append((8 + math.cos(a) * rr, 8 + math.sin(a) * rr))
    d.polygon(pts, fill=c + (255,), outline=dk(c)); d.ellipse([6, 6, 9, 9], fill=core + (255,))
    return im

def spiky(c, n=8, R=7, r=4):
    im = star(c, lt(c)[:3], n, R, r); return im

def medusa():
    im = new(); d = ImageDraw.Draw(im); c = (190, 120, 220)
    d.pieslice([2, 2, 13, 13], 180, 360, fill=c + (230,), outline=dk(c))
    for x in (4, 6, 8, 10, 12): d.line([(x, 8), (x + (1 if x % 4 else -1), 13)], fill=lt(c, 0.3))
    d.point((6, 5), fill=lt(c, 0.8)); return im

def goldfish():
    im = new(); d = ImageDraw.Draw(im); c = (235, 170, 40)
    d.ellipse([2, 5, 11, 11], fill=c + (255,), outline=dk(c)); d.polygon([(10, 8), (14, 4), (14, 12)], fill=lt(c, 0.2), outline=dk(c))
    d.point((4, 7), fill=(30, 30, 30, 255)); return im

def crystal(c):
    im = new(); d = ImageDraw.Draw(im)
    d.polygon([(8, 1), (12, 6), (10, 14), (6, 14), (4, 6)], fill=c + (255,), outline=dk(c))
    d.line([(8, 2), (8, 13)], fill=lt(c, 0.6)); return im

def flame():
    im = new(); d = ImageDraw.Draw(im)
    d.polygon([(8, 1), (12, 7), (12, 11), (8, 14), (4, 11), (4, 7), (6, 5)], fill=(240, 110, 30, 255), outline=(140, 50, 10, 255))
    d.polygon([(8, 6), (10, 10), (8, 13), (6, 10)], fill=(255, 220, 90, 255)); return im

def eye():
    im = new(); d = ImageDraw.Draw(im)
    d.ellipse([2, 4, 13, 11], fill=(235, 225, 210, 255), outline=(120, 80, 70, 255))
    d.ellipse([5, 5, 10, 10], fill=(150, 60, 40, 255)); d.ellipse([7, 7, 8, 8], fill=(20, 10, 10, 255)); d.point((6, 6), fill=(255, 255, 255, 255))
    return im

def beads():
    im = new(); d = ImageDraw.Draw(im)
    for i in range(7):
        a = math.pi * (0.15 + 0.7 * i / 6); x = 8 + math.cos(a) * 5.5; y = 4 + math.sin(a) * 8
        d.ellipse([x - 1.6, y - 1.6, x + 1.6, y + 1.6], fill=(220, 70, 60, 255) if i % 2 else (240, 200, 90, 255), outline=(90, 30, 20, 255))
    return im

def battery():
    im = new(); d = ImageDraw.Draw(im)
    d.rectangle([5, 3, 10, 13], fill=(120, 125, 130, 255), outline=(50, 50, 55, 255)); d.rectangle([7, 1, 8, 2], fill=(200, 160, 60, 255))
    d.line([(6, 8), (9, 6)], fill=(255, 230, 80, 255)); d.line([(6, 10), (9, 8)], fill=(255, 230, 80, 255)); return im

def empty():
    im = new(); d = ImageDraw.Draw(im); cu = (200, 120, 60)
    d.ellipse([2, 1, 13, 5], fill=cu + (255,), outline=dk(cu)); d.ellipse([2, 10, 13, 14], fill=cu + (255,), outline=dk(cu))
    for y in (6, 7, 8, 9): d.point((7 + (y % 2), y), fill=(200, 230, 255, 120))
    return im

def etak():
    im = new(); d = ImageDraw.Draw(im)
    d.polygon([(8, 2), (13, 5), (13, 11), (8, 14), (3, 11), (3, 5)], fill=(40, 60, 140, 255), outline=(15, 20, 60, 255))
    d.line([(8, 2), (8, 8), (13, 5)], fill=(90, 130, 230, 255)); d.line([(8, 8), (3, 5)], fill=(90, 130, 230, 255)); d.point((8, 8), fill=(200, 230, 255, 255))
    return im

def blob(c, alpha=255, shine=True):
    im = new(); d = ImageDraw.Draw(im)
    d.polygon([(4, 12), (2, 9), (3, 5), (7, 3), (11, 4), (14, 8), (12, 12), (8, 13)], fill=c + (alpha,), outline=dk(c))
    if shine: d.point((5, 6), fill=lt(c, 0.8)); d.point((6, 5), fill=lt(c, 0.6))
    return im

def bracelet():
    im = new(); d = ImageDraw.Draw(im); c = (220, 190, 80)
    d.ellipse([2, 4, 13, 12], outline=c + (255,), width=2); d.ellipse([3, 5, 12, 11], outline=dk(c))
    d.point((4, 6), fill=lt(c, 0.8)); return im

def black_spray():
    im = new(); d = ImageDraw.Draw(im)
    for (x, y, r) in [(7, 8, 4), (11, 5, 2), (4, 4, 1.5), (12, 11, 1.5)]:
        d.ellipse([x - r, y - r, x + r, y + r], fill=(20, 18, 25, 255), outline=(60, 50, 80, 255))
    d.point((6, 6), fill=(150, 130, 200, 255)); return im

def gravi():
    im = new(); d = ImageDraw.Draw(im)
    d.ellipse([2, 2, 13, 13], outline=(150, 150, 160, 255), width=2)
    for i in range(6):
        a = i * math.pi / 3; d.point((8 + math.cos(a) * 3, 8 + math.sin(a) * 3), fill=(210, 210, 230, 255))
    d.ellipse([7, 7, 8, 8], fill=(240, 240, 255, 255)); return im

def lead():
    im = new(); d = ImageDraw.Draw(im)
    d.rectangle([2, 4, 13, 14], fill=(95, 98, 105, 255), outline=(45, 47, 52, 255)); d.rectangle([2, 3, 13, 5], fill=(120, 124, 132, 255), outline=(45, 47, 52, 255))
    d.ellipse([5, 7, 10, 12], fill=(230, 200, 40, 255)); d.point((7, 9), fill=(20, 20, 20, 255))
    for a in (0, 2.09, 4.19): d.line([(7.5, 9.5), (7.5 + math.cos(a) * 2.3, 9.5 + math.sin(a) * 2.3)], fill=(20, 20, 20, 255))
    return im

def porcelain():
    im = new(); d = ImageDraw.Draw(im)
    d.rounded_rectangle([4, 3, 11, 14], 2, fill=(240, 240, 235, 255), outline=(140, 140, 150, 255)); d.rectangle([5, 1, 10, 3], fill=(200, 200, 205, 255), outline=(120, 120, 130, 255))
    d.line([(4, 9), (11, 9)], fill=(60, 90, 200, 255)); d.line([(4, 10), (11, 10)], fill=(60, 90, 200, 255)); return im

ICONS = {
    'artifact_medusa': medusa(), 'artifact_night_star': star((40, 50, 90), (120, 200, 255), 5, 7, 3),
    'artifact_gravi': gravi(), 'artifact_goldfish': goldfish(), 'artifact_crystal': crystal((90, 220, 230)),
    'artifact_flame': flame(), 'artifact_eye': eye(), 'artifact_mama_beads': beads(),
    'artifact_moonlight': orb((170, 210, 250), 4, ring=(220, 240, 255)), 'artifact_battery': battery(), 'artifact_empty': empty(),
    'artifact_etak': etak(), 'artifact_crystal_thorn': spiky((110, 220, 210), 6, 7, 3), 'artifact_slime': blob((90, 180, 70)),
    'artifact_urchin': spiky((70, 40, 60), 10, 7, 4), 'artifact_bracelet': bracelet(), 'artifact_black_spray': black_spray(),
    'artifact_witch_jelly': blob((120, 255, 120), 200), 'container_lead': lead(), 'container_porcelain': porcelain(),
}
for n, im in ICONS.items(): im.save(os.path.join(OUT, n + '.png'))
print(len(ICONS))
