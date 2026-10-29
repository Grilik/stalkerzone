package ru.stalkerzone.world.xray;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.stalkerzone.block.ObjPropBlock;

/**
 * Дороги-переходы между уровнями оригинала (там, где в игре был переход на другой уровень). Полотно ведётся
 * ровным уклоном от края одного уровня до края другого: где рельеф выше дороги — туннель с бетонной обделкой
 * и лампами, где ниже — насыпь. Работает в чанках между уровнями (внутри уровня дорога своя, из игры).
 */
public final class LinkRoads {
    static final int HALF = 2, TUNNEL_H = 5;

    private LinkRoads() {}

    public static void decorate(ChunkAccess c) {
        ChunkPos cp = c.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        for (LevelRegions.Link l : LevelRegions.links()) {
            LevelRegion ra = LevelRegions.byId(l.a()), rb = LevelRegions.byId(l.b());
            if (ra == null || rb == null) continue;
            double ax = l.ax(), az = l.az(), bx = l.bx(), bz = l.bz();
            // быстрый отсев: чанк вдали от отрезка
            double minx = Math.min(ax, bx) - 8, maxx = Math.max(ax, bx) + 8, minz = Math.min(az, bz) - 8, maxz = Math.max(az, bz) + 8;
            if (x0 + 15 < minx || x0 > maxx || z0 + 15 < minz || z0 > maxz) continue;
            double ya = ra.ground(ax, az), yb = rb.ground(bx, bz);
            double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz, len = Math.sqrt(len2);
            if (len < 1) continue;
            for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) {
                int x = x0 + i, z = z0 + j;
                if (LevelRegions.covers(x, z)) continue;
                double t = ((x + 0.5 - ax) * dx + (z + 0.5 - az) * dz) / len2;
                if (t < 0 || t > 1) continue;
                double px = ax + t * dx, pz = az + t * dz;
                double w = ((x + 0.5 - ax) * dz - (z + 0.5 - az) * dx) / len;   // поперечное смещение со знаком
                if (Math.abs(w) > HALF + 1.5) continue;
                int road = (int) Math.round(ya + (yb - ya) * t);
                boolean edge = Math.abs(w) > HALF + 0.5;
                column(c, x, z, road, edge, (int) Math.round(t * len));
            }
        }
    }

    private static BlockState b(net.minecraft.world.level.block.Block x) { return x.defaultBlockState(); }

    private static void column(ChunkAccess c, int x, int z, int road, boolean edge, int along) {
        int top = c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        boolean tunnel = top > road + TUNNEL_H + 1;
        if (tunnel) {
            if (edge) {   // стены туннеля
                for (int y = road; y <= road + TUNNEL_H; y++) ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(Blocks.STONE_BRICKS));
                return;
            }
            ObjPropBlock.safeSet(c, new BlockPos(x, road, z), b(Blocks.GRAY_CONCRETE));
            for (int y = road + 1; y < road + TUNNEL_H; y++) ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(Blocks.AIR));
            ObjPropBlock.safeSet(c, new BlockPos(x, road + TUNNEL_H, z), b(Blocks.SMOOTH_STONE));
            if (along % 10 == 0) ObjPropBlock.safeSet(c, new BlockPos(x, road + TUNNEL_H - 1, z), b(Blocks.LANTERN).setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            return;
        }
        if (edge) {   // обочина: откос насыпи или край выемки
            for (int y = top + 1; y < road; y++) ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(Blocks.DIRT));
            ObjPropBlock.safeSet(c, new BlockPos(x, road, z), b(Blocks.GRASS_BLOCK));
            for (int y = road + 1; y <= top; y++) ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(Blocks.AIR));
            return;
        }
        for (int y = Math.min(top, road - 1); y < road; y++)   // насыпь: сверху гравий, ниже грунт
            ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(y >= road - 3 ? Blocks.GRAVEL : Blocks.DIRT));
        ObjPropBlock.safeSet(c, new BlockPos(x, road, z), b(((x * 7 + z * 13) & 7) == 0 ? Blocks.GRAVEL : Blocks.GRAY_CONCRETE_POWDER));
        for (int y = road + 1; y <= Math.max(road + 4, top); y++) ObjPropBlock.safeSet(c, new BlockPos(x, y, z), b(Blocks.AIR));
    }
}
