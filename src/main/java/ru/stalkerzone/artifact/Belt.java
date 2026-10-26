package ru.stalkerzone.artifact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.stalkerzone.gear.SuitItem;
import ru.stalkerzone.gear.Weight;
import ru.stalkerzone.sim.AnomalyType;
import ru.stalkerzone.survival.Radiation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пояс артефактов (v1.14, этап 4). Действуют артефакты в панели быстрого доступа — не больше, чем ячеек на поясе
 * костюма (без костюма — одна). Артефакты, просто лежащие в рюкзаке, фонят вполовину; в свинцовом контейнере — нет.
 * Ведьмин студень вне фарфорового контейнера разъедает всё вокруг.
 */
public final class Belt {
    private static final UUID SPEED_ID = UUID.fromString("5d3f0c1e-7a3b-4c51-9a3e-2f1c4b7d8e90");

    /** Сумма эффектов активных артефактов. */
    public static final class Sum {
        public double rad, heal, carry, speed, stamina, therm, elec, chem, grav, psi, armor;
        public int active, belt;

        double protection(AnomalyType t) {
            return switch (t) {
                case FRY, FLUFF, COMET, BRAZIER -> therm;
                case ELECTRA -> elec;
                case FOG, JELLY, RUST -> chem;
                case WEB -> 0;
                default -> grav;
            };
        }
    }

    private static final Map<UUID, Sum> LAST = new ConcurrentHashMap<>();

    /** Ячеек пояса у костюма. */
    public static int beltSize(Player p) {
        ItemStack suit = Weight.suit(p);
        if (!(suit.getItem() instanceof SuitItem si)) return 1;
        return switch (si.suit) {
            case JACKET, BANDIT -> 1;
            case ZARYA, BULAT, BERILL, MERC -> 2;
            case ECOLOG, DUTY, FREEDOM, SKAT, PROTO_SCOUT, PROTO_GUARD -> 3;
            case ECOLOG_M, SEVA, DUTY_HEAVY, FREEDOM_GUARD, MONOLITH, PROTO_EXO -> 4;
            case EXO -> 5;
        };
    }

    public static Sum compute(Player p) {
        Sum s = new Sum();
        s.belt = beltSize(p);
        for (int i = 0; i < 9 && s.active < s.belt; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!(st.getItem() instanceof ArtifactItem ai)) continue;
            ArtifactDef d = ai.def();
            if (d.caustic) continue;
            s.active++;
            s.rad += d.rad; s.heal += d.heal; s.carry += d.carry; s.speed += d.speed; s.stamina += d.stamina;
            s.therm += d.therm; s.elec += d.elec; s.chem += d.chem; s.grav += d.grav; s.psi += d.psi; s.armor += d.armor;
        }
        s.therm = cap(s.therm); s.elec = cap(s.elec); s.chem = cap(s.chem); s.grav = cap(s.grav); s.psi = cap(s.psi); s.armor = cap(s.armor);
        return s;
    }

    private static double cap(double v) { return Math.max(-0.5, Math.min(0.6, v)); }

    /** Последняя сумма для игрока (для веса, защиты в аномалиях и пси-поля); пустая, если ещё не считали. */
    public static Sum of(Player p) {
        Sum s = LAST.get(p.getUUID());
        return s == null ? new Sum() : s;
    }

    /** Множитель урона от аномалии с учётом артефактов (1 — без защиты). */
    public static double anomalyFactor(LivingEntity e, AnomalyType t) {
        if (!(e instanceof Player p)) return 1;
        return 1 - of(p).protection(t);
    }

    public static double carryBonus(Player p) { return of(p).carry; }

    public static double psi(Player p) { return of(p).psi; }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        Sum s = compute(p);
        LAST.put(p.getUUID(), s);
        AttributeInstance sp = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null) {
            AttributeModifier cur = sp.getModifier(SPEED_ID);
            if (cur == null || Math.abs(cur.getAmount() - s.speed) > 1e-6) {
                if (cur != null) sp.removeModifier(SPEED_ID);
                if (Math.abs(s.speed) > 1e-6) sp.addTransientModifier(new AttributeModifier(SPEED_ID, "stalkerzone artifacts", s.speed, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        if (p.isCreative() || p.isSpectator()) return;
        // фон артефактов: активные — полностью, лежащие в инвентаре — вполовину (контейнер глушит)
        double rad = s.rad, loose = 0;
        boolean caustic = false;
        int seen = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!(st.getItem() instanceof ArtifactItem ai)) continue;
            ArtifactDef d = ai.def();
            if (d.caustic) { caustic = true; continue; }
            boolean active = i < 9 && seen < s.belt;
            if (i < 9) seen++;
            if (!active && d.rad > 0) loose += d.rad * 0.5 * st.getCount();
        }
        rad += loose;
        if (Math.abs(rad) > 1e-6) Radiation.setDose(p, Radiation.dose(p) + rad);
        long t = p.tickCount / 20;
        if (s.heal > 0 && t % 5 == 0) p.heal((float) s.heal);
        if (s.heal < 0 && t % 5 == 0) {
            p.hurt(p.damageSources().generic(), (float) -s.heal);
            if (t % 30 == 0) p.displayClientMessage(Component.literal("Артефакт на поясе: кровотечение").withStyle(ChatFormatting.RED), true);
        }
        if (s.stamina > 0 && t % 10 == 0) p.getFoodData().eat(1, (float) (0.15 * s.stamina));
        if (caustic && t % 2 == 0) {
            p.hurt(p.damageSources().magic(), 2.0f);
            Radiation.setDose(p, Radiation.dose(p) + 4);
            p.displayClientMessage(Component.literal("Ведьмин студень разъедает снаряжение! Нужен фарфоровый контейнер.").withStyle(ChatFormatting.DARK_RED), true);
            ItemStack suit = Weight.suit(p);
            if (!suit.isEmpty()) suit.hurtAndBreak(3, p, v -> v.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
        }
    }

    /** Защита от любого урона (Медуза, Чёрные брызги) и уязвимость. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        double a = of(p).armor;
        if (Math.abs(a) > 1e-6 && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) e.setAmount((float) (e.getAmount() * (1 - a)));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { LAST.remove(e.getEntity().getUUID()); }

    public static Belt events() { return new Belt(); }
}
