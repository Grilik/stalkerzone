package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.stalkerzone.block.ObjPropBlock;
import ru.stalkerzone.zonemap.RoadNet;
import ru.stalkerzone.zonemap.RoadNet.Leg;

import java.util.List;

/**
 * v1.14. Связность карты: дороги между локациями по графу симуляции (Г-образно, вдоль осей, чтобы провода и
 * трубы ложились ровно), деревянные столбы с проводами вдоль дорог, линия ЛЭП от ЧАЭС на юг, надземные газопроводы
 * с П-образными компенсаторами, дорожные указатели у въездов. Всё ставится по чанкам при первой загрузке, как прочий
 * декор; высоту соседней опоры (в ещё не созданном чанке) берём из генератора рельефа.
 */
public final class Roads {
    static final int HALF = RoadNet.HALF;
    static final int POLE_OFF = 6;      // столбы справа от дороги
    static final int LEP_OFF = -17;     // ЛЭП слева, подальше
    static final int GAS_OFF = -10;     // газопровод слева
    static final int CAMP_R = RoadNet.CAMP_R;

    private static List<Leg> legs;

    private Roads() {}

    private static synchronized void init() {
        if (legs == null) legs = RoadNet.get().legs;
    }

    static boolean nearCenter(int x, int z, int r) { return RoadNet.get().nearCenter(x, z, r); }

    static int roadDist(int x, int z) { return RoadNet.get().roadDist(x, z); }

    public static void decorate(ChunkAccess c, ServerLevel level, java.util.Random r, boolean city) {
        init();
        ChunkPos cp = c.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ(), x1 = cp.getMaxBlockX(), z1 = cp.getMaxBlockZ();
        for (Leg l : legs) {
            int a0 = l.alongX() ? x0 : z0, a1 = l.alongX() ? x1 : z1;
            int c0 = l.alongX() ? z0 : x0, c1 = l.alongX() ? z1 : x1;
            if (l.hi() < a0 || l.lo() > a1) continue;
            int reach = Math.max(Math.max(HALF, POLE_OFF), Math.max(-LEP_OFF, -GAS_OFF)) + 1;
            if (l.fixed() + reach < c0 || l.fixed() - reach > c1) continue;
            for (int t = Math.max(a0, l.lo()); t <= Math.min(a1, l.hi()); t++) {
                // полотно
                for (int w = -HALF; w <= HALF; w++) {
                    int x = l.alongX() ? t : l.fixed() + w, z = l.alongX() ? l.fixed() + w : t;
                    if (x < x0 || x > x1 || z < z0 || z > z1 || nearCenter(x, z, CAMP_R)) continue;
                    paint(c, x, z, l.asphalt(), Math.abs(w) == HALF, r);
                }
                if (city || nearCenter(l.alongX() ? t : l.fixed(), l.alongX() ? l.fixed() : t, CAMP_R + 12)) continue;
                // деревянные столбы через 16 (на всех дорогах, кроме тех, где идёт ЛЭП)
                if (!l.lep() && Math.floorMod(t, 16) == 8) pole(c, level, l, t, POLE_OFF, 16, "prop_power_pole", "prop_pole_wires_");
                if (l.lep() && Math.floorMod(t, 32) == 8) pole(c, level, l, t, LEP_OFF, 32, "prop_lep_pylon", "prop_lep_wires_");
                if (l.gas()) gas(c, l, t, r);
            }
            // указатель у въезда: в 46 блоках от начала участка, если он у локации
            for (int end : new int[] {l.from(), l.to()}) {
                int step = end == l.lo() ? 46 : -46;
                int t = end + step;
                if (t < a0 || t > a1 || t < l.lo() || t > l.hi()) continue;
                int ex = l.alongX() ? end : l.fixed(), ez = l.alongX() ? l.fixed() : end;
                if (!nearCenter(ex, ez, 8)) continue;
                int x = l.alongX() ? t : l.fixed() + HALF + 2, z = l.alongX() ? l.fixed() + HALF + 2 : t;
                if (x < x0 || x > x1 || z < z0 || z > z1) continue;
                int y = solidTop(c, x, z) + 1;
                Direction face = l.alongX() ? (step > 0 ? Direction.WEST : Direction.EAST) : (step > 0 ? Direction.NORTH : Direction.SOUTH);
                place(c, x, y, z, "prop_road_sign", face);
            }
        }
    }

    /** Покрытие дороги: асфальт (в текстурпаке — потрескавшийся) с гравийной обочиной, или грунтовка. */
    private static void paint(ChunkAccess c, int x, int z, boolean asphalt, boolean edge, java.util.Random r) {
        int y = solidTop(c, x, z);
        BlockPos p = new BlockPos(x, y, z);
        BlockState top = c.getBlockState(p);
        if (top.is(Blocks.WATER)) {   // гать через воду
            c.setBlockState(p, Blocks.GRAVEL.defaultBlockState(), false);
            return;
        }
        BlockState s;
        double v = r.nextDouble();
        if (asphalt) s = edge ? (v < 0.6 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState()
                : (v < 0.06 ? Blocks.GRAVEL : v < 0.1 ? Blocks.COARSE_DIRT : Blocks.BLACK_CONCRETE).defaultBlockState();
        else s = (v < 0.45 ? Blocks.COARSE_DIRT : v < 0.75 ? Blocks.DIRT_PATH : Blocks.GRAVEL).defaultBlockState();
        c.setBlockState(p, s, false);
        for (int k = 1; k <= 4; k++) {   // расчистить кусты, траву и стволы над дорогой
            BlockPos q = p.above(k);
            BlockState at = c.getBlockState(q);
            if (at.isAir()) break;
            if (at.canBeReplaced() || at.is(BlockTags.LOGS) || at.is(BlockTags.LEAVES) || at.is(Blocks.SWEET_BERRY_BUSH))
                c.setBlockState(q, Blocks.AIR.defaultBlockState(), false);
        }
    }

    /** Верхний твёрдый блок колонки (не листва, не стволы, не трава). */
    static int solidTop(ChunkAccess c, int x, int z) {
        int y = c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        for (int k = 0; k < 24; k++, y--) {
            BlockState s = c.getBlockState(new BlockPos(x, y, z));
            if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) continue;
            return y;
        }
        return c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private static int baseHeight(ServerLevel level, int x, int z) {
        try {
            return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    /** Опора сбоку дороги и провода до следующей опоры по ходу участка (в сторону роста координаты). */
    private static void pole(ChunkAccess c, ServerLevel level, Leg l, int t, int off, int span, String pole, String wires) {
        int x = l.alongX() ? t : l.fixed() + off, z = l.alongX() ? l.fixed() + off : t;
        ChunkPos cp = c.getPos();
        if (x < cp.getMinBlockX() || x > cp.getMaxBlockX() || z < cp.getMinBlockZ() || z > cp.getMaxBlockZ()) return;
        BlockState under = c.getBlockState(new BlockPos(x, solidTop(c, x, z), z));
        if (under.is(Blocks.WATER)) return;
        int y = solidTop(c, x, z) + 1;
        Direction along = l.alongX() ? Direction.EAST : Direction.SOUTH;
        place(c, x, y, z, pole, along);
        if (t + span > l.hi()) return;
        int nx = l.alongX() ? t + span : x, nz = l.alongX() ? z : t + span;
        int ny = baseHeight(level, nx, nz);
        int dy = ny == Integer.MIN_VALUE ? 0 : Math.max(-3, Math.min(3, ny - y));
        int wx = l.alongX() ? x + 1 : x, wz = l.alongX() ? z : z + 1;
        if (wx > cp.getMaxBlockX() || wz > cp.getMaxBlockZ()) return;
        BlockState ws = ru.stalkerzone.world.Dressing.prop(wires + (dy + 3), along);
        if (ws != null) c.setBlockState(new BlockPos(wx, y, wz), ws, false);
    }

    /** Газопровод: участки по 4 блока, каждые 96 блоков — П-образный компенсатор; у чужих дорог пропуск. */
    private static void gas(ChunkAccess c, Leg l, int t, java.util.Random r) {
        int m = Math.floorMod(t, 96);
        boolean loop = m == 0;
        if (!loop && (m % 4 != 0 || m < 12)) return;
        int x = l.alongX() ? t : l.fixed() + GAS_OFF, z = l.alongX() ? l.fixed() + GAS_OFF : t;
        ChunkPos cp = c.getPos();
        int len = loop ? 9 : 4;
        int ex = l.alongX() ? x + len - 1 : x, ez = l.alongX() ? z : z + len - 1;
        if (x < cp.getMinBlockX() || ex > cp.getMaxBlockX() || z < cp.getMinBlockZ() || ez > cp.getMaxBlockZ()) return;
        if (roadDist(x, z) < 5 || roadDist(ex, ez) < 5) return;
        BlockState under = c.getBlockState(new BlockPos(x, solidTop(c, x, z), z));
        if (under.is(Blocks.WATER)) return;
        int y = solidTop(c, x, z) + 1;
        place(c, x, y, z, loop ? "prop_gas_loop" : "prop_gas_pipe", l.alongX() ? Direction.EAST : Direction.SOUTH);
    }

    private static void place(ChunkAccess c, int x, int y, int z, String name, Direction f) {
        BlockState s = ru.stalkerzone.world.Dressing.prop(name, f);
        if (s != null) ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
    }
}
