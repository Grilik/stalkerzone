package ru.stalkerzone.mutant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Lair;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneWorld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Мутанты у логов. Сколько их бродит у логова, зависит от его силы в симуляции; ночью на одного больше.
 * Работает только рядом с игроками, вдали мутанты существуют лишь как сила логова.
 */
public final class MutantManager {
    /** Метка существа другого мода, вставшего вместо мутанта логова: номер точки логова и вид. */
    public static final String TAG_LAIR = "szLair", TAG_KIND = "szKind";

    /** Игрок ближе этого к логову: логово «живёт» в мире. */
    private static final int ACTIVE = 110;
    private static final int MAX_PER_LAIR = 6;
    /** Новый мутант у логова не чаще чем раз в столько секунд. */
    private static final int SPAWN_EVERY = 6;

    private static final Random RND = new Random();
    private static final Map<Integer, Integer> COOLDOWN = new HashMap<>();

    private MutantManager() {}

    /** Логово стоит в стороне от центра точки, чтобы не лезть в лагерь. Направление постоянно для точки. */
    public static BlockPos denXZ(Simulation sim, Lair l) {
        Node n = sim.nodes.get(l.node);
        double ang = ((n.id * 2654435761L) >>> 8) % 360 * Math.PI / 180;
        int dist = 55;
        int x = n.x * ZoneBounds.SCALE + (int) Math.round(Math.cos(ang) * dist);
        int z = n.z * ZoneBounds.SCALE + (int) Math.round(Math.sin(ang) * dist);
        ZoneBounds b = ZoneWorld.bounds();
        return new BlockPos(b.clampX(x), 0, b.clampZ(z));
    }

    public static int wanted(Lair l, boolean night) {
        int n = (int) Math.ceil(l.strength / MutantType.avgValue(l.kind));
        if (night) n++;
        return Math.max(1, Math.min(MAX_PER_LAIR, n));
    }

    /** Вызывать раз в секунду. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        Simulation sim = ZoneData.get(server).sim;
        Set<Integer> liveLairs = new HashSet<>();
        for (Lair l : sim.lairs) liveLairs.add(l.node);

        // убрать тех, кто остался без логова или далеко от всех игроков
        for (ServerPlayer p : players) {
            for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(160),
                    e -> !(e instanceof MutantEntity) && e.getPersistentData().contains(TAG_LAIR))) {
                int node = m.getPersistentData().getInt(TAG_LAIR);
                double nearest = Double.MAX_VALUE;
                for (ServerPlayer q : players) nearest = Math.min(nearest, q.distanceTo(m));
                if (nearest > ACTIVE + 30 || (!liveLairs.contains(node) && nearest > 48)) m.discard();
            }
            for (MutantEntity m : level.getEntitiesOfClass(MutantEntity.class, p.getBoundingBox().inflate(160))) {
                if (m.lairNode() < 0) continue; // призванные вручную живут сами по себе
                double nearest = Double.MAX_VALUE;
                for (ServerPlayer q : players) nearest = Math.min(nearest, q.distanceTo(m));
                if (nearest > ACTIVE + 30 || (!liveLairs.contains(m.lairNode()) && nearest > 48)) m.discard();
            }
        }

        boolean night = level.isNight();
        // ограничение: не больше 16 мутантов в радиусе 128 от игроков
        java.util.Set<Integer> seenMut = new java.util.HashSet<>();
        for (ServerPlayer p : players) {
            AABB scan = p.getBoundingBox().inflate(128);
            for (MutantEntity m : level.getEntitiesOfClass(MutantEntity.class, scan, m -> m.isAlive())) seenMut.add(m.getId());
            for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, scan,
                    m -> m.isAlive() && m.getPersistentData().contains(TAG_LAIR) && !(m instanceof MutantEntity))) seenMut.add(m.getId());
        }
        int totalMutants = seenMut.size();
        for (Lair l : sim.lairs) {
            BlockPos den = denXZ(sim, l);
            boolean near = false;
            for (ServerPlayer p : players) if (Math.hypot(p.getX() - den.getX(), p.getZ() - den.getZ()) <= ACTIVE) near = true;
            if (!near || !level.hasChunk(den.getX() >> 4, den.getZ() >> 4)) continue;
            int cd = COOLDOWN.getOrDefault(l.node, 0);
            if (cd > 0) { COOLDOWN.put(l.node, cd - 1); continue; }

            AABB box = new AABB(den).inflate(80, 128, 80);
            List<MutantEntity> here = level.getEntitiesOfClass(MutantEntity.class, box, m -> m.lairNode() == l.node && m.isAlive());
            int foreign = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box, m -> !(m instanceof MutantEntity) && m.isAlive()
                    && m.getPersistentData().contains(TAG_LAIR) && m.getPersistentData().getInt(TAG_LAIR) == l.node).size();
            // v1.14: после выброса мутанты сперва затихают, потом пик на 3–4-е сутки
            double act = ru.stalkerzone.anomaly.EmissionEvent.mutantActivity(sim);
            if (here.size() + foreign >= Math.max(1, (int) Math.round(wanted(l, night) * act))) continue;
            if (totalMutants >= 16) continue;
            boolean leader = here.stream().anyMatch(m -> m.kind().leader());
            spawn(level, l, den, MutantType.pick(l.kind, leader, RND));
            COOLDOWN.put(l.node, SPAWN_EVERY);
            totalMutants++;
        }
    }

    private static void spawn(ServerLevel level, Lair l, BlockPos den, MutantType t) {
        int x = den.getX() + RND.nextInt(17) - 8, z = den.getZ() + RND.nextInt(17) - 8;
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        BlockPos pos = SpawnFinder.groundAt(level, x, z);
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) return;
        // не спавнить прямо на глазах у игрока
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 18 * 18) return;
        if (spawnForeign(level, l, den, t, pos)) return;
        // v1.14: свита контролёра — зомбированные сталкеры
        if (l.kind == ru.stalkerzone.sim.LairKind.CONTROLLERS && t == MutantType.SNORK && RND.nextDouble() < 0.6) {
            net.minecraft.world.entity.monster.Zombie zb = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            if (zb != null) {
                zb.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
                zb.setCustomName(net.minecraft.network.chat.Component.literal("Зомбированный сталкер"));
                zb.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE));
                zb.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));   // капюшон: не горит на солнце
                zb.getPersistentData().putInt(TAG_LAIR, l.node);
                zb.getPersistentData().putInt(TAG_KIND, MutantType.SNORK.ordinal());
                zb.restrictTo(SpawnFinder.groundAt(level, den.getX(), den.getZ()), 30);
                zb.setPersistenceRequired();
                level.addFreshEntity(zb);
                return;
            }
        }
        MutantEntity m = MutantRegistry.TYPES[t.ordinal()].get().create(level);
        if (m == null) return;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        BlockPos denGround = SpawnFinder.groundAt(level, den.getX(), den.getZ());
        m.bind(l.node, denGround);
        level.addFreshEntity(m);
    }

    /**
     * Вместо своего мутанта поставить существо другого мода из compat.json (например, сталкерских мутантов),
     * если такое существо есть в игре. Логово считает его своим и слабеет при убийстве.
     */
    private static boolean spawnForeign(ServerLevel level, Lair l, BlockPos den, MutantType t, BlockPos pos) {
        ru.stalkerzone.compat.CompatConfig.load();
        List<String> alt = ru.stalkerzone.compat.CompatConfig.MUTANTS.get(t.id);
        if (alt == null || alt.isEmpty() || RND.nextInt(100) >= 70) return false;
        String id = alt.get(RND.nextInt(alt.size()));
        net.minecraft.world.entity.EntityType<?> type = net.minecraft.world.entity.EntityType.byString(id).orElse(null);
        if (type == null) return false;
        net.minecraft.world.entity.Entity e = type.create(level);
        if (!(e instanceof net.minecraft.world.entity.Mob m)) { if (e != null) e.discard(); return false; }
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        m.getPersistentData().putInt(TAG_LAIR, l.node);
        m.getPersistentData().putInt(TAG_KIND, t.ordinal());
        if (m instanceof net.minecraft.world.entity.PathfinderMob pm) pm.restrictTo(SpawnFinder.groundAt(level, den.getX(), den.getZ()), 40);
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        return true;
    }

    /** Убит мутант логова (свой или из другого мода): ослабить логово, выдать награды. */
    public static void lairMobKilled(net.minecraft.world.entity.LivingEntity e, int lairNode, MutantType kind, net.minecraft.world.damagesource.DamageSource source) {
        MinecraftServer server = e.getServer();
        if (server == null || lairNode < 0) return;
        boolean byPlayer = source.getEntity() instanceof net.minecraft.world.entity.player.Player;
        boolean byStalker = source.getEntity() instanceof ru.stalkerzone.npc.StalkerEntity;
        if (!byPlayer && !byStalker) return;
        ZoneData data = ZoneData.get(server);
        Simulation sim = data.sim;
        Lair l = sim.lairAt(lairNode);
        if (l != null) {
            boolean destroyed = sim.mutantKilled(l, kind.value, byPlayer);
            if (destroyed && byPlayer) {
                int art = sim.lairClearReward(l);
                for (int i = 0; i < art; i++) e.spawnAtLocation(new net.minecraft.world.item.ItemStack(ru.stalkerzone.item.ModItems.ARTIFACT.get()));
                for (ServerPlayer p : server.overworld().players()) {
                    if (p.distanceTo(e) < 64) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Логово «" + l.title() + "» уничтожено!"
                            + (art > 0 ? " В логове найдены артефакты: " + art : "")).withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
        }
        if (byPlayer) {
            sim.mutantPartLoot(kind.partUnits);
            e.spawnAtLocation(new net.minecraft.world.item.ItemStack(MutantRegistry.PARTS[kind.ordinal()].get()));
        }
        data.setDirty();
    }

    /** Подписка на гибель существ других модов, стоящих в логовах. */
    public static final class Events {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent ev) {
            net.minecraft.world.entity.LivingEntity e = ev.getEntity();
            if (e.level().isClientSide || e instanceof MutantEntity || !e.getPersistentData().contains(TAG_LAIR)) return;
            int k = e.getPersistentData().getInt(TAG_KIND);
            if (k < 0 || k >= MutantType.ALL.length) return;
            lairMobKilled(e, e.getPersistentData().getInt(TAG_LAIR), MutantType.ALL[k], ev.getSource());
        }
    }
}
