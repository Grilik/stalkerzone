# Э1-07: карта ролей мест и связей групп Кордона (рельеф из mosaic.npz съёмки, границы баз — выход base_layout.py) -> C:/MineStalker/PlayTest/Кордон/Роли-мест.png
import json, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont
W = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")).replace("\\", "/") + "/"
atlas = json.load(open(W + "src/main/resources/data/stalkerzone/atlas/locations/cordon.json", encoding="utf-8"))
M = np.load("C:/MineStalker/TheZone-work-survey/mosaic.npz")
ox, oz = M["origin"]
x0, z0, x1, z1 = -4780, 2830, -3740, 4440
sl = (slice(z0 - oz, z1 - oz), slice(x0 - ox, x1 - ox))
top, gr, tid = M["top_y"][sl].astype(float), M["ground_y"][sl].astype(float), M["top_id"][sl]
names = M["names"]
cat = np.zeros(len(names), np.uint8)
for i, n in enumerate(names):
    cat[i] = (1 if "water" in n else 2 if "leaves" in n or n.endswith("_log") else 3 if n in ("gravel", "dirt_path", "andesite", "coarse_dirt", "cobblestone") else
              4 if n in ("sand", "red_sand") else 0)
c = cat[tid]
rgb = np.zeros(top.shape + (3,), float)
rgb[:] = (118, 140, 84)
rgb[c == 2] = (62, 92, 52)
rgb[c == 3] = (150, 145, 130)
rgb[c == 4] = (210, 200, 150)
rgb[c == 1] = (60, 100, 150)
bld = (top - gr >= 3) & (c != 2)
rgb[bld] = (185, 180, 172)
# отмывка рельефа
g = np.where(gr < -500, np.nan, gr)
gx = np.nan_to_num(np.gradient(g, axis=1)); gz = np.nan_to_num(np.gradient(g, axis=0))
shade = np.clip(1 - 0.08 * (gx + gz), 0.7, 1.25)[..., None]
rgb = np.clip(rgb * shade, 0, 255)
rgb[np.isnan(g)] = (30, 30, 30)
im = Image.fromarray(rgb.astype(np.uint8))
K = 0.8
im = im.resize((int(im.width * K), int(im.height * K)), Image.LANCZOS)
LEG, TOP = 380, 64
canvas = Image.new("RGB", (im.width + LEG, im.height + TOP), (25, 25, 25))
canvas.paste(im, (0, TOP))
d = ImageDraw.Draw(canvas)
F = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 14)
f2 = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 12)
def P(x, z): return ((x - x0) * K, (z - z0) * K + TOP)
# границы баз Э1-14
for f in os.listdir("C:/MineStalker/PlayTest/Кордон/Базы"):
    if f.endswith(".json"):
        b = json.load(open("C:/MineStalker/PlayTest/Кордон/Базы/" + f, encoding="utf-8"))
        pts = [P(x, z) for x, z in b["area"]]
        if len(pts) >= 3:
            d.line(pts + [pts[0]], fill=(255, 230, 80), width=2)
ROLE = [
    ("cordon.base.village", "нейтральный хаб вольных сталкеров: торговля, ночлег, костёр, медпункт", "вольные; защищает Свиноферма (договор)", (235, 235, 235), "base"),
    ("cordon.trader.sidorovich", "торговец (в деревне)", "нейтрально", (240, 110, 210), "dot"),
    ("cordon.outpost.farm", "основная база одиночек (как в «Чистом небе»)", "одиночки — ведущая группа", (240, 150, 50), "base"),
    ("cordon.outpost.wagon", "малое укрепление (дорога)", "одиночки — Свиноферма (договор)", (240, 150, 50), "minor"),
    ("cordon.checkpoint.fence_hole", "малое укрепление (тайный проход)", "одиночки — Свиноферма (договор)", (240, 150, 50), "minor"),
    ("cordon.outpost.tunnel_south", "крупный аванпост: тоннель, пост юг", "проводники — Свиноферма (договор)", (240, 150, 50), "major"),
    ("cordon.outpost.tunnel_north", "крупный аванпост: тоннель, пост север", "проводники — Свиноферма (договор)", (240, 150, 50), "major"),
    ("cordon.base.south_post", "база военных + ворота Зоны", "военные — Южный блокпост", (230, 60, 60), "base"),
    ("cordon.outpost.bridge", "крупный аванпост (переход)", "военные — Южный блокпост (приказ)", (230, 60, 60), "major"),
    ("cordon.outpost.atp", "база банды", "бандиты — АТП", (175, 120, 235), "base"),
    ("cordon.base.far_post", "база банды + переход на Свалку", "бандиты — АТП (дань)", (175, 120, 235), "base"),
    ("cordon.outpost.elevator", "зона потенциала (детали, укрытие)", "—", (110, 210, 210), "zone"),
    ("cordon.landmark.ruins", "зона потенциала (стройматериалы)", "—", (110, 210, 210), "zone"),
]
objs = {o["id"]: o for o in atlas["objects"]}
for k, o in objs.items():
    if o["kind"] == "lair":
        px, pz = P(o["pos"][0], o["pos"][-1]); d.ellipse((px - 4, pz - 4, px + 4, pz + 4), fill=(200, 30, 30))
    elif o["kind"] == "field":
        px, pz = P(o["pos"][0], o["pos"][-1]); d.ellipse((px - 7, pz - 7, px + 7, pz + 7), outline=(250, 250, 90), width=2)
    elif o["kind"] == "exit":
        px, pz = P(o["pos"][0], o["pos"][-1]); d.rectangle((px - 4, pz - 4, px + 4, pz + 4), fill=(255, 255, 255))
        d.text((px + 7, pz - 7), o.get("name", ""), font=f2, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
LINKS = [("cordon.outpost.farm", "cordon.base.village", "договор (защита)", (235, 235, 235)),
         ("cordon.outpost.farm", "cordon.outpost.wagon", "договор", (240, 150, 50)), ("cordon.outpost.farm", "cordon.checkpoint.fence_hole", "договор", (240, 150, 50)),
         ("cordon.outpost.farm", "cordon.outpost.tunnel_south", "договор", (240, 150, 50)), ("cordon.outpost.farm", "cordon.outpost.tunnel_north", "договор", (240, 150, 50)),
         ("cordon.base.south_post", "cordon.outpost.bridge", "приказ", (230, 60, 60)), ("cordon.outpost.atp", "cordon.base.far_post", "дань", (175, 120, 235))]
for a_, b_, t, c in LINKS:
    pa, pb = P(objs[a_]["pos"][0], objs[a_]["pos"][-1]), P(objs[b_]["pos"][0], objs[b_]["pos"][-1])
    d.line([pa, pb], fill=c, width=2)
    mx, mz = (pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2
    d.text((mx + 4, mz - 6), t, font=f2, fill=c, stroke_width=2, stroke_fill=(0, 0, 0))
for i, (oid, role, grp, c, sh) in enumerate(ROLE):
    o = objs[oid]; px, pz = P(o["pos"][0], o["pos"][-1])
    if sh == "base": d.rectangle((px - 9, pz - 9, px + 9, pz + 9), outline=c, width=4)
    elif sh == "local": d.rectangle((px - 7, pz - 7, px + 7, pz + 7), outline=c, width=2)
    elif sh == "major": d.polygon([(px, pz - 10), (px + 10, pz), (px, pz + 10), (px - 10, pz)], outline=c, width=3)
    elif sh == "minor": d.polygon([(px, pz - 6), (px + 6, pz), (px, pz + 6), (px - 6, pz)], outline=c, width=2)
    elif sh == "zone": d.ellipse((px - 10, pz - 10, px + 10, pz + 10), outline=c, width=3)
    else: d.ellipse((px - 5, pz - 5, px + 5, pz + 5), fill=c)
    d.text((px + 12, pz - 8), str(i + 1), font=F, fill=(255, 255, 255), stroke_width=3, stroke_fill=(0, 0, 0))
d.text((8, 8), "Э1-07: роли мест и связи групп Кордона (решения 16–17.11) — на утверждение", font=F, fill=(240, 240, 230))
d.text((8, 30), "квадрат — база (толстый — основная, тонкий — малая), ромб — аванпост (крупный / малый), круг — зона потенциала;", font=f2, fill=(200, 200, 190))
d.text((8, 45), "жёлтая линия — граница базы (Э1-14), цветные линии — связи «ведущая → зависимая», красные точки — логова, жёлтые круги — аномальные поля, белые квадраты — выходы", font=f2, fill=(200, 200, 190))
y = TOP + 6
for i, (oid, role, grp, c, sh) in enumerate(ROLE):
    d.text((im.width + 10, y), "%d. %s" % (i + 1, objs[oid].get("name", oid)), font=F, fill=c)
    d.text((im.width + 26, y + 18), role, font=f2, fill=(235, 235, 225))
    d.text((im.width + 26, y + 33), grp, font=f2, fill=(170, 170, 160))
    y += 54
canvas.save("C:/MineStalker/PlayTest/Кордон/Роли-мест.png")
print(canvas.size)
