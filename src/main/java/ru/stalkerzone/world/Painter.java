package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Ставит блоки в координатах относительно точки (ox, oy, oz), без обновления соседей. */
public final class Painter {
    private final ServerLevel level;
    private final int ox, oy, oz;
    private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

    public Painter(ServerLevel level, int ox, int oy, int oz) {
        this.level = level;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    public void set(int dx, int dy, int dz, BlockState s) {
        level.setBlock(p.set(ox + dx, oy + dy, oz + dz), s, 18);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    public BlockState get(int dx, int dy, int dz) {
        return level.getBlockState(p.set(ox + dx, oy + dy, oz + dz));
    }
}
