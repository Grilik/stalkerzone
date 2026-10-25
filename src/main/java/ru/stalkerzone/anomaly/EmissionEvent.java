package ru.stalkerzone.anomaly;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Выброс как событие в мире: предупреждение, гроза, урон тем, кто остался под открытым небом. */
public final class EmissionEvent {
    private static final int DURATION = 1200;
    private static int ticksLeft;

    private EmissionEvent() {}

    public static boolean active() { return ticksLeft > 0; }

    public static void warn(MinecraftServer server) {
        for (ServerPlayer p : server.overworld().players()) {
            p.sendSystemMessage(Component.literal("Приближается выброс! Найдите укрытие под крышей.").withStyle(ChatFormatting.RED));
        }
    }

    public static void start(MinecraftServer server) {
        ticksLeft = DURATION;
        ServerLevel level = server.overworld();
        level.setWeatherParameters(0, DURATION, true, true);
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("ВЫБРОС").withStyle(ChatFormatting.DARK_RED)));
        }
    }

    public static void tick(MinecraftServer server) {
        if (ticksLeft <= 0) return;
        ticksLeft--;
        if (ticksLeft % 40 != 0) return;
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (level.canSeeSky(p.blockPosition())) {
                p.hurt(level.damageSources().magic(), 3.0f);
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, true, true));
                p.displayClientMessage(Component.literal("Под открытым небом нельзя: спрячьтесь под крышу!").withStyle(ChatFormatting.RED), true);
            }
        }
        if (ticksLeft == 0) {
            for (ServerPlayer p : level.players()) p.displayClientMessage(Component.literal("Выброс закончился."), true);
        }
    }
}
