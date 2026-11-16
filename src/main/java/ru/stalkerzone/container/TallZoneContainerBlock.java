package ru.stalkerzone.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Э1-17: шкафчик в два блока — блок-сущность в нижней половине, верхняя открывает нижнюю и ломается вместе с ней. */
public class TallZoneContainerBlock extends ZoneContainerBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public TallZoneContainerBlock(Properties p, ContainerKind kind) {
        super(p, kind);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(HALF);
    }

    @Override
    protected boolean isUpper(BlockState s) { return s.getValue(HALF) == DoubleBlockHalf.UPPER; }

    @Override
    protected BlockPos basePos(BlockState s, BlockPos p) { return isUpper(s) ? p.below() : p; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos p = ctx.getClickedPos();
        if (p.getY() >= ctx.getLevel().getMaxBuildHeight() - 1 || !ctx.getLevel().getBlockState(p.above()).canBeReplaced(ctx)) return null;
        return super.getStateForPlacement(ctx);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState s, LivingEntity by, ItemStack st) {
        level.setBlock(pos.above(), s.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        boolean lower = !isUpper(s);
        if (dir == (lower ? Direction.UP : Direction.DOWN)
                && !(other.is(this) && other.getValue(HALF) != s.getValue(HALF))) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s, dir, other, level, pos, otherPos);
    }
}
