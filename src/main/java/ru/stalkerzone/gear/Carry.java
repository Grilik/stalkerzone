package ru.stalkerzone.gear;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.stalkerzone.item.ModItems;

import java.util.UUID;

/**
 * Ограничение инвентаря, перегруз и износ костюма от обычных попаданий.
 * Закрытые ячейки заполняются заглушкой; предмет, оказавшийся в закрытой ячейке, перекладывается в свободную
 * или выпадает под ноги.
 */
public final class Carry {
    private static final UUID SPEED_ID = UUID.fromString("5a3c1f9e-6a43-4d7b-9a51-3e0f3b7d2c11");

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        double over = Weight.overload(p);
        // спринт и прыжок решает клиент, поэтому запрет работает на обеих сторонах
        if (over > 0.5 && p.isSprinting()) p.setSprinting(false);
        if (!(p instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 2 == 0) enforceSlots(sp);
        if (sp.tickCount % 10 == 0) applySpeed(sp, over);
        if (over > 1 && sp.tickCount % 60 == 0) {
            sp.displayClientMessage(Component.literal("Вы перегружены и почти не можете идти: "
                    + Weight.format(Weight.total(sp)) + " / " + Weight.format(Weight.limit(sp)) + " кг"), true);
        } else if (over > 0 && sp.tickCount % 200 == 0) {
            sp.displayClientMessage(Component.literal("Перегруз: " + Weight.format(Weight.total(sp)) + " / " + Weight.format(Weight.limit(sp)) + " кг"), true);
        }
    }

    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof Player p && Weight.overload(p) > 1) {
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x, 0, d.z);
        }
    }

    /** Обычные попадания: изношенный костюм пропускает больше. Прочность тратит сама игра. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack suit = Weight.suit(p);
        if (suit.isEmpty()) return;
        double wear = 1 - SuitItem.condition(suit);
        if (wear > 0) e.setAmount((float) (e.getAmount() * (1 + 0.35 * wear)));
    }

    private static void applySpeed(ServerPlayer p, double over) {
        double mod = 0;
        ItemStack suit = Weight.suit(p);
        if (suit.getItem() instanceof SuitItem si) mod += si.suit.speed;
        if (over > 1) mod -= 0.85;
        else if (over > 0) mod -= 0.1 + 0.4 * over;
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(SPEED_ID);
        if (cur != null && Math.abs(cur.getAmount() - mod) < 0.01) return;
        if (cur != null) inst.removeModifier(SPEED_ID);
        if (mod != 0) inst.addTransientModifier(new AttributeModifier(SPEED_ID, "stalkerzone_weight", mod, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static boolean isLock(ItemStack s) {
        return s.getItem() instanceof LockedSlotItem;
    }

    private static void enforceSlots(ServerPlayer p) {
        Inventory inv = p.getInventory();
        boolean free = p.isCreative() || p.isSpectator();
        int open = free ? 27 : Weight.openMainSlots(p);
        boolean changed = false;
        java.util.List<ItemStack> evicted = new java.util.ArrayList<>();
        // 0-8 хотбар, 9-35 основной инвентарь
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            boolean locked = i >= 9 + open;
            if (locked) {
                if (isLock(s)) continue;
                if (!s.isEmpty()) evicted.add(s);
                inv.items.set(i, new ItemStack(ModItems.LOCKED_SLOT.get()));
                changed = true;
            } else if (isLock(s)) {
                inv.items.set(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        for (int i = 0; i < inv.armor.size(); i++) if (isLock(inv.armor.get(i))) { inv.armor.set(i, ItemStack.EMPTY); changed = true; }
        for (int i = 0; i < inv.offhand.size(); i++) if (isLock(inv.offhand.get(i))) { inv.offhand.set(i, ItemStack.EMPTY); changed = true; }
        // заглушки не должны уходить ни на курсор, ни в сундуки
        if (isLock(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        for (Slot slot : p.containerMenu.slots) {
            if (slot.container != inv && isLock(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
        boolean dropped = false;
        for (ItemStack s : evicted) {
            if (!inv.add(s) && !s.isEmpty()) {
                p.drop(s, false);
                dropped = true;
            }
        }
        if (dropped) p.displayClientMessage(Component.literal("Нет места: часть вещей выпала. Нужен рюкзак."), true);
        if (changed) inv.setChanged();
    }
}
