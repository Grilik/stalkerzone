package ru.stalkerzone.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.gear.BackpackItem;
import ru.stalkerzone.gear.LockedSlotItem;
import ru.stalkerzone.gear.SuitItem;
import ru.stalkerzone.gear.Weight;
import ru.stalkerzone.sim.AnomalyType;

/** Клиент: индикатор веса над хотбаром, строка веса в инвентаре и подсказки на предметах. */
public final class ClientGear {
    private ClientGear() {}

    private static String line(Player p) {
        return "Вес " + Weight.format(Weight.total(p)) + " / " + Weight.format(Weight.limit(p)) + " кг";
    }

    private static int color(Player p) {
        double o = Weight.overload(p);
        return o > 1 ? 0xFF4040 : o > 0 ? 0xFFB040 : 0xD0D0D0;
    }

    @Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("weight", (gui, g, partialTick, width, height) -> {
                Minecraft mc = Minecraft.getInstance();
                Player p = mc.player;
                if (p == null || mc.options.hideGui || p.isCreative() || p.isSpectator()) return;
                String text = line(p);
                int x = width / 2 + 95, y = height - 12;
                g.drawString(mc.font, text, x, y, color(p));
            });
        }
    }

    @Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void inventory(ScreenEvent.Render.Post e) {
            if (!(e.getScreen() instanceof InventoryScreen screen)) return;
            Player p = Minecraft.getInstance().player;
            if (p == null || p.isCreative()) return;
            AbstractContainerScreen<?> s = screen;
            String text = line(p) + ", ячеек " + (9 + Weight.openMainSlots(p)) + " / 36";
            e.getGuiGraphics().drawString(Minecraft.getInstance().font, text, s.getGuiLeft(), s.getGuiTop() - 11, color(p));
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent e) {
            ItemStack s = e.getItemStack();
            if (s.getItem() instanceof LockedSlotItem) {
                e.getToolTip().add(Component.literal("Ячейка закрыта: нужен рюкзак").withStyle(ChatFormatting.GRAY));
                return;
            }
            double w = Weight.unit(s);
            if (w > 0) {
                String t = "Вес: " + Weight.format(w) + " кг" + (s.getCount() > 1 ? " (всего " + Weight.format(w * s.getCount()) + ")" : "");
                e.getToolTip().add(Component.literal(t).withStyle(ChatFormatting.GRAY));
            }
            ru.stalkerzone.compat.GunQuality.tooltip(s, e.getToolTip());
            if (s.getItem() instanceof SuitItem si) {
                double c = SuitItem.condition(s);
                e.getToolTip().add(Component.literal(si.suit.origin == ru.stalkerzone.gear.Suit.Origin.IMPORT
                        ? "С Большой земли: держит пули, хуже аномалии" : si.suit.origin == ru.stalkerzone.gear.Suit.Origin.PROTO
                        ? "Универсальный прототип: сбалансирован" : "Кустарный, сшит в Зоне" + (si.suit.faction.isEmpty() ? "" : ": «" + si.suit.faction + "»"))
                        .withStyle(si.suit.origin == ru.stalkerzone.gear.Suit.Origin.IMPORT ? ChatFormatting.AQUA
                                : si.suit.origin == ru.stalkerzone.gear.Suit.Origin.PROTO ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
                if (si.suit.psi > 0) e.getToolTip().add(Component.literal(String.format("Пси-защита: %.0f%%", si.suit.psi * 100)).withStyle(ChatFormatting.DARK_PURPLE));
                e.getToolTip().add(Component.literal(String.format("Состояние: %.0f%%", c * 100))
                        .withStyle(c > 0.6 ? ChatFormatting.GREEN : c > 0.3 ? ChatFormatting.YELLOW : ChatFormatting.RED));
                if (si.suit.carryBonus > 0) e.getToolTip().add(Component.literal("Переносимый вес: +" + (int) si.suit.carryBonus + " кг").withStyle(ChatFormatting.BLUE));
                e.getToolTip().add(Component.literal(String.format("Защита: термо %.0f%%, электро %.0f%%, химия %.0f%%, гравит. %.0f%%",
                        SuitItem.protection(s, AnomalyType.FRY) * 100, SuitItem.protection(s, AnomalyType.ELECTRA) * 100,
                        SuitItem.protection(s, AnomalyType.FOG) * 100, SuitItem.protection(s, AnomalyType.GRINDER) * 100)).withStyle(ChatFormatting.BLUE));
                if (si.suit.speed != 0) e.getToolTip().add(Component.literal(String.format("Скорость: %+.0f%%", si.suit.speed * 100)).withStyle(si.suit.speed < 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
                e.getToolTip().add(Component.literal("Ремонт: /zone repair у торговца").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (s.getItem() instanceof BackpackItem bi) {
                e.getToolTip().add(Component.literal("Ячеек: +" + bi.pack.slots + ", переносимый вес: +" + (int) bi.pack.carryBonus + " кг").withStyle(ChatFormatting.BLUE));
                e.getToolTip().add(Component.literal("Работает, если лежит в инвентаре").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
