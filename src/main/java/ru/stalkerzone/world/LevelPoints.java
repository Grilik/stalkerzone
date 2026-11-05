package ru.stalkerzone.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Точки уровня из data/stalkerzone/points/&lt;levelId&gt;.json (карточка 009).
 * Обобщает KordonPoints: одна точка чтения для всех уровней.
 * Уровни — это локации оригинала (Anomaly), перенесённые 1:1.
 * Код мира берёт точки узла через сценарий (узел → уровень → точки).
 */
public final class LevelPoints {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    public static final String RESOURCE_PREFIX = "/data/stalkerzone/points/";
    public static final String SUFFIX = ".json";

    private static volatile java.util.Map<String, JsonObject> cache;

    private LevelPoints() {}

    /** Весь JSON-файл уровня или null, если файла нет в сборке. */
    public static JsonObject forLevel(String levelId) {
        if (cache == null) cache = new java.util.concurrent.ConcurrentHashMap<>();
        return cache.computeIfAbsent(levelId, id -> {
            String path = RESOURCE_PREFIX + id + SUFFIX;
            try (InputStream in = LevelPoints.class.getResourceAsStream(path)) {
                if (in == null) return null;
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                LOG.error("[stalkerzone] points/" + id + ".json не прочитан", e);
                return null;
            }
        });
    }

    /** ID уровня по ID сценария (null, если у сценария нет уровня 1:1). */
    public static String levelForScenario(String scenarioId) {
        if (ru.stalkerzone.sim.Scenarios.KORDON.equals(scenarioId)) return "l01_escape";
        return null;
    }

    /** Узел в файле точек уровня по его id или null. */
    public static JsonObject nodePoints(String levelId, int nodeId) {
        JsonObject root = forLevel(levelId);
        if (root == null || !root.has("nodes") || !root.get("nodes").isJsonArray()) return null;
        JsonArray nodes = root.getAsJsonArray("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject n = nodes.get(i).getAsJsonObject();
            if (n.has("id") && n.get("id").getAsInt() == nodeId) return n;
        }
        return null;
    }

    /** Есть ли у узла точки на уровне (т.е. реальные постройки уровня)? */
    public static boolean hasPoints(String levelId, int nodeId) {
        return nodePoints(levelId, nodeId) != null;
    }

    /** Граница Зоны из файла точек уровня или null. */
    public static ZoneBounds bounds(String levelId) {
        JsonObject r = forLevel(levelId);
        if (r == null || !r.has("bounds")) return null;
        JsonObject b = r.getAsJsonObject("bounds");
        return new ZoneBounds(b.get("minX").getAsInt(), b.get("maxX").getAsInt(),
                b.get("minZ").getAsInt(), b.get("maxZ").getAsInt());
    }

    /** Точка появления игрока {x, z} из файла точек уровня или null. */
    public static int[] spawnXZ(String levelId) {
        JsonObject r = forLevel(levelId);
        if (r == null || !r.has("spawn") || !r.get("spawn").isJsonArray()) return null;
        JsonArray a = r.getAsJsonArray("spawn");
        return new int[]{a.get(0).getAsInt(), a.get(2).getAsInt()};
    }

    /** Рабочие места площадки узла (сборщики Prefabs) нужного вида; высота — по карте высот уровня (с площадками). */
    public static java.util.List<int[]> prefabVecs(String levelId, int nodeId, String kind) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (levelId == null || ru.stalkerzone.zonemap.build.Prefabs.site(levelId, nodeId) == null) return out;
        String key = levelId + "#" + nodeId;
        java.util.List<ru.stalkerzone.zonemap.build.Prefabs.Point> ps = PREFAB.get(key);
        if (ps == null) {
            ru.stalkerzone.world.xray.LevelRegion r = ru.stalkerzone.world.xray.LevelRegions.byId(levelId);
            if (r == null) return out;   // без уровня высоты построек неизвестны
            ps = ru.stalkerzone.zonemap.build.Prefabs.points(levelId, r::groundBlock,
                    ru.stalkerzone.zonemap.build.Prefabs.POINT_LEVEL, nodeId);
            PREFAB.put(key, ps);
        }
        for (ru.stalkerzone.zonemap.build.Prefabs.Point p : ps) if (p.kind().equals(kind)) out.add(new int[] {p.x(), p.y(), p.z()});
        return out;
    }

    private static final java.util.Map<String, java.util.List<ru.stalkerzone.zonemap.build.Prefabs.Point>> PREFAB =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Точка узла вида kind: сначала рабочее место постройки (оно знает пол комнаты — торговец в подземном бункере),
     * иначе точка из файла уровня. null — нет ни той, ни другой.
     */
    public static int[] pointOf(String levelId, int nodeId, String kind) {
        java.util.List<int[]> pre = prefabVecs(levelId, nodeId, kind);
        if (!pre.isEmpty()) return pre.get(0);
        int[] v = readVec(nodePoints(levelId, nodeId), kind);
        return v == null || v.length < 3 ? null : v;
    }

    /** Все точки узла вида kind: рабочие места построек и точки файла уровня (укрытия от выброса). */
    public static java.util.List<int[]> pointsOf(String levelId, int nodeId, String kind) {
        java.util.List<int[]> out = prefabVecs(levelId, nodeId, kind);
        for (int[] v : readVecs(nodePoints(levelId, nodeId), kind)) if (v.length >= 3) out.add(v);
        return out;
    }

    /** Читает массив координат из поля JSON [x,y,z,...] (один вектор). */
    public static int[] readVec(JsonObject obj, String field) {
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) return null;
        if (!obj.get(field).isJsonArray()) return null;
        JsonArray a = obj.getAsJsonArray(field);
        int[] v = new int[a.size()];
        for (int i = 0; i < a.size(); i++) v[i] = a.get(i).getAsInt();
        return onPlateau(v);
    }

    /**
     * П-02: точки на плато площадки (деревня) — на высоте плато, а не исходной земли из kordon_points.json
     * (y = над верхним блоком земли уровня, уже исправленной площадкой).
     */
    static int[] onPlateau(int[] v) {
        if (v.length < 3 || ru.stalkerzone.zonemap.build.Prefabs.plateauAt(v[0], v[2]) == null) return v;
        ru.stalkerzone.world.xray.LevelRegion r = ru.stalkerzone.world.xray.LevelRegions.at(v[0], v[2]);
        if (r != null) v[1] = r.groundBlock(v[0], v[2]) + 1;
        return v;
    }

    /** Читает массив массивов координат из поля JSON [[x,y,z], ...]. */
    public static java.util.List<int[]> readVecs(JsonObject obj, String field) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        if (obj == null || !obj.has(field) || obj.get(field).isJsonNull()) return out;
        if (!obj.get(field).isJsonArray()) return out;
        JsonArray a = obj.getAsJsonArray(field);
        for (int i = 0; i < a.size(); i++) {
            JsonArray p = a.get(i).getAsJsonArray();
            int[] v = new int[p.size()];
            for (int j = 0; j < p.size(); j++) v[j] = p.get(j).getAsInt();
            out.add(onPlateau(v));
        }
        return out;
    }

    /** Дорожные точки между двумя узлами уровня (из поля roads). Ключ: &quot;from-to&quot;. */
    public static java.util.List<int[]> roadWaypoints(String levelId, int fromNode, int toNode) {
        JsonObject root = forLevel(levelId);
        if (root == null || !root.has("roads")) return java.util.Collections.emptyList();
        JsonObject roads = root.getAsJsonObject("roads");
        String key = fromNode + "-" + toNode;
        if (!roads.has(key)) key = toNode + "-" + fromNode;
        if (!roads.has(key)) return java.util.Collections.emptyList();
        return readVecs(roads, key);
    }
}
