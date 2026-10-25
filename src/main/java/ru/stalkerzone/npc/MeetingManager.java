package ru.stalkerzone.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.pda.PdaService;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Person;
import ru.stalkerzone.sim.Politics;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Встреча лидеров в мире (v1.7): в день перед решением встречи лидеры обеих фракций с охраной стоят на месте встречи.
 * Игрок с заказом на охрану, оказавшись рядом, ставит пост (встреча охраняется); игрок с заказом на засаду может убить лидера.
 * Перед самым концом дня, если у встречи есть враги, к месту подходит отряд нападающих.
 */
public final class MeetingManager {
    private static final String TAG = "stalkerzone_meeting";
    private static final Random RND = new Random();
    private static final Set<String> NOTIFIED = new HashSet<>();
    private static int ambushDay = -1;

    private MeetingManager() {}

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ZoneData data = ZoneData.get(server);
        Simulation sim = data.sim;
        Politics.Meeting m = sim.politics.meeting;
        boolean window = m != null && sim.day == m.day - 1;
        if (!window) {
            NOTIFIED.clear();
            if (level.getGameTime() % 100 != 0) return;
            List<StalkerEntity> old = new java.util.ArrayList<>();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof StalkerEntity se && se.getTags().contains(TAG)) old.add(se);
            }
            old.forEach(StalkerEntity::discard);
            return;
        }
        Node venue = sim.nodes.get(m.venue);
        int cx = venue.x * ZoneBounds.SCALE, cz = venue.z * ZoneBounds.SCALE;
        List<ServerPlayer> players = level.players();
        boolean near = false;
        for (ServerPlayer p : players) {
            double d = Math.hypot(p.getX() - cx, p.getZ() - cz);
            if (d < 110) near = true;
            String id = PdaService.id(p);
            if (d < 48 && !NOTIFIED.contains(id) && sim.contracts.guardPresent(sim, id)) {
                NOTIFIED.add(id);
                data.setDirty();
                p.sendSystemMessage(Component.literal("Вы на посту: встреча лидеров под вашей охраной до конца дня").withStyle(ChatFormatting.GREEN));
            }
        }
        if (!near || !level.hasChunk(cx >> 4, cz >> 4)) return;
        AABB box = new AABB(cx - 60, level.getMinBuildHeight(), cz - 60, cx + 60, level.getMaxBuildHeight(), cz + 60);
        List<StalkerEntity> present = level.getEntitiesOfClass(StalkerEntity.class, box, e -> true);
        for (int fid : new int[] {m.host, m.guest}) {
            Person l = sim.people.leader(fid);
            if (l == null) continue;
            boolean here = present.stream().anyMatch(e -> e.personId() == l.id);
            if (!here) {
                StalkerEntity e = spawn(level, fid, m.venue, cx + (fid == m.host ? -3 : 3), cz, l.name + " [" + l.rankRu() + ", лидер]");
                if (e != null) {
                    e.setPerson(l.id);
                    e.equipLeader();
                }
                long guards = present.stream().filter(x -> x.faction() == fid && x.getTags().contains(TAG)).count();
                for (long g = guards; g < 2; g++) spawn(level, fid, m.venue, cx + RND.nextInt(9) - 4, cz + RND.nextInt(9) - 4, "Охрана");
            }
        }
        // нападающие подходят в последнюю минуту
        if (data.tickCounter > StalkerZoneMod.TICKS_PER_SIM_DAY - 1200 && ambushDay != sim.day) {
            ambushDay = sim.day;
            Faction h = sim.politics.ambusher(sim, m);
            if (h != null && RND.nextDouble() < (m.guarded ? 0.5 : 0.35)) {
                double ang = RND.nextDouble() * Math.PI * 2;
                for (int i = 0; i < 4; i++) {
                    spawn(level, h.id, -1, cx + (int) (Math.cos(ang) * 28) + RND.nextInt(5) - 2, cz + (int) (Math.sin(ang) * 28) + RND.nextInt(5) - 2, "Налётчик");
                }
                for (ServerPlayer p : players) {
                    if (Math.hypot(p.getX() - cx, p.getZ() - cz) < 90) {
                        p.sendSystemMessage(Component.literal("К месту встречи подходит отряд «" + h.name + "»!").withStyle(ChatFormatting.RED));
                    }
                }
            }
        }
    }

    private static StalkerEntity spawn(ServerLevel level, int faction, int node, int x, int z, String name) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        BlockPos pos = SpawnFinder.groundAt(level, x, z);
        StalkerEntity e = ModEntities.STALKER.get().create(level);
        if (e == null) return null;
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        e.configure(faction, StalkerEntity.ROLE_STALKER, node < 0 ? -1 : -2, pos, Component.literal(name));
        e.addTag(TAG);
        level.addFreshEntity(e);
        return e;
    }
}
