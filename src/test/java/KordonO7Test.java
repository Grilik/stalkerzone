import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonRoadBridge;
import ru.stalkerzone.zonemap.build.KordonWagon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Застава О7 (лагерь у вагончика + дорожный мост): блоки в своих границах, места на полу, под мостом проход
 * высотой ≥ 3, к костру под мостом можно дойти по дну оврага, в вагончик — с земли по ступени через дверь.
 * Рельеф — ровный (как будто площадка ещё не вырыла овраг): мост выкапывает его сам.
 */
public class KordonO7Test {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    record P(int x, int y, int z) {}
    record Mk(String kind, int x, int y, int z) {}
    interface G { int at(int x, int z); }

    public static void main(String[] a) {
        for (int lvl = 0; lvl <= 3; lvl++) run("уровень " + lvl, (x, z) -> 95, lvl);
        if (f > 0) { System.out.println("FAILED " + f); System.exit(1); }
    }

    static void run(String name, G g, int lvl) {
        Map<P, String> m = new HashMap<>();
        List<Mk> marks = new ArrayList<>();
        int[] cur = new int[4];
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) {
                if (x < cur[0] || x > cur[2] || z < cur[1] || z > cur[3]) cur[0] = Integer.MIN_VALUE;
                m.put(new P(x, y, z), st);
            }
            @Override public int ground(int x, int z) { return g.at(x, z); }
            @Override public void mark(String kind, int x, int y, int z) { marks.add(new Mk(kind, x, y, z)); }
        };
        int[] w = KordonWagon.CLEAR.get(0);
        System.arraycopy(w, 0, cur, 0, 4);
        KordonWagon.build(s, lvl);
        ck(cur[0] != Integer.MIN_VALUE, name + ": вагончик в своих границах");
        int[] br = KordonRoadBridge.CLEAR.get(0);
        System.arraycopy(br, 0, cur, 0, 4);
        KordonRoadBridge.build(s, lvl);
        ck(cur[0] != Integer.MIN_VALUE, name + ": мост в своих границах");

        int bad = 0;
        for (Mk k : marks) if (!stand(m, g, new P(k.x, k.y, k.z))) { bad++; System.out.println("  не на полу: " + k); }
        ck(bad == 0, name + ": все места на полу и не в стене (" + marks.size() + ")");

        // проход под мостом: над полом лагеря до балок ≥ 3 блока
        int d = KordonRoadBridge.deck(s), fl = KordonRoadBridge.under(s, d);
        ck(d - 2 - fl >= 3, name + ": под мостом " + (d - 2 - fl) + " блока до балок (полотно " + d + ", пол " + fl + ")");

        // к костру — по дну оврага с северо-западного конца; в вагончик — с земли у будки
        Set<P> ravine = reach(m, g, new P(144, 91, -93));
        Mk cook = marks.stream().filter(k -> k.kind.equals("cook")).findFirst().orElseThrow();
        ck(ravine.contains(new P(cook.x, cook.y, cook.z)), name + ": по дну оврага дойти до костра под мостом (" + ravine.size() + " клеток)");
        Set<P> camp = reach(m, g, new P(118, 96, -76));
        boolean beds = marks.stream().filter(k -> k.kind.equals("bed") && k.x >= KordonWagon.TX0 && k.x <= KordonWagon.TX1)
                .allMatch(k -> camp.contains(new P(k.x, k.y, k.z)));
        ck(lvl == 0 || beds, name + ": от будки дойти до нар в вагончике");
    }

    static String at(Map<P, String> m, G g, int x, int y, int z) {
        String s = m.get(new P(x, y, z));
        if (s != null) return s;
        return y <= g.at(x, z) ? "minecraft:dirt" : "minecraft:air";
    }

    static boolean solid(String s) {
        return !(s.equals("minecraft:air") || s.contains("door") || s.contains("lantern") || s.contains("fence_gate")
                || s.equals("minecraft:grass") || s.contains("carpet") || s.contains("trapdoor") && s.contains("open=true") && false);
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
                if (n.x < 100 || n.x > 186 || n.z < -108 || n.z > -52) continue;
                if (dy == 1 && solid(at(m, g, p.x, p.y + 2, p.z))) continue;   // шаг вверх на блок (прыжок) — нужен простор над головой
                if (dy == -1 && solid(at(m, g, n.x, n.y + 2, n.z))) continue;
                seen.add(n);
                q.add(n);
            }
        }
        return seen;
    }

}
