package ru.stalkerzone.block;

/** Материалы Зоны (сгенерировано tools/zone_blocks/gen.py из materials.py — правьте там). */
public enum ZoneMat {
    PLANKS_GRAY("planks_gray", Kind.WOOD, true, true, false, false),
    BOARDS_OLD("boards_old", Kind.WOOD, false, false, false, false),
    PLANKS_FLOOR("planks_floor", Kind.WOOD, false, false, false, false),
    BOARDS_VERT("boards_vert", Kind.WOOD, false, false, false, false),
    FENCE_BOARDS("fence_boards", Kind.WOOD, true, true, false, false),
    WOOD_POST("wood_post", Kind.WOOD, false, true, false, false),
    WOOD_PAINTED("wood_painted", Kind.WOOD, false, false, false, false),
    VERANDA("veranda", Kind.WOOD, false, false, false, false),
    SHIFER("shifer", Kind.STONE, false, false, false, false),
    SHIFER_LIGHT("shifer_light", Kind.STONE, false, false, false, false),
    SHIFER_DARK("shifer_dark", Kind.STONE, false, false, false, false),
    ROOF_TAR("roof_tar", Kind.STONE, false, false, false, false),
    ROOF_PANEL("roof_panel", Kind.STONE, false, false, false, false),
    ROOF_RED("roof_red", Kind.METAL, false, false, false, false),
    PLASTER("plaster", Kind.STONE, false, false, false, false),
    PLASTER_BRICK("plaster_brick", Kind.STONE, false, false, false, false),
    PLASTER_OLD("plaster_old", Kind.STONE, false, false, false, false),
    BRICK_RED("brick_red", Kind.STONE, false, false, false, false),
    BRICK_DIRTY("brick_dirty", Kind.STONE, false, false, false, false),
    BRICK_SILICATE("brick_silicate", Kind.STONE, false, false, false, false),
    BRICK_OLD("brick_old", Kind.STONE, false, false, false, false),
    BASE_STONE("base_stone", Kind.STONE, false, false, false, false),
    CONCRETE("concrete", Kind.STONE, false, false, false, false),
    CONCRETE_OLD("concrete_old", Kind.STONE, false, false, false, false),
    CONCRETE_DIRTY("concrete_dirty", Kind.STONE, false, false, false, false),
    CONCRETE_WALL("concrete_wall", Kind.STONE, false, false, false, false),
    CONCRETE_PANEL("concrete_panel", Kind.STONE, false, false, false, false),
    CONCRETE_PLATE("concrete_plate", Kind.STONE, false, false, false, false),
    CONCRETE_FLOOR("concrete_floor", Kind.STONE, false, false, false, false),
    PANEL_PRIPYAT("panel_pripyat", Kind.STONE, false, false, false, false),
    PANEL_PRIPYAT2("panel_pripyat2", Kind.STONE, false, false, false, false),
    STAIR_STEPS("stair_steps", Kind.STONE, false, false, false, false),
    FACTORY_WALL("factory_wall", Kind.STONE, false, false, false, false),
    RUST("rust", Kind.METAL, false, false, false, false),
    RUST_SHEET("rust_sheet", Kind.METAL, false, false, false, false),
    CORRUGATED("corrugated", Kind.METAL, false, false, false, false),
    CORRUGATED2("corrugated2", Kind.METAL, false, false, false, false),
    METAL_PLATE("metal_plate", Kind.METAL, false, false, false, false),
    METAL_GREEN("metal_green", Kind.METAL, false, false, false, false),
    METAL_GREEN_RUST("metal_green_rust", Kind.METAL, false, false, false, false),
    HANGAR("hangar", Kind.METAL, false, false, false, false),
    CONTAINER("container", Kind.METAL, false, false, false, false),
    GARAGE("garage", Kind.METAL, false, false, false, false),
    GATE_METAL("gate_metal", Kind.METAL, false, false, false, false),
    GRATING("grating", Kind.METAL, false, false, true, false),
    TILE("tile", Kind.STONE, false, false, false, false),
    LINOLEUM("linoleum", Kind.STONE, false, false, false, false),
    RUBBLE("rubble", Kind.STONE, false, false, false, false),
    ROCKS("rocks", Kind.STONE, false, false, false, false),
    GLASS_DIRTY("glass_dirty", Kind.GLASS, false, false, true, false),
    WINDOW("window", Kind.GLASS, false, false, true, false),
    DOOR_GREEN("door_green", Kind.WOOD, false, false, false, true),
    DOOR_METAL("door_metal", Kind.METAL, false, false, false, true),
    SANDBAGS("sandbags", Kind.STONE, false, false, false, false),
    MATTRESS("mattress", Kind.WOOD, false, false, false, false),
    BARK_PINE("bark_pine", Kind.WOOD, false, false, false, false),
    BARK_BIRCH("bark_birch", Kind.WOOD, false, false, false, false),
    BARK_DEAD("bark_dead", Kind.WOOD, false, false, false, false),
    GROUND_GRASS("ground_grass", Kind.STONE, false, false, false, false),
    GROUND_EARTH("ground_earth", Kind.STONE, false, false, false, false);

    public enum Kind { WOOD, STONE, METAL, GLASS }

    public final String key;
    public final Kind kind;
    public final boolean trapdoor, fence, pane, door;

    ZoneMat(String key, Kind kind, boolean trapdoor, boolean fence, boolean pane, boolean door) {
        this.key = key; this.kind = kind; this.trapdoor = trapdoor; this.fence = fence; this.pane = pane; this.door = door;
    }

    public static ZoneMat of(String key) {
        for (ZoneMat m : values()) if (m.key.equals(key)) return m;
        return null;
    }
}
