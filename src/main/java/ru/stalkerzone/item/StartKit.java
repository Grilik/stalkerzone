package ru.stalkerzone.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.stalkerzone.compat.CompatConfig;
import ru.stalkerzone.compat.Tacz;
import ru.stalkerzone.gear.Suit;
import ru.stalkerzone.sim.PlayerStart;

import java.util.ArrayList;
import java.util.List;

/**
 * Выдача стартового набора новичка (карточка 010): превращает чистый список {@link PlayerStart#KIT}
 * в предметы мода и TaCZ. Здесь только сопоставление «позиция набора → предмет», сам список — в симуляции.
 */
public final class StartKit {
    private StartKit() {}

    /** Предметы стартового набора (без пустых: если TaCZ нет, оружие и патроны пропускаются). */
    public static List<ItemStack> build() {
        List<ItemStack> out = new ArrayList<>();
        CompatConfig.GunSpec pm = CompatConfig.byName("пм");
        for (PlayerStart.Kit k : PlayerStart.KIT) {
            switch (k.kind()) {
                case PM -> { if (pm != null) add(out, Tacz.gun(pm.id(), pm.mode(), pm.mag())); }
                // «два магазина» — две полные обоймы патронов к ПМ
                case PM_MAG -> { if (pm != null) for (int i = 0; i < k.count(); i++) add(out, Tacz.ammo(pm.ammo(), pm.mag())); }
                // отдельного предмета ножа в моде нет: ближний бой, как у NPC, идёт ванильным клинком
                case KNIFE -> add(out, new ItemStack(Items.IRON_SWORD));
                case JACKET -> add(out, new ItemStack(ModItems.SUITS[Suit.JACKET.ordinal()].get()));
                case BANDAGE, MEDKIT -> add(out, new ItemStack(ModItems.MEDS.get(), k.count()));
                case BREAD, CANNED -> add(out, new ItemStack(ModItems.FOOD.get(), k.count()));
                case PDA -> add(out, new ItemStack(ModItems.PDA.get()));
                case BOLT -> add(out, new ItemStack(ModItems.BOLT.get(), k.count()));
                case DETECTOR_ECHO -> add(out, new ItemStack(ModItems.DETECTORS[DetectorItem.Tier.ECHO.ordinal()].get()));
                case RUBLES -> out.addAll(ModItems.rubleStacks(k.count()));
            }
        }
        return out;
    }

    /** Выдать набор игроку; что не влезло — падает под ноги. */
    public static void give(ServerPlayer p) {
        for (ItemStack s : build()) if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void add(List<ItemStack> out, ItemStack s) {
        if (s != null && !s.isEmpty()) out.add(s);
    }
}
