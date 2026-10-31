package ru.stalkerzone.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.item.ModItems;

/**
 * Рюкзак на месте гибели игрока (карточка 030): вместо разбросанных по земле вещей остаётся
 * один рюкзак со всем инвентарём (кроме того, что и так остаётся у игрока, например КПК).
 * Лежит столько же, сколько раньше лежали вещи (20 минут), затем исчезает. ПКМ — забрать всё.
 */
public final class DeathBackpack {
    /** Сколько тиков рюкзак лежит на земле: 20 минут, как раньше лежали выпавшие вещи. */
    public static final int HOLD_TICKS = 20 * 60 * 20;
    /** Ключ NBT предмета со списком содержимого (формат ItemStack.save). */
    private static final String ITEMS = "Items";
    /** Значение pickupDelay, при котором предмет не подбирается никогда. */
    private static final int NEVER_PICK_UP = 32767;

    private DeathBackpack() {}

    /** Положить рюкзак на месте смерти игрока со всем его снаряжением. */
    public static void place(ServerPlayer p, ListTag items) {
        ServerLevel level = p.serverLevel();
        ItemStack st = new ItemStack(ModItems.DEATH_BACKPACK.get());
        CompoundTag tag = new CompoundTag();
        tag.put(ITEMS, items);
        tag.putString("Owner", p.getGameProfile().getName());
        st.setTag(tag);
        ItemEntity ie = new ItemEntity(level, p.getX(), p.getY() + 0.5, p.getZ(), st);
        ie.setInvulnerable(true);       // не сгорит в костре и не разлетится от взрыва
        ie.setPickUpDelay(NEVER_PICK_UP); // забрать можно только ПКМ, а не пробегая мимо
        ie.lifespan = HOLD_TICKS;       // через 20 минут исчезает
        level.addFreshEntity(ie);
    }

    /** Рюкзак ли это (по предмету). */
    public static boolean isBackpack(ItemEntity ie) {
        return ie.isAlive() && ie.getItem().getItem() == ModItems.DEATH_BACKPACK.get();
    }

    /** Забрать всё содержимое рюкзака: в инвентарь, что не влезло — под ноги. */
    public static void take(ServerPlayer p, ItemEntity ie) {
        CompoundTag tag = ie.getItem().getTag();
        int moved = 0;
        if (tag != null && tag.contains(ITEMS, Tag.TAG_LIST)) {
            ListTag items = tag.getList(ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = ItemStack.of(items.getCompound(i));
                if (s.isEmpty()) continue;
                if (!p.getInventory().add(s)) p.drop(s, false);
                moved++;
            }
        }
        PlayerFaction.backpackTaken(p);
        ie.discard();
        p.sendSystemMessage(Component.literal(moved > 0
                ? "Вы забрали снаряжение из рюкзака (" + moved + " предм.)."
                : "Рюкзак пуст.").withStyle(ChatFormatting.YELLOW));
    }
}
