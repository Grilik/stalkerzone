package ru.stalkerzone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Невидимый «корпус» большой 3D-модели: твёрдый, не загораживает свет, ничего не роняет. */
public class HitboxBlock extends Block {
    public HitboxBlock(Properties p) { super(p); }

    @Override
    public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) { return true; }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) { return 1.0f; }
}
