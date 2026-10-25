package ru.stalkerzone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * 3D-пропс на OBJ-модели. Модель может быть больше блока (труба, фонарь, автобус): соседние клетки,
 * которые она занимает, заполняются невидимыми блоками-корпусами, чтобы сквозь неё нельзя было пройти.
 * Корпуса ставятся при установке блока и убираются вместе с ним.
 */
public class ObjPropBlock extends HorizontalDirectionalBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    /** Клетки в локальных координатах (перед модели смотрит на +x, т. е. на восток). */
    protected final int[][] cells;

    public ObjPropBlock(Properties p, double[] box, int[][] cells) {
        super(p);
        this.cells = cells;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(box, d));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.EAST));
    }

    static VoxelShape rotate(double[] b, Direction d) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        double[] r = switch (d) {
            case SOUTH -> new double[] {16 - z1, x0, 16 - z0, x1};
            case WEST -> new double[] {16 - x1, 16 - z1, 16 - x0, 16 - z0};
            case NORTH -> new double[] {z0, 16 - x1, z1, 16 - x0};
            default -> new double[] {x0, z0, x1, z1};
        };
        return Block.box(Math.min(r[0], r[2]), b[1], Math.min(r[1], r[3]), Math.max(r[0], r[2]), b[4], Math.max(r[1], r[3]));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return shapes.getOrDefault(s.getValue(FACING), Shapes.block());
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1.0f; }

    public int[][] cells() { return cells; }

    /**
     * Поставить пропс при генерации чанка (там onPlace не вызывается): сам блок и его корпуса,
     * но только в пределах этого чанка и только на месте воздуха или травы.
     */
    public static void placeInChunk(net.minecraft.world.level.chunk.ChunkAccess c, BlockPos pos, BlockState s) {
        net.minecraft.world.level.ChunkPos cp = c.getPos();
        if ((pos.getX() >> 4) != cp.x || (pos.getZ() >> 4) != cp.z) return;
        if (!(s.getBlock() instanceof ObjPropBlock ob)) { c.setBlockState(pos, s, false); return; }
        c.setBlockState(pos, s, false);
        BlockState hull = ModBlocks.HITBOX.get().defaultBlockState();
        Direction f = s.getValue(FACING);
        for (int[] q : ob.cells) {
            BlockPos w = cell(pos, f, q[0], q[1], q[2]);
            if ((w.getX() >> 4) != cp.x || (w.getZ() >> 4) != cp.z) continue;
            BlockState at = c.getBlockState(w);
            if (at.isAir() || at.canBeReplaced()) c.setBlockState(w, hull, false);
        }
    }

    /** Локальная клетка -> мировая позиция с учётом поворота. */
    public static BlockPos cell(BlockPos o, Direction f, int dx, int dy, int dz) {
        return switch (f) {
            case SOUTH -> o.offset(-dz, dy, dx);
            case WEST -> o.offset(-dx, dy, -dz);
            case NORTH -> o.offset(dz, dy, -dx);
            default -> o.offset(dx, dy, dz);
        };
    }

    @Override
    public void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(s, level, pos, old, moving);
        if (level.isClientSide || old.is(this)) return;
        BlockState hull = ModBlocks.HITBOX.get().defaultBlockState();
        Direction f = s.getValue(FACING);
        for (int[] c : cells) {
            BlockPos q = cell(pos, f, c[0], c[1], c[2]);
            BlockState at = level.getBlockState(q);
            if (at.isAir() || at.canBeReplaced()) level.setBlock(q, hull, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!level.isClientSide && !now.is(this)) {
            Direction f = s.getValue(FACING);
            for (int[] c : cells) {
                BlockPos q = cell(pos, f, c[0], c[1], c[2]);
                if (level.getBlockState(q).is(ModBlocks.HITBOX.get())) level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        super.onRemove(s, level, pos, now, moving);
    }
}
