package ru.stalkerzone.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Радиация (v1.11): ржавый металл Зоны фонит. Брошенные машины, бочки, профлист с тегом
 * {@code stalkerzone:radioactive} дают дозу тем больше, чем их больше рядом и чем ближе игрок;
 * бочки с отходами ({@code radioactive_strong}) фонят в разы сильнее. Доза медленно выводится сама,
 * быстрее — медикаментами. Научный костюм защищает лучше всего (химзащита костюма).
 */
public final class Radiation {
    public static final TagKey<Block> RADIOACTIVE = TagKey.create(Registries.BLOCK, new ResourceLocation("stalkerzone", "radioactive"));
    public static final TagKey<Block> STRONG = TagKey.create(Registries.BLOCK, new ResourceLocation("stalkerzone", "radioactive_strong"));
    private static final String KEY = "stalkerzone_rad";
    private static final int R = 4;

    public static double dose(ServerPlayer p) { return p.getPersistentData().getDouble(KEY); }

    public static void setDose(ServerPlayer p, double v) { p.getPersistentData().putDouble(KEY, Math.max(0, Math.min(1000, v))); }

    /** Снять дозу (медикаменты). */
    public static void cure(ServerPlayer p, double amount) { setDose(p, dose(p) - amount); }

    /** Мощность фона в точке: сумма источников вокруг, ослабленная квадратом расстояния. */
    public static double field(ServerPlayer p) {
        BlockPos c = p.blockPosition();
        double sum = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) for (int dy = -2; dy <= 3; dy++) for (int dz = -R; dz <= R; dz++) {
            m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            BlockState st = p.level().getBlockState(m);
            if (st.isAir()) continue;
            double w = st.is(STRONG) ? 6 : st.is(RADIOACTIVE) ? 1 : 0;
            if (w > 0) sum += w / (1 + dx * dx + dy * dy + dz * dz);
        }
        return sum;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 20 != 0 || p.isCreative() || p.isSpectator()) return;
        double field = field(p);
        net.minecraft.world.item.ItemStack suit = ru.stalkerzone.gear.Weight.suit(p);
        double protect = 0;
        if (!suit.isEmpty() && suit.getItem() instanceof ru.stalkerzone.gear.SuitItem si)
            protect = si.suit.chemical * ru.stalkerzone.gear.SuitItem.condition(suit);
        double d = dose(p) + field * 0.6 * (1 - protect) - 0.15;
        setDose(p, d);
        d = dose(p);
        int dur = 60;
        if (d >= 60) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, dur, 0, true, false, true));
        if (d >= 120) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 0, true, false, true));
        if (d >= 200) p.addEffect(new MobEffectInstance(MobEffects.POISON, dur, 0, true, false, true));
        if (d >= 320 && p.tickCount % 60 == 0) p.hurt(p.damageSources().magic(), 1.0f);
        if (field > 0.4 && p.tickCount % 40 == 0) {
            String level = field > 4 ? "очень сильный" : field > 1.5 ? "сильный" : "слабый";
            p.displayClientMessage(Component.literal("☢ Счётчик трещит: " + level + " фон, доза " + Math.round(d)), true);
        } else if (d >= 60 && p.tickCount % 200 == 0) {
            p.displayClientMessage(Component.literal("☢ Доза радиации " + Math.round(d) + ": нужны медикаменты"), true);
        }
    }

    /** Для описания состояния игрока. */
    public static String describe(ServerPlayer p) {
        return String.format("Доза радиации %.0f, фон %.1f.", dose(p), field(p));
    }

    private Radiation() {}

    public static Radiation events() { return new Radiation(); }
}
