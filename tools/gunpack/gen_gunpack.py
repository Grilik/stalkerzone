"""Собирает ганпак TaCZ «Оружие Зоны» (пространство имён szguns).
Модели и текстуры свои; анимации, звуки, логика перезарядки и патроны берутся по ссылке из стандартного
пака TaCZ (tacz:...), поэтому кости моделей названы так же, как у образцов.

Запуск: python3 tools/gunpack/gen_gunpack.py  -> src/main/resources/szguns_pack/ и build/stalkerzone_guns.zip"""
import json, os, shutil, sys, zipfile
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import geo, guns, guns2

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'szguns_pack')
NS = 'szguns'

SOUNDS = {
    'ak47': {"shoot": "tacz:ak47/ak47_shoot", "shoot_3p": "tacz:ak47/ak47_shoot_3p", "reload_empty": "tacz:ak47/ak47_reload_empty",
             "reload_tactical": "tacz:ak47/ak47_reload_tactical", "inspect": "tacz:ak47/ak47_inspect", "inspect_empty": "tacz:ak47/ak47_inspect_empty",
             "draw": "tacz:ak47/ak47_draw", "put_away": "tacz:ak47/ak47_put_away", "silence": "tacz:ak47/ak47_silence", "silence_3p": "tacz:ak47/ak47_silence_3p"},
    'glock_17': {"draw": "tacz:glock_17/glock17_draw", "shoot": "tacz:glock_17/glock17_shoot", "shoot_3p": "tacz:glock_17/glock17_shoot_3p",
                 "silence": "tacz:glock_17/glock17_silence", "silence_3p": "tacz:glock_17/glock17_silence_3p"},
    'db_short': {"reload_empty": "tacz:db_short/db_short_reload_empty", "reload_tactical": "tacz:db_short/db_short_reload_tactical",
                 "inspect": "tacz:db_short/db_short_inspect", "inspect_empty": "tacz:db_short/db_short_inspect_empty",
                 "draw": "tacz:db_short/db_short_draw", "put_away": "tacz:db_short/db_short_put_away",
                 "shoot": "tacz:db_short/db_shoot", "shoot_3p": "tacz:db_short/db_shoot_3p"},
    'sks_tactical': {"shoot": "tacz:sks/sks_shoot", "shoot_3p": "tacz:sks/sks_shoot_3p", "silence": "tacz:sks/sks_silence", "silence_3p": "tacz:sks/sks_silence_3p"},
    'rpk': {"shoot": "tacz:rpk/rpk_shoot", "shoot_3p": "tacz:rpk/rpk_shoot_3p", "reload_empty": "tacz:rpk/rpk_reload_empty",
            "reload_tactical": "tacz:rpk/rpk_reload_tactical", "inspect": "tacz:rpk/rpk_inspect", "inspect_empty": "tacz:rpk/rpk_inspect_empty",
            "draw": "tacz:rpk/rpk_draw", "put_away": "tacz:rpk/rpk_put_away", "silence": "tacz:rpk/rpk_silence", "silence_3p": "tacz:rpk/rpk_silence_3p"},
}

SOUNDS['aug'] = {"shoot": "tacz:aug/aug_shoot", "shoot_3p": "tacz:aug/aug_shoot_3p", "silence": "tacz:aug/aug_silence", "silence_3p": "tacz:aug/aug_silence_3p",
                 "reload_empty": "tacz:aug/aug_reload_empty", "reload_tactical": "tacz:aug/aug_reload_tactical", "inspect": "tacz:aug/aug_inspect",
                 "inspect_empty": "tacz:aug/aug_inspect_empty", "draw": "tacz:aug/aug_draw", "put_away": "tacz:aug/aug_put_away"}
SOUNDS['m107'] = {"shoot": "tacz:m107/m107_shoot", "shoot_3p": "tacz:m107/m107_shoot_3p", "silence": "tacz:m107/m107_silence",
                  "silence_3p": "tacz:m107/m107_silence_3p", "draw": "tacz:m107/m107_draw", "put_away": "tacz:m107/m107_put_away"}
SOUNDS['db_long'] = {"reload_empty": "tacz:db_short/db_short_reload_empty", "reload_tactical": "tacz:db_short/db_short_reload_tactical",
                     "inspect": "tacz:db_short/db_short_inspect", "inspect_empty": "tacz:db_short/db_short_inspect_empty",
                     "draw": "tacz:db_short/db_short_draw", "put_away": "tacz:db_short/db_short_put_away",
                     "shoot": "tacz:db_long/dbl_shoot", "shoot_3p": "tacz:db_long/dbl_shoot_3p"}
# машины состояний анимаций: у части образцов своя, у остальных общая (rpk, aug, db_long — общая)
STATE = {'ak47': 'tacz:ak47_state_machine', 'glock_17': 'tacz:glock_17_state_machine', 'db_short': 'tacz:db_short_state_machine',
         'sks_tactical': 'tacz:sks_tactical_state_machine', 'rpk': 'tacz:default_state_machine', 'm107': 'tacz:m107_state_machine'}

# время перезарядки подогнано под длину стандартных анимаций образца
XMAG = {
    'ak47': dict(reload_feed=1.55, reload_cooldown=2.0, empty_feed=2.25, empty_cooldown=2.6),
    'glock_17': dict(reload_feed=1.05, reload_cooldown=1.5, empty_feed=1.63, empty_cooldown=1.88),
    'sks_tactical': dict(reload_feed=1.6, reload_cooldown=2.19, empty_feed=2.17, empty_cooldown=2.84),
}


def xmag_params(sk):
    p = dict(XMAG[sk])
    for i in (1, 2, 3):
        p[f'reload_xmag_{i}_feed'] = p['reload_feed'] + 0.1; p[f'reload_xmag_{i}_cooldown'] = p['reload_cooldown'] + 0.25
        p[f'empty_xmag_{i}_feed'] = p['empty_feed'] + 0.2; p[f'empty_xmag_{i}_cooldown'] = p['empty_cooldown'] + 0.25
    return p


def falloff(*pts):
    return [{"distance": d, "damage": v} for d, v in pts]


def recoil(pitch, yaw_l, yaw_r, settle=0.45):
    return {"pitch": [{"time": 0, "value": [pitch, pitch]}, {"time": 0.1, "value": [pitch, pitch]},
                      {"time": settle, "value": [-pitch * 0.25, -pitch * 0.25]}, {"time": settle + 0.15, "value": [0, 0]}],
            "yaw": [{"time": 0, "value": [yaw_l, yaw_r]}, {"time": settle * 0.8, "value": [0, 0]}]}


# ------------------------------------------------------------------ описание оружия
SPEC = {
    'ak74': dict(sk='ak47', type='rifle', sort=1, ammo='tacz:545x39', mag=30, xmag=[35, 40, 45], rpm=650, dmg=8.0,
                 fall=falloff((35, 8.0), (70, 6.5), ("infinite", 5.0)), speed=300, armor=0.3, head=1.6,
                 modes=['auto', 'semi'], weight=3.3, rec=recoil(0.55, -0.2, 0.16), inacc=dict(stand=4.0, move=4.6, sneak=2.4, lie=1.4, aim=0.12),
                 att=['scope', 'muzzle', 'stock', 'extended_mag'], tags=["#tacz:scope", "#tacz:muzzle", "#tacz:extended_mag", "#tacz:bayonet_ak", "#tacz:ammo_mod_no_he"],
                 bolt='closed_bolt', zoom=1.33, flash=0.7, rus=('АК-74', 'Основной автомат Зоны. Патрон 5,45×39, ровная отдача, держит грязь и мороз.'),
                 eng=('AK-74', 'The Zone workhorse. 5.45x39, soft recoil, shrugs off dirt and frost.'),
                 recipe=[('forge:ingots/iron', 30), ('minecraft:logs', 6), ('forge:dyes/red', 4)]),
    'aks74u': dict(sk='ak47', type='smg', sort=2, ammo='tacz:545x39', mag=30, xmag=[35, 40, 45], rpm=700, dmg=7.0,
                   fall=falloff((20, 7.0), (45, 5.5), ("infinite", 4.0)), speed=230, armor=0.25, head=1.5,
                   modes=['auto', 'semi'], weight=2.7, rec=recoil(0.7, -0.3, 0.25), inacc=dict(stand=4.5, move=5.0, sneak=2.8, lie=1.8, aim=0.2),
                   att=['scope', 'muzzle', 'extended_mag'], tags=["#tacz:scope", "#tacz:muzzle", "#tacz:extended_mag", "#tacz:ammo_mod_no_he"],
                   bolt='closed_bolt', zoom=1.25, flash=0.9, rus=('АКС-74У', '«Ксюха»: укороченный автомат со складным прикладом. Первое серьёзное оружие новичка.'),
                   eng=('AKS-74U', '"Ksyukha": a short carbine with a folding stock. A rookie\'s first serious gun.'),
                   recipe=[('forge:ingots/iron', 24), ('forge:dyes/red', 3)]),
    'pm': dict(sk='glock_17', type='pistol', sort=1, ammo='tacz:9mm', mag=8, xmag=[10, 12, 15], rpm=300, dmg=5.0,
               fall=falloff((15, 5.0), (35, 4.0), ("infinite", 3.0)), speed=140, armor=0.0, head=1.5,
               modes=['semi'], weight=0.8, rec=recoil(0.9, -0.6, 0.45, 0.3), inacc=dict(stand=2.2, move=2.7, sneak=1.6, lie=0.9, aim=0.25),
               att=['muzzle', 'extended_mag'], tags=["#tacz:muzzle", "#tacz:extended_mag"],
               bolt='closed_bolt', zoom=1.15, flash=0.4, rus=('ПМ', 'Пистолет Макарова. Слабый, зато всегда под рукой.'),
               eng=('PM', 'Makarov pistol. Weak, but always at hand.'), recipe=[('forge:ingots/iron', 10)]),
    'toz_obrez': dict(sk='db_short', type='shotgun', sort=1, ammo='tacz:12g', mag=2, rpm=150, dmg=18.0, pellets=12,
                      fall=falloff((5, 18.0), (10, 12.0), ("infinite", 7.0)), speed=110, armor=0.25, head=1.25,
                      modes=['semi'], weight=2.2, rec={"pitch": [{"time": 0, "value": [3.4, 3.4]}, {"time": 0.3, "value": [3.4, 3.4]}, {"time": 0.5, "value": [-1.0, -1.0]}, {"time": 0.7, "value": [0, 0]}],
                                                        "yaw": [{"time": 0, "value": [-1.4, -1.4]}, {"time": 0.3, "value": [-1.4, -1.4]}, {"time": 0.7, "value": [0, 0]}]},
                      inacc=dict(stand=4.5, move=4.8, sneak=4.0, lie=4.0, aim=3.8), att=[], tags=[],
                      bolt='open_bolt', zoom=1.1, flash=0.9, rus=('Обрез ТОЗ-34', 'Двустволка с отпиленными стволами и прикладом. В упор не прощает.'),
                      eng=('Sawn-off TOZ-34', 'A double barrel with the barrels and stock sawn off. Unforgiving up close.'),
                      recipe=[('forge:ingots/iron', 14), ('minecraft:logs', 4)]),
    'svd': dict(sk='sks_tactical', type='sniper', sort=1, ammo='tacz:762x54', mag=10, xmag=[15, 20, 20], rpm=300, dmg=16.0,
                fall=falloff((80, 16.0), (160, 13.0), ("infinite", 10.0)), speed=420, armor=0.45, head=2.2,
                modes=['semi'], weight=4.3, rec=recoil(1.4, -0.5, 0.5, 0.5), inacc=dict(stand=7.0, move=8.5, sneak=6.0, lie=4.0, aim=0.05),
                att=['scope', 'muzzle'], tags=["#tacz:scope", "#tacz:muzzle"],
                bolt='closed_bolt', zoom=1.5, flash=0.9, rus=('СВД', 'Снайперская винтовка Драгунова. Валит кровососа с одного попадания в голову.'),
                eng=('SVD', 'Dragunov sniper rifle. One headshot drops a bloodsucker.'),
                recipe=[('forge:ingots/iron', 36), ('minecraft:logs', 8), ('forge:glass_panes', 2)]),
    'pkm': dict(sk='rpk', type='mg', sort=1, ammo='tacz:762x54', mag=100, xmag=[100, 100, 100], rpm=650, dmg=11.0,
                fall=falloff((45, 11.0), (90, 9.0), ("infinite", 7.0)), speed=330, armor=0.4, head=1.5,
                modes=['auto'], weight=7.5, rec=recoil(0.6, -0.4, 0.35, 0.5), inacc=dict(stand=5.0, move=6.5, sneak=3.5, lie=1.6, aim=0.3),
                att=['scope'], tags=["#tacz:scope"],
                bolt='closed_bolt', zoom=1.25, flash=0.8, rus=('ПКМ', 'Пулемёт Калашникова. Лента на сотню патронов: у Долга и военных без него ни шагу.'),
                eng=('PKM', 'Kalashnikov machine gun. A hundred-round belt; Duty and the military never go without one.'),
                recipe=[('forge:ingots/iron', 48), ('minecraft:logs', 6), ('forge:storage_blocks/iron', 1)]),
}


SPEC.update({
    'akm': dict(sk='ak47', type='rifle', sort=3, ammo='tacz:762x39', mag=30, xmag=[35, 40, 45], rpm=600, dmg=9.5,
                fall=falloff((35, 9.5), (70, 7.5), ("infinite", 5.5)), speed=280, armor=0.35, head=1.6,
                modes=['auto', 'semi'], weight=3.6, rec=recoil(0.75, -0.3, 0.25), inacc=dict(stand=4.3, move=5.0, sneak=2.6, lie=1.6, aim=0.15),
                att=['scope', 'muzzle', 'stock', 'extended_mag'], tags=["#tacz:scope", "#tacz:muzzle", "#tacz:extended_mag", "#tacz:ammo_mod_no_he"],
                bolt='closed_bolt', zoom=1.33, flash=0.85, rus=('АКМ', 'Модернизированный Калашников под 7,62×39: дерево, косой компенсатор, бьёт сильно, но подбрасывает.'),
                eng=('AKM', 'Modernised Kalashnikov in 7.62x39: wood furniture, slant brake, hits hard but climbs.'),
                recipe=[('forge:ingots/iron', 30), ('minecraft:logs', 8)]),
    'aks74': dict(sk='ak47', type='rifle', sort=4, ammo='tacz:545x39', mag=30, xmag=[35, 40, 45], rpm=650, dmg=7.8,
                  fall=falloff((35, 7.8), (70, 6.3), ("infinite", 4.8)), speed=300, armor=0.3, head=1.6,
                  modes=['auto', 'semi'], weight=3.0, rec=recoil(0.6, -0.22, 0.18), inacc=dict(stand=4.2, move=4.8, sneak=2.5, lie=1.5, aim=0.13),
                  att=['scope', 'muzzle', 'extended_mag'], tags=["#tacz:scope", "#tacz:muzzle", "#tacz:extended_mag", "#tacz:ammo_mod_no_he"],
                  bolt='closed_bolt', zoom=1.33, flash=0.7, rus=('АКС-74', 'АК-74 со складным треугольным прикладом: десантный вариант, легче в рейде.'),
                  eng=('AKS-74', 'AK-74 with a folding triangular stock: the paratrooper version, lighter on a raid.'),
                  recipe=[('forge:ingots/iron', 30), ('forge:dyes/red', 4)]),
    'an94': dict(sk='ak47', type='rifle', sort=5, ammo='tacz:545x39', mag=30, xmag=[35, 40, 45], rpm=600, dmg=8.2,
                 fall=falloff((40, 8.2), (80, 6.8), ("infinite", 5.2)), speed=320, armor=0.35, head=1.7,
                 modes=['auto', 'burst', 'semi'], burst=dict(continuous_shoot=False, count=2, bpm=1800, min_interval=0.25),
                 weight=3.85, rec=recoil(0.45, -0.15, 0.12), inacc=dict(stand=3.6, move=4.2, sneak=2.1, lie=1.2, aim=0.08),
                 att=['scope', 'muzzle', 'extended_mag'], tags=["#tacz:scope", "#tacz:muzzle", "#tacz:extended_mag", "#tacz:ammo_mod_no_he"],
                 bolt='closed_bolt', zoom=1.4, flash=0.6, rus=('АН-94 «Абакан»', 'Автомат Никонова: очередь по два выстрела почти в одну точку. Мечта штурмовика.'),
                 eng=('AN-94 "Abakan"', 'Nikonov rifle: a two-round burst lands almost in one hole. An assaulter\'s dream.'),
                 recipe=[('forge:ingots/iron', 36), ('forge:dyes/black', 4)]),
    'vss': dict(sk='ak47', type='sniper', sort=6, ammo='szguns:9x39', mag=20, xmag=[20, 30, 30], rpm=700, dmg=9.0,
                fall=falloff((60, 9.0), (110, 7.0), ("infinite", 5.0)), speed=290, armor=0.5, head=2.0,
                modes=['semi', 'auto'], weight=2.6, rec=recoil(0.55, -0.2, 0.2), inacc=dict(stand=4.0, move=4.8, sneak=2.4, lie=1.4, aim=0.05),
                att=['scope', 'extended_mag'], tags=["#tacz:scope", "#tacz:extended_mag"], silenced=True,
                bolt='closed_bolt', zoom=1.5, flash=0.15, rus=('ВСС «Винторез»', 'Бесшумная снайперская винтовка под 9×39. Тихо, точно, пробивает бронежилет.'),
                eng=('VSS "Vintorez"', 'Silent sniper rifle in 9x39. Quiet, accurate, goes through body armour.'),
                recipe=[('forge:ingots/iron', 34), ('minecraft:logs', 6), ('forge:glass_panes', 2)]),
    'groza': dict(sk='aug', type='rifle', sort=7, ammo='szguns:9x39', mag=20, xmag=[20, 30, 30], rpm=700, dmg=9.0,
                  fall=falloff((30, 9.0), (60, 7.0), ("infinite", 5.0)), speed=280, armor=0.45, head=1.7,
                  modes=['auto', 'semi'], weight=3.0, rec=recoil(0.65, -0.25, 0.22), inacc=dict(stand=3.8, move=4.4, sneak=2.3, lie=1.4, aim=0.1),
                  att=['scope', 'muzzle', 'extended_mag'], tags=["#tacz:scope", "#tacz:muzzle", "#tacz:extended_mag"],
                  bolt='closed_bolt', zoom=1.35, flash=0.7, rus=('ОЦ-14 «Гроза»', 'Буллпап под 9×39 на базе АКС-74У: коротко, тяжело бьёт, в бункерах лабораторий незаменима.'),
                  eng=('OTs-14 "Groza"', 'A 9x39 bullpup built on the AKS-74U: short and hard-hitting, perfect for lab bunkers.'),
                  recipe=[('forge:ingots/iron', 32), ('forge:dyes/black', 4)]),
    'pb': dict(sk='glock_17', type='pistol', sort=2, ammo='szguns:9x18', mag=8, xmag=[10, 12, 15], rpm=300, dmg=4.6,
               fall=falloff((15, 4.6), (30, 3.6), ("infinite", 2.6)), speed=130, armor=0.0, head=1.6,
               modes=['semi'], weight=1.0, rec=recoil(0.7, -0.5, 0.4, 0.3), inacc=dict(stand=2.0, move=2.5, sneak=1.5, lie=0.8, aim=0.2),
               att=['extended_mag'], tags=["#tacz:extended_mag"], silenced=True,
               bolt='closed_bolt', zoom=1.15, flash=0.1, rus=('ПБ', 'Бесшумный пистолет на базе ПМ. Тихо снять часового — то, что нужно.'),
               eng=('PB', 'Silenced pistol built on the PM. Just right for a quiet sentry.'), recipe=[('forge:ingots/iron', 14)]),
    'fort12': dict(sk='glock_17', type='pistol', sort=3, ammo='szguns:9x18', mag=12, xmag=[15, 18, 20], rpm=320, dmg=5.0,
                   fall=falloff((18, 5.0), (35, 4.0), ("infinite", 3.0)), speed=150, armor=0.0, head=1.5,
                   modes=['semi'], weight=0.95, rec=recoil(0.85, -0.55, 0.45, 0.3), inacc=dict(stand=2.0, move=2.5, sneak=1.5, lie=0.8, aim=0.2),
                   att=['muzzle', 'extended_mag'], tags=["#tacz:muzzle", "#tacz:extended_mag"],
                   bolt='closed_bolt', zoom=1.15, flash=0.4, rus=('«Форт-12»', 'Украинский пистолет под 9×18: двенадцать патронов, надёжен, у армии и милиции на каждом поясе.'),
                   eng=('Fort-12', 'Ukrainian 9x18 pistol: twelve rounds, reliable, on every army and police belt.'), recipe=[('forge:ingots/iron', 12)]),
    'aps': dict(sk='glock_17', type='pistol', sort=4, ammo='szguns:9x18', mag=20, xmag=[20, 25, 30], rpm=750, dmg=4.8,
                fall=falloff((18, 4.8), (35, 3.8), ("infinite", 2.8)), speed=150, armor=0.0, head=1.5,
                modes=['auto', 'semi'], weight=1.2, rec=recoil(0.8, -0.6, 0.5, 0.25), inacc=dict(stand=2.4, move=2.9, sneak=1.8, lie=1.0, aim=0.3),
                att=['extended_mag'], tags=["#tacz:extended_mag"],
                bolt='closed_bolt', zoom=1.15, flash=0.45, rus=('АПС', 'Автоматический пистолет Стечкина: двадцать патронов и очередь в упор.'),
                eng=('APS', 'Stechkin automatic pistol: twenty rounds and full auto up close.'), recipe=[('forge:ingots/iron', 16)]),
    'gauss': dict(sk='m107', type='sniper', sort=9, ammo='szguns:gauss', mag=10, xmag=[10, 10, 10], rpm=50, dmg=60.0,
                  fall=falloff((200, 60.0), ("infinite", 50.0)), speed=900, armor=0.9, head=2.0,
                  modes=['semi'], weight=8.0, rec=recoil(2.2, -0.6, 0.6, 0.6), inacc=dict(stand=9.0, move=11.0, sneak=7.0, lie=4.0, aim=0.02),
                  att=['scope'], tags=["#tacz:scope"], pierce=6,
                  bolt='closed_bolt', zoom=1.6, flash=1.4, rus=('Гаусс-винтовка', 'Экспериментальный ускоритель с артефактом в сердечнике. Прошивает экзоскелет и стену за ним.'),
                  eng=('Gauss Rifle', 'Experimental accelerator with an artefact in its core. Punches through an exoskeleton and the wall behind it.'),
                  recipe=[('forge:ingots/iron', 40), ('forge:ingots/gold', 12), ('minecraft:redstone_block', 4)]),
    'toz34': dict(sk='db_long', type='shotgun', sort=2, ammo='tacz:12g', mag=2, rpm=150, dmg=19.5, pellets=12,
                  fall=falloff((8, 19.5), (16, 13.0), ("infinite", 7.5)), speed=130, armor=0.25, head=1.25,
                  modes=['semi'], weight=3.3, rec={"pitch": [{"time": 0, "value": [3.0, 3.0]}, {"time": 0.3, "value": [3.0, 3.0]}, {"time": 0.5, "value": [-0.9, -0.9]}, {"time": 0.7, "value": [0, 0]}],
                                                   "yaw": [{"time": 0, "value": [-1.2, -1.2]}, {"time": 0.3, "value": [-1.2, -1.2]}, {"time": 0.7, "value": [0, 0]}]},
                  inacc=dict(stand=3.8, move=4.2, sneak=3.4, lie=3.4, aim=3.0), att=[], tags=[],
                  bolt='open_bolt', zoom=1.1, flash=0.9, rus=('ТОЗ-34', 'Охотничья двустволка: у одиночек с Кордона это первое ружьё и последний аргумент.'),
                  eng=('TOZ-34', 'A hunting double barrel: the first gun and last argument of every Cordon loner.'),
                  recipe=[('forge:ingots/iron', 18), ('minecraft:logs', 8)]),
})
# у ПМ теперь свой калибр 9×18
SPEC['pm']['ammo'] = 'szguns:9x18'

RELOAD = {
    'ak47': {"type": "magazine", "infinite": False, "feed": {"empty": 2.25, "tactical": 1.55}, "cooldown": {"empty": 2.6, "tactical": 2.0}},
    'glock_17': {"type": "magazine", "feed": {"empty": 1.63, "tactical": 1.05}, "cooldown": {"empty": 1.88, "tactical": 1.5}},
    'db_short': {"type": "magazine", "feed": {"empty": 1.83, "tactical": 1.23}, "cooldown": {"empty": 2.3, "tactical": 1.6}},
    'sks_tactical': {"type": "magazine", "feed": {"empty": 2.17, "tactical": 1.6}, "cooldown": {"empty": 2.84, "tactical": 2.19}},
    'rpk': {"type": "magazine", "feed": {"empty": 2.95, "tactical": 1.95}, "cooldown": {"empty": 3.69, "tactical": 2.77}},
    'aug': {"type": "magazine", "feed": {"empty": 2.63, "tactical": 1.42}, "cooldown": {"empty": 3, "tactical": 2.25}},
    'm107': {"type": "magazine", "feed": {"empty": 4.3, "tactical": 3.28}, "cooldown": {"empty": 5.25, "tactical": 4.35}},
    'db_long': {"type": "magazine", "feed": {"empty": 1.83, "tactical": 1.23}, "cooldown": {"empty": 2.3, "tactical": 1.6}},
}
TIMES = {'ak47': (0.35, 0.57), 'glock_17': (0.32, 0.35), 'db_short': (0.27, 0.2), 'sks_tactical': (0.48, 0.47), 'rpk': (0.67, 0.6),
         'aug': (0.43, 0.33), 'm107': (1.3, 0.87), 'db_long': (0.27, 0.23)}
ANIM = {'ak47': 'ak47', 'glock_17': 'glock_17', 'db_short': 'db_short', 'sks_tactical': 'sks_tactical', 'rpk': 'rpk',
        'aug': 'aug', 'm107': 'm107', 'db_long': 'db_long'}
SHOW = {
    'ak47': ({"pos": [1, 20, 3.5], "rotate": [0, 0, 60], "scale": [0.5, 0.5, 0.5]}, {"pos": [-1, 20, 3], "rotate": [-180, 0, 120], "scale": [0.5, 0.5, 0.5]}),
    'glock_17': ({"pos": [4, 13, -1], "rotate": [-90, -35, 90], "scale": [0.5, 0.5, 0.5]}, {"pos": [-5, 13, -1], "rotate": [-90, -35, 90], "scale": [0.5, 0.5, 0.5]}),
    'db_short': ({"pos": [1, 22, 4], "rotate": [0, 0, 60], "scale": [0.5, 0.5, 0.5]}, {"pos": [-1, 22, 3], "rotate": [-180, 0, 120], "scale": [0.5, 0.5, 0.5]}),
    'sks_tactical': ({"pos": [0, 19, 4], "rotate": [0, 0, 60], "scale": [0.5, 0.5, 0.5]}, {"pos": [0, 19, 3], "rotate": [-180, 0, 120], "scale": [0.5, 0.5, 0.5]}),
    'rpk': ({"pos": [1, 20, 3.5], "rotate": [0, 0, 60], "scale": [0.5, 0.5, 0.5]}, {"pos": [-1, 20, 3], "rotate": [-180, 0, 120], "scale": [0.5, 0.5, 0.5]}),
    'aug': ({"pos": [1, 19, 4], "rotate": [0, 0, 50], "scale": [0.5, 0.5, 0.5]}, {"pos": [-1, 19, 3], "rotate": [-180, 0, 130], "scale": [0.5, 0.5, 0.5]}),
    'm107': ({"pos": [0, 20, 4], "rotate": [0, 0, 30], "scale": [0.5, 0.5, 0.5]}, {"pos": [-4, 24, 3], "rotate": [-90, -5, -90], "scale": [0.5, 0.5, 0.5]}),
    'db_long': ({"pos": [0, 20, 4], "rotate": [0, 0, 60], "scale": [0.5, 0.5, 0.5]}, {"pos": [0, 20, 3], "rotate": [-180, 0, 120], "scale": [0.5, 0.5, 0.5]}),
}

# свои калибры Зоны: 9×18 (ПМ, ПБ, АПС, «Форт»), 9×39 (СП-5/СП-6 для «Винтореза» и «Грозы»), батареи гаусс-винтовки
AMMO = {
    '9x18': dict(base='9mm', ru='§aПатроны 9×18мм ПМ', en='§a9x18mm PM Rounds', tracer='#FFC070', sort=1, stack=60,
                 recipe=[('forge:ingots/copper', 8), ('forge:gunpowder', 2)], count=50),
    '9x39': dict(base='762x39', ru='§eПатроны 9×39мм СП-6', en='§e9x39mm SP-6 Rounds', tracer='#C0FFC0', sort=8, stack=60,
                 recipe=[('forge:ingots/copper', 12), ('forge:gunpowder', 4)], count=40),
    'gauss': dict(base='50bmg', ru='§bБатарея гаусс-винтовки', en='§bGauss Rifle Cell', tracer='#60C8FF', sort=30, stack=10,
                  recipe=[('forge:ingots/gold', 4), ('minecraft:redstone', 8)], count=4,
                  particle={"name": "electric_spark", "delta": [0, 0, 0], "speed": 0.0, "life_time": 30, "count": 60}),
}
SHELL = {"initial_velocity": [5, 2, 1], "random_velocity": [1, 1, 0.25], "acceleration": [0.0, -10, 0.0], "angular_velocity": [360, -1200, 90], "living_time": 1.0}


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def make(gid):
    if gid in guns2.GUNS2:
        sk, fn, seed = guns2.GUNS2[gid]
        g = geo.Gun(sk, gid, seed)
        fn(g)
        return g
    return guns.make(gid)


def ammo_icon(kind):
    """Иконка патрона 32×32: гильза, пуля, у батареи гаусса — корпус с контактами."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    if kind == 'gauss':
        d.rounded_rectangle([7, 4, 25, 28], 3, fill=(72, 80, 52, 255), outline=(30, 34, 24, 255))
        d.rectangle([7, 12, 25, 15], fill=(186, 140, 62, 255))
        d.rectangle([13, 1, 19, 4], fill=(170, 170, 176, 255))
        d.rectangle([10, 19, 22, 25], fill=(96, 200, 255, 255))
        return im
    long_case = kind == '9x39'
    for i, x in enumerate((7, 14, 21)):
        top = 6 if long_case else 10
        d.rectangle([x, top + 5, x + 5, 27], fill=(186, 140, 62, 255), outline=(110, 80, 30, 255))
        d.polygon([(x, top + 5), (x + 5, top + 5), (x + 4, top), (x + 1, top)], fill=(120, 124, 120, 255) if long_case else (176, 110, 60, 255))
        d.line([(x + 1, 24), (x + 4, 24)], fill=(110, 80, 30, 255))
    return im


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    A = os.path.join(OUT, 'assets', NS); D = os.path.join(OUT, 'data', NS)
    wjson(os.path.join(OUT, 'gunpack.meta.json'), {"namespace": NS, "dependencies": {"tacz": ">=1.1.0"}})
    tex = geo.build_atlas()
    os.makedirs(os.path.join(A, 'textures', 'gun', 'uv'), exist_ok=True)
    tex.save(os.path.join(A, 'textures', 'gun', 'uv', 'zone_guns.png'))
    lang_ru, lang_en = {}, {}
    for gid, sp in SPEC.items():
        g = make(gid)
        sk = sp['sk']
        wjson(os.path.join(A, 'geo_models', 'gun', gid + '_geo.json'), g.geo())
        # иконки: слот 64x64 (3/4 вид), HUD — белый силуэт дулом влево
        slot = geo.render(g, tex, geo.side_view('right', yaw=25, pitch=-18, roll=-28), (256, 256), margin=0.04)
        slot = slot.resize((64, 64), Image.LANCZOS)
        os.makedirs(os.path.join(A, 'textures', 'gun', 'slot'), exist_ok=True)
        slot.save(os.path.join(A, 'textures', 'gun', 'slot', gid + '.png'))
        hud = geo.render(g, tex, geo.side_view('left'), (768, 256), margin=0.03, silhouette=(230, 230, 230, 255))
        os.makedirs(os.path.join(A, 'textures', 'gun', 'hud'), exist_ok=True)
        hud.resize((384, 128), Image.LANCZOS).save(os.path.join(A, 'textures', 'gun', 'hud', gid + '.png'))
        offhand, hotbar = SHOW[sk]
        display = {
            "model": f"{NS}:gun/{gid}_geo", "texture": f"{NS}:gun/uv/zone_guns",
            "hud": f"{NS}:gun/hud/{gid}", "slot": f"{NS}:gun/slot/{gid}",
            "ammo_count_style": "normal", "damage_style": "per_projectile" if sp.get('pellets') else "total",
            "animation": f"tacz:{ANIM[sk]}",
            "use_default_animation": "pistol" if sk == 'glock_17' else "rifle",
            "third_person_animation": "default",
            "player_animator_3rd": "tacz:pistol_default.player_animation" if sk == 'glock_17' else "tacz:rifle_default.player_animation",
            "show_crosshair": False,
            "transform": {"scale": {"thirdperson": [0.6, 0.6, 0.6], "ground": [0.6, 0.6, 0.6], "fixed": [1.2, 1.2, 1.2]}},
            "muzzle_flash": {"texture": "tacz:flash/common_muzzle_flash", "scale": sp['flash']},
            "iron_zoom": sp['zoom'], "zoom_model_fov": 45,
            "shell": SHELL, "ammo": {"tracer_color": "#FFB070"},
            "sounds": dict(SOUNDS[sk], dry_fire="tacz:dry_fire", fire_select="tacz:fire_select", head_hit="tacz:head_hit",
                           flesh_hit="tacz:flesh_hit", kill="tacz:kill"),
            "offhand_show": offhand, "hotbar_show": {"0": hotbar},
        }
        if STATE.get(sk):
            display["state_machine"] = STATE[sk]
        if sp.get('silenced'):
            # интегрированный глушитель: звук выстрела глушёный
            display["sounds"]["shoot"] = display["sounds"].get("silence", display["sounds"]["shoot"])
            display["sounds"]["shoot_3p"] = display["sounds"].get("silence_3p", display["sounds"]["shoot_3p"])
        if gid == 'gauss':
            display["ammo"] = {"tracer_color": "#60C8FF"}
        if sk in ('db_short', 'db_long'):
            display.pop('shell')
        wjson(os.path.join(A, 'display', 'guns', gid + '_display.json'), display)
        data = {
            "ammo": sp['ammo'], "ammo_amount": sp['mag'],
            "bolt": sp['bolt'], "rpm": sp['rpm'],
            "bullet": {"life": 0.8 if sp['type'] != 'shotgun' else 0.3, "bullet_amount": sp.get('pellets', 1), "damage": sp['dmg'],
                       "tracer_count_interval": 3 if sp['type'] == 'mg' else -1,
                       "extra_damage": {"armor_ignore": sp['armor'], "head_shot_multiplier": sp['head'], "damage_adjust": sp['fall']},
                       "speed": sp['speed'], "gravity": 0.15, "knockback": 0.6 if sp['type'] == 'shotgun' else 0, "friction": 0.02,
                       "ignite": False, "pierce": sp.get('pierce', 2 if sp['type'] in ('sniper', 'mg') else 1)},
            "reload": RELOAD[sk],
            "draw_time": TIMES[sk][0], "put_away_time": TIMES[sk][1], "aim_time": 0.2 if sp['type'] != 'pistol' else 0.13,
            "sprint_time": 0.2, "weight": sp['weight'],
            "movement_speed": {"base": 0.0, "aim": -0.2 if sp['type'] != 'pistol' else 0, "reload": -0.1},
            "fire_mode": sp['modes'], "crawl_recoil_multiplier": 0.5,
            "recoil": sp['rec'], "inaccuracy": sp['inacc'],
            "melee": {"distance": 1, "cooldown": 0.7, "default": {"animation_type": "melee_stock" if sp['type'] in ('rifle', 'sniper', 'mg') else "melee_push",
                                                                  "distance": 1, "range_angle": 30, "damage": 2, "knockback": 0.5, "prep": 0.1}},
            "allow_attachment_types": sp['att'],
        }
        if sp.get('burst'):
            data["burst_data"] = sp['burst']
        if sp.get('silenced'):
            data["silence_multiplier"] = 0.3
        if 'xmag' in sp and sk in XMAG:
            data["extended_mag_ammo_amount"] = sp['xmag']
            data["script"] = "tacz:xmag_reload_logic"
            data["script_param"] = xmag_params(sk)
        elif 'xmag' in sp:
            data["extended_mag_ammo_amount"] = sp['xmag']
        if sk == 'db_short':
            data["script"] = "tacz:db_short_gun_logic"
        wjson(os.path.join(D, 'data', 'guns', gid + '_data.json'), data)
        wjson(os.path.join(D, 'index', 'guns', gid + '.json'), {
            "name": f"{NS}.gun.{gid}.name", "display": f"{NS}:{gid}_display", "data": f"{NS}:{gid}_data",
            "tooltip": f"{NS}.gun.{gid}.desc", "type": sp['type'], "item_type": "modern_kinetic", "sort": sp['sort']})
        if sp['tags']:
            wjson(os.path.join(D, 'tacz_tags', 'attachments', 'allow_attachments', gid + '.json'), sp['tags'])
        wjson(os.path.join(D, 'recipes', 'gun', gid + '.json'), {
            "materials": [{"item": {"tag": t}, "count": c} for t, c in sp['recipe']],
            "result": {"type": "gun", "id": f"{NS}:{gid}"}, "type": "tacz:gun_smith_table_crafting"})
        lang_ru[f"{NS}.gun.{gid}.name"], lang_ru[f"{NS}.gun.{gid}.desc"] = sp['rus']
        lang_en[f"{NS}.gun.{gid}.name"], lang_en[f"{NS}.gun.{gid}.desc"] = sp['eng']
    # свои калибры
    for aid, a in AMMO.items():
        wjson(os.path.join(D, 'index', 'ammo', aid + '.json'), {"name": f"{NS}.ammo.{aid}.name", "display": f"{NS}:{aid}_display",
                                                                "stack_size": a['stack'], "sort": a['sort']})
        disp = {"model": f"tacz:ammo/{a['base']}", "texture": f"tacz:ammo/uv/{a['base']}", "slot": f"{NS}:ammo/slot/{aid}",
                "shell": {"model": f"tacz:shell/{a['base']}_shell", "texture": f"tacz:shell/{a['base']}_shell"},
                "tracer_color": a['tracer'],
                "transform": {"scale": {"thirdperson": [0.6, 0.6, 0.6], "ground": [0.6, 0.6, 0.6], "fixed": [1.2, 1.2, 1.2]}}}
        if a.get('particle'):
            disp["particle"] = a['particle']
        wjson(os.path.join(A, 'display', 'ammo', aid + '_display.json'), disp)
        os.makedirs(os.path.join(A, 'textures', 'ammo', 'slot'), exist_ok=True)
        ammo_icon(aid).save(os.path.join(A, 'textures', 'ammo', 'slot', aid + '.png'))
        wjson(os.path.join(D, 'recipes', 'ammo', aid + '.json'), {
            "materials": [{"item": {"tag": t}, "count": c} for t, c in a['recipe']],
            "result": {"type": "ammo", "id": f"{NS}:{aid}", "count": a['count']}, "type": "tacz:gun_smith_table_crafting"})
        lang_ru[f"{NS}.ammo.{aid}.name"] = a['ru']; lang_en[f"{NS}.ammo.{aid}.name"] = a['en']
    lang_ru["szguns.pack.name"] = "Оружие Зоны"; lang_en["szguns.pack.name"] = "Zone Arms"
    wjson(os.path.join(A, 'lang', 'ru_ru.json'), lang_ru)
    wjson(os.path.join(A, 'lang', 'en_us.json'), lang_en)
    # архив для ручной установки в .minecraft/tacz
    zp = os.path.join(ROOT, 'build', 'stalkerzone_guns.zip')
    os.makedirs(os.path.dirname(zp), exist_ok=True)
    with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
        for dp, _, fs in os.walk(OUT):
            for f in fs:
                full = os.path.join(dp, f)
                z.write(full, os.path.relpath(full, OUT))
    # список файлов пака для копирования из jar
    files = []
    for dp, _, fs in os.walk(OUT):
        for f in fs:
            files.append(os.path.relpath(os.path.join(dp, f), OUT).replace(os.sep, '/'))
    with open(os.path.join(OUT, 'files.txt'), 'w') as f:
        f.write('\n'.join(sorted(files)) + '\n')
    print('gunpack:', len(files), 'files ->', OUT)


if __name__ == '__main__':
    main()
