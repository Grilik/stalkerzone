package ru.stalkerzone.anomaly;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.DetectorItem;
import ru.stalkerzone.item.ModItems;

import java.util.Iterator;

/**
 * Скрытые артефакты (v1.14, этап 2). Созревший в аномалии артефакт не лежит на виду, как раньше, а «спрятан»: он
 * записан в данные мира и появляется предметом, только когда игрок подходит к нему с детектором подходящего уровня.
 * Детектор пищит чаще по мере приближения; старшие показывают направление, место и выдают аномалии рядом.
 */
public final class Artifacts {
    /** Уровень артефакта (порядок как в AnomalyType): 1 — простые, 3 — редкие. Детектор берёт свой уровень и ниже. */
    public static final int[] TIER = {3, 2, 1, 3, 2, 1, 2, 1};
    /** Больше стольких спрятанных артефактов у одной аномалии не копится (остальные «ждут» в симуляции). */
    public static final int CAP_PER_FIELD = 3;

    private Artifacts() {}

    public static int tierOf(int type) { return type >= 0 && type < TIER.length ? TIER[type] : 1; }

    /** Сколько спрятанных артефактов уже лежит в радиусе r от точки. */
    public static int hiddenNear(ZoneData d, double x, double z, double r) {
        int n = 0;
        for (int[] h : d.hidden) if (Math.hypot(h[0] - x, h[2] - z) <= r) n++;
        return n;
    }

    public static void hide(ZoneData d, int type, double x, double y, double z) {
        if (d.hidden.size() >= 600) d.hidden.remove(0);   // самые старые «уходят в землю»
        d.hidden.add(new int[] {(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), type});
        d.setDirty();
    }

    private static DetectorItem.Tier held(ServerPlayer p) {
        DetectorItem.Tier best = null;
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.getItem() instanceof DetectorItem di && (best == null || di.tier.level > best.level)) best = di.tier;
        }
        return best;
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (t % 5 != 0) return;
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        ZoneData d = ZoneData.get(server);
        boolean harvest = t % 20 == 0 ? (HARVEST = EmissionEvent.harvest(server)) : HARVEST;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (harvest) harvest(level, d, p, t);
            DetectorItem.Tier tier = held(p);
            if (tier == null) continue;
            if (tier == DetectorItem.Tier.SVAROG && t % 20 == 0) AnomalyManager.revealNear(p.getX(), p.getZ(), 24, t + 60);
            int[] best = null;
            double bd = Double.MAX_VALUE;
            for (int[] h : d.hidden) {
                if (tierOf(h[3]) > tier.level) continue;
                double dist = Math.sqrt(p.distanceToSqr(h[0] + 0.5, h[1] + 0.5, h[2] + 0.5));
                if (dist < bd) { bd = dist; best = h; }
            }
            if (best == null || bd > tier.range) {
                if (t % 40 == 0) p.displayClientMessage(Component.literal("§7" + tier.ru + ": тихо"), true);
                continue;
            }
            if (bd <= tier.reveal) {
                materialize(level, d, best, tier.level >= 3);
                p.displayClientMessage(Component.literal("§a" + tier.ru + ": артефакт здесь!"), true);
                p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.8f, 1.6f);
                continue;
            }
            // частота писка: от раза в 2 с на краю до 4 раз в секунду рядом
            double k = bd / tier.range;
            int period = Math.max(5, (int) Math.round(5 + 35 * k) / 5 * 5);
            if (t % period == 0) p.playNotifySound(tier.level == 1 ? SoundEvents.NOTE_BLOCK_BIT.get() : SoundEvents.NOTE_BLOCK_PLING.get(),
                    SoundSource.PLAYERS, 0.6f, (float) (1.9 - k));
            if (t % 10 != 0) continue;
            int bars = 1 + (int) Math.round((1 - k) * 4);
            String sig = "●".repeat(bars) + "○".repeat(5 - bars);
            if (tier.level == 1) {
                p.displayClientMessage(Component.literal("§e" + tier.ru + ": " + sig), true);
                continue;
            }
            double ang = Math.toDegrees(Math.atan2(-(best[0] + 0.5 - p.getX()), best[2] + 0.5 - p.getZ()));
            double rel = ((ang - p.getYRot()) % 360 + 360 + 22.5) % 360;
            String arrow = ARROWS[(int) (rel / 45) % 8];
            p.displayClientMessage(Component.literal("§e" + tier.ru + ": " + sig + "  " + arrow + " " + (int) bd + " м"), true);
            if (tier.level >= 3) level.sendParticles(p, ParticleTypes.END_ROD, true, best[0] + 0.5, best[1] + 0.8, best[2] + 0.5, 3, 0.15, 0.3, 0.15, 0.01);
        }
    }

    private static boolean HARVEST;

    /** «Рыбалка после дождя»: свежие артефакты мерцают и находятся руками, без детектора. */
    private static void harvest(ServerLevel level, ZoneData d, ServerPlayer p, long t) {
        int[] grab = null;
        for (int[] h : d.hidden) {
            double dist = Math.sqrt(p.distanceToSqr(h[0] + 0.5, h[1] + 0.5, h[2] + 0.5));
            if (dist > 24) continue;
            if (dist <= 1.6) grab = h;
            else if (t % 40 == (h[0] & 31)) level.sendParticles(p, ParticleTypes.GLOW, true, h[0] + 0.5, h[1] + 0.3, h[2] + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
        }
        if (grab != null) {
            materialize(level, d, grab, true);
            p.displayClientMessage(Component.literal("§aЗдесь что-то блестит… артефакт!"), true);
        }
    }

    /** Артефакт проявляется: становится предметом на земле. */
    private static void materialize(ServerLevel level, ZoneData d, int[] h, boolean glow) {
        for (Iterator<int[]> it = d.hidden.iterator(); it.hasNext(); ) if (it.next() == h) { it.remove(); break; }
        d.setDirty();
        int type = Math.max(0, Math.min(ModItems.ARTIFACTS.length - 1, h[3]));
        ItemEntity item = new ItemEntity(level, h[0] + 0.5, h[1] + 0.6, h[2] + 0.5, new ItemStack(ModItems.ARTIFACTS[type].get()));
        item.setDeltaMovement(0, 0.15, 0);
        item.setUnlimitedLifetime();
        if (glow) item.setGlowingTag(true);
        level.addFreshEntity(item);
        level.sendParticles(ParticleTypes.END_ROD, h[0] + 0.5, h[1] + 0.8, h[2] + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
    }
}
