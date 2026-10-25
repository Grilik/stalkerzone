package ru.stalkerzone.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.stalkerzone.sim.Good;

/** Предмет, соответствующий товару симуляции. */
public class GoodItem extends Item {
    public final Good good;

    public GoodItem(Good good) {
        super(new Item.Properties().stacksTo(64));
        this.good = good;
    }

    /** Сколько единиц товара в одной штуке (у частей мутантов больше одной). */
    public int units() { return 1; }

    /** Медикаменты можно принять: снимают дозу радиации и немного лечат. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (good != Good.MEDS || getClass() != GoodItem.class) return super.use(level, player, hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ru.stalkerzone.survival.Radiation.cure(sp, 80);
            sp.heal(4);
            if (!sp.isCreative()) st.shrink(1);
            sp.displayClientMessage(Component.literal("Аптечка и антирад: доза " + Math.round(ru.stalkerzone.survival.Radiation.dose(sp))), true);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
