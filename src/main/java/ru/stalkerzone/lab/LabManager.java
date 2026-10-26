package ru.stalkerzone.lab;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.DataDriveItem;
import ru.stalkerzone.mutant.MutantEntity;
import ru.stalkerzone.mutant.MutantRegistry;
import ru.stalkerzone.mutant.MutantType;
import ru.stalkerzone.npc.ModEntities;
import ru.stalkerzone.npc.StalkerEntity;
import ru.stalkerzone.sim.Lab;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.LabBuilder;
import ru.stalkerzone.world.LabLayout;
import ru.stalkerzone.world.SpawnFinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Лаборатории в мире: постройка при приближении игрока, охрана (мутанты, зомби, Монолит), пси-излучение,
 * скачивание данных с терминалов. Скачивание долгое, прерывается, если отойти от терминала, и поднимает тревогу.
 */
public final class LabManager {
    private static final int KEY = 5000;
    private static final Random RND = new Random();
    private static final Map<Integer, LabLayout> LAYOUTS = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        Lab lab;
        int part;
        BlockPos terminal;
        int left, total, nextWave;
    }

    private LabManager() {}

    /** Охрана лаборатории помечается номером логова −100−id, чтобы не трогать симуляцию логов. */
    public static int guardTag(Lab l) { return -100 - l.id; }

    public static LabLayout layout(ZoneData data, Lab l) {
        Integer y = data.campY.get(KEY + l.id);
        if (y == null) return null;
        return LAYOUTS.computeIfAbsent(l.id * 1000 + (y & 511), k -> new LabLayout(l, data.sim.nodes.get(l.node), y));
    }

    /** Лаборатория, в которой стоит игрок, или null. */
    public static LabLayout inside(ZoneData data, double x, double y, double z) {
        for (Lab l : data.sim.labs.labs) {
            LabLayout lay = layout(data, l);
            if (lay != null && lay.inside(x, y, z)) return lay;
        }
        return null;
    }

    /** Вызывать раз в секунду. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ZoneData data = ZoneData.get(server);
        Simulation sim = data.sim;
        for (Lab l : sim.labs.labs) {
            Node n = sim.nodes.get(l.node);
            int ex = n.x * ru.stalkerzone.world.ZoneBounds.SCALE + l.dx, ez = n.z * ru.stalkerzone.world.ZoneBounds.SCALE + l.dz;
            boolean near = false;
            for (ServerPlayer p : players) if (Math.hypot(p.getX() - ex, p.getZ() - ez) < 110) near = true;
            if (!near) {
                cleanup(level, l, ex, ez);
                continue;
            }
            if (!data.built.contains(KEY + l.id)) {
                if (!level.hasChunk(ex >> 4, ez >> 4)) continue;
                int y = SpawnFinder.groundAt(level, ex, ez).getY();
                LabLayout lay = new LabLayout(l, n, y);
                if (!LabBuilder.loaded(level, lay)) continue;
                LabBuilder.build(level, lay);
                data.campY.put(KEY + l.id, y);
                data.built.add(KEY + l.id);
                data.setDirty();
                continue;
            }
            LabLayout lay = layout(data, l);
            if (lay == null) continue;
            List<ServerPlayer> in = new ArrayList<>();
            for (ServerPlayer p : players) if (lay.inside(p.getX(), p.getY(), p.getZ()) && !p.isSpectator()) in.add(p);
            if (in.isEmpty()) continue;
            guards(level, lay, in, 2 + l.tier * 2);
        }
    }

    private static void cleanup(ServerLevel level, Lab l, int ex, int ez) {
        AABB box = new AABB(ex - 90, level.getMinBuildHeight(), ez - 90, ex + 90, level.getMaxBuildHeight(), ez + 90);
        for (MutantEntity m : level.getEntitiesOfClass(MutantEntity.class, box, m -> m.lairNode() == guardTag(l))) m.discard();
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, box, z -> z.getTags().contains("stalkerzone_lab"))) z.discard();
        for (StalkerEntity s : level.getEntitiesOfClass(StalkerEntity.class, box, s -> s.getTags().contains("stalkerzone_lab"))) s.discard();
    }

    private static int alive(ServerLevel level, LabLayout lay) {
        AABB box = new AABB(lay.minX(), lay.minY(), lay.minZ(), lay.maxX(), lay.surfaceY, lay.maxZ());
        int c = level.getEntitiesOfClass(MutantEntity.class, box, m -> m.lairNode() == guardTag(lay.lab) && m.isAlive()).size();
        c += level.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains("stalkerzone_lab") && m.isAlive()).size();
        return c;
    }

    /** Держать охрану на уровне cap, по одному раз в секунду, не на глазах у игрока. */
    private static void guards(ServerLevel level, LabLayout lay, List<ServerPlayer> in, int cap) {
        if (alive(level, lay) >= cap || RND.nextInt(5) != 0) return;
        spawnGuard(level, lay, in, 14);
    }

    private static void spawnGuard(ServerLevel level, LabLayout lay, List<ServerPlayer> in, int minDist) {
        LabLayout.Room rm = lay.rooms.get(RND.nextInt(lay.rooms.size()));
        if (rm == lay.entryRoom) return;
        BlockPos pos = new BlockPos(rm.cx() + RND.nextInt(5) - 2, rm.y(), rm.cz() + RND.nextInt(5) - 2);
        for (ServerPlayer p : in) if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < minDist * minDist) return;
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return;
        Lab l = lay.lab;
        double v = RND.nextDouble();
        switch (l.code) {
            // v1.14: в подземельях Агропрома бюреры и тушканы, в X-18 полтергейсты (как в ТЧ)
            case "agroprom" -> mutant(level, lay, pos, v < 0.35 ? MutantType.SNORK : v < 0.65 ? MutantType.BURER : v < 0.85 ? MutantType.TUSHKAN : MutantType.DOG);
            case "x18" -> mutant(level, lay, pos, v < 0.4 ? MutantType.BLOODSUCKER : v < 0.7 ? MutantType.POLTERGEIST : MutantType.SNORK);
            case "x16" -> {
                if (v < 0.45) zombie(level, pos);
                else mutant(level, lay, pos, v < 0.85 ? MutantType.SNORK : MutantType.CONTROLLER);
            }
            case "x10" -> {
                if (v < 0.45) monolith(level, pos);
                else mutant(level, lay, pos, v < 0.75 ? MutantType.BLOODSUCKER : MutantType.CONTROLLER);
            }
            default -> {
                if (v < 0.5) monolith(level, pos);
                else mutant(level, lay, pos, v < 0.8 ? MutantType.CHIMERA : MutantType.CONTROLLER);
            }
        }
    }

    private static void mutant(ServerLevel level, LabLayout lay, BlockPos pos, MutantType t) {
        MutantEntity m = MutantRegistry.TYPES[t.ordinal()].get().create(level);
        if (m == null) return;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        m.bind(guardTag(lay.lab), pos);
        level.addFreshEntity(m);
    }

    private static void zombie(ServerLevel level, BlockPos pos) {
        Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (z == null) return;
        z.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        z.setCustomName(Component.literal("Зомбированный сталкер"));
        z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE));
        z.setDropChance(EquipmentSlot.CHEST, 0f);
        z.addTag("stalkerzone_lab");
        z.setPersistenceRequired();
        level.addFreshEntity(z);
    }

    private static void monolith(ServerLevel level, BlockPos pos) {
        StalkerEntity e = ModEntities.STALKER.get().create(level);
        if (e == null) return;
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        e.configure(ru.stalkerzone.sim.ZoneScenario.MONOLITH, StalkerEntity.ROLE_STALKER, -1, pos, Component.literal("Монолитовец"));
        e.addTag("stalkerzone_lab");
        level.addFreshEntity(e);
    }

    // ------------------------------------------------------------------ терминалы

    public static void useTerminal(ServerPlayer p, BlockPos pos) {
        ZoneData data = ZoneData.get(p.getServer());
        Simulation sim = data.sim;
        for (Lab l : sim.labs.labs) {
            LabLayout lay = layout(data, l);
            if (lay == null) continue;
            int part = lay.terminalIndex(pos);
            if (part < 0) continue;
            String err = sim.labs.canDownload(sim, l, part);
            if (err != null) {
                p.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.GRAY));
                return;
            }
            if (SESSIONS.containsKey(p.getUUID())) {
                p.sendSystemMessage(Component.literal("Скачивание уже идёт").withStyle(ChatFormatting.GRAY));
                return;
            }
            ItemStack drive = emptyDrive(p, l.needsDisk());
            if (drive == null) {
                p.sendSystemMessage(Component.literal(l.needsDisk() ? "Нужен пустой переносной диск: на флешку столько данных не влезет"
                        : "Нужна пустая флешка или диск").withStyle(ChatFormatting.RED));
                return;
            }
            Session s = new Session();
            s.lab = l;
            s.part = part;
            s.terminal = pos;
            s.total = 20 * (45 + 25 * l.tier);
            s.left = s.total;
            s.nextWave = s.total - 200;
            SESSIONS.put(p.getUUID(), s);
            p.sendSystemMessage(Component.literal("Терминал «" + l.name + "» №" + (part + 1) + ": начато скачивание. Не отходите дальше 5 блоков. Сработала тревога!")
                    .withStyle(ChatFormatting.GOLD));
            p.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2f, 0.5f);
            return;
        }
        p.sendSystemMessage(Component.literal("Терминал не отвечает").withStyle(ChatFormatting.GRAY));
    }

    private static ItemStack emptyDrive(ServerPlayer p, boolean needDisk) {
        ItemStack best = null;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!(s.getItem() instanceof DataDriveItem d) || DataDriveItem.hasData(s)) continue;
            if (needDisk && !d.disk) continue;
            if (best == null || (!d.disk && ((DataDriveItem) best.getItem()).disk)) best = s; // флешку тратим раньше диска
        }
        return best;
    }

    /** Вызывать каждый тик: идёт скачивание. */
    public static void tickDownloads(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        ZoneData data = ZoneData.get(server);
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Session s = e.getValue();
            if (p == null || !p.isAlive() || p.distanceToSqr(s.terminal.getX() + 0.5, s.terminal.getY(), s.terminal.getZ() + 0.5) > 36) {
                if (p != null) p.sendSystemMessage(Component.literal("Скачивание прервано: связь с терминалом потеряна").withStyle(ChatFormatting.RED));
                it.remove();
                continue;
            }
            s.left--;
            if (s.left % 20 == 0) {
                int pct = 100 - (int) (100L * s.left / s.total);
                p.displayClientMessage(Component.literal("Скачивание «" + s.lab.name + "»: " + pct + "%").withStyle(ChatFormatting.AQUA), true);
            }
            if (s.left <= s.nextWave) {
                s.nextWave -= 300;
                LabLayout lay = layout(data, s.lab);
                if (lay != null) {
                    List<ServerPlayer> in = List.of(p);
                    for (int k = 0; k < 1 + s.lab.tier / 2; k++) spawnGuard(p.serverLevel(), lay, in, 10);
                }
                p.level().playSound(null, s.terminal, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2f, 0.5f);
            }
            if (s.left <= 0) {
                it.remove();
                ItemStack drive = emptyDrive(p, s.lab.needsDisk());
                if (drive == null) {
                    p.sendSystemMessage(Component.literal("Данные некуда записать: накопитель пропал").withStyle(ChatFormatting.RED));
                    continue;
                }
                Simulation sim = data.sim;
                if (sim.labs.canDownload(sim, s.lab, s.part) != null) {
                    p.sendSystemMessage(Component.literal("Пока вы качали, данные стёрли").withStyle(ChatFormatting.RED));
                    continue;
                }
                int cycle = sim.labs.downloaded(sim, s.lab, s.part, p.getGameProfile().getName());
                DataDriveItem.write(drive, s.lab.id, s.part, cycle, s.lab.name);
                data.setDirty();
                p.sendSystemMessage(Component.literal("Данные «" + s.lab.name + "» (терминал " + (s.part + 1) + ") скачаны. Отнесите их учёным на Янтарь, пока Монолит не уничтожил исследования.")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }
}
