package ru.stalkerzone.world;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Building;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;

import java.util.List;

/**
 * Приводит лагеря в мире к состоянию симуляции. Площадка строится один раз, а затем каждая постройка перерисовывается,
 * когда у неё меняется уровень, состояние (целая, потрескавшаяся, пробитая, обрушенная) или владелец точки.
 * Работает только рядом с игроком, за один вызов перерисовывает не больше одной постройки.
 */
public final class StructureManager {
    private static final int ACTIVE = 110;

    private StructureManager() {}

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ZoneData data = ZoneData.get(server);
        boolean worked = false;
        for (Node n : data.sim.nodes) {
            if (worked) break;
            int cls = data.sim.campClass(n);
            double cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
            if (ru.stalkerzone.world.xray.LevelRegions.covers((int) cx, (int) cz)) {
                // карточка 009: узлы на постройках уровня — без генерации лагеря, только декор
                if (n.existingBuildings) {
                    boolean near = false;
                    for (ServerPlayer p : players) if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= ACTIVE) near = true;
                    if (near) worked = levelNodeDecoration(level, data, n);
                }
                continue;
            }
            if (data.sim.buildingsAt(n.id).isEmpty()) continue;
            boolean near = false;
            for (ServerPlayer p : players) if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= ACTIVE) near = true;
            if (!near) continue;
            worked = step(level, data, n, cls);
        }
    }

    /** Декор на существующих постройках уровня: костёр, флаг, лежанки, ящики (карточка 009). */
    private static boolean levelNodeDecoration(ServerLevel level, ZoneData data, Node n) {
        String levelId = LevelPoints.levelForScenario(data.sim.scenario);
        if (levelId == null) return false;
        JsonObject pts = LevelPoints.nodePoints(levelId, n.id);
        if (pts == null) return false;

        // ключ декора: id узла + 1000 (отдельно от built лагерей)
        int decoKey = n.id + 10000;
        Integer lastOwner = data.rendered.get(decoKey);
        if (lastOwner != null && lastOwner == n.owner) return false; // уже нарисовано

        int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        // проверяем загрузку чанков
        for (int x = cx - 48; x <= cx + 48; x += 8)
            for (int z = cz - 48; z <= cz + 48; z += 8)
                if (!level.hasChunk(x >> 4, z >> 4)) return false;

        Camp.placeLevelDecor(level, n, pts, cx, cz);
        // карточка 013: радиоактивные пятна (насыпь, техника у дыры, ферма) — блоки с тегом radioactive
        RadioactiveSpots.place(level, data.sim, n, cx, cz);
        data.rendered.put(decoKey, n.owner);
        data.setDirty();
        return true;
    }

    /** Один шаг для точки: построить площадку или перерисовать одну постройку. Возвращает true, если что-то изменено. */
    private static boolean step(ServerLevel level, ZoneData data, Node n, int cls) {
        Simulation sim = data.sim;
        int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
        if (!data.built.contains(n.id)) {
            int r = Camp.radius(cls) + 6;
            for (int x = cx - r; x <= cx + r; x += 8)
                for (int z = cz - r; z <= cz + r; z += 8)
                    if (!level.hasChunk(x >> 4, z >> 4)) return false;
            BlockPos ground = SpawnFinder.groundAt(level, cx, cz);
            Camp.buildGround(level, n, cls, n.owner, ground, n.trader);
            data.campY.put(n.id, ground.getY());
            data.built.add(n.id);
            data.setDirty();
            return true;
        }
        BlockPos ground = new BlockPos(cx, data.campY.getOrDefault(n.id, level.getMinBuildHeight() + 64), cz);
        for (Building b : sim.buildingsAt(n.id)) {
            int key = n.id * 16 + b.type.ordinal();
            boolean rep = repairing(sim, n, b);
            // стройка перерисовывается по четвертям готовности: леса растут вместе с работой бригады
            int stage = b.building() ? 1 + (int) (b.progress() * 4) : 0;
            int state = ((((n.owner + 1) * 10 + b.level) * 10 + b.condition()) * 2 + (rep ? 1 : 0)) * 8 + stage;
            Integer drawn = data.rendered.get(key);
            if (drawn != null && drawn == state) continue;
            if (!Camp.allLoaded(level, n, cls, n.housing, ground, b.type)) continue;
            Camp.renderBuilding(level, n, cls, n.housing, n.owner, ground, b.type, b.level, b.condition(), rep);
            data.rendered.put(key, state);
            data.setDirty();
            return true;
        }
        return false;
    }

    /** Постройку чинят или строят: у точки есть хозяин, идёт стройка или она повреждена, и на точку сейчас не идёт штурм. */
    public static boolean repairing(Simulation sim, Node n, Building b) {
        if (n.owner < 0) return false;
        if (!b.building() && (b.level <= 0 || b.condition() == 0)) return false;
        for (ru.stalkerzone.sim.Faction f : sim.factions) if (f.phase == ru.stalkerzone.sim.Phase.WAR && f.warNode == n.id) return false;
        return true;
    }

    /** Центр первого места постройки данного типа на точке (в блоках мира) или null, если лагерь ещё не построен. */
    public static BlockPos siteCenter(ServerLevel level, Simulation sim, Node n, ru.stalkerzone.sim.BuildingType type) {
        ZoneData data = ZoneData.get(level.getServer());
        Integer y = data.campY.get(n.id);
        if (y == null) return null;
        int cls = sim.campClass(n);
        for (Camp.Site s : Camp.sitesOf(Camp.layout(n, cls, n.housing), type)) {
            return new BlockPos(n.x * ZoneBounds.SCALE + s.cx(), y, n.z * ZoneBounds.SCALE + s.cz());
        }
        return null;
    }

    /** Забыть, что нарисовано на точке: следующие тики построят её заново (команда /zone rebuild). */
    public static boolean forget(ZoneData data, Node n) {
        data.built.remove(n.id);
        data.rendered.keySet().removeIf(k -> k / 16 == n.id);
        data.setDirty();
        return true;
    }
}
