package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.Features;

/**
 * Строители объектов карты. Каждый рисует объект в своих локальных координатах (см. {@link B});
 * один и тот же код строит мир и превью. Поведение детерминировано (зерно от объекта).
 */
public final class Structures {
    private Structures() {}

    // частые блоки
    static final String AIR = B.AIR, GRASS = "minecraft:grass_block", DIRT = "minecraft:dirt", COARSE = "minecraft:coarse_dirt",
            PLANKS = "minecraft:spruce_planks", DPLANKS = "minecraft:dark_oak_planks", LOG = "minecraft:stripped_spruce_log[axis=y]",
            BRICKS = "minecraft:bricks", CONCRETE = "minecraft:light_gray_concrete", GCONCRETE = "minecraft:gray_concrete",
            SMOOTH = "minecraft:smooth_stone", SLAB = "minecraft:smooth_stone_slab[type=bottom]", STONEB = "minecraft:stone_bricks",
            CRACKED = "minecraft:cracked_stone_bricks", COBBLE = "minecraft:cobblestone", MOSSY = "minecraft:mossy_cobblestone",
            BARS = "minecraft:iron_bars", PANE = "minecraft:glass_pane", SAND = "minecraft:smooth_sandstone", FENCE = "minecraft:spruce_fence",
            BARREL = "stalkerzone:rusty_barrel", TIRES = "stalkerzone:tires", IRON = "minecraft:iron_block", RAWIRON = "minecraft:raw_iron_block",
            LANTERN = "minecraft:lantern[hanging=false]", CAMPFIRE = "minecraft:campfire[lit=true,facing=north]", CHAIN = "minecraft:chain[axis=y]",
            WALL = "minecraft:cobblestone_wall", OLIVE = "minecraft:green_terracotta", RUST = "minecraft:brown_terracotta";

    static String stairs(String wood, String facing) { return "minecraft:" + wood + "_stairs[facing=" + facing + ",half=bottom]"; }
    static String stairsTop(String wood, String facing) { return "minecraft:" + wood + "_stairs[facing=" + facing + ",half=top]"; }
    static String logX() { return "minecraft:stripped_spruce_log[axis=x]"; }
    static String logZ() { return "minecraft:stripped_spruce_log[axis=z]"; }

    /** Построить объект на стартовом уровне развития. */
    public static void build(Features.Placement p, BlockSink sink) { build(p, sink, defaultLevel(p)); }

    /** Объекты, у которых есть уровни развития 0..3 (их перестраивает симуляция). */
    public static boolean hasLevels(String kind) {
        return switch (kind) { case "rookie_house", "sidorovich_bunker", "kpp", "atp", "farm_elevator", "road_post", "draft_post", "military_base" -> true; default -> false; };
    }

    /** Стартовый уровень: деревня частью брошена, АТП — лагерь бандитов, элеватор пустует, военные укреплены средне. */
    public static int defaultLevel(Features.Placement p) {
        return switch (p.kind()) {
            case "rookie_house" -> Math.floorMod(p.seed(), 10) < 3 ? 0 : Math.floorMod(p.seed(), 10) < 8 ? 1 : 2;
            case "sidorovich_bunker", "kpp", "military_base" -> 2;
            case "atp", "road_post", "draft_post" -> 1;
            case "farm_elevator" -> 0;
            default -> 2;
        };
    }

    /** Построить объект на заданном уровне развития (0 руины … 3 укреплённый). */
    public static void build(Features.Placement p, BlockSink sink, int level) {
        B b = new B(sink, p.x(), p.z(), p.rot(), p.seed(), hasLevels(p.kind()) ? level : 2);
        switch (p.kind()) {
            case "rookie_house" -> Buildings.villageHouse(b);
            case "sidorovich_bunker" -> Buildings.traderBunker(b);
            case "village_fire" -> villageFire(b);
            case "kpp" -> Buildings.checkpoint(b);
            case "military_camp" -> militaryCamp(b);
            case "fence_segment" -> fenceSegment(b);
            case "watchtower" -> watchtower(b, 0, 0);
            case "hut" -> hut(b);
            case "rail_bridge" -> railBridge(b);
            case "rail_tunnel" -> railTunnel(b);
            case "tent_camp" -> tentCamp(b);
            case "atp" -> Buildings.atp(b);
            case "farm_elevator" -> Buildings.elevator(b);
            case "road_post" -> Buildings.roadPost(b);
            case "military_base" -> Buildings.militaryBase(b);
            case "vehicle_showcase" -> showcase(b);
            case "vehicle" -> Vehicles.vehicle(b, Vehicles.ALL[Math.floorMod(p.seed(), Vehicles.ALL.length)], 0, 0, 0, null, Math.floorMod(p.seed() >> 8, 3));
            case "pylon" -> pylon(b);
            case "car_wreck" -> car(b, 0, 0, p.rot(), 0);
            case "barrels" -> barrels(b, 0, 0);
            case "lair_den" -> lairDen(b);
            case "draft_base" -> draftBase(b);
            case "draft_post" -> Buildings.roadPost(b);
            case "lab_entrance" -> labEntrance(b);
            case "rail_wagons" -> railWagons(b);
            case "ruin_house" -> ruinHouse(b);
            case "bus_stop" -> busStop(b);
            case "wood_pole" -> woodPole(b);
            case "haystack" -> haystack(b);
            case "shed" -> shed(b);
            case "well" -> well(b);
            case "stalker_tents" -> stalkerTents(b);
            default -> { }
        }
    }

    /** Витрина техники: вся библиотека в три ряда (целая, разграбленная, сгоревшая). */
    static void showcase(B b) {
        for (int d = 0; d < 3; d++) {
            int x = -60;
            for (String k : Vehicles.ALL) {
                int len = Vehicles.PLANS.get(k).layers()[0][0].length();
                Vehicles.vehicle(b, k, x + len / 2, -8 + d * 8, 0, Vehicles.CAR_COLORS[(x + 60) / 7 % Vehicles.CAR_COLORS.length], d);
                x += len + 3;
            }
        }
    }

    /** Габарит для отсечения по чанкам: полуширина объекта в блоках (с запасом). */
    public static int reach(Features.Placement p) {
        return switch (p.kind()) {
            case "atp", "farm_elevator" -> 72;
            case "military_camp" -> 40;
            case "rookie_house", "sidorovich_bunker" -> 24;
            case "road_post", "draft_post" -> 28;
            case "military_base" -> 36;
            case "vehicle_showcase" -> 80;
            case "fence_segment", "pylon" -> 135;
            case "rail_bridge", "rail_tunnel" -> 24;
            case "rail_wagons" -> 40;
            case "kpp" -> 48;
            case "draft_base" -> 36;
            default -> Math.max(p.padW(), p.padD()) / 2 + 10;
        };
    }

    // ================================================================= деревня новичков

    /** Деревенский дом: кирпичный цоколь, бревенчатые стены, двускатная крыша, печная труба, крыльцо, огород с забором. */
    static void rookieHouse(B b) {
        int x0 = -4, x1 = 4, z0 = -3, z1 = 3;
        boolean ruined = b.chance(0.25);
        b.foundation(x0, z0, x1, z1, 4, COBBLE);
        b.fill(x0, 0, z0, x1, 0, z1, BRICKS);
        b.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, PLANKS);
        b.walls(x0, 1, z0, x1, 3, z1, b.chance(0.5) ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks");
        for (int y = 1; y <= 3; y++) for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) b.set(c[0], y, c[1], LOG);
        b.clear(x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1);
        // окна: стёкла, выбитые, заколоченные
        for (int x : new int[] {x0 + 2, x1 - 2}) for (int z : new int[] {z0, z1}) b.set(x, 2, z, b.chance(0.4) ? PANE : b.chance(0.5) ? AIR : "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
        b.set(x0, 2, 0, b.chance(0.5) ? PANE : AIR);
        b.set(x1, 2, 0, b.chance(0.5) ? PANE : AIR);
        // дверь с юга и крыльцо
        b.set(0, 1, z1, AIR);
        b.set(0, 2, z1, AIR);
        b.fill(-1, 0, z1 + 1, 1, 0, z1 + 1, "minecraft:spruce_slab[type=top]");
        b.set(-2, 1, z1 + 1, FENCE);
        b.set(2, 1, z1 + 1, FENCE);
        // крыша: ступени вдоль оси x
        String wood = b.chance(0.5) ? "dark_oak" : "spruce";
        for (int k = 0; k <= 4; k++) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                if (ruined && x > 0 && b.chance(0.6)) continue;
                if (b.chance(0.04)) continue;
                int zn = z0 - 1 + k, zs = z1 + 1 - k;
                if (zn < zs) {
                    b.set(x, 4 + k, zn, stairs(wood, "south"));
                    b.set(x, 4 + k, zs, stairs(wood, "north"));
                } else b.set(x, 4 + k, zn, "minecraft:" + wood + "_slab[type=bottom]");
            }
            // фронтоны
            if (k < 4) for (int z = z0 + k + 1; z <= z1 - k - 1; z++) { b.set(x0, 4 + k, z, PLANKS); b.set(x1, 4 + k, z, PLANKS); }
        }
        // труба
        b.fill(x1 - 2, 1, z0 + 1, x1 - 2, 8, z0 + 1, BRICKS);
        // внутри: печь, стол, лежанка, бочка
        b.set(x1 - 1, 1, z0 + 1, "minecraft:furnace[facing=west]");
        b.set(x0 + 1, 1, z0 + 1, "minecraft:white_wool");
        b.set(x0 + 1, 1, z0 + 2, "minecraft:white_wool");
        b.set(x0 + 2, 1, z1 - 1, "minecraft:spruce_fence");
        b.set(x0 + 2, 2, z1 - 1, "minecraft:spruce_pressure_plate");
        b.set(x1 - 1, 1, z1 - 1, BARREL);
        // огород и покосившийся забор
        for (int x = x0 - 2; x <= x1 + 2; x++) if (b.chance(0.75)) b.setG(x, 0, z0 - 6, FENCE);
        for (int z = z0 - 6; z <= z0 - 2; z++) { if (b.chance(0.75)) b.setG(x0 - 2, 0, z, FENCE); if (b.chance(0.75)) b.setG(x1 + 2, 0, z, FENCE); }
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 5; z <= z0 - 2; z++) if (b.chance(0.5)) b.setG(x, -1, z, "minecraft:coarse_dirt");
        if (ruined) {
            for (int i = 0; i < 6; i++) b.set(x0 + b.r.nextInt(9), 1 + b.r.nextInt(3), b.chance(0.5) ? z0 : z1, AIR);
            b.set(1, 1, 0, "minecraft:cobweb");
        }
    }

    static void villageFire(B b) {
        b.set(0, 0, 0, CAMPFIRE);
        b.set(-2, 0, 0, logZ());
        b.set(2, 0, 0, logZ());
        b.set(0, 0, 2, logX());
        b.set(0, 0, -2, logX());
        b.set(3, 0, 2, BARREL);
        b.set(-3, 0, -2, "minecraft:barrel[facing=up]");
        b.set(-3, 1, -2, LANTERN);
    }

    // ================================================================= военные

    // ================================================================= мелкие объекты

    /** Три товарных вагона на путях (ржавые, один без колёс на боку). */
    static void railWagons(B b) {
        for (int i = 0; i < 3; i++) {
            int x0 = -30 + i * 20;
            String body = i == 1 ? "minecraft:brown_terracotta" : b.chance(0.5) ? "minecraft:red_terracotta" : RUST;
            int y = b.groundAt(x0 + 7, 0);
            for (int x = x0; x <= x0 + 14; x++) for (int z = -2; z <= 2; z++) {
                b.set(x, y, z, (x == x0 + 2 || x == x0 + 12) && Math.abs(z) == 1 ? "minecraft:black_concrete" : "minecraft:spruce_slab[type=top]");
                for (int yy = 1; yy <= 4; yy++) {
                    boolean door = x >= x0 + 6 && x <= x0 + 8 && yy <= 3 && z == 2 && b.chance(0.7);
                    if (Math.abs(z) == 2 || x == x0 || x == x0 + 14) b.set(x, y + yy, z, door ? AIR : body);
                }
                b.set(x, y + 5, z, b.chance(0.08) ? AIR : "minecraft:smooth_stone_slab[type=bottom]");
            }
        }
    }

    /** Сгоревший дом: остатки стен, угли, без крыши. */
    static void ruinHouse(B b) {
        b.fill(-4, 0, -3, 4, 0, 3, BRICKS);
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            if (x != -4 && x != 4 && z != -3 && z != 3) { if (b.chance(0.15)) b.set(x, 1, z, "minecraft:coal_block"); continue; }
            int h = 1 + b.r.nextInt(3);
            for (int y = 1; y <= h; y++) b.set(x, y, z, y == h && b.chance(0.5) ? "minecraft:blackstone" : "minecraft:dark_oak_planks");
        }
        b.fill(3, 1, -2, 3, 6, -2, BRICKS);
        for (int i = 0; i < 4; i++) b.set(b.r.nextInt(9) - 4, 1, b.r.nextInt(7) - 3, "minecraft:dead_bush");
    }

    /** Бетонная автобусная остановка с навесом и скамейкой. */
    static void busStop(B b) {
        b.fill(-3, -1, -2, 3, -1, 1, SMOOTH);
        b.fill(-3, 0, -2, 3, 2, -2, CONCRETE);
        b.set(-3, 0, -1, CONCRETE); b.set(-3, 1, -1, CONCRETE);
        b.set(3, 0, -1, CONCRETE); b.set(3, 1, -1, CONCRETE);
        b.fill(-4, 3, -3, 4, 3, 1, SLAB);
        for (int x = -2; x <= 2; x++) b.set(x, 0, -1, "minecraft:spruce_slab[type=bottom]");
        for (int x = -2; x <= 2; x += 2) b.set(x, 1, -2, "minecraft:blue_terracotta");
    }

    /** Деревянный столб линии электропередачи (провода оборваны). */
    static void woodPole(B b) {
        int h = 7 + b.r.nextInt(2);
        for (int y = 0; y < h; y++) b.setG(0, y, 0, "minecraft:stripped_spruce_log[axis=y]");
        b.setG(-1, h - 1, 0, FENCE);
        b.setG(1, h - 1, 0, FENCE);
        if (b.chance(0.5)) for (int y = h - 2; y > h - 5; y--) b.setG(1, y, 0, "minecraft:chain[axis=y]");
    }

    static void haystack(B b) {
        b.setG(0, 0, 0, "minecraft:hay_block[axis=y]");
        b.setG(1, 0, 0, "minecraft:hay_block[axis=y]");
        b.setG(0, 0, 1, "minecraft:hay_block[axis=y]");
        b.setG(1, 0, 1, "minecraft:hay_block[axis=y]");
        b.setG(0, 1, 0, "minecraft:hay_block[axis=y]");
        if (b.chance(0.5)) b.setG(1, 1, 1, "minecraft:hay_block[axis=y]");
    }

    /** Дощатый сарай с односкатной крышей. */
    static void shed(B b) {
        b.walls(-3, 0, -2, 3, 2, 2, "minecraft:dark_oak_planks");
        b.fill(-2, 0, -1, 2, 2, 1, AIR);
        b.set(0, 0, 2, AIR); b.set(0, 1, 2, AIR);
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) b.set(x, z < 0 ? 4 : 3, z, b.chance(0.1) ? AIR : "minecraft:dark_oak_slab[type=bottom]");
        b.set(-2, 0, -1, "minecraft:hay_block[axis=y]");
        b.set(2, 0, -1, BARREL);
    }

    /** Колодец-журавль. */
    static void well(B b) {
        b.walls(-1, 0, -1, 1, 0, 1, COBBLE);
        b.set(0, 0, 0, "minecraft:water");
        b.set(0, -1, 0, "minecraft:water");
        b.fill(-1, 1, -1, -1, 2, -1, FENCE);
        b.fill(1, 1, 1, 1, 2, 1, FENCE);
        b.fill(-1, 3, -1, 1, 3, 1, "minecraft:spruce_slab[type=bottom]");
    }

    /** Стоянка сталкеров у костра: две палатки, лавки, ящики. */
    static void stalkerTents(B b) {
        tentCamp(new B(b.s, b.wx(-3, 0), b.wz(-3, 0), b.rot, b.r.nextLong()));
        tentCamp(new B(b.s, b.wx(5, 1), b.wz(5, 1), (b.rot + 180) % 360, b.r.nextLong()));
    }

    /** Военный лагерь: палатки, бронетранспортёр, мешки, ящики, генератор. */
    static void militaryCamp(B b) {
        for (int i = 0; i < 3; i++) armyTent(b, -18 + i * 12, -6);
        btr(b, 12, 6);
        for (int x = -26; x <= 26; x += 2) if (b.chance(0.8)) { b.setG(x, 0, 14, SAND); if (b.chance(0.5)) b.setG(x, 1, 14, SAND); }
        for (int i = 0; i < 6; i++) b.set(-20 + i * 3, 0, 8, i % 2 == 0 ? "minecraft:barrel[facing=up]" : "minecraft:green_wool");
        b.fill(20, 0, -10, 22, 1, -9, IRON);
        b.set(21, 2, -10, "minecraft:lightning_rod");
        b.set(0, 0, 4, CAMPFIRE);
    }

    static void armyTent(B b, int cx, int cz) {
        String cloth = "minecraft:green_wool";
        for (int z = cz - 4; z <= cz + 4; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                int h = 3 - Math.abs(dx);
                b.set(cx + dx, h, z, cloth);
                if (h > 0 && Math.abs(dx) >= 1) b.set(cx + dx, h - 1, z, cloth);
                if (z == cz - 4 || z == cz + 4) for (int y = 0; y < h; y++) b.set(cx + dx, y, z, z == cz + 4 && dx == 0 ? AIR : cloth);
            }
            b.fill(cx - 2, 0, z, cx + 2, 0, z, b.chance(0.2) ? "minecraft:green_carpet" : AIR);
        }
        b.set(cx, 1, cz + 4, AIR);
        b.set(cx - 1, 0, cz - 2, "minecraft:white_wool");
        b.set(cx + 1, 0, cz - 2, "minecraft:white_wool");
    }

    /** БТР: оливковый корпус на восьми колёсах, башня с пулемётом. */
    static void btr(B b, int cx, int cz) {
        Vehicles.vehicle(b, "btr", cx, cz, 0, null, b.lvl == 0 ? 2 : 0);
    }

    /** Звено периметра: бетонные столбы и колючая проволока по рельефу, местами прорвано. */
    static void fenceSegment(B b) {
        for (int x = -40; x < 40; x++) {
            boolean post = Math.floorMod(x, 5) == 0;
            boolean gap = Math.floorMod(x * 7 + b.ox, 37) < 2;
            if (post) { b.setG(x, 0, 0, GCONCRETE); b.setG(x, 1, 0, GCONCRETE); b.setG(x, 2, 0, GCONCRETE); b.setG(x, 3, 0, "minecraft:stone_button[face=floor]"); }
            else if (!gap) { b.setG(x, 0, 0, BARS); b.setG(x, 1, 0, BARS); if (b.chance(0.7)) b.setG(x, 2, 0, BARS); }
        }
    }

    /** Вышка на четырёх ногах с площадкой, перилами, крышей и лестницей. */
    static void watchtower(B b, int cx, int cz) {
        for (int dx : new int[] {-1, 2}) for (int dz : new int[] {-1, 2}) b.fill(cx + dx, -2, cz + dz, cx + dx, 7, cz + dz, LOG);
        b.fill(cx - 2, 8, cz - 2, cx + 3, 8, cz + 3, PLANKS);
        for (int x = cx - 2; x <= cx + 3; x++) for (int z = cz - 2; z <= cz + 3; z++)
            if (x == cx - 2 || x == cx + 3 || z == cz - 2 || z == cz + 3) b.set(x, 9, z, x == cx + 3 && z == cz + 1 ? AIR : FENCE);
        for (int dx : new int[] {-2, 3}) for (int dz : new int[] {-2, 3}) b.fill(cx + dx, 9, cz + dz, cx + dx, 11, cz + dz, FENCE);
        b.fill(cx - 3, 12, cz - 3, cx + 4, 12, cz + 4, "minecraft:spruce_slab[type=bottom]");
        b.set(cx, 11, cz, "minecraft:lantern[hanging=true]");
        for (int y = 0; y <= 8; y++) b.set(cx + 3, y, cz + 1, "minecraft:ladder[facing=west]");
        b.fill(cx + 2, 0, cz + 1, cx + 2, 8, cz + 1, PLANKS);
    }

    /** Хутор: изба, сарай, колодец. */
    static void hut(B b) {
        rookieHouse(b);
        b.fill(7, 0, -2, 10, 3, 2, "minecraft:dark_oak_planks");
        b.fill(8, 0, -1, 9, 2, 1, AIR);
        b.set(8, 0, 2, AIR); b.set(8, 1, 2, AIR);
        b.fill(6, 4, -3, 11, 4, 3, "minecraft:dark_oak_slab[type=bottom]");
        b.walls(-7, 0, 5, -5, 0, 7, COBBLE);
        b.set(-6, 0, 6, "minecraft:water");
        b.fill(-7, 1, 5, -7, 2, 5, FENCE); b.fill(-5, 1, 7, -5, 2, 7, FENCE);
        b.fill(-7, 3, 5, -5, 3, 7, "minecraft:spruce_slab[type=bottom]");
    }

    // ================================================================= железная дорога

    /**
     * Ж/д мост Кордона над трассой (как в оригинале): массивные бетонные устои со скосами, пролёт на двутавровых
     * балках, рельсы на балласте, сверху блокпост военных (мешки, пулемёт, прожектор, ящики), под мостом
     * сгоревшие машины и бетонные блоки, у въезда БТР.
     */
    static void railBridge(B b) {
        int crest = b.groundAt(-20, 0);           // верх насыпи в стороне от проезда
        int road = Math.min(b.groundAt(0, -32), b.groundAt(0, 32)); // уровень трассы
        int half = 8;
        String CAP = "minecraft:polished_andesite", SB = "minecraft:stone_bricks";
        // проезд под мостом: асфальт с разметкой
        b.fill(-half, road, -14, half, crest - 3, 14, AIR);
        for (int x = -half; x <= half; x++) for (int z = -14; z <= 14; z++)
            b.put(x, road - 1, z, Math.abs(x) <= 6 ? (x == 0 && (z & 3) < 2 ? "minecraft:white_concrete" : "minecraft:black_concrete") : "minecraft:gravel");
        // устои: кладка из бетонных блоков, скошенные края, пояс-карниз
        for (int sgn : new int[] {-1, 1}) {
            int xa = sgn * (half + 1), xb = sgn * (half + 3);
            for (int z = -14; z <= 14; z++) {
                int top = Math.abs(z) <= 5 ? crest - 2 : crest - 2 - (Math.abs(z) - 5);
                if (top < road) continue;
                b.fill(Math.min(xa, xb), road - 1, z, Math.max(xa, xb), top, z, SB);
                b.put(xa, top, z, Math.abs(z) <= 5 ? CAP : "minecraft:stone_brick_stairs[facing=" + (sgn < 0 ? "west" : "east") + ",half=bottom]");
                if (Math.abs(z) <= 5) for (int y = road; y < top; y += 4) b.put(xa, y, z, (z & 1) == 0 ? "minecraft:cracked_stone_bricks" : SB);
            }
            // скосы по краям устоев
            for (int y = road; y <= crest - 2; y++) {
                b.put(xa, y, -6, "minecraft:stone_brick_stairs[facing=south,half=bottom]");
                b.put(xa, y, 6, "minecraft:stone_brick_stairs[facing=north,half=bottom]");
            }
        }
        // пролёт: две двутавровые балки по краям, поперечины, балласт, шпалы, рельсы, перила
        for (int x = -half - 3; x <= half + 3; x++) {
            for (int z = -4; z <= 4; z++) b.put(x, crest - 2, z, Math.abs(z) == 4 ? GCONCRETE : (x & 1) == 0 ? "minecraft:smooth_stone_slab[type=top]" : GCONCRETE);
            for (int z : new int[] {-4, 4}) {
                b.put(x, crest - 3, z, "minecraft:smooth_stone_slab[type=top]"); // нижняя полка балки
                b.put(x, crest - 1, z, CAP);
                b.put(x, crest, z, (x % 4 == 0) ? "minecraft:stone_brick_wall" : BARS);
            }
            for (int z = -3; z <= 3; z++) b.put(x, crest - 1, z, "minecraft:gravel");
            if ((x & 1) == 0) for (int z = -2; z <= 2; z++) b.put(x, crest, z, "minecraft:spruce_slab[type=bottom]");
            b.put(x, crest, -1, "minecraft:rail[shape=east_west]");
            b.put(x, crest, 1, "minecraft:rail[shape=east_west]");
        }
        // надпись-полоса на балке (жёлто-чёрная, предупреждение о габарите)
        for (int x = -half; x <= half; x++) { b.put(x, crest - 3, 5, (x & 1) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete"); b.put(x, crest - 3, -5, (x & 1) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete"); }
        // блокпост на мосту: мешки полукругом, пулемёт, навес, прожектор, ящики, рация
        int px = half + 6;
        for (int z = -4; z <= 4; z++) { b.put(px, crest, z, SAND); b.put(px, crest + 1, z, z == 0 ? AIR : "minecraft:smooth_sandstone_slab[type=bottom]"); }
        for (int x = px - 4; x < px; x++) { b.put(x, crest, -4, SAND); b.put(x, crest, 4, SAND); }
        b.put(px - 1, crest, 0, "minecraft:dispenser[facing=east]");
        b.fill(px - 4, crest + 3, -4, px, crest + 3, 4, "minecraft:green_wool");
        for (int[] c : new int[][] {{px - 4, -4}, {px, -4}, {px - 4, 4}, {px, 4}}) b.fill(c[0], crest + 1, c[1], c[0], crest + 2, c[1], FENCE);
        b.put(px - 3, crest, -2, "stalkerzone:military_crate");
        b.put(px - 3, crest + 1, -2, "stalkerzone:military_crate");
        b.put(px - 3, crest, 2, "minecraft:note_block");
        b.put(px - 2, crest, 3, "minecraft:barrel[facing=up]");
        b.put(-half - 4, crest, 3, BARS);
        b.put(-half - 4, crest + 1, 3, "minecraft:iron_block");
        b.put(-half - 4, crest + 2, 3, "minecraft:sea_lantern");
        // под мостом: бетонные блоки змейкой и сгоревшие машины
        for (int[] c : new int[][] {{-5, -11}, {-4, -11}, {3, -3}, {4, -3}, {-5, 9}, {-4, 9}}) {
            b.put(c[0], road, c[1], SMOOTH);
            b.put(c[0], road + 1, c[1], "minecraft:smooth_stone_slab[type=bottom]");
        }
        Vehicles.vehicleAt(b, "zhiguli", -3, road, 3, 90, "white", 2);
        Vehicles.vehicleAt(b, "raf", 4, road, -9, 90, null, 1);
        // БТР у въезда на мост со стороны Периметра
        Vehicles.vehicleAt(b, "btr", 2, road, 24, 270, null, 0);
    }

    /** Тоннель-водопропуск под насыпью: каменная арка, сырость, разбитые лампы; внутри поселятся электры. */
    static void railTunnel(B b) {
        int floor = Math.min(b.groundAt(0, -16), b.groundAt(0, 16));
        for (int z = -14; z <= 14; z++) {
            b.fill(-3, floor, z, 3, floor + 4, z, STONEB);
            b.fill(-2, floor, z, 2, floor + 3, z, AIR);
            b.set(-2, floor + 3, z, STONEB);
            b.set(2, floor + 3, z, STONEB);
            b.fill(-2, floor - 1, z, 2, floor - 1, z, b.chance(0.3) ? "minecraft:mud" : "minecraft:mossy_cobblestone");
            if (b.chance(0.1)) b.set(b.r.nextInt(3) - 1, floor + 2, z, "minecraft:cobweb");
        }
        for (int z : new int[] {-14, 14}) for (int x = -3; x <= 3; x++) b.set(x, floor + 4, z, "minecraft:mossy_stone_bricks");
        b.set(0, floor + 3, -6, "minecraft:redstone_lamp");
        b.set(0, floor + 3, 6, "minecraft:redstone_lamp");
        b.set(1, floor, 0, "minecraft:water");
    }

    // ================================================================= лагеря

    static void tentCamp(B b) {
        // брезентовая палатка, костёр, бревна, ящики
        String cloth = b.chance(0.5) ? "minecraft:brown_wool" : "minecraft:lime_terracotta";
        for (int z = -2; z <= 2; z++) for (int dx = -2; dx <= 2; dx++) {
            int h = 2 - Math.abs(dx);
            b.set(-4 + dx, h, z, cloth);
            if (z == -2) for (int y = 0; y < h; y++) b.set(-4 + dx, y, z, cloth);
        }
        b.set(0, 0, 0, CAMPFIRE);
        b.set(2, 0, 0, logZ());
        b.set(0, 0, 2, logX());
        b.set(3, 0, -3, "minecraft:barrel[facing=up]");
        b.set(4, 0, -3, BARREL);
        b.set(3, 1, -3, LANTERN);
        if (b.chance(0.5)) b.set(-1, 0, 3, "minecraft:white_wool");
    }

    // ================================================================= АТП

    /** Грузовик ЗИЛ: кабина, кузов, колёса; часть ржавые без колёс. */
    static void truck(B b, int cx, int cz, boolean alongZ) {
        String kind = b.chance(0.45) ? "zil" : b.chance(0.5) ? "kamaz" : b.chance(0.5) ? "paz" : "uaz";
        int dmg = b.lvl == 0 ? 1 + b.r.nextInt(2) : b.lvl == 1 ? b.r.nextInt(2) : b.chance(0.2) ? 1 : 0;
        Vehicles.vehicle(b, kind, cx, cz, alongZ ? 90 : 0, null, dmg);
    }

    // ================================================================= элеватор и фермы

    /** Длинный кирпичный коровник с двускатной шиферной крышей, часть кровли провалена. */
    static void barn(B b, int x0, int z0, int len) {
        int w = 10;
        b.foundation(x0, z0, x0 + len, z0 + w, 3, COBBLE);
        b.walls(x0, 0, z0, x0 + len, 3, z0 + w, BRICKS);
        b.fill(x0 + 1, 0, z0 + 1, x0 + len - 1, 3, z0 + w - 1, AIR);
        for (int x = x0 + 2; x < x0 + len; x += 3) { b.set(x, 2, z0, b.chance(0.5) ? AIR : PANE); b.set(x, 2, z0 + w, b.chance(0.5) ? AIR : PANE); }
        b.fill(x0, 0, z0 + 4, x0, 2, z0 + 6, AIR);
        b.fill(x0 + len, 0, z0 + 4, x0 + len, 2, z0 + 6, AIR);
        for (int k = 0; k <= w / 2; k++) for (int x = x0 - 1; x <= x0 + len + 1; x++) {
            if (b.chance(0.08) || (x > x0 + len / 2 && x < x0 + len / 2 + 6 && b.chance(0.8))) continue;
            int zn = z0 - 1 + k, zs = z0 + w + 1 - k;
            if (zn < zs) {
                b.set(x, 4 + k, zn, "minecraft:cobblestone_stairs[facing=south,half=bottom]");
                b.set(x, 4 + k, zs, "minecraft:cobblestone_stairs[facing=north,half=bottom]");
            } else b.set(x, 4 + k, zn, "minecraft:cobblestone_slab[type=bottom]");
        }
        for (int x = x0 + 2; x < x0 + len - 1; x += 4) b.set(x, 0, z0 + 2, "minecraft:hay_block");
    }

    static void tractor(B b, int cx, int cz) {
        Vehicles.vehicle(b, "tractor", cx, cz, 0, null, b.lvl == 0 ? 1 : 0);
    }

    // ================================================================= прочее

    /** Опора ЛЭП: решётчатая мачта с траверсами; провода до следующей опоры (через 128 блоков к востоку). */
    static void pylon(B b) {
        for (int y = 0; y <= 22; y++) {
            int w = Math.max(1, 3 - y / 7);
            for (int dx : new int[] {-w, w}) for (int dz : new int[] {-w, w}) b.set(dx, y, dz, BARS);
            if (y % 4 == 0) for (int k = -w; k <= w; k++) { b.set(k, y, -w, BARS); b.set(k, y, w, BARS); b.set(-w, y, k, BARS); b.set(w, y, k, BARS); }
        }
        b.fill(-7, 18, 0, 7, 18, 0, IRON);
        b.fill(-4, 22, 0, 4, 22, 0, IRON);
        b.fill(-3, -2, -3, 3, -1, 3, CONCRETE);
        // провисающие провода
        int top = 17;
        for (int wire : new int[] {-7, 7}) {
            for (int dx = 1; dx < 128; dx++) {
                double t = dx / 128.0;
                int sag = (int) Math.round(6 * 4 * t * (1 - t));
                int gy = b.groundAt(dx, wire + dx / 32) + top;
                int y0 = top;
                // высота провода относительно земли этой опоры: прямая между опорами минус провис
                int yEnd = b.groundAt(128, wire + 4) + top;
                int y = (int) Math.round(y0 + (yEnd - y0) * t) - sag;
                b.set(dx, y, wire + dx / 32, "minecraft:chain[axis=x]");
            }
        }
    }

    /** Остов легковушки: ржавый кузов, пустые окна, колёса через раз. */
    static void car(B b, int cx, int cz, int rot, int variant) {
        // брошенная легковушка: модель и цвет случайны, повреждение — от ржавой до сгоревшей
        Vehicles.vehicle(b, Vehicles.randomCar(b), cx, cz, rot, null, b.r.nextInt(3));
    }

    static void barrels(B b, int cx, int cz) {
        int n = 3 + b.r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int x = cx + b.r.nextInt(5) - 2, z = cz + b.r.nextInt(5) - 2;
            b.setG(x, 0, z, b.chance(0.12) ? "stalkerzone:radioactive_barrel" : b.chance(0.7) ? BARREL : TIRES);
            if (b.chance(0.2)) b.setG(x, 1, z, BARREL);
        }
    }

    /** Логово: разрытые норы, кости, паутина, сухой бурьян. */
    static void lairDen(B b) {
        for (int i = 0; i < 40; i++) {
            int x = b.r.nextInt(25) - 12, z = b.r.nextInt(25) - 12;
            double d = Math.hypot(x, z);
            if (d > 12) continue;
            double v = b.r.nextDouble();
            if (v < 0.3) b.setG(x, -1, z, COARSE);
            else if (v < 0.45) b.setG(x, 0, z, "minecraft:bone_block[axis=" + (b.chance(0.5) ? "x" : "z") + "]");
            else if (v < 0.6) b.setG(x, 0, z, "minecraft:dead_bush");
            else if (v < 0.7) b.setG(x, 0, z, "minecraft:cobweb");
            else if (v < 0.8) { b.setG(x, -1, z, AIR); b.setG(x, -2, z, COARSE); }
        }
        b.setG(0, 0, 0, "minecraft:skeleton_skull[rotation=4]");
    }

    /** Черновая база (будет заменена прорисованной): бетонный забор, ангар, палатки, вышка. */
    static void draftBase(B b) {
        for (int x = -24; x <= 24; x++) for (int z : new int[] {-24, 24}) if (Math.abs(x) > 3 || z < 0) { b.setG(x, 0, z, CONCRETE); b.setG(x, 1, z, CONCRETE); }
        for (int z = -24; z <= 24; z++) for (int x : new int[] {-24, 24}) { b.setG(x, 0, z, CONCRETE); b.setG(x, 1, z, CONCRETE); }
        b.walls(-16, 0, -18, 4, 6, -6, BRICKS);
        b.fill(-15, 0, -17, 3, 5, -7, AIR);
        b.fill(-17, 7, -19, 5, 7, -5, SLAB);
        b.fill(-8, 0, -6, -4, 3, -6, AIR);
        armyTent(b, 12, 8);
        tentCamp(new B(b.s, b.wx(-10, 10), b.wz(-10, 10), b.rot, b.r.nextLong()));
        watchtower(b, 18, -18);
    }

    /** Вход в подземную лабораторию: бетонный бункер с решётками (сама лаборатория строится отдельно). */
    static void labEntrance(B b) {
        b.walls(-4, 0, -4, 4, 3, 4, GCONCRETE);
        b.fill(-3, 0, -3, 3, 3, 3, AIR);
        b.fill(-5, 4, -5, 5, 4, 5, SLAB);
        for (int x = -4; x <= 4; x++) { b.set(x, 1, -4, (x & 1) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete"); }
        b.fill(-1, 0, 4, 1, 2, 4, AIR);
    }
}
