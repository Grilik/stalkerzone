package ru.stalkerzone.gear;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import ru.stalkerzone.StalkerZoneMod;

/** Костюм надевается в слот нагрудника. Прочность предмета и есть износ. */
public class SuitItem extends ArmorItem {
    public final Suit suit;

    public SuitItem(Suit suit) {
        super(new Material(suit), ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1));
        this.suit = suit;
    }

    /** Состояние 0..1: 1 новый, 0 разваливается. */
    public static double condition(ItemStack s) {
        if (!s.isDamageableItem() || s.getMaxDamage() <= 0) return 1;
        return 1 - s.getDamageValue() / (double) s.getMaxDamage();
    }

    /** Защита с учётом износа: изношенный костюм держит заметно хуже. */
    public static double protection(ItemStack s, ru.stalkerzone.sim.AnomalyType t) {
        if (!(s.getItem() instanceof SuitItem si)) return 0;
        double c = condition(s);
        return si.suit.protection(t) * (0.25 + 0.75 * c);
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false; // чинят только у торговцев и техников
    }

    static final class Material implements ArmorMaterial {
        private final Suit suit;

        Material(Suit suit) { this.suit = suit; }

        @Override public int getDurabilityForType(ArmorItem.Type type) { return suit.durability; }
        @Override public int getDefenseForType(ArmorItem.Type type) { return suit.defense; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return StalkerZoneMod.MOD_ID + ":" + suit.id; }
        @Override public float getToughness() { return suit.toughness; }
        @Override public float getKnockbackResistance() { return suit.exo() ? (suit == Suit.EXO ? 0.3f : 0.2f) : 0f; }
    }
}
