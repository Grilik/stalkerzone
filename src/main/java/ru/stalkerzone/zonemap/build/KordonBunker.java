package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Бункер Сидоровича по канону оригинала (S.T.A.L.K.E.R., l01_escape) — задача П-02б.
 *
 * <p>Как в игре: на западном краю деревни новичков невысокий травяной холм; в его восточном откосе, лицом к деревне,
 * бетонный портал с подпорными стенками и распахнутой тяжёлой металлической дверью. За дверью — тамбур и бетонная лестница вниз,
 * внизу короткий коридор, вторая дверь — и комната Сидоровича: посетитель стоит у двери, торговец — за стойкой,
 * за его спиной стеллажи с товаром, ящики, шкаф, койка. На макушке холма — вентиляционная труба и антенна.
 * В обмерах ({@code docs/kordon_objects/01-bunker.md}) вокселей бункера нет — он подземный, размеры подобраны
 * по виду оригинала; место — точка торговца узла «Бункер Сидоровича» (−35, −8): торговец стоит ровно в ней.
 *
 * <p>Площадка: бункер стоит в площадке деревни (класс Site). Высота земли {@code g} — медиана по краю границ замены,
 * а не под самим холмом: так она одна и та же и при генерации (рельеф уровня/плато), и при снятии рабочих мест
 * в мире, где холм уже стоит.
 *
 * <p>Уровень развития (как в MineColonies): 0 — заброшенный (дверей нет, стены побиты, мусор, без света),
 * 1 — обжитой (двери, свет, стойка), 2 — торгующий (рация, телевизор, аптечки, больше товара),
 * 3 — укреплённый (решётка над стойкой, мешки у входа).
 */
public final class KordonBunker {
    private KordonBunker() {}

    /** Область, где воксели уровня заменяет этот сборщик: {x0, z0, x1, z1} — холм, вход и тропа к деревне. */
    public static final List<int[]> CLEAR = List.of(new int[] {-41, -15, -20, 3});

    static final String CONC = "stalkerzone:zm_concrete", CONC_OLD = "stalkerzone:zm_concrete_old",
            CONC_D = "stalkerzone:zm_concrete_dirty", CONC_SLAB = "stalkerzone:zm_concrete_slab",
            CONC_FLOOR = "stalkerzone:zm_concrete_floor", STEPS = "stalkerzone:zm_stair_steps_stairs",
            DOOR = "stalkerzone:zm_door_metal_door", DOOR_WOOD = "stalkerzone:zm_door_green_door", PANEL = "stalkerzone:zm_boards_vert", PLANKS = "minecraft:spruce_planks",
            SLAB = "minecraft:spruce_slab", SHELF = "minecraft:spruce_stairs", GATE = "minecraft:spruce_fence_gate",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate", MED = "stalkerzone:medical_crate",
            AMMO = "stalkerzone:prop_ammo_crates", CAB = "stalkerzone:prop_el_cabinet", RADIO = "stalkerzone:prop_radio",
            TV = "stalkerzone:prop_tv", RADIATOR = "stalkerzone:prop_radiator", BARREL = "minecraft:barrel",
            RUST = "stalkerzone:zm_rust", RUST_SLAB = "stalkerzone:zm_rust_slab", RUBBLE = "stalkerzone:zm_rubble_slab",
            BAGS = "stalkerzone:zm_sandbags", BARS = "minecraft:iron_bars", ROD = "minecraft:lightning_rod",
            LIGHT = "minecraft:lantern[hanging=true]", BED = "minecraft:brown_bed",
            DIRT = "minecraft:dirt", GRASS = "minecraft:grass_block", COARSE = "minecraft:coarse_dirt", GRAVEL = "minecraft:gravel",
            TUFT = "minecraft:grass", AIR = "minecraft:air";

    // план (мировые x, z); пол комнаты и коридора — F = g − 5, потолок комнаты — g − 1 (под травой)
    static final int RX0 = -39, RX1 = -32, RZ0 = -12, RZ1 = -5;   // комната Сидоровича (внутри), 8×8
    static final int CX0 = -38, CX1 = -36;                        // низ коридора (ровный пол F)
    static final int SX0 = -35, SX1 = -31;                        // лестница: пять ступеней на восток, вверх к выходу
    static final int LX0 = -30, LX1 = -29;                        // тамбур на уровне земли
    static final int FACADE = -28;                                // восточный фасад, наружная дверь
    static final int CZ0 = -3, CZ1 = -1;                          // ширина коридора, лестницы и тамбура (по z)
    static final int DOOR_Z = -2;                                 // ось входа
    static final int TRADER_X = -35, TRADER_Z = -8;               // торговец за стойкой (точка узла)
    static final int COUNTER_Z = -7;                              // стойка; посетители — z −6..−5

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня: на заброшенном (0) часть стен выбита. */
    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = Math.max(0, Math.min(3, lvl)); }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

        /** Блок стены: на уровне 0 — местами грязный осыпавшийся бетон и куски мусора (без проломов: холм не должен сыпаться внутрь). */
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
         * Дверь 1×2 (на уровне 0 — пустой проём). facing — сторона, куда дверь смотрит. Металлическая дверь мода
         * рукой не открывается (как железная), поэтому наружная тяжёлая дверь стоит распахнутой, а внутренняя,
         * к Сидоровичу, — деревянная крашеная: её открывают и игрок, и NPC.
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

        /** Подвесной фонарь (на уровне 0 — света нет, кроме одного у входа). */
        void lamp(int x, int y, int z) { if (lvl >= 1) set(x, y, z, LIGHT); }
    }

    /** Построить бункер. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, lvl);
        int g = base(b);
        hill(b, g);
        room(b, g);
        corridor(b, g);
        entrance(b, g);
        top(b, g);
    }

    /** Высота земли площадки: медиана по краю границ замены (вне холма). */
    static int base(S b) {
        int[] c = CLEAR.get(0);
        java.util.ArrayList<Integer> gs = new java.util.ArrayList<>();
        for (int x = c[0]; x <= c[2]; x++) { gs.add(b.ground(x, c[1])); gs.add(b.ground(x, c[3])); }
        for (int z = c[1] + 1; z < c[3]; z++) { gs.add(b.ground(c[0], z)); gs.add(b.ground(c[2], z)); }
        java.util.Collections.sort(gs);
        return gs.get(gs.size() / 2);
    }

    // ---------------------------------------------------------------- холм

    /** Высота холма над землёй в колонке (0 — вне холма): пологий купол + обязательное перекрытие над тамбуром. */
    static int hillH(int x, int z) { return (int) Math.round(hillF(x, z)); }

    /** Высота холма с дробной частью (купол). */
    static double hillF(int x, int z) {
        double dx = (x + 33) / 8.0, dz = (z + 6) / 9.0;
        double d = dx * dx + dz * dz;
        double h = d >= 1 ? 0 : 6.6 * Math.pow(1 - d, 0.8);
        // над лестницей и тамбуром — не меньше 1 блока грунта поверх потолка
        if (z >= CZ0 - 1 && z <= CZ1 + 1 && x >= SX0 && x <= LX1) h = Math.max(h, x >= LX0 ? 5 : x - SX0 + 1);
        return h;
    }

    /** Грунт холма: снять старое, засыпать до высоты холма, сверху трава с редкими кочками. */
    private static void hill(S b, int g) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int gl = b.ground(x, z);
            for (int y = Math.min(gl, g) + 1; y <= g + 12; y++) b.set(x, y, z, AIR);   // воксели, бугры
            for (int y = gl + 1; y <= g; y++) b.set(x, y, z, DIRT);                     // ямы до уровня площадки
            int h = hillH(x, z);
            if (h == 0) { if (gl != g) b.set(x, g, z, GRASS); continue; }
            for (int y = g; y < g + h; y++) b.set(x, y, z, DIRT);
            b.set(x, g + h, z, hash(x, 7, z) % 11 == 0 ? COARSE : GRASS);
            if (hash(x, g + h, z) % 7 == 0) b.set(x, g + h + 1, z, TUFT);
        }
    }

    // ---------------------------------------------------------------- комната Сидоровича

    private static void room(S b, int g) {
        int F = g - 5;                                   // блок пола; стоят на F + 1
        b.fill(RX0, F + 1, RZ0, RX1, F + 3, RZ1, AIR);
        b.fill(RX0 - 1, F, RZ0 - 1, RX1 + 1, F, RZ1 + 1, CONC_FLOOR);
        b.fill(RX0 - 1, F + 4, RZ0 - 1, RX1 + 1, F + 4, RZ1 + 1, CONC);
        b.walls(RX0 - 1, F + 1, RZ0 - 1, RX1 + 1, F + 3, RZ0 - 1, CONC);
        b.walls(RX0 - 1, F + 1, RZ1 + 1, RX1 + 1, F + 3, RZ1 + 1, CONC);
        b.walls(RX0 - 1, F + 1, RZ0, RX0 - 1, F + 3, RZ1, CONC);
        b.walls(RX1 + 1, F + 1, RZ0, RX1 + 1, F + 3, RZ1, CONC);
        // половина торговца обшита досками до пояса, пол — доски
        for (int x = RX0; x <= RX1; x++) for (int z = RZ0; z <= COUNTER_Z - 1; z++) b.set(x, F, z, PLANKS);
        if (b.lvl >= 1) {
            for (int x = RX0; x <= RX1; x++) b.set(x, F + 1, RZ0 - 1, PANEL);
            for (int z = RZ0; z < COUNTER_Z; z++) { b.set(RX0 - 1, F + 1, z, PANEL); b.set(RX1 + 1, F + 1, z, PANEL); }
        }

        // стойка поперёк комнаты, калитка у восточной стены — проход торговца
        for (int x = RX0; x <= RX1 - 1; x++) {
            if (b.lvl == 0 && (x == -37 || x == -36)) { b.set(x, F + 1, COUNTER_Z, RUBBLE + "[type=bottom]"); continue; }
            b.set(x, F + 1, COUNTER_Z, x == TRADER_X ? SLAB + "[type=top]" : PLANKS);
        }
        b.set(RX1, F + 1, COUNTER_Z, b.lvl >= 1 ? GATE + "[facing=south,open=false]" : AIR);
        if (b.lvl >= 3) for (int x = RX0; x <= RX1 - 1; x++) if (x != TRADER_X) b.set(x, F + 2, COUNTER_Z, BARS);
        if (b.lvl >= 2) b.set(-33, F + 2, COUNTER_Z, RADIO + "[facing=south]");
        else if (b.lvl == 1) b.set(-33, F + 2, COUNTER_Z, SLAB + "[type=bottom]");

        // за спиной торговца: стеллажи вдоль северной стены, шкаф, ящики, койка
        for (int x = RX0 + 2; x <= RX1 - 2; x++) {
            b.set(x, F + 1, RZ0, SHELF + "[facing=south,half=top]");
            b.set(x, F + 2, RZ0, b.lvl >= 1 && x % 2 == 0 ? BARREL + "[facing=south]" : AIR);
            b.set(x, F + 3, RZ0, SHELF + "[facing=south,half=top]");
        }
        if (b.lvl >= 1) {
            b.set(RX0, F + 1, RZ0, CAB + "[facing=east]");
            b.set(RX1, F + 1, RZ0, CRATE);
            b.set(RX1, F + 2, RZ0, WCRATE);
            b.set(RX1, F + 1, RZ0 + 1, AMMO + "[facing=west]");
            b.set(RX0, F + 1, RZ0 + 2, BED + "[facing=south,part=foot]");   // койка у западной стены
            b.set(RX0, F + 1, RZ0 + 3, BED + "[facing=south,part=head]");
            b.set(-37, F + 1, TRADER_Z, WCRATE);                            // ящик-табурет рядом со стойкой
        } else {
            b.set(RX1, F + 1, RZ0, WCRATE);
            b.set(-37, F + 1, RZ0 + 2, RUBBLE + "[type=bottom]");
        }
        if (b.lvl >= 2) {
            b.set(RX0, F + 2, RZ0, TV + "[facing=east]");
            b.set(RX1, F + 1, RZ0 + 2, MED);
            b.set(RX1, F + 1, RZ0 + 3, CRATE);
        }

        // половина посетителей: батарея, ящик у стены
        b.set(RX0, F + 1, RZ1, b.lvl >= 1 ? RADIATOR + "[facing=east]" : RUBBLE + "[type=bottom]");
        if (b.lvl >= 1) b.set(RX1, F + 1, RZ1, WCRATE);

        b.lamp(-36, F + 3, -10);
        b.lamp(-35, F + 3, -5);
        if (b.lvl >= 2) b.lamp(-33, F + 3, -10);

        // проём в коридор: южная стена, напротив низа коридора
        b.door(-37, F + 1, RZ1 + 1, "south", false);

        b.mark("trader", TRADER_X, F + 1, TRADER_Z);
        b.mark("shelter", -36, F + 1, -5);
        b.mark("shelter", -34, F + 1, -6);
    }

    // ---------------------------------------------------------------- коридор и лестница

    private static void corridor(S b, int g) {
        int F = g - 5;
        // низ коридора: ровный пол, потолок вровень с комнатой
        b.fill(CX0, F + 1, CZ0, CX1, F + 3, CZ1, AIR);
        b.fill(CX0 - 1, F, CZ0 - 1, CX1, F, CZ1 + 1, CONC_FLOOR);
        b.fill(CX0 - 1, F + 4, CZ0 - 1, CX1, F + 4, CZ1 + 1, CONC);
        b.walls(CX0 - 1, F + 1, CZ0, CX0 - 1, F + 3, CZ1, CONC_OLD);
        b.walls(CX0 - 1, F + 1, CZ1 + 1, CX1, F + 3, CZ1 + 1, CONC_OLD);
        for (int x = CX0; x <= CX1; x++) if (x != -37) b.walls(x, F + 1, CZ0 - 1, x, F + 3, CZ0 - 1, CONC);
        // лестница: ступени на F+1..F+5 (последняя вровень с полом тамбура g), воздух на 3 над ступенью
        for (int i = 0; i <= SX1 - SX0; i++) {
            int x = SX0 + i, y = F + 1 + i;
            for (int z = CZ0; z <= CZ1; z++) {
                b.set(x, y, z, STEPS + "[facing=east,half=bottom,shape=straight]");
                for (int k = 1; k <= 3; k++) b.set(x, y + k, z, AIR);
                for (int k = F; k < y; k++) b.set(x, k, z, CONC_OLD);       // тело лестницы до пола
                b.set(x, y + 4, z, CONC);
            }
            b.walls(x, F, CZ0 - 1, x, y + 4, CZ0 - 1, CONC_OLD);
            b.walls(x, F, CZ1 + 1, x, y + 4, CZ1 + 1, CONC_OLD);
        }
        b.lamp(-37, F + 3, DOOR_Z);
        b.lamp(-33, F + 6, DOOR_Z);
        b.mark("duty", -36, F + 1, DOOR_Z);          // курьер / охранник у двери Сидоровича
        b.mark("shelter", -37, F + 1, CZ0);
        b.mark("shelter", -38, F + 1, CZ1);
    }

    // ---------------------------------------------------------------- вход в откосе

    private static void entrance(S b, int g) {
        // тамбур на уровне земли
        b.fill(LX0, g + 1, CZ0, LX1, g + 3, CZ1, AIR);
        b.fill(LX0, g, CZ0, LX1, g, CZ1, CONC_FLOOR);
        b.fill(LX0, g + 4, CZ0 - 1, FACADE + 1, g + 4, CZ1 + 1, CONC);
        b.walls(LX0, g, CZ0 - 1, LX1, g + 3, CZ0 - 1, CONC_OLD);
        b.walls(LX0, g, CZ1 + 1, LX1, g + 3, CZ1 + 1, CONC_OLD);
        b.lamp(LX0, g + 3, DOOR_Z);
        // фасад: бетонный портал в откосе, наружная дверь, козырёк, подпорные стенки по бокам выреза
        b.walls(FACADE, g + 1, CZ0 - 1, FACADE, g + 4, CZ1 + 1, CONC);
        b.set(FACADE, g + 1, DOOR_Z - 1, CONC_OLD); b.set(FACADE, g + 1, DOOR_Z + 1, CONC_OLD);
        b.set(FACADE, g, DOOR_Z, CONC_FLOOR);
        b.door(FACADE, g + 1, DOOR_Z, "east", true);
        b.set(FACADE, g + 3, DOOR_Z, CONC);
        for (int z = CZ0 - 1; z <= CZ1 + 1; z++) b.set(FACADE + 1, g + 4, z, CONC_SLAB + "[type=bottom]");
        if (b.lvl >= 1) b.set(FACADE + 1, g + 3, DOOR_Z, LIGHT);
        else b.set(FACADE + 1, g + 3, DOOR_Z, AIR);
        // подпорные стенки держат края выреза, пока откос холма не сойдёт на нет
        for (int z : new int[] {CZ0 - 2, CZ1 + 2}) {
            for (int x = FACADE; x <= CLEAR.get(0)[2]; x++) {
                int top = x == FACADE ? g + 4 : g + Math.min(3, hillH(x, z));
                if (top <= g) break;
                b.walls(x, g + 1, z, x, top, z, CONC_OLD);
                b.set(x, g, z, CONC_OLD);
            }
        }
        // вырез перед дверью: без грунта, гравий; тропа к деревне
        for (int x = FACADE + 1; x <= CLEAR.get(0)[2]; x++) for (int z = CZ0 - 1; z <= CZ1 + 1; z++) {
            for (int y = g + 1; y <= g + 6; y++) if (!(x == FACADE + 1 && y == g + 4) && !(x == FACADE + 1 && y == g + 3 && z == DOOR_Z)) b.set(x, y, z, AIR);
            boolean edge = z == CZ0 - 1 || z == CZ1 + 1;
            if (x <= FACADE + 3) b.set(x, g, z, GRAVEL);
            else if (!edge || hash(x, g, z) % 3 == 0) b.set(x, g, z, COARSE);
        }
        // укреплённый: мешки у входа
        if (b.lvl >= 3) for (int z : new int[] {CZ0 - 1, CZ1 + 1}) {
            b.set(FACADE + 3, g + 1, z, BAGS); b.set(FACADE + 4, g + 1, z, BAGS);
        }
    }

    // ---------------------------------------------------------------- макушка холма

    private static void top(S b, int g) {
        int vx = -36, vz = -9;                       // над комнатой
        int h = hillH(vx, vz);
        b.set(vx, g + h, vz, RUST);
        b.set(vx, g + h + 1, vz, RUST);
        b.set(vx, g + h + 2, vz, RUST_SLAB + "[type=bottom]");
        int ax = -33, az = -4;                       // антенна у входа
        int ah = hillH(ax, az);
        if (b.lvl >= 1) {
            for (int k = 1; k <= 3; k++) b.set(ax, g + ah + k, az, BARS);
            b.set(ax, g + ah + 4, az, ROD);
        }
    }
}
