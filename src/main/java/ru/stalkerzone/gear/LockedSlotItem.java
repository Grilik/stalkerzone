package ru.stalkerzone.gear;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Заглушка закрытой ячейки инвентаря. Её нельзя выбросить, а выпавшая сразу исчезает. */
public class LockedSlotItem extends Item {
    public LockedSlotItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }
}
