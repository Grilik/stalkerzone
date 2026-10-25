"""Генерирует OBJ-модели пропсов и техники, текстуры, json-модели Forge, состояния блоков, переводы
и Java-таблицу GeneratedProps. Запуск: python3 tools/props/gen_props.py"""
import json, math, os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import mesh, models, textures, textures2
import models2  # noqa: F401 — дописывает пропсы и технику v1.13 в models
import models3, textures3  # noqa: F401 — v1.14: техника, ЛЭП, газопровод, ангар, двор

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
A = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'stalkerzone')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'ru', 'stalkerzone', 'block', 'GeneratedProps.java')
NS = 'stalkerzone'
FACING_Y = {'east': 0, 'south': 90, 'west': 180, 'north': 270}


def wjson(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(o, f, ensure_ascii=False, indent=1)


def used_mats(m):
    return sorted({mat for mat, _ in m.faces})


def tex_of(mat, paint=None, paint2=None):
    if mat == 'paint': return f'{NS}:block/prop/paint_{paint}'
    if mat == 'paint2': return f'{NS}:block/prop/paint_{paint2}'
    return f'{NS}:block/prop/{mat}'


def obj_model(objname, mats, paint=None, paint2=None, particle=None):
    tx = {m: tex_of(m, paint, paint2) for m in mats}
    tx['particle'] = particle or tx[mats[0]]
    return {"loader": "forge:obj", "model": f"{NS}:models/block/obj/{objname}.obj", "flip_v": True,
            "shade_quads": True, "emissive_ambient": False, "automatic_culling": False,
            "render_type": "minecraft:cutout", "ambient_occlusion": False, "textures": tx}


def item_display(size):
    s = min(0.625, 1.0 / max(size, 1.0) * 0.9)
    g = round(s, 3)
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [g, g, g]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [g * 0.4, g * 0.4, g * 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [g, g, g]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [g * 0.6, g * 0.6, g * 0.6]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [g * 0.6, g * 0.6, g * 0.6]}}


def vehicle_cells(L, W, H):
    cells = []
    for dx in range(-int(L), int(L) + 1):
        for dz in range(-int(W), int(W) + 1):
            for dy in range(0, int(math.ceil(H)) + 1):
                cx, cz = dx + 0.5, dz + 0.5
                if abs(cx - 0.5) > L / 2 - 0.35 or abs(cz - 0.5) > W / 2 - 0.3 or dy > H - 0.4:
                    continue
                if (dx, dy, dz) != (0, 0, 0):
                    cells.append((dx, dy, dz))
    return cells


def main():
    os.makedirs(os.path.join(A, 'models', 'block', 'obj'), exist_ok=True)
    os.makedirs(os.path.join(A, 'textures', 'block', 'prop'), exist_ok=True)
    tex = textures.gen_all()
    tex.update(textures2.gen_extra())
    tex.update(textures3.gen_more())
    for k, im in tex.items():
        im.save(os.path.join(A, 'textures', 'block', 'prop', k + '.png'))
    lang_ru, lang_en = {}, {}
    java_props, java_veh = [], []
    # ---------------- пропсы
    for name, (fn, ru, shape, cells, light) in models.PROPS.items():
        m = fn()
        mats = used_mats(m)
        mesh.write_obj(m, os.path.join(A, 'models', 'block', 'obj', name + '.obj'), name + '.mtl')
        mesh.write_mtl(os.path.join(A, 'models', 'block', 'obj', name + '.mtl'), mats)
        wjson(os.path.join(A, 'models', 'block', name + '.json'), obj_model(name, mats))
        mn, mx = mesh.bounds(m)
        size = float(max(mx - mn))
        wjson(os.path.join(A, 'models', 'item', name + '.json'), {"parent": f"{NS}:block/{name}", "display": item_display(size)})
        wjson(os.path.join(A, 'blockstates', name + '.json'),
              {"variants": {f"facing={f}": {"model": f"{NS}:block/{name}", "y": y} for f, y in FACING_Y.items()}})
        lang_ru[f"block.{NS}.{name}"] = ru
        lang_en[f"block.{NS}.{name}"] = name.replace('prop_', '').replace('_', ' ').title()
        java_props.append((name, shape, cells, light))
    # ---------------- техника: OBJ для целой (0) и разбитой (1); сгоревшая = разбитая с горелой краской
    for kind, (ru, paint, paint2, L, W, H) in models.VEHICLES.items():
        base = 'veh_' + kind
        for dmg in (0, 1):
            m = models.car(kind, dmg)
            mats = sorted(set(used_mats(m)) | {'paint'})
            on = f'{base}_{dmg}'
            mesh.write_obj(m, os.path.join(A, 'models', 'block', 'obj', on + '.obj'), on + '.mtl')
            mesh.write_mtl(os.path.join(A, 'models', 'block', 'obj', on + '.mtl'), mats)
            for p in models.PAINTS[:-1]:
                for d in ((0,) if dmg == 0 else (1, 2)):
                    pp = 'burnt' if d == 2 else p
                    p2 = 'burnt' if d == 2 else paint2
                    mm = obj_model(on, mats, pp, p2)
                    if d == 2:
                        for k in ('interior', 'rim', 'chrome', 'grille', 'canvas', 'black', 'wood', 'headlight', 'taillight', 'plate'):
                            if k in mm['textures']: mm['textures'][k] = f'{NS}:block/prop/paint_burnt'
                    wjson(os.path.join(A, 'models', 'block', 'veh', f'{kind}_{d}_{p}.json'), mm)
        variants = {}
        for f, y in FACING_Y.items():
            for d in (0, 1, 2):
                for p in models.PAINTS[:-1]:
                    variants[f"damage={d},facing={f},paint={p}"] = {"model": f"{NS}:block/veh/{kind}_{d}_{p}", "y": y}
        wjson(os.path.join(A, 'blockstates', base + '.json'), {"variants": variants})
        wjson(os.path.join(A, 'models', 'item', base + '.json'), {"parent": f"{NS}:block/veh/{kind}_0_{paint}", "display": item_display(L)})
        lang_ru[f"block.{NS}.{base}"] = ru
        lang_en[f"block.{NS}.{base}"] = kind.replace('_', ' ').title()
        java_veh.append((base, paint, L, W, H, vehicle_cells(L, W, H)))
    lang_ru[f"block.{NS}.prop_hitbox"] = "Корпус"
    lang_en[f"block.{NS}.prop_hitbox"] = "Hull"
    for lang, add in (('ru_ru', lang_ru), ('en_us', lang_en)):
        p = os.path.join(A, 'lang', lang + '.json')
        cur = json.load(open(p, encoding='utf-8')) if os.path.exists(p) else {}
        cur.update(add)
        wjson(p, cur)
    D = os.path.join(ROOT, 'src', 'main', 'resources', 'data', NS, 'loot_tables', 'blocks')
    for name in [x[0] for x in java_props] + [x[0] for x in java_veh]:
        wjson(os.path.join(D, name + '.json'), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                                                                                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    wjson(os.path.join(A, 'blockstates', 'prop_hitbox.json'), {"variants": {"": {"model": f"{NS}:block/prop_hitbox"}}})
    wjson(os.path.join(A, 'models', 'block', 'prop_hitbox.json'), {"textures": {"particle": f"{NS}:block/prop/rust"}, "elements": []})
    write_java(java_props, java_veh)
    print('props:', len(java_props), 'vehicles:', len(java_veh))


def write_java(props, veh):
    L = ["package ru.stalkerzone.block;", "", "/** Сгенерировано tools/props/gen_props.py: 3D-пропсы и техника (OBJ-модели). Не править руками. */",
         "public final class GeneratedProps {", "    private GeneratedProps() {}", "",
         "    /** Пропс: имя, коллизия в 1/16 блока, занятые соседние клетки (локально, перед на +x), свечение. */",
         "    public record Prop(String name, double[] box, int[][] cells, int light) {}",
         "    /** Техника: имя, краска по умолчанию, длина, ширина, высота, занятые клетки. */",
         "    public record Vehicle(String name, String paint, double length, double width, double height, int[][] cells) {}", ""]
    L.append("    public static final Prop[] PROPS = {")
    for name, shape, cells, light in props:
        cs = ", ".join("{%d, %d, %d}" % c for c in cells)
        L.append('        new Prop("%s", new double[] {%s}, new int[][] {%s}, %d),' % (name, ", ".join(str(x) for x in shape), cs, light))
    L.append("    };")
    L.append("")
    L.append("    public static final Vehicle[] VEHICLES = {")
    for name, paint, Ln, W, H, cells in veh:
        cs = ", ".join("{%d, %d, %d}" % c for c in cells)
        L.append('        new Vehicle("%s", "%s", %s, %s, %s, new int[][] {%s}),' % (name, paint, Ln, W, H, cs))
    L.append("    };")
    L.append("}")
    with open(JAVA, 'w', encoding='utf-8') as f:
        f.write("\n".join(L) + "\n")


if __name__ == '__main__':
    main()
