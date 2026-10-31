package ru.stalkerzone.anomaly;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.gear.PsiHelmetItem;
import ru.stalkerzone.lab.LabManager;
import ru.stalkerzone.npc.StalkerEntity;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.LabLayout;

/**
 * Пси-излучение. Уровни: 1 слабое (X-16), 2 сильное (X-10, X-2, окраины поля Выжигателя),
 * 3 смертельное (северная часть Радара, пока работает Выжигатель): без полноценного пси-шлема человек
 * сразу превращается в зомби. Прототип шлема держит уровни 1 и 2, но не 3.
 */
public final class PsiField {
    private PsiField() {}

    /** Уровень излучения в точке на поверхности (0, если Выжигатель отключён или далеко). */
    public static int surfaceLevel(Simulation sim, double x, double z) {
        return ru.stalkerzone.sim.Psi.surfaceLevel(sim, x, z);
    }

    public static int labLevel(String code) {
        return switch (code) {
            case "x16" -> 1;
            case "x10", "x2" -> 2;
            default -> 0;
        };
    }

    /** Вызывать раз в 2 секунды. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ZoneData data = ZoneData.get(server);
        Simulation sim = data.sim;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            LabLayout lab = LabManager.inside(data, p.getX(), p.getY(), p.getZ());
            int lv = lab != null ? labLevel(lab.lab.code) : surfaceLevel(sim, p.getX(), p.getZ());
            if (lv > 0) affect(level, p, lv);
        }
        // NPC в смертельной зоне превращаются в зомби (Монолит невосприимчив)
        if (sim.scorcher.disabled) return;
        // карточка 024: в Кордоне 1.0 Выжигателя нет — пси-поле «всей карты» здесь никого не зомбирует
        if (ru.stalkerzone.sim.Scenarios.KORDON.equals(sim.scenario)) return;
        int[] src = ru.stalkerzone.sim.Psi.source(sim);
        if (src == null) return;
        AABB box = new AABB(src[0] - ru.stalkerzone.sim.Psi.LETHAL, level.getMinBuildHeight(), src[1] - ru.stalkerzone.sim.Psi.LETHAL,
                src[0] + ru.stalkerzone.sim.Psi.LETHAL, level.getMaxBuildHeight(), src[1] + ru.stalkerzone.sim.Psi.LETHAL);
        for (StalkerEntity e : level.getEntitiesOfClass(StalkerEntity.class, box, e -> e.isAlive() && !e.isTrader() && e.faction() != ru.stalkerzone.sim.ZoneScenario.MONOLITH)) {
            if (surfaceLevel(sim, e.getX(), e.getZ()) < 3 || LabManager.inside(data, e.getX(), e.getY(), e.getZ()) != null) continue;
            BlockPos at = e.blockPosition();
            String name = e.hasCustomName() ? e.getCustomName().getString() : "сталкер";
            e.discard();
            zombie(level, at, name);
        }
    }

    private static void affect(ServerLevel level, ServerPlayer p, int lv) {
        double prot = PsiHelmetItem.protection(p, lv);
        // костюмы Монолита и научные немного гасят пси-излучение (смертельную зону без шлема не держат)
        ItemStack suit = p.getItemBySlot(EquipmentSlot.CHEST);
        if (suit.getItem() instanceof ru.stalkerzone.gear.SuitItem si && si.suit.psi > 0)
            prot = 1 - (1 - prot) * (1 - si.suit.psi * ru.stalkerzone.gear.SuitItem.condition(suit));
        // v1.14: Каменный цветок, Лунный свет и другие пси-артефакты на поясе
        double art = ru.stalkerzone.artifact.Belt.psi(p);
        if (art > 0) prot = 1 - (1 - prot) * (1 - art);
        int tier = PsiHelmetItem.tier(p);
        ItemStack helmet = p.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof PsiHelmetItem) helmet.hurtAndBreak(lv, p, x -> x.broadcastBreakEvent(EquipmentSlot.HEAD));
        level.sendParticles(p, ParticleTypes.WITCH, true, p.getX(), p.getY() + 1.6, p.getZ(), 4 * lv, 0.4, 0.4, 0.4, 0.02);
        if (lv >= 3 && tier < 2) {
            // смертельная зона: человек превращается в зомби
            String name = p.getGameProfile().getName();
            BlockPos at = p.blockPosition();
            p.sendSystemMessage(Component.literal("Выжигатель выжег ваш разум. Без полноценного пси-шлема сюда не войти.").withStyle(ChatFormatting.DARK_PURPLE));
            p.hurt(level.damageSources().magic(), Float.MAX_VALUE);
            zombie(level, at, name);
            return;
        }
        float base = lv == 1 ? 1.5f : lv == 2 ? 3.0f : 4.0f;
        float dmg = (float) (base * (1 - prot));
        if (dmg > 0.2f) p.hurt(level.damageSources().magic(), dmg);
        if (prot < 0.6) {
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, true, false));
            if (lv >= 2) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false));
        }
        String msg = lv >= 3 ? "Пси-излучение Выжигателя! Шлем едва держит"
                : tier == 0 ? "Пси-излучение! Без пси-шлема здесь долго не протянуть"
                : prot < 0.6 ? "Пси-излучение сильнее, чем рассчитан шлем" : null;
        if (msg != null) p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** Зомбированный: там, где человек попал под смертельное излучение. */
    private static void zombie(ServerLevel level, BlockPos at, String name) {
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null) return;
        z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        z.setCustomName(Component.literal("Зомбированный " + name));
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        z.setDropChance(EquipmentSlot.HEAD, 0f);
        z.setDropChance(EquipmentSlot.CHEST, 0f);
        z.setPersistenceRequired();
        z.addTag("stalkerzone_psi_zombie");
        level.addFreshEntity(z);
    }
}
