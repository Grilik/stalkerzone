package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.ArrayList;
import java.util.List;

/**
 * Элеватор Кордона (узел 4, смарт-террейн {@code esc_smart_terrain_5_9}) — заново по вокселям оригинала l01_escape
 * (Anomaly) и обмеру {@code docs/kordon_objects/04-elevator.md} (П-22). Прежний сборщик (отключён в PR #64) переписан.
 *
 * <p>Что стоит (по срезам вокселей, земля ≈ 102,5):
 * <ul>
 *   <li><b>башня элеватора</b> (обмер 5, восток) — бетонная, x 227..233, z −239..−231, верх ≈ +13; внутри лестница
 *   до плоской крыши с ограждением — пост часового;</li>
 *   <li><b>силосы</b> — три ржавых цилиндра (центры x 209, 214, 219; z −235), высота +7;</li>
 *   <li><b>галерея</b> — ржавый короб на высоте +8..+11 над силосами от x 207 до башни (вход с этажа башни +8);</li>
 *   <li>бетонный каркас между силосами и башней (x 222..226, z −243..−229) с дощатым настилом на +4;</li>
 *   <li><b>коровники</b> (обмер 6): длинный западный x 179..192, z −235..−211 (кирпич, кровля скатами, конёк ≈ +11)
 *   и южный x 185..215, z −212..−200 (пологая кровля, ≈ +7), две пристройки; внутри стойла, кормушки, сено;</li>
 *   <li>трактор у костра (обмер 7).</li>
 * </ul>
 *
 * <p>Решение пользователя: <b>малая застава одиночек</b> (3–4 человека). Костёр — точка узла (195, −227);
 * нары ×4 на первом этаже башни, {@code guard} ×2 (крыша башни и у костра лицом к дороге), {@code rest} ×2 у огня,
 * {@code shelter} ×3 (башня ×2, коровник). Уровни 0–3: 0 — брошено (костёр потушен, нар нет), 1 — стоянка,
 * 2 — пост (мешки у входа в башню и на крыше), 3 — укрепление (решётки на окнах башни, навес на крыше).
 */
public final class KordonElevator {
    private KordonElevator() {}

    /** Плато площадки elevator (ровная часть) и откос. */
    public static final int[] PLATEAU = {176, -247, 237, -191};
    public static final int MARGIN = 6;
    public static final List<int[]> CLEAR = List.of(new int[] {PLATEAU[0] - MARGIN, PLATEAU[1] - MARGIN, PLATEAU[2] + MARGIN, PLATEAU[3] + MARGIN});

    static final String BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty", BRICK_O = "stalkerzone:zm_brick_old",
            CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty", CONC_O = "stalkerzone:zm_concrete_old",
            PLATE = "stalkerzone:zm_concrete_plate", FLOOR = "stalkerzone:zm_concrete_floor",
            BOARDS = "stalkerzone:zm_boards_old", PLANKS = "stalkerzone:zm_planks_gray", SHIFER = "stalkerzone:zm_shifer",
            RUST = "stalkerzone:zm_rust_sheet", RUSTB = "stalkerzone:zm_rust", POST_F = "stalkerzone:zm_wood_post_fence",
            BAGS = "stalkerzone:zm_sandbags", MATTRESS = "stalkerzone:zm_mattress_slab[type=bottom]",
            CRATE = "stalkerzone:wooden_crate", MCRATE = "stalkerzone:military_crate", BARREL = "stalkerzone:rusty_barrel",
            TIRE = "stalkerzone:prop_tire[facing=north]", TRASH = "stalkerzone:prop_trash_bags[facing=north]",
            RUBBLE_SL = "stalkerzone:zm_rubble_slab[type=bottom]", HAY = "minecraft:hay_block[axis=y]",
            BARS = "minecraft:iron_bars", LADDER_W = "minecraft:ladder[facing=west]",
            FIRE_ON = "minecraft:campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]",
            FIRE_OFF = "minecraft:campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]",
            LOG = "minecraft:spruce_log[axis=x]", AIR = "minecraft:air";

    // башня
    public static final int TX0 = 227, TX1 = 233, TZ0 = -239, TZ1 = -231, DOOR_Z = -235;
    /** Лестница внутри башни у восточной стены. */
    public static final int LAD_X = 232, LAD_Z = -232;
    // силосы и галерея
    static final int[] SILOS = {209, 214, 219};
    static final int SILO_Z = -235, GX0 = 207, GZ0 = -237, GZ1 = -234;
    // коровники
    public static final int WX0 = 179, WX1 = 192, WZ0 = -235, WZ1 = -211;
    public static final int SX0 = 193, SX1 = 215, SZ0 = -212, SZ1 = -200;
    /** Костёр заставы — точка campfire узла 4 из kordon_points.json. */
    public static final int[] FIRE = {195, -227};

    static final double[] WEAR = {1.0, 0.6, 0.35, 0.2};
    private static final ThreadLocal<Integer> LVL = ThreadLocal.withInitial(() -> 1);
    static int lvl() { return LVL.get(); }

    static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1 ^ 0x3E1E;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    static boolean p(int x, int y, int z, double p) { return hash(x, y, z) % 1000 < p * 1000; }

    static boolean hole(int x, int y, int z, double p) { return p(x, y, z, p * WEAR[lvl()]); }

    static int base(BlockSink b, int x0, int z0, int x1, int z1) {
        List<Integer> gs = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) gs.add(b.ground(x, z));
        gs.sort(Integer::compare);
        return gs.get(gs.size() / 2);
    }

    /** Построить элеватор на уровне развития lvl (0–3). */
    public static void build(BlockSink b, int lvl) {
        LVL.set(Math.max(0, Math.min(3, lvl)));
        int g = base(b, 200, -245, 235, -225);
        frame(b, g);
        silos(b, g);
        tower(b, g);
        gallery(b, g);
        westBarn(b);
        southBarn(b);
        camp(b);
        yard(b);
    }

    // ---------------------------------------------------------------- башня

    /**
     * Башня: стены из старого бетона, три перекрытия (+4, +8, +12) с люками над лестницей, окна-бойницы.
     * Первый этаж — жильё заставы (4 нары), второй — укрытие, с третьего (+8) вход в галерею, крыша (+12) — пост.
     */
    static void tower(BlockSink b, int g) {
        int l = lvl(), y0 = g + 1, top = g + 12;
        for (int x = TX0; x <= TX1; x++) for (int z = TZ0; z <= TZ1; z++) {
            boolean edge = x == TX0 || x == TX1 || z == TZ0 || z == TZ1;
            boolean corner = (x == TX0 || x == TX1) && (z == TZ0 || z == TZ1);
            b.set(x, g, z, edge ? CONC_O : FLOOR);
            for (int y = y0; y < top; y++) {
                if (!edge) { b.set(x, y, z, (y - g) % 4 == 0 ? PLATE : AIR); continue; }
                b.set(x, y, z, corner ? CONC_D : hole(x, y, z, 0.06 * (y - g) / 6.0) ? AIR : CONC_O);
            }
            b.set(x, top, z, edge ? CONC_D : PLATE);
        }
        // окна-бойницы по этажам (на ур. 3 — решётки)
        for (int fl = 0; fl < 3; fl++) {
            int wy = g + 2 + fl * 4;
            for (int x = TX0 + 2; x <= TX1 - 2; x += 2) for (int z : new int[] {TZ0, TZ1}) b.set(x, wy, z, l >= 3 ? BARS : AIR);
            for (int z = TZ0 + 2; z <= TZ1 - 2; z += 3) b.set(TX1, wy, z, l >= 3 ? BARS : AIR);
        }
        // вход с запада и проём в галерею на +8
        for (int y = y0; y < y0 + 2; y++) b.set(TX0, y, DOOR_Z, AIR);
        for (int z = GZ0 + 1; z < GZ1; z++) for (int y = g + 9; y <= g + 10; y++) b.set(TX0, y, z, AIR);
        // лестница сквозь люки до крыши
        for (int y = y0; y <= top; y++) b.set(LAD_X, y, LAD_Z, LADDER_W);
        b.set(LAD_X + 1, top, LAD_Z, CONC_D);
        // крыша-пост: ограждение по краю (мешки с ур. 2), навес на ур. 3
        for (int x = TX0; x <= TX1; x++) for (int z = TZ0; z <= TZ1; z++) {
            boolean edge = x == TX0 || x == TX1 || z == TZ0 || z == TZ1;
            if (edge) b.set(x, top + 1, z, l >= 2 ? BAGS : BARS);
        }
        if (l >= 3) {
            for (int[] c : new int[][] {{TX0 + 1, TZ0 + 1}, {TX1 - 1, TZ0 + 1}, {TX0 + 1, TZ0 + 4}, {TX1 - 1, TZ0 + 4}})
                for (int y = top + 1; y <= top + 2; y++) b.set(c[0], y, c[1], POST_F);
            for (int x = TX0 + 1; x <= TX1 - 1; x++) for (int z = TZ0 + 1; z <= TZ0 + 4; z++) b.set(x, top + 3, z, RUST + "_slab[type=bottom]");
        }
        b.mark("guard", 230, top + 1, -237);
        // первый этаж: нары ×4 у северной стены, ящики, мешки у входа
        for (int x = TX0 + 1; x <= TX0 + 4; x++) {
            b.set(x, y0, TZ0 + 1, l >= 1 ? MATTRESS : p(x, y0, TZ0, 0.5) ? TRASH : AIR);
            b.mark("bed", x, y0, TZ0 + 2);
        }
        b.set(TX1 - 1, y0, TZ0 + 1, CRATE); b.set(TX1 - 1, y0, TZ0 + 3, l >= 1 ? MCRATE : CRATE); b.set(TX0 + 1, y0, TZ1 - 1, BARREL);
        if (l >= 2) for (int z : new int[] {DOOR_Z - 2, DOOR_Z + 2}) b.set(TX0 - 1, y0, z, BAGS);
        b.mark("shelter", 230, y0, -233);
        // второй этаж: укрытие
        b.set(TX0 + 1, g + 5, TZ0 + 1, CRATE); b.set(TX0 + 1, g + 5, TZ1 - 1, TRASH);
        b.mark("shelter", 229, g + 5, -235);
    }

    /** Бетонный каркас между силосами и башней: колонны, балки и дощатый настил на +4; колонны рядов галереи — до +8. */
    static void frame(BlockSink b, int g) {
        int x0 = 222, x1 = 226, z0 = -243, z1 = -229;
        for (int x = x0; x <= x1; x += 4) for (int z = z0; z <= z1; z += (z == -239 ? 2 : 4)) {
            int h = z == GZ0 || z == GZ1 || z == -237 || z == -235 ? 8 : 4;
            for (int y = g + 1; y < g + h; y++) b.set(x, y, z, CONC_O);
        }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean beam = x == x0 || x == x1 || z == z0 || z == z1;
            if (beam) b.set(x, g + 4, z, CONC_O);
            else if (!hole(x, g + 4, z, 0.25)) b.set(x, g + 4, z, BOARDS + "_slab[type=bottom]");
        }
        // под настилом — завал мешков с зерном (сено) и тележка
        b.set(224, g + 1, -241, HAY); b.set(225, g + 1, -241, HAY); b.set(225, g + 2, -241, HAY); b.set(223, g + 1, -231, BARREL);
    }

    /** Силосы: три ржавых цилиндра ⌀5 на бетонном цоколе, высота +7, сверху — галерея. */
    static void silos(BlockSink b, int g) {
        for (int cx : SILOS) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
            int x = cx + dx, z = SILO_Z + dz;
            boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            for (int y = g + 1; y <= g + 7; y++) {
                if (!ring) { b.set(x, y, z, y == g + 7 ? RUSTB : AIR); continue; }
                b.set(x, y, z, y == g + 1 ? CONC_O : hole(x, y, z, 0.05) ? AIR : (hash(x, y, z) & 3) == 0 ? RUSTB : RUST);
            }
            b.set(x, g, z, CONC_O);
        }
    }

    /**
     * Галерея: ржавый короб над силосами от x 207 до башни (z −237..−234), пол из досок на +8, стены до +10,
     * крыша на +11; окна-проёмы; вход с этажа башни +8. С ур. 0 часть пола провалена у западного конца.
     */
    static void gallery(BlockSink b, int g) {
        int fl = g + 8;
        for (int x = GX0; x < TX0; x++) for (int z = GZ0; z <= GZ1; z++) {
            boolean side = z == GZ0 || z == GZ1, end = x == GX0;
            b.set(x, fl, z, side ? RUSTB : (lvl() == 0 && x < GX0 + 3) ? AIR : PLANKS);
            for (int y = fl + 1; y <= fl + 2; y++) {
                if (side || end) {
                    boolean win = y == fl + 2 && Math.floorMod(x - GX0, 3) == 1;
                    b.set(x, y, z, win || hole(x, y, z, 0.12) ? AIR : RUST);
                } else b.set(x, y, z, AIR);
            }
            if (!hole(x, fl + 3, z, 0.15)) b.set(x, fl + 3, z, RUST + "_slab[type=bottom]");
        }
        b.set(GX0 + 4, fl + 1, GZ0 + 1, CRATE);
    }

    // ---------------------------------------------------------------- коровники

    /** Стена с дырами по уровню; окна-проёмы через 3 на высоте +2. */
    static void walls(BlockSink b, int x0, int z0, int x1, int z1, int g, int h, String low, String up) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (!edge) { b.set(x, g, z, p(x, g, z, 0.3) ? "minecraft:coarse_dirt" : FLOOR); for (int y = g + 1; y <= g + h; y++) b.set(x, y, z, AIR); continue; }
            for (int y = g + 1; y <= g + h; y++) {
                boolean win = y == g + 2 && ((x == x0 || x == x1) ? Math.floorMod(z - z0, 3) == 1 : Math.floorMod(x - x0, 3) == 1)
                        && !((x == x0 || x == x1) && (z == z0 || z == z1));
                b.set(x, y, z, win || hole(x, y, z, 0.10) ? AIR : y - g <= 2 ? low : up);
            }
        }
    }

    /**
     * Западный коровник: кирпичные стены 4, двускатная кровля framed-скатами из шифера вдоль z (конёк ≈ +11),
     * ворота в торцах, стойла с кормушками по обе стороны прохода; южный конец целый — укрытие.
     */
    static void westBarn(BlockSink b) {
        int g = base(b, WX0, WZ0, WX1, WZ1), h = 4, yT = g + h + 1;
        walls(b, WX0, WZ0, WX1, WZ1, g, h, BRICK_D, BRICK);
        int w = WX1 - WX0 + 1, half = w / 2;
        for (int i = 0; i < half; i++) {
            int y = yT + i, xa = WX0 - 1 + i, xb = WX1 + 1 - i;
            for (int z = WZ0 - 1; z <= WZ1 + 1; z++) {
                boolean keep = z >= WZ1 - 6;   // над укрытием у южного торца кровля целая
                if (!keep && hole(xa >> 1, y, z >> 1, 0.22)) { } else b.set(xa, y, z, Framed.slope(SHIFER, "east", false));
                if (!keep && hole(xb >> 1, y + 7, z >> 1, 0.22)) { } else b.set(xb, y, z, Framed.slope(SHIFER, "west", false));
            }
            for (int x = xa + 1; x < xb; x++) for (int z : new int[] {WZ0, WZ1}) b.set(x, y, z, BOARDS);
        }
        for (int z = WZ0 - 1; z <= WZ1 + 1; z++) for (int x = WX0 - 1 + half; x <= WX1 + 1 - half; x++) b.set(x, yT + half - 1, z, SHIFER);   // конёк
        // ворота в торцах (по центру, 2 × 3) и боковая дверь к костру
        for (int x = WX0 + half - 1; x <= WX0 + half; x++) for (int y = g + 1; y <= g + 3; y++) { b.set(x, y, WZ0, AIR); b.set(x, y, WZ1, AIR); }
        for (int y = g + 1; y <= g + 2; y++) b.set(WX1, y, -225, AIR);
        // стойла: перегородки-жерди и кормушки вдоль проходов
        for (int z = WZ0 + 3; z <= WZ1 - 8; z += 3) for (int x : new int[] {WX0 + 1, WX0 + 2, WX0 + 3, WX1 - 3, WX1 - 2, WX1 - 1})
            if (!p(x, g + 1, z, 0.3 * WEAR[lvl()])) b.set(x, g + 1, z, POST_F);
        for (int z = WZ0 + 2; z <= WZ1 - 8; z++) {
            if (p(WX0 + 4, g, z, 0.3)) b.set(WX0 + 4, g + 1, z, CONC_O + "_slab[type=bottom]");
            if (p(WX1 - 4, g, z, 0.3)) b.set(WX1 - 4, g + 1, z, CONC_O + "_slab[type=bottom]");
        }
        for (int[] c : new int[][] {{WX0 + 1, WZ0 + 1}, {WX0 + 2, WZ0 + 1}, {WX0 + 1, WZ0 + 2}, {WX1 - 1, -220}})
            b.set(c[0], g + 1, c[1], HAY);
        b.set(WX0 + 1, g + 2, WZ0 + 1, HAY);
        // южный конец: ящики, укрытие
        b.set(WX0 + 1, g + 1, WZ1 - 1, CRATE); b.set(WX1 - 1, g + 1, WZ1 - 1, BARREL);
        b.mark("shelter", WX0 + 3, g + 1, WZ1 - 3);
    }

    /**
     * Южный коровник вдоль x: кирпич до +2, выше доски, пологая кровля плитами (≈ +7), двери на восточном торце
     * и к двору; пристройки — северная (молочная) и южная (развалины).
     */
    static void southBarn(BlockSink b) {
        int g = base(b, SX0, SZ0, SX1, SZ1), h = 3, yT = g + h + 1;
        walls(b, SX0, SZ0, SX1, SZ1, g, h, BRICK_O, BOARDS);
        for (int z = SZ0 - 1; z <= SZ1 + 1; z++) {
            int k = Math.min(z - (SZ0 - 1), (SZ1 + 1) - z), y = yT + k / 2;
            for (int x = SX0 - 1; x <= SX1 + 1; x++) {
                if (hole(x >> 1, y, z >> 1, 0.25)) continue;
                b.set(x, y, z, SHIFER + "_slab[type=" + (k % 2 == 0 ? "bottom" : "top") + "]");
            }
            if (z > SZ0 && z < SZ1) for (int yy = yT; yy < y; yy++) { b.set(SX0, yy, z, BOARDS); b.set(SX1, yy, z, BOARDS); }
        }
        for (int z = -207; z <= -206; z++) for (int y = g + 1; y <= g + 2; y++) { b.set(SX1, y, z, AIR); b.set(SX0, y, z, AIR); }
        for (int x = 203; x <= 204; x++) for (int y = g + 1; y <= g + 2; y++) b.set(x, y, SZ0, AIR);
        for (int x = SX0 + 2; x < SX1 - 1; x += 3) if (!p(x, g, -206, 0.4 * WEAR[lvl()])) b.set(x, g + 1, SZ0 + 3, POST_F);
        b.set(SX1 - 2, g + 1, SZ1 - 1, HAY); b.set(SX1 - 3, g + 1, SZ1 - 1, HAY); b.set(SX0 + 2, g + 1, SZ1 - 1, TRASH);
        // северная пристройка (x 195..201, z −217..−213) и южная (x 195..201, z −199..−194, без крыши)
        int gn = base(b, 195, -217, 201, -213);
        walls(b, 195, -217, 201, -213, gn, 3, BRICK_D, BRICK_D);
        for (int x = 195; x <= 201; x++) for (int z = -217; z <= -213; z++) if (!hole(x, gn + 4, z, 0.2)) b.set(x, gn + 4, z, SHIFER + "_slab[type=bottom]");
        for (int y = gn + 1; y <= gn + 2; y++) b.set(198, y, -213, AIR);
        int gs = base(b, 195, -199, 201, -194);
        for (int x = 195; x <= 201; x++) for (int z = -199; z <= -194; z++) {
            boolean edge = x == 195 || x == 201 || z == -194;
            if (edge) for (int y = gs + 1; y <= gs + 1 + (hash(x, 1, z) % 3); y++) b.set(x, y, z, BRICK_O);
            else if (p(x, gs, z, 0.35)) b.set(x, gs + 1, z, RUBBLE_SL);
        }
    }

    // ---------------------------------------------------------------- лагерь и двор

    /** Костёр заставы в точке узла, лавки-брёвна, rest ×2, часовой у костра лицом к дороге на юг. */
    static void camp(BlockSink b) {
        int l = lvl(), fx = FIRE[0], fz = FIRE[1], g = b.ground(fx, fz);
        b.set(fx, g, fz, CONC_O);
        b.set(fx, g + 1, fz, l >= 1 ? FIRE_ON : FIRE_OFF);
        if (l >= 1) { b.set(fx - 1, g + 1, fz - 2, LOG); b.set(fx, g + 1, fz - 2, LOG); b.set(fx + 1, g + 1, fz + 2, LOG); b.set(fx, g + 1, fz + 2, LOG); }
        b.mark("rest", fx - 2, g + 1, fz);
        b.mark("rest", fx + 2, g + 1, fz);
        b.set(fx - 2, g + 1, fz - 3, CRATE);
        int gx = 204, gz = -219;
        b.mark("guard", gx, b.ground(gx, gz) + 1, gz);
        if (l >= 2) for (int x = gx - 1; x <= gx + 1; x++) b.set(x, b.ground(x, gz + 1) + 1, gz + 1, BAGS);
        Vehicles.vehicle(new B(b, 200, -222, 0, 4071L, 0), "tractor", 0, 0, 0, null, 2);
    }

    /** Мусор: шины, бочки, обломки — редко, мимо построек. */
    static void yard(BlockSink b) {
        String[] junk = {TIRE, TRASH, RUBBLE_SL, BARREL, HAY, SHIFER + "_slab[type=bottom]"};
        for (int x = PLATEAU[0]; x <= PLATEAU[2]; x++) for (int z = PLATEAU[1]; z <= PLATEAU[3]; z++) {
            if (hash(x, 5, z) % 71 != 0 || busy(x, z)) continue;
            b.set(x, b.ground(x, z) + 1, z, junk[hash(x, 6, z) % junk.length]);
        }
    }

    static boolean busy(int x, int z) {
        int[][] fp = {{WX0 - 2, WZ0 - 2, WX1 + 2, WZ1 + 2}, {SX0 - 2, SZ0 - 2, SX1 + 2, SZ1 + 2}, {194, -218, 202, -193},
                {GX0 - 3, -244, TX1 + 2, -228}, {FIRE[0] - 4, FIRE[1] - 4, 207, FIRE[1] + 9}};
        for (int[] f : fp) if (x >= f[0] && x <= f[2] && z >= f[1] && z <= f[3]) return true;
        return false;
    }
}
