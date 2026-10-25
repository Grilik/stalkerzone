package ru.stalkerzone.gear;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import ru.stalkerzone.StalkerZoneMod;

/**
 * Пси-шлемы учёных. Прототип (ранний, слабый) держит пси-излучение лабораторий и окраин поля Выжигателя.
 * Полноценный шлем (после изучения X-16) спасает и в смертельной зоне у Радара. Оба изнашиваются.
 */
public class PsiHelmetItem extends ArmorItem {
    public final boolean prototype;

    public PsiHelmetItem(boolean prototype) {
        super(new Material(prototype), ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1));
        this.prototype = prototype;
    }

    /** Уровень защиты: 0 нет шлема, 1 прототип, 2 полноценный. Сломанный шлем не защищает. */
    public static int tier(Player p) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.HEAD);
        if (!(s.getItem() instanceof PsiHelmetItem h)) return 0;
        if (SuitItem.condition(s) < 0.05) return 0;
        return h.prototype ? 1 : 2;
    }

    /** Доля поглощённого пси-урона для уровня излучения level (1 слабое, 2 сильное, 3 смертельное). */
    public static double protection(Player p, int level) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.HEAD);
        int t = tier(p);
        if (t == 0) return 0;
        double c = 0.5 + 0.5 * SuitItem.condition(s);
        if (t == 1) return (level <= 1 ? 0.85 : level == 2 ? 0.55 : 0) * c;
        return (level <= 2 ? 0.9 : 0.65) * c;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) { return false; }

    static final class Material implements ArmorMaterial {
        private final boolean prototype;

        Material(boolean prototype) { this.prototype = prototype; }

        @Override public int getDurabilityForType(ArmorItem.Type type) { return prototype ? 220 : 500; }
        @Override public int getDefenseForType(ArmorItem.Type type) { return 1; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return StalkerZoneMod.MOD_ID + (prototype ? ":psi_helmet_proto" : ":psi_helmet"); }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    }
}
