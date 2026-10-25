package ru.stalkerzone.item;

import net.minecraft.world.item.Item;

/** Деньги: купюры номиналом 1, 100 и 1000. */
public class RubleItem extends Item {
    public final int value;

    public RubleItem(int value) {
        super(new Item.Properties().stacksTo(64));
        this.value = value;
    }
}
