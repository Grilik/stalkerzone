package ru.stalkerzone.world.xray;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import ru.stalkerzone.block.ObjPropBlock;
import ru.stalkerzone.zonemap.build.Prefabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ставит постройки {@link Prefabs} в чанки при генерации (вместо вокселей уровня в их границах). */
public final class PrefabLayer {
    private PrefabLayer() {}

    /** Уровень развития построек при генерации мира (дальше их меняет развитие баз). */
    public static final int WORLDGEN_LEVEL = 2;

    private static final Map<String, Map<Long, List<long[]>>> CACHE = new HashMap<>();
    private static final Map<String, List<String>> STATES = new HashMap<>();

    private static long chunk(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private static synchronized Map<Long, List<long[]>> of(LevelRegion r) {
        return CACHE.computeIfAbsent(r.id, id -> {
            Map<Long, List<long[]>> byChunk = new HashMap<>();
            List<String> states = new ArrayList<>();
            Map<String, Integer> idx = new HashMap<>();
            for (Map.Entry<Long, String> e : Prefabs.build(id, r::groundBlock, WORLDGEN_LEVEL).entrySet()) {
                long k = e.getKey();
                int x = Prefabs.kx(k), z = Prefabs.kz(k);
                int si = idx.computeIfAbsent(e.getValue(), s -> { states.add(s); return states.size() - 1; });
                byChunk.computeIfAbsent(chunk(x >> 4, z >> 4), c -> new ArrayList<>()).add(new long[] {k, si});
            }
            STATES.put(id, states);
            return byChunk;
        });
    }

    public static void place(ChunkAccess c, LevelRegion r) {
        if (Prefabs.clear(r.id).isEmpty()) return;
        ChunkPos cp = c.getPos();
        List<long[]> list = of(r).get(chunk(cp.x, cp.z));
        if (list == null) return;
        List<String> states = STATES.get(r.id);
        for (long[] b : list) {
            BlockState st = ru.stalkerzone.world.ZoneMapPlacer.state(states.get((int) b[1]));
            ObjPropBlock.safeSet(c, new BlockPos(Prefabs.kx(b[0]), Prefabs.ky(b[0]), Prefabs.kz(b[0])), st);
        }
    }

    /** Воксели уровня без тех, что попадают в границы построек. */
    public static int[] without(int[] v, String levelId) {
        if (Prefabs.clear(levelId).isEmpty()) return v;
        int[] out = new int[v.length];
        int n = 0;
        for (int i = 0; i + 3 < v.length; i += 4) {
            if (Prefabs.cleared(levelId, v[i], v[i + 2])) continue;
            System.arraycopy(v, i, out, n, 4);
            n += 4;
        }
        return n == v.length ? v : java.util.Arrays.copyOf(out, n);
    }
}
