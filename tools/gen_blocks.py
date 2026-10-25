"""Ресурсы для декоративных блоков мода (v1.11): blockstates, модели, предметы, лут, язык, теги.
Список блоков совпадает с ModBlocks.DECOR (Java). python3 tools/gen_blocks.py"""
import json, os
R = 'src/main/resources'
A = R + '/assets/stalkerzone'
D = R + '/data/stalkerzone'
for p in ('blockstates', 'models/block', 'models/item'): os.makedirs(f'{A}/{p}', exist_ok=True)
os.makedirs(f'{D}/loot_tables/blocks', exist_ok=True)
os.makedirs(f'{D}/tags/blocks', exist_ok=True)
os.makedirs(f'{R}/data/minecraft/tags/blocks/mineable', exist_ok=True)

PAINTS = {'red': ('красная', 'Red'), 'white': ('белая', 'White'), 'blue': ('синяя', 'Blue'), 'cream': ('кремовая', 'Cream'),
          'olive': ('оливковая', 'Olive'), 'orange': ('оранжевая', 'Orange'), 'green': ('зелёная', 'Green'), 'beige': ('бежевая', 'Beige')}

def w(path, obj):
    with open(path, 'w', encoding='utf-8') as f: json.dump(obj, f, ensure_ascii=False, indent=1)

lang_ru, lang_en = {}, {}
blocks = []  # (id, kind)

def cube(name, tex=None, cutout=False):
    tex = tex or name
    m = {"parent": "minecraft:block/cube_all", "textures": {"all": f"stalkerzone:block/{tex}"}}
    if cutout: m["render_type"] = "minecraft:translucent"
    w(f'{A}/models/block/{name}.json', m)
    w(f'{A}/blockstates/{name}.json', {"variants": {"": {"model": f"stalkerzone:block/{name}"}}})
    w(f'{A}/models/item/{name}.json', {"parent": f"stalkerzone:block/{name}"})
    loot(name)

def loot(name, slab=False):
    entry = {"type": "minecraft:item", "name": f"stalkerzone:{name}"}
    if slab:
        entry["functions"] = [{"function": "minecraft:set_count", "count": 2, "add": False,
                               "conditions": [{"condition": "minecraft:block_state_property", "block": f"stalkerzone:{name}", "properties": {"type": "double"}}]},
                              {"function": "minecraft:explosion_decay"}]
    w(f'{D}/loot_tables/blocks/{name}.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

def stairs(name, tex):
    t = f"stalkerzone:block/{tex}"
    for suf, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
        w(f'{A}/models/block/{name}{suf}.json', {"parent": f"minecraft:block/{parent}", "textures": {"bottom": t, "top": t, "side": t}})
    var = {}
    rot = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    for f in ('east', 'north', 'south', 'west'):
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                model = name + ('_inner' if shape.startswith('inner') else '_outer' if shape.startswith('outer') else '')
                y = rot[f]
                if half == 'bottom':
                    if shape in ('inner_left', 'outer_left'): y -= 90
                else:
                    if shape in ('inner_right', 'outer_right'): y += 90
                v = {"model": f"stalkerzone:block/{model}"}
                if half == 'top': v["x"] = 180
                y %= 360
                if y: v["y"] = y
                if v.get("x") or v.get("y"): v["uvlock"] = True
                var[f"facing={f},half={half},shape={shape}"] = v
    w(f'{A}/blockstates/{name}.json', {"variants": var})
    w(f'{A}/models/item/{name}.json', {"parent": f"stalkerzone:block/{name}"})
    loot(name)

def slab(name, tex, full):
    t = f"stalkerzone:block/{tex}"
    w(f'{A}/models/block/{name}.json', {"parent": "minecraft:block/slab", "textures": {"bottom": t, "top": t, "side": t}})
    w(f'{A}/models/block/{name}_top.json', {"parent": "minecraft:block/slab_top", "textures": {"bottom": t, "top": t, "side": t}})
    w(f'{A}/blockstates/{name}.json', {"variants": {"type=bottom": {"model": f"stalkerzone:block/{name}"},
                                                   "type=top": {"model": f"stalkerzone:block/{name}_top"},
                                                   "type=double": {"model": f"stalkerzone:block/{full}"}}})
    w(f'{A}/models/item/{name}.json', {"parent": f"stalkerzone:block/{name}"})
    loot(name, True)

def named(id_, ru, en):
    lang_ru[f"block.stalkerzone.{id_}"] = ru
    lang_en[f"block.stalkerzone.{id_}"] = en
    blocks.append(id_)

# стройматериалы
cube('whitewash'); named('whitewash', 'Побелённая штукатурка', 'Whitewashed Plaster')
cube('whitewash_damaged'); named('whitewash_damaged', 'Облупленная побелка', 'Peeling Whitewash')
cube('concrete_fence'); named('concrete_fence', 'Бетонный забор ПО-2', 'Concrete Fence Panel')
cube('slate_roof'); named('slate_roof', 'Шифер', 'Slate Roofing')
stairs('slate_roof_stairs', 'slate_roof'); named('slate_roof_stairs', 'Шиферная кровля (скат)', 'Slate Roof Stairs')
slab('slate_roof_slab', 'slate_roof', 'slate_roof'); named('slate_roof_slab', 'Шиферная кровля (плита)', 'Slate Roof Slab')
# техника
for c, (ru, en) in PAINTS.items():
    b = f'car_paint_{c}'
    cube(b); named(b, f'Кузов, {ru} краска', f'{en} Car Body')
    stairs(b + '_stairs', b); named(b + '_stairs', f'Кузов, {ru} краска (скос)', f'{en} Car Body Stairs')
    slab(b + '_slab', b, b); named(b + '_slab', f'Кузов, {ru} краска (половина)', f'{en} Car Body Slab')
cube('car_glass', cutout=True); named('car_glass', 'Стекло кабины', 'Cab Glass')
cube('car_grille'); named('car_grille', 'Решётка радиатора', 'Radiator Grille')
cube('car_headlight'); named('car_headlight', 'Фара', 'Headlight')
# колесо: ось как у бревна
w(f'{A}/models/block/car_wheel.json', {"parent": "minecraft:block/cube_column", "textures": {"end": "stalkerzone:block/car_wheel", "side": "stalkerzone:block/car_wheel_tread"}})
w(f'{A}/models/block/car_wheel_horizontal.json', {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": "stalkerzone:block/car_wheel", "side": "stalkerzone:block/car_wheel_tread"}})
w(f'{A}/blockstates/car_wheel.json', {"variants": {"axis=y": {"model": "stalkerzone:block/car_wheel"},
                                                   "axis=z": {"model": "stalkerzone:block/car_wheel_horizontal", "x": 90},
                                                   "axis=x": {"model": "stalkerzone:block/car_wheel_horizontal", "x": 90, "y": 90}}})
w(f'{A}/models/item/car_wheel.json', {"parent": "stalkerzone:block/car_wheel_horizontal"})
loot('car_wheel'); named('car_wheel', 'Колесо', 'Wheel')
# ящики
for nm, ru, en in (('wooden_crate', 'Деревянный ящик', 'Wooden Crate'), ('military_crate', 'Армейский ящик', 'Military Crate'),
                   ('medical_crate', 'Ящик с медикаментами', 'Medical Crate')):
    cube(nm); named(nm, ru, en)
# радиоактивная бочка: та же модель, что у ржавой
bar = json.load(open(f'{A}/models/block/rusty_barrel.json'))
bar['textures'] = {"particle": "stalkerzone:block/radioactive_barrel_side", "side": "stalkerzone:block/radioactive_barrel_side", "top": "stalkerzone:block/radioactive_barrel_top"}
w(f'{A}/models/block/radioactive_barrel.json', bar)
w(f'{A}/blockstates/radioactive_barrel.json', {"variants": {"": {"model": "stalkerzone:block/radioactive_barrel"}}})
w(f'{A}/models/item/radioactive_barrel.json', {"parent": "stalkerzone:block/radioactive_barrel"})
loot('radioactive_barrel'); named('radioactive_barrel', 'Бочка с радиоактивными отходами', 'Radioactive Waste Barrel')

# язык
for fn, d in (('ru_ru.json', lang_ru), ('en_us.json', lang_en)):
    p = f'{A}/lang/{fn}'
    cur = json.load(open(p, encoding='utf-8'))
    cur.update(d)
    w(p, cur)

# теги: радиоактивный металл и инструмент добычи
metal = [b for b in blocks if b.startswith('car_paint') or b in ('car_grille', 'car_headlight', 'car_wheel')]
w(f'{D}/tags/blocks/radioactive.json', {"replace": False, "values": [f"stalkerzone:{b}" for b in metal] +
                                        ["stalkerzone:rusty_barrel", "minecraft:raw_iron_block"]})
w(f'{D}/tags/blocks/radioactive_strong.json', {"replace": False, "values": ["stalkerzone:radioactive_barrel"]})
pick = [b for b in blocks if not b.endswith('crate')] + ['rusty_barrel']
w(f'{R}/data/minecraft/tags/blocks/mineable/pickaxe.json', {"replace": False, "values": [f"stalkerzone:{b}" for b in pick]})
w(f'{R}/data/minecraft/tags/blocks/mineable/axe.json', {"replace": False, "values": [f"stalkerzone:{b}" for b in blocks if b.endswith('crate')]})
print(len(blocks), 'blocks')
