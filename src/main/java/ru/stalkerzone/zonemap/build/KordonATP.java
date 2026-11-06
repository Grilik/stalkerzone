package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.ArrayList;
import java.util.List;

/**
 * АТП Кордона (узел 5, смарт-террейн {@code esc_smart_terrain_7_11}) — заново по вокселям оригинала l01_escape (Anomaly)
 * и обмеру {@code docs/kordon_objects/05-atp.md} (П-21). Прежний сборщик (отключён в PR #64) переписан целиком.
 *
 * <p>Решение пользователя (06.11, 23:50): <b>база бандитов, как в оригинале</b>, обжитая — костёр в жилом боксе
 * (северный навес, точка костра узла), нары, бочки с огнём, шины, ржавые листы, мусор. Рабочие места: {@code bed} ×6,
 * {@code rest} ×3, {@code cook}, {@code guard} ×2 (у ворот и на крыше конторы), {@code patrol} вдоль забора,
 * {@code shelter} ×4 (контора, боксы).
 *
 * <p>Уровни базы 0–3, как у деревни: 0 — руины (дыры, костёр потушен, нар нет), 1 — лагерь (костёр, нары, две бочки
 * с огнём, мешки на крыше), 2 — обжитая (дыры залатаны, часть окон заколочена, проломы забора — ржавыми листами,
 * мешки у ворот, все бочки с огнём), 3 — укреплённая (решётки на нижних окнах, навес над постом на крыше,
 * баррикада у ворот). Постройки и места те же на всех уровнях.
 *
 * <p>Объекты (номера обмера):
 * <ul>
 *   <li>2 — контора: двухэтажная, кирпич с бетонными углами, двускатная кровля из досок вдоль z; восточная пристройка;</li>
 *   <li>3 — боксы: три гаражных бокса с воротами на запад (в среднем — сгоревший ЗИЛ), пристройка, северный навес;</li>
 *   <li>6 — мастерская: высокий цех со смотровой ямой, южная часть с воротами на запад, длинное восточное крыло;</li>
 *   <li>4 — дощатый склад под шифером; 7 — водокачка на столбах; 1 — прожекторная мачта;</li>
 *   <li>5, 8, 9 — разбитые грузовики и машины во дворе и у ворот; бетонный забор по югу и востоку (проём ворот x 299..303).</li>
 * </ul>
 * Высоты — от земли плато (AUTO, ≈ 92 в оригинале); на склоне каждая постройка садится на медиану земли своего следа.
 */
public final class KordonATP {
    private KordonATP() {}

    /** Плато площадки atp: ровная часть (весь двор, забор, машина у ворот) и откос. */
    public static final int[] PLATEAU = {289, -158, 355, -88};
    public static final int MARGIN = 6;

    /** Границы замены вокселей оригинала (= плато с откосом). */
    public static final List<int[]> CLEAR = List.of(new int[] {PLATEAU[0] - MARGIN, PLATEAU[1] - MARGIN, PLATEAU[2] + MARGIN, PLATEAU[3] + MARGIN});

    static final String BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty", BRICK_O = "stalkerzone:zm_brick_old",
            CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty", CONC_O = "stalkerzone:zm_concrete_old",
            PLATE = "stalkerzone:zm_concrete_plate", FLOOR = "stalkerzone:zm_concrete_floor",
            BOARDS = "stalkerzone:zm_boards_old", PLANKS = "stalkerzone:zm_planks_gray", SHIFER = "stalkerzone:zm_shifer_dark",
            RUST = "stalkerzone:zm_rust_sheet", GREEN_R = "stalkerzone:zm_metal_green_rust", POST = "stalkerzone:zm_wood_post",
            POST_F = "stalkerzone:zm_wood_post_fence", FENCE = "stalkerzone:concrete_fence", GLASS = "stalkerzone:zm_glass_dirty_pane",
            RUBBLE = "stalkerzone:zm_rubble", RUBBLE_SL = "stalkerzone:zm_rubble_slab[type=bottom]",
            CRATE = "stalkerzone:wooden_crate", MCRATE = "stalkerzone:military_crate",
            AMMO = "stalkerzone:prop_ammo_crates[facing=north]", TRASH = "stalkerzone:prop_trash_bags[facing=north]",
            PROP_RUBBLE = "stalkerzone:prop_rubble[facing=north]", TIRE = "stalkerzone:prop_tire[facing=north]",
            TIRES = "stalkerzone:tires", BARREL = "stalkerzone:rusty_barrel", BARREL_L = "stalkerzone:prop_barrel_lying[facing=east]",
            BAGS = "stalkerzone:zm_sandbags", MATTRESS = "stalkerzone:zm_mattress_slab[type=bottom]",
            FIRE_ON = "minecraft:campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]",
            FIRE_OFF = "minecraft:campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]",
            ANVIL = "minecraft:damaged_anvil[facing=north]", CHAIN = "minecraft:chain[axis=y]", COBWEB = "minecraft:cobweb",
            AIR = "minecraft:air";

    // контора (обмер 2): x 313..323, z −155..−138; пристройка x 323..329, z −155..−149
    public static final int KX0 = 313, KX1 = 323, KZ0 = -155, KZ1 = -138;
    // боксы (обмер 3): x 332..342, z −139..−125, пристройка x 342..350, z −132..−125; навес x 333..342, z −155..−140
    public static final int BX0 = 332, BX1 = 342, BZ0 = -139, BZ1 = -125;
    // мастерская (обмер 6): цех x 315..325, z −119..−105; южная часть до z −97; крыло x 325..350, z −114..−105
    public static final int MX0 = 315, MX1 = 325, MZ0 = -119, MZ1 = -105, MZS = -97;
    // склад (обмер 4): x 302..310, z −135..−115
    public static final int SX0 = 302, SX1 = 310, SZ0 = -135, SZ1 = -115;

    /** Следы построек (x0, z0, x1, z1) — мусор двора туда не сыплется. */
    static final int[][] FOOTPRINTS = {
            {KX0 - 1, KZ0 - 1, 330, KZ1 + 1}, {330, -156, 351, BZ1 + 1}, {MX0 - 2, MZ0 - 1, 351, MZS + 1},
            {SX0 - 1, SZ0 - 1, SX1 + 1, SZ1 + 1}, {291, -111, 297, -105}, {303, -157, 308, -152}};

    static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1 ^ 0x5A7B;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Доля p (0..1) по шуму позиции. */
    static boolean p(int x, int y, int z, double p) { return hash(x, y, z) % 1000 < p * 1000; }

    /** Множитель разрухи по уровню развития: 0 — руины после ухода хозяев, 3 — укреплённая база (дыры залатаны). */
    static final double[] WEAR = {1.0, 0.6, 0.35, 0.2};
    // сборка однопоточная в пределах вызова build (как у прочих сборщиков — статические методы без общего состояния
    // между вызовами); уровень передаётся через поле, чтобы не тащить его в каждый помощник
    private static final ThreadLocal<Integer> LVL = ThreadLocal.withInitial(() -> 1);
    static int lvl() { return LVL.get(); }
    /** Дыра с долей hole с учётом уровня. */
    static boolean hole(int x, int y, int z, double hole) { return p(x, y, z, hole * WEAR[lvl()]); }

    /** Построить АТП — базу бандитов на уровне развития lvl (0–3).  */
    public static void build(BlockSink b, int lvl) {
        LVL.set(Math.max(0, Math.min(3, lvl)));
        office(b);
        boxes(b);
        workshop(b);
        store(b);
        waterTower(b);
        mast(b);
        fence(b);
        vehicles(b);
        guards(b);
        fireBarrels(b);
        yard(b);
    }

    /** Костёр узла (точка campfire из kordon_points.json) — в жилом боксе. */
    public static final int[] FIRE = {337, -151};
    /** Обход вдоль забора (изнутри двора). */
    static final int[][] PATROL = {{296, -96}, {312, -96}, {330, -96}, {348, -96}, {351, -104}, {351, -117}};
    /** Бочки с огнём: первые две с ур. 1, остальные с ур. 2. */
    static final int[][] FIRE_BARRELS = {{328, -131}, {306, -98}, {310, -142}, {345, -118}, {300, -112}};

    /**
     * Часовые: у ворот (за мешками с ур. 2) и на крыше конторы — площадка на плоской крыше восточной пристройки
     * с лестницей снаружи (мешки с ур. 1, навес с ур. 3). Обход вдоль забора.
     */
    static void guards(BlockSink b) {
        int l = lvl();
        int gx = 305, gz = -96, g = b.ground(gx, gz);
        b.mark("guard", gx, g + 1, gz);
        if (l >= 2) for (int x = 304; x <= 307; x++) b.set(x, b.ground(x, -95) + 1, -95, BAGS);
        if (l >= 3) for (int[] c : new int[][] {{297, -95}, {298, -95}, {297, -96}}) {
            b.set(c[0], b.ground(c[0], c[1]) + 1, c[1], RUST);
            b.set(c[0], b.ground(c[0], c[1]) + 2, c[1], BAGS);
        }
        // площадка на крыше пристройки конторы (x 325..329, z −154..−150, на g + 4)
        int ga = base(b, KX1, KZ0, 329, -149), top = ga + 4;
        for (int x = 325; x <= 329; x++) for (int z = -154; z <= -150; z++) {
            b.set(x, top, z, PLANKS);
            boolean edge = x == 325 || x == 329 || z == -154 || z == -150;
            if (l >= 1 && edge && !(x >= 328 && z == -150)) b.set(x, top + 1, z, BAGS);   // проход с лестницы
        }
        for (int y = ga + 1; y <= ga + 3; y++) b.set(329, y, -150, CONC_O);              // стена под лестницей целая
        for (int y = ga + 1; y <= top; y++) b.set(330, y, -150, "minecraft:ladder[facing=east]");
        if (l >= 3) {
            for (int[] c : new int[][] {{325, -154}, {329, -154}, {325, -150}})
                for (int y = top + 1; y <= top + 2; y++) b.set(c[0], y, c[1], POST_F);
            for (int x = 325; x <= 329; x++) for (int z = -154; z <= -150; z++) b.set(x, top + 3, z, RUST + "_slab[type=bottom]");
        }
        b.mark("guard", 327, top + 1, -152);
        for (int[] p : PATROL) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }

    /** Бочки с огнём во дворе (костёр на ржавой бочке): у ворот и между постройками. */
    static void fireBarrels(BlockSink b) {
        int l = lvl(), n = l == 0 ? 0 : l == 1 ? 2 : FIRE_BARRELS.length;
        for (int i = 0; i < FIRE_BARRELS.length; i++) {
            int x = FIRE_BARRELS[i][0], z = FIRE_BARRELS[i][1], g = b.ground(x, z);
            if (i < n) { b.set(x, g + 1, z, BARREL); b.set(x, g + 2, z, FIRE_ON); }
            else if (l == 0 && i < 2) b.set(x, g + 1, z, BARREL_L);
        }
        if (l >= 1) for (int[] t : new int[][] {{294, -98}, {295, -98}, {340, -97}, {341, -97}}) {   // шины и листы у забора
            int g = b.ground(t[0], t[1]);
            b.set(t[0], g + 1, t[1], TIRES);
            if (l >= 2) b.set(t[0], g + 2, t[1], TIRE);
        }
        if (l >= 2) for (int[] t : new int[][] {{332, -97}, {333, -97}, {334, -97}})
            b.set(t[0], b.ground(t[0], t[1]) + 1, t[1], RUST + "_slab[type=bottom]");
    }

    /** Медиана земли по следу. */
    static int base(BlockSink b, int x0, int z0, int x1, int z1) {
        List<Integer> gs = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) gs.add(b.ground(x, z));
        gs.sort(Integer::compare);
        return gs.get(gs.size() / 2);
    }

    /** Блок разрушенной стены: дыра с долей hole (чаще у верха), иначе материал. */
    static void wall(BlockSink b, int x, int y, int z, String st, double hole) {
        b.set(x, y, z, hole(x, y, z, hole) ? AIR : st);
    }

    /**
     * Коробка этажа: стены по краю (низ y0, высота h), внутри воздух, пол по всему следу на y0 − 1.
     * Окна — выбитые проёмы (2 блока на y0+1..y0+2 при h ≥ 4, иначе 1), шаг 3 от углов; редко осколок стекла.
     */
    static void box(BlockSink b, int x0, int z0, int x1, int z1, int y0, int h, String low, String up, double hole, boolean windows) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            if (!edge) b.set(x, y0 - 1, z, p(x, y0, z, 0.25) ? CONC_O : FLOOR);
            for (int y = y0; y < y0 + h; y++) {
                if (!edge) { b.set(x, y, z, AIR); continue; }
                String st = corner ? CONC_D : y - y0 < 2 ? low : up;
                double hp = corner ? 0 : hole * (y - y0 + 1) / h;
                wall(b, x, y, z, st, hp);
            }
        }
        if (!windows) return;
        int wy = y0 + 1, wh = h >= 4 ? 2 : 1;
        for (int x = x0 + 2; x <= x1 - 2; x += 3) for (int z : new int[] {z0, z1}) window(b, x, wy, z, wh);
        for (int z = z0 + 2; z <= z1 - 2; z += 3) for (int x : new int[] {x0, x1}) window(b, x, wy, z, wh);
    }

    /** Окно: до ур. 1 выбито (редкий осколок), с ур. 2 часть заколочена досками, на ур. 3 нижние — в решётках. */
    static void window(BlockSink b, int x, int y, int z, int h) {
        int l = lvl();
        for (int k = 0; k < h; k++) {
            String w = k == 0 && p(x, y, z, 0.08) ? GLASS : AIR;
            if (l >= 2 && p(x, y + k, z, 0.4)) w = BOARDS;
            if (l >= 3 && k == 0 && y <= b.ground(x, z) + 2 && !w.equals(BOARDS)) w = "minecraft:iron_bars";
            b.set(x, y + k, z, w);
        }
    }

    /** Проём (дверь без двери, ворота): воздух x0..x1, z0..z1, высотой h от y0. */
    static void opening(BlockSink b, int x0, int z0, int x1, int z1, int y0, int h) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y < y0 + h; y++) b.set(x, y, z, AIR);
    }

    /**
     * Пологая двускатная кровля из плит вдоль z (конёк по середине x): на каждый шаг от карниза — пол-блока вверх.
     * Фронтоны на торцах z0, z1 заполняются материалом gable. Дыры — доля hole.
     */
    static void gableZ(BlockSink b, int x0, int z0, int x1, int z1, int yT, String roof, String gable, double hole) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            int k = Math.min(x - (x0 - 1), (x1 + 1) - x);
            int y = yT + k / 2;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (hole(x, y, z, hole) || hole(x >> 1, y, z >> 2, hole * 0.6)) continue;
                b.set(x, y, z, roof + "_slab[type=" + (k % 2 == 0 ? "bottom" : "top") + "]");
            }
            if (x >= x0 && x <= x1) for (int yy = yT; yy < y; yy++) for (int z : new int[] {z0, z1}) wall(b, x, yy, z, gable, 0.12);
        }
    }

    /** Плоская кровля из плит на высоте y (с дырами), с выносом over. */
    static void flat(BlockSink b, int x0, int z0, int x1, int z1, int y, String roof, double hole, int over) {
        for (int x = x0 - over; x <= x1 + over; x++) for (int z = z0 - over; z <= z1 + over; z++)
            if (!hole(x, y, z, hole) && !hole(x >> 1, y, z >> 1, hole * 0.5)) b.set(x, y, z, roof + "_slab[type=bottom]");
    }

    /** Укрытие: место shelter, над ним (3×3) кровля без дыр — от выброса прячутся под целым перекрытием. */
    static void shelter(BlockSink b, int x, int y, int z, int roofY, String roof) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(x + dx, roofY, z + dz, roof);
        b.mark("shelter", x, y, z);
    }

    /** Куча обломков на полу у (x, z) радиусом r. */
    static void rubble(BlockSink b, int cx, int y, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            int d = Math.abs(x - cx) + Math.abs(z - cz);
            if (d > r) continue;
            b.set(x, y, z, d == 0 && r > 1 ? RUBBLE : p(x, y, z, 0.3) ? PROP_RUBBLE : RUBBLE_SL);
        }
    }

    // ---------------------------------------------------------------- контора

    /**
     * Контора: два этажа (3 + 3, перекрытие из плит на g+4 с обвалом в северной комнате), кирпич с бетонными углами,
     * кровля из старых досок (конёк ≈ g+11, как в оригинале). Вход с юга (x 318) и с запада (z −143), поперечная
     * стена z −146 с проёмом; лестница-стремянка у восточной стены. Пристройка на восток — склад бумаг, плоская ржавая крыша.
     */
    private static void office(BlockSink b) {
        int g = base(b, KX0, KZ0, KX1, KZ1), y1 = g + 1, slab = g + 4, y2 = g + 5, yT = g + 8;
        box(b, KX0, KZ0, KX1, KZ1, y1, 3, BRICK_D, BRICK, 0.10, true);
        box(b, KX0, KZ0, KX1, KZ1, y2, 3, BRICK, BRICK_O, 0.22, true);
        for (int x = KX0; x <= KX1; x++) for (int z = KZ0; z <= KZ1; z++) {
            boolean edge = x == KX0 || x == KX1 || z == KZ0 || z == KZ1;
            b.set(x, slab, z, edge ? CONC_D : hole(x, slab, z, 0.06) ? AIR : PLATE);
        }
        // обвал перекрытия в северной комнате: дыра в плитах и куча на полу первого этажа
        opening(b, 315, -152, 318, -149, slab, 1);
        rubble(b, 316, y1, -150, 2);
        // поперечная стена первого этажа с проёмом
        for (int x = KX0 + 1; x < KX1; x++) for (int y = y1; y < slab; y++) wall(b, x, y, -146, CONC_O, 0.08);
        opening(b, 318, -146, 318, -146, y1, 2);
        // входы: юг (с крыльцом) и запад
        opening(b, 318, KZ1, 319, KZ1, y1, 2);
        b.set(318, g, KZ1 + 1, CONC_O + "_slab[type=top]");
        b.set(319, g, KZ1 + 1, CONC_O + "_slab[type=top]");
        opening(b, KX0, -143, KX0, -142, y1, 2);
        // стремянка на второй этаж у восточной стены
        for (int y = y1; y <= slab; y++) b.set(KX1 - 1, y, -140, "minecraft:ladder[facing=west]");
        b.set(KX1 - 2, slab, -140, AIR);
        b.set(KX1 - 1, slab, -140, "minecraft:ladder[facing=west]");
        // кровля и фронтоны
        gableZ(b, KX0, KZ0, KX1, KZ1, yT, BOARDS, BRICK_O, 0.10);
        // обстановка: ящики, мусор, паутина
        b.set(314, y1, -139, CRATE); b.set(322, y1, -154, TRASH); b.set(314, y1, -154, BARREL_L);
        b.set(321, y2, -141, CRATE); b.set(314, y2, -148, TRASH); b.set(320, y2, -154, COBWEB); b.set(316, y1 + 2, -139, COBWEB);
        rubble(b, 321, y2, -151, 1);
        // укрытия — в двух комнатах первого этажа (под целым перекрытием)
        shelter(b, 316, y1, -141, slab, PLATE + "");
        shelter(b, 321, y1, -153, slab, PLATE + "");
        // пристройка
        int ax0 = KX1, ax1 = 329, az0 = KZ0, az1 = -149;
        for (int x = ax0 + 1; x <= ax1; x++) for (int z = az0; z <= az1; z++) {
            boolean edge = x == ax1 || z == az0 || z == az1;
            if (!edge) b.set(x, g, z, FLOOR);
            for (int y = y1; y < y1 + 3; y++) b.set(x, y, z, edge ? (hole(x, y, z, 0.12) ? AIR : CONC_O) : AIR);
        }
        flat(b, ax0 + 1, az0, ax1, az1, y1 + 3, RUST, 0.25, 0);
        opening(b, KX1, -152, KX1, -152, y1, 2);
        opening(b, ax1, -152, ax1, -151, y1, 2);
        b.set(327, y1, -154, CRATE); b.set(328, y1, -154, CRATE); b.set(328, y1 + 1, -154, TRASH);
    }

    // ---------------------------------------------------------------- боксы

    /**
     * Боксы: три гаражных бокса под плоской дощатой кровлей, ворота на запад сорваны (створки лежат у проёмов),
     * в среднем боксе — сгоревший ЗИЛ-130. Пристройка на восток (ржавая крыша), северный навес под ржавым листом.
     */
    private static void boxes(BlockSink b) {
        int g = base(b, BX0, BZ0, BX1, BZ1), y = g + 1;
        box(b, BX0, BZ0, BX1, BZ1, y, 4, BRICK_D, CONC_O, 0.10, false);
        for (int z : new int[] {-134, -129}) for (int x = BX0 + 1; x < BX1; x++) for (int yy = y; yy < y + 4; yy++) wall(b, x, yy, z, CONC_O, 0.06);
        int[][] gates = {{-138, -136}, {-133, -130}, {-128, -126}};
        for (int[] gt : gates) {
            opening(b, BX0, gt[0], BX0, gt[1], y, 3);
            for (int z = gt[0]; z <= gt[1]; z++) b.set(BX0 - 2, y, z, "stalkerzone:zm_metal_green_rust_slab[type=bottom]");   // створки
        }
        flat(b, BX0, BZ0, BX1, BZ1, y + 4, BOARDS, 0.18, 0);
        // бокс 1 (север): укрытие, бочки
        shelter(b, 336, y, -137, y + 4, BOARDS + "_slab[type=bottom]");
        b.set(340, y, -138, BARREL); b.set(341, y, -138, BARREL_L); b.set(341, y, -135, TIRES);
        // бокс 2: сгоревший грузовик
        Vehicles.vehicleAt(new B(b, 337, -131, 0, 5021L, 0), "zil", 0, 0, 0, 0, null, 2);
        // бокс 3 (юг): склад хабара — ящики у стен
        b.set(341, y, -128, MCRATE); b.set(341, y, -127, CRATE); b.set(341, y + 1, -127, AMMO); b.set(340, y, -126, CRATE);
        b.set(334, y, -126, TIRE); b.set(334, y + 1, -126, TIRE);
        // пристройка: укрытие
        int ax1 = 350, az0 = -132;
        for (int x = BX1 + 1; x <= ax1; x++) for (int z = az0; z <= BZ1; z++) {
            boolean edge = x == ax1 || z == az0 || z == BZ1;
            if (!edge) b.set(x, g, z, FLOOR);
            for (int yy = y; yy < y + 3; yy++) b.set(x, yy, z, edge ? (hole(x, yy, z, 0.10) ? AIR : CONC) : AIR);
        }
        flat(b, BX1 + 1, az0, ax1, BZ1, y + 3, RUST, 0.15, 0);
        opening(b, BX1, -127, BX1, -127, y, 2);
        opening(b, 347, BZ1, 348, BZ1, y, 2);
        b.set(349, y, -131, CRATE); b.set(343, y, -131, TRASH);
        shelter(b, 346, y, -129, y + 3, RUST + "_slab[type=bottom]");
        // северный навес: стены с трёх сторон, запад открыт под ржавым козырьком на столбах
        int nx0 = 333, nz0 = -155, nz1 = -140;
        int gn = base(b, nx0, nz0, BX1, nz1), yn = gn + 1;
        for (int x = nx0; x <= BX1; x++) for (int z = nz0; z <= nz1; z++) {
            boolean edge = x == BX1 || z == nz0 || z == nz1 || (x == nx0 && Math.floorMod(z - nz0, 5) == 0);
            for (int yy = yn; yy < yn + 3; yy++) b.set(x, yy, z, edge ? (hole(x, yy, z, 0.12) ? AIR : x == nx0 ? CONC_O : BRICK_D) : AIR);
        }
        for (int z = nz0; z <= nz1; z += 5) for (int yy = yn; yy < yn + 3; yy++) b.set(331, yy, z, POST_F);
        flat(b, 331, nz0, BX1, nz1, yn + 3, RUST, 0.20, 0);
        camp(b, yn);
    }

    /**
     * Жилой бокс бандитов (северный навес, точка костра узла (337, −151)): костёр посередине с дырой в кровле,
     * повар у огня, три места отдыха вокруг, шесть нар вдоль восточной стены. На ур. 0 костёр потушен, нар нет.
     */
    static void camp(BlockSink b, int yn) {
        int l = lvl(), fx = FIRE[0], fz = FIRE[1];
        b.set(fx, yn - 1, fz, CONC_O);
        b.set(fx, yn, fz, l >= 1 ? FIRE_ON : FIRE_OFF);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(fx + dx, yn + 3, fz + dz, AIR);   // дымоход
        b.mark("cook", fx, yn, fz + 2);
        for (int[] r : new int[][] {{fx - 2, fz}, {fx + 2, fz}, {fx, fz - 2}}) {
            b.mark("rest", r[0], yn, r[1]);
            if (l >= 1) b.set(r[0] + Integer.signum(r[0] - fx), yn, r[1] + Integer.signum(r[1] - fz), TIRE);   // шина-сиденье за спиной
        }
        // нары: матрасы на дощатом настиле вдоль стены x 341, место bed — рядом
        for (int z = -147; z <= -142; z++) {
            b.set(BX1 - 1, yn, z, l >= 1 ? MATTRESS : p(BX1 - 1, yn, z, 0.5) ? BOARDS + "_slab[type=bottom]" : AIR);
            if (l >= 2 && (z & 1) == 0) b.set(BX1 - 1, yn + 2, z, PLANKS + "_slab[type=bottom]");   // верхний ярус-полка
            b.mark("bed", BX1 - 2, yn, z);
        }
        b.set(334, yn, -154, l >= 1 ? BARREL : BARREL_L); b.set(335, yn, -154, TIRES); b.set(334, yn + 1, -154, TIRE);
        b.set(341, yn, -154, CRATE); b.set(340, yn, -154, TRASH);
        if (l >= 1) { b.set(341, yn, -153, MCRATE); b.set(334, yn, -141, TRASH); }
    }

    // ---------------------------------------------------------------- мастерская

    /**
     * Мастерская: высокий цех (кирпич до g+2, выше доски, кровля шифер с проваленным участком), смотровая яма,
     * северные ворота; южная часть ниже, ворота на запад; длинное восточное крыло (кровля обрушена наполовину)
     * и комнатка-кладовая. Хабар — в цехе у верстака.
     */
    private static void workshop(BlockSink b) {
        int g = base(b, MX0, MZ0, MX1, MZS), y = g + 1;
        box(b, MX0, MZ0, MX1, MZ1, y, 6, BRICK_D, BOARDS, 0.16, true);
        gableZ(b, MX0, MZ0, MX1, MZ1, y + 6, SHIFER, BOARDS, 0.12);
        opening(b, 318, MZ0, 321, MZ0, y, 4);                                    // ворота цеха на север
        for (int x = 318; x <= 321; x++) b.set(x, y, MZ0 - 2, "stalkerzone:zm_metal_green_rust_slab[type=bottom]");
        opening(b, 317, -113, 321, -110, y + 7, 4);                              // провал кровли
        rubble(b, 319, y, -107, 2);
        // смотровая яма
        for (int z = -117; z <= -111; z++) { b.set(323, g, z, AIR); b.set(323, g - 1, z, AIR); b.set(323, g - 2, z, CONC_O); }
        b.set(322, y, -117, BARREL); b.set(324, y + 4, -114, CHAIN); b.set(324, y + 3, -114, CHAIN);
        // верстак с хабаром
        b.set(316, y, -116, ANVIL); b.set(316, y, -115, MCRATE); b.set(316, y, -114, CRATE); b.set(316, y + 1, -114, AMMO);
        b.set(317, y, -117, TIRES);
        // южная часть: одноэтажная, ворота на запад
        box(b, MX0, MZ1, MX1, MZS, y, 4, BRICK_D, CONC_O, 0.12, true);
        flat(b, MX0, MZ1 + 1, MX1, MZS, y + 4, SHIFER, 0.20, 1);
        opening(b, MX0, -103, MX0, -100, y, 3);
        opening(b, 319, MZ1, 321, MZ1, y, 3);                                     // проезд в цех
        b.set(324, y, -98, TRASH); b.set(323, y, -98, BARREL_L); b.set(316, y, -98, TIRE);
        // восточное крыло: длинный сарай, кровля только над западной половиной
        int wx1 = 350, wz0 = -114, wz1 = MZ1;
        for (int x = MX1 + 1; x <= wx1; x++) for (int z = wz0; z <= wz1; z++) {
            boolean edge = x == wx1 || z == wz0 || z == wz1;
            if (!edge) b.set(x, g, z, p(x, g, z, 0.3) ? CONC_O : FLOOR);
            for (int yy = y; yy < y + 3; yy++) b.set(x, yy, z, edge ? (hole(x, yy, z, 0.18) ? AIR : CONC_O) : AIR);
        }
        flat(b, MX1 + 1, wz0, 338, wz1, y + 3, BOARDS, 0.25, 0);
        for (int x = 339; x <= wx1; x += 3) b.set(x, y, -110 + (x & 1), x % 2 == 0 ? RUBBLE_SL : PROP_RUBBLE);
        opening(b, MX1, -110, MX1, -109, y, 2);
        opening(b, wx1, -111, wx1, -110, y, 3);
        b.set(334, y, -113, TIRES); b.set(335, y, -113, TIRES); b.set(344, y, -106, BARREL);
        // кладовая к югу от крыла
        int cx0 = 340, cx1 = 346, cz1 = -99;
        for (int x = cx0; x <= cx1; x++) for (int z = wz1 + 1; z <= cz1; z++) {
            boolean edge = x == cx0 || x == cx1 || z == cz1;
            if (!edge) b.set(x, g, z, FLOOR);
            for (int yy = y; yy < y + 3; yy++) b.set(x, yy, z, edge ? (hole(x, yy, z, 0.10) ? AIR : BRICK_D) : AIR);
        }
        flat(b, cx0, wz1 + 1, cx1, cz1, y + 3, RUST, 0.15, 0);
        opening(b, 343, wz1, 343, wz1, y, 2);
        opening(b, cx0, -101, cx0, -101, y, 2);
        b.set(345, y, -100, CRATE); b.set(341, y, -100, TRASH);
    }

    // ---------------------------------------------------------------- склад, водокачка, мачта

    /** Дощатый склад под шифером (обмер 4): вход с юга, ворота на восток, внутри шины и мусор. */
    private static void store(BlockSink b) {
        int g = base(b, SX0, SZ0, SX1, SZ1), y = g + 1;
        box(b, SX0, SZ0, SX1, SZ1, y, 3, PLANKS, BOARDS, 0.14, true);
        gableZ(b, SX0, SZ0, SX1, SZ1, y + 3, SHIFER, BOARDS, 0.10);
        opening(b, 305, SZ1, 306, SZ1, y, 2);
        opening(b, SX1, -126, SX1, -124, y, 3);
        b.set(303, y, -133, TIRES); b.set(304, y, -133, TIRES); b.set(303, y + 1, -133, TIRE);
        b.set(309, y, -118, TRASH); b.set(303, y, -121, BARREL_L); b.set(308, y, -131, CRATE);
        rubble(b, 306, y, -128, 1);
    }

    /** Водокачка (обмер 7): четыре столба, настил и ржавый бак (верх ≈ g+11). Лестницы нет — сгнила. */
    private static void waterTower(BlockSink b) {
        int x0 = 292, x1 = 296, z0 = -110, z1 = -106, g = base(b, x0, z0, x1, z1);
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}})
            for (int y = g + 1; y <= g + 7; y++) b.set(c[0], y, c[1], POST);
        for (int y = g + 3; y <= g + 6; y += 3) {
            for (int x = x0 + 1; x < x1; x++) { b.set(x, y, z0, POST_F); b.set(x, y, z1, POST_F); }
            for (int z = z0 + 1; z < z1; z++) { b.set(x0, y, z, POST_F); b.set(x1, y, z, POST_F); }
        }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (!hole(x, g + 8, z, 0.15)) b.set(x, g + 8, z, PLANKS + "_slab[type=bottom]");
        for (int y = g + 9; y <= g + 11; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean cornerCut = (x == x0 || x == x1) && (z == z0 || z == z1);
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (cornerCut) continue;
            if (edge) wall(b, x, y, z, RUST, 0.08);
            else if (y == g + 11) b.set(x, y, z, RUST + "_slab[type=top]");
        }
        b.set(294, g + 1, -108, "stalkerzone:zm_metal_plate_slab[type=bottom]");   // упавший люк
    }

    /** Прожекторная мачта (обмер 1): бетонный башмак, деревянная стойка до g+13 с ржавой перекладиной. */
    private static void mast(BlockSink b) {
        int x = 306, z = -154, g = b.ground(x, z);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 0; dz++) b.set(x + dx, g + 1, z + dz, CONC_O);
        for (int y = g + 2; y <= g + 13; y++) b.set(x, y, z, POST);
        for (int dx = -2; dx <= 2; dx++) if (dx != 0) b.set(x + dx, g + 12, z, RUST + "_slab[type=bottom]");
        b.set(x - 2, g + 11, z, CHAIN);
        b.set(x + 2, g + 1, z, "stalkerzone:zm_metal_plate_slab[type=bottom]");   // упавший прожектор
    }

    // ---------------------------------------------------------------- забор, машины, двор

    /** Бетонный забор (2 блока) по югу (z −94) и востоку (x 353) с проломами; проём ворот x 299..303. */
    private static void fence(BlockSink b) {
        for (int x = 292; x <= 353; x++) if (x < 299 || x > 303) fencePost(b, x, -94);
        for (int z = -116; z < -94; z++) fencePost(b, 353, z);
    }

    private static void fencePost(BlockSink b, int x, int z) {
        int g = b.ground(x, z), seg = hash(x >> 2, 7, z >> 2) % 100;
        if (seg < 12) {   // пролом: на ур. 0–1 обломок плиты, с ур. 2 залатан ржавыми листами
            if (lvl() >= 2) { b.set(x, g + 1, z, RUST); b.set(x, g + 2, z, RUST); }
            else if (p(x, g, z, 0.5)) b.set(x, g + 1, z, CONC_O + "_slab[type=bottom]");
            return;
        }
        b.set(x, g + 1, z, FENCE);
        if (seg >= 22) b.set(x, g + 2, z, FENCE);
    }

    /** Брошенные машины по обмеру: КамАЗ во дворе (5), «Москвич» у склада (8), ЗИЛ у ворот (9), УАЗ у боксов. */
    private static void vehicles(BlockSink b) {
        Vehicles.vehicle(new B(b, 317, -124, 0, 5011L, 0), "kamaz", 0, 0, 0, null, 1);
        Vehicles.vehicle(new B(b, 311, -108, 0, 5012L, 0), "moskvich", 0, 0, 90, null, 2);
        Vehicles.vehicle(new B(b, 299, -92, 0, 5013L, 0), "zil", 0, 0, 0, null, 1);
        Vehicles.vehicle(new B(b, 336, -122, 0, 5014L, 0), "uaz", 0, 0, 0, null, 2);
    }

    /** Мусор по двору: шины, мешки, обломки, бочки, листы шифера — редко, мимо построек и забора. */
    private static void yard(BlockSink b) {
        String[] junk = {TIRE, TIRES, TRASH, PROP_RUBBLE, BARREL_L, RUBBLE_SL, "stalkerzone:zm_shifer_slab[type=bottom]", CRATE};
        for (int x = PLATEAU[0]; x <= PLATEAU[2]; x++) for (int z = PLATEAU[1]; z <= PLATEAU[3]; z++) {
            if (hash(x, 3, z) % 61 != 0 || occupied(x, z)) continue;
            if (z >= -95 && z <= -93 || x >= 352 && x <= 354) continue;               // забор
            b.set(x, b.ground(x, z) + 1, z, junk[hash(x, 4, z) % junk.length]);
        }
    }

    static boolean occupied(int x, int z) {
        for (int[] f : FOOTPRINTS) if (x >= f[0] && x <= f[2] && z >= f[1] && z <= f[3]) return true;
        // машины
        int[][] cars = {{317, -124}, {311, -108}, {299, -92}, {336, -122}};
        for (int[] c : cars) if (Math.abs(x - c[0]) <= 6 && Math.abs(z - c[1]) <= 6) return true;
        for (int[][] set : new int[][][] {PATROL, FIRE_BARRELS, {{305, -96}}})
            for (int[] c : set) if (Math.abs(x - c[0]) <= 1 && Math.abs(z - c[1]) <= 1) return true;
        return false;
    }
}
