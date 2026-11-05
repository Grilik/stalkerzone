package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Деревня новичков Кордона по обмерам оригинала ({@code docs/kordon_objects/00-village.md}, Anomaly l01_escape) — П-03.
 *
 * <p>Семь деревянных изб на местах оригинала: след, высота стен (4 блока: на 3 крыша казалась больше дома — плейтест 06.11), тип и направление крыши
 * (конёк по обмерам, ската 45° дают те же 7–9 блоков), материалы стен и кровли. Плюс два остова техники (объекты 5 и 9),
 * два подвала у костра (укрытия от выброса, как в оригинале), дощатый забор «люками» по краю плато с воротами
 * на дороги и проходом к бункеру, тропы от костра к дверям.
 *
 * <p>Назначение домов (предложение штаба, П-03): 2 — штаб Волка (самый большой, у костра), 3 и 8 — казармы,
 * 1 — медпункт (ближний к костру), 4 — склад (у бункера), 7 — мастерская, 6 — дом новичков (у места появления).
 *
 * <p>Деревня стоит на плато площадки village: пол изб — на земле плато, место NPC — на плато + 1. Внутри высота
 * 4 блока до балок, двери проходимы, свет — фонари под балками.
 *
 * <p>Уровень развития (как в MineColonies): 0 — обжитые руины (окна пустые, дыры в стенах и крышах, без дверей
 * и света), 1 — залатанная (дыры забиты досками, двери, свет), 2 — обжитая (стёкла, больше утвари),
 * 3 — укреплённая (мешки у ворот, решётки на окнах штаба).
 */
public final class KordonVillage {
    private KordonVillage() {}

    /** Область сборщика — плато деревни без западного края x −40 (там выход выемки бункера, его границы). */
    public static final List<int[]> CLEAR = List.of(new int[] {-39, -24, 56, 34});

    /** Костёр (точка campfire узла; сам костёр ставит декор узла). */
    public static final int FIRE_X = 2, FIRE_Z = -1;

    static final String PLANKS = "stalkerzone:zm_planks_gray", BOARDS = "stalkerzone:zm_boards_old",
            BOARDS_V = "stalkerzone:zm_boards_vert", PAINTED = "stalkerzone:zm_wood_painted",
            PLASTER = "stalkerzone:zm_plaster", PLASTER_O = "stalkerzone:zm_plaster_old",
            BRICK = "stalkerzone:zm_brick_red", BRICK_D = "stalkerzone:zm_brick_dirty",
            CONC_O = "stalkerzone:zm_concrete_old", CONC = "stalkerzone:zm_concrete",
            POST = "stalkerzone:zm_wood_post", POST_FENCE = "stalkerzone:zm_wood_post_fence",
            FLOOR = "stalkerzone:zm_planks_floor", RUST = "stalkerzone:zm_rust_sheet",
            GLASS = "stalkerzone:zm_glass_dirty_pane", LOG = "minecraft:spruce_log[axis=x]", LOG_Z = "minecraft:spruce_log[axis=z]",
            FENCE_TD = "stalkerzone:zm_fence_boards_trapdoor", GARDEN = "stalkerzone:zm_fence_boards_fence",
            DOOR = "stalkerzone:zm_door_green_door", BARS = "minecraft:iron_bars", LIGHT = "minecraft:lantern[hanging=true]",
            LAMP = "minecraft:lantern[hanging=false]", BAGS = "stalkerzone:zm_sandbags",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate", MED = "stalkerzone:medical_crate",
            AMMO = "stalkerzone:prop_ammo_crates", CAB = "stalkerzone:prop_el_cabinet", RADIO = "stalkerzone:prop_radio",
            TV = "stalkerzone:prop_tv", BARREL = "minecraft:barrel[facing=up]", STOVE = "minecraft:furnace",
            TABLE = "minecraft:spruce_slab[type=top]", CHAIR = "minecraft:spruce_stairs", SHELF = "minecraft:spruce_stairs",
            PATH = "minecraft:dirt_path", COARSE = "minecraft:coarse_dirt", GRAVEL = "minecraft:gravel",
            DIRT = "minecraft:dirt", GRASS = "minecraft:grass_block", STEPS = "stalkerzone:zm_planks_gray_stairs",
            AIR = "minecraft:air";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня развития. */
    static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = Math.max(0, Math.min(3, lvl)); }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

        /** Блок стены/кровли: руины (0) — пятая часть выбита; залатанная (1) — дыры забиты старыми досками. */
        void wall(int x, int y, int z, String st) {
            int h = hash(x, y, z) % 100;
            if (lvl == 0 && h < 20) { s.set(x, y, z, AIR); return; }
            if (lvl == 1 && h < 5 && !st.startsWith("framedblocks:")) { s.set(x, y, z, st.contains("_stairs") ? BOARDS + "_stairs" + st.substring(st.indexOf('[')) : BOARDS); return; }
            s.set(x, y, z, st);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.set(x, y, z, st);
        }

        void lamp(int x, int y, int z) { if (lvl >= 1) set(x, y, z, LIGHT); }
    }

    /** Изба: след, стены, кровля, двери (x, z, вдоль X: 1/0), роль. */
    record House(int n, String role, int x0, int z0, int x1, int z1, int wallH, char roof, String wall, String corner,
                 String plinth, String roofMat, int[][] doors) {}

    /** Конёк: 'X' — вдоль X (скаты на север и юг), 'Z' — вдоль Z, 'H' — вальма. Размеры — по обмерам 00-village.md. */
    static final House[] HOUSES = {
            new House(1, "медпункт", -17, -13, -4, -7, 4, 'H', PLANKS, POST, CONC_O, PLANKS, new int[][] {{-10, -7, 1}}),
            new House(2, "штаб Волка", 11, -13, 25, -3, 4, 'X', PLANKS, POST, CONC_O, "stalkerzone:zm_shifer", new int[][] {{18, -3, 1}, {11, -8, 0}}),
            new House(3, "казарма", 32, -15, 47, -6, 4, 'X', PLASTER, CONC, CONC, PLANKS, new int[][] {{39, -6, 1}}),
            new House(4, "склад", -34, 9, -23, 17, 4, 'X', PLANKS, POST, CONC_O, PLANKS, new int[][] {{-23, 13, 0}}),
            new House(6, "дом новичков", 3, 13, 15, 24, 4, 'H', BRICK, BRICK_D, CONC_O, PLANKS, new int[][] {{9, 13, 1}}),
            new House(7, "мастерская", -16, 14, -6, 28, 4, 'Z', PLANKS, POST, CONC_O, PLANKS, new int[][] {{-11, 14, 1}, {-6, 21, 0}, {-6, 22, 0}}),
            new House(8, "казарма", 27, 15, 40, 25, 4, 'X', PAINTED, PLASTER, PLASTER, "stalkerzone:zm_shifer", new int[][] {{33, 15, 1}}),
    };

    /**
     * Пробник FramedBlocks (П-05): изба, у которой кровля — framed-скаты с текстурой кровли, а на окнах снаружи —
     * наличники (доски на стену) и подоконники. Без FramedBlocks ставится прежний вид: ступени и плиты.
     */
    static boolean framed(House h) { return h.n == 2; }

    /** Наличники: крашеные доски вокруг окна снаружи. */
    static final String TRIM = "stalkerzone:zm_wood_painted";

    /** Подвалы у костра: вход (x, z) и направление спуска по X (−1 — на запад, +1 — на восток). */
    public static final int[][] CELLARS = {{-4, 4, -1}, {9, 4, 1}};

    /** Ворота в заборе: {сторона (N, S, W, E), от, до} — на дороги и к бункеру. */
    static final int[][] GATES = {{'N', 0, 3}, {'S', 20, 23}, {'E', 3, 6}, {'W', -5, 1}};

    /** Построить деревню. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, lvl);
        paths(b);
        for (House h : HOUSES) house(b, h);
        for (int[] c : CELLARS) cellar(b, c[0], c[1], c[2]);
        wrecks(b);
        poles(b);
        fence(b);
        camp(b);
        patrol(b);
    }

    // ---------------------------------------------------------------- избы

    private static void house(S b, House h) {
        int g = floor(b, h);
        int y = g + 1, top = y + h.wallH;                 // стены y..top-1, кровля с top
        // стены, углы, цоколь, окна
        for (int x = h.x0; x <= h.x1; x++) for (int z = h.z0; z <= h.z1; z++) {
            boolean edge = x == h.x0 || x == h.x1 || z == h.z0 || z == h.z1;
            boolean corner = (x == h.x0 || x == h.x1) && (z == h.z0 || z == h.z1);
            b.set(x, g, z, edge ? h.plinth : FLOOR);
            for (int yy = y; yy < top; yy++) {
                if (!edge) { b.set(x, yy, z, AIR); continue; }
                String m = corner ? h.corner : yy == y && !h.plinth.equals(h.wall) ? h.plinth : h.wall;
                if (yy == y) b.set(x, yy, z, m); else b.wall(x, yy, z, m);   // нижний венец целый: без лазов в стенах
            }
        }
        for (int x = h.x0 + 2; x <= h.x1 - 2; x += 3) { window(b, h, x, y + 1, h.z0); window(b, h, x, y + 1, h.z1); }
        for (int z = h.z0 + 2; z <= h.z1 - 2; z += 3) { window(b, h, h.x0, y + 1, z); window(b, h, h.x1, y + 1, z); }
        for (int[] d : h.doors) door(b, d[0], y, d[1], d[2] == 1, h);
        // балки под кровлей (на них фонари) и кровля
        if (h.roof == 'Z') for (int z = h.z0 + 2; z <= h.z1 - 2; z += 4) for (int x = h.x0 + 1; x < h.x1; x++) b.set(x, top, z, LOG);
        else for (int x = h.x0 + 2; x <= h.x1 - 2; x += 4) for (int z = h.z0 + 1; z < h.z1; z++) b.set(x, top, z, LOG_Z);
        String st = framed(h) ? "framed:" + h.roofMat : h.roofMat + "_stairs", full = h.roofMat;
        // первый ряд кровли — карнизный свес вровень с верхним венцом стен, следующий ложится на стену
        switch (h.roof) {
            case 'X' -> gableX(b, h, top - 1, st, full);
            case 'Z' -> gableZ(b, h, top - 1, st, full);
            default -> hip(b, h, top - 1, st, full);
        }
        if (h.n == 3) porch(b, h, y);
        if (h.n == 6) for (int x = 2; x <= 16; x++) if (x < 8 || x > 10) b.set(x, b.ground(x, 11) + 1, 11, GARDEN);
        interior(b, h, y, top);
    }

    /** Пол избы: земля под следом; бугры срезаются, ямы засыпаются (на плато всё ровно). */
    static int floor(S b, House h) {
        int n = (h.x1 - h.x0 + 1) * (h.z1 - h.z0 + 1), i = 0;
        int[] gs = new int[n];
        for (int x = h.x0; x <= h.x1; x++) for (int z = h.z0; z <= h.z1; z++) gs[i++] = b.ground(x, z);
        java.util.Arrays.sort(gs);
        int g = gs[n / 2];
        for (int x = h.x0 - 1; x <= h.x1 + 1; x++) for (int z = h.z0 - 1; z <= h.z1 + 1; z++) {
            int gl = b.ground(x, z);
            for (int yy = gl + 1; yy < g; yy++) b.set(x, yy, z, DIRT);
            for (int yy = g + 1; yy <= gl; yy++) b.set(x, yy, z, AIR);
        }
        return g;
    }

    static void window(S b, House h, int x, int y, int z) {
        for (int[] d : h.doors) if (Math.abs(d[0] - x) + Math.abs(d[1] - z) <= 1) return;
        String w = b.lvl == 0 ? AIR : b.lvl == 1 && (hash(x, y, z) & 1) == 0 ? BOARDS
                : b.lvl >= 3 && h.n == 2 ? BARS : GLASS;
        b.set(x, y, z, w);
        if (b.lvl >= 2) b.set(x, y + 1, z, w.equals(GLASS) ? GLASS : h.wall);
        if (framed(h) && b.lvl >= 1) trim(b, h, x, y, z, b.lvl >= 2 ? 2 : 1);
    }

    /** Наличники и подоконник окна высотой hgt снаружи стены (FramedBlocks: доски на стену и полплиты; без мода — подоконник-плита). */
    static void trim(S b, House h, int x, int y, int z, int hgt) {
        int ox = x == h.x0 ? -1 : x == h.x1 ? 1 : 0, oz = z == h.z0 ? -1 : z == h.z1 ? 1 : 0;
        String toWall = ox < 0 ? "east" : ox > 0 ? "west" : oz < 0 ? "south" : "north";
        int ax = oz != 0 ? 1 : 0, az = ox != 0 ? 1 : 0;            // вдоль стены
        int cx = x + ox, cz = z + oz;                               // клетка снаружи окна
        for (int k = 0; k < hgt; k++) {
            b.set(cx - ax, y + k, cz - az, Framed.of(Framed.Shape.WALL_BOARD, TRIM, toWall, false));
            b.set(cx + ax, y + k, cz + az, Framed.of(Framed.Shape.WALL_BOARD, TRIM, toWall, false));
        }
        b.set(cx, y + hgt, cz, Framed.of(Framed.Shape.WALL_BOARD, TRIM, toWall, false));
        if (Framed.loaded) b.set(cx, y - 1, cz, Framed.of(Framed.Shape.SLAB_EDGE, TRIM, toWall, true));   // без мода — без подоконника, как было
    }

    /** Дверь 1×2 (на руинах — пустой проём), над ней — стена. */
    static void door(S b, int x, int y, int z, boolean alongX, House h) {
        String f = alongX ? (z == h.z0 ? "north" : "south") : (x == h.x0 ? "west" : "east");
        if (b.lvl >= 1) {
            b.set(x, y, z, DOOR + "[facing=" + f + ",hinge=left,open=false,half=lower]");
            b.set(x, y + 1, z, DOOR + "[facing=" + f + ",hinge=left,open=false,half=upper]");
        } else {
            b.set(x, y, z, AIR);
            b.set(x, y + 1, z, AIR);
        }
        // крыльцо: ступень-плита снаружи
        int ox = alongX ? x : x + (x == h.x0 ? -1 : 1), oz = alongX ? z + (z == h.z0 ? -1 : 1) : z;
        b.set(ox, y - 1, oz, PLANKS);
    }

    /** Скат кровли: высокая сторона — facing. st — «материал_stairs» или «framed:материал» для пробника FramedBlocks. */
    static String slope(String st, String facing) {
        if (st.startsWith("framed:")) return Framed.slope(st.substring(7), facing, false);
        return st + "[facing=" + facing + ",half=bottom]";
    }

    /** Двускатная кровля, конёк вдоль X: скаты на север и юг, фронтоны на западе и востоке. */
    static void gableX(S b, House h, int y, String st, String full) {
        int half = (h.z1 - h.z0 + 2) / 2;
        for (int i = 0; i <= half; i++) {
            int yy = y + i, za = h.z0 - 1 + i, zb = h.z1 + 1 - i;
            if (za > zb) break;
            for (int x = h.x0 - 1; x <= h.x1 + 1; x++) {
                if (za == zb) { b.wall(x, yy, za, full); continue; }
                b.wall(x, yy, za, slope(st, "south"));
                b.wall(x, yy, zb, slope(st, "north"));
            }
            for (int z = za + 1; z < zb; z++) { b.wall(h.x0, yy, z, h.wall); b.wall(h.x1, yy, z, h.wall); }
        }
    }

    /** Двускатная кровля, конёк вдоль Z: скаты на запад и восток, фронтоны на севере и юге. */
    static void gableZ(S b, House h, int y, String st, String full) {
        int half = (h.x1 - h.x0 + 2) / 2;
        for (int i = 0; i <= half; i++) {
            int yy = y + i, xa = h.x0 - 1 + i, xb = h.x1 + 1 - i;
            if (xa > xb) break;
            for (int z = h.z0 - 1; z <= h.z1 + 1; z++) {
                if (xa == xb) { b.wall(xa, yy, z, h.n == 7 ? "stalkerzone:zm_rust" : full); continue; }
                b.wall(xa, yy, z, slope(st, "east"));
                b.wall(xb, yy, z, slope(st, "west"));
            }
            for (int x = xa + 1; x < xb; x++) { b.wall(x, yy, h.z0, h.wall); b.wall(x, yy, h.z1, h.wall); }
        }
    }

    /** Вальмовая кровля: ступени со всех четырёх сторон до конька. */
    static void hip(S b, House h, int y, String st, String full) {
        int a = h.x0 - 1, c = h.x1 + 1, d = h.z0 - 1, e = h.z1 + 1, yy = y;
        while (a <= c && d <= e) {
            if (c - a <= 1 || e - d <= 1) {
                for (int x = a; x <= c; x++) for (int z = d; z <= e; z++) b.wall(x, yy, z, full);
                break;
            }
            for (int x = a; x <= c; x++) {
                b.wall(x, yy, d, slope(st, "south"));
                b.wall(x, yy, e, slope(st, "north"));
            }
            for (int z = d + 1; z < e; z++) {
                b.wall(a, yy, z, slope(st, "east"));
                b.wall(c, yy, z, slope(st, "west"));
            }
            a++; c--; d++; e--; yy++;
        }
    }

    /** Крыльцо-веранда казармы 3 (обмер: x 37..42, z −5..−3, высота 3): дощатый настил, столбы, навес. */
    static void porch(S b, House h, int y) {
        for (int x = 37; x <= 42; x++) for (int z = -5; z <= -3; z++) {
            b.set(x, y - 1, z, PLANKS);
            boolean post = (x == 37 || x == 42) && z == -3;
            if (post) for (int yy = y; yy <= y + 2; yy++) b.set(x, yy, z, POST_FENCE);
            if (z > -5) b.wall(x, y + 3, z, RUST + "_slab[type=bottom]");   // навес под карнизом (z −5 — карниз казармы)
        }
    }

    // ---------------------------------------------------------------- обстановка и рабочие места

    private static void interior(S b, House h, int y, int top) {
        // фонари под балками
        if (h.roof == 'Z') for (int z = h.z0 + 2; z <= h.z1 - 2; z += 8) b.lamp((h.x0 + h.x1) / 2, top - 1, z);
        else for (int x = h.x0 + 2; x <= h.x1 - 2; x += 8) b.lamp(x, top - 1, (h.z0 + h.z1) / 2);
        boolean f = b.lvl >= 1;
        switch (h.n) {
            case 2 -> {          // штаб: стол с рацией, стулья, шкаф, ящики; старший — за столом
                int tx = 18, tz = -8;
                for (int x = tx - 1; x <= tx + 1; x++) b.set(x, y, tz, f ? TABLE : AIR);
                if (b.lvl >= 2) b.set(tx, y + 1, tz, RADIO + "[facing=south]");
                b.set(tx - 1, y, tz + 1, f ? CHAIR + "[facing=north]" : AIR);
                b.set(tx + 1, y, tz + 1, f ? CHAIR + "[facing=north]" : AIR);
                b.set(h.x0 + 1, y, h.z0 + 1, f ? TV + "[facing=south]" : AIR);
                b.set(h.x0 + 2, y, h.z0 + 1, f ? CAB + "[facing=south]" : WCRATE);
                b.set(h.x0 + 3, y, h.z0 + 1, CRATE);
                bedRow(b, h, h.x1 - 3, h.x1 - 1, h.z0 + 1, y, 'N');   // койки старшего и заместителя
                b.mark("duty", tx, y, tz - 1);
                b.mark("work", tx + 3, y, tz);
            }
            case 3, 8 -> {       // казармы: койки вдоль длинных стен, печка, ящики
                bedRow(b, h, h.x0 + 1, h.x1 - 1, h.z0 + 1, y, 'N');
                bedRow(b, h, h.x0 + 1, h.x1 - 1, h.z1 - 1, y, 'S');
                b.set(h.x1 - 1, y, (h.z0 + h.z1) / 2, f ? STOVE + "[facing=west]" : AIR);
                b.set(h.x0 + 1, y, (h.z0 + h.z1) / 2, WCRATE);
                b.mark("rest", h.x1 - 2, y, (h.z0 + h.z1) / 2);
            }
            case 1 -> {          // медпункт: койки, аптечки, шкаф; медик у стола
                bedRow(b, h, h.x0 + 1, h.x0 + 6, h.z0 + 1, y, 'N');
                b.set(h.x1 - 1, y, h.z0 + 1, MED);
                b.set(h.x1 - 1, y, h.z0 + 2, MED);
                b.set(h.x1 - 2, y, h.z0 + 1, f ? CAB + "[facing=south]" : AIR);
                b.set(h.x1 - 3, y, h.z0 + 1, f ? "minecraft:brewing_stand" : AIR);
                b.set(h.x1 - 1, y, h.z1 - 1, f ? TABLE : AIR);
                b.mark("work", h.x1 - 2, y, h.z1 - 1);
            }
            case 4 -> {          // склад: штабеля ящиков и бочек вдоль стен, проход посередине
                for (int x = h.x0 + 1; x <= h.x1 - 1; x++) {
                    String c = (x & 1) == 0 ? CRATE : WCRATE;
                    b.set(x, y, h.z0 + 1, c);
                    if (b.lvl >= 2 || (x % 3) == 0) b.set(x, y + 1, h.z0 + 1, (x & 1) == 0 ? WCRATE : CRATE);
                    b.set(x, y, h.z1 - 1, x % 3 == 0 ? BARREL : AMMO + "[facing=north]");
                }
                b.mark("work", h.x1 - 2, y, (h.z0 + h.z1) / 2);
            }
            case 7 -> {          // мастерская: верстаки, наковальня, точило, бочки; ворота на восток
                b.set(h.x0 + 1, y, h.z0 + 2, f ? "minecraft:crafting_table" : WCRATE);
                b.set(h.x0 + 1, y, h.z0 + 3, f ? "minecraft:smithing_table" : AIR);
                b.set(h.x0 + 1, y, h.z0 + 5, f ? "minecraft:anvil[facing=east]" : AIR);
                b.set(h.x0 + 1, y, h.z0 + 7, f ? "minecraft:grindstone[face=floor,facing=east]" : AIR);
                b.set(h.x0 + 1, y, h.z1 - 1, BARREL);
                b.set(h.x0 + 2, y, h.z1 - 1, BARREL);
                b.set(h.x1 - 1, y, h.z1 - 1, CRATE);
                b.mark("work", h.x0 + 2, y, h.z0 + 3);
                b.mark("work", h.x0 + 2, y, h.z0 + 6);
            }
            case 6 -> {          // дом новичков: нары, стол, лавки
                bedRow(b, h, h.x0 + 1, h.x1 - 1, h.z1 - 1, y, 'S');
                int tx = (h.x0 + h.x1) / 2, tz = h.z0 + 4;
                for (int x = tx - 1; x <= tx + 1; x++) b.set(x, y, tz, f ? TABLE : AIR);
                b.set(tx - 1, y, tz - 1, f ? CHAIR + "[facing=south]" : AIR);
                b.set(tx + 1, y, tz + 1, f ? CHAIR + "[facing=north]" : AIR);
                b.set(h.x0 + 1, y, h.z0 + 1, WCRATE);
                b.mark("rest", tx, y, tz - 1);
                b.mark("rest", tx, y, tz + 1);
            }
            default -> { }
        }
    }

    /** Ряд коек у стены (wall: 'N' — у северной, изголовье к стене); перед каждой — место «bed» на полу. */
    static void bedRow(S b, House h, int xa, int xb, int z, int y, char wall) {
        int dz = wall == 'N' ? 1 : -1;
        String face = wall == 'N' ? "north" : "south";
        rows:
        for (int x = xa; x <= xb; x += 2) {
            for (int[] d : h.doors) if (Math.abs(d[0] - x) <= 1 && Math.abs(d[1] - z) <= 2) continue rows;   // не перед дверью
            if (b.lvl >= 1) {
                b.set(x, y, z, "minecraft:brown_bed[facing=" + face + ",part=head]");
                b.set(x, y, z + dz, "minecraft:brown_bed[facing=" + face + ",part=foot]");
            } else {
                b.set(x, y, z, "stalkerzone:zm_mattress_slab[type=bottom]");
            }
            b.mark("bed", x, y, z + 2 * dz);
        }
    }

    // ---------------------------------------------------------------- подвалы у костра

    /**
     * Подвал-погреб: над входом дощатая будка, 5 ступеней вниз, комната 6 × 5 высотой 3 под землёй (кирпич, дощатый пол,
     * фонарь, нары и ящики). Укрытие от выброса: места «shelter» на полу.
     *
     * <p>П-11: верх первой ступени — вровень с площадкой входа (с плато заходишь без прыжка, обратно — тоже
     * по полуступеням), над каждой ступенью 3 блока воздуха, проём в комнату — на ровном полу после последней ступени.
     */
    static void cellar(S b, int ex, int ez, int dir) {
        int g = b.ground(ex, ez);
        int F = g - 5;                                    // пол подвала (блок), стоят на F + 1, потолок F + 4 = g − 1
        String step = STEPS + "[facing=" + (dir < 0 ? "east" : "west") + ",half=bottom]";
        // k = 0 — площадка входа (пол g, как плато); k = 1..5 — ступени на высоте g − k + 1 (верх первой = g + 1);
        // k = 6 — проём в комнату на полу F
        for (int k = 0; k <= 6; k++) {
            int x = ex + dir * k;
            int floor = k == 0 ? g : k <= 5 ? g - k + 1 : F;          // блок, на котором стоят
            int ceil = k == 6 ? F + 4 : floor + 4;                       // над ступенью — 3 блока воздуха
            for (int z = ez - 1; z <= ez + 1; z++) {
                if (z != ez) {                                           // стенки: кирпич под землёй, доски будки над ней
                    for (int yy = F; yy < ceil; yy++) b.set(x, yy, z, yy > g ? BOARDS_V : BRICK_D);
                    b.set(x, ceil, z, ceil > g ? PLANKS + "_slab[type=bottom]" : PLANKS);
                    continue;
                }
                b.set(x, floor, z, k == 0 ? PLANKS : k <= 5 ? step : FLOOR);
                for (int yy = F; yy < floor; yy++) b.set(x, yy, z, BRICK_D);
                for (int yy = floor + 1; yy < ceil; yy++) b.set(x, yy, z, AIR);
                b.set(x, ceil, z, ceil > g ? PLANKS + "_slab[type=bottom]" : PLANKS);   // крыша будки — плитами, ниже земли — доски
            }
        }
        // комната за проёмом
        int rx0 = dir < 0 ? ex - 12 : ex + 7, rx1 = rx0 + 5, rz0 = ez - 2, rz1 = ez + 2;
        for (int x = rx0 - 1; x <= rx1 + 1; x++) for (int z = rz0 - 1; z <= rz1 + 1; z++) {
            boolean edge = x == rx0 - 1 || x == rx1 + 1 || z == rz0 - 1 || z == rz1 + 1;
            if (x == ex + dir * 6 && z == ez) continue;                  // проём
            b.set(x, F, z, edge ? BRICK_D : FLOOR);
            b.set(x, F + 4, z, PLANKS);
            for (int yy = F + 1; yy <= F + 3; yy++) b.set(x, yy, z, edge ? BRICK_D : AIR);
        }
        int far = dir < 0 ? rx0 : rx1;
        if (b.lvl >= 1) {
            for (int z = rz0; z <= rz1; z += 2) b.set(far, F + 1, z, "stalkerzone:zm_mattress_slab[type=bottom]");
            b.set((rx0 + rx1) / 2, F + 3, ez, LIGHT);
        }
        b.set(far, F + 1, rz0 + 1, WCRATE);
        b.set((rx0 + rx1) / 2, F + 1, rz0, BARREL);
        b.set((rx0 + rx1) / 2 + 1, F + 1, rz0, CRATE);
        int mid = (rx0 + rx1) / 2;
        b.mark("shelter", mid, F + 1, ez);
        b.mark("shelter", mid - 1, F + 1, rz1);
        b.mark("shelter", mid + 1, F + 1, rz1);
    }

    // ---------------------------------------------------------------- техника, столбы

    private static void wrecks(S b) {
        // объект 5: ржавый ЗИЛ у восточного края; объект 9: сгоревший «Москвич»
        Vehicles.vehicle(new B(b.s, 48, 11, 0, 4801L, b.lvl), "zil", 0, 0, 0, null, 1);
        Vehicles.vehicle(new B(b.s, 47, 20, 90, 4702L, b.lvl), "moskvich", 0, 0, 0, null, 2);
    }

    /** Деревянные столбы электропередачи (высокие узкие объекты обмеров у домов 4, 5 и 6). */
    private static void poles(S b) {
        for (int[] p : new int[][] {{-20, 7}, {15, 10}, {45, 8}}) {
            int g = b.ground(p[0], p[1]);
            for (int yy = g + 1; yy <= g + 9; yy++) b.set(p[0], yy, p[1], POST_FENCE);
            b.set(p[0], g + 9, p[1], POST);
            b.set(p[0] - 1, g + 9, p[1], POST_FENCE);
            b.set(p[0] + 1, g + 9, p[1], POST_FENCE);
        }
    }

    // ---------------------------------------------------------------- тропы

    private static void paths(S b) {
        int fx = FIRE_X, fz = FIRE_Z;
        path(b, fx, -2, -39, -2);                  // к бункеру (выемка за краем плато)
        path(b, -10, -2, -10, -6);                 // медпункт
        path(b, fx, -2, 18, -2);                   // штаб
        path(b, 18, -2, 39, -2); path(b, 39, -2, 39, -5);   // казарма 3
        path(b, fx, -2, fx, -24);                  // северные ворота (к свалке техники)
        path(b, -3, -1, -3, 10); path(b, -3, 10, -11, 10); path(b, -11, 10, -11, 13);   // мастерская
        path(b, -3, 10, -22, 10); path(b, -22, 10, -22, 13);                              // склад
        path(b, 6, -1, 6, 8); path(b, 6, 8, 9, 8); path(b, 9, 8, 9, 12);                   // дом новичков
        path(b, 9, 8, 33, 8); path(b, 33, 8, 33, 14);                                     // казарма 8
        path(b, 21, 8, 21, 34);                    // южные ворота (к дороге на юг)
        path(b, 18, -2, 22, -2); path(b, 22, -2, 22, 4); path(b, 22, 4, 56, 4);           // восточные ворота
    }

    /** Тропа шириной 2 по прямой (по X или по Z): утоптанная земля с пятнами гравия. */
    static void path(S b, int xa, int za, int xb, int zb) {
        int n = Math.max(Math.abs(xb - xa), Math.abs(zb - za));
        for (int i = 0; i <= n; i++) {
            int x = xa + Integer.signum(xb - xa) * i, z = za + Integer.signum(zb - za) * i;
            for (int w = 0; w <= 1; w++) {
                int px = xa == xb ? x + w : x, pz = xa == xb ? z : z + w;
                int[] c = CLEAR.get(0);
                if (px < c[0] || px > c[2] || pz < c[1] || pz > c[3]) continue;
                int h = hash(px, 3, pz) % 10;
                b.set(px, b.ground(px, pz), pz, h < 6 ? PATH : h < 9 ? COARSE : GRAVEL);
            }
        }
    }

    // ---------------------------------------------------------------- забор и ворота

    /** Дощатый забор «люками» (неоткрываемый) по краю плато, столбы через 4, проёмы ворот. */
    private static void fence(S b) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) { fenceAt(b, x, c[1], 'N'); fenceAt(b, x, c[3], 'S'); }
        for (int z = c[1] + 1; z < c[3]; z++) { fenceAt(b, c[0], z, 'W'); fenceAt(b, c[2], z, 'E'); }
        if (b.lvl >= 3) for (int[] gt : GATES) {   // мешки по сторонам ворот
            for (int t : new int[] {gt[1] - 1, gt[2] + 1}) {
                int x = gt[0] == 'N' || gt[0] == 'S' ? t : gt[0] == 'W' ? c[0] + 1 : c[2] - 1;
                int z = gt[0] == 'N' ? c[1] + 1 : gt[0] == 'S' ? c[3] - 1 : t;
                b.set(x, b.ground(x, z) + 1, z, BAGS);
            }
        }
    }

    static boolean gate(char side, int t) {
        for (int[] gt : GATES) if (gt[0] == side && t >= gt[1] && t <= gt[2]) return true;
        return false;
    }

    static void fenceAt(S b, int x, int z, char side) {
        int t = side == 'N' || side == 'S' ? x : z;
        if (gate(side, t)) return;
        int g = b.ground(x, z);
        boolean post = Math.floorMod(t, 4) == 0 || gate(side, t - 1) || gate(side, t + 1);
        if (post) { b.set(x, g + 1, z, POST); b.set(x, g + 2, z, POST_FENCE); return; }
        if (b.lvl == 0 && hash(x, 9, z) % 5 == 0) return;              // руины: пролёты выбиты
        String f = switch (side) { case 'N' -> "north"; case 'S' -> "south"; case 'W' -> "west"; default -> "east"; };
        String td = FENCE_TD + "[facing=" + f + ",half=bottom,open=true]";
        b.set(x, g + 1, z, td);
        b.set(x, g + 2, z, td);
    }

    // ---------------------------------------------------------------- костёр, часовые, патруль

    private static void camp(S b) {
        int fx = FIRE_X, fz = FIRE_Z;
        b.mark("cook", fx, b.ground(fx, fz - 2) + 1, fz - 2);
        for (int[] d : new int[][] {{-2, 0}, {2, 0}, {0, 2}, {-2, 2}, {2, 2}})
            b.mark("rest", fx + d[0], b.ground(fx + d[0], fz + d[1]) + 1, fz + d[1]);
        // часовые — у ворот, изнутри
        for (int[] p : new int[][] {{-1, -22}, {24, 32}, {54, 7}, {-36, 3}})
            b.mark("guard", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }

    /** Обход вдоль забора изнутри; порядок важен. */
    private static void patrol(S b) {
        int[][] route = {{-37, -21}, {0, -21}, {53, -21}, {53, 8}, {53, 31}, {21, 31}, {-37, 31}, {-37, 3}};
        for (int[] p : route) b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }
}
