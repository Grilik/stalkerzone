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

    /** Рабочее место постройки (карточка 031): вид, координаты (y — пол над землёй/площадкой). */
    public record Point(String kind, int x, int y, int z) {}

    /** Уровень развития, на котором снимаются рабочие места (как генерация мира). */
    public static final int POINT_LEVEL = 1;

    /** Один сборщик построек уровня: границы замены, сами блоки и рабочие места (карточка 031). */
    public interface Builder {
        /** Границы {x0, z0, x1, z1}, где воксели уровня заменяются постройками. */
        java.util.List<int[]> clear();
        /** Построить блоки постройки в мировых координатах на уровне развития lvl (0–3). */
        void build(BlockSink sink, int lvl);
    }

    private static Builder of(java.util.List<int[]> clear, java.util.function.ObjIntConsumer<BlockSink> build) {
        return new Builder() {
            @Override public java.util.List<int[]> clear() { return clear; }
            @Override public void build(BlockSink sink, int lvl) { build.accept(sink, lvl); }
        };
    }

    /** Сборщики уровня Kordon (l01_escape) в порядке применения; первый — южный блокпост. */
    private static final List<Builder> BUILDERS = java.util.List.of(
            of(KordonSouthPost.CLEAR, KordonSouthPost::build),
            of(KordonBunker.CLEAR, KordonBunker::build),
            of(KordonATP.CLEAR, KordonATP::build),
            of(KordonFarPost.CLEAR, KordonFarPost::build),
            of(KordonElevator.CLEAR, KordonElevator::build),
            of(KordonBridgeTunnel.CLEAR, KordonBridgeTunnel::build));

    /** Границы {x0, z0, x1, z1}, где воксели уровня заменяются постройками; пусто — уровень без замен. */
    public static List<int[]> clear(String levelId) {
        if (!"l01_escape".equals(levelId)) return List.of();
        List<int[]> out = new java.util.ArrayList<>();
        for (Builder b : BUILDERS) out.addAll(b.clear());
        return out;
    }

    public static boolean cleared(String levelId, int x, int z) {
        for (int[] c : clear(levelId)) if (x >= c[0] && x <= c[2] && z >= c[1] && z <= c[3]) return true;
        return false;
    }

    /** Рабочие места построек уровня (карточка 031): каждый сборщик выдаёт свои точки тем же проходом. */
    public static List<Point> points(String levelId, Ground g) { return points(levelId, g, POINT_LEVEL); }

    /** Рабочие места построек уровня на уровне развития lvl. */
    public static List<Point> points(String levelId, Ground g, int lvl) {
        java.util.ArrayList<Point> out = new java.util.ArrayList<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) {}
            @Override public int ground(int x, int z) { return g.at(x, z); }
            @Override public void mark(String kind, int x, int y, int z) { out.add(new Point(kind, x, y, z)); }
        };
        if ("l01_escape".equals(levelId)) for (Builder b : BUILDERS) b.build(s, lvl);
        return out;
    }

    // упаковка позиции: x и z по 21 бит (±1 млн), y — 21 бит; раньше z+2^21 вылезал в поле y и ломал координаты
    public static long key(int x, int y, int z) { return ((long) (x + (1 << 20)) << 42) | ((long) (y + 2048) << 21) | (z + (1 << 20)); }
    public static int kx(long k) { return (int) (k >>> 42) - (1 << 20); }
    public static int ky(long k) { return (int) ((k >>> 21) & 0x1fffff) - 2048; }
    public static int kz(long k) { return (int) (k & 0x1fffff) - (1 << 20); }

    /** Все блоки построек уровня (уровень развития lvl), с соединёнными решётками и стёклами. */
    public static Map<Long, String> build(String levelId, Ground g, int lvl) {
        Map<Long, String> m = new HashMap<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { m.put(key(x, y, z), st); }
            @Override public int ground(int x, int z) { return g.at(x, z); }
        };
        if ("l01_escape".equals(levelId)) for (Builder b : BUILDERS) b.build(s, lvl);
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
