import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonBunker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Бункер Сидоровича (П-02б): от тропы перед входом можно дойти по лестнице до стойки и (через калитку) до места
 * торговца; торговец стоит в точке узла (−35, −8) на полу, под холмом есть грунт над потолком, рабочие места на месте.
 * Проверяется на ровной площадке и на неровной земле (до площадок), на всех уровнях развития.
 */
public class BunkerTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    record P(int x, int y, int z) {}
    record Mk(String kind, int x, int y, int z) {}

    interface G { int at(int x, int z); }

    public static void main(String[] a) {
        run("ровная площадка 84", (x, z) -> 84, 1);
        run("неровная земля 83..87", (x, z) -> 83 + Math.floorMod(x * 7 + z * 13, 5), 1);
        for (int lvl = 0; lvl <= 3; lvl++) run("уровень " + lvl, (x, z) -> 84, lvl);
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
        KordonBunker.build(s, lvl);
        int[] c = KordonBunker.CLEAR.get(0);
        int out = 0;
        for (P p : m.keySet()) if (p.x < c[0] || p.x > c[2] || p.z < c[1] || p.z > c[3]) out++;
        ck(out == 0, name + ": все блоки в границах замены (вне: " + out + ")");

        Mk trader = null, duty = null;
        int shelters = 0;
        for (Mk k : marks) {
            if (k.kind.equals("trader")) trader = k;
            if (k.kind.equals("duty")) duty = k;
            if (k.kind.equals("shelter")) shelters++;
        }
        ck(trader != null && trader.x == -35 && trader.z == -8, name + ": торговец в точке узла (−35, −8)");
        ck(duty != null, name + ": место курьера/охраны (duty)");
        ck(shelters >= 4, name + ": укрытий от выброса " + shelters);
        if (trader == null) return;
        for (Mk k : marks) {
            boolean ok = !solid(at(m, g, k.x, k.y, k.z)) && !solid(at(m, g, k.x, k.y + 1, k.z)) && solid(at(m, g, k.x, k.y - 1, k.z));
            ck(ok, name + ": точка " + k.kind + " (" + k.x + ", " + k.y + ", " + k.z + ") на полу, не в стене");
        }
        // над комнатой — грунт (бункер под землёй), холм над лестницей не проломлен
        int roomTop = trader.y + 3;
        boolean covered = true;
        for (int x = -39; x <= -32; x++) for (int z = -12; z <= -5; z++)
            covered &= solid(at(m, g, x, roomTop, z)) && solid(at(m, g, x, roomTop + 1, z));
        ck(covered, name + ": потолок комнаты под землёй");

        // проход: от тропы перед входом до посетительской половины и до торговца
        int gy = trader.y + 4;   // F + 1 + 4 = g
        P start = new P(-23, gy + 1, -2);
        Set<P> seen = reach(m, g, start);
        boolean visitor = seen.contains(new P(-35, trader.y, -6));
        boolean behind = seen.contains(new P(trader.x, trader.y, trader.z));
        if (lvl >= 1) {
            ck(visitor, name + ": от входа дойти до стойки (" + seen.size() + " клеток)");
            ck(behind, name + ": торговец доходит до своего места через калитку");
        } else {
            ck(visitor, name + ": в заброшенный бункер можно войти (" + seen.size() + " клеток)");
        }
        ck(m.getOrDefault(new P(-28, gy + 1, -2), "").contains("door") || lvl == 0, name + ": наружная дверь на уровне земли");
    }

    static String at(Map<P, String> m, G g, int x, int y, int z) {
        String s = m.get(new P(x, y, z));
        if (s != null) return s;
        return y <= g.at(x, z) ? "minecraft:dirt" : "minecraft:air";
    }

    static boolean solid(String s) {
        return !(s.equals("minecraft:air") || s.contains("door") || s.contains("lantern") || s.contains("fence_gate")
                || s.equals("minecraft:grass"));
    }

    static boolean stand(Map<P, String> m, G g, P p) {
        return !solid(at(m, g, p.x, p.y, p.z)) && !solid(at(m, g, p.x, p.y + 1, p.z)) && solid(at(m, g, p.x, p.y - 1, p.z));
    }

    /** Обход по клеткам «где можно стоять»: шаг вбок, вниз на 1, вверх на 1 — только на ступень или полублок. */
    static Set<P> reach(Map<P, String> m, G g, P start) {
        Set<P> seen = new HashSet<>();
        ArrayDeque<P> q = new ArrayDeque<>();
        if (stand(m, g, start)) { seen.add(start); q.add(start); }
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            P p = q.poll();
            if (seen.size() > 20000) break;
            for (int[] e : d) for (int dy = -1; dy <= 1; dy++) {
                P n = new P(p.x + e[0], p.y + dy, p.z + e[1]);
                if (seen.contains(n) || !stand(m, g, n)) continue;
                if (dy == 1) {
                    String sup = at(m, g, n.x, n.y - 1, n.z);
                    if (!(sup.contains("stairs") || sup.contains("type=bottom"))) continue;
                    if (solid(at(m, g, p.x, p.y + 2, p.z))) continue;
                }
                if (dy == -1 && solid(at(m, g, n.x, n.y + 2, n.z))) continue;
                seen.add(n);
                q.add(n);
            }
        }
        return seen;
    }
}
