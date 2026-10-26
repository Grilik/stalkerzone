"""v1.14: иконки болта и детекторов 16x16 (Отклик, Медведь, Велес, Сварог)."""
from PIL import Image, ImageDraw
import os
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/stalkerzone/textures/item')

def img():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))

def bolt():
    im = img(); d = ImageDraw.Draw(im)
    # шестигранная гайка и стержень болта наискось, с обрывком бинта
    for i in range(7):
        d.point((4 + i, 11 - i), fill=(150, 150, 145, 255)); d.point((5 + i, 11 - i), fill=(110, 110, 105, 255))
    d.polygon([(9, 2), (12, 2), (14, 4), (12, 7), (9, 7), (8, 4)], fill=(120, 118, 112, 255), outline=(70, 68, 64, 255))
    d.point((11, 4), fill=(40, 40, 40, 255))
    d.line([(3, 12), (6, 14)], fill=(220, 210, 190, 255)); d.line([(2, 13), (4, 15)], fill=(200, 60, 50, 255))
    return im

def box(body, edge, screen, kind):
    im = img(); d = ImageDraw.Draw(im)
    d.rounded_rectangle([3, 2, 12, 14], 2, fill=body, outline=edge)
    if kind == 'echo':
        d.ellipse([6, 4, 9, 7], fill=(40, 40, 40, 255)); d.point((7, 5), fill=(255, 60, 40, 255)); d.point((8, 5), fill=(255, 120, 90, 255))
        for y in (9, 11): d.line([(5, y), (10, y)], fill=edge)
        d.line([(10, 2), (12, 0)], fill=(60, 60, 60, 255))
    elif kind == 'bear':
        d.ellipse([4, 4, 11, 11], fill=(20, 30, 20, 255), outline=(10, 10, 10, 255))
        d.line([(7, 7), (10, 5)], fill=(90, 230, 90, 255)); d.point((7, 7), fill=(200, 255, 200, 255))
        d.point((5, 12), fill=(255, 200, 40, 255)); d.point((10, 12), fill=(255, 60, 40, 255))
    elif kind == 'veles':
        d.rectangle([4, 3, 11, 10], fill=(10, 35, 25, 255), outline=(5, 15, 10, 255))
        for x in range(5, 11, 2): d.line([(x, 4), (x, 9)], fill=(25, 70, 45, 255))
        for y in range(4, 10, 2): d.line([(5, y), (10, y)], fill=(25, 70, 45, 255))
        d.point((8, 6), fill=(120, 255, 160, 255)); d.point((6, 8), fill=(120, 255, 160, 255))
        d.line([(5, 12), (10, 12)], fill=(90, 90, 80, 255))
    else:
        d.rectangle([4, 3, 11, 11], fill=(15, 25, 45, 255), outline=(5, 10, 20, 255))
        d.ellipse([5, 4, 10, 10], outline=(70, 140, 230, 255)); d.ellipse([7, 6, 8, 8], outline=(110, 190, 255, 255))
        d.point((9, 5), fill=(255, 220, 90, 255)); d.point((6, 9), fill=(255, 80, 60, 255))
        d.line([(5, 13), (10, 13)], fill=(150, 150, 160, 255))
    return im

bolt().save(os.path.join(OUT, 'bolt.png'))
box((70, 70, 65, 255), (35, 35, 32, 255), None, 'echo').save(os.path.join(OUT, 'detector_echo.png'))
box((200, 160, 50, 255), (110, 85, 25, 255), None, 'bear').save(os.path.join(OUT, 'detector_bear.png'))
box((60, 64, 58, 255), (30, 32, 28, 255), None, 'veles').save(os.path.join(OUT, 'detector_veles.png'))
box((95, 105, 120, 255), (45, 50, 60, 255), None, 'svarog').save(os.path.join(OUT, 'detector_svarog.png'))
print('ok')
