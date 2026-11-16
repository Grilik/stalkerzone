# Э1-17: превью-лист контейнеров мода (текстуры 64 px и изометрия) в C:/MineStalker/PlayTest/Сундуки/. Запуск: python -I tools/containers/preview.py
import os, random, numpy as np
from PIL import Image, ImageDraw, ImageFont
OUT = "C:/MineStalker/PlayTest/Сундуки"
os.makedirs(OUT + "/textures", exist_ok=True)
F = lambda s: ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", s)
S = 64


def noise(img, amt, seed):
    r = np.random.default_rng(seed); a = np.asarray(img).astype(int)
    a[..., :3] += r.integers(-amt, amt + 1, a[..., :3].shape)
    return Image.fromarray(np.clip(a, 0, 255).astype("uint8"), "RGBA")


def planks(c, seed, vertical=False, worn=0):
    r = random.Random(seed); im = Image.new("RGBA", (S, S), c + (255,)); d = ImageDraw.Draw(im)
    for i in range(0, S, 16):
        k = r.randint(-14, 10); cc = tuple(max(0, min(255, v + k)) for v in c)
        box = (0, i, S, i + 15) if not vertical else (i, 0, i + 15, S)
        d.rectangle(box, fill=cc + (255,))
        for _ in range(5):
            y = i + r.randint(1, 14)
            g = tuple(v - 18 for v in cc) + (255,)
            d.line((y, 0, y, S) if vertical else (0, y, S, y), fill=g)
        d.line((i + 15, 0, i + 15, S) if vertical else (0, i + 15, S, i + 15), fill=(40, 28, 18, 255))
    for _ in range(worn):
        x, y = r.randint(0, 60), r.randint(0, 60); d.rectangle((x, y, x + r.randint(2, 6), y + 1), fill=(60, 50, 40, 255))
    return noise(im, 6, seed)


def frame(im, c, w=5):
    d = ImageDraw.Draw(im)
    for b in ((0, 0, S - 1, w), (0, S - 1 - w, S - 1, S - 1), (0, 0, w, S - 1), (S - 1 - w, 0, S - 1, S - 1)):
        d.rectangle(b, fill=c + (255,))
    d.rectangle((0, 0, S - 1, S - 1), outline=(30, 22, 14, 255))
    for x, y in ((2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4), (S // 2, 2), (S // 2, S - 4)):
        d.rectangle((x, y, x + 1, y + 1), fill=(70, 70, 75, 255))
    return im


def stencil(im, text, size, col, xy=None):
    d = ImageDraw.Draw(im); f = F(size)
    w = d.textlength(text, font=f)
    x, y = xy or ((S - w) / 2, S / 2 - size / 2 - 2)
    d.text((x, y), text, font=f, fill=col + (230,))
    return im


def metal(c, seed, rust=0, ribs=0):
    r = np.random.default_rng(seed); im = Image.new("RGBA", (S, S), c + (255,)); d = ImageDraw.Draw(im)
    for i in range(ribs):
        y = int((i + 1) * S / (ribs + 1))
        d.line((3, y, S - 4, y), fill=tuple(v + 25 for v in c) + (255,)); d.line((3, y + 1, S - 4, y + 1), fill=tuple(v - 30 for v in c) + (255,))
    d.rectangle((0, 0, S - 1, S - 1), outline=tuple(v - 45 for v in c) + (255,))
    im = noise(im, 5, seed); a = np.asarray(im).astype(int)
    if rust:
        m = np.kron(r.random((S // 4, S // 4)), np.ones((4, 4)))
        m = (m + r.random((S, S)) * 0.5) / 1.5; mask = m > (1 - rust)
        a[mask, :3] = (np.array([120, 62, 30]) + r.integers(-20, 20, (mask.sum(), 3)))
    return Image.fromarray(np.clip(a, 0, 255).astype("uint8"), "RGBA")


def handle(im, x, y, w=10, col=(35, 35, 38)):
    d = ImageDraw.Draw(im); d.rectangle((x, y, x + w, y + 2), fill=col + (255,))
    d.rectangle((x, y - 2, x + 1, y + 2), fill=col + (255,)); d.rectangle((x + w - 1, y - 2, x + w, y + 2), fill=col + (255,)); return im


def latch(im, x, y):
    ImageDraw.Draw(im).rectangle((x, y, x + 5, y + 8), fill=(85, 85, 88, 255), outline=(40, 40, 42, 255)); return im


def locker(c, seed, rust):
    im = metal(c, seed, rust=rust); d = ImageDraw.Draw(im)
    for y in range(6, 20, 3): d.line((16, y, 47, y), fill=(30, 34, 30, 255))
    d.line((S // 2, 0, S // 2, S), fill=tuple(v - 40 for v in c) + (255,)); handle(im, 26, 40, 4); handle(im, 34, 40, 4)
    d.rectangle((22, 26, 41, 31), fill=(220, 220, 210, 255)); stencil(im, "12", 6, (20, 20, 20), (28, 25)); return im


def office(c, seed, rust):
    im = metal(c, seed, rust=rust); d = ImageDraw.Draw(im)
    for i in range(3):
        y0 = 3 + i * 20; d.rectangle((3, y0, 60, y0 + 17), outline=tuple(v - 50 for v in c) + (255,))
        d.rectangle((26, y0 + 4, 37, y0 + 8), fill=(235, 235, 225, 255)); handle(im, 25, y0 + 12, 13, (60, 60, 62))
    return im


def safe(c, seed, rust):
    im = metal(c, seed, rust=rust); d = ImageDraw.Draw(im)
    d.rectangle((5, 5, 58, 58), outline=tuple(v - 35 for v in c) + (255,), width=2)
    d.ellipse((22, 18, 38, 34), fill=(60, 60, 62, 255), outline=(170, 170, 170, 255)); d.line((30, 19, 30, 24), fill=(200, 200, 200, 255))
    d.rectangle((42, 38, 46, 52), fill=(150, 150, 150, 255)); d.rectangle((2, 12, 4, 20), fill=(30, 30, 30, 255)); d.rectangle((2, 44, 4, 52), fill=(30, 30, 30, 255))
    return im


def variants():
    out = []
    wood, wd, fw, fd = (150, 112, 70), (112, 92, 70), (122, 88, 52), (90, 72, 55)
    out.append(("1A", "Деревянный ящик — обычный", (16, 16, 16), frame(planks(wood, 1), fw), frame(planks(wood, 2), fw), frame(planks(wood, 3, True), fw), "18 ячеек, 15 кг"))
    out.append(("1B", "Деревянный ящик — старый, серый", (16, 16, 16), frame(planks(wd, 4, worn=14), fd), frame(planks(wd, 5, worn=10), fd), frame(planks(wd, 6, True, worn=12), fd), "18 ячеек, 15 кг"))
    ol, fo = (88, 98, 58), (70, 80, 45)
    f = latch(latch(stencil(frame(planks(ol, 7), fo), "ВОГ-25", 11, (230, 230, 220)), 8, 2), 50, 2)
    out.append(("2A", "Армейский ящик — зелёный", (16, 12, 16), f, handle(frame(planks(ol, 8), fo), 26, 30, 12), stencil(frame(planks(ol, 9, True), fo), "★", 18, (230, 230, 220)), "18 ячеек, 20 кг"))
    ol2, fo2 = (70, 80, 52), (52, 60, 40)
    f = frame(planks(ol2, 10, worn=8), fo2); ImageDraw.Draw(f).rectangle((5, 26, 58, 34), fill=(160, 40, 35, 255)); stencil(f, "1 ШТ.", 9, (230, 225, 210), (6, 8)); latch(latch(f, 8, 2), 50, 2)
    out.append(("2B", "Армейский — красная полоса", (16, 12, 16), f, handle(frame(planks(ol2, 11, worn=6), fo2), 26, 30, 12), frame(planks(ol2, 12, True, worn=6), fo2), "18 ячеек, 20 кг"))
    f = stencil(stencil(frame(planks(wood, 13), fw), "5,45×39", 11, (25, 25, 25), (8, 14)), "НЕ КАНТОВАТЬ", 6, (25, 25, 25), (9, 40))
    out.append(("3A", "Ящик с маркировкой — патроны", (16, 14, 16), f, stencil(frame(planks(wood, 14), fw), "№ 0471", 10, (25, 25, 25)), frame(planks(wood, 15, True), fw), "18 ячеек, 15 кг"))
    f = stencil(stencil(frame(planks(wd, 16, worn=8), fd), "ОГНЕОПАСНО", 8, (170, 30, 25), (6, 12)), "СКЛАД №3", 10, (25, 25, 25), (9, 34))
    out.append(("3B", "Ящик с маркировкой — склад", (16, 14, 16), f, stencil(frame(planks(wd, 17, worn=8), fd), "X", 22, (25, 25, 25)), frame(planks(wd, 18, True, worn=8), fd), "18 ячеек, 15 кг"))
    gb, br = (88, 100, 104), (120, 90, 60)
    out.append(("4A", "Металл. ящик — серый, ржавчина", (16, 12, 16), handle(metal(gb, 19, 0.35, 2), 26, 8, 12), metal(gb, 20, 0.4, 2), metal(gb, 21, 0.3, 3), "27 ячеек, 30 кг"))
    out.append(("4B", "Металл. ящик — ржавый насквозь", (16, 12, 16), handle(metal(br, 22, 0.6, 2), 26, 8, 12), metal(br, 23, 0.65, 2), metal(br, 24, 0.55), "27 ячеек, 30 кг"))
    zn, zn2 = (120, 128, 112), (95, 110, 80)
    out.append(("5A", "Цинк патронный — серый", (10, 7, 12), stencil(metal(zn, 25, 0, 1), "7,62", 12, (230, 230, 220)), metal(zn, 26, 0, 1), handle(metal(zn, 27), 26, 30, 12), "6 ячеек, 3 кг"))
    out.append(("5B", "Цинк — зелёный, жёлтая надпись", (10, 7, 12), stencil(metal(zn2, 28, 0.12, 1), "5,45", 12, (240, 220, 80)), metal(zn2, 29, 0.1, 1), handle(metal(zn2, 30, 0.1), 26, 30, 12), "6 ячеек, 3 кг"))
    lk, lb = (96, 116, 100), (80, 100, 130)
    out.append(("6A", "Шкафчик раздевалки — зелёный", (16, 32, 12), locker(lk, 31, 0.08), metal(lk, 32, 0.08), metal(lk, 33, 0.08), "27 ячеек, 40 кг"))
    out.append(("6B", "Шкафчик — синий, облезлый", (16, 32, 12), locker(lb, 34, 0.25), metal(lb, 35, 0.25), metal(lb, 36, 0.2), "27 ячеек, 40 кг"))
    bg, gr = (178, 168, 140), (128, 130, 132)
    out.append(("7A", "Канцелярский шкаф — бежевый", (16, 16, 14), office(bg, 37, 0.05), metal(bg, 38, 0.05), metal(bg, 39, 0.05), "18 ячеек, 35 кг"))
    out.append(("7B", "Канцелярский шкаф — серый", (16, 16, 14), office(gr, 40, 0.15), metal(gr, 41, 0.15), metal(gr, 42, 0.1), "18 ячеек, 35 кг"))
    sg, sb = (50, 72, 58), (80, 66, 56)
    out.append(("8A", "Сейф — тёмно-зелёный", (14, 16, 14), safe(sg, 43, 0.05), metal(sg, 44, 0.05), metal(sg, 45, 0.05), "9 ячеек, 120 кг"))
    out.append(("8B", "Сейф — старый, коричневый", (14, 16, 14), safe(sb, 46, 0.3), metal(sb, 47, 0.3), metal(sb, 48, 0.25), "9 ячеек, 120 кг"))
    return out


def coeffs(dst, src):
    A = []
    for (x, y), (u, v) in zip(dst, src):
        A += [[x, y, 1, 0, 0, 0, -u * x, -u * y], [0, 0, 0, x, y, 1, -v * x, -v * y]]
    return np.linalg.solve(np.array(A, float), np.array(src, float).reshape(8)).tolist()


def face(canvas, tex, quad):
    w = tex.transform(canvas.size, Image.PERSPECTIVE, coeffs(quad, [(0, 0), (tex.width, 0), (tex.width, tex.height), (0, tex.height)]), Image.NEAREST)
    canvas.alpha_composite(w)


def shade(t, k):
    a = np.asarray(t).astype(float); a[..., :3] *= k; return Image.fromarray(np.clip(a, 0, 255).astype("uint8"), "RGBA")


def fit(t, a, b):
    """Грань a×b (1/16): центр текстуры; выше блока — текстура растягивается на 2 блока."""
    if b > 16:
        t = t.resize((S, S * 2), Image.NEAREST); b0, b1 = 0, S * 2
    else:
        b0, b1 = int(S * (16 - b) / 32), int(S * (16 + b) / 32)
    return t.crop((int(S * (16 - a) / 32), b0, int(S * (16 + a) / 32), b1))


def render(shape, fr, sd, tp, U=9):
    w, h, d = shape; c = Image.new("RGBA", (320, 350), (0, 0, 0, 0)); ox, oy = 160, 300 - (0 if h > 16 else 40)

    def P(x, y, z):
        return (ox + (x - z) * U * 0.866, oy + (x + z) * U * 0.5 - y * U)
    face(c, shade(fit(fr, w, h), 0.95), [P(0, h, d), P(w, h, d), P(w, 0, d), P(0, 0, d)])
    face(c, shade(fit(sd, d, h), 0.72), [P(w, h, d), P(w, h, 0), P(w, 0, 0), P(w, 0, d)])
    face(c, shade(fit(tp, w, d), 1.08), [P(0, h, 0), P(w, h, 0), P(w, h, d), P(0, h, d)])
    return c


vs = variants()
cols, TW, TH = 4, 330, 420
sheet = Image.new("RGBA", (cols * TW, (len(vs) + cols - 1) // cols * TH + 70), (38, 40, 38, 255))
dr = ImageDraw.Draw(sheet)
dr.text((20, 18), "Э1-17 — контейнеры мода: выберите A или B в каждой паре (или оба — тогда случайно по месту)", font=F(24), fill=(235, 235, 225))
for i, (code, name, shape, fr, sd, tp, cap) in enumerate(vs):
    for nm, t in (("front", fr), ("side", sd), ("top", tp)):
        t.save("%s/textures/%s_%s.png" % (OUT, code, nm))
    x, y = (i % cols) * TW, (i // cols) * TH + 70
    sheet.alpha_composite(render(shape, fr, sd, tp), (x + 5, y))
    dr.text((x + 14, y + 350), code + "  " + name, font=F(15), fill=(240, 240, 230))
    dr.text((x + 14, y + 374), "%s;  размер %d×%d×%d из 16" % ((cap,) + shape), font=F(13), fill=(190, 195, 185))
sheet.convert("RGB").save(OUT + "/preview.png")
print(len(vs), OUT + "/preview.png")
