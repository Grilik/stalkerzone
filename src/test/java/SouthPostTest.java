import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonSouthPost;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * П-20: северный фронт Южного блокпоста — огневые позиции вдоль границы с Зоной, пулемётная точка с уровня 2,
 * запретная полоса (kill_line). Земля плоская (y=70), блок под ногами — y=70, ноги — y=71.
 */
public class SouthPostTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    record M(String kind, int x, int y, int z) {}

    static final class Sink implements BlockSink {
        final Map<Long, String> b = new HashMap<>();
        final List<M> marks = new ArrayList<>();
        public void set(int x, int y, int z, String s) { b.put(key(x, y, z), s); }
        public int ground(int x, int z) { return 70; }
        public void mark(String k, int x, int y, int z) { marks.add(new M(k, x, y, z)); }
    }

    static long key(int x, int y, int z) { return ((long) (x + 4096) << 40) | ((long) (y + 64) << 24) | (z + 8192); }

    static boolean air(Sink s, int x, int y, int z) {
        String v = s.b.get(key(x, y, z));
        return v == null || v.contains("air") && !v.contains("chair") || v.contains("iron_bars") && false;
    }

    static boolean solidBelow(Sink s, int x, int y, int z) {
        if (y - 1 == 70) { String v = s.b.get(key(x, 70, z)); return v == null || !v.contains("air"); }
        String v = s.b.get(key(x, y - 1, z));
        return v != null && !v.contains("air") && !v.contains("ladder") && !v.contains("bars") && !v.contains("lantern");
    }

    /** Достижимость по земле (шаг вверх/вниз ≤ 1) от (sx,71,sz). */
    static Set<Long> reach(Sink s, int sx, int sz) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] {sx, 71, sz}); seen.add(key(sx, 71, sz));
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] dd : d) for (int dy = -1; dy <= 1; dy++) {
                int x = c[0] + dd[0], y = c[1] + dy, z = c[2] + dd[1];
                if (x < 28 || x > 107 || z < 195 || z > 262 || y < 71 || y > 74) continue;
                if (!air(s, x, y, z) || !air(s, x, y + 1, z) || !solidBelow(s, x, y, z)) continue;
                if (seen.add(key(x, y, z))) q.add(new int[] {x, y, z});
            }
        }
        return seen;
    }

    static List<M> of(Sink s, String k) { List<M> r = new ArrayList<>(); for (M m : s.marks) if (m.kind.equals(k)) r.add(m); return r; }

    public static void main(String[] a) {
        for (int lvl = 0; lvl <= 3; lvl++) {
            String L = "ур." + lvl + ": ";
            Sink s = new Sink();
            KordonSouthPost.build(s, lvl);
            int mg = of(s, "mg").size();
            ck(lvl >= 2 ? mg == 1 : mg == 0, L + "пулемётов: " + mg + (lvl >= 2 ? " (ровно 1)" : " (нет)"));
            ck(of(s, "kill_line").size() >= 2, L + "kill_line ≥ 2 → " + of(s, "kill_line").size());
            int north = 0;
            for (M g : of(s, "guard")) if (g.z() == 213) north++;
            ck(north >= 3, L + "guard у северных позиций ≥ 3 → " + north);
            for (M k : of(s, "kill_line")) ck(k.z() < 211, L + "kill_line (" + k.x() + "," + k.z() + ") со стороны Зоны");
            for (M m : s.marks) {
                if (m.kind().equals("rest")) continue;   // скамейки старых меток — не П-20
                if (m.y() > 71) continue;                   // площадка вышки
                String bl = s.b.get(key(m.x(), m.y(), m.z()));
                ck(bl == null || bl.contains("air"), L + "метка " + m.kind() + " (" + m.x() + "," + m.z() + ") на свободной клетке");
            }
            // проходимость: от точки патруля (84,214) до позиций у забора (не вышки)
            Set<Long> r = reach(s, 84, 214);
            for (M m : s.marks) {
                if (!(m.kind().equals("guard") || m.kind().equals("mg")) || m.z() != 213) continue;
                ck(r.contains(key(m.x(), m.y(), m.z())), L + m.kind() + " (" + m.x() + "," + m.z() + ") достижим пешком");
            }
        }
        // все блоки внутри CLEAR
        Sink s = new Sink(); KordonSouthPost.build(s, 3);
        int[] c = KordonSouthPost.CLEAR.get(0);
        boolean in = true;
        for (long k : s.b.keySet()) { int x = (int) (k >> 40) - 4096, z = (int) (k & 0xFFFFFF) - 8192; if (x < c[0] || x > c[2] || z < c[1] || z > c[3]) in = false; }
        ck(in, "все блоки в CLEAR");
        System.out.println(f == 0 ? "ВСЕ ПРОШЛИ" : "ПРОВАЛЕНО: " + f);
        if (f > 0) System.exit(1);
    }
}
