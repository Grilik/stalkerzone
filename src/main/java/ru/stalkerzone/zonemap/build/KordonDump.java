package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Свалка техники А6 и застава одиночек О12 (чаша x −60..−10, z −145..−95), по вокселям оригинала l01_escape (Anomaly),
 * см. {@code docs/kordon_objects/13-dump-o12.md}.
 *
 * <p>Воксели оригинала на свалке редкие (≈ 1 700 в чаше): остовы техники высотой до 3 (материал «техника» 214 + ржавый
 * металл 209), низкие кучи ржавого листа, бетонная полоса (203) вдоль z −111..−110, груда досок на западе и дощатый
 * барак с тентом на восточном краю (x −15..−5, z −101..−93, высота до 4) — это и есть О12. Сборщик ставит то же:
 * остовы по следам обмера, кучи, бетонную полосу, бочки и шины на дне чаши, барак заставы с нарами, костёр,
 * вышку и мешки. Рельеф чаши и плато дна — дело площадки (узел 20, у «Программиста»): сборщик берёт землю у {@code ground}.
 *
 * <p>У свалки уровней развития нет — это аномальная зона, она одинакова всегда и просто детализирована. Уровень
 * (0–3) касается только заставы О12: 0 — брошенная стоянка (выбитые окна, без двери и света, костёр потушен),
 * 1 — застава (барак обжит), 2 — пост (мешки), 3 — укрепление (вышка и колючка по краю).
 */
public final class KordonDump {
    private KordonDump() {}

    /** Область сборщика: чаша свалки и барак О12 (воксели оригинала внутри заменяются). */
    public static final List<int[]> CLEAR = List.of(new int[] {-74, -140, -3, -90});

    static final String PLANKS = "stalkerzone:zm_planks_gray", FLOOR = "stalkerzone:zm_planks_floor", RUST = "stalkerzone:zm_rust_sheet",
            CONC = "stalkerzone:zm_concrete", CONC_O = "stalkerzone:zm_concrete_old", BAGS = "stalkerzone:zm_sandbags",
            POST = "stalkerzone:zm_wood_post_fence", GLASS = "stalkerzone:zm_glass_dirty_pane", DOOR = "stalkerzone:zm_door_green_door",
            BARREL = "stalkerzone:rusty_barrel", RAD = "stalkerzone:radioactive_barrel", TIRES = "stalkerzone:tires",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate", LIGHT = "minecraft:lantern[hanging=true]",
            MATTRESS = "stalkerzone:zm_mattress_slab[type=bottom]", STOVE = "minecraft:furnace[facing=east]",
            TABLE = "minecraft:spruce_slab[type=top]", LADDER = "minecraft:ladder", TARP = "minecraft:gray_wool",
            SEAT = "minecraft:stripped_spruce_log[axis=z]", WIRE = "minecraft:iron_bars", AIR = "minecraft:air",
            FIRE = "minecraft:campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]",
            FIRE_OFF = "minecraft:campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]";

    // барак О12 (обмер: доски 204 + тент 211, x −15..−5, z −101..−93, высота до 4)
    public static final int HX0 = -14, HX1 = -8, HZ0 = -100, HZ1 = -95, DOOR_Z = -97;
    /** Костёр заставы — у западной стены барака. */
    public static final int FX = -18, FZ = -98;
    /** Вышка на восточном краю чаши (уровень 3). */
    public static final int TX = -6, TZ = -102;

    /** Обход по краю чаши. */
    static final int[][] PATROL = {{-20, -106}, {-20, -92}, {-8, -91}, {-4, -98}, {-28, -128}};

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Построить свалку и заставу. Блоки — в мировых координатах. */
    public static void build(BlockSink b, int lvl) {
        lvl = Math.max(0, Math.min(3, lvl));
        scatter(b);                 // мелочь первой — крупное (остовы, кучи) ставится поверх
        junk(b);
        concreteStrip(b);
        piles(b);
        wrecks(b);
        hut(b, lvl);
        camp(b, lvl);
        if (lvl >= 2) bags(b);
        if (lvl >= 3) { tower(b); wire(b); }
        for (int[] p : PATROL)
            b.mark("patrol", p[0], b.ground(p[0], p[1]) + 1, p[1]);
    }

    static int g(BlockSink b, int x, int z) { return b.ground(x, z); }

    /** Остовы по следам обмера: тип техники, центр, куда смотрит нос (0 — на +x, 90 — на +z), повреждение. */
    private static void wrecks(BlockSink b) {
        Object[][] w = {
                {"laz", -49, -115, 0, 2}, {"kamaz", -52, -108, 90, 2}, {"paz", -27, -99, 0, 2},
                {"zil", -26, -104, 0, 2}, {"btr", -8, -107, 90, 1}, {"zil_fire", -20, -119, 0, 2},
                {"raf", -49, -122, 90, 2}, {"zhiguli", -34, -125, 0, 2}, {"moskvich", -30, -120, 90, 2},
                {"zaporozhets", -41, -126, 0, 2}, {"uaz", -44, -133, 90, 1}, {"niva", -23, -113, 0, 2},
                {"tractor", -58, -120, 0, 1}, {"zhiguli", -37, -131, 180, 1}, {"moskvich", -46, -128, 270, 2}};
        long seed = 7000;
        for (Object[] o : w) {
            int x = (Integer) o[1], z = (Integer) o[2];
            B v = new B(b, x, z, (Integer) o[3], seed++, 2);
            Vehicles.vehicle(v, (String) o[0], 0, 0, 0, null, (Integer) o[4]);
        }
    }

    /** Кучи ржавого листа (обмер: высота 1–2): центр, радиусы, высота. Сверху — полублок. */
    private static void piles(BlockSink b) {
        int[][] p = {{-41, -134, 3, 2, 2}, {-52, -124, 2, 2, 2}, {-33, -129, 3, 1, 1}, {-36, -121, 3, 1, 1},
                {-14, -118, 3, 1, 2}, {-26, -125, 2, 1, 1}, {-47, -108, 1, 2, 1}, {-38, -116, 2, 1, 1}, {-56, -131, 3, 2, 2}};
        for (int[] q : p) for (int x = q[0] - q[2]; x <= q[0] + q[2]; x++) for (int z = q[1] - q[3]; z <= q[1] + q[3]; z++) {
            double d = Math.pow((x - q[0]) / (double) (q[2] + 1), 2) + Math.pow((z - q[1]) / (double) (q[3] + 1), 2);
            if (d > 1 || hash(x, 3, z) % 5 == 0) continue;
            int h = Math.max(1, q[4] - (int) (d * 1.6)), gy = g(b, x, z);
            for (int k = 1; k < h; k++) b.set(x, gy + k, z, RUST);
            b.set(x, gy + h, z, RUST + "_slab[type=bottom]");
        }
        // груда досок на западном краю (обмер: x −72..−68, z −113..−110, высота до 3)
        for (int x = -72; x <= -68; x++) for (int z = -113; z <= -110; z++) {
            int h = 1 + hash(x, 5, z) % 3, gy = g(b, x, z);
            for (int k = 1; k <= h; k++) b.set(x, gy + k, z, hash(x, k, z) % 3 == 0 ? PLANKS + "_slab[type=bottom]" : PLANKS);
        }
    }

    /** Бетонная полоса (обмер: x −33..−18, z −111..−110, высота 1): сломанные плиты, проходы через каждые 5 блоков. */
    private static void concreteStrip(BlockSink b) {
        for (int x = -33; x <= -18; x++) {
            if (Math.floorMod(x, 5) == 0) continue;
            for (int z = -111; z <= -110; z++) {
                if (hash(x, 7, z) % 5 == 0) continue;
                b.set(x, g(b, x, z) + 1, z, hash(x, 8, z) % 2 == 0 ? CONC_O : CONC);
            }
        }
    }

    /** Бочки, шины, ящики и стопки шин на дне чаши: по хешу, в стороне от остовов. */
    private static void junk(BlockSink b) {
        for (int i = 0; i < 40; i++) {
            int x = -56 + hash(i, 1, 11) % 40, z = -138 + hash(i, 2, 12) % 28;
            if (g(b, x, z) > g(b, -40, -124) + 5) continue;           // выше дна чаши — не сбрасывают
            if (z >= -118 && z <= -102 && x <= -43) continue;      // остов автобуса и КамАЗа
            if (z >= -111 && z <= -110) continue;                     // бетонная полоса
            int gy = g(b, x, z) + 1;
            switch (i % 4) {
                case 0 -> b.set(x, gy, z, BARREL);
                case 1 -> b.set(x, gy, z, RAD);
                case 2 -> { b.set(x, gy, z, TIRES); if (i % 8 == 2) b.set(x, gy + 1, z, TIRES); }
                default -> b.set(x, gy, z, WCRATE);
            }
        }
    }

    /** Мелкий мусор по всей чаше: листы металла, доски, бетонные обломки, кости техники (двери, капоты). */
    private static void scatter(BlockSink b) {
        outer:
        for (int i = 0; i < 90; i++) {
            int x = -66 + hash(i, 21, 5) % 50, z = -138 + hash(i, 22, 6) % 40;
            if (z >= -111 && z <= -110) continue;
            for (int[] pp : PATROL) if (Math.abs(x - pp[0]) <= 1 && Math.abs(z - pp[1]) <= 1) continue outer;
            if (x >= HX0 - 6 && x <= HX1 + 3 && z >= HZ0 - 4 && z <= HZ1 + 4) continue;     // двор заставы чистый
            int gy = g(b, x, z) + 1;
            switch (hash(i, 23, 7) % 5) {
                case 0, 1 -> b.set(x, gy, z, RUST + "_slab[type=bottom]");
                case 2 -> b.set(x, gy, z, PLANKS + "_slab[type=bottom]");
                case 3 -> b.set(x, gy, z, CONC_O + "_slab[type=bottom]");
                default -> b.set(x, gy, z, hash(i, 24, 8) % 2 == 0 ? "minecraft:coal_block" : "stalkerzone:zm_metal_plate");
            }
        }
    }

    /** Барак О12: стены 3 из досок, плоская кровля из ржавого листа, тент на восточном козырьке, дверь на запад. */
    private static void hut(BlockSink b, int lvl) {
        int n = (HX1 - HX0 + 1) * (HZ1 - HZ0 + 1), i = 0;
        int[] gs = new int[n];
        for (int x = HX0; x <= HX1; x++) for (int z = HZ0; z <= HZ1; z++) gs[i++] = g(b, x, z);
        java.util.Arrays.sort(gs);
        int gr = gs[n / 2], y = gr + 1, top = y + 3;
        for (int x = HX0; x <= HX1; x++) for (int z = HZ0; z <= HZ1; z++) {
            boolean edge = x == HX0 || x == HX1 || z == HZ0 || z == HZ1;
            for (int yy = gr - 1; yy < gr; yy++) b.set(x, yy, z, FLOOR);
            b.set(x, gr, z, FLOOR);
            for (int yy = y; yy < top; yy++) {
                if (!edge) { b.set(x, yy, z, AIR); continue; }
                boolean win = yy == y + 1 && (z == HZ0 || z == HZ1) && Math.floorMod(x - HX0, 3) == 1;
                if (win) { b.set(x, yy, z, lvl == 0 ? AIR : GLASS); continue; }
                b.set(x, yy, z, lvl == 0 && hash(x, yy, z) % 7 == 0 ? AIR : PLANKS);
            }
            b.set(x, top, z, RUST + "_slab[type=bottom]");
        }
        // тент (обмер: материал 211) — козырёк над восточной стороной
        for (int x = HX1 + 1; x <= HX1 + 2; x++) for (int z = HZ0; z <= HZ1; z++) b.set(x, y + 2, z, TARP);
        for (int z : new int[] {HZ0, HZ1}) for (int yy = y; yy <= y + 1; yy++) b.set(HX1 + 2, yy, z, POST);
        // дверь на запад, выровненная с землёй снаружи
        if (lvl >= 1) {
            b.set(HX0, y, DOOR_Z, DOOR + "[facing=west,hinge=left,open=false,half=lower]");
            b.set(HX0, y + 1, DOOR_Z, DOOR + "[facing=west,hinge=left,open=false,half=upper]");
        } else { b.set(HX0, y, DOOR_Z, AIR); b.set(HX0, y + 1, DOOR_Z, AIR); }
        for (int x = HX0 - 1; x >= HX0 - 3; x--) {           // подход: земля снаружи вровень с полом
            int go = g(b, x, DOOR_Z);
            for (int yy = go + 1; yy <= gr; yy++) b.set(x, yy, DOOR_Z, FLOOR);
            for (int yy = gr + 1; yy <= go; yy++) b.set(x, yy, DOOR_Z, AIR);
            b.set(x, gr + 1, DOOR_Z, AIR); b.set(x, gr + 2, DOOR_Z, AIR);
        }
        // обстановка: нары вдоль северной стены, печка и стол у южной, ящики
        for (int x : new int[] {HX0 + 1, HX0 + 2, HX1 - 2, HX1 - 1}) {
            b.set(x, y, HZ0 + 1, lvl >= 1 ? MATTRESS : AIR);
            b.mark("bed", x, y, HZ0 + 2);
        }
        b.set(HX0 + 1, y, HZ1 - 1, lvl >= 1 ? STOVE : AIR);
        b.set(HX0 + 2, y, HZ1 - 1, lvl >= 1 ? TABLE : WCRATE);
        b.set(HX1 - 2, y, HZ1 - 1, WCRATE);
        b.set(HX1 - 1, y, HZ1 - 1, CRATE);
        if (lvl >= 1) { b.set((HX0 + HX1) / 2, top - 1, HZ0 + 2, LIGHT); b.set((HX0 + HX1) / 2, top - 1, HZ1 - 1, LIGHT); }
        b.mark("rest", (HX0 + HX1) / 2, y, DOOR_Z);
        b.mark("work", HX0 + 3, y, HZ1 - 1);
    }

    /** Костёр заставы с чурбаками, ящик у огня; места cook / rest / gate. */
    private static void camp(BlockSink b, int lvl) {
        int gy = g(b, FX, FZ);
        b.set(FX, gy + 1, FZ, lvl >= 1 ? FIRE : FIRE_OFF);
        for (int[] s : new int[][] {{FX - 2, FZ}, {FX + 1, FZ - 2}, {FX, FZ + 2}})
            b.set(s[0], g(b, s[0], s[1]) + 1, s[1], SEAT);
        b.set(FX + 2, g(b, FX + 2, FZ + 1) + 1, FZ + 1, CRATE);
        b.mark("cook", FX + 1, g(b, FX + 1, FZ) + 1, FZ);
        b.mark("rest", FX - 1, g(b, FX - 1, FZ - 1) + 1, FZ - 1);
        b.mark("rest", FX - 1, g(b, FX - 1, FZ + 1) + 1, FZ + 1);
        // у дороги от чаши — вход на заставу, часовой лицом к чаше
        b.mark("gate", FX - 4, g(b, FX - 4, DOOR_Z) + 1, DOOR_Z);
        b.mark("guard", FX - 1, g(b, FX - 1, FZ + 4) + 1, FZ + 4);
    }

    /** Уровень 2: мешки заслоном западнее костра (x −21) с проходом у двери (z −99..−95). */
    private static void bags(BlockSink b) {
        for (int z = -104; z <= -91; z++) {
            if (z >= -99 && z <= -95) continue;
            b.set(-21, g(b, -21, z) + 1, z, BAGS);
        }
        for (int x = HX0; x <= HX1; x++) b.set(x, g(b, x, HZ1 + 2) + 1, HZ1 + 2, x % 3 == 0 ? AIR : BAGS);
    }

    /** Уровень 3: деревянная вышка у восточного края с лестницей и площадкой в мешках. */
    private static void tower(BlockSink b) {
        int gy = g(b, TX, TZ);
        for (int yy = gy + 1; yy <= gy + 5; yy++) {
            for (int[] c : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) b.set(TX + c[0], yy, TZ + c[1], POST);
            b.set(TX + 1, yy, TZ + 3, POST);                              // опора лестницы
            b.set(TX + 1, yy, TZ + 2, LADDER + "[facing=north]");
        }
        for (int x = TX; x <= TX + 2; x++) for (int z = TZ; z <= TZ + 2; z++) {
            b.set(x, gy + 6, z, PLANKS);
            if (x == TX || x == TX + 2 || z == TZ) b.set(x, gy + 7, z, BAGS);
        }
        b.set(TX + 1, gy + 6, TZ + 2, AIR);
        b.mark("guard", TX + 1, gy + 7, TZ + 1);
    }

    /** Уровень 3: лёгкая колючка (решётка) по северному краю чаши, с проходами. */
    private static void wire(BlockSink b) {
        for (int x = -66; x <= -26; x++) {
            if (Math.floorMod(x, 9) < 2) continue;
            b.set(x, g(b, x, -138) + 1, -138, WIRE);
        }
    }
}
