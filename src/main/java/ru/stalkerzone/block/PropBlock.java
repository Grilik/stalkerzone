package ru.stalkerzone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Простой пропс с формой меньше блока (бочка, покрышки). */
public class PropBlock extends Block {
    private final VoxelShape shape;

    public PropBlock(Properties p, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(p);
        this.shape = Block.box(x0, y0, z0, x1, y1, z1);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }
}
