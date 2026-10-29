package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.stalkerzone.block.ModBlocks;

import java.util.List;
import java.util.Random;

/**
 * Облик Зоны по чанкам: Рыжий лес, сухие деревья, кучи хлама на Свалке, остовы машин, ржавые бочки, покрышки,
 * плиты перекрытий, столбы ЛЭП. Делается один раз на чанк (метка в самом нижнем блоке), не трогает лагеря.
 */
public final class WorldDecorator {
    private static int[][] centers;

    private WorldDecorator() {}

    private static synchronized int[][] centers() {
        if (centers == null) {
            List<ru.stalkerzone.sim.Node> nodes = ru.stalkerzone.sim.ZoneScenario.create(0).nodes;
            centers = new int[nodes.size()][];
            for (int i = 0; i < nodes.size(); i++) centers[i] = new int[] {nodes.get(i).x * ZoneBounds.SCALE, nodes.get(i).z * ZoneBounds.SCALE};
        }
        return centers;
    }

    private static boolean nearCamp(int x, int z) {
        for (int[] c : centers()) if (Math.abs(x - c[0]) < 75 && Math.abs(z - c[1]) < 75) return true;
        return false;
    }

    public static void decorate(ChunkAccess chunk, ZoneBounds b, net.minecraft.server.level.ServerLevel level) {
        ChunkPos cp = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        BlockPos marker = new BlockPos(cp.getMinBlockX(), minY, cp.getMinBlockZ());
        if (chunk.getBlockState(marker).is(Blocks.REINFORCED_DEEPSLATE)) return;
        int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
        ru.stalkerzone.block.ObjPropBlock.safeSet(chunk, marker, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        if (!b.inside(cp.getMinBlockX() - 8, cp.getMinBlockZ() - 8) || !b.inside(cp.getMaxBlockX() + 8, cp.getMaxBlockZ() + 8)) return;
        // v1.14: уровень оригинала 1:1 — свой рельеф, постройки и деревья, наш декор не нужен
        ru.stalkerzone.world.xray.LevelRegion lr = ru.stalkerzone.world.xray.LevelRegions.atChunk(cp.x, cp.z);
        if (lr != null) { ru.stalkerzone.world.xray.XrayPlacer.place(chunk, lr); ru.stalkerzone.world.xray.LinkRoads.decorate(chunk); return; }
        ru.stalkerzone.world.xray.LinkRoads.decorate(chunk);
        Random r = new Random(cp.toLong() * 341873128712L + 132897987541L);
        int biome = ZoneBiomes.index(cx, cz);
        // v1.14: дороги между локациями, столбы с проводами, ЛЭП, газопроводы (доходят и до лагерей)
        Roads.decorate(chunk, level, new Random(r.nextLong()), biome == ZoneBiomes.PRIPYAT);
        if (nearCamp(cx, cz)) return;
        if (biome == ZoneBiomes.PRIPYAT) {
            city(chunk, cp, r);
            cityProps(chunk, cp, r);
            return;
        }
        switch (biome) {
            case ZoneBiomes.RED_FOREST -> {
                int n = 3 + r.nextInt(4);
                for (int i = 0; i < n; i++) redTree(chunk, cp, r);
            }
            case ZoneBiomes.GARBAGE -> {
                if (r.nextDouble() < 0.6) junkPile(chunk, cp, r);
                if (r.nextDouble() < 0.12) car(chunk, cp, r);
            }
            case ZoneBiomes.PSI, ZoneBiomes.CHAES -> {
                if (r.nextDouble() < 0.35) deadTree(chunk, cp, r);
                if (r.nextDouble() < 0.15) slabs(chunk, cp, r);
            }
            case ZoneBiomes.SWAMPS -> {
                // v1.14: Болота — протоки и бочаги, камыш, топь, сухие деревья
                marsh(chunk, cp, r);
                int n = 1 + r.nextInt(3);
                for (int i = 0; i < n; i++) pond(chunk, cp, r);
                if (r.nextDouble() < 0.4) deadTree(chunk, cp, r);
            }
            case ZoneBiomes.YANTAR, ZoneBiomes.AGROPROM, ZoneBiomes.DARK, ZoneBiomes.RADAR -> {
                if (r.nextDouble() < 0.45) deadTree(chunk, cp, r);
            }
            default -> {
                if (r.nextDouble() < 0.15) deadTree(chunk, cp, r);
            }
        }
        double p = r.nextDouble();
        if (p < 0.035) car(chunk, cp, r);
        else if (p < 0.075) barrels(chunk, cp, r);
        else if (p < 0.095) slabs(chunk, cp, r);
        else if (p < 0.115) tires(chunk, cp, r);
        if (r.nextDouble() < 0.015) pole(chunk, cp, r);
        // v1.13: места крушения вертолётов, брошенные стоянки сталкеров, армейские позиции, вагоны на запасных путях
        double q = r.nextDouble();
        if (q < 0.005) crash(chunk, cp, r);
        else if (q < 0.017) stalkerStop(chunk, cp, r);
        else if (q < 0.025 && (biome == ZoneBiomes.RADAR || biome == ZoneBiomes.PSI || biome == ZoneBiomes.ARMY || biome == ZoneBiomes.AGROPROM))
            armyPosition(chunk, cp, r);
        else if (q < 0.029 && (biome == ZoneBiomes.AGROPROM || biome == ZoneBiomes.GARBAGE || biome == ZoneBiomes.KORDON))
            wagons(chunk, cp, r);
        // v1.14: водокачки и ангары колхозов, экскаваторы на Свалке
        double w = r.nextDouble();
        if (w < 0.006 && (biome == ZoneBiomes.KORDON || biome == ZoneBiomes.AGROPROM || biome == ZoneBiomes.GARBAGE || biome == ZoneBiomes.WILD))
            landmark(chunk, cp, r, "prop_water_tower");
        else if (w < 0.010 && (biome == ZoneBiomes.KORDON || biome == ZoneBiomes.AGROPROM || biome == ZoneBiomes.ARMY || biome == ZoneBiomes.WILD))
            landmark(chunk, cp, r, "prop_hangar");
        else if (w < 0.016 && biome == ZoneBiomes.GARBAGE) {
            int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8, y = ground(chunk, x, z);
            BlockState s = Dressing.vehicle("veh_excavator", Dressing.dir(r), r, 1);
            if (s != null && dry(chunk, x, y, z)) ru.stalkerzone.block.ObjPropBlock.placeInChunk(chunk, new BlockPos(x, y, z), s);
        }
        if ((biome == ZoneBiomes.PSI || biome == ZoneBiomes.CHAES || biome == ZoneBiomes.RADAR) && r.nextDouble() < 0.06) {
            int x = cp.getMinBlockX() + 2 + r.nextInt(12), z = cp.getMinBlockZ() + 2 + r.nextInt(12), y = ground(chunk, x, z);
            if (dry(chunk, x, y, z)) Dressing.chunkProp(chunk, x, y, z, "prop_sign_rad", r);
        }
    }

    // ---------------------------------------------------------------- v1.13: места и объекты

    /** Крупный объект (водокачка, ангар): ставим так, чтобы хитбокс целиком уместился в чанке, и не на дороге. */
    private static void landmark(ChunkAccess c, ChunkPos cp, Random r, String name) {
        int x = cp.getMinBlockX() + 7, z = cp.getMinBlockZ() + 6, y = ground(c, x, z);
        if (!dry(c, x, y, z) || Roads.roadDist(x, z) < 12) return;
        BlockState s = Dressing.prop(name, Direction.EAST);
        if (s != null) ru.stalkerzone.block.ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
    }

    /** Разбитый Ми-8 или Ми-24 с обломками вокруг: обгоревший, винт сломан. */
    private static void crash(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8, y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        BlockState s = Dressing.vehicle(r.nextBoolean() ? "veh_mi8" : "veh_mi24", r.nextBoolean() ? Direction.EAST : Direction.SOUTH, r, 1 + r.nextInt(2));
        if (s == null) return;
        ru.stalkerzone.block.ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
        for (int i = 0; i < 4; i++) {
            int dx = cp.getMinBlockX() + 1 + r.nextInt(14), dz = cp.getMinBlockZ() + 1 + r.nextInt(14), dy = ground(c, dx, dz);
            if (Math.abs(dx - x) < 3 && Math.abs(dz - z) < 3 || !dry(c, dx, dy, dz)) continue;
            Dressing.chunkProp(c, dx, dy, dz, i == 0 ? "prop_ammo_crates" : "prop_rubble", r);
        }
    }

    /** Брошенная стоянка сталкеров: кострище, палатка, бревно, ящики. */
    private static void stalkerStop(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 7, z = cp.getMinBlockZ() + 7, y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        Dressing.chunkProp(c, x, y, z, "prop_campfire", r);
        int ys = ground(c, x, z + 2);
        if (dry(c, x, ys, z + 2)) Dressing.chunkProp(c, x, ys, z + 2, "prop_log_seat", r);
        int xt = x + 4, yt = ground(c, xt, z);
        if (dry(c, xt, yt, z)) Dressing.chunkProp(c, xt, yt, z, "prop_tent", r);
        int xa = x - 3, ya = ground(c, xa, z - 2);
        if (dry(c, xa, ya, z - 2)) Dressing.chunkProp(c, xa, ya, z - 2, r.nextBoolean() ? "prop_pallet_sacks" : "prop_barrel", r);
    }

    /** Брошенная армейская позиция: ежи, колючка, палатка, грибок, ящики, знак. */
    private static void armyPosition(ChunkAccess c, ChunkPos cp, Random r) {
        String[] kinds = {"prop_hedgehog", "prop_hedgehog", "prop_concertina", "prop_army_tent", "prop_watch_booth", "prop_ammo_crates", "prop_sign_stop", "prop_sandbags"};
        for (int i = 0; i < 6; i++) {
            int x = cp.getMinBlockX() + 2 + r.nextInt(12), z = cp.getMinBlockZ() + 2 + r.nextInt(12), y = ground(c, x, z);
            if (dry(c, x, y, z)) Dressing.chunkProp(c, x, y, z, kinds[i == 0 ? 3 : r.nextInt(kinds.length)], r);
        }
        if (r.nextDouble() < 0.6) {   // брошенная техника
            String[] tech = {"veh_brdm2", "veh_t72", "veh_zil131", "veh_uaz452", "veh_btr", "veh_gaz66"};
            int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8, y = ground(c, x, z);
            BlockState s = Dressing.vehicle(tech[r.nextInt(tech.length)], Dressing.dir(r), r, 1 + r.nextInt(2));
            if (s != null && dry(c, x, y, z)) ru.stalkerzone.block.ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
        }
    }

    /** Ржавые товарные вагоны на заросшем запасном пути. */
    private static void wagons(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 8, z = cp.getMinBlockZ() + 8, y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        BlockState s = Dressing.vehicle(r.nextInt(3) == 0 ? "veh_tank_wagon" : "veh_wagon", r.nextBoolean() ? Direction.EAST : Direction.SOUTH, r, 1);
        if (s != null) ru.stalkerzone.block.ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
    }

    /** Улица Припяти: остановки, будки, ларьки, клумбы, скамейки, фонари, стенды, мусорные баки. */
    private static void cityProps(ChunkAccess c, ChunkPos cp, Random r) {
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        String[] street = {"prop_street_lamp", "prop_bench", "prop_trash_bin", "prop_flower_bed", "prop_phone_booth", "prop_poster_board"};
        for (int i = 0; i < 2; i++) {
            int x = x0 + 2 + r.nextInt(12), z = z0 + 2, y = ground(c, x, z);
            if (dry(c, x, y, z)) Dressing.chunkProp(c, x, y, z, street[r.nextInt(street.length)], r);
        }
        double v = r.nextDouble();
        if (v < 0.12) {
            int x = x0 + 13, z = z0 + 7, y = ground(c, x, z);
            if (dry(c, x, y, z)) Dressing.chunkProp(c, x, y, z, "prop_bus_stop", r);
        } else if (v < 0.18) {
            int x = x0 + 13, z = z0 + 13, y = ground(c, x, z);
            if (dry(c, x, y, z)) Dressing.chunkProp(c, x, y, z, "prop_kiosk", r);
        }
    }

    /** Сглаженный шум 0..1 для островков топи. */
    private static double marshNoise(int x, int z) {
        double v = Math.sin(x * 0.11 + Math.sin(z * 0.07) * 2.1) + Math.sin(z * 0.13 + Math.sin(x * 0.05) * 1.7)
                + 0.5 * Math.sin((x + z) * 0.23);
        return (v + 2.5) / 5.0;
    }

    /**
     * Топь вместо озера: где вода по сторону островов, поднимаем илистое дно до уровня воды и кладём траву —
     * получаются кочки и острова, между ними протоки (база «Чистого неба» стоит на острове).
     */
    private static void marsh(ChunkAccess c, ChunkPos cp, Random r) {
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = cp.getMinBlockX() + dx, z = cp.getMinBlockZ() + dz;
            int top = ground(c, x, z) - 1;
            if (!c.getBlockState(new BlockPos(x, top, z)).is(Blocks.WATER)) continue;
            double n = marshNoise(x, z);
            if (n < 0.47) continue;
            int bottom = top;
            while (bottom > top - 8 && c.getBlockState(new BlockPos(x, bottom, z)).is(Blocks.WATER)) bottom--;
            for (int y = bottom + 1; y < top; y++) set(c, x, y, z, b(Blocks.MUD));
            set(c, x, top, z, b(n > 0.6 ? Blocks.GRASS_BLOCK : Blocks.MUD));
            if (n < 0.52 && r.nextDouble() < 0.5) {
                set(c, x, top + 1, z, b(Blocks.SUGAR_CANE));
                if (r.nextBoolean()) set(c, x, top + 2, z, b(Blocks.SUGAR_CANE));
            } else if (n > 0.6 && r.nextDouble() < 0.25) {
                set(c, x, top + 1, z, b(Blocks.GRASS));
            }
        }
    }

    /** Бочаг: мелкая вода с илистым дном, камыш по берегу. */
    private static void pond(ChunkAccess c, ChunkPos cp, Random r) {
        int cx = cp.getMinBlockX() + 3 + r.nextInt(10), cz = cp.getMinBlockZ() + 3 + r.nextInt(10);
        int rad = 2 + r.nextInt(3);
        int y0 = ground(c, cx, cz) - 1;
        for (int dx = -rad - 1; dx <= rad + 1; dx++) for (int dz = -rad - 1; dz <= rad + 1; dz++) {
            int x = cx + dx, z = cz + dz;
            if ((x >> 4) != cp.x || (z >> 4) != cp.z) continue;
            double d = Math.hypot(dx, dz) + r.nextDouble() * 0.8;
            int top = ground(c, x, z) - 1;
            if (Math.abs(top - y0) > 2) continue;
            if (d <= rad) {
                for (int k = 1; k <= 3; k++) set(c, x, top + k, z, b(Blocks.AIR));
                set(c, x, y0, z, b(Blocks.WATER));
                set(c, x, y0 - 1, z, b(Blocks.MUD));
                if (top > y0) for (int y = y0 + 1; y <= top; y++) set(c, x, y, z, b(Blocks.AIR));
            } else if (d <= rad + 1.2 && r.nextDouble() < 0.5) {
                set(c, x, top, z, b(Blocks.MUD));
                if (r.nextDouble() < 0.6) {
                    set(c, x, top + 1, z, b(Blocks.SUGAR_CANE));
                    if (r.nextBoolean()) set(c, x, top + 2, z, b(Blocks.SUGAR_CANE));
                }
            }
        }
    }

    // ---------------------------------------------------------------- помощники

    private static int ground(ChunkAccess c, int x, int z) {
        return c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1;
    }

    private static boolean dry(ChunkAccess c, int x, int y, int z) {
        BlockState below = c.getBlockState(new BlockPos(x, y - 1, z));
        return below.getFluidState().isEmpty() && !below.is(Blocks.WATER) && !below.isAir();
    }

    private static void set(ChunkAccess c, int x, int y, int z, BlockState s) {
        ChunkPos cp = c.getPos();
        if (x < cp.getMinBlockX() || x > cp.getMaxBlockX() || z < cp.getMinBlockZ() || z > cp.getMaxBlockZ()) return;
        ru.stalkerzone.block.ObjPropBlock.safeSet(c, new BlockPos(x, y, z), s);
    }

    private static BlockState b(Block b) { return b.defaultBlockState(); }

    // ---------------------------------------------------------------- деревья

    /** Дерево Рыжего леса: тонкий ствол, ржавая листва шапкой. */
    private static void redTree(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 3 + r.nextInt(10), z = cp.getMinBlockZ() + 3 + r.nextInt(10);
        int y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        int h = 6 + r.nextInt(5);
        BlockState log = b(r.nextDouble() < 0.3 ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG);
        for (int k = 0; k < h; k++) set(c, x, y + k, z, log);
        BlockState leaves = ModBlocks.RED_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        boolean dead = r.nextDouble() < 0.25;
        if (dead) return;
        for (int dy = h - 4; dy <= h; dy++) {
            int rad = dy >= h - 1 ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (dx == 0 && dz == 0 && dy < h) continue;
                if (Math.abs(dx) == rad && Math.abs(dz) == rad && r.nextBoolean()) continue;
                if (r.nextDouble() < 0.15) continue;
                set(c, x + dx, y + dy, z + dz, leaves);
            }
        }
    }

    /** Сухое дерево: голый ствол с парой сучьев. */
    private static void deadTree(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 3 + r.nextInt(10), z = cp.getMinBlockZ() + 3 + r.nextInt(10);
        int y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        int h = 4 + r.nextInt(5);
        BlockState log = b(r.nextBoolean() ? Blocks.STRIPPED_OAK_LOG : Blocks.STRIPPED_SPRUCE_LOG);
        for (int k = 0; k < h; k++) set(c, x, y + k, z, log);
        for (int i = 0; i < 2; i++) {
            Direction d = Direction.from2DDataValue(r.nextInt(4));
            int by = y + h - 2 - i * 2;
            BlockState branch = log.setValue(RotatedPillarBlock.AXIS, d.getAxis());
            set(c, x + d.getStepX(), by, z + d.getStepZ(), branch);
            if (r.nextBoolean()) set(c, x + d.getStepX() * 2, by + 1, z + d.getStepZ() * 2, branch);
        }
    }

    // ---------------------------------------------------------------- Припять

    /** Квартал Припяти: улицы по краям чанка, в середине дом, магазин или двор. */
    private static void city(ChunkAccess c, ChunkPos cp, Random r) {
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        // улицы: асфальт с трещинами и травой
        for (int i = 0; i < 16; i++) for (int k = 0; k < 2; k++) {
            road(c, x0 + i, z0 + k, r);
            road(c, x0 + k, z0 + i, r);
        }
        if (r.nextDouble() < 0.12) car(c, cp, r);
        double v = r.nextDouble();
        if (v < 0.45) block(c, cp, r, 5, 12, 7);
        else if (v < 0.68) block(c, cp, r, 9, 10, 6);
        else if (v < 0.82) block(c, cp, r, 2, 11, 9);
        else yard(c, cp, r);
    }

    private static void road(ChunkAccess c, int x, int z, Random r) {
        int y = ground(c, x, z) - 1;
        BlockState top = c.getBlockState(new BlockPos(x, y, z));
        if (!top.getFluidState().isEmpty()) return;
        double v = r.nextDouble();
        set(c, x, y, z, b(v < 0.08 ? Blocks.COARSE_DIRT : v < 0.13 ? Blocks.GRASS_BLOCK : v < 0.55 ? Blocks.GRAY_CONCRETE : Blocks.BLACK_CONCRETE));
        set(c, x, y + 1, z, b(Blocks.AIR));
    }

    /** Жилой дом: этажи по 3 блока, полосы плит, окна (часть выбита), балконы, плоская крыша, обвалы. */
    private static void block(ChunkAccess c, ChunkPos cp, Random r, int floors, int w, int d) {
        int x0 = cp.getMinBlockX() + 3, z0 = cp.getMinBlockZ() + 3 + (9 - d) / 2;
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        int y0 = ground(c, x0 + w / 2, z0 + d / 2);
        if (!dry(c, x0 + w / 2, y0, z0 + d / 2)) return;
        int h = floors * 3;
        Block wall = r.nextDouble() < 0.6 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_TERRACOTTA;
        Block band = r.nextBoolean() ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_BLUE_TERRACOTTA;
        boolean ruined = r.nextDouble() < 0.25;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            for (int y = y0 - 4; y < y0; y++) set(c, x, y, z, b(Blocks.STONE)); // фундамент
            set(c, x, y0 - 1, z, b(Blocks.SMOOTH_STONE));
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            for (int y = 0; y <= h; y++) {
                int fy = y % 3;
                if (ruined && x > x1 - 4 && y > h / 2 && r.nextDouble() < 0.7) continue; // обрушенный угол
                if (edge) {
                    boolean window = fy == 1 && ((x - x0) % 3 == 1 || (z - z0) % 3 == 1) && !(x == x0 && z == z0) && !(x == x1 && z == z1);
                    BlockState st = fy == 0 ? b(band) : window ? (r.nextDouble() < 0.3 ? b(Blocks.GLASS_PANE) : b(Blocks.AIR)) : b(wall);
                    if (!window && r.nextDouble() < 0.03) st = b(Blocks.AIR);
                    set(c, x, y0 + y, z, st);
                } else if (fy == 0 && y > 0) {
                    set(c, x, y0 + y, z, b(Blocks.SMOOTH_STONE));
                } else {
                    set(c, x, y0 + y, z, b(Blocks.AIR));
                }
            }
            if (!ruined || x <= x1 - 4) set(c, x, y0 + h + 1, z, edge ? b(Blocks.SMOOTH_STONE_SLAB) : b(Blocks.AIR));
        }
        // подъезд и лестница на этажи
        int dx = x0 + w / 2;
        set(c, dx, y0, z1, b(Blocks.AIR));
        set(c, dx, y0 + 1, z1, b(Blocks.AIR));
        for (int fl = 0; fl < floors; fl++) {
            int y = y0 + fl * 3;
            set(c, x0 + 1, y + 3, z0 + 1, b(Blocks.AIR));
            set(c, x0 + 1, y + 3, z0 + 2, b(Blocks.AIR));
            for (int k = 0; k < 3; k++) set(c, x0 + 1, y + k, z0 + 3 - k, b(Blocks.STONE_STAIRS).setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
        }
        // балконы с южной стороны: 3D-балкон с ограждением и хламом, иначе плита с решёткой
        boolean has3d = Dressing.prop("prop_balcony", Direction.SOUTH) != null;
        for (int fl = 1; fl < floors; fl++) for (int x = x0 + 2; x < x1 - 1; x += 3) {
            if (has3d) {
                if (r.nextDouble() < 0.6 && !(ruined && x > x1 - 4 && fl * 3 > h / 2))
                    ru.stalkerzone.block.ObjPropBlock.placeInChunk(c, new BlockPos(x, y0 + fl * 3, z1 + 1), Dressing.prop("prop_balcony", Direction.SOUTH));
                continue;
            }
            if (r.nextDouble() < 0.5) set(c, x, y0 + fl * 3, z1 + 1, b(Blocks.SMOOTH_STONE_SLAB));
            if (r.nextDouble() < 0.5) set(c, x, y0 + fl * 3 + 1, z1 + 1, b(Blocks.IRON_BARS));
        }
        // крыша: вентшахты и антенны
        if (!ruined) for (int k = 0; k < 2 + r.nextInt(2); k++) {
            int rx = x0 + 2 + r.nextInt(Math.max(1, w - 4)), rz = z0 + 2 + r.nextInt(Math.max(1, d - 4));
            Dressing.chunkProp(c, rx, y0 + h + 1, rz, k == 0 || r.nextBoolean() ? "prop_roof_vent" : "prop_tv_antenna", r);
        }
        if (floors >= 9 && r.nextBoolean()) for (int k = 1; k <= 4; k++) set(c, x0 + 2, y0 + h + k, z0 + 2, b(Blocks.IRON_BARS));
    }

    /** Двор: деревья, скамейки, качели, мусор. */
    private static void yard(ChunkAccess c, ChunkPos cp, Random r) {
        for (int i = 0; i < 3; i++) {
            int x = cp.getMinBlockX() + 4 + r.nextInt(8), z = cp.getMinBlockZ() + 4 + r.nextInt(8), y = ground(c, x, z);
            if (!dry(c, x, y, z)) continue;
            int h = 5 + r.nextInt(3);
            for (int k = 0; k < h; k++) set(c, x, y + k, z, b(Blocks.BIRCH_LOG));
            BlockState leaves = b(Blocks.BIRCH_LEAVES).setValue(LeavesBlock.PERSISTENT, true);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = h - 3; dy <= h; dy++) {
                if (Math.abs(dx) + Math.abs(dz) > 3 || (dx == 0 && dz == 0 && dy < h) || r.nextDouble() < 0.25) continue;
                set(c, x + dx, y + dy, z + dz, leaves);
            }
        }
        int bx = cp.getMinBlockX() + 5, bz = cp.getMinBlockZ() + 12, by = ground(c, bx, bz);
        for (int k = 0; k < 3; k++) set(c, bx + k, by, bz, b(Blocks.SPRUCE_STAIRS).setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
        int sx = cp.getMinBlockX() + 10, sz = cp.getMinBlockZ() + 6, sy = ground(c, sx, sz);
        // v1.14: детская площадка — качели, карусель, горка (ржавые, облезлые)
        String[] play = {"prop_swing", "prop_carousel", "prop_slide"};
        if (r.nextDouble() < 0.7 && dry(c, sx, sy, sz) && Dressing.chunkProp(c, sx, sy, sz, play[r.nextInt(3)], r)) {
            int px = cp.getMinBlockX() + 12, pz = cp.getMinBlockZ() + 10, py = ground(c, px, pz);
            if (r.nextBoolean() && dry(c, px, py, pz)) Dressing.chunkProp(c, px, py, pz, play[r.nextInt(3)], r);
            if (r.nextBoolean()) barrels(c, cp, r);
            return;
        }
        set(c, sx, sy, sz, b(Blocks.IRON_BARS));
        set(c, sx, sy + 1, sz, b(Blocks.IRON_BARS));
        set(c, sx + 2, sy, sz, b(Blocks.IRON_BARS));
        set(c, sx + 2, sy + 1, sz, b(Blocks.IRON_BARS));
        set(c, sx, sy + 2, sz, b(Blocks.CHAIN));
        set(c, sx + 1, sy + 2, sz, b(Blocks.IRON_BARS));
        set(c, sx + 2, sy + 2, sz, b(Blocks.CHAIN));
        if (r.nextBoolean()) barrels(c, cp, r);
    }

    // ---------------------------------------------------------------- пропсы

    /** Куча хлама Свалки: гравий, щебень, ржавые детали, бочки. */
    private static void junkPile(ChunkAccess c, ChunkPos cp, Random r) {
        int x0 = cp.getMinBlockX() + 4 + r.nextInt(8), z0 = cp.getMinBlockZ() + 4 + r.nextInt(8);
        int rad = 2 + r.nextInt(3);
        Block[] junk = {Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.RAW_IRON_BLOCK, Blocks.GRAVEL};
        for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > rad) continue;
            int x = x0 + dx, z = z0 + dz, y = ground(c, x, z);
            int hh = (int) Math.round((rad - d) * 0.8) + (r.nextDouble() < 0.3 ? 1 : 0);
            for (int k = 0; k < hh; k++) set(c, x, y + k, z, b(junk[r.nextInt(junk.length)]));
            if (hh > 0 && r.nextDouble() < 0.12) set(c, x, y + hh, z, b(Blocks.IRON_BARS));
        }
        int y = ground(c, x0 + rad + 1, z0);
        if (!Dressing.chunkProp(c, x0 + rad + 1, y, z0, "prop_barrel_lying", r)) set(c, x0 + rad + 1, y, z0, b(ModBlocks.RUSTY_BARREL.get()));
        int yr = ground(c, x0 - rad - 1, z0 + 1);
        Dressing.chunkProp(c, x0 - rad - 1, yr, z0 + 1, r.nextBoolean() ? "prop_rubble" : "prop_gas_lying", r);
        String[] house = {"prop_fridge_lying", "prop_bathtub", "prop_trash_bags", "prop_radiator", "prop_fridge", "prop_trash_bags"};
        for (int i = 0; i < 2; i++) {
            int x = x0 + r.nextInt(2 * rad + 1) - rad, z = z0 + rad + 1 + r.nextInt(2), yy = ground(c, x, z);
            if (dry(c, x, yy, z)) Dressing.chunkProp(c, x, yy, z, house[r.nextInt(house.length)], r);
        }
    }

    /** Остов машины: ржавый кузов, пустые окна, колёса. */
    private static void car(ChunkAccess c, ChunkPos cp, Random r) {
        int x0 = cp.getMinBlockX() + 4 + r.nextInt(6), z0 = cp.getMinBlockZ() + 4 + r.nextInt(5);
        boolean alongX = r.nextBoolean();
        int len = 5, wid = 3;
        int y = ground(c, x0 + 1, z0 + 1);
        if (!dry(c, x0 + 1, y, z0 + 1)) return;
        // округлая 3D-модель; если её нет — старый остов из блоков
        int cx8 = cp.getMinBlockX() + 8, cz8 = cp.getMinBlockZ() + 8, y8 = ground(c, cx8, cz8);
        if (dry(c, cx8, y8, cz8) && Dressing.chunkCar(c, cx8, y8, cz8, r, r.nextInt(6) == 0)) return;
        Block body = r.nextDouble() < 0.5 ? Blocks.BROWN_TERRACOTTA : r.nextBoolean() ? Blocks.ORANGE_TERRACOTTA : Blocks.CYAN_TERRACOTTA;
        for (int i = 0; i < len; i++) for (int j = 0; j < wid; j++) {
            int x = alongX ? x0 + i : x0 + j, z = alongX ? z0 + j : z0 + i;
            boolean wheel = (i == 0 || i == len - 1) && (j == 0 || j == wid - 1);
            set(c, x, y, z, wheel ? b(Blocks.BLACK_CONCRETE) : b(body));
            if (i >= 1 && i <= 3) {
                boolean window = j != 1 || i == 2;
                set(c, x, y + 1, z, window ? b(Blocks.AIR) : b(body));
                if (i == 2) set(c, x, y + 1, z, b(Blocks.IRON_BARS));
                set(c, x, y + 2, z, r.nextDouble() < 0.2 ? b(Blocks.AIR) : b(body));
            }
        }
    }

    private static void barrels(ChunkAccess c, ChunkPos cp, Random r) {
        int x0 = cp.getMinBlockX() + 3 + r.nextInt(10), z0 = cp.getMinBlockZ() + 3 + r.nextInt(10);
        int n = 2 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            int x = x0 + r.nextInt(3) - 1, z = z0 + r.nextInt(3) - 1, y = ground(c, x, z);
            if (!dry(c, x, y, z)) continue;
            String[] kinds = {"prop_barrel", "prop_barrel", "prop_barrel_blue", "prop_barrel_green", "prop_barrel_lying", "prop_gas_cylinder", "prop_barrel_rad"};
            if (Dressing.chunkProp(c, x, y, z, kinds[r.nextInt(kinds.length)], r)) continue;
            set(c, x, y, z, b(ModBlocks.RUSTY_BARREL.get()));
            if (r.nextDouble() < 0.25) set(c, x, y + 1, z, b(ModBlocks.RUSTY_BARREL.get()));
        }
    }

    private static void tires(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 3 + r.nextInt(10), z = cp.getMinBlockZ() + 3 + r.nextInt(10), y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        if (Dressing.chunkProp(c, x, y, z, r.nextBoolean() ? "prop_tire_stack" : "prop_tire", r)) {
            if (r.nextBoolean()) Dressing.chunkProp(c, x + 1, ground(c, x + 1, z), z, "prop_tire", r);
            if (r.nextInt(3) == 0) Dressing.chunkProp(c, x, ground(c, x, z + 2), z + 2, "prop_cable_spool", r);
            return;
        }
        int h = 1 + r.nextInt(3);
        for (int k = 0; k < h; k++) set(c, x, y + k, z, b(ModBlocks.TIRES.get()));
        if (r.nextBoolean()) set(c, x + 1, ground(c, x + 1, z), z, b(ModBlocks.TIRES.get()));
    }

    /** Бетонные плиты перекрытий, сваленные стопкой и наискось. */
    private static void slabs(ChunkAccess c, ChunkPos cp, Random r) {
        int x0 = cp.getMinBlockX() + 3 + r.nextInt(6), z0 = cp.getMinBlockZ() + 3 + r.nextInt(6);
        int y = ground(c, x0, z0);
        if (!dry(c, x0, y, z0)) return;
        int layers = 1 + r.nextInt(3);
        for (int l = 0; l < layers; l++) {
            for (int i = 0; i < 5; i++) for (int j = 0; j < 2; j++) {
                set(c, x0 + i + l, y + l, z0 + j, b(Blocks.SMOOTH_STONE_SLAB).setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
        }
        for (int i = 0; i < 4; i++) set(c, x0 + i, y + i / 2, z0 + 3, b(Blocks.SMOOTH_STONE_SLAB).setValue(SlabBlock.TYPE, (i & 1) == 0 ? SlabType.BOTTOM : SlabType.TOP));
        set(c, x0 + 2, y, z0 + 2, b(Blocks.IRON_BARS));
        int yp = ground(c, x0 + 2, z0 + 6);
        if (z0 + 7 <= c.getPos().getMaxBlockZ()) Dressing.chunkProp(c, x0 + 2, yp, z0 + 6, r.nextBoolean() ? "prop_concrete_pipe" : "prop_fbs", r);
    }

    /** Покосившийся деревянный столб ЛЭП с перекладиной и обрывками проводов. */
    private static void pole(ChunkAccess c, ChunkPos cp, Random r) {
        int x = cp.getMinBlockX() + 7, z = cp.getMinBlockZ() + 7, y = ground(c, x, z);
        if (!dry(c, x, y, z)) return;
        if (r.nextBoolean() && Dressing.chunkProp(c, x, y, z, "prop_power_pole", r)) return;
        BlockState log = b(Blocks.STRIPPED_SPRUCE_LOG);
        for (int k = 0; k < 8; k++) set(c, x, y + k, z, log);
        for (int dx = -2; dx <= 2; dx++) if (dx != 0) set(c, x + dx, y + 7, z, b(Blocks.SPRUCE_FENCE));
        for (int k = 1; k <= 3; k++) {
            set(c, x - 2, y + 7 - k, z, b(Blocks.CHAIN));
            if (r.nextBoolean()) set(c, x + 2, y + 7 - k, z, b(Blocks.CHAIN));
        }
    }
}
