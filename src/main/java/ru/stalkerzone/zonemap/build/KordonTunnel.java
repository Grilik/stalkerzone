package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Туннель с «электрами» под ж/д насыпью (П-23) — аномальная зона узла 7 (смарт-террейн Anomaly {@code esc_smart_terrain_4_9},
 * центр (132, −260)) и две заставы одиночек у его входов. Обмеры — {@code docs/kordon_objects/07-tunnel.md}.
 *
 * <p><b>Решение пользователя:</b> зона без уровней — один детализированный объект по оригиналу. Сами «электры» ставит логика
 * (район аномалий узла 7); сборщик только отмечает 5 мест {@code anomaly} внутри туннеля.
 *
 * <p><b>Геометрия</b> (ось север—юг, x = 132; z растёт к югу):
 * <ul>
 *   <li>северный портал z −268..−267, проход z −266..−258, южный портал z −257..−256 (13 блоков насыпи); граница
 *   {@code gate} узла (124, −268)..(140, −252) и точки AI-узла (караулы z −268, −265, −255; патруль −258) лежат в этой полосе;</li>
 *   <li>проход шириной 7 (x 129..135), свод ступенчатой аркой: по оси 5 блоков, у стен 3; бетонные стены 2 блока (x 127..128 и
 *   136..137), фасады порталов с крыльями x 126..138 высотой до 10 над полом (обмер: 9–11);</li>
 *   <li>над сводом — насыпь с балластом и двумя рельсами вдоль x на высоте не ниже пола + 9; рельсы идут по всей площадке,
 *   высота повторяет карту высот (уклон 1 блок на блок);</li>
 *   <li>внутри — остов «Жигулей» вдоль западной стены (обмер: техника 62 вокселя в x 129..133, z −262..−258), обломки у стен,
 *   обгоревшие следы вокруг мест аномалий; осевая полоса x 132..134 свободна.</li>
 * </ul>
 *
 * <p><b>Площадка:</b> рельеф правит {@link Cut} — два двора перед порталами выравниваются на отметку пола туннеля с откосом 8 блоков
 * (выемка в насыпи у порталов). Пол = 25-й процентиль земли дворов до правки ({@link #floorOf}); после правки дворы ровные, и та же
 * функция даёт тот же пол — состояния между сборщиком и правкой не нужно.
 *
 * <p><b>Заставы</b> (по одной у входа, по разные стороны насыпи) — костёр, мешки, палатка, пост-гнездо, землянка-укрытие. Места каждой:
 * {@code guard} ×2, {@code rest} ×2, {@code bed} ×3, {@code shelter} ×2 и {@code cook} ×1. Костёр южной заставы стоит ровно в точке
 * {@code campfire} узла (131, −254) — её ставит и декор узла.
 *
 * <p>Уровень развития 0–3: 0 — брошено (пробоины в фасадах, холодный костёр, нет мешков и света), 1 — живая застава, 2 — мешки, фонари,
 * 3 — второй ряд мешков и колючка.
 */
public final class KordonTunnel {
    private KordonTunnel() {}

    /** Ось туннеля по x и границы по z (мировые координаты). */
    public static final int AX = 132, ZN0 = -268, ZN1 = -267, ZI0 = -266, ZI1 = -258, ZS0 = -257, ZS1 = -256;

    /** Дворы заставы перед порталами: ровные на отметке пола (ядро), откос — {@link #MARGIN}. {x0, z0, x1, z1}. */
    public static final int[] NORTH_YARD = {121, -284, 143, -269}, SOUTH_YARD = {121, -255, 143, -240};
    public static final int MARGIN = 8;

    /** Область сборщика: туннель, рельсы и дворы с откосами (воксели оригинала внутри заменяются). */
    public static final List<int[]> CLEAR = List.of(new int[] {106, -296, 158, -228});

    /** Границы рельсового полотна по x. */
    static final int RAIL_X0 = 110, RAIL_X1 = 154, RAIL_Z0 = -263, RAIL_Z1 = -261, BED_Z0 = -265, BED_Z1 = -259, CROWN_Z = -262;

    /** Места внутри туннеля, куда логика ставит «электры» (по оси проход свободен). */
    public static final int[][] ANOMALY = {{133, -265}, {135, -263}, {132, -262}, {134, -260}, {133, -258}};

    static final String CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty", CONC_OLD = "stalkerzone:zm_concrete_old",
            CONC_ST = "stalkerzone:zm_concrete_dirty_stairs", FLOOR = "stalkerzone:zm_concrete_floor",
            CAP = "minecraft:polished_andesite", CAP_ST = "minecraft:polished_andesite_stairs",
            BLACK = "minecraft:black_concrete", COAL = "minecraft:coal_block",
            GRAVEL = "minecraft:gravel", DIRT = "minecraft:dirt", COARSE = "minecraft:coarse_dirt", AIR = "minecraft:air",
            RAIL = "minecraft:rail", RUBBLE = "stalkerzone:zm_rubble", RUBBLE_SL = "stalkerzone:zm_rubble_slab[type=bottom]",
            TIRES = "stalkerzone:tires", BARREL = "stalkerzone:rusty_barrel", RAD = "stalkerzone:radioactive_barrel",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate",
            BAGS = "stalkerzone:zm_sandbags", CORR = "stalkerzone:zm_corrugated", POST = "stalkerzone:zm_wood_post_fence",
            MATTRESS = "stalkerzone:zm_mattress_slab[type=bottom]", BARS = "minecraft:iron_bars",
            LANTERN = "minecraft:lantern[hanging=true]", LOG = "minecraft:spruce_log[axis=x]",
            FIRE = "minecraft:campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]",
            FIRE_OFF = "minecraft:campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    // ---------------------------------------------------------------- площадка: рельеф

    /**
     * Отметка пола туннеля (верхний блок земли дворов): 25-й процентиль высот ядер обоих дворов. Низ, а не медиана — двор
     * вплотную к насыпи наполовину на её откосе; процентиль берёт уровень поля перед насыпью.
     */
    public static int floorOf(Prefabs.Ground g) {
        int nx = (NORTH_YARD[2] - NORTH_YARD[0]) / 2 + 1, nz = (NORTH_YARD[3] - NORTH_YARD[1]) / 2 + 1;
        int sx = (SOUTH_YARD[2] - SOUTH_YARD[0]) / 2 + 1, sz = (SOUTH_YARD[3] - SOUTH_YARD[1]) / 2 + 1;
        int[] hs = new int[nx * nz + sx * sz];
        int i = 0;
        for (int[] r : new int[][] {NORTH_YARD, SOUTH_YARD})
            for (int x = r[0]; x <= r[2]; x += 2) for (int z = r[1]; z <= r[3]; z += 2) hs[i++] = g.at(x, z);
        java.util.Arrays.sort(hs, 0, i);
        return hs[i / 4];
    }

    /** Выемка у порталов: дворы заставы ровные на отметке пола, откос по краю — плавный (как плато деревни). */
    public static final class Cut implements Site.Shaper {
        private int floor;
        private boolean ready;

        @Override public int[] bounds() {
            return new int[] {NORTH_YARD[0] - MARGIN, NORTH_YARD[1] - MARGIN, SOUTH_YARD[2] + MARGIN, SOUTH_YARD[3] + MARGIN};
        }

        @Override public void prepare(Prefabs.Ground g) { floor = floorOf(g); ready = true; }

        /** Верхний блок земли дворов или {@link Integer#MIN_VALUE}, пока рельеф не считан. */
        public int floor() { return ready ? floor : Integer.MIN_VALUE; }

        static double dist(int[] r, int x, int z) {
            double dx = Math.max(0, Math.max(r[0] - x, x - r[2])), dz = Math.max(0, Math.max(r[1] - z, z - r[3]));
            return Math.hypot(dx, dz);
        }

        @Override public double shape(int x, int z, double orig) {
            double d = Math.min(dist(NORTH_YARD, x, z), dist(SOUTH_YARD, x, z));
            double y = floor + 0.5;
            if (d <= 0) return y;
            if (d >= MARGIN) return orig;
            double t = d / MARGIN;
            t = t * t * (3 - 2 * t);
            return y * (1 - t) + orig * t;
        }
    }

    /** Новая правка рельефа для площадки tunnel. */
    public static Cut cut() { return new Cut(); }

    // ---------------------------------------------------------------- сборка

    private static final class T {
        final BlockSink s;
        final int lvl, f;
        final int[] ry = new int[RAIL_X1 - RAIL_X0 + 1];

        T(BlockSink s, int lvl) {
            this.s = s;
            this.lvl = Math.max(0, Math.min(3, lvl));
            this.f = floorOf(s::ground);
            // высота рельсов: по земле на оси полотна, над туннелем — не ниже пола + 9; уклон не круче 1:1
            for (int x = RAIL_X0; x <= RAIL_X1; x++) {
                int b = s.ground(x, CROWN_Z) + 1;
                if (x >= 124 && x <= 140) b = Math.max(b, f + 9);
                ry[x - RAIL_X0] = b;
            }
            for (int i = 1; i < ry.length; i++) ry[i] = Math.max(ry[i], ry[i - 1] - 1);
            for (int i = ry.length - 2; i >= 0; i--) ry[i] = Math.max(ry[i], ry[i + 1] - 1);
        }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.set(x, y, z, st);
        }

        int railY(int x) { return ry[Math.max(RAIL_X0, Math.min(RAIL_X1, x)) - RAIL_X0]; }

        /** Верх насыпи над туннелем в колонке (x, z): гребень на уровне балласта, откосы 1:1, но не ниже верха перекрытия. */
        int cover(int x, int z) {
            int crown = railY(x) - 1;
            return Math.max(f + 7, crown - Math.max(0, Math.abs(z - CROWN_Z) - 3));
        }

        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }
    }

    /** Построить туннель и заставы. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        T t = new T(sink, lvl);
        body(t);
        approaches(t);
        rails(t);
        interior(t);
        northOutpost(t);
        southOutpost(t);
    }

    /** Высота светового проёма по расстоянию от оси: свод ступенчатой аркой 5–5–4–3. */
    static int clearH(int adx) { return adx <= 1 ? 5 : adx == 2 ? 4 : 3; }

    /** Верх фасада по расстоянию от оси (обмер: 11 в середине, 9 по краям). */
    static int facadeTop(int adx) { return adx <= 4 ? 10 : adx == 5 ? 9 : 7; }

    private static String hull(T t, int x, int y, int z, boolean facade) {
        int h = hash(x, y, z) % 100;
        if (facade) return h < 25 ? CONC_OLD : h < 40 ? CONC : CONC_D;
        return h < 12 ? CONC_OLD : h < 25 ? CONC : CONC_D;
    }

    /** Бетонный массив (стены, перекрытие), проход со сводом, фасады порталов, насыпь над сводом. */
    private static void body(T t) {
        int f = t.f;
        for (int x = AX - 6; x <= AX + 6; x++) {
            int adx = Math.abs(x - AX);
            for (int z = ZN0; z <= ZS1; z++) {
                boolean facade = z <= ZN1 || z >= ZS0;
                if (adx == 6) {                                  // крылья фасада — только на порталах
                    if (!facade) continue;
                    for (int y = f; y <= f + facadeTop(6); y++) t.set(x, y, z, hull(t, x, y, z, true));
                    continue;
                }
                int gg = t.ground(x, z);
                for (int y = gg + 1; y < f; y++) t.set(x, y, z, DIRT);          // основание, если земля ниже пола
                for (int y = f; y <= f + 7; y++) t.set(x, y, z, hull(t, x, y, z, facade));
                if (facade) {
                    int top = f + facadeTop(adx);
                    for (int y = f + 8; y <= top; y++) {
                        boolean hole = t.lvl == 0 && hash(x, y, z) % 100 < 9 && y < top;
                        t.set(x, y, z, hole ? AIR : y == top && adx <= 4 ? CAP : hull(t, x, y, z, true));
                    }
                } else {                                         // насыпь над перекрытием
                    int top = t.cover(x, z);
                    for (int y = f + 8; y <= top; y++) {
                        if (y <= gg) continue;                  // родная земля выше — не трогаем
                        t.set(x, y, z, y == top ? (Math.abs(z - CROWN_Z) <= 3 ? GRAVEL : COARSE) : DIRT);
                    }
                }
                if (adx <= 3) opening(t, x, z, adx, facade);
            }
        }
        portalFrames(t);
    }

    /** Проход: пол, свод, оконтовка порталов. */
    private static void opening(T t, int x, int z, int adx, boolean facade) {
        int f = t.f, h = clearH(adx);
        int fl = hash(x, 5, z) % 100;
        t.set(x, f, z, fl < 55 ? FLOOR : fl < 80 ? GRAVEL : CONC_D);
        for (int y = f + 1; y <= f + h; y++) t.set(x, y, z, AIR);
        String face = x < AX ? "east" : "west";
        if (adx == 3) t.set(x, f + 4, z, CONC_ST + "[facing=" + face + ",half=top]");
        else if (adx == 2) t.set(x, f + 5, z, CONC_ST + "[facing=" + face + ",half=top]");
        else t.set(x, f + 6, z, CONC_D);
    }

    /** Оконтовка проёма на наружных гранях фасадов и копоть над ним. */
    private static void portalFrames(T t) {
        int f = t.f;
        for (int z : new int[] {ZN0, ZS1}) {
            for (int x = AX - 4; x <= AX + 4; x++) {
                int adx = Math.abs(x - AX);
                if (adx == 4) for (int y = f; y <= f + 4; y++) t.set(x, y, z, CAP);
                else if (adx <= 1) t.set(x, f + 6, z, CAP);
                else {
                    String face = x < AX ? "east" : "west";
                    t.set(x, adx == 3 ? f + 4 : f + 5, z, CAP_ST + "[facing=" + face + ",half=top]");
                }
            }
            // копоть: языки пламени из проёма на фасаде
            for (int x = AX - 3; x <= AX + 3; x++) for (int y = f + 7; y <= f + 9; y++) {
                int p = 62 - 14 * (y - f - 7) - 8 * Math.abs(x - AX);
                if (hash(x, y, z) % 100 < p) t.set(x, y, z, BLACK);
            }
            t.set(AX, f + 8, z, BLACK);
        }
    }

    /** Двор перед порталом: гравий у входов и разбитая плита, чтобы подход читался. */
    private static void approaches(T t) {
        int f = t.f;
        for (int[] r : new int[][] {{ZN0 - 1, -1}, {ZS1 + 1, 1}}) {
            for (int k = 0; k < 6; k++) {
                int z = r[0] + r[1] * k;
                for (int x = AX - 4; x <= AX + 4; x++) {
                    int h = hash(x, 11, z) % 100;
                    if (h < 70 - 10 * k) t.set(x, f, z, h < 30 ? GRAVEL : h < 50 ? CONC_OLD : COARSE);
                }
            }
        }
    }

    /** Полотно: балласт и два рельса вдоль x по всей площадке. */
    private static void rails(T t) {
        for (int x = RAIL_X0; x <= RAIL_X1; x++) {
            int y = t.railY(x);
            boolean hull = x >= AX - 5 && x <= AX + 5;
            for (int z = BED_Z0; z <= BED_Z1; z++) {
                int gg = t.ground(x, z);
                if (!hull) {                                     // над насыпью — досыпать до уровня балласта
                    for (int yy = gg; yy < y - 1; yy++) t.set(x, yy, z, yy == gg ? GRAVEL : hash(x, yy, z) % 3 == 0 ? GRAVEL : DIRT);
                    t.set(x, y - 1, z, GRAVEL);
                } else t.set(x, y - 1, z, GRAVEL);
                for (int yy = y; yy <= y + 3; yy++) t.set(x, yy, z, AIR);
            }
            String shape = "east_west";
            if (t.railY(x + 1) == y + 1) shape = "ascending_east";
            else if (t.railY(x - 1) == y + 1) shape = "ascending_west";
            boolean gap = t.lvl == 0 && hash(x, 3, 0) % 100 < 10;
            if (!gap) for (int z : new int[] {RAIL_Z0, RAIL_Z1}) t.set(x, y, z, RAIL + "[shape=" + shape + "]");
            if (x % 3 == 0) for (int z = BED_Z0; z <= BED_Z1; z += 6) t.set(x, y - 1, z, COARSE);   // шпалы-пятна
        }
    }

    // ---------------------------------------------------------------- внутри туннеля

    private static boolean isMark(int x, int z) {
        for (int[] a : ANOMALY) if (a[0] == x && a[1] == z) return true;
        return false;
    }

    /** Остов, обломки, копоть и места аномалий. */
    private static void interior(T t) {
        int f = t.f;
        // остов легковушки вдоль западной стены: x 129..131, z −264..−259, пол — f
        BlockSink onFloor = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { t.s.set(x, y, z, st); }
            @Override public int ground(int x, int z) { return f + 1; }
        };
        B car = new B(onFloor, 130, -261, 90, 0x7E1E57C, Math.max(1, t.lvl));
        Vehicles.vehicle(car, "zhiguli", 0, 0, 0, null, 2);

        // обломки у стен: западный край и восточный, осевая полоса x 132..134 свободна
        for (int z = ZI0; z <= ZI1; z++) {
            for (int x : new int[] {129, 130, 131, 135}) {
                if (x <= 131 && z >= -264 && z <= -259) continue;       // под остовом
                if (isMark(x, z)) continue;
                int h = hash(x, 21, z) % 100;
                if (h >= (x == 135 ? 16 : 32)) continue;
                int kind = hash(x, 22, z) % 6;
                String st = switch (kind) {
                    case 0 -> RUBBLE_SL;
                    case 1 -> RUBBLE;
                    case 2 -> TIRES;
                    case 3 -> BARREL;
                    case 4 -> WCRATE;
                    default -> hash(x, 23, z) % 4 == 0 ? RAD : CONC_OLD + "_slab[type=bottom]";
                };
                t.set(x, f + 1, z, st);
            }
        }
        // свет: две лампы у порталов, на нижних уровнях выбиты
        if (t.lvl >= 2) {
            t.set(AX, f + 5, -265, LANTERN);
            t.set(AX, f + 5, -259, LANTERN);
        }
        // обгоревшие следы вокруг мест аномалий: пол, стены, свод
        for (int[] a : ANOMALY) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int x = a[0] + dx, z = a[1] + dz;
                if (x < AX - 3 || x > AX + 3 || z < ZI0 || z > ZI1) continue;
                if (x <= 131 && z >= -264 && z <= -259) continue;
                int d2 = dx * dx + dz * dz;
                if (d2 <= 5 && hash(x, 31, z) % 100 < 72 - 8 * d2) t.set(x, f, z, hash(x, 32, z) % 5 == 0 ? COAL : BLACK);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && Math.abs(x - AX) <= 1 && hash(x, 33, z) % 100 < 60) t.set(x, f + 6, z, BLACK);
            }
            for (int z = a[1] - 2; z <= a[1] + 2; z++) {
                if (z < ZI0 || z > ZI1) continue;
                for (int y = f + 1; y <= f + 3; y++) {
                    int p = 55 - 12 * Math.abs(z - a[1]) - 5 * (y - f - 1);
                    if (hash(AX - 4, y, z) % 100 < p) t.set(AX - 4, y, z, BLACK);
                    if (hash(AX + 4, y, z) % 100 < p) t.set(AX + 4, y, z, BLACK);
                }
            }
            t.mark("anomaly", a[0], f + 1, a[1]);
        }
    }

    // ---------------------------------------------------------------- заставы

    /** Палатка-шалаш: ступенчатый профиль шириной 7 (стенки |du|=3, скат до гребня), задняя стенка, вход с другого торца; 3 лежанки. */
    private static void tent(T t, int cx, int zBack, int zOpen, String wool) {
        int f = t.f, s = Integer.signum(zOpen - zBack);
        for (int z = zBack; z != zOpen + s; z += s) {
            for (int du : new int[] {-3, 3}) t.set(cx + du, f + 1, z, wool);
            for (int du : new int[] {-2, 2}) t.set(cx + du, f + 2, z, wool);
            for (int du : new int[] {-1, 1}) t.set(cx + du, f + 3, z, wool);
            t.set(cx, f + 4, z, wool);
        }
        for (int du = -2; du <= 2; du++) t.set(cx + du, f + 1, zBack, wool);      // задняя стенка
        for (int du = -1; du <= 1; du++) t.set(cx + du, f + 2, zBack, wool);
        t.set(cx, f + 3, zBack, wool);
        // лежанки: два матраса у задней стенки и один по оси; место сна — на клетку дальше от стенки
        t.set(cx - 1, f + 1, zBack + s, MATTRESS);
        t.set(cx + 1, f + 1, zBack + s, MATTRESS);
        t.set(cx, f + 1, zBack + 2 * s, MATTRESS);
        t.mark("bed", cx - 1, f + 1, zBack + 2 * s);
        t.mark("bed", cx + 1, f + 1, zBack + 2 * s);
        t.mark("bed", cx, f + 1, zBack + 3 * s);
    }

    /** Пост-гнездо 5×5: мешки в два ряда, крыша из профлиста, проём в сторону входа; часовой внутри. */
    private static void nest(T t, int x0, int z0, boolean openEast) {
        int f = t.f;
        for (int x = x0; x <= x0 + 4; x++) for (int z = z0; z <= z0 + 4; z++) {
            boolean edge = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
            if (edge) {
                boolean gap = (openEast ? x == x0 + 4 : x == x0) && (z == z0 + 1 || z == z0 + 2);
                if (!gap) { t.set(x, f + 1, z, BAGS); t.set(x, f + 2, z, BAGS); }
            }
            t.set(x, f + 3, z, CORR);
        }
        t.set(x0 + 2, f + 1, z0 + 3, t.lvl >= 1 ? CRATE : AIR);
        if (t.lvl >= 2) t.set(x0 + 1, f + 2, z0 + 1, LANTERN);
        t.mark("guard", x0 + 2, f + 1, z0 + 2);
    }

    /** Землянка-укрытие 5×5: бетон, перекрытие и земля сверху, дверной проём в сторону костра; два места shelter внутри. */
    private static void dugout(T t, int x0, int z0, boolean doorEast) {
        int f = t.f;
        for (int x = x0; x <= x0 + 4; x++) for (int z = z0; z <= z0 + 4; z++) {
            boolean edge = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
            if (edge) {
                boolean door = (doorEast ? x == x0 + 4 : x == x0) && z == z0 + 2;
                for (int y = f + 1; y <= f + 2; y++) t.set(x, y, z, door ? AIR : hash(x, y, z) % 3 == 0 ? CONC_OLD : CONC_D);
            } else for (int y = f + 1; y <= f + 2; y++) t.set(x, y, z, AIR);
            t.set(x, f + 3, z, CONC);
            t.set(x, f + 4, z, edge ? COARSE : DIRT);
        }
        if (t.lvl >= 1) t.set(x0 + 2, f + 2, z0 + 2, LANTERN);
        t.mark("shelter", x0 + 1, f + 1, z0 + 1);
        t.mark("shelter", x0 + 3, f + 1, z0 + 3);
    }

    private static void screen(T t, int x0, int x1, int z) {
        if (t.lvl < 1) return;
        for (int x = x0; x <= x1; x++) {
            t.set(x, t.f + 1, z, BAGS);
            if (t.lvl >= 2) t.set(x, t.f + 2, z, BAGS);
            if (t.lvl >= 3) t.set(x, t.f + 3, z, BARS);
        }
    }

    /** Северная застава: двор z −284..−269 перед северным порталом. */
    private static void northOutpost(T t) {
        int f = t.f;
        // костёр, чурбаки, места
        t.set(138, f + 1, -274, t.lvl >= 1 ? FIRE : FIRE_OFF);
        t.set(136, f + 1, -274, LOG);
        t.set(140, f + 1, -274, LOG);
        t.set(138, f + 1, -272, LOG);
        t.mark("cook", 138, f + 1, -275);
        t.mark("rest", 137, f + 1, -275);
        t.mark("rest", 139, f + 1, -275);
        // палатка, пост, землянка, мешки у входа
        tent(t, 140, -281, -278, "minecraft:green_wool");
        nest(t, 122, -276, true);
        dugout(t, 122, -283, true);
        screen(t, 136, 138, -270);
        t.mark("guard", 137, f + 1, -271);
        // хозяйство
        t.set(142, f + 1, -273, CRATE);
        t.set(143, f + 1, -273, WCRATE);
        t.set(142, f + 1, -275, BARREL);
        if (t.lvl >= 1) t.set(134, f + 1, -280, TIRES);
    }

    /** Южная застава: двор z −255..−240 за южным порталом; костёр — в точке campfire узла (131, −254). */
    private static void southOutpost(T t) {
        int f = t.f;
        t.set(131, f + 1, -254, t.lvl >= 1 ? FIRE : FIRE_OFF);
        t.set(129, f + 1, -254, LOG);
        t.set(131, f + 1, -252, LOG);
        t.mark("cook", 130, f + 1, -254);
        t.mark("rest", 130, f + 1, -253);
        t.mark("rest", 132, f + 1, -253);
        tent(t, 124, -247, -250, "minecraft:gray_wool");
        nest(t, 138, -252, false);
        dugout(t, 138, -245, false);
        screen(t, 126, 128, -254);
        t.mark("guard", 127, f + 1, -253);
        t.set(136, f + 1, -250, CRATE);
        t.set(136, f + 1, -249, WCRATE);
        t.set(129, f + 1, -248, BARREL);
        if (t.lvl >= 1) t.set(135, f + 1, -246, TIRES);
    }
}
