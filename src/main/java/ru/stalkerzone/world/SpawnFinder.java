package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Подбирает сухое место на поверхности рядом с нужной точкой. */
public final class SpawnFinder {
    private SpawnFinder() {}

    public static BlockPos groundAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4); // сгенерировать чанк
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean dry(ServerLevel level, BlockPos p) {
        return level.getFluidState(p.below()).isEmpty() && !level.getBlockState(p.below()).isAir();
    }

    /** Ищет спиралью сухую точку не дальше 400 блоков от (x, z) и внутри границ. Если не нашла, берёт саму точку. */
    public static BlockPos safeNear(ServerLevel level, ZoneBounds b, int x, int z) {
        BlockPos first = groundAt(level, x, z);
        if (dry(level, first)) return first;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int r = 8; r <= 400; r += 8) {
            for (int[] d : dirs) {
                int px = x + d[0] * r, pz = z + d[1] * r;
                if (!b.inside(px, pz) || b.distanceToFence(px, pz) < 3) continue;
                BlockPos p = groundAt(level, px, pz);
                if (dry(level, p)) return p;
            }
        }
        return first;
    }
}
