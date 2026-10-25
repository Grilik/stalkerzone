"""Текстуры пропсов части 3 (v1.14): профлист ангаров, сетка-рабица, эмаль, жёлтая краска газопровода, гусеницы."""
import numpy as np
from PIL import Image, ImageDraw
from textures import S, noise, fbm, to_img, rusty, RUST


def gen_more(seed=23):
    rng = np.random.default_rng(seed)
    T = {}
    # профлист: оцинковка с волной, ржавые потёки и пятна
    a = rusty((150, 154, 150), rng, 0.4, 0.05, streaks=True, dirt=0.3)
    xx = np.arange(S)
    wave = 0.78 + 0.3 * (0.5 + 0.5 * np.sin(xx / S * 2 * np.pi * 8))
    T['corrugated'] = a * wave[None, :, None]
    # сетка-рабица: ромбы проволоки, остальное прозрачное (cutout)
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    step = 16
    for k in range(-S, 2 * S, step):
        d.line([(k, 0), (k + S, S)], fill=(128, 124, 116, 255), width=2)
        d.line([(k + S, 0), (k, S)], fill=(128, 124, 116, 255), width=2)
    arr = np.asarray(img, np.float32)
    rr = fbm(S, S, rng, (20, 6), (0.6, 0.4))
    m = np.clip((rr - 0.45) * 4, 0, 1)[..., None]
    arr[..., :3] = arr[..., :3] * (1 - m) + RUST * m
    T['chainlink'] = arr
    # эмаль (холодильник, ванна): белая, сколы до чёрного металла, ржавые края
    e = rusty((214, 212, 204), rng, 0.18, 0.35, streaks=True, dirt=0.25)
    T['enamel'] = e
    T['cast_iron'] = rusty((54, 54, 56), rng, 0.3, 0.0, streaks=False, dirt=0.1)
    # жёлтая краска газопровода с ржавчиной
    T['gas_yellow'] = rusty((206, 170, 46), rng, 0.3, 0.3, streaks=True, dirt=0.2)
    # гусеница: траки поперёк
    t = np.ones((S, S, 3), np.float32) * np.array([58, 54, 50], np.float32)
    for y in range(0, S, 12):
        t[y:y + 3] *= 0.45
        t[y + 5:y + 7, ::9] = (90, 80, 70)
    t *= (0.8 + 0.35 * fbm(S, S, rng))[..., None]
    r = fbm(S, S, rng, (24, 6), (0.6, 0.4))
    mm = np.clip((r - 0.5) * 3, 0, 1)[..., None]
    T['track'] = t * (1 - mm) + RUST * 0.8 * mm
    # краска детских площадок (облезлая)
    T['play_red'] = rusty((178, 48, 38), rng, 0.25, 0.45, streaks=True, dirt=0.2)
    T['play_blue'] = rusty((52, 96, 160), rng, 0.25, 0.45, streaks=True, dirt=0.2)
    T['play_yellow'] = rusty((214, 176, 54), rng, 0.25, 0.45, streaks=True, dirt=0.2)
    # мусорные мешки: чёрный полиэтилен с бликами
    b = np.ones((S, S, 3), np.float32) * 30
    b += 40 * np.clip(fbm(S, S, rng, (16, 5), (0.6, 0.4)) - 0.45, 0, 1)[..., None] * 3
    T['trash_bag'] = b
    # кирпич водонапорной башни: красный, закопчённый
    br = np.ones((S, S, 3), np.float32) * np.array([140, 64, 46], np.float32)
    for y in range(0, S, 8):
        br[y:y + 1] = (150, 146, 136)
        off = 0 if (y // 8) % 2 == 0 else 8
        br[y:y + 8, off::16] = (150, 146, 136)
    br *= (0.75 + 0.35 * fbm(S, S, rng))[..., None]
    soot = fbm(S, S, rng, (60, 20), (0.7, 0.3))
    br *= (1 - 0.4 * np.clip(soot - 0.4, 0, 1))[..., None]
    T['brick_red'] = br
    # дорожный указатель: синий щит, белая кайма и надписи (как на трассе к Чернобылю)
    from PIL import ImageFont
    try:
        fb = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
    except OSError:
        fb = ImageFont.load_default()
    img = Image.new('RGBA', (S, S), (28, 70, 150, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 3, S - 4, S - 4], outline=(235, 235, 230, 255), width=3)
    d.text((12, 16), "ПРИПЯТЬ", font=fb, fill=(240, 240, 235, 255))
    d.text((92, 16), "18", font=fb, fill=(240, 240, 235, 255))
    d.line([(10, 44), (S - 10, 44)], fill=(235, 235, 230, 255), width=2)
    d.text((12, 52), "ЧЕРНОБЫЛЬ", font=fb, fill=(240, 240, 235, 255))
    d.text((100, 52), "9", font=fb, fill=(240, 240, 235, 255))
    d.line([(10, 80), (S - 10, 80)], fill=(235, 235, 230, 255), width=2)
    d.text((12, 88), "ЧАЭС", font=fb, fill=(240, 240, 235, 255))
    d.text((92, 88), "22", font=fb, fill=(240, 240, 235, 255))
    a = np.asarray(img, np.float32)
    rr = noise(S, S, 3, rng) * noise(S, S, 12, rng)
    rmask = np.clip((rr - 0.3) * 5, 0, 1)[..., None]
    a[..., :3] = a[..., :3] * (0.8 + 0.25 * fbm(S, S, rng))[..., None] * (1 - rmask) + RUST * rmask
    T['sign_road'] = a
    return {k: to_img(v) for k, v in T.items()}
