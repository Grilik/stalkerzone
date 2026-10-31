package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Свиноферма Кордона по геометрии оригинала (S.T.A.L.K.E.R. Anomaly, l01_escape), карточка 038.
 * Два длинных корпуса свинарников (X 189..226, Z -442..-428 и -406..-392) с двускатными крышами вдоль длинной оси
 * (коньки на Z -435/-434 и -399/-398), ворота в торцах, окна под карнизом. У восточных торцов — подсобки-караулки
 * (X 227..231), со стороны двора — навесы над воротами корпусов, в корпусах нары и кормушки.
 * Двор между корпусами обнесён изгородью: два загона с кормушками, сено, телега, техника.
 * Размеры, места и высоты сняты с вокселей уровня (обмеры — docs/kordon_objects/10-farm.md), материалы — по таблице кодов.
 *
 * <p>Уровень развития (как в MineColonies): 0 — руины, 1 — лагерь, 2 — обжитой, 3 — укреплённый.
 */
public final class KordonFarm {
    private KordonFarm() {}

    /** Области, где воксели уровня заменяет этот сборщик: {x0, z0, x1, z1}. Все объекты с запасом 2–3 блока. */
    public static final List<int[]> CLEAR = List.of(new int[] {185, -445, 240, -389});

    static final String BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty",
            CONC_OLD = "stalkerzone:zm_concrete_old",
            BOARDS = "stalkerzone:zm_planks_gray", BOARDS_O = "stalkerzone:zm_boards_old",
            BOARDS_SLAB = "stalkerzone:zm_planks_gray_slab",
            FENCE = "stalkerzone:zm_fence_boards", POST = "stalkerzone:zm_wood_post",
            SHIFER_ST = "stalkerzone:zm_shifer_stairs",
            RUST = "stalkerzone:zm_rust_sheet",
            GLASS = "stalkerzone:zm_glass_dirty_pane", LOG = "minecraft:spruce_log",
            LADDER = "minecraft:ladder", AIR = "minecraft:air",
            HAY = "minecraft:hay_block[axis=y]", TUMP = "minecraft:water_cauldron",
            WHEEL = "stalkerzone:car_wheel[axis=x]", CRATE = "stalkerzone:wooden_crate",
            BARREL = "stalkerzone:prop_barrel", CAMPFIRE = "minecraft:campfire",
            PATH = "minecraft:coarse_dirt", GRAVEL = "minecraft:gravel";

    /** Профиль скатов корпуса по обмерам: подъём над карнизом для каждой из 13 полос крыши (конёк — две средние полосы). */
    private static final int[] ROOF_BARN = {0, 1, 1, 2, 3, 3, 4, 4, 3, 3, 2, 1, 0};
    /** Крыша подсобки: полосы вдоль корпуса, конёк двумя полосами посередине. */
    private static final int[] ROOF_ANNEX = {0, 1, 2, 2, 1, 0};
    /** Крыша навеса: профиль поперёк корпуса (7 полос). */
    private static final int[] ROOF_CANOPY = {0, 1, 2, 2, 2, 1, 0};

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

        /** Блок стены/крыши: на уровне 0 — пятая часть выбита, на 1 — изредка. */
        void wall(int x, int y, int z, String st) {
            int h = hash(x, y, z) % 100;
            if (lvl == 0 && h < 20) { s.set(x, y, z, AIR); return; }
            if (lvl == 1 && h < 3) { s.set(x, y, z, AIR); return; }
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

        /** Окно: стекло (на руинах пусто, на уровне 1 — через одно заколочено). */
        void window(int x, int y, int z) {
            String w = lvl == 0 ? AIR : lvl == 1 && (hash(x, y, z) & 1) == 0 ? BOARDS_O : GLASS;
            s.set(x, y, z, w);
        }

        /** Ворота: проём 2×3 в стене; с уровня 2 — створки из железных люков (facing — наружу). */
        void gate(int x, int y, int z, boolean alongX, String facing) {
            for (int i = 0; i < 2; i++) for (int dy = 0; dy < 3; dy++) {
                int xx = alongX ? x + i : x, zz = alongX ? z : z + i;
                s.set(xx, y + dy, zz, lvl >= 2
                        ? "minecraft:iron_trapdoor[facing=" + facing + ",half=top,open=false]" : AIR);
            }
        }

        /** Двускатная крыша ступенями вдоль X: профиль задаёт подъём над карнизом для полосы z0+i. */
        void roofZ(int x0, int x1, int z0, int z1, int y, int[] prof, String st, String full) {
            int n = z1 - z0, top = 0;
            for (int o : prof) top = Math.max(top, o);
            for (int i = 0; i <= n; i++) {
                int o = prof[Math.min(i, prof.length - 1)];
                for (int x = x0; x <= x1; x++)
                    wall(x, y + o, z0 + i, o >= top ? full
                            : st + "[facing=" + (i * 2 <= n ? "north" : "south") + ",half=bottom]");
            }
        }

        /** Крыша навеса ступенями вдоль Z: профиль задаёт подъём для полосы x0+i. */
        void roofX(int x0, int x1, int z0, int z1, int y, int[] prof, String st, String full) {
            int n = x1 - x0, top = 0;
            for (int o : prof) top = Math.max(top, o);
            for (int i = 0; i <= n; i++) {
                int o = prof[Math.min(i, prof.length - 1)];
                for (int z = z0; z <= z1; z++)
                    wall(x0 + i, y + o, z, o >= top ? full
                            : st + "[facing=" + (i * 2 <= n ? "west" : "east") + ",half=bottom]");
            }
        }

        /** Фронтон в торце: треугольник от карниза (y) до полосы крыши. */
        void gableEnd(int x, int z0, int z1, int[] prof, int y, String st) {
            for (int i = 0; i <= z1 - z0; i++) {
                int o = prof[Math.min(i, prof.length - 1)];
                for (int yy = y; yy < y + o; yy++) wall(x, yy, z0 + i, st);
            }
        }

        /** Кормушка: дощатый лоток 3×1, в середине пойло, по краям сено. */
        void trough(int x, int y, int z, boolean alongX) {
            for (int i = 0; i < 3; i++) {
                int xx = alongX ? x + i : x, zz = alongX ? z : z + i;
                set(xx, y, zz, BOARDS);
                set(xx, y + 1, zz, i == 1 ? TUMP : HAY);
            }
        }

        /** Изгородь: дощатый забор высотой h со столбами через 3 блока (на руинах — с проломами). */
        void fence(int xa, int za, int xb, int zb, int h) {
            int n = Math.max(Math.abs(xb - xa), Math.abs(zb - za));
            for (int i = 0; i <= n; i++) {
                int x = xa + Integer.signum(xb - xa) * i, z = za + Integer.signum(zb - za) * i;
                boolean post = i % 3 == 0;
                if (lvl == 0 && !post && hash(x, 4, z) % 4 == 0) continue;
                int g = ground(x, z);
                for (int y = g + 1; y <= g + h; y++) set(x, y, z, post ? POST : FENCE);
            }
        }
    }

    /** Построить свиноферму. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, Math.max(0, Math.min(3, lvl)));
        clearAndPave(b);
        barn(b, -442, -428, false);            // северный корпус, ворота со двора в южной стене
        barn(b, -406, -392, true);             // южный корпус, ворота со двора в северной стене
        annex(b, -438, -431);                  // подсобка-караулка у восточного торца северного корпуса
        annex(b, -402, -395);                  // подсобка у восточного торца южного корпуса
        canopy(b, -427, -423);                 // навес над воротами северного корпуса
        canopy(b, -410, -406);                 // навес над воротами южного корпуса
        yard(b);                               // изгородь двора, загоны, кормушки, сено, телега
        vehicles(b);                           // грузовик у въезда и трактор во дворе
        camp(b);                               // кухня у двора
        patrol(b);
    }

    // ---------------------------------------------------------------- площадка

    private static void clearAndPave(S b) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int g = b.ground(x, z);
            for (int y = g + 1; y <= g + 16; y++) b.set(x, y, z, AIR);
        }
        for (int x = 187; x <= 227; x++) for (int z = -428; z <= -405; z++) {          // двор и проход
            int g = b.ground(x, z);
            b.set(x, g, z, hash(x, 3, z) % 5 == 0 ? GRAVEL : PATH);
        }
        for (int x = 233; x <= 239; x++) for (int z = -443; z <= -391; z++) {          // подъезд с востока
            int g = b.ground(x, z);
            b.set(x, g, z, hash(x, 5, z) % 4 == 0 ? GRAVEL : PATH);
        }
    }

    // ---------------------------------------------------------------- корпуса

    /** Корпус свинарника: стены X 189..226, Z zn..zs; конёк крыши вдоль X, ворота в торцах, окна под карнизом. */
    private static void barn(S b, int zn, int zs, boolean doorNorth) {
        final int x0 = 189, x1 = 226, h = 3;
        int y = b.floor(x0, zn, x1, zs, CONC_OLD);
        b.fill(x0, y - 1, zn, x1, y - 1, zs, CONC_OLD);
        for (int x = x0; x <= x1; x++) {
            boolean corner = x == x0 || x == x1;
            for (int yy = y; yy < y + h; yy++) b.wall(x, yy, zs, corner ? BRICK : BOARDS);   // южная стена
            boolean open = x >= 209 && x <= 211 || x >= 218 && x <= 220 || !doorNorth && x >= 200 && x <= 202;
            b.wall(x, y, zn, corner ? BRICK : CONC_OLD);                                     // цоколь северной
            if (open) b.window(x, y + h - 1, zn);                                            // окно под карнизом
            else for (int yy = y + 1; yy < y + h; yy++)
                b.wall(x, yy, zn, corner ? BRICK : (x - x0) % 3 == 0 ? LOG : BOARDS);
        }
        int zc = (zn + zs) / 2;
        for (int z = zn + 1; z <= zs - 1; z++) {                                             // торцевые стены
            if (z == zc || z == zc - 1) continue;                                            // место ворот
            for (int yy = y; yy < y + h; yy++) { b.wall(x0, yy, z, BRICK_D); b.wall(x1, yy, z, BRICK_D); }
        }
        for (int z : new int[] {zn + 2, zs - 2}) { b.window(x0, y + h - 1, z); b.window(x1, y + h - 1, z); }
        b.gate(x0, y, zc - 1, false, "west");
        b.gate(x1, y, zc - 1, false, "east");
        b.gate(doorNorth ? 201 : 202, y, doorNorth ? zn : zs, true, doorNorth ? "north" : "south");
        b.roofZ(x0, x1, zn + 1, zs - 1, y + h, ROOF_BARN, SHIFER_ST, RUST);
        b.gableEnd(x0, zn + 1, zs - 1, ROOF_BARN, y + h, BRICK);
        b.gableEnd(x1, zn + 1, zs - 1, ROOF_BARN, y + h, BRICK);
        if (b.lvl >= 1) {
            for (int x : new int[] {194, 200, 212, 220}) {                                   // нары у южной стены
                b.set(x, y, zs - 1, BOARDS_SLAB + "[type=top]");
                b.mark("bed", x, y, zs - 2);
            }
            b.trough(196, y - 1, zn + 1, true);                                              // кормушка у северной
            b.mark("work", 196, y, zn + 3);
        }
    }

    /** Подсобка-караулка у восточного торца корпуса: X 227..231, Z zn..zs; нары, крыша ступенями, пост на коньке. */
    private static void annex(S b, int zn, int zs) {
        final int x0 = 227, x1 = 231, h = 3;
        int y = b.floor(x0, zn, x1, zs, CONC_OLD);
        b.fill(x0, y - 1, zn, x1, y - 1, zs, CONC_OLD);
        for (int x = x0; x <= x1; x++) for (int z = zn; z <= zs; z++) {
            if (x != x0 && x != x1 && z != zn && z != zs) continue;
            boolean corner = (x == x0 || x == x1) && (z == zn || z == zs);
            for (int yy = y; yy < y + h; yy++) b.wall(x, yy, z, corner ? BRICK : BOARDS);
        }
        int zc = (zn + zs) / 2;
        b.fill(x1, y, zc, x1, y + 1, zc, AIR);                                     // вход с востока
        if (b.lvl >= 2) {
            b.set(x1, y, zc, "minecraft:spruce_door[facing=east,half=lower,hinge=left]");
            b.set(x1, y + 1, zc, "minecraft:spruce_door[facing=east,half=upper,hinge=left]");
        }
        b.window(229, y + h - 1, zn);
        b.roofZ(x0, x1, zn + 1, zs - 1, y + h, ROOF_ANNEX, SHIFER_ST, RUST);
        b.gableEnd(x0, zn + 1, zs - 1, ROOF_ANNEX, y + h, BOARDS);
        b.gableEnd(x1, zn + 1, zs - 1, ROOF_ANNEX, y + h, BOARDS);
        for (int yy = b.ground(x1 + 1, zn + 4) + 1; yy <= y + h + 2; yy++)          // лестница на крышу
            b.set(x1 + 1, yy, zn + 4, LADDER + "[facing=west]");
        b.mark("guard", (x0 + x1) / 2, y + h + 3, zn + 4);                          // часовой на коньке
        if (b.lvl >= 1) {
            b.set(x0 + 1, y, zs - 1, BOARDS_SLAB + "[type=top]");
            b.mark("bed", x0 + 1, y, zs - 2);
            b.set(x1 - 1, y, zn + 2, CRATE);
        }
    }

    // ---------------------------------------------------------------- навесы

    /** Навес над воротами корпуса со стороны двора: стойки X 200 и 206, крыша ступенями поперёк (профиль X). */
    private static void canopy(S b, int z0, int z1) {
        final int x0 = 200, x1 = 206;
        int y = b.floor(x0, z0, x1, z1, CONC_OLD);
        b.fill(x0, y - 1, z0, x1, y - 1, z1, CONC_OLD);
        for (int x : new int[] {x0, x1}) for (int z : new int[] {z0, z1})
            for (int yy = y; yy < y + 3; yy++) b.wall(x, yy, z, POST);
        b.roofX(x0, x1, z0, z1, y + 3, ROOF_CANOPY, SHIFER_ST, RUST);
        if (b.lvl >= 2) b.set((x0 + x1) / 2, y + 4, (z0 + z1) / 2, "minecraft:lantern[hanging=true]");
    }

    // ---------------------------------------------------------------- двор и загоны

    /** Двор: изгородь с въездом, два загона с кормушками, скирды и телега. На руинах (0) — только изгородь. */
    private static void yard(S b) {
        b.fence(188, -428, 188, -418, 2);                       // западная изгородь двора
        b.fence(188, -415, 188, -405, 2);
        b.fence(228, -428, 228, -418, 2);                       // восточная: въезд на ферму
        b.fence(228, -415, 228, -405, 2);
        if (b.lvl < 1) return;
        for (int[] p : new int[][] {{189, 198}, {208, 216}}) {   // загоны вдоль прохода
            b.fence(p[0], -427, p[1], -427, 2);
            b.fence(p[0], -407, p[1], -407, 2);
        }
        b.fence(198, -426, 198, -418, 2); b.fence(198, -415, 198, -407, 2);   // ворота загона — в проход
        b.fence(208, -426, 208, -418, 2); b.fence(208, -415, 208, -407, 2);
        b.fence(216, -426, 216, -407, 2);
        for (int x : new int[] {192, 210}) {                    // кормушки и сено в загонах
            int g = b.ground(x, -421);
            b.trough(x, g, -421, true);
            b.mark("work", x, g + 1, -419);
            int hz = b.ground(x + 3, -413) + 1;
            b.set(x + 3, hz, -413, HAY);
            b.set(x + 3, hz + 1, -413, HAY);
            b.set(x + 4, hz, -413, HAY);
        }
        for (int[] hp : new int[][] {{218, -426}, {219, -425}, {220, -425}}) {   // скирда у въезда
            int g = b.ground(hp[0], hp[1]) + 1;
            b.set(hp[0], g, hp[1], HAY);
            b.set(hp[0], g + 1, hp[1], HAY);
        }
        b.set(221, b.ground(221, -426) + 1, -426, BARREL);
        cart(b, 219, -423);
    }

    /** Телега во дворе: колёса, дощатый кузов, оглобли и сено. */
    private static void cart(S b, int x, int z) {
        int g = b.ground(x, z) + 1;
        for (int dx : new int[] {0, 3}) for (int dz = 0; dz <= 1; dz++) b.set(x + dx, g, z + dz, WHEEL);
        b.fill(x, g + 1, z, x + 3, g + 1, z + 1, BOARDS);
        b.fill(x, g + 2, z, x, g + 2, z + 1, BOARDS);
        b.fill(x, g + 2, z + 1, x + 3, g + 2, z + 1, BOARDS);
        b.set(x + 1, g + 2, z, HAY);
        b.set(x + 2, g + 2, z, HAY);
        for (int dx : new int[] {4, 5}) b.set(x + dx, g + 1, z, "minecraft:spruce_fence");
    }

    /** Кухня у двора: костёр, лавки, ящик и бочка; рабочие места повара и отдыха. */
    private static void camp(S b) {
        if (b.lvl < 1) return;
        int g = b.ground(221, -411) + 1;
        b.set(221, g, -411, CAMPFIRE);
        b.set(220, b.ground(220, -412) + 1, -412, BOARDS_SLAB + "[type=bottom]");
        b.set(222, b.ground(222, -412) + 1, -412, BOARDS_SLAB + "[type=bottom]");
        b.set(222, b.ground(222, -410) + 1, -410, CRATE);
        if (b.lvl >= 2) b.set(220, b.ground(220, -410) + 1, -410, BARREL);
        b.mark("cook", 221, g, -410);
        b.mark("rest", 220, b.ground(220, -412) + 1, -412);
        b.mark("rest", 222, b.ground(222, -412) + 1, -412);
    }

    /** Техника фермы по обмерам (материалы «техника»): грузовик у въезда и трактор во дворе. */
    private static void vehicles(S b) {
        B v1 = new B(b.s, 234, -423, 0, 3801L, b.lvl);
        Vehicles.vehicle(v1, "zil", 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 233, b.ground(233, -420) + 1, -420);
        B v2 = new B(b.s, 226, -413, 270, 3802L, b.lvl);
        Vehicles.vehicle(v2, "tractor", 0, 0, 0, null, b.lvl == 0 ? 2 : 0);
        b.mark("work", 224, b.ground(224, -417) + 1, -417);
    }

    /** Маршрут обхода фермы: загоны, въезд и выход к воротам; порядок важен (карточка 031). */
    private static void patrol(S b) {
        int[][] route = {{191, -424}, {191, -414}, {203, -416}, {214, -424}, {225, -424}, {227, -417}};
        for (int[] p : route) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }
}
