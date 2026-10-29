package ru.stalkerzone.world.xray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Дома и заборы уровня, выделенные из геометрии игры (tools/xray: extract_buildings.py → &lt;id&gt;.bld.json рядом с .szl).
 * Вместо кубиков из вокселей такие постройки собираются по шаблону ({@link HouseBuilder}): ровные стены, двускатная
 * крыша ступенями, окна, двери, заборы — с размерами и материалами оригинала.
 */
public final class LevelBuildings {
    /** Дом: след стен (по осям, с карнизом), уровень земли, высота стен и конька, ось конька, материалы, целость крыши. */
    public record House(int x0, int z0, int x1, int z1, int y, int eave, int ridge, boolean alongX, int wall, int roof,
                        double cover, List<int[]> doors) {
        public boolean covers(int x, int z, int pad) { return x >= x0 - pad && x <= x1 + pad && z >= z0 - pad && z <= z1 + pad; }
    }

    public record Fence(Set<Long> cells, int h) {}

    public final List<House> houses = new ArrayList<>();
    public final List<Fence> fences = new ArrayList<>();
    /** Клетки заборов (для быстрого поиска соседей). */
    public final Map<Long, Fence> fenceAt = new HashMap<>();

    private static final Map<String, LevelBuildings> CACHE = new HashMap<>();
    private static final LevelBuildings EMPTY = new LevelBuildings();

    public static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    public static synchronized LevelBuildings of(String id) {
        return CACHE.computeIfAbsent(id, LevelBuildings::load);
    }

    private static LevelBuildings load(String id) {
        File f = new File(LevelRegions.dir(), id + ".bld.json");
        if (!f.isFile()) return EMPTY;
        LevelBuildings b = new LevelBuildings();
        try (FileReader r = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("buildings")) {
                JsonObject h = e.getAsJsonObject();
                List<int[]> doors = new ArrayList<>();
                for (JsonElement d : h.getAsJsonArray("doors")) {
                    JsonArray a = d.getAsJsonArray();
                    doors.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
                }
                b.houses.add(new House(h.get("x0").getAsInt(), h.get("z0").getAsInt(), h.get("x1").getAsInt(), h.get("z1").getAsInt(),
                        h.get("y").getAsInt(), h.get("eave").getAsInt(), h.get("ridge").getAsInt(), "x".equals(h.get("axis").getAsString()),
                        h.get("wall").getAsInt(), h.get("roof").getAsInt(), h.get("roofCover").getAsDouble(), doors));
            }
            for (JsonElement e : o.getAsJsonArray("fences")) {
                JsonObject fo = e.getAsJsonObject();
                Set<Long> cells = new HashSet<>();
                for (JsonElement c : fo.getAsJsonArray("cells")) {
                    JsonArray a = c.getAsJsonArray();
                    cells.add(key(a.get(0).getAsInt(), a.get(1).getAsInt()));
                }
                Fence fe = new Fence(cells, fo.get("h").getAsInt());
                b.fences.add(fe);
                for (long k : cells) b.fenceAt.put(k, fe);
            }
        } catch (Exception ex) {
            System.err.println("[stalkerzone] не прочитан " + f + ": " + ex);
        }
        return b;
    }

    /** Дом, в след которого (с карнизом) попадает колонка, или null. */
    public House houseAt(int x, int z) {
        for (House h : houses) if (h.covers(x, z, 0)) return h;
        return null;
    }
}
