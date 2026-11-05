package ru.stalkerzone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Секция забора «люками» (П-02в, забор деревни): люк, который стоит открытым — вертикальной доской — и не
 * открывается/не закрывается ни рукой, ни редстоуном. Ставится всегда открытым (вертикально), сторона — по взгляду
 * игрока, как у обычного люка. Состояния блока те же, что у {@link TrapDoorBlock}, — модели и blockstates прежние.
 */
public class FixedTrapDoorBlock extends TrapDoorBlock {
    public FixedTrapDoorBlock(Properties p, BlockSetType set) { super(p, set); }

    /** Клик рукой ничего не меняет (и не тратит действие — можно поставить блок рядом). */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Редстоун и соседи не открывают и не закрывают секцию. */
    @Override
    public void neighborChanged(BlockState s, Level l, BlockPos pos, Block b, BlockPos from, boolean moving) {}

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(OPEN, true).setValue(POWERED, false);
    }
}
