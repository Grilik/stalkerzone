package ru.stalkerzone.zonemap.build;

/**
 * Формы FramedBlocks (П-04) для сборщиков: скат, угол, плита, панель… с «камуфляжем» — текстурой любого блока
 * (наш {@code stalkerzone:zm_*} или ванильный). Чистая Java: сборщик получает строку состояния блока для
 * {@code BlockSink.set}, мир её ставит.
 *
 * <p>Если FramedBlocks установлен ({@link #loaded}, ставит мод при запуске), строка — framed-блок с NBT камуфляжа:
 * {@code framedblocks:framed_slope[facing=north,type=bottom]{camo:{type:"framedblocks:block",state:{Name:"stalkerzone:zm_shifer"}}}}.
 * Генерация мира ставит блок и кладёт NBT в его block entity ({@code PrefabLayer}). Без FramedBlocks — запасной
 * ванильный вариант того же материала: ступени, плита или полный блок ({@link #fallback}).
 *
 * <p>Пример в сборщике: {@code b.set(x, y, z, Framed.slope("stalkerzone:zm_shifer", "north", false));}
 */
public final class Framed {
    private Framed() {}

    /** FramedBlocks загружен (ставит StalkerZoneMod через ModList). В тестах и превью — false, если не задано. */
    public static volatile boolean loaded;

    /** Формы, которые нужны постройкам; id блока FramedBlocks 9.4 (Forge 1.20.1) и что ставить без него. */
    public enum Shape {
        /** Скат на всю клетку: высокая сторона — facing. Без мода — ступени. */
        SLOPE("framed_slope"),
        /** Внешний угол ската (конёк вальмовой крыши). Без мода — угловые ступени outer. */
        CORNER_SLOPE("framed_corner_slope"),
        /** Внутренний угол ската (ендова). Без мода — угловые ступени inner. */
        INNER_CORNER_SLOPE("framed_inner_corner_slope"),
        /** Обычные ступени. Без мода — ступени. */
        STAIRS("framed_stairs"),
        /** Плита (низ/верх). Без мода — плита. */
        SLAB("framed_slab"),
        /** Полплиты вдоль края клетки (facing — сторона). Без мода — плита. */
        SLAB_EDGE("framed_slab_edge"),
        /** Вертикальная панель в половину клетки у стороны facing (рамы, тонкие стены). Без мода — полный блок. */
        PANEL("framed_panel"),
        /** Тонкая доска на полу/потолке. Без мода — плита. */
        FLOOR_BOARD("framed_floor_board"),
        /** Тонкая доска на стене у стороны facing (наличники, обшивка). Без мода — ничего (воздух). */
        WALL_BOARD("framed_wall_board"),
        /** Столб по оси (x, y, z — в facing). Без мода — полный блок. */
        PILLAR("framed_pillar"),
        /** Полный блок с текстурой. Без мода — сам блок камуфляжа. */
        CUBE("framed_cube");

        public final String id;
        Shape(String id) { this.id = "framedblocks:" + id; }
    }

    /** Скат: high side — facing (north/south/west/east), top — перевёрнутый (под карнизом). */
    public static String slope(String camo, String facing, boolean top) { return of(Shape.SLOPE, camo, facing, top); }

    public static String cornerSlope(String camo, String facing, boolean top) { return of(Shape.CORNER_SLOPE, camo, facing, top); }

    public static String innerCornerSlope(String camo, String facing, boolean top) { return of(Shape.INNER_CORNER_SLOPE, camo, facing, top); }

    public static String slab(String camo, boolean top) { return of(Shape.SLAB, camo, "north", top); }

    public static String panel(String camo, String facing) { return of(Shape.PANEL, camo, facing, false); }

    /**
     * Форма с камуфляжем. facing — north/south/west/east (для PILLAR — ось x/y/z), top — верхняя половина
     * клетки (плиты, ступени, скаты вниз). camo — блок, чью текстуру взять, можно с состоянием: "minecraft:oak_log[axis=y]".
     */
    public static String of(Shape s, String camo, String facing, boolean top) {
        return loaded ? framed(s, camo, facing, top) : fallback(s, camo, facing, top);
    }

    /** Строка framed-блока с NBT камуфляжа (без проверки, загружен ли мод). */
    public static String framed(Shape s, String camo, String facing, boolean top) {
        String props = switch (s) {
            case SLOPE -> "facing=" + facing + ",type=" + (top ? "top" : "bottom");
            case CORNER_SLOPE, INNER_CORNER_SLOPE -> "facing=" + facing + ",type=" + (top ? "top" : "bottom");
            case STAIRS -> "facing=" + facing + ",half=" + (top ? "top" : "bottom");
            case SLAB, FLOOR_BOARD -> "top=" + top;
            case SLAB_EDGE -> "facing=" + facing + ",top=" + top;
            case PANEL, WALL_BOARD -> "facing=" + facing;
            case PILLAR -> "axis=" + axis(facing);
            case CUBE -> "";
        };
        return s.id + (props.isEmpty() ? "" : "[" + props + "]") + camoNbt(camo);
    }

    /** NBT камуфляжа FramedBlocks 9.4: {camo:{type:"framedblocks:block",state:{Name:…,Properties:{…}}}}. */
    public static String camoNbt(String camo) {
        int br = camo.indexOf('[');
        String name = br < 0 ? camo : camo.substring(0, br);
        StringBuilder st = new StringBuilder("{Name:\"").append(name).append('"');
        if (br >= 0 && camo.endsWith("]")) {
            st.append(",Properties:{");
            String[] kv = camo.substring(br + 1, camo.length() - 1).split(",");
            for (int i = 0; i < kv.length; i++) {
                String[] p = kv[i].split("=", 2);
                if (i > 0) st.append(',');
                st.append(p[0].trim()).append(":\"").append(p.length > 1 ? p[1].trim() : "").append('"');
            }
            st.append('}');
        }
        return "{camo:{type:\"framedblocks:block\",state:" + st.append('}') + "}}";
    }

    /** Ванильный (или наш) запасной вариант той же формы из материала камуфляжа. */
    public static String fallback(Shape s, String camo, String facing, boolean top) {
        String full = camo;
        String base = camo.indexOf('[') < 0 ? camo : camo.substring(0, camo.indexOf('['));
        String half = top ? "top" : "bottom";
        return switch (s) {
            case SLOPE, STAIRS -> stairsOf(base, full, facing, half, "straight");
            case CORNER_SLOPE -> stairsOf(base, full, facing, half, "outer_left");
            case INNER_CORNER_SLOPE -> stairsOf(base, full, facing, half, "inner_left");
            case SLAB, SLAB_EDGE, FLOOR_BOARD -> slabOf(base, full, half);
            case WALL_BOARD -> "minecraft:air";
            case PANEL, PILLAR, CUBE -> full;
        };
    }

    /** Ступени материала (у наших zm_* есть *_stairs, у ванильных — по таблице), иначе полный блок. */
    private static String stairsOf(String base, String full, String facing, String half, String shape) {
        String st = variant(base, "_stairs");
        return st == null ? full : st + "[facing=" + facing + ",half=" + half + ",shape=" + shape + "]";
    }

    private static String slabOf(String base, String full, String half) {
        String sl = variant(base, "_slab");
        return sl != null ? sl + "[type=" + half + "]" : full;
    }

    /** Ступени/плита материала или null, если такой формы у материала нет. */
    static String variant(String base, String suffix) {
        if (base.startsWith("stalkerzone:zm_")) return base + suffix;   // ZoneBlocks: у каждого ZoneMat есть _stairs и _slab
        String v = VANILLA.get(base);
        return v == null ? null : "minecraft:" + v + suffix;
    }

    private static final java.util.Map<String, String> VANILLA = new java.util.HashMap<>();
    static {
        for (String w : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"})
            VANILLA.put("minecraft:" + w + "_planks", w);
        String[][] m = {{"bricks", "brick"}, {"stone_bricks", "stone_brick"}, {"mossy_stone_bricks", "mossy_stone_brick"},
                {"cobblestone", "cobblestone"}, {"mossy_cobblestone", "mossy_cobblestone"}, {"stone", "stone"},
                {"andesite", "andesite"}, {"polished_andesite", "polished_andesite"}, {"granite", "granite"},
                {"diorite", "diorite"}, {"sandstone", "sandstone"}, {"deepslate_bricks", "deepslate_brick"},
                {"cobbled_deepslate", "cobbled_deepslate"}, {"mud_bricks", "mud_brick"}, {"cut_copper", "cut_copper"},
                {"weathered_cut_copper", "weathered_cut_copper"}, {"oxidized_cut_copper", "oxidized_cut_copper"}};
        for (String[] p : m) VANILLA.put("minecraft:" + p[0], p[1]);
    }

    private static String axis(String facing) {
        return switch (facing) {
            case "x", "east", "west" -> "x";
            case "z", "north", "south" -> "z";
            default -> "y";
        };
    }

    /** Разделить строку сборщика на состояние блока и NBT block entity ("" — нет NBT). */
    public static String[] split(String s) {
        int br = s.indexOf('{');
        return br < 0 ? new String[] {s, ""} : new String[] {s.substring(0, br), s.substring(br)};
    }
}
