package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Элеватор на Кордоне по геометрии оригинала (S.T.A.L.K.E.R. Anomaly, l01_escape): высокая бетонная башня-силос
 * с лестницей внутри и этажами через 4 блока, корпус элеватора (ржавый металл, старый бетон, доски, шифер),
 * склады и навесы вокруг, забор. Размеры и места сняты с вокселей уровня (tools/xray),
 * собираются шаблонами с ровными стенами, крышами, окнами и дверями.
 *
 * <p>Уровень развития (как в MineColonies): 0 — руины, 1 — лагерь, 2 — обжитой, 3 — укреплённый.
 */
public final class KordonElevator {
    private KordonElevator() {}

    /** Области, где воксели уровня заменяет этот сборщик: {x0, z0, x1, z1}. */
    public static final List<int[]> CLEAR = List.of(
            new int[] {128, -272, 141, -255},   // башня-силос и техника
            new int[] {175, -247, 246, -158}    // корпус, склады, навесы
    );

    static final String CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty",
            CONC_OLD = "stalkerzone:zm_concrete_old", CONC_SLAB = "stalkerzone:zm_concrete_slab",
            BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty",
            BOARDS = "stalkerzone:zm_planks_gray", BOARDS_O = "stalkerzone:zm_boards_old",
            SHIFER = "stalkerzone:zm_shifer", SHIFER_ST = "stalkerzone:zm_shifer_stairs",
            RUST = "stalkerzone:zm_rust_sheet", RUST_ST = "stalkerzone:zm_rust_sheet_stairs",
            RUST_SLAB = "stalkerzone:zm_rust_sheet_slab", CORR = "stalkerzone:zm_corrugated",
            CORR_SLAB = "stalkerzone:zm_corrugated_slab", BAGS = "stalkerzone:zm_sandbags",
            PO2 = "stalkerzone:concrete_fence", GLASS = "stalkerzone:zm_glass_dirty_pane",
            BARS = "minecraft:iron_bars", LOG = "minecraft:spruce_log",
            LADDER = "minecraft:ladder", AIR = "minecraft:air", GRAVEL = "minecraft:gravel",
            PATH = "minecraft:coarse_dirt";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня: на руинах часть блоков стен и крыш выбита. */
    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = Math.max(0, Math.min(3, lvl)); }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        /** Рабочее место (карточка 031): отмечается тем же проходом сборки. */
        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

        /** Блок стены/крыши: на уровне 0 — четверть выбита, на 1 — изредка. */
        void wall(int x, int y, int z, String st) {
            int h = hash(x, y, z) % 100;
            if (lvl == 0 && h < 22) { s.set(x, y, z, AIR); return; }
            if (lvl == 1 && h < 4) { s.set(x, y, z, AIR); return; }
            s.set(x, y, z, st);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.set(x, y, z, st);
        }

        /** Пол здания: медиана высоты земли в следе + 1. */
        int floor(int x0, int z0, int x1, int z1, String found) {
            int n = (x1 - x0 + 1) * (z1 - z0 + 1), i = 0;
            int[] gs = new int[n];
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) gs[i++] = ground(x, z);
            java.util.Arrays.sort(gs);
            int top = gs[n / 2];
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                int g = ground(x, z);
                for (int y = g + 1; y <= top; y++) s.set(x, y, z, found);
                for (int y = top + 1; y <= g; y++) s.set(x, y, z, AIR);
                if (g > top) s.set(x, top, z, found);
            }
            return top + 1;
        }

        /** Окно: стекло (на уровне 0 пусто, на 1 заколочено досками через одно). */
        void window(int x, int y, int z) {
            String w = lvl == 0 ? AIR : lvl == 1 && (hash(x, y, z) & 1) == 0 ? BOARDS_O : GLASS;
            s.set(x, y, z, w);
        }
/** Коробка стен с углами, окнами по шагу и проёмами дверей; внутри пусто. */
        void box(int x0, int z0, int x1, int z1, int y0, int h, String wallMat, String cornerMat, int winStep, int winY, int[][] doors) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = y0; y < y0 + h; y++) {
                    if (!edge) { s.set(x, y, z, AIR); continue; }
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    wall(x, y, z, corner ? cornerMat : wallMat);
                }
            }
            if (winStep > 0) for (int y : new int[] {winY}) {
                for (int x = x0 + 2; x <= x1 - 2; x += winStep) { window(x, y0 + y, z0); window(x, y0 + y, z1); }
                for (int z = z0 + 2; z <= z1 - 2; z += winStep) { window(x0, y0 + y, z); window(x1, y0 + y, z); }
            }
            if (doors != null) for (int[] d : doors) door(d[0], y0, d[1], d[2] == 1);
        }

        /** Дверной проём 1×2 (на уровне 2+ — дверь). alongX: стена тянется по X. */
        void door(int x, int y, int z, boolean alongX) {
            if (lvl >= 2) {
                String f = alongX ? "south" : "east";
                s.set(x, y, z, "minecraft:spruce_door[facing=" + f + ",half=lower,hinge=left]");
                s.set(x, y + 1, z, "minecraft:spruce_door[facing=" + f + ",half=upper,hinge=left]");
            } else {
                s.set(x, y, z, AIR);
                s.set(x, y + 1, z, AIR);
            }
        }

        /** Двускатная крыша с коньком вдоль Z. */
        void gableZ(int x0, int z0, int x1, int z1, int y, String roofSt, String roofFull, String gable, int overhang) {
            int w = x1 - x0 + 1, half = (w + 1) / 2;
            for (int i = 0; i < half; i++) {
                int yy = y + i;
                int xa = x0 - overhang + i, xb = x1 + overhang - i;
                if (xa > xb) break;
                for (int z = z0 - overhang; z <= z1 + overhang; z++) {
                    wall(xa, yy, z, roofSt);
                    if (xb != xa) wall(xb, yy, z, roofSt);
                }
                for (int z = z0; z <= z1; z++) wall(xa, yy + 1, z, gable);
            }
            int peak = y + half - 1;
            for (int z = z0 - overhang; z <= z1 + overhang; z++) wall(x0 + overhang + half - 1, peak, z, roofFull);
        }
/** Вальмовая (четырёхскатная) крыша. */
        void hip(int x0, int z0, int x1, int z1, int y, String roofSt, String roofFull) {
            int a = x0, b = x1, c = z0, d = z1, yy = y;
            while (true) {
                for (int x = a; x <= b; x++) for (int z = c; z <= d; z++) {
                    boolean edge = x == a || x == b || z == c || z == d;
                    wall(x, yy, z, edge ? roofSt : roofFull);
                }
                if (b - a <= 1 || d - c <= 1) {
                    for (int x = a; x <= b; x++) for (int z = c; z <= d; z++) wall(x, yy, z, roofFull);
                    break;
                }
                a++; b--; c++; d--; yy++;
            }
        }

        /** Плоская крыша с парапетом (на уровне 3 — мешки на крыше). */
        void flat(int x0, int z0, int x1, int z1, int y, String roof, String parapet) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                wall(x, y, z, roof);
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge && parapet != null) wall(x, y + 1, z, lvl >= 3 ? BAGS : parapet);
            }
        }
    }
/** Построить элеватор. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, lvl);
        clearAndPave(b);
        siloTower(b);
        mainBuilding(b);
        warehouses(b);
        smallBuilding(b);
        vehicles(b);
        fence(b);
        workplaces(b);
    }

    // ---------------------------------------------------------------- площадка

    private static void clearAndPave(S b) {
        for (int[] c : CLEAR) {
            for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
                int g = b.ground(x, z);
                for (int y = g + 1; y <= g + 20; y++) b.set(x, y, z, AIR);
            }
        }
        // двор между корпусом и складами
        for (int x = 208; x <= 217; x++) for (int z = -236; z <= -220; z++)
            b.set(x, b.ground(x, z), z, (hash(x, 2, z) % 7 == 0) ? GRAVEL : PATH);
    }
// ---------------------------------------------------------------- башня-силос

    /** Высокая бетонная башня элеватора (X 131..138, Z -269..-263):
     *  этажи через 4 блока, лестница внутри до верха, пост часового наверху. */
    private static void siloTower(S b) {
        int y = b.floor(131, -268, 136, -264, CONC_D);
        b.fill(131, y - 1, -268, 136, y - 1, -264, CONC_D);
        // стены башни: бетон, 3 этажа по 4 блока
        for (int floor = 0; floor < 3; floor++) {
            int fy = y + floor * 4;
            b.box(131, -268, 136, -264, fy, 4, CONC, CONC_OLD, 0, 0, null);
            if (floor < 2) b.fill(132, fy + 4, -267, 135, fy + 4, -265, BOARDS_O);
            // проём для лестницы у южной стены
            b.set(133, fy, -264, AIR); b.set(133, fy + 1, -264, AIR);
        }
        // верхняя площадка и парапет
        int top = y + 12;
        b.fill(131, top, -268, 136, top, -264, CONC_SLAB);
        for (int x = 131; x <= 136; x++) {
            for (int z = -268; z <= -264; z++) {
                boolean edge = x == 131 || x == 136 || z == -268 || z == -264;
                if (edge) b.wall(x, top + 1, z, b.lvl >= 3 ? BAGS : CONC_SLAB);
            }
        }
        // лестница внутри по южной стене
        b.fill(133, y, -264, 133, top, -264, LADDER + "[facing=north]");
        // вход внизу с востока
        b.set(136, y, -266, AIR); b.set(136, y + 1, -266, AIR);
        if (b.lvl >= 2) {
            b.set(136, y, -266, "minecraft:spruce_door[facing=west,half=lower,hinge=left]");
            b.set(136, y + 1, -266, "minecraft:spruce_door[facing=west,half=upper,hinge=left]");
        }
        // пост часового наверху
        b.mark("guard", 133, top + 1, -266);
        if (b.lvl >= 2) b.set(133, top + 2, -266, "minecraft:lantern[hanging=true]");
        // низкая пристройка с востока (X 137-138)
        int low = b.floor(137, -268, 138, -263, CONC_D);
        b.box(137, -268, 138, -263, low, 4, CONC_OLD, CONC_D, 0, 0, new int[][] {{138, -266, 1}});
        b.flat(137, -268, 138, -263, low + 4, CONC_SLAB, null);
    }
// ---------------------------------------------------------------- корпус элеватора

    /** Главный корпус элеватора (X 207..234, Z -244..-227):
     *  северная секция — доски с шиферной крышей, южная — старый бетон и ржавый металл,
     *  центральная высокая секция — конвейерная башня. */
    private static void mainBuilding(S b) {
        // северная секция (доски, шифер, двускатная, 2 этажа)
        int northY = b.floor(208, -244, 233, -238, CONC_D);
        b.fill(208, northY - 1, -244, 233, northY - 1, -238, CONC_D);
        b.box(208, -244, 233, -238, northY, 4, BOARDS, LOG, 3, 1, new int[][] {{213, -238, 1}, {228, -238, 1}});
        b.fill(209, northY + 4, -243, 232, northY + 4, -239, BOARDS_O);
        b.box(208, -244, 233, -238, northY + 4, 4, BOARDS, LOG, 3, 1, null);
        b.gableZ(208, -244, 233, -238, northY + 8, SHIFER_ST, SHIFER, BOARDS, 1);
        // лестница на второй этаж
        b.fill(210, northY, -244, 210, northY + 7, -244, LADDER + "[facing=south]");

        // южная секция (старый бетон, ржавый металл, выше)
        int southY = b.floor(208, -237, 233, -227, CONC_D);
        b.fill(208, southY - 1, -237, 233, southY - 1, -227, CONC_D);
        b.box(208, -237, 233, -227, southY, 5, CONC_OLD, CONC_D, 4, 1, new int[][] {{217, -227, 1}, {225, -227, 1}});
        b.fill(209, southY + 5, -236, 232, southY + 5, -228, CONC);
        b.box(208, -237, 233, -227, southY + 5, 5, RUST, CONC_D, 4, 2, null);
        b.fill(209, southY + 10, -236, 232, southY + 10, -228, RUST_SLAB);
        b.gableZ(208, -237, 233, -227, southY + 10, RUST_ST, RUST, CONC_OLD, 1);

        // центральная конвейерная башня (X 215..226, Z -240..-231)
        int twY = b.floor(215, -240, 226, -232, CONC_D);
        b.fill(215, twY - 1, -240, 226, twY - 1, -232, CONC_D);
        for (int fl = 0; fl < 2; fl++) {
            int fy = twY + fl * 5;
            b.box(215, -240, 226, -232, fy, 5, CONC_OLD, CONC_D, 0, 0, null);
            b.fill(216, fy + 5, -239, 225, fy + 5, -233, CONC);
        }
        b.box(215, -240, 226, -232, twY + 10, 4, CONC_OLD, CONC_D, 0, 0, null);
        b.flat(215, -240, 226, -232, twY + 14, CONC_SLAB, CONC_D);
        // лестница внутри конвейерной башни
        b.fill(220, twY, -240, 220, twY + 13, -240, LADDER + "[facing=south]");
        // проход между секциями
        for (int x = 209; x <= 212; x++) b.set(x, northY + 4, -238, AIR);

        // рабочие места
        b.mark("work", 220, twY + 10, -235);
    }
// ---------------------------------------------------------------- склады и навесы

    /** Склады и навесы вокруг корпуса (X 178..217, Z -236..-194):
     *  кирпичный склад, дощатые сараи, открытые навесы. */
    private static void warehouses(S b) {
        // кирпичный склад (северо-запад)
        int wy = b.floor(178, -236, 193, -222, CONC_D);
        b.fill(178, wy - 1, -236, 193, wy - 1, -222, CONC_D);
        b.box(178, -236, 193, -222, wy, 4, BRICK, BRICK_D, 4, 1, new int[][] {{188, -236, 1}, {185, -222, 1}});
        b.fill(179, wy + 4, -235, 192, wy + 4, -223, BOARDS_O);
        b.box(178, -236, 193, -222, wy + 4, 3, BRICK, BRICK_D, 4, 1, null);
        b.gableZ(178, -236, 193, -222, wy + 7, SHIFER_ST, SHIFER, BRICK, 1);
        // нары и спальные места в складе
        if (b.lvl >= 1) for (int z = -234; z <= -224; z += 3) {
            b.set(179, wy, z, "minecraft:spruce_slab[type=top]");
            b.mark("bed", 180, wy, z);
        }

        // дощатый сарай (юго-запад)
        int sy = b.floor(178, -220, 195, -208, CONC_D);
        b.fill(178, sy - 1, -220, 195, sy - 1, -208, CONC_D);
        b.box(178, -220, 195, -208, sy, 4, BOARDS, LOG, 3, 1, new int[][] {{183, -208, 1}});
        b.gableZ(178, -220, 195, -208, sy + 4, SHIFER_ST, SHIFER, BOARDS, 1);

        // дощатый сарай (южный)
        int s2y = b.floor(179, -202, 194, -194, CONC_D);
        b.fill(179, s2y - 1, -202, 194, s2y - 1, -194, CONC_D);
        b.box(179, -202, 194, -194, s2y, 3, BOARDS_O, LOG, 0, 0, new int[][] {{183, -194, 0}});
        b.gableZ(179, -202, 194, -194, s2y + 3, SHIFER_ST, SHIFER, BOARDS_O, 0);

        // открытый навес (между корпусом и складами, X 200..207, Z -228..-220)
        int ny = b.floor(200, -228, 207, -220, CONC_D);
        for (int z = -228; z <= -220; z++) {
            b.wall(200, ny, z, LOG); b.wall(200, ny + 1, z, LOG);
            b.wall(207, ny, z, LOG); b.wall(207, ny + 1, z, LOG);
        }
        b.fill(200, ny + 3, -229, 207, ny + 3, -221, SHIFER_ST);
        b.flat(200, -228, 207, -221, ny + 3, SHIFER, null);
        // ящики под навесом
        if (b.lvl >= 1) {
            b.set(202, ny, -225, "stalkerzone:military_crate");
            b.set(205, ny, -224, "stalkerzone:wooden_crate");
            b.mark("work", 203, ny, -223);
        }
    }
// ---------------------------------------------------------------- малая постройка

    /** Небольшая бетонная постройка (X 231..243, Z -173..-161). */
    private static void smallBuilding(S b) {
        int y = b.floor(233, -171, 241, -163, CONC_D);
        b.fill(233, y - 1, -171, 241, y - 1, -163, CONC_D);
        b.box(233, -171, 241, -163, y, 3, CONC_OLD, CONC_D, 0, 0, new int[][] {{237, -163, 0}});
        b.flat(233, -171, 241, -163, y + 3, CORR_SLAB, null);
    }

    // ---------------------------------------------------------------- техника

    /** Брошенная техника у элеватора. */
    private static void vehicles(S b) {
        // грузовик у башни-силоса
        int zi = -1, ka = -1, tr = -1;
        for (int i = 0; i < Vehicles.ALL.length; i++) {
            if (Vehicles.ALL[i].equals("zil")) zi = i;
            if (Vehicles.ALL[i].equals("kamaz")) ka = i;
            if (Vehicles.ALL[i].equals("tractor")) tr = i;
        }
        if (zi >= 0) {
            B v = new B(b.s, 132, -259, 0, 5151L, b.lvl);
            Vehicles.vehicle(v, Vehicles.ALL[zi], 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        }
        if (ka >= 0) {
            B v = new B(b.s, 198, -230, 90, 5151L, b.lvl);
            Vehicles.vehicle(v, Vehicles.ALL[ka], 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        }
        if (tr >= 0) {
            B v = new B(b.s, 190, -210, 90, 5151L, b.lvl);
            Vehicles.vehicle(v, Vehicles.ALL[tr], 0, 0, 0, null, b.lvl == 0 ? 2 : 1);
        }
    }
// ---------------------------------------------------------------- забор

    /** Бетонный забор периметра вокруг башни, корпуса и складов. */
    private static void fence(S b) {
        // вокруг башни
        line(b, 129, -270, 129, -261);
        line(b, 129, -261, 139, -261);
        line(b, 139, -261, 139, -270);
        line(b, 139, -270, 129, -270);
        // вокруг корпуса и складов — север
        line(b, 200, -244, 235, -244);
        // восток
        line(b, 235, -244, 235, -196);
        line(b, 235, -196, 243, -196);
        line(b, 243, -196, 243, -168);
        line(b, 243, -168, 235, -168);
        line(b, 235, -168, 235, -161);
        // юг
        line(b, 235, -161, 233, -161);
        // запад (с проездом X=178 Z=-205..-210)
        line(b, 178, -196, 178, -210);
        line(b, 178, -205, 183, -244);
        line(b, 183, -244, 178, -244);
    }

    private static void line(S b, int xa, int za, int xb, int zb) {
        int n = Math.max(Math.abs(xb - xa), Math.abs(zb - za));
        for (int i = 0; i <= n; i++) {
            int x = xa + Integer.signum(xb - xa) * i, z = za + Integer.signum(zb - za) * i;
            int g = b.ground(x, z);
            boolean post = i % 3 == 0;
            if (b.lvl == 0 && !post && hash(x, 7, z) % 5 == 0) continue;
            for (int y = g + 1; y <= g + 3; y++) b.set(x, y, z, post ? CONC : PO2);
            if (post) b.set(x, g + 4, z, b.lvl >= 3 ? BARS : CONC_SLAB);
            else if (b.lvl >= 3) b.set(x, g + 4, z, BARS);
        }
    }

    // ---------------------------------------------------------------- рабочие места

    /** Карточка 031: cook во дворе между корпусом и складами, patrol по периметру. */
    private static void workplaces(S b) {
        // повар во дворе
        int cy = b.ground(208, -232);
        b.mark("cook", 208, cy + 1, -232);
        if (b.lvl >= 1) {
            b.set(207, cy + 1, -232, "minecraft:spruce_slab[type=bottom]");
            b.set(209, cy + 1, -232, "minecraft:spruce_slab[type=bottom]");
            b.set(208, cy + 1, -231, "minecraft:campfire[lit=true]");
        }
        // маршрут патруля — обход периметра
        int[][] route = {
            {132, -266}, {132, -261}, {138, -261}, {138, -266},  // вокруг башни
            {200, -244}, {230, -244}, {240, -244},                // север
            {240, -170}, {235, -165}, {233, -165},                // восток
            {180, -196}, {180, -230}, {200, -240}                 // запад
        };
        for (int[] p : route) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }
}