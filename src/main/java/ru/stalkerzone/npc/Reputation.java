package ru.stalkerzone.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Репутация игрока у группировок и сталкерский кодекс (v1.14, этап 8). Хранится у игрока (−100…100).
 * <ul>
 *   <li>Убил бойца группировки: −25 у неё, +8 у её заклятых врагов. При −40 и ниже её бойцы стреляют первыми.</li>
 *   <li>Кодекс: поднял раненого (аптечка) — +10; кто ведёт себя по-сталкерски, тому и лидеры рады.</li>
 *   <li>Бар нейтральный: Долг требует убрать оружие; застава Долга при входе, «врагов Долга» не пускает.</li>
 * </ul>
 */
public final class Reputation {
    private static final String KEY = "sz_rep";
    public static final int HOSTILE = -40, FRIEND = 40;

    private Reputation() {}

    /** v1.16: репутация лежит в PlayerPersisted и переживает смерть (раньше сбрасывалась при возрождении). */
    static net.minecraft.nbt.CompoundTag store(ServerPlayer p) {
        net.minecraft.nbt.CompoundTag root = p.getPersistentData();
        net.minecraft.nbt.CompoundTag per = root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, per);
        if (!per.contains(KEY) && root.contains(KEY)) { per.putIntArray(KEY, root.getIntArray(KEY)); root.remove(KEY); }
        return per;
    }

    public static int get(ServerPlayer p, int faction) {
        int[] r = store(p).getIntArray(KEY);
        return faction >= 0 && faction < r.length ? r[faction] : 0;
    }

    public static void add(ServerPlayer p, Simulation sim, int faction, int delta, String why) {
        if (faction < 0 || faction >= sim.factions.size() || delta == 0) return;
        int[] r = store(p).getIntArray(KEY);
        if (r.length < sim.factions.size()) r = java.util.Arrays.copyOf(r, sim.factions.size());
        int before = r[faction];
        r[faction] = Math.max(-100, Math.min(100, r[faction] + delta));
        store(p).putIntArray(KEY, r);
        Faction f = sim.factions.get(faction);
        if (why != null) p.displayClientMessage(Component.literal("«" + f.name + "»: " + (delta > 0 ? "+" : "") + delta + " (" + why + ")")
                .withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        if (before > HOSTILE && r[faction] <= HOSTILE)
            p.sendSystemMessage(Component.literal("«" + f.name + "» считают вас врагом: их бойцы стреляют без предупреждения.").withStyle(ChatFormatting.RED));
        if (before < FRIEND && r[faction] >= FRIEND)
            p.sendSystemMessage(Component.literal("«" + f.name + "» считают вас своим.").withStyle(ChatFormatting.GREEN));
        PlayerFaction.checkStanding(p, sim);
    }

    /** Игрок убил бойца группировки. */
    public static void killed(ServerPlayer p, Simulation sim, int faction) {
        if (faction < 0 || faction >= sim.factions.size()) return;
        add(p, sim, faction, -25, "убит их боец");
        for (Faction o : sim.factions) if (o.id != faction && sim.hostilityTo(o, faction) >= 0.6) add(p, sim, o.id, 8, null);
    }

    public static boolean hostile(ServerPlayer p, int faction) { return get(p, faction) <= HOSTILE; }

    public static String describe(ServerPlayer p, Simulation sim) {
        StringBuilder sb = new StringBuilder(PlayerFaction.describe(p, sim)).append("\nРепутация:");
        for (Faction f : sim.factions) {
            int v = get(p, f.id);
            String s = f.fanatic ? "фанатики, говорить не о чем" : f.raider ? "грабят всех (" + v + ")"
                    : v <= HOSTILE ? "враг (" + v + ")" : v >= FRIEND ? "свой (" + v + ")" : "нейтрально (" + v + ")";
            sb.append("\n").append(f.name).append(": ").append(s);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Бар и застава Долга

    private static final Map<UUID, Integer> WARNINGS = new HashMap<>();
    private static final Map<UUID, Long> GATE = new HashMap<>();

    static boolean armed(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return ru.stalkerzone.compat.Tacz.isGun(s) || s.getItem() instanceof SwordItem || s.getItem() instanceof ProjectileWeaponItem;
    }

    public static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (t % 40 != 0) return;
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        ZoneData d = ZoneData.get(server);
        Node bar = null;
        for (Node n : d.sim.nodes) if (n.name.equals("Бар")) bar = n;
        if (bar == null || bar.owner < 0) return;
        Faction keeper = d.sim.factions.get(bar.owner);
        if (keeper.raider || keeper.fanatic) return;
        double bx = bar.x * ZoneBounds.SCALE, bz = bar.z * ZoneBounds.SCALE;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            double dist = Math.hypot(p.getX() - bx, p.getZ() - bz);
            UUID id = p.getUUID();
            // застава на подходе
            if (dist < 80 && dist > 55) {
                Long last = GATE.get(id);
                if (last == null || t - last > 12000) {
                    GATE.put(id, t);
                    if (hostile(p, keeper.id)) {
                        p.sendSystemMessage(Component.literal("Застава «" + keeper.name + "»: «Тебе здесь не рады. Ещё шаг — и стреляем».").withStyle(ChatFormatting.RED));
                    } else {
                        p.sendSystemMessage(Component.literal("Застава «" + keeper.name + "»: «Стоять! Бар — территория без стрельбы. Оружие убрать, вести себя прилично. Проходи».")
                                .withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            // в самом Баре оружие держать нельзя
            if (dist < 40 && armed(p)) {
                int w = WARNINGS.merge(id, 1, Integer::sum);
                if (w == 1 || w == 3) p.displayClientMessage(Component.literal("«Убери ствол! В Баре не стреляют»").withStyle(ChatFormatting.GOLD), true);
                if (w >= 5) {
                    WARNINGS.put(id, 0);
                    p.hurt(level.damageSources().generic(), 3.0f);
                    level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
                    p.sendSystemMessage(Component.literal("Охранник «" + keeper.name + "» двинул вам прикладом: «Сказано — убрать оружие!»").withStyle(ChatFormatting.RED));
                    add(p, d.sim, keeper.id, -5, "оружие в Баре");
                }
            } else if (dist < 40) {
                WARNINGS.remove(id);
            }
        }
    }
}
