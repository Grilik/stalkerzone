package ru.stalkerzone.zonemap;

import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.NodeType;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.sim.ZoneScenario;
import ru.stalkerzone.world.ZoneBounds;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.14. Сеть дорог по графу симуляции, без зависимостей от Minecraft (проверяется тестом). Каждое ребро графа —
 * два участка вдоль осей (Г-образно), так столбы, провода и трубы ложатся ровно. Отмечено, по каким участкам идёт
 * ЛЭП (кратчайший путь ЧАЭС → Кордон) и газопровод (рёбра Агропрома, кроме городских).
 */
public final class RoadNet {
    /** Участок вдоль оси: координата along от from до to при постоянной поперечной fixed. */
    public record Leg(boolean alongX, int fixed, int from, int to, boolean asphalt, boolean lep, boolean gas, int a, int b) {
        public int lo() { return Math.min(from, to); }
        public int hi() { return Math.max(from, to); }
        public int length() { return hi() - lo(); }
    }

    public static final int HALF = 2;     // полуширина полотна (5 блоков)
    public static final int CAMP_R = 36;  // в лагерь дорога не заходит

    public final List<Leg> legs;
    public final int[][] centers;
    public final List<Node> nodes;

    private static RoadNet instance;

    public static synchronized RoadNet get() {
        if (instance == null) instance = new RoadNet(ZoneScenario.create(0));
        return instance;
    }

    public RoadNet(Simulation sim) {
        nodes = sim.nodes;
        int S = ZoneBounds.SCALE;
        centers = new int[nodes.size()][];
        for (int i = 0; i < nodes.size(); i++) centers[i] = new int[] {nodes.get(i).x * S, nodes.get(i).z * S};
        int chaes = find("ЧАЭС"), kordon = find("Кордон"), agro = find("Агропром");
        List<Long> lepEdges = new ArrayList<>();
        if (chaes >= 0 && kordon >= 0) {
            // свой поиск: в графе симуляции путь на север в начале закрыт Выжигателем, а провода-то висят
            int[] p = shortest(sim, chaes, kordon);
            for (int i = 0; i + 1 < p.length; i++) lepEdges.add(key(p[i], p[i + 1]));
        }
        List<Leg> out = new ArrayList<>();
        for (int a = 0; a < nodes.size(); a++) for (int b = a + 1; b < nodes.size(); b++) {
            if (sim.graph.edgeDays(a, b) <= 0) continue;
            Node na = nodes.get(a), nb = nodes.get(b);
            boolean ca = na.type == NodeType.CITY, cb = nb.type == NodeType.CITY;
            boolean asphalt = ca == cb;   // в город и из города — разбитая грунтовка, остальное — старый асфальт
            boolean lep = lepEdges.contains(key(a, b));
            boolean gas = agro >= 0 && (a == agro || b == agro) && !ca && !cb;
            int ax = centers[a][0], az = centers[a][1], bx = centers[b][0], bz = centers[b][1];
            if (((a * 31 + b * 17) & 1) == 0) {
                out.add(new Leg(true, az, ax, bx, asphalt, lep, gas, a, b));
                out.add(new Leg(false, bx, az, bz, asphalt, lep, gas, a, b));
            } else {
                out.add(new Leg(false, ax, az, bz, asphalt, lep, gas, a, b));
                out.add(new Leg(true, bz, ax, bx, asphalt, lep, gas, a, b));
            }
        }
        out.removeIf(l -> l.length() == 0);
        legs = List.copyOf(out);
    }

    /** Дейкстра по длине рёбер (дни пути) без учёта закрытых проходов. */
    static int[] shortest(Simulation sim, int from, int to) {
        int n = sim.nodes.size();
        int[] d = new int[n], prev = new int[n];
        boolean[] done = new boolean[n];
        java.util.Arrays.fill(d, Integer.MAX_VALUE);
        java.util.Arrays.fill(prev, -1);
        d[from] = 0;
        for (int it = 0; it < n; it++) {
            int u = -1;
            for (int i = 0; i < n; i++) if (!done[i] && d[i] != Integer.MAX_VALUE && (u < 0 || d[i] < d[u])) u = i;
            if (u < 0) break;
            done[u] = true;
            for (int v = 0; v < n; v++) {
                int w = sim.graph.edgeDays(u, v);
                if (w > 0 && d[u] + w < d[v]) { d[v] = d[u] + w; prev[v] = u; }
            }
        }
        if (d[to] == Integer.MAX_VALUE) return new int[] {from};
        List<Integer> path = new ArrayList<>();
        for (int c = to; c >= 0; c = prev[c]) path.add(0, c);
        return path.stream().mapToInt(Integer::intValue).toArray();
    }

    private static long key(int a, int b) { return Math.min(a, b) * 1000L + Math.max(a, b); }

    public int find(String name) {
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).name.equals(name)) return i;
        return -1;
    }

    public boolean nearCenter(int x, int z, int r) {
        for (int[] c : centers) if (Math.abs(x - c[0]) < r && Math.abs(z - c[1]) < r) return true;
        return false;
    }

    /** Расстояние от точки до оси ближайшей дороги (поперёк полотна), MAX_VALUE — если дороги рядом нет. */
    public int roadDist(int x, int z) {
        int best = Integer.MAX_VALUE;
        for (Leg l : legs) {
            int along = l.alongX() ? x : z, across = l.alongX() ? z : x;
            if (along < l.lo() || along > l.hi()) continue;
            best = Math.min(best, Math.abs(across - l.fixed()));
        }
        return best;
    }
}
