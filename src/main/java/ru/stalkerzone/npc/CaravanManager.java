package ru.stalkerzone.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Caravan;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.LevelPoints;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;

import java.util.*;

/**
 * Живая Зона в мире (014): караваны и курьеры на дорогах.
 * Отражает караваны симуляции, идущие по рёбрам графа: спавнит отряд NPC
 * на дорожных точках из points/level.json, заставляет их идти к следующей точке.
 * Курьеры Торговцев получают большие рюкзаки и охрану.
 * Вне радиуса игроков — только симуляция, NPC убираются.
 * Ограничение: не больше 24 NPC в радиусе 128 блоков от всех игроков.
 */
public final class CaravanManager {
    private static final int ACTIVE_RADIUS = 128;
    private static final int SPAWN_SCAN = 48;
    private static final int MAX_CARAVAN_NPC = 24;
    private static final int WAYPOINT_STEP = 5;
    private static final int MAX_COURIER_GUARDS = 2;

    private static final Random RND = new Random();
    private static final Map<Integer, List<StalkerEntity>> spawned = new HashMap<>();

    private CaravanManager() {}
/** Вызывать раз в секунду (раз в 20 тиков). */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) { clearAll(); return; }
        ZoneData data = ZoneData.get(server);
        Simulation sim = data.sim;

        // убрать отряды исчезнувших или далеко ушедших караванов
        spawned.entrySet().removeIf(entry -> {
            int idx = entry.getKey();
            if (idx < 0 || idx >= sim.caravans.size()) { discardAll(entry.getValue()); return true; }
            Caravan cv = sim.caravans.get(idx);
            if (cv.leg >= cv.route.length - 1) { discardAll(entry.getValue()); return true; }
            if (!nearAnyPlayer(players, cv, sim)) { discardAll(entry.getValue()); return true; }
            return false;
        });

        // посчитать сколько NPC уже живо
        int alive = 0;
        for (List<StalkerEntity> list : spawned.values())
for (int i = 0; i < sim.caravans.size(); i++) {
            if (spawned.containsKey(i)) continue;
            Caravan cv = sim.caravans.get(i);
            if (cv.leg >= cv.route.length - 1) continue;
            if (!nearAnyPlayer(players, cv, sim)) continue;

            int from = cv.route[cv.leg];
            int to = cv.route[cv.leg + 1];
            String levelId = LevelPoints.levelForScenario(sim.scenario);
            if (levelId == null) continue;
            List<int[]> road = LevelPoints.roadWaypoints(levelId, from, to);
            if (road.isEmpty()) continue;

            int[] best = null;
            for (ServerPlayer p : players) {
                for (int[] pt : road) {
                    if (Math.hypot(p.getX() - pt[0], p.getZ() - pt[2]) < SPAWN_SCAN) { best = pt; break; }
                }
                if (best != null) break;
            }
            if (best == null) continue;

            int room = MAX_CARAVAN_NPC - alive;
            if (room <= 0) break;

            boolean courier = cv.courierFor >= 0;
            int guards = courier ? Math.min(MAX_COURIER_GUARDS, cv.guards) : Math.min(room, Math.max(1, cv.guards));
            guards = Math.min(guards, room);

            int wpIdx = nearestRoadIndex(road, best[0], best[2]);
            int nextIdx = Math.min(road.size() - 1, wpIdx + WAYPOINT_STEP);
            int[] nextPt = road.get(nextIdx);
            List<StalkerEntity> squad = new ArrayList<>();

            for (int g = 0; g < guards; g++) {
                int sx = best[0] + RND.nextInt(5) - 2;
                int sz = best[2] + RND.nextInt(5) - 2;
                BlockPos ground = SpawnFinder.groundAt(level, sx, sz);
                if (ground.getY() < level.getMinBuildHeight()) continue;
                StalkerEntity e = ModEntities.STALKER.get().create(level);
                if (e == null) continue;

                String name;
                if (courier && g == 0) name = "Курьер [" + NpcManager.callsign() + "]";
                else if (courier) name = "Охранник [" + NpcManager.callsign() + "]";
                else name = NpcManager.callsign() + " [" + rankFor(sim.factions.get(cv.owner)) + "]";

                e.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
                e.configure(cv.owner, StalkerEntity.ROLE_STALKER, from, ground, Component.literal(name));

                if (courier && g == 0) {
                    ItemStack bp = new ItemStack(ru.stalkerzone.item.ModItems.BACKPACKS[0].get());
                    e.setItemSlot(EquipmentSlot.CHEST, bp);
                    e.setDropChance(EquipmentSlot.CHEST, 0f);
                }
                e.getPersistentData().putInt("sz_caravan_idx", i);
                if (courier && g == 0) e.getPersistentData().putBoolean("sz_courier", true);
                e.getPersistentData().putInt("sz_caravan_next_x", nextPt[0]);
                e.getPersistentData().putInt("sz_caravan_next_y", nextPt[1]);
                e.getPersistentData().putInt("sz_caravan_next_z", nextPt[2]);
                level.addFreshEntity(e);
                squad.add(e);
                alive++;
                if (alive >= MAX_CARAVAN_NPC) break;
            }
            if (!squad.isEmpty()) spawned.put(i, squad);
            if (alive >= MAX_CARAVAN_NPC) break;
        }
// вести отряды к следующей точке дороги
        for (Map.Entry<Integer, List<StalkerEntity>> entry : spawned.entrySet()) {
            int idx = entry.getKey();
            if (idx < 0 || idx >= sim.caravans.size()) continue;
            Caravan cv = sim.caravans.get(idx);
            if (cv.leg >= cv.route.length - 1) continue;
            int from = cv.route[cv.leg];
            int to = cv.route[cv.leg + 1];
            String levelId = LevelPoints.levelForScenario(sim.scenario);
            if (levelId == null) continue;
            List<int[]> road = LevelPoints.roadWaypoints(levelId, from, to);
            if (road.isEmpty()) continue;
            for (StalkerEntity e : entry.getValue()) {
                if (!e.isAlive()) continue;
                int nx = e.getPersistentData().getInt("sz_caravan_next_x");
                int ny = e.getPersistentData().getInt("sz_caravan_next_y");
                int nz = e.getPersistentData().getInt("sz_caravan_next_z");
                if (nx == 0 && ny == 0 && nz == 0) continue;
                double dist = Math.hypot(e.getX() - nx, e.getZ() - nz);
                if (dist < 3.0) {
                    int wpi = nearestRoadIndex(road, nx, nz);
                    int ni = Math.min(road.size() - 1, wpi + WAYPOINT_STEP);
                    int[] np = road.get(ni);
                    e.getPersistentData().putInt("sz_caravan_next_x", np[0]);
                    e.getPersistentData().putInt("sz_caravan_next_y", np[1]);
                    e.getPersistentData().putInt("sz_caravan_next_z", np[2]);
                    if (e.getNavigation().isDone())
                        e.getNavigation().moveTo(np[0] + 0.5, np[1], np[2] + 0.5, 0.8);
                } else if (e.getNavigation().isDone()) {
                    e.getNavigation().moveTo(nx + 0.5, ny, nz + 0.5, 0.8);
                }
            }
        }
    }
/** Гибель NPC каравана: ослабить караван, для курьера — потеря груза через Ledger. */
    public static void caravanNpcKilled(StalkerEntity e, Simulation sim) {
        int idx = e.getPersistentData().getInt("sz_caravan_idx");
        if (idx < 0 || idx >= sim.caravans.size()) return;
        Caravan cv = sim.caravans.get(idx);
        cv.guards = Math.max(0, cv.guards - 1);
        if (cv.courierFor >= 0 && e.getPersistentData().getBoolean("sz_courier")) {
            for (int j = 0; j < cv.cargo.qty.length; j++) {
                if (j < sim.ledger.destroyed.length) sim.ledger.destroyed[j] += cv.cargo.qty[j];
                cv.cargo.qty[j] = 0;
            }
            cv.cargo.money = 0;
            sim.factions.get(cv.courierFor).resent(cv.owner, 0.3);
        }
        List<StalkerEntity> list = spawned.get(idx);
        if (list != null) { list.remove(e); if (list.isEmpty()) spawned.remove(idx); }
    }

    private static void clearAll() {
        for (List<StalkerEntity> list : spawned.values())
            for (StalkerEntity e : list) if (e.isAlive()) e.discard();
        spawned.clear();
    }

    private static void discardAll(List<StalkerEntity> list) {
        for (StalkerEntity e : list) if (e.isAlive()) e.discard();
    }

    private static boolean nearAnyPlayer(List<ServerPlayer> players, Caravan cv, Simulation sim) {
        int from = cv.route[cv.leg];
        int to = cv.route[cv.leg + 1];
        String levelId = LevelPoints.levelForScenario(sim.scenario);
        if (levelId == null) return false;
        List<int[]> road = LevelPoints.roadWaypoints(levelId, from, to);
        if (road.isEmpty()) {
            Node fn = sim.nodes.get(from), tn = sim.nodes.get(to);
            double cx = (fn.x + tn.x) / 2.0 * ZoneBounds.SCALE;
            double cz = (fn.z + tn.z) / 2.0 * ZoneBounds.SCALE;
            for (ServerPlayer p : players)
                if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= ACTIVE_RADIUS) return true;
            return false;
        }
        for (ServerPlayer p : players)
            for (int[] pt : road)
                if (Math.hypot(p.getX() - pt[0], p.getZ() - pt[2]) <= ACTIVE_RADIUS) return true;
        return false;
    }

    private static int nearestRoadIndex(List<int[]> road, int x, int z) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < road.size(); i++) {
            double d = Math.hypot(road.get(i)[0] - x, road.get(i)[2] - z);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }

    private static String rankFor(ru.stalkerzone.sim.Faction f) {
        double v = f.exp + RND.nextGaussian() * 25;
        int r = v < 30 ? 0 : v < 60 ? 1 : v < 85 ? 2 : 3;
        return ru.stalkerzone.sim.Person.RANKS[r];
    }
}
