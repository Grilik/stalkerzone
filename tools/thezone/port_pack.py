#!/usr/bin/env python3
"""Э1-01/Э1-02: перенос ресурспака TheZone из Bedrock (.mcworld) в Java 1.20.1.

Только читает архив мира; пишет пак в указанную папку. Текстуры пака в git не кладутся
(основа — BDcraft Cubik by Sphax, перекрашено автором TheZone; пак — для локальной версии).

Запуск:
    python -I tools/thezone/port_pack.py <TheZone.mcworld> <папка-пака> [отчёт.md]
Пример:
    python -I tools/thezone/port_pack.py "C:/Users/vladi/Downloads/TheZone.mcworld" "C:/MineStalker/modpack/resourcepacks/TheZone"

Имена текстур в паке — эпохи Java 1.7 (пак когда-то перевели с Java на Bedrock конвертером),
поэтому таблица ниже переводит 1.7-имена в имена 1.20.1.
"""
import json
import re
import sys
import zipfile
from pathlib import PurePosixPath

PACK_ROOT = "resource_packs/TheZone/"
PACK_FORMAT = 15  # Java 1.20 – 1.20.1

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
          "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"]


def color(c):
    return "light_gray" if c == "silver" else c


# --- блоки: точные соответствия 1.7 -> 1.20.1 -------------------------------------------
BLOCKS = {
    "bed_feet_end": None, "bed_feet_side": None, "bed_feet_top": None,       # кровати с 1.13 — текстура сущности
    "bed_head_end": None, "bed_head_side": None, "bed_head_top": None,
    "bedrock": "bedrock", "bookshelf": "bookshelf", "brewing_stand": "brewing_stand", "brick": "bricks",
    "cauldron_bottom": "cauldron_bottom", "cauldron_inner": "cauldron_inner", "cauldron_side": "cauldron_side",
    "cauldron_top": "cauldron_top", "clay": "clay", "coal_block": "coal_block", "cobblestone": "cobblestone",
    "cobblestone_mossy": "mossy_cobblestone",
    "crafting_table_front": "crafting_table_front", "crafting_table_side": "crafting_table_side",
    "crafting_table_top": "crafting_table_top",
    "daylight_detector_side": "daylight_detector_side", "daylight_detector_top": "daylight_detector_top",
    "deadbush": "dead_bush", "diamond_block": "diamond_block", "dirt": "dirt",
    "dispenser_front_horizontal": "dispenser_front", "dispenser_front_vertical": "dispenser_front_vertical",
    "door_acacia_lower": "acacia_door_bottom", "door_acacia_upper": "acacia_door_top",
    "door_birch_lower": "birch_door_bottom", "door_birch_upper": "birch_door_top",
    "door_dark_oak_lower": "dark_oak_door_bottom", "door_dark_oak_upper": "dark_oak_door_top",
    "door_iron_lower": "iron_door_bottom", "door_iron_upper": "iron_door_top",
    "door_jungle_lower": "jungle_door_bottom", "door_jungle_upper": "jungle_door_top",
    "door_spruce_lower": "spruce_door_bottom", "door_spruce_upper": "spruce_door_top",
    "door_wood_lower": "oak_door_bottom", "door_wood_upper": "oak_door_top",
    "double_plant_fern_bottom": "large_fern_bottom", "double_plant_fern_top": "large_fern_top",
    "double_plant_grass_bottom": "tall_grass_bottom", "double_plant_grass_top": "tall_grass_top",
    "double_plant_paeonia_bottom": "peony_bottom", "double_plant_paeonia_top": "peony_top",
    "double_plant_rose_bottom": "rose_bush_bottom", "double_plant_rose_top": "rose_bush_top",
    "double_plant_sunflower_back": "sunflower_back", "double_plant_sunflower_bottom": "sunflower_bottom",
    "double_plant_sunflower_front": "sunflower_front", "double_plant_sunflower_top": "sunflower_top",
    "double_plant_syringa_bottom": "lilac_bottom", "double_plant_syringa_top": "lilac_top",
    "dropper_front_horizontal": "dropper_front", "dropper_front_vertical": "dropper_front_vertical",
    "emerald_block": "emerald_block", "end_stone": "end_stone", "fern": "fern",
    "fire_layer_0": "fire_0", "fire_layer_1": "fire_1", "fire_0": "fire_0", "fire_1": "fire_1",
    "flower_allium": "allium", "flower_blue_orchid": "blue_orchid", "flower_dandelion": "dandelion",
    "flower_houstonia": "azure_bluet", "flower_oxeye_daisy": "oxeye_daisy", "flower_paeonia": None,
    "flower_rose": "poppy", "flower_tulip_orange": "orange_tulip", "flower_tulip_pink": "pink_tulip",
    "flower_tulip_red": "red_tulip", "flower_tulip_white": "white_tulip",
    "furnace_front_off": "furnace_front", "furnace_front_on": "furnace_front_on",
    "furnace_side": "furnace_side", "furnace_top": "furnace_top",
    "glass": "glass", "glowstone": "glowstone", "gold_block": "gold_block",
    "grass_side": "grass_block_side", "grass_side_overlay": "grass_block_side_overlay",
    "grass_side_snowed": "grass_block_snow", "grass_top": "grass_block_top", "gravel": "gravel",
    "hardened_clay": "terracotta",
    "hopper_inside": "hopper_inside", "hopper_outside": "hopper_outside", "hopper_top": "hopper_top",
    "iron_bars": "iron_bars", "iron_block": "iron_block", "iron_trapdoor": "iron_trapdoor",
    "jukebox_side": "jukebox_side", "jukebox_top": "jukebox_top", "ladder": "ladder", "lapis_block": "lapis_block",
    "lava_flow": "lava_flow", "lava_still": "lava_still",
    "leaves_birch": "birch_leaves", "leaves_jungle": "jungle_leaves", "leaves_oak": "oak_leaves",
    "leaves_spruce": "spruce_leaves", "lever": "lever",
    "log_birch": "birch_log", "log_birch_top": "birch_log_top", "log_jungle": "jungle_log",
    "log_jungle_top": "jungle_log_top", "log_oak": "oak_log", "log_oak_top": "oak_log_top",
    "log_spruce": "spruce_log", "log_spruce_top": "spruce_log_top",
    "nether_brick": "nether_bricks", "netherrack": "netherrack",
    "nether_wart_stage_0": "nether_wart_stage0", "nether_wart_stage_1": "nether_wart_stage1",
    "nether_wart_stage_2": "nether_wart_stage2", "noteblock": "note_block",
    "piston_bottom": "piston_bottom", "piston_side": "piston_side", "piston_top_normal": "piston_top",
    "planks_acacia": "acacia_planks", "planks_big_oak": "dark_oak_planks", "planks_birch": "birch_planks",
    "planks_jungle": "jungle_planks", "planks_oak": "oak_planks", "planks_spruce": "spruce_planks",
    "portal": "nether_portal",
    "prismarine_rough": "prismarine",
    "pumpkin_face_off": "carved_pumpkin", "pumpkin_face_on": "jack_o_lantern",
    "pumpkin_side": "pumpkin_side", "pumpkin_top": "pumpkin_top",
    "quartz_block_bottom": "quartz_block_bottom", "quartz_block_chiseled": "chiseled_quartz_block",
    "quartz_block_chiseled_top": "chiseled_quartz_block_top", "quartz_block_lines": "quartz_pillar",
    "quartz_block_lines_top": "quartz_pillar_top", "quartz_block_side": "quartz_block_side",
    "quartz_block_top": "quartz_block_top",
    "rail_normal": "rail", "rail_normal_turned": "rail_corner", "rail": "rail",
    "red_sand": "red_sand", "redstone_block": "redstone_block",
    "redstone_lamp_off": "redstone_lamp", "redstone_lamp_on": "redstone_lamp_on", "redstone_ore": "redstone_ore",
    "redstone_torch_off": "redstone_torch_off", "redstone_torch_on": "redstone_torch",
    "reeds": "sugar_cane",
    "repeater_off": "repeater", "repeater_on": "repeater_on",
    "sand": "sand", "sandstone_bottom": "sandstone_bottom", "sandstone_carved": "chiseled_sandstone",
    "sandstone_normal": "sandstone", "sandstone_smooth": "cut_sandstone", "sandstone_top": "sandstone_top",
    "sea_lantern": "sea_lantern", "snow": "snow", "soul_sand": "soul_sand", "sponge": "sponge", "stone": "stone",
    "stone_slab_side": "smooth_stone_slab_side", "stone_slab_top": "smooth_stone",
    "stonebrick": "stone_bricks", "stonebrick_carved": "chiseled_stone_bricks",
    "stonebrick_cracked": "cracked_stone_bricks", "stonebrick_mossy": "mossy_stone_bricks",
    "tallgrass": "grass", "tnt_bottom": "tnt_bottom", "tnt_side": "tnt_side", "tnt_top": "tnt_top",
    "torch_on": "torch", "trapdoor": "oak_trapdoor", "vine": "vine",
    "water_flow": "water_flow", "water_still": "water_still", "waterlily": "lily_pad",
}
# Bedrock-специфичные и служебные — пропускаются (в Java нет своей текстуры)
BLOCK_SKIP_RE = re.compile(r"(_carried$|^carried_|_opaque$| - Copy$|^reeds512$|^door_jungle_inner$|^cobblestone_\d$)")

# --- предметы 1.7 -> 1.20.1 -------------------------------------------------------------
ITEMS = {
    "apple_golden": "golden_apple", "beef_cooked": "cooked_beef", "beef_raw": "beef",
    "bow_standby": "bow", "bucket_milk": "milk_bucket", "chicken_cooked": "cooked_chicken",
    "clock": None, "clock2": None, "compass": None, "compass2": None,   # в 1.20 — кадры clock_00.. / compass_00..
    "fish_clownfish_raw": "tropical_fish", "fish_cooked": "cooked_cod", "fish_pufferfish_raw": "pufferfish",
    "fish_raw": "cod", "fish_salmon_cooked": "cooked_salmon", "fish_salmon_raw": "salmon",
    "fishing_rod_cast": "fishing_rod_cast", "fishing_rod_uncast": "fishing_rod",
    "fireworks_charge": "firework_star", "fireworks_charge_overlay": "firework_star_overlay",
    "gold_axe": "golden_axe", "gold_boots": "golden_boots", "gold_chestplate": "golden_chestplate",
    "gold_helmet": "golden_helmet", "gold_leggings": "golden_leggings", "gold_pickaxe": "golden_pickaxe",
    "gold_shovel": "golden_shovel", "gold_sword": "golden_sword",
    "porkchop_cooked": "cooked_porkchop", "porkchop_raw": "porkchop",
    "potato_baked": "baked_potato", "potato_poisonous": "poisonous_potato",
    "potion_bottle_drinkable": "potion", "potion_bottle_splash": "splash_potion",
    "sign": "oak_sign", "bed": None,
    "wood_axe": "wooden_axe", "wood_hoe": "wooden_hoe", "wood_pickaxe": "wooden_pickaxe",
    "wood_shovel": "wooden_shovel", "wood_sword": "wooden_sword",
}
ITEM_SAME = None  # прочие имена (apple, arrow, bread, diamond_sword…) в 1.20.1 совпадают

# --- сущности, окружение, прочее ---------------------------------------------------------
ENTITY = {
    "chest/double_normal": None,       # в 1.20 сундук двойной — две половины normal_left/right
    "chest/trapped_double": None,
    "sign": "signs/oak",
    "skulls/skeleton": None, "skulls/wither_skeleton": None, "skulls/zombie": None,  # с 1.13 — текстуры моба
    "villager/butcher": None, "villager/farmer": None, "villager/librarian": None,   # с 1.14 — слои профессий
    "villager/priest": None, "villager/smith": None,
    "zombie_villager/zombie_villager": None,
    "iron_golem": "iron_golem/iron_golem",
}
OTHER = {
    "environment/sun": "environment/sun", "environment/moon_phases": "environment/moon_phases",
    "models/armor/chain_1": "models/armor/chainmail_layer_1", "models/armor/chain_2": "models/armor/chainmail_layer_2",
    "models/armor/diamond_1": "models/armor/diamond_layer_1", "models/armor/diamond_2": "models/armor/diamond_layer_2",
    "models/armor/gold_1": "models/armor/gold_layer_1", "models/armor/gold_2": "models/armor/gold_layer_2",
    "models/armor/iron_1": "models/armor/iron_layer_1", "models/armor/iron_2": "models/armor/iron_layer_2",
    "map/map_background": "map/map_background", "map/map_icons": "map/map_icons",
    "particle/particles": None,   # в 1.20 частицы — отдельные файлы, атлас не подходит
    "painting/kz": None,          # в 1.20 картины — отдельные файлы, атлас не подходит
}


def block_target(name):
    if name in BLOCKS:
        return BLOCKS[name]
    m = re.fullmatch(r"wool_colored_(\w+)", name)
    if m:
        return f"{color(m.group(1))}_wool"
    m = re.fullmatch(r"hardened_clay_stained_(\w+)", name)
    if m:
        return f"{color(m.group(1))}_terracotta"
    m = re.fullmatch(r"glass_pane_top_(\w+)", name)
    if m:
        return f"{color(m.group(1))}_stained_glass_pane_top"
    m = re.fullmatch(r"glass_(\w+)", name)
    if m and color(m.group(1)) in COLORS:
        return f"{color(m.group(1))}_stained_glass"
    m = re.fullmatch(r"(wheat|carrots|potatoes)_stage_(\d)", name)
    if m:
        return f"{m.group(1)}_stage{m.group(2)}"
    m = re.fullmatch(r"destroy_stage_(\d)", name)
    if m:
        return f"destroy_stage_{m.group(1)}"
    return "?"


def target(rel):
    """rel — путь внутри textures/ без расширения. Возвращает путь в assets/minecraft/textures/ или None/'?'."""
    p = PurePosixPath(rel)
    top, name = p.parts[0], "/".join(p.parts[1:])
    if top == "blocks":
        if BLOCK_SKIP_RE.search(name):
            return None
        t = block_target(name)
        return t if t in (None, "?") else "block/" + t
    if top == "items":
        if name in ITEMS:
            t = ITEMS[name]
            return None if t is None else "item/" + t
        return "item/" + name
    if top == "entity":
        if name.startswith("banner/"):
            return None if " - Copy" in name else "entity/banner/" + name[len("banner/"):]
        if name in ENTITY:
            t = ENTITY[name]
            return None if t is None else "entity/" + t
        return "entity/" + name
    if top == "environment" and name.startswith("destroy_stage_"):
        return "block/" + name
    key = f"{top}/{name}"
    if key in OTHER:
        return OTHER[key]
    return "?"


def main():
    src, out = sys.argv[1], PurePosixPath(sys.argv[2].replace("\\", "/"))
    report = sys.argv[3] if len(sys.argv) > 3 else None
    z = zipfile.ZipFile(src)
    files = {n[len(PACK_ROOT):]: n for n in z.namelist()
             if n.startswith(PACK_ROOT + "textures/") and not n.endswith("/")}
    ok, skipped, unknown = [], [], []
    import os
    def write(rel_target, data):
        dst = os.path.join(str(out), "assets", "minecraft", "textures", *rel_target.split("/"))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "wb") as f:
            f.write(data)
    for rel, full in sorted(files.items()):
        rel = rel[len("textures/"):]
        if not rel.endswith(".png"):
            continue
        base = rel[:-4]
        t = target(base)
        if t == "?":
            unknown.append(base); continue
        if t is None:
            skipped.append(base); continue
        write(t + ".png", z.read(full))
        ok.append((base, t))
        meta = PACK_ROOT + "textures/" + rel + ".mcmeta"   # анимация рядом с текстурой
        if meta in z.namelist():
            write(t + ".png.mcmeta", z.read(meta))
    # анимации, у которых .mcmeta лежит отдельно от .png с тем же именем (fire_layer_N, portal, sea_lantern…)
    for n in z.namelist():
        if n.startswith(PACK_ROOT + "textures/blocks/") and n.endswith(".png.mcmeta"):
            base = n[len(PACK_ROOT + "textures/"):-len(".png.mcmeta")]
            if PACK_ROOT + "textures/" + base + ".png" not in z.namelist():
                t = target(base)
                if t not in (None, "?") and t in {tt for _, tt in ok}:
                    write(t + ".png.mcmeta", z.read(n))
    os.makedirs(str(out), exist_ok=True)
    with open(os.path.join(str(out), "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": PACK_FORMAT,
                            "description": "TheZone — текстуры оригинальной карты (BDcraft Cubik by Sphax, перекраска TheZone)"}},
                  f, ensure_ascii=False, indent=2)
    if PACK_ROOT + "pack_icon.png" in z.namelist():
        with open(os.path.join(str(out), "pack.png"), "wb") as f:
            f.write(z.read(PACK_ROOT + "pack_icon.png"))
    print(f"перенесено {len(ok)}, пропущено (нет аналога в Java) {len(skipped)}, не сопоставлено {len(unknown)}")
    if report:
        with open(report, "w", encoding="utf-8") as f:
            f.write("# Перенос пака TheZone: результат port_pack.py\n\n")
            f.write(f"Перенесено: {len(ok)} · пропущено: {len(skipped)} · не сопоставлено: {len(unknown)}\n\n")
            f.write("## Пропущено (в Java 1.20.1 нет отдельной текстуры)\n" + "\n".join(f"- `{s}`" for s in skipped) + "\n\n")
            f.write("## Не сопоставлено\n" + ("\n".join(f"- `{s}`" for s in unknown) or "—") + "\n\n")
            f.write("## Перенесено\n| Bedrock (1.7) | Java 1.20.1 |\n|---|---|\n" + "\n".join(f"| `{a}` | `{b}` |" for a, b in ok) + "\n")


if __name__ == "__main__":
    main()
