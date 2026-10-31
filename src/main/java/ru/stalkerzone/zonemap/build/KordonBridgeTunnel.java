package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Рухнувший ж/д мост над трассой и туннель с электрами под насыпью — по обмерам оригинала (S.T.A.L.K.E.R. Anomaly,
 * l01_escape). Размеры и места — из docs/kordon_objects/08-bridge.md и 07-tunnel.md, а собираются объекты ровными
 * шаблонами (пролёт на двутаврах, бетонные устои, арка туннеля) вместо «куч вокселей». Координаты — мировые
 * (как в .szl и kordon_points.json).
 *
 * <p>Мост (узел «Разрушенный мост», центр 267,−297): устои, пролёт с двутаврами, металлическая ферма со
 * сломанным средним пролётом, рельсы на балласте; под мостом — укрытие военных (мешки, навес, ящики) и шлагбаум.
 * <p>Туннель (узел «Туннель с электрами», центр 132,−260): настоящий проход со сводом 3×4 сквозь насыпь,
 * бетонные стены и арка; внутри — аномальное поле электр (район узла, карточка 029).
 *
 * <p>Уровень развития: 0 — руины (проломы, нет мешков и навеса), 1 — лагерь, 2 — обжитой, 3 — укреплённый.
 */
public final class KordonBridgeTunnel {
    private KordonBridgeTunnel() {}

    /** Области, где воксели уровня заменяет этот сборщик: мост и туннель (с запасом 3–4 блока). */
    public static final List<int[]> CLEAR = List.of(
            new int[] {215, -325, 326, -250},   // мост: устои, пролёт, подходы (севернее z -250 — элеватор)
            new int[] {95, -273, 142, -211});   // туннель: насыпь с проходом

    static final String CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty",
            CONC_OLD = "stalkerzone:zm_concrete_old", CONC_SLAB = "stalkerzone:zm_concrete_slab",
            CONC_ST = "stalkerzone:zm_concrete_dirty_stairs",
            RUST = "stalkerzone:zm_rust_sheet", RUST_SLAB = "stalkerzone:zm_rust_sheet_slab",
            CORR = "stalkerzone:zm_corrugated", BAGS = "stalkerzone:zm_sandbags",
            CAP = "minecraft:polished_andesite",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate",
            BARS = "minecraft:iron_bars", LOG = "minecraft:spruce_log",
            LIGHT = "minecraft:lantern", RAIL = "minecraft:rail",
            AIR = "minecraft:air", GRAVEL = "minecraft:gravel", DIRT = "minecraft:coarse_dirt",
            SMOOTH = "minecraft:smooth_stone_slab";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня: на руинах часть полотна выбита, обустройство поста растёт с уровнем. */
    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = Math.max(0, Math.min(3, lvl)); }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.set(x, y, z, st);
        }

        /** Медианная высота земли в прямоугольнике — уровень полотна над неровным рельефом. */
        int medianGround(int x0, int z0, int x1, int z1) {
            int n = (x1 - x0 + 1) * (z1 - z0 + 1), i = 0;
            int[] gs = new int[n];
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) gs[i++] = ground(x, z);
            java.util.Arrays.sort(gs);
            return gs[n / 2];
        }

        /** Блок конструкций: на уровне 0 изредка пролом. */
        void wall(int x, int y, int z, String st) {
            int h = hash(x, y, z) % 100;
            if (lvl == 0 && h < 8) { s.set(x, y, z, AIR); return; }
            s.set(x, y, z, st);
        }
    }

    /** Построить мост и туннель. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, lvl);
        clear(b);
        bridge(b);
        tunnel(b);
    }

    // ---------------------------------------------------------------- очистка

    private static void clear(S b) {
        for (int[] c : CLEAR)
            for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
                int g = b.ground(x, z);
                for (int y = g + 1; y <= g + 32; y++) b.set(x, y, z, AIR);
            }
    }

    // ---------------------------------------------------------------- мост

    /**
     * Ж/д мост над трассой: бетонные устои со скосами, пролёт на двутаврах по балласту с рельсами, ферма
     * со сломанным средним пролётом. Трасса (N→S) проходит под мостом.
     */
    private static void bridge(S b) {
        int deck = b.medianGround(235, -311, 296, -283) + 11;
        int road = b.medianGround(255, -309, 279, -285);
        int z0 = -300, z1 = -294;  // полотно 7 блоков

        // проезд под мостом
        for (int x = 255; x <= 279; x++) for (int z = -312; z <= -282; z++)
            for (int y = road + 1; y <= deck - 2; y++) b.set(x, y, z, AIR);
        for (int x = 255; x <= 279; x++) for (int z = -312; z <= -282; z++)
            b.set(x, road - 1, z, (Math.abs(x - 267) <= 3) ? "minecraft:black_concrete" : GRAVEL);

        // устои
        for (int sgn : new int[] {-1, 1}) {
            int a = sgn < 0 ? 245 : 275, c = sgn < 0 ? 255 : 285;
            for (int x = a; x <= c; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
                int g = b.ground(x, z);
                for (int y = g + 1; y <= deck; y++) b.wall(x, y, z, CONC_D);
            }
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                int edge = sgn < 0 ? a : c;
                int g = b.ground(edge, z);
                for (int y = deck; y > g + 1; y--) {
                    b.set(edge, y, z, CONC_ST + "[facing=" + (sgn < 0 ? "west" : "east") + ",half=bottom]");
                    b.set(edge, y - 1, z, CONC_D);
                }
            }
        }

        // пролёт и рельсы
        for (int x = 245; x <= 285; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean beam = z == z0 || z == z1;
                b.wall(x, deck - 2, z, beam ? CAP : CONC_D);
                b.wall(x, deck - 1, z, beam ? CAP : GRAVEL);
                b.wall(x, deck, z, x % 3 == 0 ? CONC_SLAB : CONC);
            }
            for (int z : new int[] {z0, z1}) b.wall(x, deck + 1, z, x % 4 == 0 ? RUST : BARS);
            if (x % 2 == 0) for (int z = z0 + 1; z <= z1 - 1; z++) b.set(x, deck, z, SMOOTH + "[type=bottom]");
            b.set(x, deck, -298, RAIL + "[shape=east_west]");
            b.set(x, deck, -296, RAIL + "[shape=east_west]");
        }

        // ферма (средний пролёт обрушен)
        for (int x = 245; x <= 285; x++) {
            if (x >= 260 && x <= 274) continue;
            for (int z : new int[] {z0, z1}) {
                if (x % 4 == 0) b.wall(x, deck + 1, z, RUST);
                b.wall(x, deck + 2, z, RUST);
                b.wall(x - 1, deck + 3, z, RUST);
            }
            if (x % 4 == 0) for (int y = deck + 2; y <= deck + 3; y++) b.wall(x, y, z0 + 1, RUST);
        }
        for (int x = 260; x <= 274; x += 2) {
            b.set(x, deck + 1, -299, RUST);
            b.set(x + 1, deck, -298, RUST_SLAB + "[type=top]");
            if (x % 4 == 0) b.set(x, deck + 1, -296, RUST_SLAB + "[type=bottom]");
        }

        postOnBridge(b, deck, z0, z1);
        underBridge(b);
        vehicles(b);
    }

    /** Бронетехника и сгоревшие машины у моста (объекты 1/3/4 обмеров — техника). */
    private static void vehicles(S b) {
        vehicleAt(b, "btr", 268, -319, 180, 0);
        vehicleAt(b, "zil", 315, -303, 90, 1);
        vehicleAt(b, "zhiguli", 310, -300, 90, 2);
        vehicleAt(b, "raf", 268, -267, 0, 2);
    }

    private static void postOnBridge(S b, int deck, int z0, int z1) {
        int px = 250;
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            b.set(px, deck + 1, z, BAGS); b.set(px, deck + 2, z, BAGS);
        }
        for (int x = px - 2; x < px; x++) { b.set(x, deck + 1, z0 + 1, BAGS); b.set(x, deck + 1, z1 - 1, BAGS); }
        b.set(px - 1, deck + 1, -297, "minecraft:dispenser[facing=east]");
        b.fill(px - 3, deck + 4, z0 + 1, px, deck + 4, z1 - 1, CORR);
        for (int[] c : new int[][] {{px - 3, z0 + 1}, {px, z0 + 1}, {px - 3, z1 - 1}, {px, z1 - 1}})
            b.fill(c[0], deck + 2, c[1], c[0], deck + 3, c[1], LOG);
        if (b.lvl >= 2) {
            b.set(px - 2, deck + 1, -298, CRATE);
            b.set(px - 2, deck + 1, -296, WCRATE);
            b.set(px - 1, deck + 1, -296, "minecraft:note_block");
        }
        b.mark("guard", px - 1, deck + 1, -297);
    }
    /** Пространство под мостом: укрытие военных (мешки, навес, ящики) и шлагбаум на трассе. */
    private static void underBridge(S b) {
        int g = b.ground(267, -285);
        b.fill(264, g + 1, -285, 270, g + 2, -285, CONC_OLD);
        if (b.lvl >= 1) for (int x = 264; x <= 270; x++)
            b.set(x, g + 3, -285, (x & 1) == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
        b.mark("gate", 267, g + 1, -285);
        b.mark("gate", 262, g + 1, -285);

        int ux = 269, uz = -303;
        int ug = b.ground(ux, uz);
        for (int[] c : new int[][] {{ux - 3, uz}, {ux + 3, uz}, {ux, uz - 1}, {ux, uz + 1}})
            for (int y = ug + 1; y <= ug + 2; y++) b.set(c[0], y, c[1], BAGS);
        b.set(ux - 2, ug + 1, uz, BAGS); b.set(ux + 2, ug + 1, uz, BAGS);
        if (b.lvl >= 1) {
            b.fill(ux - 3, ug + 3, uz - 2, ux + 3, ug + 3, uz + 2, CORR);
            for (int[] c : new int[][] {{ux - 3, uz - 2}, {ux + 3, uz - 2}, {ux - 3, uz + 2}, {ux + 3, uz + 2}})
                b.fill(c[0], ug + 1, c[1], c[0], ug + 2, c[1], LOG);
        }
        if (b.lvl >= 2) {
            b.set(ux - 1, ug + 1, uz + 1, CRATE);
            b.set(ux + 1, ug + 1, uz - 1, WCRATE);
            b.set(ux, ug + 1, uz, WCRATE);
            b.set(ux + 1, ug + 1, uz + 1, "minecraft:spruce_slab[type=top]");
        }
        b.mark("bed", ux + 1, ug + 1, uz + 1);
        b.set(ux, ug + 3, uz, LIGHT);
    }

    /** Ставит технику {@link Vehicles} в мировую точку, если вид есть в списке (поворот, износ). */
    private static void vehicleAt(S b, String kind, int x, int z, int rot, int wear) {
        int idx = -1;
        for (int i = 0; i < Vehicles.ALL.length; i++) if (Vehicles.ALL[i].equals(kind)) idx = i;
        if (idx < 0) return;
        B v = new B(b.s, x, z, rot, 0x5ADC0DE + hash(x, 0, z), Math.max(1, b.lvl));
        Vehicles.vehicle(v, Vehicles.ALL[idx], 0, 0, 0, null, wear);
    }

    // ---------------------------------------------------------------- туннель

    /**
     * Туннель под насыпью: бетонный проход со сводом 3×4 сквозь насыпь (N↔S), арочный потолок, порталы
     * по торцам. Внутри — поле электр (район узла, карточка 029); здесь строится только сам проход.
     */
    private static void tunnel(S b) {
        int floor = b.medianGround(126, -269, 138, -263) + 1;
        int xw = 130, xe = 134;          // внутренние грани стен (проход 3 блока)
        int z0 = -268, z1 = -264;        // сквозь насыпь

        // насыпь
        for (int x = 126; x <= 138; x++) for (int z = -269; z <= -263; z++) {
            int g = b.ground(x, z);
            for (int y = g + 1; y <= floor + 9; y++) b.set(x, y, z, DIRT);
        }
        // проход
        for (int x = xw + 1; x <= xe - 1; x++) for (int z = z0; z <= z1; z++) {
            for (int y = floor; y <= floor + 4; y++) b.set(x, y, z, AIR);
            b.set(x, floor - 1, z, "minecraft:gravel");
        }
        // бетонные стены и арочный свод
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int x = xw - 1; x <= xw; x++) for (int y = floor; y <= floor + 3; y++) b.wall(x, y, z, CONC);
            for (int x = xe; x <= xe + 1; x++) for (int y = floor; y <= floor + 3; y++) b.wall(x, y, z, CONC);
            b.wall(xw + 1, floor + 3, z, CONC_ST + "[facing=west,half=bottom]");
            b.wall(xe - 1, floor + 3, z, CONC_ST + "[facing=east,half=bottom]");
            for (int x = xw + 1; x <= xe - 1; x++) b.wall(x, floor + 4, z, CONC);
        }
        // порталы
        for (int z : new int[] {z0 - 1, z1 + 1}) {
            for (int x = xw - 1; x <= xe + 1; x++) for (int y = floor; y <= floor + 4; y++) b.wall(x, y, z, CONC_OLD);
            for (int x = xw + 1; x <= xe - 1; x++) for (int y = floor; y <= floor + 3; y++) b.set(x, y, z, AIR);
        }
        // мусор и влага
        for (int z = z0; z <= z1; z++) {
            b.set(xw + 1, floor, z, "minecraft:mossy_cobblestone");
            if (hash(z, 2, z) % 3 == 0) b.set(xe - 1, floor, z, DIRT);
        }
        if (b.lvl >= 2) b.set(xw + 1, floor + 3, (z0 + z1) / 2, LIGHT + "[hanging=true]");
        b.mark("guard", xw + 1, floor, z0 - 1);
    }
}
