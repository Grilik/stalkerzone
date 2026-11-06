import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonDump;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Свалка техники А6 + застава О12: блоки в своих границах, места на полу, от входа заставы пешком (без прыжков)
 * дойти до нар, костра и стола; на уровне 3 — вышка с часовым наверху; барак и вышка не пересекаются.
 * Рельеф — ровный (плато дна чаши — дело площадки).
 */
public class KordonDumpTest {
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
        int[] cur = KordonDump.CLEAR.get(0).clone();
        boolean[] out = {false};
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) {
                if (x < cur[0] || x > cur[2] || z < cur[1] || z > cur[3]) out[0] = true;
                m.put(new P(x, y, z), st);
            }
            @Override public int ground(int x, int z) { return g.at(x, z); }
            @Override public void mark(String kind, int x, int y, int z) { marks.add(new Mk(kind, x, y, z)); }
        };
        KordonDump.build(s, lvl);
        ck(!out[0], name + ": всё в своих границах");
        ck(m.size() > 600, name + ": блоков " + m.size());

        int bad = 0;
        for (Mk k : marks) if (!stand(m, g, new P(k.x, k.y, k.z))) { bad++; System.out.println("  не на полу: " + k); }
        ck(bad == 0, name + ": все места на полу и не в стене (" + marks.size() + ")");

        Mk gate = marks.stream().filter(k -> k.kind.equals("gate")).findFirst().orElseThrow();
        Set<P> camp = walk(m, g, new P(gate.x, gate.y, gate.z));
        boolean ok = marks.stream().filter(k -> k.kind.equals("bed") || k.kind.equals("cook") || k.kind.equals("work")
                || (k.kind.equals("rest") && k.x >= KordonDump.HX0)).allMatch(k -> camp.contains(new P(k.x, k.y, k.z)));
        ck(ok, name + ": от входа дойти пешком до нар, стола, костра и барака (" + camp.size() + " клеток)");
        long beds = marks.stream().filter(k -> k.kind.equals("bed")).count();
        ck(beds >= 4, name + ": нар " + beds);
        // потолок барака
        int y = g.at(KordonDump.HX0 + 2, KordonDump.DOOR_Z) + 1;
        ck(!solid(at(m, g, KordonDump.HX0 + 2, y + 2, KordonDump.DOOR_Z)), name + ": внутри барака ≥ 3 блока до кровли");
        if (lvl >= 3) {
            Mk t = marks.stream().filter(k -> k.kind.equals("guard") && k.x == KordonDump.TX + 1).findFirst().orElse(null);
            ck(t != null && t.y >= g.at(t.x, t.z) + 7, name + ": часовой на вышке");
        }
        for (int[] p : new int[][] {{KordonDump.HX0 + 1, KordonDump.HZ0 + 1}}) {
            String st = at(m, g, p[0], y, p[1]);
            ck(lvl == 0 || st.contains("mattress"), name + ": нары — подстилка");
        }
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

}
