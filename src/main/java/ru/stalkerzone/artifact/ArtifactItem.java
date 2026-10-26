package ru.stalkerzone.artifact;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.sim.Good;

import java.util.List;

/** Артефакт: товар «Артефакты» (по цене — несколько единиц у редких) с эффектами на поясе. */
public class ArtifactItem extends GoodItem {
    public final int index;

    public ArtifactItem(int index) {
        super(Good.ARTIFACT, 16);
        this.index = index;
    }

    public ArtifactDef def() { return ArtifactDef.ALL[index]; }

    @Override
    public int units() { return def().value; }

    @Override
    public Rarity getRarity(ItemStack s) {
        return switch (def().tier) { case 1 -> Rarity.COMMON; case 2 -> Rarity.UNCOMMON; default -> Rarity.RARE; };
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> out, TooltipFlag f) {
        ArtifactDef d = def();
        out.add(Component.literal("§8" + d.note));
        for (String line : d.effects()) out.add(Component.literal(line));
        out.add(Component.literal("§7Уровень " + d.tier + ", цена: " + d.value + " ед. товара «Артефакты»"));
        out.add(Component.literal(d.caustic ? "§cХранить только в фарфоровом контейнере!" : "§8Действует в панели быстрого доступа (пояс)"));
    }
}
