package ru.stalkerzone.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.stalkerzone.StalkerZoneMod;

/**
 * Красный выброс на клиенте (карточка 030): небо и туман краснеют, нарастая к пику и спадая после,
 * а у игроков снаружи укрытий экран дополнительно темнеет красным. Без шейдеров: цвет тумана
 * подкрашивается событием {@code ComputeFogColor}, экран — полупрозрачным прямоугольником поверх GUI.
 * Данные приходят минимальным пакетом {@code Net.EmissionPhase} (сервер шлёт его каждые полсекунды).
 */
@Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEmission {
    /** Целевая «краснота» 0..1 из пакета; текущая плавно догоняет её. */
    private static float intensity, target;
    /** Игрок сейчас под открытым небом (по мнению сервера) — экран темнеет сильнее. */
    private static boolean outside;

    private ClientEmission() {}

    /** Пакет с сервера: фаза выброса (0 нет, 1 выброс, 2 затухание), яркость и «снаружи ли игрок». */
    public static void receive(int phase, float value, boolean out) {
        target = phase == 0 ? 0f : Math.max(0f, Math.min(1f, value));
        outside = out;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().level == null) {   // вышли из мира — краснота сбрасывается
            intensity = target = 0f;
            outside = false;
            return;
        }
        intensity += (target - intensity) * 0.08f;
        if (Math.abs(target - intensity) < 0.005f) intensity = target;
    }

    /** Туман (и горизонт неба) краснеет тем сильнее, чем ближе пик выброса. */
    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor e) {
        if (intensity <= 0.001f) return;
        float k = intensity * 0.85f;
        e.setRed(mix(e.getRed(), 0.55f, k));
        e.setGreen(mix(e.getGreen(), 0.06f, k));
        e.setBlue(mix(e.getBlue(), 0.06f, k));
    }

    /** Красный оттенок на весь экран: небо и воздух — всем, снаружи укрытий — заметнее. */
    @SubscribeEvent
    public static void overlay(RenderGuiEvent.Post e) {
        if (intensity <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        float a = Math.min(1f, intensity * (outside ? 0.38f : 0.14f));
        int argb = ((int) (a * 255f) << 24) | 0x8B0000;   // тёмно-красный
        e.getGuiGraphics().fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), argb);
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
