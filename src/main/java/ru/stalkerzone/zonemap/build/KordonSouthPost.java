package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Южный блокпост Кордона по геометрии оригинала (S.T.A.L.K.E.R. Anomaly, l01_escape), этап 1 плана после плейтеста.
 * Размеры, места и высоты каждого объекта сняты с вокселей уровня (tools/xray), а собираются объекты шаблонами
 * с ровными стенами, крышами, окнами и дверями — вместо «куч вокселей». Координаты — мировые (как в .szl).
 *
 * <p>Состав: здание КПП (кирпич, двускатная крыша) с двухэтажной кирпичной частью, бетонная будка у ворот,
 * казарма (дощатая на бетонном цоколе, шифер, тамбур), три металлические вышки, бетонный забор периметра,
 * через дорогу — двухэтажный дом с вальмовой крышей, гараж и сетчатый вольер; на дороге мешки, блоки, шлагбаум; БТР.
 *
 * <p>Уровень развития (как в MineColonies): 0 — руины, 1 — лагерь, 2 — обжитой, 3 — укреплённый.
 */
public final class KordonSouthPost {
    private KordonSouthPost() {}

    /** Области, где воксели уровня заменяет этот сборщик: {x0, z0, x1, z1}. Включает выход патруля на дорогу (z 195). */
    public static final List<int[]> CLEAR = List.of(new int[] {28, 195, 107, 262});

    static final String BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty",
            BRICK_ST = "stalkerzone:zm_brick_red_stairs", CONC = "stalkerzone:zm_concrete", CONC_D = "stalkerzone:zm_concrete_dirty",
            CONC_SLAB = "stalkerzone:zm_concrete_slab", PLASTER = "stalkerzone:zm_plaster", PLASTER_O = "stalkerzone:zm_plaster_old",
            BOARDS = "stalkerzone:zm_planks_gray", BOARDS_O = "stalkerzone:zm_boards_old",
            SHIFER = "stalkerzone:zm_shifer", SHIFER_ST = "stalkerzone:zm_shifer_stairs", SHIFER_SLAB = "stalkerzone:zm_shifer_slab",
            RUST = "stalkerzone:zm_rust_sheet", RUST_ST = "stalkerzone:zm_rust_sheet_stairs", RUST_SLAB = "stalkerzone:zm_rust_sheet_slab",
            CORR = "stalkerzone:zm_corrugated", CORR_SLAB = "stalkerzone:zm_corrugated_slab",
            BAGS = "stalkerzone:zm_sandbags", PO2 = "stalkerzone:concrete_fence",
            GLASS = "stalkerzone:zm_glass_dirty_pane", BARS = "minecraft:iron_bars", LOG = "minecraft:spruce_log",
            LADDER = "minecraft:ladder", AIR = "minecraft:air", GRAVEL = "minecraft:gravel", PATH = "minecraft:coarse_dirt";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня: на руинах часть блоков стен и крыш выбита. */
    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = lvl; }

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
                for (int y = top + 1; y <= g; y++) s.set(x, y, z, AIR);          // срезать бугор под зданием
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
                    if (xa == xb) { wall(xa, yy, z, roofFull); continue; }
                    wall(xa, yy, z, roofSt + "[facing=east,half=bottom]");
                    wall(xb, yy, z, roofSt + "[facing=west,half=bottom]");
                }
                // фронтоны
                for (int x = xa + 1; x < xb; x++) { wall(x, yy, z0, gable); wall(x, yy, z1, gable); }
                if (xb - xa == 1) for (int z = z0 - overhang; z <= z1 + overhang; z++) { /* конёк из двух ступеней */ }
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

        /** Плоская крыша с парапетом (на уровне 3 — мешки на крыше). */
        void flat(int x0, int z0, int x1, int z1, int y, String roof, String parapet) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                wall(x, y, z, roof);
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge && parapet != null) wall(x, y + 1, z, lvl >= 3 ? BAGS : parapet);
            }
        }
    }

    /** Построить блокпост. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, Math.max(0, Math.min(3, lvl)));
        clearAndPave(b);
        westHouse(b);
        roadBlock(b);
        kppHouse(b);
        barracks(b);
        tower(b, 97, 214, 100, 218, 'W');
        tower(b, 65, 232, 68, 235, 'E');
        tower(b, 65, 252, 69, 257, 'N');
        fence(b);
        btr(b);
        camp(b);
        patrol(b);
    }

    // ---------------------------------------------------------------- площадка

    private static void clearAndPave(S b) {
        int[] c = CLEAR.get(0);
        // всё, что выше земли, — воздух (на случай, если воксели уже стоят)
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int g = b.ground(x, z);
            for (int y = g + 1; y <= g + 16; y++) b.set(x, y, z, AIR);
        }
        // бетонированный двор у здания КПП и дорожка к казарме
        for (int x = 76; x <= 83; x++) for (int z = 217; z <= 226; z++) b.set(x, b.ground(x, z), z, (hash(x, 0, z) % 9 == 0) ? GRAVEL : CONC);
        for (int z = 219; z <= 253; z++) for (int x = 82; x <= 83; x++) b.set(x, b.ground(x, z), z, (hash(x, 1, z) % 5 == 0) ? GRAVEL : PATH);
    }

    // ---------------------------------------------------------------- через дорогу

    /** Двухэтажный дом с вальмовой крышей (x39–50, z207–218), гараж и сетчатый вольер. */
    private static void westHouse(S b) {
        int y = b.floor(39, 207, 50, 218, CONC_D);
        b.fill(39, y - 1, 207, 50, y - 1, 218, CONC_D);
        b.box(39, 207, 50, 218, y, 4, PLASTER_O, CONC_D, 3, 1, new int[][] {{50, 212, 0}, {44, 207, 1}});
        b.fill(40, y + 3, 208, 49, y + 3, 217, BOARDS_O);           // перекрытие
        b.box(39, 207, 50, 218, y + 4, 4, PLASTER_O, CONC_D, 3, 1, null);
        b.fill(40, y + 7, 208, 49, y + 7, 217, BOARDS_O);
        b.hip(39, 207, 50, 218, y + 8, SHIFER_ST, SHIFER);
        b.fill(41, y, 216, 41, y + 6, 216, LADDER + "[facing=north]");   // лестница на второй этаж
        // гараж (ржавое железо, плоская крыша)
        int g = b.floor(30, 211, 33, 217, CONC_D);
        b.box(30, 211, 33, 217, g, 3, RUST, RUST, 0, 0, null);
        b.fill(33, g, 213, 33, g + 1, 215, AIR);                       // ворота к дому
        b.flat(30, 211, 33, 217, g + 3, CORR_SLAB, null);
        b.mark("work", 34, b.ground(34, 214) + 1, 214);                // карточка 031: место у гаража через дорогу
        // сетчатый вольер
        int k = b.floor(31, 219, 36, 223, GRAVEL);
        for (int x = 31; x <= 36; x++) for (int z = 219; z <= 223; z++)
            if (x == 31 || x == 36 || z == 219 || z == 223) for (int yy = k; yy < k + 3; yy++) b.wall(x, yy, z, (x == 31 || x == 36) && (z == 219 || z == 223) ? LOG : BARS);
    }

    /** Мешки с песком поперёк дороги, проезд со шлагбаумом, бетонные блоки у здания КПП. */
    private static void roadBlock(S b) {
        for (int x = 50; x <= 62; x++) {
            if (x >= 54 && x <= 59) continue;                         // проезд
            int g = b.ground(x, 204);
            b.set(x, g + 1, 204, BAGS);
            if (b.lvl >= 1) b.set(x, g + 2, 204, BAGS + (b.lvl >= 3 ? "" : ""));
        }
        for (int z = 205; z <= 207; z++) { int g = b.ground(51, z); b.set(51, g + 1, z, BAGS); }
        // шлагбаум
        int g = b.ground(53, 206);
        b.fill(53, g + 1, 206, 53, g + 2, 206, "minecraft:polished_andesite");
        if (b.lvl >= 1) for (int x = 54; x <= 60; x++) b.set(x, g + 2, 206, (x & 1) == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
        else b.set(54, b.ground(54, 207) + 1, 207, "minecraft:red_concrete");
        // бетонные блоки (ФБС) у здания
        for (int x = 62; x <= 64; x++) for (int z = 209; z <= 213; z++) {
            int gg = b.ground(x, z);
            b.set(x, gg + 1, z, CONC);
            if ((x + z) % 2 == 0) b.set(x, gg + 2, z, CONC_D);
        }
        // карточка 031: караул у проезда в мешках
        b.mark("gate", 55, b.ground(55, 207) + 1, 207);
        b.mark("gate", 61, b.ground(61, 205) + 1, 205);
    }

    // ---------------------------------------------------------------- здание КПП

    /** Кирпичное здание КПП (x63–75, z208–223, двускатная крыша вдоль Z) и двухэтажная часть (x70–76, z211–218). */
    private static void kppHouse(S b) {
        int y = b.floor(63, 208, 75, 223, CONC_D);
        b.fill(63, y - 1, 208, 75, y - 1, 223, CONC_D);
        b.box(63, 208, 69, 223, y, 4, BRICK, BRICK_D, 3, 1, new int[][] {{63, 215, 0}, {66, 223, 1}});
        b.fill(64, y + 4, 209, 68, y + 4, 222, AIR);
        b.gableZ(63, 208, 69, 223, y + 4, RUST_ST, RUST, BRICK, 1);
        // двухэтажная кирпичная часть с плоской крышей и парапетом
        b.box(70, 208, 76, 223, y, 4, BRICK, BRICK_D, 3, 1, new int[][] {{73, 223, 1}});
        b.box(70, 211, 76, 218, y + 4, 5, BRICK, BRICK_D, 3, 2, null);
        b.fill(71, y + 4, 209, 75, y + 4, 210, CONC_SLAB);              // козырёк над первым этажом
        b.fill(71, y + 4, 219, 75, y + 4, 222, CONC_SLAB);
        b.fill(71, y + 3, 212, 75, y + 3, 217, CONC);                    // перекрытие
        b.set(71, y + 3, 213, AIR); b.fill(71, y, 213, 71, y + 3, 213, LADDER + "[facing=east]");
        b.flat(70, 211, 76, 218, y + 9, CONC, BRICK_D);
        b.mark("duty", 73, y, 215);                                      // карточка 031: дежурный внутри здания, 1-й этаж
        // проход между частями здания
        for (int z = 214; z <= 215; z++) b.fill(69, y, z, 70, y + 1, z, AIR);
        // бетонная будка у ворот периметра
        int k = b.floor(77, 211, 80, 216, CONC_D);
        b.box(77, 211, 80, 216, k, 3, CONC, CONC_D, 2, 1, new int[][] {{79, 216, 1}});
        b.flat(77, 211, 80, 216, k + 3, CONC_SLAB, null);
    }

    // ---------------------------------------------------------------- казарма

    /** Казарма (x84–99, z230–250): бетонный цоколь, дощатые стены, шиферная двускатная крыша; тамбур с севера. */
    private static void barracks(S b) {
        int y = b.floor(84, 225, 99, 250, CONC_D);
        b.fill(84, y - 1, 230, 99, y - 1, 250, CONC_D);
        b.box(84, 230, 99, 250, y, 4, BOARDS, LOG, 3, 1, new int[][] {{91, 250, 1}});
        b.fill(84, y, 230, 99, y, 250, AIR);
        for (int x = 84; x <= 99; x++) for (int z = 230; z <= 250; z++)
            if (x == 84 || x == 99 || z == 230 || z == 250) b.wall(x, y, z, CONC);   // цоколь
        b.fill(91, y, 230, 92, y + 1, 230, AIR);                          // проход в тамбур
        b.gableZ(84, 230, 99, 250, y + 4, SHIFER_ST, SHIFER, BOARDS, 1);
        // нары вдоль стен
        if (b.lvl >= 1) for (int z = 232; z <= 248; z += 3) {
            b.set(85, y, z, "minecraft:spruce_slab[type=top]"); b.set(98, y, z, "minecraft:spruce_slab[type=top]");
            b.mark("bed", 86, y, z); b.mark("bed", 97, y, z);            // карточка 031: спальные места у нар
        }
        // тамбур
        b.fill(86, y - 1, 225, 97, y - 1, 229, CONC_D);
        b.box(86, 225, 97, 229, y, 4, BOARDS, LOG, 3, 1, null);
        b.fill(91, y, 225, 92, y + 1, 225, AIR);
        if (b.lvl >= 2) {
            b.set(91, y, 225, "minecraft:spruce_door[facing=north,half=lower,hinge=left]");
            b.set(91, y + 1, 225, "minecraft:spruce_door[facing=north,half=upper,hinge=left]");
            b.set(92, y, 225, "minecraft:spruce_door[facing=north,half=lower,hinge=right]");
            b.set(92, y + 1, 225, "minecraft:spruce_door[facing=north,half=upper,hinge=right]");
        }
        b.gableZ(86, 225, 97, 229, y + 4, SHIFER_ST, SHIFER, BOARDS, 0);
    }

    // ---------------------------------------------------------------- вышки

    /** Металлическая вышка: четыре опоры, площадка на 7, ограждение, крыша; лестница с указанной стороны. */
    private static void tower(S b, int x0, int z0, int x1, int z1, char ladder) {
        int g = Integer.MAX_VALUE;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) g = Math.min(g, b.ground(x, z));
        int cx0 = x0, cz0 = z0, cx1 = Math.min(x1, x0 + 3), cz1 = Math.min(z1, z0 + 3);
        int top = g + 7;
        b.mark("guard", (cx0 + cx1) / 2, top + 1, (cz0 + cz1) / 2);   // карточка 031: пост часового — центр площадки
        for (int[] c : new int[][] {{cx0, cz0}, {cx1, cz0}, {cx0, cz1}, {cx1, cz1}})
            for (int y = b.ground(c[0], c[1]) + 1; y <= top + 3; y++) b.set(c[0], y, c[1], y <= top ? "minecraft:iron_block" : "minecraft:iron_bars");
        for (int x = cx0 - 1; x <= cx1 + 1; x++) for (int z = cz0 - 1; z <= cz1 + 1; z++) {
            b.wall(x, top, z, RUST_SLAB + "[type=top]");
            boolean edge = x == cx0 - 1 || x == cx1 + 1 || z == cz0 - 1 || z == cz1 + 1;
            if (edge) b.wall(x, top + 1, z, b.lvl >= 3 ? BAGS : BARS);
            b.wall(x, top + 4, z, CORR_SLAB);
        }
        // лестница по средней грани
        int lx = (cx0 + cx1) / 2, lz = (cz0 + cz1) / 2;
        int[] p = switch (ladder) {
            case 'W' -> new int[] {cx0 - 1, lz};
            case 'E' -> new int[] {cx1 + 1, lz};
            case 'N' -> new int[] {lx, cz0 - 1};
            default -> new int[] {lx, cz1 + 1};
        };
        String face = switch (ladder) { case 'W' -> "west"; case 'E' -> "east"; case 'N' -> "north"; default -> "south"; };
        for (int y = b.ground(p[0], p[1]) + 1; y <= top + 1; y++) b.set(p[0], y, p[1], LADDER + "[facing=" + face + "]");
        b.set(p[0], top, p[1], LADDER + "[facing=" + face + "]");
        if (b.lvl >= 2) b.set((cx0 + cx1) / 2, top + 3, (cz0 + cz1) / 2, "minecraft:lantern[hanging=true]");
    }

    // ---------------------------------------------------------------- забор

    /** Бетонный забор периметра (ПО-2): панели высотой 3, столбы через 3 с колючкой сверху на уровне 3. */
    private static void fence(S b) {
        line(b, 80, 211, 105, 211);   // север (от будки)
        line(b, 105, 211, 105, 259);  // восток
        line(b, 65, 259, 105, 259);   // юг
        line(b, 65, 224, 65, 231);    // запад: от здания КПП до вышки
        line(b, 65, 236, 65, 251);    //        между вышками
    }

    private static void line(S b, int xa, int za, int xb, int zb) {
        int n = Math.max(Math.abs(xb - xa), Math.abs(zb - za));
        for (int i = 0; i <= n; i++) {
            int x = xa + Integer.signum(xb - xa) * i, z = za + Integer.signum(zb - za) * i;
            int g = b.ground(x, z);
            boolean post = i % 3 == 0;
            if (b.lvl == 0 && !post && hash(x, 7, z) % 5 == 0) continue;   // проломы
            for (int y = g + 1; y <= g + 3; y++) b.set(x, y, z, post ? CONC : PO2);
            if (post) b.set(x, g + 4, z, b.lvl >= 3 ? BARS : CONC_SLAB);
            else if (b.lvl >= 3) b.set(x, g + 4, z, BARS);
        }
    }

    // ---------------------------------------------------------------- техника и лагерь

    private static void btr(S b) {
        int idx = -1;
        for (int i = 0; i < Vehicles.ALL.length; i++) if (Vehicles.ALL[i].equals("btr")) idx = i;
        if (idx < 0) return;
        B v = new B(b.s, 74, 242, 90, 5151L, b.lvl);
        Vehicles.vehicle(v, Vehicles.ALL[idx], 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 72, b.ground(72, 242) + 1, 242);                  // карточка 031: место у БТР
    }

    /** Лавки и ящики во дворе у казармы (уровень 1+). */
    private static void camp(S b) {
        if (b.lvl < 1) return;
        // костёр ставит лагерь узла (точка campfire), здесь — лавки и ящики вокруг
        int g = b.ground(78, 240);
        b.set(76, g + 1, 240, "minecraft:spruce_slab[type=bottom]");
        b.set(80, g + 1, 240, "minecraft:spruce_slab[type=bottom]");
        b.set(78, b.ground(78, 237) + 1, 237, "stalkerzone:military_crate");
        b.set(79, b.ground(79, 237) + 1, 237, "stalkerzone:wooden_crate");
        // карточка 031: повар у костра и лавки
        b.mark("cook", 77, b.ground(77, 241) + 1, 241);
        b.mark("rest", 76, g + 1, 240);
        b.mark("rest", 80, g + 1, 240);
    }

    /** Карточка 031: маршрут патруля — обход внутри забора по углам, затем выход на дорогу; порядок важен. */
    private static void patrol(S b) {
        int[][] route = {{68, 213}, {102, 213}, {102, 256}, {68, 256}, {57, 195}, {57, 262}};
        for (int[] p : route) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }
}
