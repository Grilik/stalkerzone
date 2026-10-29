package ru.stalkerzone.world.xray;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.stalkerzone.block.ObjPropBlock;

import java.util.Random;

/**
 * Перенос уровня оригинала в чанк при первой загрузке (v1.14, «Кордон 1:1»): точная высота земли и покрытие,
 * вода, постройки вокселями с блоками по материалу X-Ray, подземные помещения (бункер Сидоровича), деревья.
 * Рельеф в целом даёт генератор ({@link XrayTerrain}), здесь — доводка до метра и всё, что на земле.
 */
public final class XrayPlacer {
    private XrayPlacer() {}

    private static BlockState b(Block x) { return x.defaultBlockState(); }

    /** Блок по материалу X-Ray (gamemtl.xr: id материала). */
    static BlockState material(int id, int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        boolean worn = (h & 7) == 0;
        return switch (id) {
            case 2 -> b(Blocks.STRIPPED_SPRUCE_LOG);                                   // wood
            case 3 -> b(worn ? Blocks.ANDESITE : Blocks.LIGHT_GRAY_CONCRETE);           // concrete
            case 4 -> b(Blocks.GRAY_CONCRETE);                                         // asphalt
            case 5 -> b(worn ? Blocks.BROWN_TERRACOTTA : Blocks.IRON_BLOCK);            // metal
            case 6 -> b(Blocks.GLASS_PANE);                                            // glass
            case 12 -> b(Blocks.SMOOTH_STONE);                                         // flooring_tile
            case 13 -> b(worn ? Blocks.BROWN_TERRACOTTA : Blocks.GRAY_TERRACOTTA);      // metal_plate
            case 18 -> b(worn ? Blocks.CRACKED_STONE_BRICKS : Blocks.BRICKS);           // bricks
            case 19 -> b(Blocks.SPRUCE_PLANKS);                                        // wooden_board
            case 34 -> b(Blocks.IRON_BARS);                                            // metal_pipe
            case 48 -> b(Blocks.IRON_BARS);                                            // setka_rabica
            case 49 -> b(Blocks.LIGHT_GRAY_TERRACOTTA);                                // shifer
            case 53 -> b(worn ? Blocks.LIGHT_GRAY_TERRACOTTA : Blocks.WHITE_TERRACOTTA); // stucco
            case 60 -> b(Blocks.GRAY_TERRACOTTA);                                      // tin
            case 81 -> b(Blocks.BROWN_WOOL);                                           // cloth
            case 8 -> b(Blocks.GRAVEL);
            // v1.17: категории по текстурам рендер-геометрии (tools/xray/geo2cform.py)
            case 200 -> b((h & 15) == 0 ? Blocks.GRANITE : (h & 15) == 1 ? Blocks.MUD_BRICKS : Blocks.BRICKS);        // кирпич
            case 201 -> b(worn ? Blocks.LIGHT_GRAY_TERRACOTTA : (h & 15) == 1 ? Blocks.CALCITE : Blocks.WHITE_TERRACOTTA); // штукатурка
            case 202 -> b(worn ? Blocks.ANDESITE : (h & 15) == 1 ? Blocks.POLISHED_ANDESITE : Blocks.LIGHT_GRAY_CONCRETE); // бетон
            case 203 -> b(worn ? Blocks.COBBLESTONE : (h & 3) == 1 ? Blocks.ANDESITE : Blocks.POLISHED_ANDESITE);  // старый бетон
            case 204 -> b(worn ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);                              // доски
            case 205 -> b(Blocks.DARK_OAK_PLANKS);                                                                 // крашеное дерево
            case 206 -> b(Blocks.SPRUCE_LOG);                                                                      // столбы, брёвна
            case 207 -> b(worn ? Blocks.GRAY_TERRACOTTA : Blocks.LIGHT_GRAY_TERRACOTTA);                           // шифер
            case 208 -> b(worn ? Blocks.BROWN_TERRACOTTA : Blocks.RED_TERRACOTTA);                                 // красная кровля
            case 209 -> b((h & 3) == 0 ? Blocks.ORANGE_TERRACOTTA : Blocks.BROWN_TERRACOTTA);                      // ржавый металл
            case 210 -> b(worn ? Blocks.GRAY_TERRACOTTA : (h & 3) == 1 ? Blocks.IRON_BLOCK : Blocks.LIGHT_GRAY_TERRACOTTA); // металл
            case 211 -> b(Blocks.IRON_BARS);                                                                       // решётки, сетка
            case 212 -> b(Blocks.LIGHT_GRAY_STAINED_GLASS);                                                        // стекло
            case 213 -> b(Blocks.DARK_OAK_PLANKS);                                                                 // двери, ворота
            case 214 -> b(switch (h & 7) { case 0, 1 -> Blocks.ORANGE_TERRACOTTA; case 2 -> Blocks.GRAY_TERRACOTTA; case 3 -> Blocks.CYAN_TERRACOTTA; default -> Blocks.BROWN_TERRACOTTA; }); // техника
            case 215 -> b(worn ? Blocks.BROWN_TERRACOTTA : Blocks.GREEN_TERRACOTTA);                               // военная техника
            case 216 -> b(Blocks.BLACK_TERRACOTTA);                                                                // колёса
            case 217 -> b((h & 3) == 0 ? Blocks.GREEN_WOOL : Blocks.BROWN_WOOL);                                   // мешки, брезент, матрасы
            case 219 -> b(worn ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE);                              // плитка
            case 220 -> b(Blocks.PACKED_MUD);                                                                      // линолеум, смола
            case 221 -> b(Blocks.LANTERN);                                                                         // лампы
            case 222 -> b(Blocks.OAK_LOG);                                                                         // пни, брёвна
            case 223 -> b(worn ? Blocks.WHITE_TERRACOTTA : Blocks.YELLOW_TERRACOTTA);                             // жёлтая штукатурка
            case 224 -> b(worn ? Blocks.WHITE_TERRACOTTA : Blocks.ORANGE_TERRACOTTA);                             // охра
            case 225 -> b(worn ? Blocks.WHITE_TERRACOTTA : Blocks.PINK_TERRACOTTA);                               // розовая штукатурка
            case 100 -> b(switch (h & 15) {                                            // скала (tools/xray: наклонный «бетон»)
                case 0, 1, 2 -> Blocks.ANDESITE;
                case 3, 4 -> Blocks.COBBLESTONE;
                case 5 -> Blocks.TUFF;
                case 6 -> Blocks.MOSSY_COBBLESTONE;
                default -> Blocks.STONE;
            });
            default -> b(Blocks.STONE);
        };
    }

    static BlockState surfaceBlock(int code, int x, int z) {
        int h = (x * 31 + z * 17) & 15;
        return switch (code) {
            case LevelRegion.S_DIRT -> b(h < 6 ? Blocks.COARSE_DIRT : Blocks.DIRT);
            case LevelRegion.S_GRAVEL -> b(Blocks.GRAVEL);
            case LevelRegion.S_SAND -> b(Blocks.SAND);
            case LevelRegion.S_ASPHALT -> b(h < 2 ? Blocks.GRAVEL : Blocks.GRAY_CONCRETE_POWDER);
            case LevelRegion.S_DEATH -> b(Blocks.PODZOL);
            default -> b(h == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);
        };
    }

    public static void place(ChunkAccess c, LevelRegion r) {
        ChunkPos cp = c.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // 1. рельеф до метра, покрытие, вода
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = x0 + dx, z = z0 + dz;
            if (!r.contains(x, z)) continue;
            int g = r.groundBlock(x, z);
            int top = c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            for (int y = Math.min(top, g) - 3; y <= Math.max(top, g) + 2; y++) {
                p.set(x, y, z);
                BlockState now = c.getBlockState(p);
                if (y > g) { if (!now.isAir()) ObjPropBlock.safeSet(c, p.immutable(), b(Blocks.AIR)); }
                else if (y == g) ObjPropBlock.safeSet(c, p.immutable(), surfaceBlock(r.surface(x, z), x, z));
                else if (y >= g - 2) ObjPropBlock.safeSet(c, p.immutable(), b(Blocks.DIRT));
                else if (now.isAir() || !now.getFluidState().isEmpty()) ObjPropBlock.safeSet(c, p.immutable(), b(Blocks.STONE));
            }
            int w = r.water(x, z);
            if (w != Integer.MIN_VALUE && w > g) {
                ObjPropBlock.safeSet(c, new BlockPos(x, g, z), b(Blocks.MUD));
                for (int y = g + 1; y <= w; y++) ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(Blocks.WATER));
            }
        }
        // 2. подземные помещения
        int[] a = r.air(cp.x, cp.z);
        for (int i = 0; i + 2 < a.length; i += 3) ObjPropBlock.safeSet(c, new BlockPos(a[i], a[i + 1], a[i + 2]), b(Blocks.AIR));
        // 3. постройки
        int[] v = r.voxels(cp.x, cp.z);
        for (int i = 0; i + 3 < v.length; i += 4)
            ObjPropBlock.safeSet(c, new BlockPos(v[i], v[i + 1], v[i + 2]), material(v[i + 3], v[i], v[i + 1], v[i + 2]));
        doors(c, v);
        // 4. деревья
        // кроны свисают в соседние чанки: ставим и деревья соседей, но только свою часть (у дерева свой посев)
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++)
            for (int[] t : r.trees(cp.x + ox, cp.z + oz))
                tree(c, t[0], r.groundBlock(t[0], t[2]) + 1, t[2], t[3], t[4], t[5], new Random(t[0] * 341873128712L + t[2] * 132897987541L));
    }

    /**
     * Двери (v1.17): полотно двери из игры (текстуры door_*) — столбик из 2–3 вокселей в плоскости стены.
     * Нижние два ставим настоящей дверью, повёрнутой по стене; что выше — доски над проёмом.
     */
    private static void doors(ChunkAccess c, int[] v) {
        java.util.Set<Long> d = new java.util.HashSet<>();
        for (int i = 0; i + 3 < v.length; i += 4) if (v[i + 3] == 213) d.add(BlockPos.asLong(v[i], v[i + 1], v[i + 2]));
        if (d.isEmpty()) return;
        ChunkPos cp = c.getPos();
        for (long k : d) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (d.contains(BlockPos.asLong(x, y - 1, z)) || !d.contains(BlockPos.asLong(x, y + 1, z))) continue;
            if (d.contains(BlockPos.asLong(x, y + 3, z))) continue;   // ворота и высокие створки — оставить досками
            // плоскость двери: соседи по X (стена или полотно) — значит дверь смотрит на север/юг
            boolean alongX = solid(c, cp, x - 1, y, z) || solid(c, cp, x + 1, y, z);
            boolean alongZ = solid(c, cp, x, y, z - 1) || solid(c, cp, x, y, z + 1);
            if (alongX == alongZ) continue;   // не понять, как стоит
            net.minecraft.core.Direction f = alongX ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.EAST;
            BlockState lo = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, f);
            ObjPropBlock.safeSet(c, new BlockPos(x, y, z), lo.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            ObjPropBlock.safeSet(c, new BlockPos(x, y + 1, z), lo.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
    }

    private static boolean solid(ChunkAccess c, ChunkPos cp, int x, int y, int z) {
        if ((x >> 4) != cp.x || (z >> 4) != cp.z) return false;
        BlockState s = c.getBlockState(new BlockPos(x, y, z));
        return !s.isAir() && !s.canBeReplaced();
    }

    /**
     * Дерево: порода из игры (v1.17: 1 сосна, 2 ель, 3 берёза, 4 лиственное, 5 сухое; 0 — не известна, наугад),
     * высота и ширина кроны — как у дерева в игре.
     */
    private static void tree(ChunkAccess c, int x, int y, int z, int h, int rad, int species, Random r) {
        int roll = r.nextInt(10);
        // kind: <3 берёза, 3..4 хвойное (конусом), иначе лиственное
        int kind = switch (species) { case 1, 2 -> 3; case 3 -> 0; case 4 -> roll < 2 ? 0 : 6; default -> roll; };
        Block log = species == 1 ? Blocks.SPRUCE_LOG : kind < 3 ? Blocks.BIRCH_LOG : kind < 5 ? Blocks.SPRUCE_LOG : (roll & 1) == 0 ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG;
        Block leaves = kind < 3 ? Blocks.BIRCH_LEAVES : kind < 5 ? Blocks.SPRUCE_LEAVES : (roll & 1) == 0 ? Blocks.DARK_OAK_LEAVES : Blocks.OAK_LEAVES;
        boolean dead = species == 5 || (species == 0 && r.nextInt(12) == 0);
        h = Math.max(4, Math.min(22, h));
        rad = Math.max(1, Math.min(4, rad));
        ChunkPos cp = c.getPos();
        for (int k = 0; k < h; k++) set(c, cp, x, y + k, z, b(log));
        if (dead) return;
        BlockState lv = b(leaves).setValue(LeavesBlock.PERSISTENT, true);
        int crown = Math.max(2, h / 2);
        for (int dy = h - crown; dy <= h + 1; dy++) {
            int rr = dy > h - 1 ? Math.max(1, rad - 1) : rad;
            if (kind >= 3 && kind < 5) rr = Math.max(1, rad * (h + 1 - dy) / Math.max(1, crown));   // сосна — конусом
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                if (dx * dx + dz * dz > rr * rr + 1 || (dx == 0 && dz == 0 && dy < h) || r.nextInt(7) == 0) continue;
                set(c, cp, x + dx, y + dy, z + dz, lv);
            }
        }
    }

    private static void set(ChunkAccess c, ChunkPos cp, int x, int y, int z, BlockState s) {
        if ((x >> 4) != cp.x || (z >> 4) != cp.z) return;
        BlockPos p = new BlockPos(x, y, z);
        if (!c.getBlockState(p).isAir() && !c.getBlockState(p).canBeReplaced()) return;
        ObjPropBlock.safeSet(c, p, s);
    }
}
