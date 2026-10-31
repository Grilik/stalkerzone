package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Постройки уровней, собранные по геометрии оригинала (этап 1 плана после плейтеста): вместо вокселей .szl
 * в своих границах. Чистая Java (без Minecraft): мир получает готовый набор «позиция → состояние блока».
 */
public final class Prefabs {
    private Prefabs() {}

    public interface Ground { int at(int x, int z); }

    /** Границы {x0, z0, x1, z1}, где воксели уровня заменяются постройками; пусто — уровень без замен. */
    public static List<int[]> clear(String levelId) {
        return "l01_escape".equals(levelId) ? KordonSouthPost.CLEAR : List.of();
    }

    public static boolean cleared(String levelId, int x, int z) {
        for (int[] c : clear(levelId)) if (x >= c[0] && x <= c[2] && z >= c[1] && z <= c[3]) return true;
        return false;
    }

    public static long key(int x, int y, int z) { return ((long) (x + (1 << 21)) << 42) | ((long) (y + 2048) << 21) | (z + (1 << 21)); }
    public static int kx(long k) { return (int) (k >>> 42) - (1 << 21); }
    public static int ky(long k) { return (int) ((k >>> 21) & 0x1fffff) - 2048; }
    public static int kz(long k) { return (int) (k & 0x1fffff) - (1 << 21); }

    /** Все блоки построек уровня (уровень развития lvl), с соединёнными решётками и стёклами. */
    public static Map<Long, String> build(String levelId, Ground g, int lvl) {
        Map<Long, String> m = new HashMap<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { m.put(key(x, y, z), st); }
            @Override public int ground(int x, int z) { return g.at(x, z); }
        };
        if ("l01_escape".equals(levelId)) KordonSouthPost.build(s, lvl);
        connect(m);
        return m;
    }

    /** Решётки и стёкла-панели в генерации мира сами не соединяются: проставить стороны по соседям. */
    static void connect(Map<Long, String> m) {
        Map<Long, String> upd = new HashMap<>();
        for (Map.Entry<Long, String> e : m.entrySet()) {
            String st = e.getValue();
            if (st.contains("[") || !(st.endsWith("iron_bars") || st.endsWith("_pane"))) continue;
            long k = e.getKey();
            int x = kx(k), y = ky(k), z = kz(k);
            String n = side(m, x, y, z - 1), so = side(m, x, y, z + 1), w = side(m, x - 1, y, z), ea = side(m, x + 1, y, z);
            upd.put(k, st + "[north=" + n + ",south=" + so + ",west=" + w + ",east=" + ea + "]");
        }
        m.putAll(upd);
    }

    private static String side(Map<Long, String> m, int x, int y, int z) {
        String s = m.get(key(x, y, z));
        return s != null && !s.equals("minecraft:air") && !s.contains("ladder") && !s.contains("door") ? "true" : "false";
    }
}
