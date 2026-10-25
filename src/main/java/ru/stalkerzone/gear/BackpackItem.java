package ru.stalkerzone.gear;

import net.minecraft.world.item.Item;

/** Рюкзак работает, если лежит в инвентаре (считается лучший). */
public class BackpackItem extends Item {
    public final Backpack pack;

    public BackpackItem(Backpack pack) {
        super(new Item.Properties().stacksTo(1));
        this.pack = pack;
    }
}
