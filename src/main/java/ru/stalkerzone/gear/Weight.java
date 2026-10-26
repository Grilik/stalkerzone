package ru.stalkerzone.gear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.registries.ForgeRegistries;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.item.RubleItem;

/**
 * Переносимый вес и число открытых ячеек. Считается одинаково на сервере и на клиенте
 * (инвентарь у клиента синхронизирован), поэтому и индикатор, и запрет прыжка совпадают с сервером.
 */
public final class Weight {
    /** Предел без снаряжения, кг. Выше начинается перегруз. */
    public static final double BASE_LIMIT = 50;
    /** Сверх предела на столько кг игрок почти не может идти. */
    public static final double HARD_EXTRA = 20;
    /** Открытых ячеек основного инвентаря без рюкзака (хотбар открыт всегда). */
    public static final int BASE_MAIN_SLOTS = 9;

    private Weight() {}

    /** Вес одной штуки, кг. */
    public static double unit(ItemStack s) {
        Item it = s.getItem();
        if (s.isEmpty() || it instanceof LockedSlotItem || it instanceof RubleItem) return 0;
        if (it instanceof SuitItem si) return si.suit.weight;
        if (it instanceof BackpackItem bi) return bi.pack.weight;
        if (it instanceof ru.stalkerzone.mutant.MutantPartItem mp) return mp.type.partWeight;
        if (it instanceof GoodItem gi) {
            return switch (gi.good) {
                case FOOD -> 0.4;
                case PARTS -> 0.5;
                case AMMO -> 0.3;
                case MEDS -> 0.2;
                case GEAR -> 3.0;
                case ARTIFACT -> 0.5;
                case MATERIALS -> 1.5;
                case PROTO -> 3.5;
                case AMMO_PRO -> 0.3;
                case GEAR_PRO -> 3.5;
            };
        }
        var key = ForgeRegistries.ITEMS.getKey(it);
        if (key != null && key.getNamespace().equals("tacz")) return 3.5;
        if (it instanceof ArmorItem a) return 1 + a.getDefense() * 1.2;
        if (it instanceof BowItem || it instanceof CrossbowItem || it instanceof ShieldItem) return 1.5;
        if (it instanceof TieredItem) return 1.5;
        if (it instanceof BlockItem) return 0.8;
        if (s.isEdible()) return 0.3;
        return 0.1;
    }

    public static double total(Player p) {
        Inventory inv = p.getInventory();
        double sum = 0;
        for (ItemStack s : inv.items) sum += unit(s) * s.getCount();
        for (ItemStack s : inv.armor) sum += unit(s) * s.getCount();
        for (ItemStack s : inv.offhand) sum += unit(s) * s.getCount();
        return sum;
    }

    public static ItemStack suit(Player p) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.CHEST);
        return s.getItem() instanceof SuitItem ? s : ItemStack.EMPTY;
    }

    /** Лучший рюкзак из инвентаря. */
    public static Backpack backpack(Player p) {
        Backpack best = null;
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof BackpackItem bi && (best == null || bi.pack.ordinal() > best.ordinal())) best = bi.pack;
        }
        return best;
    }

    public static double limit(Player p) {
        double l = BASE_LIMIT;
        ItemStack suit = suit(p);
        if (suit.getItem() instanceof SuitItem si) {
            // изношенный экзоскелет тащит меньше: сервоприводы сдают
            l += si.suit.carryBonus * (si.suit.exo() ? 0.4 + 0.6 * SuitItem.condition(suit) : 1);
        }
        Backpack b = backpack(p);
        if (b != null) l += b.carryBonus;
        l += ru.stalkerzone.artifact.Belt.carryBonus(p);   // v1.14: Грави, Ночная звезда, Золотая рыбка
        return Math.max(10, l);
    }

    public static int openMainSlots(Player p) {
        Backpack b = backpack(p);
        return Math.min(27, BASE_MAIN_SLOTS + (b == null ? 0 : b.slots));
    }

    /** 0 не перегружен, 0..1 перегруз, больше 1: не может идти. */
    public static double overload(Player p) {
        if (p.isCreative() || p.isSpectator()) return 0;
        double lim = limit(p);
        double w = total(p);
        return w <= lim ? 0 : (w - lim) / HARD_EXTRA;
    }

    public static String format(double kg) {
        return String.format(java.util.Locale.ROOT, "%.1f", kg);
    }
}
