package ru.stalkerzone.zonemap.build;

import java.util.List;

import ru.stalkerzone.zonemap.BlockSink;

/**
 * Дальний (северный) блокпост Кордона по геометрии оригинала (S.T.A.L.K.E.R. Anomaly, l01_escape).
 * Блокпост бандитов у выхода на Свалку: будки, мешки, вышка, ограждение — по обмерам.
 * Размеры, места и высоты каждого объекта сняты с вокселей уровня (tools/xray), а собираются объекты шаблонами
 * с ровными стенами, крышами, окнами и дверями — вместо «куч вокселей». Координаты — мировые (как в .szl).
 *
 * <p>Состав: главная будка (доски, шифер и красная кровля, двускатная крыша), металлическая вышка,
 * будка из профлиста с военной техникой у въезда, грузовик, скала, мешки на дороге, забор периметра.
 *
 * <p>Уровень развития (как в MineColonies): 0 — руины, 1 — лагерь, 2 — обжитой, 3 — укреплённый.
 */
public final class KordonFarPost {
    private KordonFarPost() {}

    /** Области, где воксели уровня заменяет этот сборщик: {x0, z0, x1, z1}. Все объекты с запасом 2–4 блока. */
    public static final List<int[]> CLEAR = List.of(new int[] {214, -830, 280, -750});

    static final String BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty",
            CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty",
            CONC_SLAB = "stalkerzone:zm_concrete_slab", CONC_OLD = "stalkerzone:zm_concrete_old",
            BOARDS = "stalkerzone:zm_planks_gray", BOARDS_O = "stalkerzone:zm_boards_old",
            SHIFER = "stalkerzone:zm_shifer", SHIFER_ST = "stalkerzone:zm_shifer_stairs",
            SHIFER_SLAB = "stalkerzone:zm_shifer_slab",
            RUST = "stalkerzone:zm_rust_sheet", RUST_ST = "stalkerzone:zm_rust_sheet_stairs",
            RUST_SLAB = "stalkerzone:zm_rust_sheet_slab",
            CORR = "stalkerzone:zm_corrugated", CORR_SLAB = "stalkerzone:zm_corrugated_slab",
            RED_ROOF = "stalkerzone:zm_roof_red", RED_ROOF_ST = "stalkerzone:zm_roof_red_stairs",
            RED_ROOF_SLAB = "stalkerzone:zm_roof_red_slab",
            BAGS = "stalkerzone:zm_sandbags", PO2 = "stalkerzone:concrete_fence",
            GLASS = "stalkerzone:zm_glass_dirty_pane", BARS = "minecraft:iron_bars",
            LOG = "minecraft:spruce_log", LADDER = "minecraft:ladder", AIR = "minecraft:air",
            GRAVEL = "minecraft:gravel", PATH = "minecraft:coarse_dirt",
            STONE = "minecraft:stone";

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

        /** Рабочее место (карточка 031): отмечается тем же проходом сборки, без второй постройки блоков. */
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

        /** Пол здания: средняя (медиана) высота земли в следе + 1; ниже — фундамент, выше — грунт срезается. */
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

        /** Двускатная крыша с коньком вдоль Z: скаты ступенями, фронтоны из материала стен. */
        void gableZ(int x0, int z0, int x1, int z1, int y, String roofSt, String roofFull, String gable, int overhang) {
            int w = x1 - x0 + 1, half = (w + 1) / 2;
            for (int i = 0; i < half; i++) {
                int yy = y + i;
                int xa = x0 - overhang + i, xb = x1 + overhang - i;
                if (xa > xb) break;
                for (int z = z0 - overhang; z <= z1 + overhang; z++) {
                    if (xb - xa == 0) {
                        wall(xa, yy, z, roofFull);
                    } else {
                        wall(xa, yy, z, roofSt + "[facing=west,half=bottom]");
                        wall(xb, yy, z, roofSt + "[facing=east,half=bottom]");
                    }
                }
                for (int x = xa + 1; x < xb; x++) { wall(x, yy, z0 - overhang, gable); wall(x, yy, z1 + overhang, gable); }
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

        /** Вальмовая крыша над прямоугольником: ярусы ступеней со всех сторон до конька/шпиля. */
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
    }

    /** Построить блокпост. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, Math.max(0, Math.min(3, lvl)));
        clearAndPave(b);
        mainShack(b);
        porch(b);
        metalPost(b);
        vehicle(b);
        rocks(b);
        fence(b);
        camp(b);
        patrol(b);
    }

    // ---------------------------------------------------------------- площадка

    private static void clearAndPave(S b) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int g = b.ground(x, z);
            for (int y = g + 1; y <= g + 16; y++) b.set(x, y, z, AIR);
        }
        for (int x = 229; x <= 246; x++) for (int z = -812; z <= -808; z++)
            b.set(x, b.ground(x, z), z, (hash(x, 0, z) % 7 == 0) ? GRAVEL : PATH);
    }

    // ---------------------------------------------------------------- главная будка

    /** Главная будка блокпоста: дощатые стены, шифер и красная кровля, двускатная крыша, проём ворот. */
    private static void mainShack(S b) {
        int y = b.floor(229, -825, 246, -814, CONC_D);
        b.fill(229, y - 1, -825, 246, y - 1, -814, CONC_D);
        b.box(229, -825, 246, -814, y, 4, BOARDS, LOG, 3, 1,
                new int[][] {{237, -814, 0}, {238, -814, 0}});
        for (int x = 229; x <= 246; x++) for (int z = -825; z <= -814; z++)
            if (x == 229 || x == 246 || z == -825 || z == -814) b.wall(x, y, z, CONC);
        b.fill(237, y, -814, 238, y + 1, -814, AIR);
        if (b.lvl >= 2) {
            b.set(237, y, -814, "minecraft:spruce_door[facing=south,half=lower,hinge=left]");
            b.set(237, y + 1, -814, "minecraft:spruce_door[facing=south,half=upper,hinge=left]");
            b.set(238, y, -814, "minecraft:spruce_door[facing=south,half=lower,hinge=right]");
            b.set(238, y + 1, -814, "minecraft:spruce_door[facing=south,half=upper,hinge=right]");
        }
        if (b.lvl >= 1) for (int z = -823; z <= -817; z += 3) {
            b.set(230, y, z, "minecraft:spruce_slab[type=top]"); b.set(245, y, z, "minecraft:spruce_slab[type=top]");
            b.mark("bed", 231, y, z); b.mark("bed", 244, y, z);
        }
        b.gableZ(229, -825, 235, -814, y + 4, SHIFER_ST, SHIFER, BOARDS, 1);
        b.gableZ(236, -825, 246, -814, y + 4, RED_ROOF_ST, RED_ROOF, BOARDS, 1);
        b.fill(235, y + 4, -826, 236, y + 4, -813, SHIFER_SLAB + "[type=bottom]");
    }

    // ---------------------------------------------------------------- крыльцо

    /** Крыльцо с южной стороны: дощатый навес на столбах (x229–233, z-813–-812). */
    private static void porch(S b) {
        int y = b.floor(229, -813, 233, -812, CONC_D);
        b.fill(229, y - 1, -813, 233, y - 1, -812, CONC_D);
        for (int x = 229; x <= 233; x++) {
            if (x == 229 || x == 233) {
                b.wall(x, y, -812, LOG);
                b.wall(x, y + 1, -812, LOG);
            }
            b.wall(x, y + 2, -812, BOARDS);
            b.set(x, y + 3, -812, SHIFER_SLAB + "[type=bottom]");
            b.wall(x, y + 2, -813, BOARDS);
            b.set(x, y + 3, -813, SHIFER_SLAB + "[type=bottom]");
        }
        b.fill(234, y + 3, -814, 237, y + 3, -811, SHIFER_SLAB + "[type=bottom]");
        if (b.lvl >= 1) {
            b.set(231, y, -812, "minecraft:spruce_slab[type=bottom]");
            b.set(232, y, -812, "minecraft:spruce_slab[type=bottom]");
            b.mark("rest", 231, y, -812);
            b.mark("rest", 232, y, -812);
        }
    }

    // ---------------------------------------------------------------- пост из профлиста

    /** Будка из профлиста с военной техникой у дороги (x228–239, z-809–-798). */
    private static void metalPost(S b) {
        int y = b.floor(228, -803, 233, -798, CONC_D);
        b.fill(228, y - 1, -803, 233, y - 1, -798, CONC_D);
        b.box(228, -803, 233, -798, y, 4, CORR, RUST, 0, 0,
                new int[][] {{231, -798, 1}});
        b.flat(228, -803, 233, -798, y + 4, CORR_SLAB, CORR);
        b.mark("guard", 230, y + 1, -800);
        if (b.lvl >= 2) {
            tower(b, 228, -803, 233, -798, 'S');
        }
        int idx = -1;
        for (int i = 0; i < Vehicles.ALL.length; i++) if (Vehicles.ALL[i].equals("btr")) idx = i;
        if (idx >= 0) {
            B v = new B(b.s, 236, -804, 90, (5151L * 2) & 0x7fffffffL, b.lvl);
            Vehicles.vehicle(v, Vehicles.ALL[idx], 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
            b.mark("work", 235, b.ground(235, -804) + 1, -804);
        }
        for (int x = 228; x <= 230; x++) {
            int g = b.ground(x, -806);
            b.set(x, g + 1, -806, BAGS);
            if (b.lvl >= 1) b.set(x, g + 2, -806, BAGS);
        }
        b.mark("gate", 229, b.ground(229, -806) + 1, -806);
    }

    // ---------------------------------------------------------------- вышка

    /** Металлическая вышка: четыре опоры, площадка на 7, ограждение, крыша; лестница с указанной стороны. */
    private static void tower(S b, int x0, int z0, int x1, int z1, char ladder) {
        int g = Integer.MAX_VALUE;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) g = Math.min(g, b.ground(x, z));
        int cx0 = x0, cz0 = z0, cx1 = Math.min(x1, x0 + 3), cz1 = Math.min(z1, z0 + 3);
        int top = g + 7;
        b.mark("guard", (cx0 + cx1) / 2, top + 1, (cz0 + cz1) / 2);
        for (int[] c : new int[][] {{cx0, cz0}, {cx1, cz0}, {cx0, cz1}, {cx1, cz1}})
            for (int yy = b.ground(c[0], c[1]) + 1; yy <= top + 3; yy++) b.set(c[0], yy, c[1], yy <= top ? "minecraft:iron_block" : "minecraft:iron_bars");
        for (int x = cx0 - 1; x <= cx1 + 1; x++) for (int z = cz0 - 1; z <= cz1 + 1; z++) {
            b.wall(x, top, z, RUST_SLAB + "[type=top]");
            boolean edge = x == cx0 - 1 || x == cx1 + 1 || z == cz0 - 1 || z == cz1 + 1;
            if (edge) b.wall(x, top + 1, z, b.lvl >= 3 ? BAGS : BARS);
            b.wall(x, top + 4, z, CORR_SLAB);
        }
        int lx = (cx0 + cx1) / 2, lz = (cz0 + cz1) / 2;
        int[] p = switch (ladder) {
            case 'W' -> new int[] {cx0 - 1, lz};
            case 'E' -> new int[] {cx1 + 1, lz};
            case 'N' -> new int[] {lx, cz0 - 1};
            default -> new int[] {lx, cz1 + 1};
        };
        String face = switch (ladder) { case 'W' -> "west"; case 'E' -> "east"; case 'N' -> "north"; default -> "south"; };
        for (int yy = b.ground(p[0], p[1]) + 1; yy <= top + 1; yy++) b.set(p[0], yy, p[1], LADDER + "[facing=" + face + "]");
        b.set(p[0], top, p[1], LADDER + "[facing=" + face + "]");
        if (b.lvl >= 2) b.set((cx0 + cx1) / 2, top + 3, (cz0 + cz1) / 2, "minecraft:lantern[hanging=true]");
    }

    // ---------------------------------------------------------------- техника

    /** Грузовик на дороге (x218–227, z-792–-783): ЗИЛ-130 бортовой. */
    private static void vehicle(S b) {
        int idx = -1;
        for (int i = 0; i < Vehicles.ALL.length; i++) if (Vehicles.ALL[i].equals("zil")) idx = i;
        if (idx < 0) return;
        B v = new B(b.s, 222, -788, 0, (5151L * 3) & 0x7fffffffL, b.lvl);
        Vehicles.vehicle(v, Vehicles.ALL[idx], 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 223, b.ground(223, -788) + 1, -788);
    }

    // ---------------------------------------------------------------- скалы

    /** Скальное образование у дороги (x269–276, z-763–-754): камень по обмерам. */
    private static void rocks(S b) {
        int y = b.floor(269, -763, 276, -754, GRAVEL);
        for (int x = 269; x <= 276; x++) for (int z = -763; z <= -754; z++) {
            int h = hash(x, 0, z) % 3 + 1;
            for (int yy = y; yy < y + h; yy++) b.wall(x, yy, z, STONE);
            if (hash(x, 1, z) % 7 == 0) b.wall(x, y + h, z, GRAVEL);
        }
    }

    // ---------------------------------------------------------------- забор

    /** Забор периметра: бетонные панели ПО-2, столбы через 3 с колючкой сверху на уровне 3. */
    private static void fence(S b) {
        line(b, 229, -829, 250, -829);
        line(b, 250, -829, 250, -808);
        line(b, 214, -808, 229, -808);
        line(b, 214, -829, 214, -808);
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

    // ---------------------------------------------------------------- лагерь

    /** Костёр и ящики у будки (уровень 1+). */
    private static void camp(S b) {
        if (b.lvl < 1) return;
        int g = b.ground(234, -819);
        b.set(233, g + 1, -819, "minecraft:spruce_slab[type=bottom]");
        b.set(235, g + 1, -819, "minecraft:spruce_slab[type=bottom]");
        b.set(234, g + 1, -818, "minecraft:spruce_slab[type=bottom]");
        b.set(233, b.ground(233, -817) + 1, -817, "stalkerzone:military_crate");
        b.set(234, b.ground(234, -817) + 1, -817, "stalkerzone:wooden_crate");
        b.mark("cook", 234, g + 1, -820);
        b.mark("rest", 233, g + 1, -819);
        b.mark("rest", 235, g + 1, -819);
    }

    // ---------------------------------------------------------------- патруль

    /** Карточка 031: маршрут патруля — обход внутри забора и выход на дорогу; порядок важен. */
    private static void patrol(S b) {
        int[][] route = {{231, -827}, {248, -827}, {248, -810}, {231, -810}, {219, -806}, {219, -798}};
        for (int[] p : route) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }
}
