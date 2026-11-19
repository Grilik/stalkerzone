package ru.stalkerzone.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.stalkerzone.StalkerZoneMod;

/**
 * Э1-11Б: кнопка «Зона» в меню создания мира. Добавляется к ванильному {@link CreateWorldScreen}
 * после его инициализации; обычное создание миров не меняется. Нажатие открывает {@link ZoneWorldScreen}
 * (имя мира → независимая копия золотой копии TheZone → вход в мир).
 */
@Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ZoneCreateWorld {
    private ZoneCreateWorld() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof CreateWorldScreen screen)) return;
        String createLabel = Component.translatable("selectWorld.create").getString();
        String cancelLabel = CommonComponents.GUI_CANCEL.getString();
        Button create = null;
        Button cancel = null;
        for (Renderable r : screen.renderables) {
            if (!(r instanceof Button b)) continue;
            String msg = b.getMessage().getString();
            if (create == null && createLabel.equals(msg)) create = b;
            else if (cancel == null && cancelLabel.equals(msg)) cancel = b;
        }
        if (create == null || cancel == null) return; // раскладка изменилась — не мешаем ванильному экрану
        int[] xy = ZoneButtonLayout.place(screen.width, create.getX(), create.getY(), create.getWidth(),
                cancel.getX(), cancel.getWidth());
        e.addListener(Button.builder(Component.literal("Зона"),
                        b -> screen.getMinecraft().setScreen(new ZoneWorldScreen(screen)))
                .bounds(xy[0], xy[1], ZoneButtonLayout.WIDTH, ZoneButtonLayout.HEIGHT).build());
    }
}
