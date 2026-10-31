package ru.stalkerzone.item;

import net.minecraft.world.item.Item;

/**
 * Рюкзак на месте гибели игрока (карточка 030). Лежит на земле как предмет и не подбирается,
 * пока по нему не щёлкнут ПКМ: тогда {@code npc.DeathBackpack} отдаёт игроку всё содержимое.
 */
public class DeathBackpackItem extends Item {
    public DeathBackpackItem() {
        super(new Item.Properties().stacksTo(1));
    }
}
