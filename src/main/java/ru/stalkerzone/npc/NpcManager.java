package ru.stalkerzone.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneWorld;
import ru.stalkerzone.zonemap.build.Prefabs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Держит число видимых NPC на точке равным тому, что говорит симуляция. Работает только рядом с игроками,
 * чтобы не грузить чанки без нужды. Лишних NPC убирает без последствий для симуляции, а вот гибель в бою
 * уменьшает численность фракции.
 */
public final class NpcManager {
    /** Радиус, в котором NPC привязаны к точке (блоки). */
    private static final int AREA = 48;
    /** Пока игрок ближе этого расстояния к точке, её NPC поддерживаются. */
    private static final int ACTIVE = 110;
    private static final int MAX_PER_NODE = 8;

    /** Места вокруг костра (карточка 028): точка campfire — центр, NPC садятся на радиусе 2. */
    private static final int[][] CAMPFIRE_RING = {
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

    private static final String[] CALLSIGNS = {"Лис", "Грач", "Бродяга", "Седой", "Хмурый", "Дым", "Шрам", "Курсант", "Топор", "Весёлый",
            "Тихий", "Бык", "Север", "Сыч", "Ворон", "Гвоздь", "Малой", "Кащей", "Доцент", "Рыжий", "Бахмач", "Ёж", "Угрюмый", "Сапёр"};
    /** Случайный позывной. */
    public static String callsign() { return CALLSIGNS[RND.nextInt(CALLSIGNS.length)]; }
    private static String traderName(Node n) {
        return ru.stalkerzone.sim.TraderNames.name(n);
    }

    /** Звание рядового по среднему опыту фракции. */
    private static String rankFor(Faction f) {
        double v = f.exp + RND.nextGaussian() * 25;
        int r = v < 30 ? 0 : v < 60 ? 1 : v < 85 ? 2 : 3;
        return ru.stalkerzone.sim.Person.RANKS[r];
    }

    private static final Random RND = new Random();

    private NpcManager() {}

    /** Вызывать раз в секунду. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        Simulation sim = ZoneData.get(server).sim;
        for (Node n : sim.nodes) {
            if (!near(players, n)) continue;
            ensure(level, sim, n);
        }
    }

    private static boolean near(List<ServerPlayer> players, Node n) {
        double cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        for (ServerPlayer p : players) {
            if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= ACTIVE) return true;
        }
        return false;
    }

    /** Сколько NPC показывать на точке. */
    public static int target(Simulation sim, Node n) {
        if (n.owner < 0) return 0;
        Faction f = sim.factions.get(n.owner);
        int owned = Math.max(1, sim.ownedCount(f));
        double share = f.members * 0.35 / owned / 3.0 * (n.id == f.baseNode ? 1.5 : 1.0);
        return Math.max(1, Math.min(MAX_PER_NODE, (int) Math.round(share)));
    }

    private static void ensure(ServerLevel level, Simulation sim, Node n) {
        int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        if (!level.hasChunk(cx >> 4, cz >> 4)) return;
        AABB box = new AABB(cx - AREA, level.getMinBuildHeight(), cz - AREA, cx + AREA, level.getMaxBuildHeight(), cz + AREA);
        List<StalkerEntity> here = level.getEntitiesOfClass(StalkerEntity.class, box, e -> e.homeNode() == n.id && e.isAlive());

        List<StalkerEntity> stalkers = new ArrayList<>();
        List<StalkerEntity> builders = new ArrayList<>();
        StalkerEntity trader = null;
        for (StalkerEntity e : here) {
            if (e.isTrader()) {
                if (trader == null) trader = e; else e.discard();
            } else if (e.isBuilder()) {
                builders.add(e);
            } else {
                stalkers.add(e);
            }
        }
        builders(level, sim, n, builders);

        if (n.trader && trader == null) {
            // карточка 009: на постройках уровня торговец — в точке trader из points
            BlockPos tp = levelPointPos(level, sim, n, "trader");
            if (tp != null) spawnAt(level, StalkerEntity.ROLE_TRADER, Math.max(0, n.owner), n, tp, Component.literal("Торговец " + traderName(n)));
            else spawn(level, sim, n, StalkerEntity.ROLE_TRADER, Math.max(0, n.owner), cx, cz, 0);
        }

        int wanted = target(sim, n);
        // чужие фракции на точке больше не живут: она сменила владельца
        stalkers.removeIf(e -> {
            if (n.owner < 0 || e.faction() != n.owner) {
                e.discard();
                return true;
            }
            return false;
        });
        if (stalkers.size() < wanted) {
            StalkerEntity e;
            if (n.existingBuildings) {
                // карточка 009: NPC на точках beds/campfire/guards
                e = spawnAtLevelPoint(level, sim, n, stalkers);
            } else {
                e = spawn(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner, cx, cz, 22);
            }
            if (e != null) assignPerson(sim, n, e, stalkers);
        } else if (stalkers.size() > wanted + 1) {
            // рядовых убирать раньше именных
            StalkerEntity drop = stalkers.get(0);
            for (StalkerEntity e : stalkers) if (e.personId() == 0) { drop = e; break; }
            drop.discard();
        }
    }

    /** На базе фракции появляются её именные сталкеры: сначала лидер и заместитель, потом остальные. */
    private static void assignPerson(Simulation sim, Node n, StalkerEntity e, List<StalkerEntity> present) {
        Faction f = sim.factions.get(e.faction());
        java.util.Set<Integer> here = new java.util.HashSet<>();
        for (StalkerEntity o : present) if (o.personId() > 0) here.add(o.personId());
        ru.stalkerzone.sim.Person pick = null;
        if (n.id == f.baseNode) {
            for (ru.stalkerzone.sim.Person p : sim.people.of(f.id)) {
                if (here.contains(p.id) || p.injury > 0) continue;
                if (pick == null || p.role.ordinal() > pick.role.ordinal()) pick = p;
            }
            if (pick != null && pick.role.ordinal() < ru.stalkerzone.sim.Person.Role.SQUAD_LEADER.ordinal() && RND.nextDouble() < 0.5) pick = null;
        } else if (RND.nextDouble() < 0.25) {
            for (ru.stalkerzone.sim.Person p : sim.people.of(f.id)) {
                if (here.contains(p.id) || p.injury > 0 || p.role == ru.stalkerzone.sim.Person.Role.LEADER || p.role == ru.stalkerzone.sim.Person.Role.DEPUTY) continue;
                pick = p;
                break;
            }
        }
        if (pick != null) {
            e.setPerson(pick.id);
            e.setCustomName(Component.literal(pick.name + " [" + pick.rankRu() + ", " + pick.role.ru + "]"));
            e.setMedic(pick.cls == ru.stalkerzone.sim.Person.Cls.MEDIC || pick.cls == ru.stalkerzone.sim.Person.Cls.DOCTOR);
            if (pick.role == ru.stalkerzone.sim.Person.Role.LEADER || pick.role == ru.stalkerzone.sim.Person.Role.DEPUTY) e.equipLeader();
        } else {
            e.setMedic(RND.nextDouble() < (sim.people.medics(f.id) > 0 ? 0.18 : 0.08));
        }
    }

    /** Строители чинят повреждённые постройки своей точки (v1.6): по одному у каждой ремонтируемой. */
    private static void builders(ServerLevel level, Simulation sim, Node n, List<StalkerEntity> builders) {
        List<ru.stalkerzone.sim.Building> broken = new ArrayList<>();
        if (n.owner >= 0) for (ru.stalkerzone.sim.Building b : sim.buildingsAt(n.id)) if (ru.stalkerzone.world.StructureManager.repairing(sim, n, b)) broken.add(b);
        builders.removeIf(e -> {
            if (broken.isEmpty() || e.faction() != n.owner || builders.indexOf(e) >= broken.size()) {
                e.discard();
                return true;
            }
            return false;
        });
        if (builders.size() >= Math.min(2, broken.size()) || broken.isEmpty()) return;
        ru.stalkerzone.sim.Building b = broken.get(builders.size());
        BlockPos site = ru.stalkerzone.world.StructureManager.siteCenter(level, sim, n, b.type);
        if (site == null) return;
        spawnAt(level, StalkerEntity.ROLE_BUILDER, n.owner, n, site, Component.literal("Строитель"));
    }

    private static void spawnAt(ServerLevel level, int role, int faction, Node n, BlockPos around, Component name) {
        int x = around.getX() + RND.nextInt(7) - 3, z = around.getZ() + RND.nextInt(7) - 3;
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        BlockPos pos = SpawnFinder.groundAt(level, x, z);
        StalkerEntity e = ModEntities.STALKER.get().create(level);
        if (e == null) return;
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        e.configure(faction, role, n.id, pos, name);
        level.addFreshEntity(e);
    }

    private static StalkerEntity spawn(ServerLevel level, Simulation sim, Node n, int role, int faction, int cx, int cz, int radius) {
        int x = cx + (radius == 0 ? 0 : RND.nextInt(radius * 2 + 1) - radius);
        int z = cz + (radius == 0 ? 0 : RND.nextInt(radius * 2 + 1) - radius);
        ZoneBounds b = ZoneWorld.bounds();
        if (!b.inside(x, z) || !level.hasChunk(x >> 4, z >> 4)) return null;
        BlockPos pos = SpawnFinder.groundAt(level, x, z);
        if (!level.getFluidState(pos.below()).isEmpty()) return null;
        // жители стоят только на естественной земле, не на крышах построек
        net.minecraft.world.level.block.state.BlockState below = level.getBlockState(pos.below());
        if (!(below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(net.minecraft.tags.BlockTags.SAND)
                || below.is(net.minecraft.world.level.block.Blocks.GRAVEL) || below.is(net.minecraft.world.level.block.Blocks.STONE)
                || below.is(net.minecraft.world.level.block.Blocks.MUD) || below.is(net.minecraft.world.level.block.Blocks.PODZOL))) return null;

        StalkerEntity e = ModEntities.STALKER.get().create(level);
        if (e == null) return null;
        String name = role == StalkerEntity.ROLE_TRADER ? traderName(n)
                : CALLSIGNS[RND.nextInt(CALLSIGNS.length)] + " [" + rankFor(sim.factions.get(faction)) + "]";
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        e.configure(faction, role, n.id, pos, Component.literal(role == StalkerEntity.ROLE_TRADER ? "Торговец " + name : name));
        level.addFreshEntity(e);
        return e;
    }

    /** Точка в мире из level points или null. */
    private static BlockPos levelPointPos(ServerLevel level, Simulation sim, Node n, String field) {
        String levelId = ru.stalkerzone.world.LevelPoints.levelForScenario(sim.scenario);
        if (levelId == null) return null;
        com.google.gson.JsonObject pts = ru.stalkerzone.world.LevelPoints.nodePoints(levelId, n.id);
        if (pts == null) return null;
        int[] v = ru.stalkerzone.world.LevelPoints.readVec(pts, field);
        if (v == null || v.length < 3) return null;
        return new BlockPos(v[0], v[1], v[2]);
    }
    /** Радиус «тихой зоны» у торговца: группировки не начинают бой (блоки, карточка 011). */
    public static final int QUIET_RADIUS = 12;

    /** Точка торговца узла в мире (trader из points уровня) или центр узла, либо null. */
    public static BlockPos traderSpot(ServerLevel level, Simulation sim, Node n) {
        BlockPos p = levelPointPos(level, sim, n, "trader");
        if (p != null) return p;
        return new BlockPos(n.x * ZoneBounds.SCALE, level.getMinBuildHeight(), n.z * ZoneBounds.SCALE);
    }

    /** В «тихой зоне» торговца ли точка мира: у бункера группировки не начинают бой друг с другом (карточка 011). */
    public static boolean quietZone(ServerLevel level, Simulation sim, double x, double z) {
        for (Node n : sim.nodes) {
            if (!n.trader) continue;
            BlockPos c = traderSpot(level, sim, n);
            if (Math.hypot(x - c.getX(), z - c.getZ()) <= QUIET_RADIUS) return true;
        }
        return false;
    }



    /** Спавн NPC на рабочем месте: из построек уровня (карточка 031) или из старых точек level points. */
    private static StalkerEntity spawnAtLevelPoint(ServerLevel level, Simulation sim, Node n,
                                                    List<StalkerEntity> present) {
        List<Prefabs.Point> pp = prefabPoints(level, sim, n);
        if (pp != null) return spawnAtPrefabPoint(level, sim, n, present, pp);
        String levelId = ru.stalkerzone.world.LevelPoints.levelForScenario(sim.scenario);
        if (levelId == null) return null;
        com.google.gson.JsonObject pts = ru.stalkerzone.world.LevelPoints.nodePoints(levelId, n.id);
        if (pts == null) return null;

        // Собираем доступные точки: guards → часовые, beds → жители, campfire → дежурный
        java.util.List<int[]> guards = ru.stalkerzone.world.LevelPoints.readVecs(pts, "guards");
        java.util.List<int[]> beds = ru.stalkerzone.world.LevelPoints.readVecs(pts, "beds");
        int[] campfire = ru.stalkerzone.world.LevelPoints.readVec(pts, "campfire");

        // Сначала guards (первые двое — часовые)
        int guardCount = 0;
        for (StalkerEntity e : present) {
            if (e.getPersistentData().getBoolean("sz_guard")) guardCount++;
        }
        if (guardCount < Math.min(2, guards.size()) && !guards.isEmpty()) {
            int[] spot = guards.get(RND.nextInt(guards.size()));
            BlockPos pos = new BlockPos(spot[0], spot[1], spot[2]);
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                StalkerEntity e = spawnFixed(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner, pos);
                if (e != null) { e.getPersistentData().putBoolean("sz_guard", true); return e; }
            }
        }

        // Потом beds и campfire
        java.util.List<int[]> spots = new java.util.ArrayList<>();
        for (int[] bed : beds) spots.add(bed);
        int campfireFrom = spots.size();
        if (campfire != null && campfire.length >= 3) {
            // карточка 028: точка campfire — центр костра, NPC садятся рядом, на радиусе 2 (не в огонь)
            for (int[] d : CAMPFIRE_RING) {
                spots.add(new int[]{campfire[0] + d[0], campfire[1], campfire[2] + d[1]});
            }
        }
        if (!spots.isEmpty()) {
            int idx = RND.nextInt(spots.size());
            int[] spot = spots.get(idx);
            BlockPos pos = new BlockPos(spot[0], spot[1], spot[2]);
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                StalkerEntity e = spawnFixed(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner, pos);
                if (e != null && campfire != null && campfire.length >= 3 && idx >= campfireFrom) {
                    // своё место у костра: NPC сидит здесь и смотрит на огонь
                    e.getPersistentData().putBoolean("sz_campfire", true);
                    e.getPersistentData().putInt("sz_fire_x", campfire[0]);
                    e.getPersistentData().putInt("sz_fire_y", campfire[1]);
                    e.getPersistentData().putInt("sz_fire_z", campfire[2]);
                }
                return e;
            }
        }

        // fallback: случайная точка в радиусе
        int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        return spawn(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner, cx, cz, 22);
    }

    /** Рабочие места постройки узла (карточка 031): точки не меняются, поэтому кэш по уровню. */
    private static final java.util.Map<String, List<Prefabs.Point>> PREFAB_POINTS = new java.util.HashMap<>();

    /** Точки постройки, в чьих границах замены лежит центр узла, или null, если постройки нет. */
    private static List<Prefabs.Point> prefabPoints(ServerLevel level, Simulation sim, Node n) {
        String levelId = ru.stalkerzone.world.LevelPoints.levelForScenario(sim.scenario);
        if (levelId == null || Prefabs.clear(levelId).isEmpty()) return null;
        int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        if (!Prefabs.cleared(levelId, cx, cz)) return null;
        synchronized (PREFAB_POINTS) {
            List<Prefabs.Point> ps = PREFAB_POINTS.get(levelId);
            if (ps == null) {
                // y нужен только точкам; непрогруженные чанки не генерируем
                Prefabs.Ground g = (x, z) -> level.hasChunk(x >> 4, z >> 4)
                        ? SpawnFinder.groundAt(level, x, z).getY() : 75;
                ps = Prefabs.points(levelId, g);
                PREFAB_POINTS.put(levelId, ps);
            }
            return ps;
        }
    }

    /** Спавн на рабочих местах постройки: часовые на постах, остальные — на нарах и у костра. */
    private static StalkerEntity spawnAtPrefabPoint(ServerLevel level, Simulation sim, Node n,
                                                     List<StalkerEntity> present, List<Prefabs.Point> pts) {
        List<Prefabs.Point> guards = ofKind(pts, "guard");
        List<Prefabs.Point> spots = new java.util.ArrayList<>(ofKind(pts, "bed"));
        int beds = spots.size();
        spots.addAll(ofKind(pts, "cook"));
        // Сначала guards (первые двое — часовые)
        int guardCount = 0;
        for (StalkerEntity e : present) if (e.isGuard()) guardCount++;
        if (guardCount < Math.min(2, guards.size())) {
            Prefabs.Point p = guards.get(RND.nextInt(guards.size()));
            StalkerEntity e = spawnFixed(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner,
                    new BlockPos(p.x(), p.y(), p.z()));
            if (e != null) { e.getPersistentData().putBoolean("sz_guard", true); return e; }
        }
        // Потом нары и костёр
        if (!spots.isEmpty()) {
            int idx = RND.nextInt(spots.size());
            Prefabs.Point p = spots.get(idx);
            StalkerEntity e = spawnFixed(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner,
                    new BlockPos(p.x(), p.y(), p.z()));
            if (e != null && idx >= beds) {   // своё место у костра: NPC сидит здесь и смотрит на огонь
                e.getPersistentData().putBoolean("sz_campfire", true);
                e.getPersistentData().putInt("sz_fire_x", p.x());
                e.getPersistentData().putInt("sz_fire_y", p.y());
                e.getPersistentData().putInt("sz_fire_z", p.z());
            }
            if (e != null) return e;
        }
        int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        return spawn(level, sim, n, StalkerEntity.ROLE_STALKER, n.owner, cx, cz, 22);
    }

    /** Точки нужного вида, в порядке сборщика. */
    private static List<Prefabs.Point> ofKind(List<Prefabs.Point> pts, String kind) {
        List<Prefabs.Point> out = new java.util.ArrayList<>();
        for (Prefabs.Point p : pts) if (p.kind().equals(kind)) out.add(p);
        return out;
    }

    /** Спавн в конкретную точку (без проверки земли — полагаемся на точку из файла). */
    private static StalkerEntity spawnFixed(ServerLevel level, Simulation sim, Node n, int role, int faction,
                                            BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        BlockPos ground = SpawnFinder.groundAt(level, pos.getX(), pos.getZ());
        StalkerEntity e = ModEntities.STALKER.get().create(level);
        if (e == null) return null;
        String name = CALLSIGNS[RND.nextInt(CALLSIGNS.length)] + " ["
                + rankFor(sim.factions.get(faction)) + "]";
        e.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
        e.configure(faction, role, n.id, ground, Component.literal(name));
        level.addFreshEntity(e);
        return e;
    }
}
