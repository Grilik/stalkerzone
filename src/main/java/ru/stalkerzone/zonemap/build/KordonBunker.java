package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Бункер Сидоровича по канону оригинала (S.T.A.L.K.E.R., l01_escape) — задачи П-02б, П-02д.
 *
 * <p>Как в игре: за западным краем деревни новичков земля поднимается в склон; бункер врыт в этот склон. С плато
 * к нему ведёт выемка с бетонными подпорными стенками, в торце выемки — бетонный портал с козырьком и распахнутой
 * тяжёлой металлической дверью. За дверью тамбур и бетонная лестница вниз, внизу коридор, деревянная дверь —
 * и комната Сидоровича: посетитель у двери, торговец за стойкой, за его спиной стеллажи, ящики, шкаф, койка.
 * Над бункером — родной склон уровня; грунт досыпается только там, где перекрытие лестницы и тамбура иначе вышло бы
 * наружу, и сходит на нет плавно (без «бугра» на ровном месте). Над комнатой — вентиляционная труба, у входа антенна.
 *
 * <p>Высота внутри — 4 блока везде (комната, коридор, тамбур, над каждой ступенью), чтобы не задевать потолок.
 * Высота площадки {@code g} — медиана земли на плато деревни (x −39), у выхода из выемки.
 *
 * <p>Уровень развития (как в MineColonies): 0 — заброшенный (дверей нет, стены побиты, мусор, без света),
 * 1 — обжитой (двери, свет, стойка), 2 — торгующий (рация, телевизор, аптечки, больше товара),
 * 3 — укреплённый (решётка над стойкой, мешки в выемке).
 */
public final class KordonBunker {
    private KordonBunker() {}

    /** Область, где воксели и деревья уровня заменяет этот сборщик: {x0, z0, x1, z1} — склон с бункером и выемка. */
    public static final List<int[]> CLEAR = List.of(new int[] {-76, -20, -40, 6});

    static final String CONC = "stalkerzone:zm_concrete", CONC_OLD = "stalkerzone:zm_concrete_old",
            CONC_D = "stalkerzone:zm_concrete_dirty", CONC_SLAB = "stalkerzone:zm_concrete_slab",
            CONC_FLOOR = "stalkerzone:zm_concrete_floor", STEPS = "stalkerzone:zm_stair_steps_stairs",
            DOOR = "stalkerzone:zm_door_metal_door", DOOR_WOOD = "stalkerzone:zm_door_green_door",
            PANEL = "stalkerzone:zm_boards_vert", PLANKS = "minecraft:spruce_planks",
            SLAB = "minecraft:spruce_slab", SHELF = "minecraft:spruce_stairs", GATE = "minecraft:spruce_fence_gate",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate", MED = "stalkerzone:medical_crate",
            AMMO = "stalkerzone:prop_ammo_crates", CAB = "stalkerzone:prop_el_cabinet", RADIO = "stalkerzone:prop_radio",
            TV = "stalkerzone:prop_tv", RADIATOR = "stalkerzone:prop_radiator", BARREL = "minecraft:barrel",
            RUST = "stalkerzone:zm_rust", RUST_SLAB = "stalkerzone:zm_rust_slab", RUBBLE = "stalkerzone:zm_rubble_slab",
            BAGS = "stalkerzone:zm_sandbags", BARS = "minecraft:iron_bars", ROD = "minecraft:lightning_rod",
            LIGHT = "minecraft:lantern[hanging=true]", BED = "minecraft:brown_bed",
            DIRT = "minecraft:dirt", GRASS = "minecraft:grass_block", COARSE = "minecraft:coarse_dirt", GRAVEL = "minecraft:gravel",
            TUFT = "minecraft:grass", AIR = "minecraft:air";

    /** Высота помещений в блоках (воздух над полом). */
    public static final int H = 4;

    // план (мировые x, z); пол комнаты и коридора — F = g − 5, потолок комнаты — F + H + 1 = g
    public static final int RX0 = -63, RX1 = -56, RZ0 = -12, RZ1 = -5;   // комната Сидоровича (внутри), 8×8
    public static final int CX0 = -62, CX1 = -60;                        // низ коридора (ровный пол F)
    public static final int DOOR_X = -61;                                // дверь из коридора в комнату (северная стена)
    public static final int SX0 = -59, SX1 = -55;                        // лестница: пять ступеней на восток, вверх к выходу
    public static final int LX0 = -54, LX1 = -53;                        // тамбур на уровне земли площадки
    public static final int FACADE = -52;                                // фасад в торце выемки, наружная дверь
    public static final int CUT_X1 = -40;                                // выемка от фасада до края плато (x −39 — уже деревня)
    public static final int CZ0 = -3, CZ1 = -1;                          // ширина коридора, лестницы и тамбура (по z)
    public static final int DOOR_Z = -2;                                 // ось входа
    public static final int TRADER_X = -59, TRADER_Z = -8;               // торговец за стойкой
    public static final int COUNTER_Z = -7;                              // стойка; посетители — z −6..−5

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня: на заброшенном (0) часть стен побита. */
    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = Math.max(0, Math.min(3, lvl)); }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

        /** Блок стены: на уровне 0 — местами грязный осыпавшийся бетон и куски мусора (без проломов). */
        void wall(int x, int y, int z, String st) {
            int h = hash(x, y, z) % 100;
            if (lvl == 0 && h < 10) { s.set(x, y, z, CONC_D); return; }
            if (lvl == 0 && h < 14) { s.set(x, y, z, "stalkerzone:zm_rubble"); return; }
            s.set(x, y, z, st);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.set(x, y, z, st);
        }

        void walls(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) wall(x, y, z, st);
        }

        /**
         * Дверь 1×2 (на уровне 0 — пустой проём). Металлическая дверь мода рукой не открывается (как железная),
         * поэтому наружная тяжёлая дверь стоит распахнутой, а внутренняя, к Сидоровичу, — деревянная крашеная.
         */
        void door(int x, int y, int z, String facing, boolean metal) {
            if (lvl >= 1) {
                String d = (metal ? DOOR : DOOR_WOOD) + "[facing=" + facing + ",hinge=left,open=" + metal;
                set(x, y, z, d + ",half=lower]");
                set(x, y + 1, z, d + ",half=upper]");
            } else {
                set(x, y, z, AIR);
                set(x, y + 1, z, AIR);
            }
        }

        void lamp(int x, int y, int z) { if (lvl >= 1) set(x, y, z, LIGHT); }
    }

    /** Построить бункер. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, lvl);
        int g = base(b);
        int[][] surf = surface(b, g);
        slope(b, g, surf);
        room(b, g);
        corridor(b, g);
        entrance(b, g, surf);
        top(b, g, surf);
    }

    /** Высота площадки: медиана земли по краю плато деревни (x = край выемки + 1), у входа в бункер. */
    static int base(S b) {
        int[] c = CLEAR.get(0);
        int[] gs = new int[c[3] - c[1] + 1];
        for (int z = c[1]; z <= c[3]; z++) gs[z - c[1]] = b.ground(CUT_X1 + 1, z);
        java.util.Arrays.sort(gs);
        return gs[gs.length / 2];
    }

    // ---------------------------------------------------------------- склон

    /**
     * Поверхность над бункером: родной рельеф, а где перекрытие лестницы, тамбура или фасада выше него — досыпка
     * до «перекрытие + 1», сходящая на нет на 1 блок высоты за каждые 1,5 блока в стороны (как склон уровня).
     * Возвращает высоту верхнего блока в каждой колонке границ замены.
     */
    static int[][] surface(S b, int g) {
        int[] c = CLEAR.get(0);
        int w = c[2] - c[0] + 1, d = c[3] - c[1] + 1;
        java.util.List<int[]> need = new java.util.ArrayList<>();   // {x, z, нужная высота поверхности}
        int F = g - 5;
        for (int x = SX0; x <= FACADE; x++) for (int z = CZ0 - 1; z <= CZ1 + 1; z++) {
            int top;
            if (x <= SX1) top = F + 1 + (x - SX0) + H + 1;          // потолок над ступенью
            else top = g + H + 1;                                    // тамбур и фасад
            need.add(new int[] {x, z, top + 1});
        }
        for (int x = RX0 - 1; x <= RX1 + 1; x++) for (int z = RZ0 - 1; z <= RZ1 + 1; z++) need.add(new int[] {x, z, g + 1});
        int[][] s = new int[w][d];
        for (int i = 0; i < w; i++) for (int j = 0; j < d; j++) {
            int x = c[0] + i, z = c[1] + j;
            double h = b.ground(x, z);
            for (int[] n : need) {
                double dist = Math.hypot(x - n[0], z - n[1]);
                h = Math.max(h, n[2] - dist / 1.5);
            }
            s[i][j] = (int) Math.floor(h);
        }
        return s;
    }

    static int surf(int[][] s, int x, int z) {
        int[] c = CLEAR.get(0);
        return s[x - c[0]][z - c[1]];
    }

    /** Досыпать грунт до поверхности (родной рельеф не трогается), сверху трава с кочками. */
    private static void slope(S b, int g, int[][] s) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int gl = b.ground(x, z), top = surf(s, x, z);
            if (top <= gl) continue;
            for (int y = gl; y < top; y++) b.set(x, y, z, DIRT);
            b.set(x, top, z, hash(x, 7, z) % 11 == 0 ? COARSE : GRASS);
            if (hash(x, top, z) % 7 == 0) b.set(x, top + 1, z, TUFT);
        }
    }

    // ---------------------------------------------------------------- комната Сидоровича

    private static void room(S b, int g) {
        int F = g - 5;                                   // блок пола; стоят на F + 1, потолок F + H + 1
        int C = F + H + 1;
        b.fill(RX0, F + 1, RZ0, RX1, C - 1, RZ1, AIR);
        b.fill(RX0 - 1, F, RZ0 - 1, RX1 + 1, F, RZ1 + 1, CONC_FLOOR);
        b.fill(RX0 - 1, C, RZ0 - 1, RX1 + 1, C, RZ1 + 1, CONC);
        b.walls(RX0 - 1, F + 1, RZ0 - 1, RX1 + 1, C - 1, RZ0 - 1, CONC);
        b.walls(RX0 - 1, F + 1, RZ1 + 1, RX1 + 1, C - 1, RZ1 + 1, CONC);
        b.walls(RX0 - 1, F + 1, RZ0, RX0 - 1, C - 1, RZ1, CONC);
        b.walls(RX1 + 1, F + 1, RZ0, RX1 + 1, C - 1, RZ1, CONC);
        // половина торговца: пол — доски, стены обшиты до пояса
        for (int x = RX0; x <= RX1; x++) for (int z = RZ0; z <= COUNTER_Z - 1; z++) b.set(x, F, z, PLANKS);
        if (b.lvl >= 1) {
            for (int x = RX0; x <= RX1; x++) b.set(x, F + 1, RZ0 - 1, PANEL);
            for (int z = RZ0; z < COUNTER_Z; z++) { b.set(RX0 - 1, F + 1, z, PANEL); b.set(RX1 + 1, F + 1, z, PANEL); }
        }

        // стойка поперёк комнаты, калитка у восточной стены — проход торговца
        for (int x = RX0; x <= RX1 - 1; x++) {
            if (b.lvl == 0 && (x == TRADER_X - 2 || x == TRADER_X - 1)) { b.set(x, F + 1, COUNTER_Z, RUBBLE + "[type=bottom]"); continue; }
            b.set(x, F + 1, COUNTER_Z, x == TRADER_X ? SLAB + "[type=top]" : PLANKS);
        }
        b.set(RX1, F + 1, COUNTER_Z, b.lvl >= 1 ? GATE + "[facing=south,open=false]" : AIR);
        if (b.lvl >= 3) for (int x = RX0; x <= RX1 - 1; x++) if (x != TRADER_X) b.set(x, F + 2, COUNTER_Z, BARS);
        if (b.lvl >= 2) b.set(RX1 - 1, F + 2, COUNTER_Z, RADIO + "[facing=south]");
        else if (b.lvl == 1) b.set(RX1 - 1, F + 2, COUNTER_Z, SLAB + "[type=bottom]");

        // за спиной торговца: стеллажи вдоль северной стены, шкаф, ящики, койка
        for (int x = RX0 + 2; x <= RX1 - 2; x++) {
            b.set(x, F + 1, RZ0, SHELF + "[facing=south,half=top]");
            b.set(x, F + 2, RZ0, b.lvl >= 1 && x % 2 == 0 ? BARREL + "[facing=south]" : AIR);
            b.set(x, F + 3, RZ0, SHELF + "[facing=south,half=top]");
            b.set(x, F + 4, RZ0, b.lvl >= 2 && x % 2 != 0 ? WCRATE : AIR);
        }
        if (b.lvl >= 1) {
            b.set(RX0, F + 1, RZ0, CAB + "[facing=east]");
            b.set(RX1, F + 1, RZ0, CRATE);
            b.set(RX1, F + 2, RZ0, WCRATE);
            b.set(RX1, F + 1, RZ0 + 1, AMMO + "[facing=west]");
            b.set(RX0, F + 1, RZ0 + 2, BED + "[facing=south,part=foot]");   // койка у западной стены
            b.set(RX0, F + 1, RZ0 + 3, BED + "[facing=south,part=head]");
            b.set(TRADER_X - 2, F + 1, TRADER_Z, WCRATE);                   // ящик-табурет у стойки
        } else {
            b.set(RX1, F + 1, RZ0, WCRATE);
            b.set(TRADER_X - 2, F + 1, RZ0 + 2, RUBBLE + "[type=bottom]");
        }
        if (b.lvl >= 2) {
            b.set(RX0, F + 2, RZ0, TV + "[facing=east]");
            b.set(RX1, F + 1, RZ0 + 2, MED);
            b.set(RX1, F + 1, RZ0 + 3, CRATE);
        }

        // половина посетителей: батарея, ящик в углу
        b.set(RX0, F + 1, RZ1, b.lvl >= 1 ? RADIATOR + "[facing=east]" : RUBBLE + "[type=bottom]");
        if (b.lvl >= 1) b.set(RX1, F + 1, RZ1, WCRATE);

        b.lamp(TRADER_X - 1, C - 1, -10);
        b.lamp(TRADER_X, C - 1, -5);
        if (b.lvl >= 2) b.lamp(TRADER_X + 2, C - 1, -10);

        b.door(DOOR_X, F + 1, RZ1 + 1, "south", false);
        b.set(DOOR_X, F + 3, RZ1 + 1, CONC);              // перемычка над дверью
        b.set(DOOR_X, F + 4, RZ1 + 1, CONC);

        b.mark("trader", TRADER_X, F + 1, TRADER_Z);
        b.mark("shelter", TRADER_X - 1, F + 1, -5);
        b.mark("shelter", TRADER_X + 1, F + 1, -6);
    }

    // ---------------------------------------------------------------- коридор и лестница

    private static void corridor(S b, int g) {
        int F = g - 5, C = F + H + 1;
        b.fill(CX0, F + 1, CZ0, CX1, C - 1, CZ1, AIR);
        b.fill(CX0 - 1, F, CZ0 - 1, CX1, F, CZ1 + 1, CONC_FLOOR);
        b.fill(CX0 - 1, C, CZ0 - 1, CX1, C, CZ1 + 1, CONC);
        b.walls(CX0 - 1, F + 1, CZ0, CX0 - 1, C - 1, CZ1, CONC_OLD);
        b.walls(CX0 - 1, F + 1, CZ1 + 1, CX1, C - 1, CZ1 + 1, CONC_OLD);
        for (int x = CX0; x <= CX1; x++) if (x != DOOR_X) b.walls(x, F + 1, CZ0 - 1, x, C - 1, CZ0 - 1, CONC);
        // лестница: ступени на F+1..F+5 (последняя вровень с полом тамбура g), над ступенью H блоков воздуха
        for (int i = 0; i <= SX1 - SX0; i++) {
            int x = SX0 + i, y = F + 1 + i;
            for (int z = CZ0; z <= CZ1; z++) {
                b.set(x, y, z, STEPS + "[facing=east,half=bottom,shape=straight]");
                for (int k = 1; k <= H; k++) b.set(x, y + k, z, AIR);
                for (int k = F; k < y; k++) b.set(x, k, z, CONC_OLD);       // тело лестницы до пола
                b.set(x, y + H + 1, z, CONC);
            }
            b.walls(x, F, CZ0 - 1, x, y + H + 1, CZ0 - 1, CONC_OLD);
            b.walls(x, F, CZ1 + 1, x, y + H + 1, CZ1 + 1, CONC_OLD);
        }
        b.lamp(DOOR_X, C - 1, DOOR_Z);
        b.lamp(SX0 + 2, F + 3 + H, DOOR_Z);
        b.mark("duty", CX1, F + 1, DOOR_Z);              // курьер у двери Сидоровича
        b.mark("shelter", DOOR_X, F + 1, CZ0);
        b.mark("shelter", CX0, F + 1, CZ1);
    }

    // ---------------------------------------------------------------- вход в торце выемки

    private static void entrance(S b, int g, int[][] s) {
        // тамбур на уровне площадки
        b.fill(LX0, g + 1, CZ0, LX1, g + H, CZ1, AIR);
        b.fill(LX0, g, CZ0, LX1, g, CZ1, CONC_FLOOR);
        b.fill(LX0, g + H + 1, CZ0 - 1, FACADE, g + H + 1, CZ1 + 1, CONC);
        b.walls(LX0, g, CZ0 - 1, LX1, g + H, CZ0 - 1, CONC_OLD);
        b.walls(LX0, g, CZ1 + 1, LX1, g + H, CZ1 + 1, CONC_OLD);
        b.lamp(LX0, g + H, DOOR_Z);
        // фасад: бетонный портал, распахнутая металлическая дверь, козырёк с фонарём
        b.walls(FACADE, g + 1, CZ0 - 1, FACADE, g + H, CZ1 + 1, CONC);
        b.set(FACADE, g, DOOR_Z, CONC_FLOOR);
        b.door(FACADE, g + 1, DOOR_Z, "east", true);
        b.set(FACADE, g + 3, DOOR_Z, CONC);
        for (int z = CZ0 - 1; z <= CZ1 + 1; z++) b.set(FACADE + 1, g + H + 1, z, CONC_SLAB + "[type=bottom]");
        b.set(FACADE + 1, g + H, DOOR_Z, b.lvl >= 1 ? LIGHT : AIR);

        // выемка от фасада до плато: дно на уровне площадки, по бокам подпорные стенки вровень со склоном
        for (int x = FACADE + 1; x <= CUT_X1; x++) {
            for (int z = CZ0 - 1; z <= CZ1 + 1; z++) {
                int top = Math.max(surf(s, x, z), g + H + 2);
                for (int y = g + 1; y <= top + 1; y++)
                    if (!(x == FACADE + 1 && (y == g + H + 1 || (y == g + H && z == DOOR_Z)))) b.set(x, y, z, AIR);
                boolean edge = z == CZ0 - 1 || z == CZ1 + 1;
                for (int y = Math.min(b.ground(x, z), g) ; y < g; y++) b.set(x, y, z, DIRT);   // яма под дном выемки
                if (x <= FACADE + 3) b.set(x, g, z, GRAVEL);
                else if (!edge || hash(x, g, z) % 3 == 0) b.set(x, g, z, COARSE);
                else b.set(x, g, z, GRASS);
            }
            for (int z : new int[] {CZ0 - 2, CZ1 + 2}) {
                int top = x == FACADE + 1 ? g + H + 1 : surf(s, x, z);
                if (top <= g) continue;
                b.walls(x, g, z, x, top, z, CONC_OLD);
            }
        }
        // укреплённый: мешки в выемке
        if (b.lvl >= 3) for (int z : new int[] {CZ0 - 1, CZ1 + 1}) {
            b.set(FACADE + 4, g + 1, z, BAGS); b.set(FACADE + 5, g + 1, z, BAGS);
        }
    }

    // ---------------------------------------------------------------- на склоне над бункером

    private static void top(S b, int g, int[][] s) {
        int vx = TRADER_X - 1, vz = -9;                     // вентиляция над комнатой
        int h = surf(s, vx, vz);
        b.set(vx, h + 1, vz, RUST);
        b.set(vx, h + 2, vz, RUST);
        b.set(vx, h + 3, vz, RUST_SLAB + "[type=bottom]");
        if (b.lvl >= 1) {                                   // антенна над тамбуром
            int ax = LX0, az = CZ0 - 2;
            int ah = surf(s, ax, az);
            for (int k = 1; k <= 3; k++) b.set(ax, ah + k, az, BARS);
            b.set(ax, ah + 4, az, ROD);
        }
    }
}
