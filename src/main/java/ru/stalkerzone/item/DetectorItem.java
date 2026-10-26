package ru.stalkerzone.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Детектор артефактов (v1.14). Работает в руке (основной или второй). Артефакты в аномалиях невидимы, пока к ним
 * не подойти с детектором нужного уровня.
 */
public class DetectorItem extends Item {
    public enum Tier {
        ECHO("Отклик", 1, 14, 2.0, "пищит чаще по мере приближения, направления не показывает"),
        BEAR("Медведь", 2, 20, 2.5, "показывает направление и расстояние"),
        VELES("Велес", 3, 28, 4.5, "показывает, где лежит артефакт, и подсвечивает его"),
        SVAROG("Сварог", 4, 40, 5.5, "находит все артефакты и выдаёт аномалии рядом");

        public final String ru, hint;
        public final int level, range;
        public final double reveal;

        Tier(String ru, int level, int range, double reveal, String hint) {
            this.ru = ru; this.level = level; this.range = range; this.reveal = reveal; this.hint = hint;
        }
    }

    public final Tier tier;

    public DetectorItem(Tier tier) {
        super(new Properties().stacksTo(1));
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> out, TooltipFlag f) {
        out.add(Component.literal("Уровень " + tier.level + ", радиус " + tier.range + " м: " + tier.hint));
        out.add(Component.literal("Держите в руке рядом с аномалиями."));
    }
}
