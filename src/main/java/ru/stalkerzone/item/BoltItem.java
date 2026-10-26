package ru.stalkerzone.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.stalkerzone.anomaly.BoltEntity;

/** Болт: ПКМ — бросить вперёд. Главный инструмент сталкера против аномалий. */
public class BoltItem extends Item {
    public BoltItem() { super(new Properties().stacksTo(64)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.4f, 1.6f);
        if (!level.isClientSide) {
            BoltEntity b = new BoltEntity(level, p);
            b.setItem(new ItemStack(this));
            b.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.1f, 1.5f);
            level.addFreshEntity(b);
        }
        p.getCooldowns().addCooldown(this, 8);
        if (!p.getAbilities().instabuild) s.shrink(1);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
