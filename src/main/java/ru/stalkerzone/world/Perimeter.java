package ru.stalkerzone.world;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Посты военных (v1.14, этап 7): «войти легко, выйти с хабаром — нет». У блокпоста или базы военных игрока с
 * артефактами останавливают на досмотр: 20 секунд, чтобы договориться (/zone bribe — четверть цены хабара),
 * иначе хабар конфискуют. Сбежавшему стреляют вслед. Взятки и изъятое уходят военным через учёт симуляции.
 */
public final class Perimeter {
    private static final int RADIUS = 34, FLEE = 46, WAIT = 400, COOLDOWN = 12000;

    private static final class Check {
        int node;
        long deadline;
        long bribe;
    }

    private static final Map<UUID, Check> CHECKS = new HashMap<>();
    private static final Map<UUID, Long> CLEARED = new HashMap<>();

    private Perimeter() {}

    /** Сколько единиц товара «Артефакты» у игрока (редкие стоят нескольких). */
    static int artifactUnits(ServerPlayer p) {
        int u = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof GoodItem gi && gi.good == Good.ARTIFACT) u += gi.units() * s.getCount();
        }
        return u;
    }

    private static Node postNear(ZoneData d, ServerPlayer p, Faction mil, int r) {
        for (Node n : d.sim.nodes) {
            if (n.owner != mil.id) continue;
            if (Math.abs(n.x * ZoneBounds.SCALE - p.getX()) < r && Math.abs(n.z * ZoneBounds.SCALE - p.getZ()) < r) return n;
        }
        return null;
    }

    public static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (t % 20 != 0) return;
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        ZoneData d = ZoneData.get(server);
        Faction mil = d.sim.military();
        if (mil == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            UUID id = p.getUUID();
            Check c = CHECKS.get(id);
            if (c != null) {
                Node n = d.sim.nodes.get(c.node);
                double dist = Math.hypot(n.x * ZoneBounds.SCALE - p.getX(), n.z * ZoneBounds.SCALE - p.getZ());
                if (dist > FLEE) {   // сбежал
                    CHECKS.remove(id);
                    CLEARED.put(id, t + COOLDOWN);
                    p.hurt(level.damageSources().generic(), 6.0f);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                    level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5f, 2.0f);
                    p.sendSystemMessage(Component.literal("Вдогонку стреляют: «Стой, стрелять буду!» Пуля задела ногу.").withStyle(ChatFormatting.RED));
                } else if (t >= c.deadline) {   // конфискация
                    CHECKS.remove(id);
                    CLEARED.put(id, t + COOLDOWN);
                    int taken = 0;
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack s = p.getInventory().getItem(i);
                        if (s.getItem() instanceof GoodItem gi && gi.good == Good.ARTIFACT) {
                            taken += gi.units() * s.getCount();
                            p.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                    d.sim.playerConfiscated(mil, Good.ARTIFACT, taken);
                    d.setDirty();
                    p.sendSystemMessage(Component.literal("Военные изъяли хабар («до выяснения»): " + taken + " ед. артефактов. Контейнеры не тронули.")
                            .withStyle(ChatFormatting.RED));
                } else if ((c.deadline - t) % 100 == 0) {
                    p.displayClientMessage(Component.literal("Досмотр: " + (c.deadline - t) / 20 + " с. /zone bribe — " + c.bribe + " руб.").withStyle(ChatFormatting.GOLD), true);
                }
                continue;
            }
            Long cl = CLEARED.get(id);
            if (cl != null && cl > t) continue;
            Node post = postNear(d, p, mil, RADIUS);
            if (post == null) continue;
            int units = artifactUnits(p);
            if (units <= 0) continue;
            Check nc = new Check();
            nc.node = post.id;
            nc.deadline = t + WAIT;
            nc.bribe = Math.max(200, units * d.sim.buyPrice(post, Good.ARTIFACT) / 4);
            CHECKS.put(id, nc);
            level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.0f, 0.6f);
            p.sendSystemMessage(Component.literal("«" + post.name + "»: Стоять! Досмотр. Артефакты есть? Есть. Сдаём… или договоримся? (/zone bribe — "
                    + nc.bribe + " руб., 20 секунд)").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Взятка на посту. */
    public static String bribe(ServerPlayer p, ZoneData d) {
        Check c = CHECKS.get(p.getUUID());
        if (c == null) return "Вас никто не досматривает.";
        long cash = ru.stalkerzone.npc.TradeService.countRubles(p);
        if (cash < c.bribe) return "Сержант смотрит на ваши " + cash + " руб. и качает головой: нужно " + c.bribe + ".";
        ru.stalkerzone.npc.TradeService.takeRubles(p, c.bribe);
        d.sim.playerBribe(d.sim.military(), c.bribe);
        d.setDirty();
        CHECKS.remove(p.getUUID());
        CLEARED.put(p.getUUID(), p.getServer().getTickCount() + (long) COOLDOWN);
        return "Сержант пересчитал купюры: «Проходи. Я тебя не видел». Взятка " + c.bribe + " руб.";
    }
}
