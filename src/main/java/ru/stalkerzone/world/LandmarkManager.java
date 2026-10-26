package ru.stalkerzone.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Node;

import java.util.List;
import java.util.Random;

/** Отдельные ориентиры карты, не входящие в лагеря: Дуга, антенна Выжигателя, колесо обозрения, градирня, завод «Янтарь». */
public final class LandmarkManager {
    private static final int KEY = 6000;

    private record Spot(String node, int dx, int dz, String id, int radius) {}

    private static final Spot[] SPOTS = {
            // «Дуга» и антенна Выжигателя стоят в центре пси-поля к северу от Радара
            new Spot("Радар", 0, ZoneBiomes.PSI_OFFSET_Z, "duga", 34),
            new Spot("Радар", 40, ZoneBiomes.PSI_OFFSET_Z + 12, "scorcher", 8),
            new Spot("Припять: центр", 48, 42, "wheel", 16),
            new Spot("ЧАЭС", 80, -55, "cooling", 18),
            new Spot("Янтарь", 52, -62, "yantar", 22),
            // v1.14: знаковые места новых локаций
            new Spot("Собачья деревня", 0, 0, "dog_village", 26),
            new Spot("Кордон Лесника", 0, 0, "forester", 8),
            new Spot("Хутор Болотного Доктора", 0, 0, "doctor", 8),
            new Spot("Болота", 58, -46, "church", 10),
            new Spot("Мёртвый город", 60, 50, "dead_city", 30),
    };

    private LandmarkManager() {}

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ZoneData data = ZoneData.get(server);
        for (int i = 0; i < SPOTS.length; i++) {
            if (data.built.contains(KEY + i)) continue;
            Spot sp = SPOTS[i];
            Node n = null;
            for (Node x : data.sim.nodes) if (x.name.equals(sp.node())) n = x;
            if (n == null) continue;
            int cx = n.x * ZoneBounds.SCALE + sp.dx(), cz = n.z * ZoneBounds.SCALE + sp.dz();
            boolean near = false;
            for (ServerPlayer p : players) if (Math.hypot(p.getX() - cx, p.getZ() - cz) < 120 + sp.radius() * 2) near = true;
            if (!near) continue;
            boolean loaded = true;
            for (int x = cx - sp.radius(); x <= cx + sp.radius() && loaded; x += 8)
                for (int z = cz - sp.radius(); z <= cz + sp.radius(); z += 8)
                    if (!level.hasChunk(x >> 4, z >> 4)) { loaded = false; break; }
            if (!loaded) continue;
            int y = SpawnFinder.groundAt(level, cx, cz).getY();
            Painter pt = new Painter(level, cx, y, cz);
            switch (sp.id()) {
                case "duga" -> Landmarks.duga(pt);
                case "scorcher" -> Landmarks.scorcherTower(pt);
                case "wheel" -> Landmarks.ferrisWheel(pt);
                case "cooling" -> Landmarks.coolingTower(pt);
                case "dog_village" -> Landmarks.dogVillage(pt, new Random(i * 31L));
                case "forester", "doctor" -> Landmarks.hut(pt, sp.id().equals("doctor"));
                case "church" -> Landmarks.swampChurch(pt);
                case "dead_city" -> Landmarks.deadCity(pt, new Random(i * 31L));
                default -> Landmarks.yantarFactory(pt, new Random(i * 31L));
            }
            data.built.add(KEY + i);
            data.setDirty();
            return; // не больше одного ориентира за вызов
        }
    }
}
