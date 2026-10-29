package ru.stalkerzone.world.xray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import ru.stalkerzone.block.ObjPropBlock;
import ru.stalkerzone.block.ZoneBlocks;
import ru.stalkerzone.block.ZoneMat;
import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * Сельский дом и сарай Зоны по шаблону (v1.17): фундамент, стены с угловыми столбами, окна и дверь, двускатная крыша
 * ступенями со свесами и фронтонами, печная труба; крыша целая, дырявая или одни стропила — как в оригинале.
 * Всё считается по колонкам, поэтому дом на границе чанков собирается из частей одинаково.
 */
public final class HouseBuilder {
    private HouseBuilder() {}

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    static BlockState wallBlock(int m, int x, int y, int z) {
        int h = hash(x, y, z) & 15;
        return ZoneBlocks.full(switch (m) {
            case 200 -> h < 3 ? ZoneMat.BRICK_DIRTY : ZoneMat.BRICK_RED;
            case 201, 223, 224, 225 -> h < 3 ? ZoneMat.PLASTER_OLD : ZoneMat.PLASTER;
            case 205 -> ZoneMat.WOOD_PAINTED;
            default -> h < 3 ? ZoneMat.BOARDS_OLD : ZoneMat.PLANKS_GRAY;
        });
    }

    static ZoneMat roofMat(int m) {
        return switch (m) {
            case 208 -> ZoneMat.ROOF_RED;
            case 209, 210 -> ZoneMat.RUST_SHEET;
            default -> ZoneMat.SHIFER;
        };
    }

    static Block roofStairs(int m) { return ZoneBlocks.STAIRS.get(roofMat(m)).get(); }

    static Block roofSlab(int m) { return ZoneBlocks.SLAB.get(roofMat(m)).get(); }

    private static void set(ChunkAccess c, ChunkPos cp, int x, int y, int z, BlockState s) {
        if ((x >> 4) != cp.x || (z >> 4) != cp.z) return;
        ObjPropBlock.safeSet(c, new BlockPos(x, y, z), s);
    }

    public static void build(ChunkAccess c, LevelBuildings.House h) {
        ChunkPos cp = c.getPos();
        int cx0 = cp.getMinBlockX(), cz0 = cp.getMinBlockZ();
        if (h.x1() + 1 < cx0 || h.x0() - 1 > cx0 + 15 || h.z1() + 1 < cz0 || h.z0() - 1 > cz0 + 15) return;
        // стены внутри следа (след с карнизом), крыша — по всему следу
        boolean shrink = h.x1() - h.x0() >= 6 && h.z1() - h.z0() >= 6;
        int wx0 = h.x0() + (shrink ? 1 : 0), wx1 = h.x1() - (shrink ? 1 : 0), wz0 = h.z0() + (shrink ? 1 : 0), wz1 = h.z1() - (shrink ? 1 : 0);
        int y0 = h.y();
        int E = Math.max(3, Math.min(5, h.eave()));
        int top = y0 + E;                      // верх стен
        boolean ax = h.alongX();
        // поперёк конька
        int a0 = ax ? wz0 : wx0, a1 = ax ? wz1 : wx1;
        double mid = (a0 + a1) / 2.0;
        int halfSpan = (a1 - a0 + 2) / 2;
        int roofTop = top + halfSpan + 1;
        int state = h.cover() >= 0.4 ? 0 : h.cover() >= 0.25 ? 1 : 2;   // целая, дырявая, стропила
        Block st = roofStairs(h.roof()), sl = roofSlab(h.roof());
        // дверь: ближайшая к стене из найденных в игре, иначе посередине длинной стороны
        int[] door = pickDoor(h, wx0, wx1, wz0, wz1);
        // печная труба: на коньке, на трети длины
        int chX = ax ? wx0 + (wx1 - wx0) / 3 : (int) Math.round(mid), chZ = ax ? (int) Math.round(mid) : wz0 + (wz1 - wz0) / 3;

        for (int x = Math.max(cx0, h.x0() - 1); x <= Math.min(cx0 + 15, h.x1() + 1); x++)
            for (int z = Math.max(cz0, h.z0() - 1); z <= Math.min(cz0 + 15, h.z1() + 1); z++) {
                boolean inWalls = x >= wx0 && x <= wx1 && z >= wz0 && z <= wz1;
                boolean edgeX = x == wx0 || x == wx1, edgeZ = z == wz0 || z == wz1;
                boolean onWall = inWalls && (edgeX || edgeZ);
                boolean corner = edgeX && edgeZ;
                int across = ax ? z : x, along = ax ? x : z;
                int al0 = ax ? wx0 : wz0, al1 = ax ? wx1 : wz1;
                // расчистка всего, что было (воксели старого дома уже не ставились), до верха крыши
                for (int y = y0 + 1; y <= roofTop + 3; y++) set(c, cp, x, y, z, Blocks.AIR.defaultBlockState());
                // фундамент и пол
                if (inWalls) {
                    set(c, cp, x, y0, z, onWall ? ZoneBlocks.full(ZoneMat.BASE_STONE) : ZoneBlocks.full(ZoneMat.PLANKS_FLOOR));
                    set(c, cp, x, y0 - 1, z, ZoneBlocks.full(ZoneMat.BASE_STONE));
                }
                // стены
                if (onWall) {
                    for (int y = y0 + 1; y <= top; y++) {
                        BlockState s = corner ? ZoneBlocks.full(ZoneMat.WOOD_POST) : wallBlock(h.wall(), x, y, z);
                        int along2 = edgeZ && !corner ? x : z;   // позиция вдоль этой стены
                        int wallLen0 = edgeZ ? wx0 : wz0;
                        boolean window = !corner && (y == y0 + 2 || (E >= 4 && y == y0 + 3))
                                && Math.floorMod(along2 - wallLen0, 3) == 2 && !nearDoor(door, x, z);
                        if (window) s = windowBlock(h, edgeZ && !edgeX, x, y, z, state);
                        set(c, cp, x, y, z, s);
                    }
                    if (door != null && door[0] == x && door[1] == z) {
                        Direction f = door[2] == 0 ? Direction.SOUTH : Direction.EAST;
                        BlockState d = ZoneBlocks.DOOR.get(ZoneMat.DOOR_GREEN).get().defaultBlockState().setValue(DoorBlock.FACING, f);
                        set(c, cp, x, y0 + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                        set(c, cp, x, y0 + 2, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                        set(c, cp, x, y0, z, ZoneBlocks.full(ZoneMat.PLANKS_FLOOR));
                    }
                }
                // фронтоны: торцевые стены до ската
                boolean gable = inWalls && (along == al0 || along == al1);
                // крыша: свес на 1 за стены по обе стороны и по торцам
                boolean underRoof = across >= a0 - 1 && across <= a1 + 1 && along >= al0 - 1 && along <= al1 + 1;
                if (!underRoof) continue;
                double dEdge = Math.min(across - (a0 - 1), (a1 + 1) - across);
                int yr = top + (int) dEdge;
                if (gable) for (int y = top + 1; y < yr; y++) set(c, cp, x, y, z, wallBlock(h.wall(), x, y, z));
                boolean ridge = Math.abs(across - mid) < 0.6;
                int hh = hash(x, yr, z);
                boolean rafterRow = Math.floorMod(along - al0, 2) == 0;
                if (state == 1 && hh % 100 < 35 && !gable) {         // дыра в кровле: видны стропила
                    if (rafterRow) set(c, cp, x, yr, z, ZoneBlocks.FENCE.get(ZoneMat.WOOD_POST).get().defaultBlockState());
                    continue;
                }
                if (state == 2 && !gable) {                         // одни стропила
                    if (rafterRow || ridge) set(c, cp, x, yr, z, ridge ? ZoneBlocks.full(ZoneMat.WOOD_POST) : ZoneBlocks.FENCE.get(ZoneMat.WOOD_POST).get().defaultBlockState());
                    continue;
                }
                if (ridge) {
                    set(c, cp, x, yr, z, sl.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                    // чётный пролёт: конёк из двух ступеней — ставим полублок сверху у обеих
                } else {
                    Direction f = ax ? (across < mid ? Direction.SOUTH : Direction.NORTH) : (across < mid ? Direction.EAST : Direction.WEST);
                    set(c, cp, x, yr, z, st.defaultBlockState().setValue(StairBlock.FACING, f));
                    // под ступенью — сплошной блок кровли над стеной (чтобы не было щели на фронтоне)
                }
                if (x == chX && z == chZ && h.wall() != 201) for (int y = y0 + 1; y <= yr + 2; y++)
                    set(c, cp, x, y, z, ZoneBlocks.full(ZoneMat.BRICK_DIRTY));
            }
    }

    private static BlockState windowBlock(LevelBuildings.House h, boolean alongXWall, int x, int y, int z, int state) {
        int r = hash(x, 7, z) % 10;
        if (state >= 1 && r < 3) return ZoneBlocks.full(ZoneMat.BOARDS_OLD);               // заколочено
        if (state >= 1 && r < 6) return Blocks.AIR.defaultBlockState();                     // выбито
        BlockState p = ZoneBlocks.PANE.get(ZoneMat.WINDOW).get().defaultBlockState();
        return alongXWall ? p.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
                : p.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
    }

    private static boolean nearDoor(int[] door, int x, int z) {
        return door != null && Math.abs(door[0] - x) + Math.abs(door[1] - z) <= 1;
    }

    /** {x, z, 0 если стена вдоль X (дверь смотрит на юг) / 1 если вдоль Z}. */
    private static int[] pickDoor(LevelBuildings.House h, int wx0, int wx1, int wz0, int wz1) {
        int[] best = null;
        double bd = 3;
        for (int[] d : h.doors()) {
            int x = Math.max(wx0, Math.min(wx1, d[0])), z = Math.max(wz0, Math.min(wz1, d[2]));
            double dx = Math.min(x - wx0, wx1 - x), dz = Math.min(z - wz0, wz1 - z);
            int[] cand = dz <= dx ? new int[] {x, z - wz0 < wz1 - z ? wz0 : wz1, 0} : new int[] {x - wx0 < wx1 - x ? wx0 : wx1, z, 1};
            double dist = Math.hypot(cand[0] - d[0], cand[1] - d[2]);
            if (dist < bd) { bd = dist; best = cand; }
        }
        if (best == null) {
            if (h.alongX()) best = new int[] {(wx0 + wx1) / 2, wz1, 0};
            else best = new int[] {wx1, (wz0 + wz1) / 2, 1};
        }
        // не в угол
        if (best[2] == 0) best[0] = Math.max(wx0 + 1, Math.min(wx1 - 1, best[0]));
        else best[1] = Math.max(wz0 + 1, Math.min(wz1 - 1, best[1]));
        return best;
    }

    /** Забор из досок: щиты (открытые люки) вдоль линии, столбы в углах и через три секции; высота как в игре. */
    public static void fence(ChunkAccess c, LevelBuildings b, LevelRegion r) {
        ChunkPos cp = c.getPos();
        BlockState post = ZoneBlocks.FENCE.get(ZoneMat.WOOD_POST).get().defaultBlockState();
        Block panel = ZoneBlocks.TRAPDOOR.get(ZoneMat.FENCE_BOARDS).get();
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++)
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                LevelBuildings.Fence f = b.fenceAt.get(LevelBuildings.key(x, z));
                if (f == null || b.houseAt(x, z) != null) continue;
                boolean n = b.fenceAt.containsKey(LevelBuildings.key(x, z - 1)), s = b.fenceAt.containsKey(LevelBuildings.key(x, z + 1));
                boolean w = b.fenceAt.containsKey(LevelBuildings.key(x - 1, z)), e = b.fenceAt.containsKey(LevelBuildings.key(x + 1, z));
                int links = (n ? 1 : 0) + (s ? 1 : 0) + (w ? 1 : 0) + (e ? 1 : 0);
                boolean lineX = (w || e) && !(n || s), lineZ = (n || s) && !(w || e);
                boolean isPost = !(lineX || lineZ) || links != 2 || Math.floorMod(lineX ? x : z, 3) == 0;
                int hh = hash(x, 1, z);
                if (hh % 11 == 0) continue;   // выломанная секция
                int g = r.groundBlock(x, z);
                int top = g + Math.max(1, Math.min(2, f.h()));
                for (int y = g + 1; y <= top; y++) {
                    BlockState st;
                    if (isPost) st = post.setValue(FenceBlock.NORTH, n).setValue(FenceBlock.SOUTH, s).setValue(FenceBlock.WEST, w).setValue(FenceBlock.EAST, e);
                    else {
                        Direction facing = lineX ? Direction.NORTH : Direction.EAST;
                        st = panel.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, true);
                    }
                    if (y == top && !isPost && hh % 5 == 0) continue;   // выбитая доска сверху
                    set(c, cp, x, y, z, st);
                }
            }
    }
}
