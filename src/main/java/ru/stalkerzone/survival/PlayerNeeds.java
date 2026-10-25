package ru.stalkerzone.survival;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Усталость и сон игрока. Недосып копится со временем бодрствования (до предела примерно за два игровых дня),
 * усталость растёт от бега и падает на отдыхе. Сон в кровати снимает и то и другое.
 * Состояние хранится в постоянных данных игрока, поэтому переживает смерть.
 */
public final class PlayerNeeds {
    private static final String SLEEPY = "stalkerzone_sleepy";
    private static final String TIRED = "stalkerzone_tired";

    /** Шаг расчёта, тиков. */
    private static final int STEP = 20;
    /** Недосып за шаг: 100 за 48000 тиков (два игровых дня). */
    private static final double SLEEPY_RATE = 100.0 / (48000.0 / STEP);

    public static double sleepy(ServerPlayer p) { return data(p).getDouble(SLEEPY); }
    public static double tired(ServerPlayer p) { return data(p).getDouble(TIRED); }

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static String describe(ServerPlayer p) {
        String gear = String.format("Вес %s / %s кг, открыто ячеек %d из 36.",
                ru.stalkerzone.gear.Weight.format(ru.stalkerzone.gear.Weight.total(p)),
                ru.stalkerzone.gear.Weight.format(ru.stalkerzone.gear.Weight.limit(p)),
                9 + ru.stalkerzone.gear.Weight.openMainSlots(p));
        net.minecraft.world.item.ItemStack suit = ru.stalkerzone.gear.Weight.suit(p);
        if (!suit.isEmpty()) gear += String.format(" Костюм: %s, состояние %.0f%%.", suit.getHoverName().getString(),
                ru.stalkerzone.gear.SuitItem.condition(suit) * 100);
        return String.format("Недосып %.0f%%, усталость %.0f%%. ", sleepy(p), tired(p)) + gear + " " + Radiation.describe(p);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % STEP != 0 || p.isCreative() || p.isSpectator()) return;
        CompoundTag d = data(p);
        double sleepy = d.getDouble(SLEEPY);
        double tired = d.getDouble(TIRED);

        sleepy = Math.min(100, sleepy + SLEEPY_RATE);
        if (p.isSprinting()) tired += 1.5;
        else if (p.isSwimming()) tired += 1.2;
        else if (p.getDeltaMovement().horizontalDistanceSqr() > 0.0004) tired += 0.15;
        else tired -= 2.0;
        // у костра отдыхается лучше
        if (p.isCrouching()) tired -= 1.0;
        // перегруз выматывает: каждый шаг с лишним весом
        double over = ru.stalkerzone.gear.Weight.overload(p);
        if (over > 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.0004) tired += 0.5 + Math.min(2, over);
        tired = Math.max(0, Math.min(100, tired));

        d.putDouble(SLEEPY, sleepy);
        d.putDouble(TIRED, tired);
        apply(p, sleepy, tired);
    }

    private static void apply(ServerPlayer p, double sleepy, double tired) {
        int dur = STEP * 3;
        if (sleepy >= 60 || tired >= 70) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 0, true, false, true));
        if (sleepy >= 80 || tired >= 90) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur, 0, true, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 0, true, false, true));
        }
        if (sleepy >= 95) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dur * 2, 0, true, false, true));
            if (p.tickCount % 200 == 0) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, true, false, true));
        }
        if (tired >= 95) p.causeFoodExhaustion(0.2f);
        if (p.tickCount % 600 == 0) {
            String msg = sleepy >= 95 ? "Вы засыпаете на ходу. Найдите кровать."
                    : sleepy >= 80 ? "Глаза слипаются: нужен сон."
                    : sleepy >= 60 ? "Хочется спать."
                    : tired >= 90 ? "Вы на пределе, остановитесь и отдохните."
                    : tired >= 70 ? "Вы устали: передохните."
                    : null;
            if (msg != null) p.displayClientMessage(Component.literal(msg), true);
        }
    }

    /** Выспался: недосып и усталость сняты. Событие приходит и при вынужденном пробуждении, поэтому восстанавливаем только если наступил день. */
    @SubscribeEvent
    public void onWake(PlayerWakeUpEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.level().isDay()) return;
        CompoundTag d = data(p);
        d.putDouble(SLEEPY, 0);
        d.putDouble(TIRED, 0);
        p.displayClientMessage(Component.literal("Вы выспались."), true);
    }
}
