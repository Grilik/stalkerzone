package ru.stalkerzone.zonemap.atlas;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Атлас Зоны (Э1-13, решение б1): world.json + locations/&lt;id&gt;.json. Чистая Java, без Minecraft.
 * Хранит разобранный JSON (Map/List) — схема в data/stalkerzone/atlas/atlas.schema.json,
 * проверки — {@link AtlasValidator}. Мод атлас пока не читает (Э1-05…Э1-07, Э2-05).
 */
public final class Atlas {
    public static final String RESOURCE_PREFIX = "/data/stalkerzone/atlas/";

    public final Map<String, Object> world;
    /** Локации в порядке world.locations. */
    public final List<Map<String, Object>> locations;

    public Atlas(Map<String, Object> world, List<Map<String, Object>> locations) {
        this.world = world;
        this.locations = locations;
    }

    /** Читает world.json и все локации; {@code reader} отдаёт текст по пути относительно папки атласа. */
    public static Atlas load(Function<String, String> reader) {
        Map<String, Object> world = Json.obj(Json.parse(reader.apply("world.json")));
        if (world == null) throw new IllegalArgumentException("world.json: ожидался объект");
        List<Object> refs = Json.list(world.get("locations"));
        if (refs == null) throw new IllegalArgumentException("world.json: нет locations[]");
        List<Map<String, Object>> locs = new ArrayList<>();
        for (Object r : refs) {
            Map<String, Object> ref = Json.obj(r);
            String file = ref == null ? null : Json.str(ref.get("file"));
            if (file == null) throw new IllegalArgumentException("world.json: у локации нет file");
            Map<String, Object> loc = Json.obj(Json.parse(reader.apply(file)));
            if (loc == null) throw new IllegalArgumentException(file + ": ожидался объект");
            locs.add(loc);
        }
        return new Atlas(world, locs);
    }

    /** Из папки на диске (тесты, конвейер). */
    public static Atlas loadDir(Path dir) {
        return load(rel -> {
            try {
                return Files.readString(dir.resolve(rel), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("атлас: не прочитан " + rel, e);
            }
        });
    }

    /** Из ресурсов мода (data/stalkerzone/atlas/). */
    public static Atlas loadResources() {
        return load(rel -> {
            try (InputStream in = Atlas.class.getResourceAsStream(RESOURCE_PREFIX + rel)) {
                if (in == null) throw new IllegalArgumentException("атлас: нет ресурса " + rel);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalArgumentException("атлас: не прочитан " + rel, e);
            }
        });
    }

    public static String id(Map<String, Object> m) { return Json.str(m.get("id")); }

    /** Все объекты всех локаций. */
    public List<Map<String, Object>> objects() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> loc : locations) out.addAll(objects(loc));
        return out;
    }

    public static List<Map<String, Object>> objects(Map<String, Object> loc) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Object> l = Json.list(loc.get("objects"));
        if (l != null) for (Object o : l) { Map<String, Object> m = Json.obj(o); if (m != null) out.add(m); }
        return out;
    }

    /** Объекты-узлы симуляции: номер узла -> объект (по возрастанию номера). */
    public Map<Integer, Map<String, Object>> nodes() {
        Map<Integer, Map<String, Object>> r = new TreeMap<>();
        for (Map<String, Object> o : objects()) {
            double n = Json.num(o.get("node"));
            if (!Double.isNaN(n)) r.put((int) n, o);
        }
        return r;
    }

    /** x позиции объекта (первая координата pos). */
    public static double x(Map<String, Object> obj) { double[] p = Json.nums(obj.get("pos")); return p[0]; }

    /** z позиции объекта (последняя координата pos: [x, z] или [x, y, z]). */
    public static double z(Map<String, Object> obj) { double[] p = Json.nums(obj.get("pos")); return p[p.length - 1]; }
}
