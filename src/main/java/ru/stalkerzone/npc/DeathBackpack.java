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

    private DeathBackpack() {}

    /**
     * Положить рюкзак на месте смерти игрока со всем его снаряжением (П-10: сущность-рюкзак на земле,
     * ПКМ — окно; раньше — выпавший предмет, по которому ПКМ почти не попадал).
     */
    public static void place(ServerPlayer p, ListTag items) {
        ServerLevel level = p.serverLevel();
        DeathBackpackEntity e = ModEntities.DEATH_BACKPACK.get().create(level);
        if (e == null) return;
        int slot = 0;
        for (int i = 0; i < items.size() && slot < DeathBackpackEntity.SIZE; i++) {
            ItemStack s = ItemStack.of(items.getCompound(i));
            if (!s.isEmpty()) e.items.setItem(slot++, s);
        }
        e.owner = p.getGameProfile().getName();
        double y = Math.max(p.getY(), level.getMinBuildHeight() + 1);
        e.moveTo(p.getX(), y, p.getZ(), p.getYRot(), 0);
        level.addFreshEntity(e);
    }

    /** Старый рюкзак-предмет (миры до П-10): ПКМ по нему по-прежнему отдаёт всё. */
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
