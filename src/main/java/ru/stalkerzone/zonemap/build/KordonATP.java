package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * АТП Кордона по геометрии оригинала (S.T.A.L.K.E.R. Anomaly, l01_escape).
 * Гаражи и мастерские (кирпич/доски/бетон, двускатные и плоские крыши), будки,
 * водонапорная башня/вышки, забор по периметру, техника.
 */
public final class KordonATP {
    private KordonATP() {}

    public static final List<int[]> CLEAR = List.of(new int[] {288, -160, 356, -86});

    static final String BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty",
            CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty",
            CONC_OLD = "stalkerzone:zm_concrete_old", CONC_SLAB = "stalkerzone:zm_concrete_slab",
            BOARDS = "stalkerzone:zm_planks_gray", BOARDS_O = "stalkerzone:zm_boards_old",
            SHIFER = "stalkerzone:zm_shifer", SHIFER_ST = "stalkerzone:zm_shifer_stairs",
            RUST = "stalkerzone:zm_rust_sheet", RUST_SLAB = "stalkerzone:zm_rust_sheet_slab",
            CORR = "stalkerzone:zm_corrugated", CORR_SLAB = "stalkerzone:zm_corrugated_slab",
            BAGS = "stalkerzone:zm_sandbags", PO2 = "stalkerzone:concrete_fence",
            GLASS = "stalkerzone:zm_glass_dirty_pane", BARS = "minecraft:iron_bars", LOG = "minecraft:spruce_log",
            LADDER = "minecraft:ladder", AIR = "minecraft:air",
            WOOD_DOOR = "minecraft:spruce_door", WOOD_SLAB = "minecraft:spruce_slab",
            CRATE = "stalkerzone:wooden_crate", MCRATE = "stalkerzone:military_crate";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = lvl; }

        int ground(int x, int z) { return s.ground(x, z); }
        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }
        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

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

        void window(int x, int y, int z) {
            String w = lvl == 0 ? AIR : lvl == 1 && (hash(x, y, z) & 1) == 0 ? BOARDS_O : GLASS;
            s.set(x, y, z, w);
        }

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

        void door(int x, int y, int z, boolean alongX) {
            if (lvl >= 2) {
                String f = alongX ? "south" : "east";
                s.set(x, y, z, WOOD_DOOR + "[facing=" + f + ",half=lower,hinge=left]");
                s.set(x, y + 1, z, WOOD_DOOR + "[facing=" + f + ",half=upper,hinge=left]");
            } else {
                s.set(x, y, z, AIR);
                s.set(x, y + 1, z, AIR);
            }
        }

        void gableZ(int x0, int z0, int x1, int z1, int y, String roofSt, String roofFull, String gable, int overhang) {
            int w = x1 - x0 + 1, half = (w + 1) / 2;
            for (int i = 0; i < half; i++) {
                int yy = y + i;
                int xa = x0 - overhang + i, xb = x1 + overhang - i;
                if (xa > xb) break;
                for (int z = z0 - overhang; z <= z1 + overhang; z++) {
                    if (xa == xb) { wall(xa, yy, z, roofFull); continue; }
                    wall(xa, yy, z, roofSt + "[facing=east,half=bottom]");
                    wall(xb, yy, z, roofSt + "[facing=west,half=bottom]");
                }
                for (int x = xa + 1; x < xb; x++) { wall(x, yy, z0, gable); wall(x, yy, z1, gable); }
            }
        }

        void gableX(int x0, int z0, int x1, int z1, int y, String roofSt, String roofFull, String gable, int overhang) {
            int w = z1 - z0 + 1, half = (w + 1) / 2;
            for (int i = 0; i < half; i++) {
                int yy = y + i;
                int za = z0 - overhang + i, zb = z1 + overhang - i;
                if (za > zb) break;
                for (int x = x0 - overhang; x <= x1 + overhang; x++) {
                    if (za == zb) { wall(x, yy, za, roofFull); continue; }
                    wall(x, yy, za, roofSt + "[facing=north,half=bottom]");
                    wall(x, yy, zb, roofSt + "[facing=south,half=bottom]");
                }
                for (int z = za + 1; z < zb; z++) { wall(x0, yy, z, gable); wall(x1, yy, z, gable); }
            }
        }

        void hip(int x0, int z0, int x1, int z1, int y, String roofSt, String roofFull) {
            int a = x0 - 1, b = x1 + 1, c = z0 - 1, d = z1 + 1, yy = y;
            while (a <= b && c <= d) {
                for (int x = a; x <= b; x++) {
                    wall(x, yy, c, a == b ? roofFull : roofSt + "[facing=south,half=bottom]");
                    wall(x, yy, d, a == b ? roofFull : roofSt + "[facing=north,half=bottom]");
                }
                for (int z = c + 1; z < d; z++) {
                    wall(a, yy, z, roofSt + "[facing=east,half=bottom]");
                    wall(b, yy, z, roofSt + "[facing=west,half=bottom]");
                }
                if (b - a <= 1 || d - c <= 1) {
                    for (int x = a; x <= b; x++) for (int z = c; z <= d; z++) wall(x, yy, z, roofFull);
                    break;
                }
                a++; b--; c++; d--; yy++;
            }
        }

        void flat(int x0, int z0, int x1, int z1, int y, String roof, String parapet) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                wall(x, y, z, roof);
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge && parapet != null) wall(x, y + 1, z, lvl >= 3 ? BAGS : parapet);
            }
        }

        void tower(int x0, int z0, int x1, int z1, char ladder) {
            int g = Integer.MAX_VALUE;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) g = Math.min(g, ground(x, z));
            int cx0 = x0, cz0 = z0, cx1 = Math.min(x1, x0 + 3), cz1 = Math.min(z1, z0 + 3);
            int top = g + 7;
            mark("guard", (cx0 + cx1) / 2, top + 1, (cz0 + cz1) / 2);
            for (int[] c : new int[][] {{cx0, cz0}, {cx1, cz0}, {cx0, cz1}, {cx1, cz1}})
                for (int y = ground(c[0], c[1]) + 1; y <= top + 3; y++) set(c[0], y, c[1], y <= top ? "minecraft:iron_block" : BARS);
            for (int x = cx0 - 1; x <= cx1 + 1; x++) for (int z = cz0 - 1; z <= cz1 + 1; z++) {
                wall(x, top, z, RUST_SLAB + "[type=top]");
                boolean edge = x == cx0 - 1 || x == cx1 + 1 || z == cz0 - 1 || z == cz1 + 1;
                if (edge) wall(x, top + 1, z, lvl >= 3 ? BAGS : BARS);
                wall(x, top + 4, z, CORR_SLAB);
            }
            int lx = (cx0 + cx1) / 2, lz = (cz0 + cz1) / 2;
            int[] p = switch (ladder) {
                case 'W' -> new int[] {cx0 - 1, lz};
                case 'E' -> new int[] {cx1 + 1, lz};
                case 'N' -> new int[] {lx, cz0 - 1};
                default -> new int[] {lx, cz1 + 1};
            };
            String face = switch (ladder) { case 'W' -> "west"; case 'E' -> "east"; case 'N' -> "north"; default -> "south"; };
            for (int y = ground(p[0], p[1]) + 1; y <= top + 1; y++) set(p[0], y, p[1], LADDER + "[facing=" + face + "]");
            if (lvl >= 2) set((cx0 + cx1) / 2, top + 3, (cz0 + cz1) / 2, "minecraft:lantern[hanging=true]");
        }

        void waterTower(int cx, int cz) {
            int g = ground(cx, cz);
            fill(cx - 1, g + 1, cz - 1, cx + 1, g + 2, cz + 1, CONC_OLD);
            for (int[] c : new int[][] {{cx - 1, cz - 1}, {cx + 1, cz - 1}, {cx - 1, cz + 1}, {cx + 1, cz + 1}})
                for (int y = g + 3; y <= g + 10; y++) set(c[0], y, c[1], "minecraft:iron_block");
            for (int y = g + 9; y <= g + 12; y++)
                for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) {
                    boolean edge = x == cx - 1 || x == cx + 1 || z == cz - 1 || z == cz + 1;
                    wall(x, y, z, edge ? RUST : RUST_SLAB);
                }
            fill(cx - 1, g + 12, cz - 1, cx + 1, g + 12, cz + 1, CORR);
            for (int y = g + 1; y <= g + 11; y++) set(cx + 2, y, cz, LADDER + "[facing=west]");
            mark("guard", cx, g + 11, cz);
        }

        void line(int xa, int za, int xb, int zb) {
            int n = Math.max(Math.abs(xb - xa), Math.abs(zb - za));
            for (int i = 0; i <= n; i++) {
                int x = xa + Integer.signum(xb - xa) * i, z = za + Integer.signum(zb - za) * i;
                int g = ground(x, z);
                boolean post = i % 3 == 0;
                if (lvl == 0 && !post && hash(x, 7, z) % 5 == 0) continue;
                for (int y = g + 1; y <= g + 3; y++) set(x, y, z, post ? CONC : PO2);
                if (post) set(x, g + 4, z, lvl >= 3 ? BARS : CONC_SLAB);
                else if (lvl >= 3) set(x, g + 4, z, BARS);
            }
        }
    }

    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, Math.max(0, Math.min(3, lvl)));
        clearAndPave(b);
        obj2_garage(b);
        obj3_workshop(b);
        obj4_workshop2(b);
        obj6_largeGarage(b);
        obj1_waterTower(b);
        obj7_tower(b);
        obj5_vehicles(b);
        obj8_vehicles(b);
        obj9_vehicles(b);
        fence(b);
        patrol(b);
        camp(b);
    }

    private static void clearAndPave(S b) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int g = b.ground(x, z);
            for (int y = g + 1; y <= g + 16; y++) b.set(x, y, z, AIR);
        }
    }

    private static void obj1_waterTower(S b) {
        b.waterTower(306, -155);
    }

    private static void obj2_garage(S b) {
        int x0 = 312, x1 = 330, z0 = -156, z1 = -135;
        int y = b.floor(x0, z0, x1, z1, CONC_D);
        b.fill(x0, y - 1, z0, x1, y - 1, z1, CONC_D);
        b.box(x0, z0, x1, z1, y, 8, BRICK, BOARDS, 4, 2, null);
        b.fill(316, y, -135, 318, y + 2, -135, AIR);
        b.fill(324, y, -135, 326, y + 2, -135, AIR);
        if (b.lvl >= 2) {
            for (int gx : new int[] {316, 324}) for (int dy = 0; dy < 3; dy++) {
                b.set(gx, y + dy, -135, "minecraft:iron_trapdoor[facing=south,half=top,open=false]");
                b.set(gx + 1, y + dy, -135, "minecraft:iron_trapdoor[facing=south,half=top,open=false]");
            }
        }
        b.fill(320, y, -156, 321, y + 1, -156, AIR);
        if (b.lvl >= 2) {
            b.set(320, y, -156, WOOD_DOOR + "[facing=north,half=lower,hinge=left]");
            b.set(320, y + 1, -156, WOOD_DOOR + "[facing=north,half=upper,hinge=left]");
        }
        b.gableZ(x0, z0, x1, z1, y + 8, SHIFER_ST, SHIFER, BRICK, 1);
        if (b.lvl >= 1) for (int z = -153; z <= -139; z += 3) {
            b.set(x0 + 1, y, z, WOOD_SLAB + "[type=top]");
            b.mark("bed", x0 + 2, y, z);
        }
        b.mark("work", 319, y, -137);
    }

    private static void obj3_workshop(S b) {
        int xn0 = 330, xn1 = 352, zn0 = -156, zn1 = -140;
        int yn = b.floor(xn0, zn0, xn1, zn1, CONC_D);
        b.fill(xn0, yn - 1, zn0, xn1, yn - 1, zn1, CONC_D);
        b.box(xn0, zn0, xn1, zn1, yn, 4, BRICK, CONC, 5, 1, null);
        b.fill(341, yn, -140, 342, yn + 1, -140, AIR);
        if (b.lvl >= 2) {
            b.set(341, yn, -140, WOOD_DOOR + "[facing=south,half=lower,hinge=left]");
            b.set(341, yn + 1, -140, WOOD_DOOR + "[facing=south,half=upper,hinge=left]");
        }
        b.flat(xn0, zn0, xn1, zn1, yn + 4, CONC_SLAB, BRICK_D);

        int xm0 = 330, xm1 = 352, zm0 = -139, zm1 = -127;
        int ym = b.floor(xm0, zm0, xm1, zm1, CONC_D);
        b.fill(xm0, ym - 1, zm0, xm1, ym - 1, zm1, CONC_D);
        b.box(xm0, zm0, xm1, zm1, ym, 6, BOARDS, BRICK, 4, 2, null);
        b.fill(338, ym, -127, 339, ym + 1, -127, AIR);
        b.fill(346, ym, -127, 347, ym + 1, -127, AIR);
        if (b.lvl >= 2) {
            for (int dx : new int[] {338, 346}) {
                b.set(dx, ym, -127, WOOD_DOOR + "[facing=south,half=lower,hinge=left]");
                b.set(dx, ym + 1, -127, WOOD_DOOR + "[facing=south,half=upper,hinge=left]");
            }
        }
        b.gableZ(xm0, zm0, xm1, zm1, ym + 6, RUST_SLAB + "[facing=east,half=bottom]", RUST, BOARDS, 1);
        if (b.lvl >= 1) for (int z = -137; z <= -130; z += 3) {
            b.set(xm0 + 1, ym, z, WOOD_SLAB + "[type=top]");
            b.mark("bed", xm0 + 2, ym, z);
        }
        b.mark("work", 340, ym, -134);

        int xs0 = 330, xs1 = 352, zs0 = -126, zs1 = -122;
        int ys = b.floor(xs0, zs0, xs1, zs1, CONC_D);
        b.fill(xs0, ys - 1, zs0, xs1, ys - 1, zs1, CONC_D);
        for (int x = xs0; x <= xs1; x += 4) {
            for (int y = ys; y <= ys + 3; y++) b.set(x, y, zs0, LOG);
            for (int y = ys; y <= ys + 3; y++) b.set(x, y, zs1, LOG);
        }
        b.flat(xs0, zs0, xs1, zs1, ys + 4, CORR_SLAB, null);
    }

    private static void obj4_workshop2(S b) {
        int x0 = 301, x1 = 311, z0 = -136, z1 = -114;
        int y = b.floor(x0, z0, x1, z1, CONC_D);
        b.fill(x0, y - 1, z0, x1, y - 1, z1, CONC_D);
        b.box(x0, z0, x1, z1, y, 5, BOARDS, BRICK, 3, 2, null);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (x == x0 || x == x1 || z == z0 || z == z1) b.wall(x, y, z, BRICK);
        b.fill(305, y, -114, 306, y + 1, -114, AIR);
        b.fill(305, y, -136, 306, y + 1, -136, AIR);
        if (b.lvl >= 2) {
            b.set(305, y, -114, WOOD_DOOR + "[facing=south,half=lower,hinge=left]");
            b.set(305, y + 1, -114, WOOD_DOOR + "[facing=south,half=upper,hinge=left]");
            b.set(305, y, -136, WOOD_DOOR + "[facing=north,half=lower,hinge=left]");
            b.set(305, y + 1, -136, WOOD_DOOR + "[facing=north,half=upper,hinge=left]");
        }
        b.gableZ(x0, z0, x1, z1, y + 5, SHIFER_ST, SHIFER, BOARDS, 1);
        if (b.lvl >= 1) for (int z = -132; z <= -118; z += 3) {
            b.set(x0 + 1, y, z, WOOD_SLAB + "[type=top]");
            b.mark("bed", x0 + 2, y, z);
        }
        b.mark("work", 308, y, -125);
    }

    private static void obj5_vehicles(S b) {
        B v = new B(b.s, 317, -124, 0, 9991L, b.lvl);
        Vehicles.vehicle(v, "zil", 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 316, b.ground(316, -125) + 1, -125);
    }

    private static void obj6_largeGarage(S b) {
        int x0 = 313, x1 = 352, z0 = -120, z1 = -96;
        int y = b.floor(x0, z0, x1, z1, CONC_D);
        b.fill(x0, y - 1, z0, x1, y - 1, z1, CONC_D);

        int zn0 = -120, zn1 = -113;
        b.box(x0, zn0, x1, zn1, y, 9, BRICK, CONC, 5, 2, null);
        for (int x = x0 + 3; x <= x1 - 3; x += 5) {
            b.window(x, y + 5, zn0);
            b.window(x, y + 5, zn1);
        }
        for (int z = zn0 + 3; z <= zn1 - 3; z += 5) {
            b.window(x0, y + 5, z);
            b.window(x1, y + 5, z);
        }
        b.gableX(x0, zn0, x1, zn1, y + 9, SHIFER_ST, SHIFER, BRICK, 1);

        int zs0 = -112, zs1 = -96;
        int ys = b.floor(x0, zs0, x1, zs1, CONC_D);
        ys = Math.max(ys, y);
        b.fill(x0, ys - 1, zs0, x1, ys - 1, zs1, CONC_D);
        b.box(x0, zs0, x1, zs1, ys, 6, BOARDS, BRICK, 0, 0, null);

        for (int gx : new int[] {320, 333, 345}) {
            b.fill(gx, ys, zs1, gx + 1, ys + 2, zs1, AIR);
            if (b.lvl >= 2) for (int dx = 0; dx <= 1; dx++)
                for (int dy = 0; dy < 3; dy++)
                    b.set(gx + dx, ys + dy, zs1, "minecraft:iron_trapdoor[facing=south,half=top,open=false]");
        }
        b.fill(330, ys, zs0, 331, ys + 1, zs0, AIR);
        b.fill(340, ys, zs0, 341, ys + 1, zs0, AIR);
        if (b.lvl >= 2) for (int dx : new int[] {330, 340}) {
            b.set(dx, ys, zs0, WOOD_DOOR + "[facing=north,half=lower,hinge=left]");
            b.set(dx, ys + 1, zs0, WOOD_DOOR + "[facing=north,half=upper,hinge=left]");
        }
        b.gableX(x0, zs0, x1, zs1, ys + 6, SHIFER_ST, SHIFER, BOARDS, 1);

        if (b.lvl >= 1) for (int z = zs0 + 1; z <= zs1 - 4; z += 4) {
            b.set(x0 + 1, ys, z, WOOD_SLAB + "[type=top]");
            b.mark("bed", x0 + 2, ys, z);
        }
        for (int gx : new int[] {318, 331, 343}) b.mark("work", gx, ys, zs1 + 1);
    }

    private static void obj7_tower(S b) {
        b.tower(294, -108, 296, -106, 'S');
    }

    private static void obj8_vehicles(S b) {
        B v = new B(b.s, 310, -108, 0, 9992L, b.lvl);
        Vehicles.vehicle(v, "kamaz", 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 311, b.ground(311, -109) + 1, -109);
    }

    private static void obj9_vehicles(S b) {
        B v1 = new B(b.s, 299, -93, 0, 9993L, b.lvl);
        Vehicles.vehicle(v1, "laz", 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 297, b.ground(297, -94) + 1, -94);
        B v2 = new B(b.s, 297, -91, 90, 9994L, b.lvl);
        Vehicles.vehicle(v2, "zil", 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 300, b.ground(300, -92) + 1, -92);
    }

    private static void fence(S b) {
        b.line(290, -158, 352, -158);
        b.line(354, -158, 354, -88);
        b.line(354, -88, 310, -88);
        b.line(298, -88, 290, -88);
        b.line(290, -88, 290, -158);
    }

    private static void camp(S b) {
        if (b.lvl < 1) return;
        int g = b.ground(305, -88);
        b.set(303, g + 1, -88, MCRATE);
        b.set(307, g + 1, -88, CRATE);
        b.set(305, g + 1, -89, "stalkerzone:rusty_barrel");
        b.mark("rest", 304, g + 1, -88);
        b.mark("rest", 306, g + 1, -88);
    }

    private static void patrol(S b) {
        int[][] route = {
            {292, -156}, {350, -156}, {350, -90}, {292, -90},
            {307, -88}
        };
        for (int[] p : route) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }
}
