package ru.stalkerzone.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Э1-17: контейнер мода (ящик, цинк, шкаф, сейф) — открывается как сундук, добыча — один раз при первом открытии. */
public class ZoneContainerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final ContainerKind kind;
    /** Формы по сторонам света (индекс — Direction.get2DDataValue()): [нижний блок], [верхний блок]. */
    private final VoxelShape[] lower = new VoxelShape[4], upper = new VoxelShape[4];

    public ZoneContainerBlock(Properties p, ContainerKind kind) {
        super(p);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int i = dir.get2DDataValue();
            lower[i] = shape(dir, 0, Math.min(16, kind.h));
            upper[i] = kind.tall() ? shape(dir, 0, kind.h - 16) : lower[i];
        }
    }

    /** Коробка w×h×d по центру клетки, перед — к facing. */
    private VoxelShape shape(Direction dir, int y0, int y1) {
        double a = (16 - kind.w) / 2.0, b = (16 - kind.d) / 2.0;
        boolean ns = dir == Direction.NORTH || dir == Direction.SOUTH;
        return ns ? Block.box(a, y0, b, 16 - a, y1, 16 - b) : Block.box(b, y0, a, 16 - b, y1, 16 - a);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** Клетка с блок-сущностью (у двухблочного — нижняя). */
    protected BlockPos basePos(BlockState s, BlockPos p) { return p; }

    protected boolean isUpper(BlockState s) { return false; }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        int i = s.getValue(FACING).get2DDataValue();
        return isUpper(s) ? upper[i] : lower[i];
    }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isUpper(state) ? null : new ZoneContainerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(basePos(s, pos)) instanceof ZoneContainerBlockEntity c) player.openMenu(c);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level level, BlockPos pos, BlockState ns, boolean moved) {
        if (!s.is(ns.getBlock()) && level.getBlockEntity(pos) instanceof ZoneContainerBlockEntity c) {
            Containers.dropContents(level, pos, c);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(s, level, pos, ns, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
}
