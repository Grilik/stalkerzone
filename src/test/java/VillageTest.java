import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonVillage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Деревня новичков (П-03): все блоки в своих границах, от костра можно дойти до каждого рабочего места (в избах и
 * в подвалах), места на полу и не в стене, место появления игрока (5, 2) свободно, в избах и подвалах потолок не
 * ниже 3 блоков над полом (кроме дверных проёмов). На ровном плато, на неровной земле и на всех уровнях развития.
 */
public class VillageTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    record P(int x, int y, int z) {}
    record Mk(String kind, int x, int y, int z) {}
    interface G { int at(int x, int z); }

    public static void main(String[] a) {
        for (int lvl = 0; lvl <= 3; lvl++) run("плато 79, уровень " + lvl, (x, z) -> 79, lvl);
        run("неровная земля", (x, z) -> 79 + Math.floorMod(x * 7 + z * 13, 3), -1);   // только границы и места на полу
        if (f > 0) { System.out.println("FAILED " + f); System.exit(1); }
    }

    static void run(String name, G g, int lvl) {
        Map<P, String> m = new HashMap<>();
        List<Mk> marks = new ArrayList<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { m.put(new P(x, y, z), st); }
            @Override public int ground(int x, int z) { return g.at(x, z); }
            @Override public void mark(String kind, int x, int y, int z) { marks.add(new Mk(kind, x, y, z)); }
        };
        boolean full = lvl >= 0;
        KordonVillage.build(s, Math.max(1, lvl));
        int[] c = KordonVillage.CLEAR.get(0);
        int out = 0;
        for (P p : m.keySet()) if (p.x < c[0] || p.x > c[2] || p.z < c[1] || p.z > c[3]) out++;
        ck(out == 0, name + ": все блоки в границах деревни (вне: " + out + ")");

        Map<String, Integer> kinds = new HashMap<>();
        for (Mk k : marks) kinds.merge(k.kind, 1, Integer::sum);
        ck(kinds.getOrDefault("bed", 0) >= 14, name + ": коек " + kinds.get("bed"));
        ck(kinds.getOrDefault("shelter", 0) >= 6, name + ": укрытий в подвалах " + kinds.get("shelter"));
        ck(kinds.getOrDefault("guard", 0) >= 4 && kinds.getOrDefault("patrol", 0) >= 6 && kinds.getOrDefault("cook", 0) == 1
                && kinds.getOrDefault("duty", 0) == 1 && kinds.getOrDefault("work", 0) >= 4, name + ": места " + kinds);

        int bad = 0;
        for (Mk k : marks) if (!stand(m, g, new P(k.x, k.y, k.z))) { bad++; System.out.println("  не на полу: " + k); }
        ck(bad == 0, name + ": все места на полу и не в стене");

        if (!full) return;
        // обход от костра (по плато, через двери, вниз в подвалы)
        int fx = KordonVillage.FIRE_X, fz = KordonVillage.FIRE_Z - 2;
        Set<P> seen = reach(m, g, new P(fx, g.at(fx, fz) + 1, fz));
        int lost = 0;
        for (Mk k : marks) if (!seen.contains(new P(k.x, k.y, k.z))) { lost++; System.out.println("  не дойти: " + k); }
        ck(lost == 0, name + ": от костра можно дойти до каждого места (" + seen.size() + " клеток)");

        // место появления игрока свободно
        boolean free = true;
        for (int x = 4; x <= 6; x++) for (int z = 1; z <= 3; z++)
            free &= !solid(at(m, g, x, g.at(x, z) + 1, z)) && !solid(at(m, g, x, g.at(x, z) + 2, z));
        ck(free, name + ": место появления игрока (5, 2) свободно");

        // высота: под крышей и в подвалах над каждой клеткой, где можно стоять, ≥ 3 блока воздуха (кроме дверей)
        int low = 0;
        for (P p : seen) {
            String under = at(m, g, p.x, p.y - 1, p.z);
            boolean inside = p.y < g.at(p.x, p.z) || solid(at(m, g, p.x, p.y + 4, p.z)) || solid(at(m, g, p.x, p.y + 3, p.z));
            if (!inside || at(m, g, p.x, p.y, p.z).contains("door") || at(m, g, p.x, p.y + 1, p.z).contains("door")) continue;
            boolean doorway = solid(at(m, g, p.x - 1, p.y, p.z)) && solid(at(m, g, p.x + 1, p.y, p.z))
                    || solid(at(m, g, p.x, p.y, p.z - 1)) && solid(at(m, g, p.x, p.y, p.z + 1));   // проём в стене
            if (doorway) continue;
            // считаем только клетки на полу изб и подвалов и на ступенях подвалов (не на мебели)
            if (!(under.contains("zm_planks_floor") || under.contains("zm_planks_gray_stairs"))) continue;
            if (solid(at(m, g, p.x, p.y + 2, p.z)) && !under.contains("stairs")) { low++; if (low < 4) System.out.println("  низко: " + p); }
        }
        ck(low == 0, name + ": потолок не ниже 3 блоков над полом");

        // П-11: в подвал с плато и обратно — пешком, без прыжков (подъём только по ступеням и полублокам)
        for (int[] cl : KordonVillage.CELLARS) {
            int ax = cl[0] - cl[2], az = cl[1];                    // клетка плато перед входом
            P top = new P(ax, g.at(ax, az) + 1, az);
            Set<P> down = walk(m, g, top);
            Mk sh = null;
            for (Mk k : marks) if (k.kind.equals("shelter") && Math.abs(k.x - cl[0]) < 16 && Math.abs(k.z - cl[1]) < 4) { sh = k; break; }
            boolean in = sh != null && down.contains(new P(sh.x, sh.y, sh.z));
            boolean back = sh != null && walk(m, g, new P(sh.x, sh.y, sh.z)).contains(top);
            ck(in && back, name + ": подвал (" + cl[0] + ", " + cl[1] + "): с плато вниз до укрытия и обратно без прыжков");
        }
    }

    static String at(Map<P, String> m, G g, int x, int y, int z) {
        String s = m.get(new P(x, y, z));
        if (s != null) return s;
        return y <= g.at(x, z) ? "minecraft:dirt" : "minecraft:air";
    }

    static boolean solid(String s) {
        return !(s.equals("minecraft:air") || s.contains("door") || s.contains("lantern") || s.contains("fence_gate")
                || s.equals("minecraft:grass") || s.contains("trapdoor") && s.contains("open=true") && false);
    }

    static boolean stand(Map<P, String> m, G g, P p) {
        return !solid(at(m, g, p.x, p.y, p.z)) && !solid(at(m, g, p.x, p.y + 1, p.z)) && solid(at(m, g, p.x, p.y - 1, p.z));
    }

    /** Обход пешком: шаг вверх только на ступень или нижний полублок (без прыжка), вниз — на блок. */
    static Set<P> walk(Map<P, String> m, G g, P start) {
        Set<P> seen = new HashSet<>();
        ArrayDeque<P> q = new ArrayDeque<>();
        if (stand(m, g, start)) { seen.add(start); q.add(start); }
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            P p = q.poll();
            if (seen.size() > 50000) break;
            for (int[] e : d) for (int dy = -1; dy <= 1; dy++) {
                P n = new P(p.x + e[0], p.y + dy, p.z + e[1]);
                if (seen.contains(n) || !stand(m, g, n)) continue;
                if (Math.abs(n.x - start.x) > 24 || Math.abs(n.z - start.z) > 24) continue;
                if (dy == 1) {
                    String sup = at(m, g, n.x, n.y - 1, n.z);
                    // вверх — только на ступень или нижний полублок (полшага); на целый блок — это прыжок
                    if (!(sup.contains("stairs") || sup.contains("type=bottom"))) continue;
                }
                if (dy == -1 && solid(at(m, g, n.x, n.y + 2, n.z))) continue;
                seen.add(n);
                q.add(n);
            }
        }
        return seen;
    }

    static Set<P> reach(Map<P, String> m, G g, P start) {
        Set<P> seen = new HashSet<>();
        ArrayDeque<P> q = new ArrayDeque<>();
        if (stand(m, g, start)) { seen.add(start); q.add(start); }
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            P p = q.poll();
            if (seen.size() > 200000) break;
            for (int[] e : d) for (int dy = -1; dy <= 1; dy++) {
                P n = new P(p.x + e[0], p.y + dy, p.z + e[1]);
                if (seen.contains(n) || !stand(m, g, n)) continue;
                if (n.x < -45 || n.x > 60 || n.z < -28 || n.z > 38) continue;
                if (dy == 1 && solid(at(m, g, p.x, p.y + 2, p.z))) continue;   // шаг вверх на блок (прыжок) — нужен простор над головой
                if (dy == -1 && solid(at(m, g, n.x, n.y + 2, n.z))) continue;
                seen.add(n);
                q.add(n);
            }
        }
        return seen;
    }
}
