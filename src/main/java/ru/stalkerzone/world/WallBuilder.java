package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Ставит в чанке забор и невидимую стену по {@link ZoneBounds}. Повторный вызов ничего не портит. */
public final class WallBuilder {
    /** Высота решётки над основанием. */
    private static final int BARS_HEIGHT = 4;
    /** Шаг столбов забора, блоков. */
    private static final int POST_STEP = 8;

    private WallBuilder() {}

    public static void build(ChunkAccess chunk, ZoneBounds b) {
        ChunkPos cp = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        boolean changed = false;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx;
                int z = cp.getMinBlockZ() + lz;
                ZoneBounds.Kind kind = b.kind(x, z);
                if (kind == ZoneBounds.Kind.NONE) continue;

                if (kind == ZoneBounds.Kind.BARRIER) {
                    if (chunk.getBlockState(p.set(x, minY, z)).is(Blocks.BARRIER)) continue; // уже стоит
                    for (int y = minY; y < maxY; y++) chunk.setBlockState(p.set(x, y, z), barrier, false);
                    changed = true;
                } else {
                    int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz); // первый свободный блок над дном
                    if (chunk.getBlockState(p.set(x, floor, z)).is(Blocks.STONE_BRICKS)) continue; // уже стоит
                    boolean post = Math.floorMod(x + z, POST_STEP) == 0;
                    chunk.setBlockState(p.set(x, floor - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), false);
                    chunk.setBlockState(p.set(x, floor, z), Blocks.STONE_BRICKS.defaultBlockState(), false);
                    BlockState bars = bars(b, x, z);
                    for (int i = 1; i <= BARS_HEIGHT + 1; i++) {
                        BlockState s;
                        if (post) s = Blocks.COBBLESTONE_WALL.defaultBlockState();
                        else if (i <= BARS_HEIGHT) s = bars;
                        else continue;
                        chunk.setBlockState(p.set(x, floor + i, z), s, false);
                    }
                    changed = true;
                }
            }
        }
        if (changed) chunk.setUnsaved(true);
    }

    /** Решётка, соединённая с соседними колоннами забора (при установке без обновления формы связи надо задать вручную). */
    private static BlockState bars(ZoneBounds b, int x, int z) {
        BlockState s = Blocks.IRON_BARS.defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (b.kind(x + d.getStepX(), z + d.getStepZ()) == ZoneBounds.Kind.FENCE) {
                s = s.setValue(switch (d) {
                    case NORTH -> IronBarsBlock.NORTH;
                    case SOUTH -> IronBarsBlock.SOUTH;
                    case EAST -> IronBarsBlock.EAST;
                    default -> IronBarsBlock.WEST;
                }, true);
            }
        }
        return s;
    }
}
