package ru.stalkerzone.npc.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import ru.stalkerzone.sim.tactics.CoverFinder;

import java.util.HashMap;
import java.util.Map;

/** Сетка блоков мира для CoverFinder: твёрдым считается всё, у чего есть коллизия. Кэш на один поиск. */
final class WorldGrid implements CoverFinder.Grid {
    private final Level level;
    private final Map<Long, Boolean> cache = new HashMap<>();
    private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

    WorldGrid(Level level) { this.level = level; }

    @Override
    public boolean solid(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        Boolean b = cache.get(k);
        if (b != null) return b;
        p.set(x, y, z);
        boolean s;
        if (!level.hasChunkAt(p)) s = true;            // незагруженное: туда не бежим и сквозь не стреляем
        else {
            BlockState st = level.getBlockState(p);
            s = !st.isAir() && !st.getCollisionShape(level, p).isEmpty();
        }
        cache.put(k, s);
        return s;
    }
}
