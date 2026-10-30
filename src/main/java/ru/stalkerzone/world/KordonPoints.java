package ru.stalkerzone.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Точки уровня Кордона (карточка 004): data/stalkerzone/kordon_points.json — координаты Minecraft, снятые с уровня
 * Anomaly l01_escape скриптом tools/xray/kordon_points.py. Здесь — только то, что нужно миру сейчас (граница, старт);
 * костры, места сна, часовые, укрытия и дороги разбирают карточки 009+.
 */
public final class KordonPoints {
    public static final String RESOURCE = "/data/stalkerzone/kordon_points.json";
    /** Уровень оригинала, из которого сделан Кордон. */
    public static final String LEVEL = "l01_escape";

    private static volatile JsonObject root;

    private KordonPoints() {}

    /** Весь файл точек или null, если его нет в сборке. */
    public static JsonObject root() {
        JsonObject r = root;
        if (r != null) return r;
        synchronized (KordonPoints.class) {
            if (root != null) return root;
            try (InputStream in = KordonPoints.class.getResourceAsStream(RESOURCE)) {
                if (in == null) return null;
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                System.err.println("[stalkerzone] kordon_points.json не прочитан: " + e);
                return null;
            }
            return root;
        }
    }

    /** Граница Зоны по реальному краю уровня (игровая область Anomaly), или null. */
    public static ZoneBounds bounds() {
        JsonObject r = root();
        if (r == null || !r.has("bounds")) return null;
        JsonObject b = r.getAsJsonObject("bounds");
        return new ZoneBounds(b.get("minX").getAsInt(), b.get("maxX").getAsInt(), b.get("minZ").getAsInt(), b.get("maxZ").getAsInt());
    }

    /** Точка появления игрока {x, z} у костра деревни новичков, или null. */
    public static int[] spawnXZ() {
        JsonObject r = root();
        if (r == null || !r.has("spawn") || !r.get("spawn").isJsonArray()) return null;
        JsonArray a = r.getAsJsonArray("spawn");
        return new int[]{a.get(0).getAsInt(), a.get(2).getAsInt()};
    }
}
