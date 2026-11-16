package ru.stalkerzone.zonemap.build;

/**
 * Словарь «материал → блок» (решение а6, Э2-13). Сборщик пишет материал, а не id блока; словарь отдаёт
 * состояние блока для текущего режима:
 * <ul>
 *   <li>{@link Mode#THEZONE} (по умолчанию) — ванильный блок, который ресурспак TheZone рисует этим материалом
 *       (таблица {@code docs/thezone/02-materials.md}): наши постройки не отличаются от построек TheZone;</li>
 *   <li>{@link Mode#MOD} — блок мода {@code stalkerzone:zm_*} (или прежний ванильный вид, если своего блока нет).</li>
 * </ul>
 * Материалы с пометкой {@code modOnly} — формы, которых нет в ванили под пак (например, полублок мешков):
 * в обоих режимах ставится блок мода.
 * <p>Режим переключается в одном месте: {@link #mode(Mode)} или свойство JVM {@code -Dstalkerzone.materials=mod}.
 * <p>Пример: {@code b.put(x, y, z, Materials.SANDBAG.id());},
 * ступени: {@code Materials.ROOF_SHEET_STAIRS.with("facing=south,half=bottom")}.
 */
public enum Materials {
    // ---------------------------------------------------------------- бетон
    /** Серый бетон — главный материал TheZone: стены, площадки, дороги. */
    BETON("minecraft:quartz_block", "stalkerzone:zm_concrete"),
    /** Бетонная плита (светлее): перекрытия, цоколи, промышленные стены. */
    BETON_PLATE("minecraft:iron_block", "stalkerzone:zm_concrete_plate"),
    /** Гладкий бетонный пол. */
    BETON_FLOOR("minecraft:smooth_stone", "stalkerzone:zm_concrete_floor"),
    /** Плита бетона в полблока: полы, кровли, бордюры, ступени. */
    BETON_SLAB("minecraft:smooth_stone_slab[type=bottom]", "stalkerzone:zm_concrete_slab[type=bottom]"),
    /** Треснувший бетон с выбоиной: разрушенные стены, асфальт с ямами. */
    BETON_CRACKED("minecraft:chiseled_stone_bricks", "stalkerzone:zm_concrete_old"),
    /** Бетонные блоки (ФБС): стенки, змейка на дороге, ограды. */
    BETON_BLOCK("minecraft:stone_bricks", "stalkerzone:zm_concrete_wall"),
    BETON_BLOCK_CRACKED("minecraft:cracked_stone_bricks", "stalkerzone:zm_concrete_dirty"),
    BETON_BLOCK_MOSSY("minecraft:mossy_stone_bricks", "stalkerzone:zm_concrete_old"),
    BETON_BLOCK_SLAB("minecraft:stone_brick_slab[type=bottom]", "stalkerzone:zm_concrete_wall_slab[type=bottom]"),
    /** Булыжник: фундаменты, руины, насыпи. */
    FOUNDATION("minecraft:cobblestone", "stalkerzone:zm_base_stone"),

    // ---------------------------------------------------------------- штукатурка
    PLASTER_WHITE("minecraft:white_terracotta", "stalkerzone:zm_plaster"),
    PLASTER_BLUE("minecraft:blue_terracotta", "stalkerzone:zm_plaster"),
    PLASTER_LIGHT_BLUE("minecraft:light_blue_terracotta", "stalkerzone:zm_plaster"),
    PLASTER_RED("minecraft:red_terracotta", "stalkerzone:zm_plaster_brick"),
    PLASTER_GREEN("minecraft:green_terracotta", "stalkerzone:zm_plaster"),
    PLASTER_BROWN("minecraft:brown_terracotta", "stalkerzone:zm_plaster_old"),
    /** Штукатурка с трещиной. */
    PLASTER_CRACKED("minecraft:quartz_pillar[axis=y]", "stalkerzone:zm_plaster_old"),

    // ---------------------------------------------------------------- плитка
    TILE_ORANGE("minecraft:orange_terracotta", "stalkerzone:zm_tile"),
    /** Зелёный кафель: санузлы, лаборатории, больница. */
    TILE_GREEN("minecraft:emerald_block", "stalkerzone:zm_tile"),
    TILE_BLUE("minecraft:lapis_block", "stalkerzone:zm_tile"),
    TILE_RED("minecraft:red_wool", "stalkerzone:zm_tile"),

    // ---------------------------------------------------------------- кровля и профлист
    /** Белый гофрированный лист (шифер, профнастил): кровли сараев и ангаров. */
    ROOF_SHEET("minecraft:nether_bricks", "stalkerzone:zm_shifer"),
    ROOF_SHEET_SLAB("minecraft:nether_brick_slab[type=bottom]", "stalkerzone:zm_shifer_slab[type=bottom]"),
    ROOF_SHEET_STAIRS("minecraft:nether_brick_stairs[facing=north,half=bottom]", "stalkerzone:zm_shifer_stairs[facing=north,half=bottom]"),
    /** Забор из профлиста. */
    ROOF_SHEET_FENCE("minecraft:nether_brick_fence", "minecraft:nether_brick_fence"),

    // ---------------------------------------------------------------- кирпич и дерево
    BRICK_RED("minecraft:bricks", "stalkerzone:zm_brick_red"),
    /** Белый силикатный кирпич. */
    BRICK_WHITE("minecraft:chiseled_quartz_block", "stalkerzone:zm_brick_silicate"),
    /** Серые старые доски. */
    PLANKS_OLD("minecraft:oak_planks", "stalkerzone:zm_planks_gray"),
    PLANKS_OLD_SLAB("minecraft:oak_slab[type=bottom]", "stalkerzone:zm_planks_gray_slab[type=bottom]"),
    FENCE_WOOD("minecraft:oak_fence", "stalkerzone:zm_planks_gray_fence"),
    /** Деревянный столб (ноги вышки). */
    POST_WOOD("minecraft:oak_log[axis=y]", "stalkerzone:zm_wood_post"),

    // ---------------------------------------------------------------- укрепления
    /** Мешки с песком: укрепления, блокпосты. */
    SANDBAG("minecraft:sponge", "stalkerzone:zm_sandbags"),
    /** Полублок мешков — в ванили под пак формы нет, ставится блок мода. */
    SANDBAG_SLAB("stalkerzone:zm_sandbags_slab[type=bottom]", "stalkerzone:zm_sandbags_slab[type=bottom]", true),
    /** Жёлто-чёрные полосы: разметка опасности, ограждения. */
    WARN_STRIPES("minecraft:gold_block", "minecraft:yellow_concrete"),
    /** Шлагбаум: красная и белая полосы стрелы. */
    BARRIER_RED("minecraft:red_terracotta", "minecraft:red_concrete"),
    BARRIER_WHITE("minecraft:white_terracotta", "minecraft:white_concrete"),
    /** Решётка из прутьев, колючка. */
    BARS("minecraft:iron_bars", "minecraft:iron_bars"),
    /** Колючая проволока (спираль): чередуется с {@link #BARS}. */
    BARBED_WIRE("minecraft:cobweb", "minecraft:cobweb"),

    // ---------------------------------------------------------------- тонкие заборы (исключение а6, Э2-16)
    // Заборы TheZone толстые (полный блок); тонких под пак нет, поэтому тонкие заборы — блоки мода в обоих режимах.
    /** Тонкий дощатый забор. */
    FENCE_THIN_BOARDS("stalkerzone:zm_fence_boards_fence", "stalkerzone:zm_fence_boards_fence", true),
    /** Сетка-рабица. */
    FENCE_THIN_MESH("stalkerzone:zm_grating_pane", "stalkerzone:zm_grating_pane", true),
    /** Бетонная плита ПО-2. */
    FENCE_THIN_CONCRETE("stalkerzone:concrete_fence", "stalkerzone:concrete_fence", true),

    /** Разбитое стекло окна. */
    GLASS_BROKEN("minecraft:glass_pane", "stalkerzone:zm_glass_dirty_pane"),
    LADDER("minecraft:ladder[facing=north]", "minecraft:ladder[facing=north]");

    /** Режим словаря: под пак TheZone (ваниль) или блоками мода. */
    public enum Mode { THEZONE, MOD }

    private static volatile Mode mode = "mod".equalsIgnoreCase(System.getProperty("stalkerzone.materials")) ? Mode.MOD : Mode.THEZONE;

    public static Mode mode() { return mode; }

    /** Переключить режим (единственное место). Возвращает прежний — для тестов и превью. */
    public static Mode mode(Mode m) { Mode old = mode; mode = m == null ? Mode.THEZONE : m; return old; }

    /** Состояние блока в режиме THEZONE и в режиме MOD. */
    public final String thezone, mod;
    /** Только блок мода (нет ванильной формы под пак). */
    public final boolean modOnly;

    Materials(String thezone, String mod) { this(thezone, mod, false); }

    Materials(String thezone, String mod, boolean modOnly) { this.thezone = thezone; this.mod = mod; this.modOnly = modOnly; }

    /** Состояние блока для текущего режима. */
    public String id() { return id(mode); }

    public String id(Mode m) { return m == Mode.MOD || modOnly ? mod : thezone; }

    /** То же с другими свойствами: {@code with("facing=south,half=bottom")} заменяет свойства по умолчанию. */
    public String with(String props) {
        String s = id();
        int i = s.indexOf('[');
        return (i < 0 ? s : s.substring(0, i)) + "[" + props + "]";
    }
}
