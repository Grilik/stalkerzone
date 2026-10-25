package ru.stalkerzone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ru.stalkerzone.lab.LabManager;

/** Терминал с данными лаборатории. ПКМ с пустой флешкой или диском в руке начинает скачивание. Не ломается. */
public class LabTerminalBlock extends Block {
    public LabTerminalBlock(Properties p) {
        super(p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) LabManager.useTerminal(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
