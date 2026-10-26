package ru.stalkerzone.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import ru.stalkerzone.block.ModBlocks;
import ru.stalkerzone.sim.BuildingType;
import ru.stalkerzone.sim.Node;

import java.util.List;
import java.util.Random;

/**
 * Узнаваемые здания оригинала, встроенные в развитие баз: занимают место обычной постройки того же типа
 * (штаб, мастерская, казарма) и меняют вид по уровню: 1 залатанные руины, 2 отремонтированное здание
 * с мешками и светом, 3 укреплённое: прожекторы, антенны, колючка и флаг владельца.
 */
public final class Landmarks {
    private Landmarks() {}

    private static BlockState b(Block b) { return b.defaultBlockState(); }

    /** Подменить места обычных построек на знаковые здания этой локации. */
    public static void override(Node n, int cls, List<Camp.Site> sites) {
        switch (n.name) {
            case "Кордон" -> {
                replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -15, 9, 7, Direction.SOUTH, "sidorovich", 5));
                for (int i = 0; i < sites.size(); i++) {
                    Camp.Site s = sites.get(i);
                    if (s.type() == BuildingType.BARRACKS) sites.set(i, new Camp.Site(s.type(), s.cx(), s.cz(), 5, 5, s.door(), "village", 6));
                }
            }
            case "Бар" -> {
                replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -18, 15, 9, Direction.SOUTH, "bar100", 9));
                replace(sites, BuildingType.WORKSHOP, new Camp.Site(BuildingType.WORKSHOP, -23, -3, 11, 9, Direction.EAST, "rostok", 9));
            }
            case "Тёмная долина" -> replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -17, 13, 9, Direction.SOUTH, "factory", 8));
            case "Армейские склады" -> {
                replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -16, 13, 7, Direction.SOUTH, "army_barracks", 9));
                replace(sites, BuildingType.WORKSHOP, new Camp.Site(BuildingType.WORKSHOP, -23, -3, 11, 7, Direction.EAST, "hangar", 7));
            }
            case "Янтарь" -> replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -15, 11, 7, Direction.SOUTH, "science_bunker", 5));
            case "Военная база «Периметр»" -> replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -17, 13, 9, Direction.SOUTH, "military_hq", 9));
            case "ЧАЭС" -> replace(sites, BuildingType.HQ, new Camp.Site(BuildingType.HQ, 0, -19, 17, 11, Direction.SOUTH, "sarcophagus", 16));
            case "Агропром" -> {
                if (cls == 0) replace(sites, BuildingType.BARRACKS, new Camp.Site(BuildingType.BARRACKS, 0, 5, 13, 7, Direction.NORTH, "nii", 11));
                else replace(sites, BuildingType.WORKSHOP, new Camp.Site(BuildingType.WORKSHOP, 0, -13, 13, 7, Direction.SOUTH, "nii", 11));
            }
            case "Свалка" -> {
                if (cls == 0) replace(sites, BuildingType.BARRACKS, new Camp.Site(BuildingType.BARRACKS, 0, 6, 11, 7, Direction.NORTH, "depot", 8));
                else replace(sites, BuildingType.WORKSHOP, new Camp.Site(BuildingType.WORKSHOP, 0, -13, 11, 7, Direction.SOUTH, "depot", 8));
            }
            case "Дикая территория" -> replace(sites, BuildingType.WORKSHOP, new Camp.Site(BuildingType.WORKSHOP, 0, -13, 11, 7, Direction.SOUTH, "rail_depot", 7));
            case "Припять: центр" -> city(sites, "polesie", 15, 9, 20);
            case "Припять: «Прометей»" -> city(sites, "prometheus", 13, 9, 9);
            case "Припять: «Лазурный»" -> city(sites, "lazurny", 15, 9, 8);
            case "Припять: больница" -> city(sites, "hospital", 15, 9, 13);
            default -> {
                if (n.name.startsWith("Блокпост")) replace(sites, BuildingType.BARRACKS, new Camp.Site(BuildingType.BARRACKS, 0, 7, 7, 5, Direction.NORTH, "checkpoint", 5));
            }
        }
    }

    /** Район Припяти: мастерская становится знаковым зданием, казармы становятся жилыми панельками. */
    private static void city(List<Camp.Site> sites, String main, int w, int d, int h) {
        replace(sites, BuildingType.WORKSHOP, new Camp.Site(BuildingType.WORKSHOP, 0, -14, w, d, Direction.SOUTH, main, h));
        int[][] spots = {{-13, 6}, {13, 6}, {0, 15}};
        int k = 0;
        for (int i = 0; i < sites.size(); i++) {
            Camp.Site s = sites.get(i);
            if (s.type() == BuildingType.BARRACKS && k < spots.length) {
                sites.set(i, new Camp.Site(s.type(), spots[k][0], spots[k][1], 7, 5, Direction.NORTH, "panel", 12));
                k++;
            }
        }
    }

    private static void replace(List<Camp.Site> sites, BuildingType t, Camp.Site with) {
        for (int i = 0; i < sites.size(); i++) {
            if (sites.get(i).type() == t) {
                sites.set(i, with);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- общие детали

    private static void hollow(Painter pt, int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (x != x0 && x != x1 && z != z0 && z != z1) continue;
            pt.fill(x, y0, z, x, y1, z, wall);
        }
    }

    /** Окна в ряд на высоте y: по уровню пустые проёмы, доски или стекло. */
    private static void windows(Painter pt, Camp.Site s, int y, int level, Random r) {
        BlockState glass = b(level >= 2 ? Blocks.GLASS_PANE : Blocks.AIR);
        for (int x = s.x0() + 2; x <= s.x1() - 2; x += 3) {
            BlockState w = level == 1 && r.nextDouble() < 0.4 ? b(Blocks.SPRUCE_PLANKS) : glass;
            pt.set(x, y, s.z0(), w);
            pt.set(x, y, s.z1(), w);
        }
        for (int z = s.z0() + 2; z <= s.z1() - 2; z += 3) {
            pt.set(s.x0(), y, z, glass);
            pt.set(s.x1(), y, z, glass);
        }
    }

    private static void door(Painter pt, Camp.Site s, int w, int h) {
        int xm = s.cx(), zm = s.cz();
        for (int k = -w; k <= w; k++) {
            switch (s.door()) {
                case SOUTH -> pt.fill(xm + k, 0, s.z1(), xm + k, h - 1, s.z1(), b(Blocks.AIR));
                case NORTH -> pt.fill(xm + k, 0, s.z0(), xm + k, h - 1, s.z0(), b(Blocks.AIR));
                case EAST -> pt.fill(s.x1(), 0, zm + k, s.x1(), h - 1, zm + k, b(Blocks.AIR));
                default -> pt.fill(s.x0(), 0, zm + k, s.x0(), h - 1, zm + k, b(Blocks.AIR));
            }
        }
    }

    /** Уровень 2+: мешки у входа и фонари; уровень 3: прожекторы, антенна, колючка, флаг. */
    private static void upgrades(Painter pt, Camp.Site s, int level, int f, int top) {
        if (level >= 2) {
            int ox = s.door().getStepX(), oz = s.door().getStepZ();
            int px = ox > 0 ? s.x1() + 2 : ox < 0 ? s.x0() - 2 : s.cx();
            int pz = oz > 0 ? s.z1() + 2 : oz < 0 ? s.z0() - 2 : s.cz();
            BlockState sand = b(Blocks.SMOOTH_SANDSTONE);
            for (int k = -2; k <= 2; k++) {
                if (Math.abs(k) < 1) continue;
                if (oz != 0) pt.set(px + k, 0, pz, sand); else pt.set(px, 0, pz + k, sand);
            }
            pt.set(s.x0() + 1, 1, s.z0() + 1, b(Blocks.LANTERN));
        }
        if (level >= 3) {
            for (int x : new int[] {s.x0(), s.x1()}) for (int z : new int[] {s.z0(), s.z1()}) pt.set(x, top + 1, z, b(Blocks.SEA_LANTERN));
            pt.fill(s.x1() - 1, top + 1, s.z0() + 1, s.x1() - 1, top + 4, s.z0() + 1, b(Blocks.LIGHTNING_ROD));
            for (int x = s.x0(); x <= s.x1(); x += 2) pt.set(x, top + 1, s.z1(), b(Blocks.IRON_BARS));
            pt.fill(s.x0() + 1, top + 1, s.z1() - 1, s.x0() + 1, top + 3, s.z1() - 1, b(Blocks.COBBLESTONE_WALL));
            pt.set(s.x0() + 1, top + 4, s.z1() - 1, b(Camp.bannerOf(f)));
        }
    }

    private static void flatRoof(Painter pt, Camp.Site s, int y, BlockState roof, int level, Random r) {
        for (int x = s.x0() - 1; x <= s.x1() + 1; x++) for (int z = s.z0() - 1; z <= s.z1() + 1; z++) {
            if (level == 1 && r.nextDouble() < 0.18) continue; // дыры в кровле у залатанных руин
            pt.set(x, y, z, roof);
        }
    }

    /** Двускатная крыша из ступеней вдоль оси X. */
    private static void pitchedRoof(Painter pt, Camp.Site s, int y, Block stairs, Block ridge, int level, Random r) {
        int half = (s.d() + 1) / 2;
        for (int k = 0; k < half; k++) {
            int zn = s.z0() - 1 + k, zs = s.z1() + 1 - k;
            for (int x = s.x0() - 1; x <= s.x1() + 1; x++) {
                if (level == 1 && r.nextDouble() < 0.12) continue;
                if (zn < zs) {
                    pt.set(x, y + k, zn, stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                    pt.set(x, y + k, zs, stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                } else {
                    pt.set(x, y + k, zn, b(ridge));
                }
            }
        }
    }

    // ---------------------------------------------------------------- здания

    public static void paint(Painter pt, Camp.Site s, int level, int f) {
        Random r = new Random(s.cx() * 92821L + s.cz() * 68917L + level);
        switch (s.landmark()) {
            case "sidorovich" -> sidorovich(pt, s, level, f, r);
            case "village" -> village(pt, s, level, f, r);
            case "bar100" -> bar100(pt, s, level, f, r);
            case "rostok", "hangar", "depot" -> hangar(pt, s, level, f, r);
            case "factory" -> factory(pt, s, level, f, r);
            case "army_barracks", "military_hq" -> barracksBlock(pt, s, level, f, r);
            case "science_bunker" -> scienceBunker(pt, s, level, f, r);
            case "sarcophagus" -> sarcophagus(pt, s, level, f, r);
            case "nii" -> nii(pt, s, level, f, r);
            case "rail_depot" -> railDepot(pt, s, level, f, r);
            case "checkpoint" -> checkpoint(pt, s, level, f, r);
            case "polesie" -> polesie(pt, s, level, f, r);
            case "prometheus" -> prometheus(pt, s, level, f, r);
            case "lazurny" -> lazurny(pt, s, level, f, r);
            case "hospital" -> hospital(pt, s, level, f, r);
            case "panel" -> panel(pt, s, level, f, r);
            default -> { }
        }
    }

    /** Бункер Сидоровича: бетонная коробка, засыпанная землёй, железная дверь, вытяжка, внутри прилавок. */
    private static void sidorovich(Painter pt, Camp.Site s, int level, int f, Random r) {
        hollow(pt, s.x0(), -1, s.z0(), s.x1(), 3, s.z1(), b(Blocks.GRAY_CONCRETE));
        pt.fill(s.x0() + 1, -1, s.z0() + 1, s.x1() - 1, -1, s.z1() - 1, b(Blocks.POLISHED_ANDESITE));
        // земляной холм поверх
        for (int x = s.x0() - 1; x <= s.x1() + 1; x++) for (int z = s.z0() - 1; z <= s.z1() + 1; z++) {
            pt.set(x, 4, z, b(Blocks.GRASS_BLOCK));
            boolean edge = x == s.x0() - 1 || x == s.x1() + 1 || z == s.z0() - 1 || z == s.z1() + 1;
            if (edge) pt.fill(x, 0, z, x, 3, z, b(r.nextBoolean() ? Blocks.DIRT : Blocks.COARSE_DIRT));
        }
        door(pt, s, 0, 2);
        pt.fill(s.cx(), 0, s.z1() + 1, s.cx(), 1, s.z1() + 1, b(Blocks.AIR));
        pt.set(s.cx() - 1, 1, s.z1() + 1, b(Blocks.IRON_BLOCK));
        pt.set(s.cx() + 1, 1, s.z1() + 1, b(Blocks.IRON_BLOCK));
        // прилавок и ящики
        for (int x = s.x0() + 2; x <= s.x1() - 2; x++) pt.set(x, 0, s.z0() + 2, b(Blocks.SPRUCE_PLANKS));
        pt.set(s.x0() + 1, 0, s.z0() + 1, b(Blocks.BARREL));
        pt.set(s.x1() - 1, 0, s.z0() + 1, b(Blocks.BARREL));
        pt.set(s.x1() - 1, 1, s.z0() + 1, b(Blocks.BARREL));
        pt.set(s.cx(), 3, s.cz(), b(Blocks.LANTERN));
        pt.fill(s.x1() - 1, 5, s.z0() + 1, s.x1() - 1, 6, s.z0() + 1, b(Blocks.IRON_BARS));
        upgrades(pt, s, level, f, 4);
    }

    /** Дом деревни новичков: кирпичный цоколь, дощатые стены, двускатная крыша, печная труба. */
    private static void village(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.SPRUCE_PLANKS));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), 0, s.z1(), b(Blocks.BRICKS));
        hollow(pt, s.x0(), 1, s.z0(), s.x1(), 2, s.z1(), b(level >= 2 ? Blocks.SPRUCE_PLANKS : Blocks.STRIPPED_SPRUCE_LOG));
        windows(pt, s, 1, level, r);
        door(pt, s, 0, 2);
        pitchedRoof(pt, s, 3, level >= 3 ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, level, r);
        pt.fill(s.x1() - 1, 3, s.z0() + 1, s.x1() - 1, 5, s.z0() + 1, b(Blocks.BRICKS));
        Camp.bedAt(pt, s.x0() + 1, s.z0() + 1, Direction.SOUTH);
        pt.set(s.x1() - 1, 0, s.z1() - 1, b(Blocks.FURNACE));
        upgrades(pt, s, Math.min(level, 2), f, 4);
    }

    /** Бар «100 рентген»: двухэтажное кирпичное здание завода, вывеска над входом, внутри стойка. */
    private static void bar100(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.POLISHED_ANDESITE));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), 7, s.z1(), b(Blocks.BRICKS));
        pt.fill(s.x0() + 1, 4, s.z0() + 1, s.x1() - 1, 4, s.z1() - 1, b(Blocks.SPRUCE_PLANKS));
        windows(pt, s, 1, level, r);
        windows(pt, s, 6, level, r);
        door(pt, s, 1, 3);
        flatRoof(pt, s, 8, b(Blocks.SMOOTH_STONE_SLAB), level, r);
        // вывеска «100 рентген»: жёлто-красная полоса над входом
        for (int x = s.cx() - 3; x <= s.cx() + 3; x++) pt.set(x, 3, s.z1() + 1, b(((x & 1) == 0) ? Blocks.YELLOW_CONCRETE : Blocks.RED_CONCRETE));
        // стойка бармена и столы
        for (int x = s.x0() + 2; x <= s.x1() - 2; x++) pt.set(x, 0, s.z0() + 2, b(Blocks.DARK_OAK_PLANKS));
        for (int x = s.x0() + 2; x <= s.x1() - 2; x += 4) {
            pt.set(x, 0, s.z1() - 3, b(Blocks.OAK_FENCE));
            pt.set(x, 1, s.z1() - 3, b(Blocks.OAK_PRESSURE_PLATE));
        }
        pt.set(s.x0() + 1, 0, s.z0() + 1, b(Blocks.BARREL));
        pt.set(s.x1() - 1, 0, s.z0() + 1, b(Blocks.JUKEBOX));
        pt.set(s.cx(), 3, s.cz(), b(Blocks.LANTERN));
        // лестница на второй этаж
        for (int k = 0; k < 4; k++) pt.set(s.x1() - 1, k, s.z1() - 1 - k, b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        pt.fill(s.x1() - 1, 4, s.z1() - 5, s.x1() - 1, 4, s.z1() - 2, b(Blocks.AIR));
        upgrades(pt, s, level, f, 8);
    }

    /** Ангар: высокие ржавые стены из терракоты, арочная крыша, широкие ворота. */
    private static void hangar(Painter pt, Camp.Site s, int level, int f, Random r) {
        int h = s.height() - 3;
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.GRAVEL));
        BlockState wall = b(level >= 3 ? Blocks.LIGHT_GRAY_CONCRETE : level == 2 ? Blocks.TERRACOTTA : Blocks.BROWN_TERRACOTTA);
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), h, s.z1(), wall);
        for (int x = s.x0(); x <= s.x1(); x += 3) pt.fill(x, 0, s.z0(), x, h, s.z0(), b(Blocks.IRON_BLOCK));
        door(pt, s, 2, h - 1);
        // арка: ступени по краям, ровно в середине
        for (int x = s.x0() - 1; x <= s.x1() + 1; x++) {
            for (int z = s.z0(); z <= s.z1(); z++) {
                int dz = Math.min(z - s.z0(), s.z1() - z);
                int y = h + 1 + Math.min(2, dz / 2);
                if (level == 1 && r.nextDouble() < 0.15) continue;
                pt.set(x, y, z, b(level >= 2 ? Blocks.IRON_BLOCK : Blocks.RAW_IRON_BLOCK));
            }
        }
        pt.set(s.x0() + 1, 0, s.z0() + 1, b(Blocks.ANVIL));
        pt.set(s.x0() + 3, 0, s.z0() + 1, b(Blocks.BLAST_FURNACE));
        pt.set(s.x0() + 5, 0, s.z0() + 1, b(Blocks.SMITHING_TABLE));
        pt.set(s.x0() + 1, 0, s.z0() + 3, b(Blocks.GRINDSTONE));
        pt.set(s.x0() + 2, 0, s.z1() - 1, b(ModBlocks.RUSTY_BARREL.get()));
        pt.set(s.x0() + 3, 0, s.z1() - 1, b(ModBlocks.TIRES.get()));
        upgrades(pt, s, level, f, h + 3);
    }

    /** Завод в Тёмной долине: цех из кирпича и бетона, высокая труба. */
    private static void factory(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.STONE));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), 6, s.z1(), b(Blocks.BRICKS));
        for (int x = s.x0(); x <= s.x1(); x += 4) pt.fill(x, 0, s.z0(), x, 6, s.z0(), b(Blocks.STONE_BRICKS));
        windows(pt, s, 2, level, r);
        windows(pt, s, 4, level, r);
        door(pt, s, 1, 3);
        flatRoof(pt, s, 7, b(Blocks.STONE_BRICK_SLAB), level, r);
        // труба
        int tx = s.x0() + 2, tz = s.z0() + 2;
        pt.fill(tx - 1, 0, tz - 1, tx + 1, 22, tz + 1, b(Blocks.BRICKS));
        pt.fill(tx, 1, tz, tx, 22, tz, b(Blocks.AIR));
        pt.fill(tx - 1, 18, tz - 1, tx + 1, 18, tz + 1, b(Blocks.RED_CONCRETE));
        pt.set(s.x1() - 2, 0, s.z0() + 2, b(Blocks.FURNACE));
        pt.set(s.x1() - 3, 0, s.z0() + 2, b(Blocks.BLAST_FURNACE));
        upgrades(pt, s, level, f, 7);
    }

    /** Казарма военных складов и штаб «Периметра»: двухэтажный бетонный блок с флагштоком. */
    private static void barracksBlock(Painter pt, Camp.Site s, int level, int f, Random r) {
        int h = s.height() - 2;
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.POLISHED_ANDESITE));
        BlockState wall = b(level >= 2 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.ANDESITE);
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), h, s.z1(), wall);
        pt.fill(s.x0() + 1, 3, s.z0() + 1, s.x1() - 1, 3, s.z1() - 1, b(Blocks.SMOOTH_STONE));
        for (int x = s.x0(); x <= s.x1(); x++) pt.set(x, 3, s.z1(), b(Blocks.GREEN_TERRACOTTA));
        windows(pt, s, 1, level, r);
        windows(pt, s, 5, level, r);
        door(pt, s, 0, 2);
        flatRoof(pt, s, h + 1, b(Blocks.SMOOTH_STONE_SLAB), level, r);
        for (int k = 0; k < 3; k++) pt.set(s.x1() - 1, k, s.z1() - 1 - k, b(Blocks.STONE_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        pt.fill(s.x1() - 1, 3, s.z1() - 4, s.x1() - 1, 3, s.z1() - 2, b(Blocks.AIR));
        pt.set(s.cx(), 0, s.z0() + 1, b(Blocks.CARTOGRAPHY_TABLE));
        pt.set(s.cx() - 1, 0, s.z0() + 1, b(Blocks.LECTERN));
        // флагшток
        pt.fill(s.cx(), 0, s.z1() + 3, s.cx(), 6, s.z1() + 3, b(Blocks.IRON_BARS));
        pt.set(s.cx(), 7, s.z1() + 3, b(Camp.bannerOf(f)));
        upgrades(pt, s, level, f, h + 1);
    }

    /** Бункер учёных на Янтаре: полуутопленный белый бункер с люком и антеннами. */
    private static void scienceBunker(Painter pt, Camp.Site s, int level, int f, Random r) {
        hollow(pt, s.x0(), -1, s.z0(), s.x1(), 3, s.z1(), b(Blocks.WHITE_CONCRETE));
        pt.fill(s.x0() + 1, -1, s.z0() + 1, s.x1() - 1, -1, s.z1() - 1, b(Blocks.SMOOTH_QUARTZ));
        for (int x = s.x0(); x <= s.x1(); x++) { pt.set(x, 1, s.z0(), b(Blocks.CYAN_TERRACOTTA)); pt.set(x, 1, s.z1(), b(Blocks.CYAN_TERRACOTTA)); }
        flatRoof(pt, s, 4, b(Blocks.SMOOTH_STONE), Math.max(2, level), r);
        door(pt, s, 0, 2);
        pt.set(s.cx() - 1, 1, s.z1(), b(Blocks.IRON_BARS));
        pt.set(s.cx() + 1, 1, s.z1(), b(Blocks.IRON_BARS));
        pt.set(s.x0() + 1, 0, s.z0() + 1, b(Blocks.BREWING_STAND));
        pt.set(s.x0() + 2, 0, s.z0() + 1, b(Blocks.ENCHANTING_TABLE));
        pt.set(s.x1() - 1, 0, s.z0() + 1, b(Blocks.CAULDRON));
        Camp.bedAt(pt, s.x1() - 2, s.z1() - 2, Direction.WEST);
        pt.set(s.cx(), 3, s.cz(), b(Blocks.SEA_LANTERN));
        // антенны и спутниковая тарелка
        pt.fill(s.x0() + 1, 5, s.z0() + 1, s.x0() + 1, 9, s.z0() + 1, b(Blocks.IRON_BARS));
        pt.fill(s.x1() - 2, 5, s.z0() + 2, s.x1() - 2, 6, s.z0() + 2, b(Blocks.IRON_BARS));
        pt.set(s.x1() - 2, 7, s.z0() + 2, b(Blocks.DAYLIGHT_DETECTOR));
        upgrades(pt, s, level, f, 4);
    }

    /** Саркофаг ЧАЭС: ступенчатая серая громада с контрфорсами и вентиляционной трубой в красно-белую полосу. */
    private static void sarcophagus(Painter pt, Camp.Site s, int level, int f, Random r) {
        int h = 14;
        for (int step = 0; step < 4; step++) {
            int inset = step * 2, y0 = step * 3, y1 = Math.min(h, y0 + 5);
            hollow(pt, s.x0() + inset / 2, y0, s.z0() + inset, s.x1() - inset / 2, y1, s.z1(), b(step % 2 == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));
        }
        pt.fill(s.x0(), h, s.z0() + 6, s.x1(), h, s.z1(), b(Blocks.GRAY_CONCRETE));
        // контрфорсы
        for (int x = s.x0(); x <= s.x1(); x += 4) pt.fill(x, 0, s.z1() + 1, x, 8, s.z1() + 1, b(Blocks.DEEPSLATE_TILES));
        door(pt, s, 1, 3);
        // вентиляционная труба
        int tx = s.x1() - 3, tz = s.z0() + 2;
        for (int y = 0; y <= 34; y++) {
            BlockState st = b((y / 4) % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
            pt.set(tx, y, tz, st);
            pt.set(tx + 1, y, tz, st);
            pt.set(tx, y, tz + 1, st);
            pt.set(tx + 1, y, tz + 1, st);
        }
        for (int y = 6; y <= 30; y += 8) pt.fill(tx - 1, y, tz - 1, tx + 2, y, tz + 2, b(Blocks.IRON_BARS));
        pt.set(s.cx(), 0, s.cz(), b(Blocks.CRYING_OBSIDIAN));
        pt.set(s.cx(), 1, s.cz(), b(Blocks.END_ROD));
    }

    /** НИИ Агропром: трёхэтажный кирпичный институт с бетонным каркасом и выбитыми окнами. */
    private static void nii(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.POLISHED_ANDESITE));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), 9, s.z1(), b(Blocks.BRICKS));
        for (int y : new int[] {3, 6}) {
            pt.fill(s.x0() + 1, y, s.z0() + 1, s.x1() - 1, y, s.z1() - 1, b(Blocks.SMOOTH_STONE));
            for (int x = s.x0(); x <= s.x1(); x++) { pt.set(x, y, s.z0(), b(Blocks.LIGHT_GRAY_CONCRETE)); pt.set(x, y, s.z1(), b(Blocks.LIGHT_GRAY_CONCRETE)); }
        }
        for (int y : new int[] {1, 4, 7}) windows(pt, s, y, level, r);
        door(pt, s, 1, 2);
        flatRoof(pt, s, 10, b(Blocks.SMOOTH_STONE_SLAB), level, r);
        // лестница по этажам
        for (int k = 0; k < 3; k++) pt.set(s.x0() + 1 + k, k, s.z0() + 1, b(Blocks.STONE_STAIRS).setValue(StairBlock.FACING, Direction.EAST));
        pt.fill(s.x0() + 1, 3, s.z0() + 1, s.x0() + 3, 3, s.z0() + 1, b(Blocks.AIR));
        for (int k = 0; k < 3; k++) pt.set(s.x0() + 1 + k, 3 + k, s.z0() + 2, b(Blocks.STONE_STAIRS).setValue(StairBlock.FACING, Direction.EAST));
        pt.fill(s.x0() + 1, 6, s.z0() + 2, s.x0() + 3, 6, s.z0() + 2, b(Blocks.AIR));
        for (int k = 0; k < 4; k++) pt.set(s.x1() - 2, 1 + k * 2, s.cz(), b(Blocks.BOOKSHELF));
        pt.set(s.cx(), 4, s.cz(), b(Blocks.LECTERN));
        upgrades(pt, s, level, f, 10);
    }

    /** Ремонтное депо Дикой территории: цех и вагоны на путях. */
    private static void railDepot(Painter pt, Camp.Site s, int level, int f, Random r) {
        hangar(pt, s, level, f, r);
        int z = s.z1() + 3;
        for (int x = s.x0() - 1; x <= s.x1() + 1; x++) pt.set(x, -1, z, b(Blocks.GRAVEL));
        for (int x = s.x0() - 1; x <= s.x1() + 1; x++) pt.set(x, 0, z, b(Blocks.RAIL));
    }

    /** КПП блокпоста: будка, шлагбаум, бетонные блоки. */
    private static void checkpoint(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.POLISHED_ANDESITE));
        hollow(pt, s.x0(), 0, s.z0(), s.x0() + 3, 2, s.z1(), b(level >= 2 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.SPRUCE_PLANKS));
        pt.set(s.x0() + 3, 1, s.cz(), b(Blocks.GLASS_PANE));
        pt.fill(s.x0() - 1, 3, s.z0() - 1, s.x0() + 4, 3, s.z1() + 1, b(Blocks.SMOOTH_STONE_SLAB));
        pt.fill(s.x0() + 1, 0, s.z1(), s.x0() + 1, 1, s.z1(), b(Blocks.AIR));
        // шлагбаум в красно-белую полосу
        pt.set(s.x0() + 4, 0, s.cz(), b(Blocks.COBBLESTONE_WALL));
        for (int x = s.x0() + 4; x <= s.x1() + 2; x++) pt.set(x, 1, s.cz(), b(((x & 1) == 0) ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE));
        for (int z = s.z0(); z <= s.z1(); z += 2) pt.set(s.x1() + 3, 0, z, b(Blocks.SMOOTH_STONE));
        upgrades(pt, s, level, f, 3);
    }

    // ---------------------------------------------------------------- Припять

    /** Многоэтажная коробка: этажи по 3 блока, полосы между этажами, окна, лестница внутри. */
    private static void tower(Painter pt, Camp.Site s, int level, Random r, int floors, Block wall, Block band) {
        int h = floors * 3;
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.SMOOTH_STONE));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), h, s.z1(), b(wall));
        for (int fl = 1; fl <= floors; fl++) {
            int y = fl * 3;
            pt.fill(s.x0() + 1, y, s.z0() + 1, s.x1() - 1, y, s.z1() - 1, b(Blocks.SMOOTH_STONE));
            for (int x = s.x0(); x <= s.x1(); x++) { pt.set(x, y, s.z0(), b(band)); pt.set(x, y, s.z1(), b(band)); }
            windows(pt, s, y - 2, level, r);
        }
        windows(pt, s, h - 1, level, r);
        // лестничная клетка
        for (int fl = 0; fl < floors; fl++) {
            int y = fl * 3;
            pt.fill(s.x1() - 2, y + 3, s.z0() + 1, s.x1() - 1, y + 3, s.z0() + 2, b(Blocks.AIR));
            for (int k = 0; k < 3; k++) pt.set(s.x1() - 1, y + k, s.z0() + 3 - k, b(Blocks.STONE_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        }
        flatRoof(pt, s, h + 1, b(Blocks.SMOOTH_STONE_SLAB), Math.max(level, 2), r);
        door(pt, s, 0, 2);
    }

    /** Гостиница «Полесье»: высокий белый корпус с жёлтой полосой на крыше. */
    private static void polesie(Painter pt, Camp.Site s, int level, int f, Random r) {
        tower(pt, s, level, r, 6, Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE);
        for (int x = s.x0(); x <= s.x1(); x++) pt.set(x, 19, s.z1() + 1, b(Blocks.YELLOW_CONCRETE));
        pt.fill(s.cx() - 3, 0, s.z1() + 1, s.cx() + 3, 0, s.z1() + 3, b(Blocks.SMOOTH_STONE_SLAB));
        upgrades(pt, s, level, f, 19);
    }

    /** Кинотеатр «Прометей»: низкий зал с мозаичным фасадом. */
    private static void prometheus(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.POLISHED_ANDESITE));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), 6, s.z1(), b(Blocks.LIGHT_GRAY_CONCRETE));
        for (int x = s.x0(); x <= s.x1(); x++) for (int y = 2; y <= 6; y++)
            pt.set(x, y, s.z1(), b(((x * 3 + y * 5) % 7 < 3) ? Blocks.ORANGE_TERRACOTTA : (x + y) % 2 == 0 ? Blocks.RED_TERRACOTTA : Blocks.YELLOW_TERRACOTTA));
        flatRoof(pt, s, 7, b(Blocks.SMOOTH_STONE_SLAB), level, r);
        door(pt, s, 2, 2);
        for (int x = s.x0() + 2; x <= s.x1() - 2; x += 2) for (int z = s.z0() + 2; z <= s.z1() - 3; z += 2)
            pt.set(x, 0, z, b(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        pt.fill(s.x0() + 2, 2, s.z0() + 1, s.x1() - 2, 4, s.z0() + 1, b(Blocks.WHITE_WOOL));
        upgrades(pt, s, level, f, 7);
    }

    /** Бассейн «Лазурный»: стеклянные стены, пустая чаша бассейна с остатками воды. */
    private static void lazurny(Painter pt, Camp.Site s, int level, int f, Random r) {
        pt.fill(s.x0(), -1, s.z0(), s.x1(), -1, s.z1(), b(Blocks.WHITE_CONCRETE));
        hollow(pt, s.x0(), 0, s.z0(), s.x1(), 6, s.z1(), b(Blocks.LIGHT_GRAY_CONCRETE));
        for (int x = s.x0() + 1; x < s.x1(); x++) for (int y = 1; y <= 5; y++) {
            BlockState g = r.nextDouble() < (level == 1 ? 0.5 : 0.15) ? b(Blocks.AIR) : b(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            pt.set(x, y, s.z0(), g);
            pt.set(x, y, s.z1(), g);
        }
        pt.fill(s.x0() + 2, -3, s.z0() + 2, s.x1() - 2, -1, s.z1() - 2, b(Blocks.CYAN_TERRACOTTA));
        pt.fill(s.x0() + 3, -2, s.z0() + 3, s.x1() - 3, -1, s.z1() - 3, b(Blocks.AIR));
        pt.fill(s.x0() + 3, -2, s.z0() + 3, s.x0() + 5, -2, s.z1() - 3, b(Blocks.WATER));
        for (int k = 0; k < 4; k++) pt.set(s.x1() - 2, k, s.z0() + 1, b(Blocks.LADDER).setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
        pt.fill(s.x1() - 3, 3, s.z0() + 1, s.x1() - 3, 6, s.z0() + 1, b(Blocks.IRON_BARS));
        flatRoof(pt, s, 7, b(Blocks.SMOOTH_STONE_SLAB), level, r);
        door(pt, s, 1, 2);
        upgrades(pt, s, level, f, 7);
    }

    /** Больница (медсанчасть №126): белый корпус с красным крестом. */
    private static void hospital(Painter pt, Camp.Site s, int level, int f, Random r) {
        tower(pt, s, level, r, 4, Blocks.WHITE_TERRACOTTA, Blocks.LIGHT_GRAY_CONCRETE);
        int cx = s.cx(), y = 9;
        for (int k = -2; k <= 2; k++) {
            pt.set(cx + k, y, s.z1(), b(Blocks.RED_CONCRETE));
            pt.set(cx, y + k, s.z1(), b(Blocks.RED_CONCRETE));
        }
        Camp.bedAt(pt, s.x0() + 1, s.z0() + 1, Direction.SOUTH);
        Camp.bedAt(pt, s.x0() + 3, s.z0() + 1, Direction.SOUTH);
        upgrades(pt, s, level, f, 13);
    }

    /** Жилая панелька района (казарма группировки в городе). */
    private static void panel(Painter pt, Camp.Site s, int level, int f, Random r) {
        tower(pt, s, level, r, 4, r.nextBoolean() ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_TERRACOTTA, Blocks.GRAY_CONCRETE);
        Camp.bedAt(pt, s.x0() + 1, s.z0() + 1, Direction.SOUTH);
        upgrades(pt, s, Math.min(level, 2), f, 13);
    }

    // ---------------------------------------------------------------- отдельные ориентиры

    /** Радар «Дуга»: решётчатая стена антенн. */
    public static void duga(Painter pt) {
        for (int x = -30; x <= 30; x++) {
            for (int y = 0; y <= 42; y++) {
                boolean frame = x % 6 == 0 || y % 7 == 0 || (x + y) % 7 == 0;
                if (frame) pt.set(x, y, 0, b(y < 3 ? Blocks.POLISHED_BLACKSTONE : Blocks.IRON_BARS));
            }
            if (x % 6 == 0) {
                pt.fill(x, 0, -4, x, 0, 4, b(Blocks.POLISHED_BLACKSTONE));
                for (int y = 0; y <= 28; y += 4) { pt.set(x, y, -1, b(Blocks.CHAIN)); pt.set(x, y, 1, b(Blocks.CHAIN)); }
            }
        }
        pt.fill(-3, 0, 6, 3, 3, 10, b(Blocks.GRAY_CONCRETE));
        pt.fill(-2, 0, 7, 2, 2, 9, b(Blocks.AIR));
    }

    /** Колесо обозрения Припяти. */
    public static void ferrisWheel(Painter pt) {
        int R = 14, cy = 18;
        for (int a = 0; a < 360; a += 3) {
            double t = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(t) * R), y = cy + (int) Math.round(Math.sin(t) * R);
            pt.set(x, y, 0, b(Blocks.YELLOW_CONCRETE));
            if (a % 30 == 0) {
                for (int k = 0; k < R; k++) pt.set((int) Math.round(Math.cos(t) * k), cy + (int) Math.round(Math.sin(t) * k), 0, b(Blocks.IRON_BARS));
                pt.fill(x, y - 2, -1, x, y - 1, 1, b(Blocks.YELLOW_TERRACOTTA));
            }
        }
        for (int k = 0; k <= cy; k++) {
            int dx = k * 6 / cy;
            pt.set(-6 + dx, k, -2, b(Blocks.IRON_BLOCK));
            pt.set(6 - dx, k, -2, b(Blocks.IRON_BLOCK));
            pt.set(-6 + dx, k, 2, b(Blocks.IRON_BLOCK));
            pt.set(6 - dx, k, 2, b(Blocks.IRON_BLOCK));
        }
    }

    /** Градирня ЧАЭС: гиперболоид из бетонных колец. */
    public static void coolingTower(Painter pt) {
        for (int y = 0; y <= 40; y++) {
            double t = (y - 26) / 26.0;
            double rad = 10 + 6 * t * t;
            for (int a = 0; a < 360; a += 2) {
                double ang = Math.toRadians(a);
                pt.set((int) Math.round(Math.cos(ang) * rad), y, (int) Math.round(Math.sin(ang) * rad), b(y % 9 == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));
            }
        }
    }

    /** Руины завода «Янтарь» над входом в X-16. */
    public static void yantarFactory(Painter pt, Random r) {
        for (int i = 0; i < 4; i++) {
            int x0 = -18 + i * 10, z0 = -8;
            int h = 5 + r.nextInt(5);
            for (int x = x0; x <= x0 + 7; x++) for (int z = z0; z <= z0 + 12; z++) {
                boolean edge = x == x0 || x == x0 + 7 || z == z0 || z == z0 + 12;
                if (!edge) continue;
                int hh = (int) (h * (0.4 + 0.6 * r.nextDouble()));
                pt.fill(x, 0, z, x, hh, z, b(r.nextDouble() < 0.3 ? Blocks.CRACKED_STONE_BRICKS : Blocks.BRICKS));
            }
        }
        pt.fill(-14, 0, 14, -12, 18, 16, b(Blocks.BRICKS));
    }

    // ---------------------------------------------------------------- v1.14: новые места

    /** Разорённый дом: стены с проломами, обвалившаяся крыша, пустые окна. */
    private static void ruinHouse(Painter pt, int x0, int z0, int w, int d, Random r, Block wall) {
        for (int x = x0; x <= x0 + w; x++) for (int z = z0; z <= z0 + d; z++) {
            boolean edge = x == x0 || x == x0 + w || z == z0 || z == z0 + d;
            pt.set(x, 0, z, b(edge ? Blocks.COBBLESTONE : r.nextDouble() < 0.3 ? Blocks.COARSE_DIRT : Blocks.SPRUCE_PLANKS));
            if (!edge) continue;
            int h = r.nextDouble() < 0.25 ? r.nextInt(2) : 3;
            for (int y = 1; y <= h; y++) {
                boolean window = y == 2 && (x + z) % 3 == 0;
                pt.set(x, y, z, window ? b(Blocks.AIR) : b(r.nextDouble() < 0.15 ? Blocks.MOSSY_COBBLESTONE : wall));
            }
        }
        // остатки крыши
        for (int x = x0; x <= x0 + w; x++) if (r.nextDouble() < 0.5)
            pt.set(x, 4, z0 + d / 2, b(Blocks.SPRUCE_SLAB));
    }

    /** Собачья деревня: брошенные дома, колодец, кости и норы псов у Периметра. */
    public static void dogVillage(Painter pt, Random r) {
        int[][] houses = {{-20, -16}, {-6, -18}, {9, -15}, {-18, 4}, {8, 6}, {-4, 14}};
        for (int[] h : houses) ruinHouse(pt, h[0], h[1], 6 + r.nextInt(2), 5 + r.nextInt(2), r, r.nextBoolean() ? Blocks.SPRUCE_PLANKS : Blocks.STRIPPED_SPRUCE_LOG);
        // колодец
        pt.fill(-1, 0, -1, 1, 1, 1, b(Blocks.COBBLESTONE));
        pt.set(0, 1, 0, b(Blocks.WATER));
        pt.fill(-1, 2, -1, -1, 3, -1, b(Blocks.SPRUCE_FENCE));
        pt.fill(1, 2, 1, 1, 3, 1, b(Blocks.SPRUCE_FENCE));
        pt.fill(-1, 4, -1, 1, 4, 1, b(Blocks.SPRUCE_SLAB));
        // кости и норы
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(44) - 22, z = r.nextInt(44) - 22;
            pt.set(x, 1, z, b(r.nextDouble() < 0.5 ? Blocks.BONE_BLOCK : Blocks.COARSE_DIRT));
        }
        // покосившийся забор
        for (int x = -24; x <= 24; x++) if (r.nextDouble() < 0.55) pt.set(x, 1, -24, b(Blocks.SPRUCE_FENCE));
        for (int x = -24; x <= 24; x++) if (r.nextDouble() < 0.55) pt.set(x, 1, 24, b(Blocks.SPRUCE_FENCE));
    }

    /** Избушка отшельника: сруб с крыльцом, печной трубой, лавкой (Лесник) или грядкой и сушёными травами (Доктор). */
    public static void hut(Painter pt, boolean doctor) {
        pt.fill(-4, 0, -4, 4, 0, 4, b(Blocks.SPRUCE_PLANKS));
        for (int y = 1; y <= 3; y++) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
            if (!edge) { pt.set(x, y, z, b(Blocks.AIR)); continue; }
            boolean corner = Math.abs(x) == 4 && Math.abs(z) == 4;
            boolean window = y == 2 && (x == 0 || z == 0) && !(z == 4 && x == 0);
            pt.set(x, y, z, corner ? b(Blocks.STRIPPED_SPRUCE_LOG) : window ? b(Blocks.GLASS_PANE) : b(Blocks.SPRUCE_LOG));
        }
        pt.set(0, 1, 4, b(Blocks.AIR));
        pt.set(0, 2, 4, b(Blocks.AIR));
        for (int k = 0; k <= 4; k++) {
            int y = 4 + k / 2;
            for (int x = -5 + k; x <= 5 - k; x++) for (int z = -5; z <= 5; z++) if (Math.abs(x) == 5 - k)
                pt.set(x, y, z, b(Blocks.DARK_OAK_PLANKS));
        }
        pt.fill(-1, 6, -5, 1, 6, 5, b(Blocks.DARK_OAK_SLAB));
        pt.fill(3, 4, -3, 3, 8, -3, b(Blocks.BRICKS));
        pt.set(3, 9, -3, b(Blocks.CAMPFIRE));
        pt.set(-3, 1, -3, b(Blocks.FURNACE));
        pt.set(-3, 1, 3, b(Blocks.RED_BED));
        pt.set(2, 1, 3, b(Blocks.CRAFTING_TABLE));
        if (doctor) {
            for (int x = -3; x <= 3; x++) pt.set(x, 3, -3, b(Blocks.HANGING_ROOTS));
            pt.set(3, 1, 2, b(Blocks.BREWING_STAND));
            for (int x = -3; x <= 3; x++) { pt.set(x, 0, 7, b(Blocks.FARMLAND)); pt.set(x, 1, 7, b(Blocks.POTATOES)); }
        } else {
            pt.fill(-2, 1, 5, 2, 1, 6, b(Blocks.SPRUCE_PLANKS));
            pt.set(-2, 2, 6, b(Blocks.SPRUCE_FENCE));
            pt.set(2, 2, 6, b(Blocks.SPRUCE_FENCE));
            pt.set(3, 1, 2, b(Blocks.BARREL));
        }
    }

    /** Старая церковь на Болотах: кирпичный сруб с луковичным куполом и колокольней. */
    public static void swampChurch(Painter pt) {
        pt.fill(-5, 0, -8, 5, 0, 8, b(Blocks.STONE_BRICKS));
        for (int y = 1; y <= 7; y++) for (int x = -5; x <= 5; x++) for (int z = -8; z <= 8; z++) {
            boolean edge = Math.abs(x) == 5 || Math.abs(z) == 8;
            if (!edge) { pt.set(x, y, z, b(Blocks.AIR)); continue; }
            boolean window = (y == 4 || y == 5) && (z % 4 == 0 || x == 0);
            pt.set(x, y, z, window ? b(Blocks.AIR) : b(y < 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.WHITE_TERRACOTTA));
        }
        pt.set(0, 1, 8, b(Blocks.AIR)); pt.set(0, 2, 8, b(Blocks.AIR)); pt.set(0, 3, 8, b(Blocks.AIR));
        pt.fill(-5, 8, -8, 5, 8, 8, b(Blocks.GREEN_TERRACOTTA));
        // барабан и купол
        pt.fill(-2, 9, -2, 2, 12, 2, b(Blocks.WHITE_TERRACOTTA));
        for (int y = 13; y <= 17; y++) {
            int rr = y <= 14 ? 3 : y == 15 ? 2 : y == 16 ? 1 : 0;
            pt.fill(-rr, y, -rr, rr, y, rr, b(Blocks.GREEN_TERRACOTTA));
        }
        pt.set(0, 18, 0, b(Blocks.GOLD_BLOCK));
        pt.set(0, 19, 0, b(Blocks.IRON_BARS));
        pt.set(0, 20, 0, b(Blocks.IRON_BARS));
        pt.set(-1, 19, 0, b(Blocks.IRON_BARS));
        pt.set(1, 19, 0, b(Blocks.IRON_BARS));
    }

    /** Мёртвый город: пустые пятиэтажки без окон, обрушенный подъезд, ржавые машины. */
    public static void deadCity(Painter pt, Random r) {
        int[][] blocks = {{-28, -20, 22, 9}, {-28, 6, 22, 9}, {4, -20, 10, 26}};
        for (int[] q : blocks) {
            int x0 = q[0], z0 = q[1], w = q[2], d = q[3], h = 12 + r.nextInt(4);
            for (int y = 0; y <= h; y++) for (int x = x0; x <= x0 + w; x++) for (int z = z0; z <= z0 + d; z++) {
                boolean edge = x == x0 || x == x0 + w || z == z0 || z == z0 + d;
                boolean floor = y % 3 == 0;
                if (!edge && !floor) continue;
                if (edge && !floor && (x + z) % 3 == 1 && y % 3 == 2) continue;   // окна
                if (y > h - 3 && r.nextDouble() < 0.4) continue;                       // осыпавшийся верх
                pt.set(x, y, z, b(r.nextDouble() < 0.12 ? Blocks.CRACKED_STONE_BRICKS : edge ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.SMOOTH_STONE));
            }
            // обрушенный подъезд — щебень
            int cx = x0 + w / 2;
            for (int k = 0; k < 18; k++) pt.set(cx + r.nextInt(5) - 2, r.nextInt(3), z0 - 1 - r.nextInt(3), b(Blocks.GRAVEL));
        }
    }

    /** Антенна Выжигателя на пси-поле. */
    public static void scorcherTower(Painter pt) {
        for (int y = 0; y <= 36; y++) {
            int w = Math.max(0, 4 - y / 9);
            for (int dx : new int[] {-w, w}) for (int dz : new int[] {-w, w}) pt.set(dx, y, dz, b(Blocks.IRON_BARS));
        }
        pt.fill(-3, 36, -3, 3, 36, 3, b(Blocks.IRON_BLOCK));
        pt.set(0, 37, 0, b(Blocks.END_ROD));
        pt.set(0, 38, 0, b(Blocks.END_ROD));
        pt.fill(-5, 0, -5, 5, 0, 5, b(Blocks.POLISHED_BLACKSTONE));
    }
}
