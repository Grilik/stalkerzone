package ru.stalkerzone.pda;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.ZoneLife;

/** КПК погибшего сталкера (v1.14): ПКМ — прочитать последнюю запись, тайник отмечается. */
public class DeadPdaItem extends Item {
    public DeadPdaItem() { super(new Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
        ZoneData data = ZoneData.get(p.getServer());
        ZoneLife.Stash s = st.getTag() == null ? null : data.life.stash(st.getTag().getInt("stash"));
        if (s == null) {
            p.sendSystemMessage(Component.literal("Экран разбит, память стёрта. Можно сдать торговцу на запчасти.").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.consume(st);
        }
        p.sendSystemMessage(Component.literal("КПК сталкера " + s.owner + ". Последняя запись: «" + s.note + "»").withStyle(ChatFormatting.GRAY));
        if (s.placed) p.sendSystemMessage(Component.literal("Отметка тайника: " + s.x + ", " + s.z + " — похоже, его уже нашли.").withStyle(ChatFormatting.DARK_GRAY));
        else {
            s.known = true;
            data.setDirty();
            p.sendSystemMessage(Component.literal("[КПК] Отмечен тайник: " + s.x + ", " + s.z + " (≈" + (int) Math.hypot(s.x - p.getX(), s.z - p.getZ()) + " м).").withStyle(ChatFormatting.DARK_GREEN));
        }
        return InteractionResultHolder.consume(st);
    }
}
