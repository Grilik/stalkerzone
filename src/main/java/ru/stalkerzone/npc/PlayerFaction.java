package ru.stalkerzone.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.PlayerCareer;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneWorld;

/**
 * Игрок в группировке (v1.16): вступление и уход, звание по опыту, свои/чужие для NPC, скидки у торговцев,
 * смерть с потерей снаряжения и возрождение на базе своей группировки. Всё хранится в PlayerPersisted
 * и переживает смерть. Логика правил — PlayerCareer (чистая, с тестами).
 */
public final class PlayerFaction {
    private PlayerFaction() {}

    private static final String FAC = "sz_fac", XP = "sz_xp", LEFT = "sz_left", KEPT = "sz_kept", DEATH = "sz_death";
    /** Насколько близко к базе нужно подойти, чтобы вступить (блоков). */
    public static final int JOIN_RADIUS = 90;

    public static int faction(ServerPlayer p) { return Reputation.store(p).getInt(FAC) - 1; }
    public static int xp(ServerPlayer p) { return Reputation.store(p).getInt(XP); }
    public static int rank(ServerPlayer p) { return PlayerCareer.rank(xp(p)); }

    public static String title(ServerPlayer p) {
        int f = faction(p);
        return f < 0 ? "одиночка без группировки" : PlayerCareer.title(f, rank(p));
    }

    private static void set(ServerPlayer p, int faction, int xp) {
        CompoundTag t = Reputation.store(p);
        t.putInt(FAC, faction + 1);
        t.putInt(XP, xp);
    }

    // ------------------------------------------------------------------ свои и чужие

    /** Нападут ли бойцы группировки npcFaction на игрока. */
    public static boolean npcHostile(int npcFaction, ServerPlayer p, Simulation sim) {
        int mine = faction(p);
        if (mine == npcFaction) return false;
        Faction f = sim.factions.get(npcFaction);
        if (mine >= 0 && mine < sim.factions.size() && sim.hostilityTo(f, mine) >= 0.6) return true;   // вы в стане врага
        if (f.raider || f.fanatic) return true;
        return Reputation.hostile(p, npcFaction);
    }

    // ------------------------------------------------------------------ вступление и уход

    public static String join(ServerPlayer p, ZoneData data, int fid) {
        Simulation sim = data.sim;
        if (fid < 0 || fid >= sim.factions.size()) return "Нет такой группировки.";
        Faction f = sim.factions.get(fid);
        int cur = faction(p);
        if (cur == fid) return "Вы уже в «" + f.name + "»: " + title(p) + ".";
        if (cur >= 0) return "Сначала уйдите из «" + sim.factions.get(cur).name + "»: /zone leave";
        String no = PlayerCareer.refusal(f, Reputation.get(p, fid));
        if (no != null) return no;
        long left = Reputation.store(p).getLong(LEFT);
        long now = p.serverLevel().getGameTime();
        if (left > 0 && now - left < 24000) return "Вас ещё помнят как ушедшего. Попробуйте через " + (24000 - (now - left)) / 1200 + " мин.";
        Node base = sim.nodes.get(f.baseNode);
        double dist = Math.hypot(p.getX() - base.x * ZoneBounds.SCALE, p.getZ() - base.z * ZoneBounds.SCALE);
        if (dist > JOIN_RADIUS) return "Вступают на базе: дойдите до «" + base.name + "» (ещё " + (int) (dist - JOIN_RADIUS) + " блоков).";
        set(p, fid, 0);
        Reputation.add(p, sim, fid, 10, "вступили");
        sim.log(p.getGameProfile().getName() + " вступил в «" + f.name + "»");
        data.setDirty();
        StringBuilder sb = new StringBuilder("Вы вступили в «" + f.name + "». Звание: " + PlayerCareer.title(fid, 0) + ".");
        StringBuilder foes = new StringBuilder();
        for (Faction o : sim.factions) if (o.id != fid && sim.hostilityTo(o, fid) >= 0.6) foes.append(foes.length() > 0 ? ", " : "").append(o.name);
        if (foes.length() > 0) sb.append(" Теперь по вам стреляют: ").append(foes).append(".");
        sb.append(" Возрождение — на базе «").append(base.name).append("», у торговцев своих скидка.");
        return sb.toString();
    }

    public static String leave(ServerPlayer p, ZoneData data) {
        int cur = faction(p);
        if (cur < 0) return "Вы ни в какой группировке не состоите.";
        Faction f = data.sim.factions.get(cur);
        set(p, -1, 0);
        Reputation.store(p).putLong(LEFT, p.serverLevel().getGameTime());
        Reputation.add(p, data.sim, cur, -35, "ушли из группировки");
        data.sim.log(p.getGameProfile().getName() + " ушёл из «" + f.name + "»");
        data.setDirty();
        return "Вы ушли из «" + f.name + "». Звание потеряно, возрождаться будете на Кордоне.";
    }

    /** Свои выгоняют, если репутация у них упала ниже нуля (например, стрелял по своим). */
    public static void checkStanding(ServerPlayer p, Simulation sim) {
        int cur = faction(p);
        if (cur < 0 || Reputation.get(p, cur) >= 0) return;
        set(p, -1, 0);
        Reputation.store(p).putLong(LEFT, p.serverLevel().getGameTime());
        p.sendSystemMessage(Component.literal("Вас выгнали из «" + sim.factions.get(cur).name + "»: своих не трогают.").withStyle(ChatFormatting.RED));
        sim.log(p.getGameProfile().getName() + " изгнан из «" + sim.factions.get(cur).name + "»");
    }

    // ------------------------------------------------------------------ опыт и звания

    public static void award(ServerPlayer p, Simulation sim, int amount, String why) {
        int f = faction(p);
        if (f < 0 || amount <= 0) return;
        int before = xp(p), after = before + amount;
        set(p, f, after);
        if (why != null) p.displayClientMessage(Component.literal("+" + amount + " опыта (" + why + ")").withStyle(ChatFormatting.AQUA), true);
        int r0 = PlayerCareer.rank(before), r1 = PlayerCareer.rank(after);
        if (r1 > r0) {
            p.sendSystemMessage(Component.literal("«" + sim.factions.get(f).name + "»: вам присвоено звание «" + PlayerCareer.title(f, r1)
                    + "». Скидка у своих торговцев выросла.").withStyle(ChatFormatting.GREEN));
            sim.log(p.getGameProfile().getName() + " получил звание «" + PlayerCareer.title(f, r1) + "» в «" + sim.factions.get(f).name + "»");
        }
    }

    /** Игрок убил бойца: опыт, если это враг его группировки. */
    public static void onKill(ServerPlayer p, Simulation sim, int victim, boolean leader) {
        int f = faction(p);
        if (f < 0 || victim == f || victim < 0 || victim >= sim.factions.size()) return;
        if (sim.hostilityTo(sim.factions.get(f), victim) < 0.6) return;
        award(p, sim, leader ? PlayerCareer.XP_KILL_ENEMY_LEADER : PlayerCareer.XP_KILL_ENEMY, leader ? "убит вражеский командир" : "убит враг");
    }

    /** Выполнен контракт: заказчик — своя группировка даёт больше опыта. */
    public static void contractDone(ServerPlayer p, Simulation sim, int issuer) {
        int f = faction(p);
        if (f < 0) return;
        award(p, sim, issuer == f ? PlayerCareer.XP_CONTRACT_OWN : PlayerCareer.XP_CONTRACT_OTHER, "контракт выполнен");
    }

    public static String describe(ServerPlayer p, Simulation sim) {
        int f = faction(p);
        StringBuilder sb = new StringBuilder();
        if (f < 0) sb.append("Группировка: нет (вступить: /zone join <группировка> на её базе)");
        else {
            int x = xp(p), next = PlayerCareer.toNext(x);
            sb.append("Группировка: ").append(sim.factions.get(f).name).append(", звание «").append(title(p)).append("», опыт ").append(x)
                    .append(next > 0 ? " (до следующего " + next + ")" : " (высшее звание)");
        }
        int[] d = Reputation.store(p).getIntArray(DEATH);
        if (d.length == 3) sb.append("\nМесто последней гибели: ").append(d[0]).append(" ").append(d[1]).append(" ").append(d[2]);
        return sb.toString();
    }

    // ------------------------------------------------------------------ скидки

    /** Выставить множители цен симуляции под игрока у торговца этой точки; вызвать reset() после сделки. */
    public static void applyTrade(ServerPlayer p, Simulation sim, Node n) {
        int owner = n.owner;
        if (owner < 0 || owner >= sim.factions.size()) { sim.playerBuyFactor = 1; sim.playerSellFactor = 1; return; }
        int rep = Reputation.get(p, owner);
        boolean member = faction(p) == owner;
        int rank = member ? rank(p) : 0;
        sim.playerBuyFactor = PlayerCareer.buyFactor(rep, member, rank);
        sim.playerSellFactor = PlayerCareer.sellFactor(rep, member, rank);
    }

    public static void resetTrade(Simulation sim) {
        sim.playerBuyFactor = 1;
        sim.playerSellFactor = 1;
    }

    /** Строка для меню торговца: какая скидка действует. */
    public static String tradeNote(ServerPlayer p, Simulation sim, Node n) {
        if (n.owner < 0 || n.owner >= sim.factions.size()) return null;
        int rep = Reputation.get(p, n.owner);
        boolean member = faction(p) == n.owner;
        double b = PlayerCareer.buyFactor(rep, member, member ? rank(p) : 0);
        if (Math.abs(b - 1) < 0.001) return null;
        return b < 1 ? "Вам скидка " + Math.round((1 - b) * 100) + "%" + (member ? " (вы свой)" : " (вас уважают)")
                : "Вам наценка " + Math.round((b - 1) * 100) + "%: здесь вас не любят";
    }

    // ------------------------------------------------------------------ смерть и возрождение

    public static final class CareerEvents {   // имя уникально: EventBus генерирует обработчики по простому имени класса и метода
        /** Снаряжение остаётся на месте гибели (лежит 20 минут), КПК остаётся при себе. */
        @SubscribeEvent
        public void onPlayerDrops(LivingDropsEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            if (p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
            ListTag kept = new ListTag();
            e.getDrops().removeIf(ie -> {
                ItemStack s = ie.getItem();
                if (s.getItem() == ru.stalkerzone.item.ModItems.PDA.get()) { kept.add(s.save(new CompoundTag())); return true; }
                return false;
            });
            for (ItemEntity ie : e.getDrops()) ie.lifespan = 20 * 60 * 20;
            CompoundTag t = Reputation.store(p);
            t.put(KEPT, kept);
            t.putIntArray(DEATH, new int[]{p.getBlockX(), p.getBlockY(), p.getBlockZ()});
        }

        @SubscribeEvent
        public void onPlayerClone(PlayerEvent.Clone e) {
            if (!e.isWasDeath() || !(e.getEntity() instanceof ServerPlayer np) || !(e.getOriginal() instanceof ServerPlayer op)) return;
            // Forge и так переносит PlayerPersisted; подстрахуемся
            CompoundTag old = op.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            if (!old.isEmpty()) np.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, old.copy());
        }

        @SubscribeEvent
        public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer p)) return;
            CompoundTag t = Reputation.store(p);
            ListTag kept = t.getList(KEPT, Tag.TAG_COMPOUND);
            for (int i = 0; i < kept.size(); i++) p.getInventory().add(ItemStack.of(kept.getCompound(i)));
            t.remove(KEPT);
            ZoneData data = ZoneData.get(p.getServer());
            int f = faction(p);
            int[] d = t.getIntArray(DEATH);
            String where = d.length == 3 ? " Снаряжение осталось на месте гибели: " + d[0] + " " + d[1] + " " + d[2] + " (лежит 20 минут)." : "";
            if (f >= 0 && f < data.sim.factions.size()) {
                Node base = data.sim.nodes.get(data.sim.factions.get(f).baseNode);
                ServerLevel level = p.getServer().overworld();
                BlockPos pos = SpawnFinder.safeNear(level, ZoneWorld.bounds(), base.x * ZoneBounds.SCALE, base.z * ZoneBounds.SCALE);
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), 0f);
                p.sendSystemMessage(Component.literal("Вас вытащили свои: вы очнулись на базе «" + base.name + "»." + where).withStyle(ChatFormatting.YELLOW));
            } else if (!where.isEmpty()) {
                p.sendSystemMessage(Component.literal("Вы очнулись." + where).withStyle(ChatFormatting.YELLOW));
            }
        }

        /** Опыт за мутантов. */
        @SubscribeEvent
        public void onMutantKilled(LivingDeathEvent e) {
            if (e.getEntity().level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
            boolean mutant = e.getEntity() instanceof ru.stalkerzone.mutant.MutantEntity
                    || e.getEntity().getPersistentData().contains(ru.stalkerzone.mutant.MutantManager.TAG_LAIR);
            if (!mutant || p.getServer() == null) return;
            award(p, ZoneData.get(p.getServer()).sim, PlayerCareer.XP_KILL_MUTANT, null);
        }
    }
}
