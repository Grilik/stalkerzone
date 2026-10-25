package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Граф дорог между локациями: время в пути в днях и опасность (шанс нападения мутантов за день).
 * Дорогу можно закрыть (проход к ЧАЭС закрыт, пока работает Выжигатель); после изменения вызвать build().
 */
public final class ZoneGraph {
    private static final int INF = 1_000_000;

    private final int n;
    private final int[][] days;
    private final double[][] danger;
    private final boolean[][] closed;
    private final int[][] dist;
    private final int[][] next;

    public ZoneGraph(int n) {
        this.n = n;
        days = new int[n][n];
        danger = new double[n][n];
        closed = new boolean[n][n];
        dist = new int[n][n];
        next = new int[n][n];
    }

    public void connect(int a, int b, int d, double dangerPerDay) {
        days[a][b] = days[b][a] = d;
        danger[a][b] = danger[b][a] = dangerPerDay;
    }

    public void setClosed(int a, int b, boolean value) {
        closed[a][b] = closed[b][a] = value;
    }

    public boolean isClosed(int a, int b) {
        return closed[a][b];
    }

    private boolean open(int a, int b) {
        return days[a][b] > 0 && !closed[a][b];
    }

    /** Вызвать после всех connect/setClosed. */
    public void build() {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) dist[i][j] = 0;
                else dist[i][j] = open(i, j) ? days[i][j] : INF;
                next[i][j] = open(i, j) ? j : -1;
            }
        }
        for (int k = 0; k < n; k++)
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    if (dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                        next[i][j] = next[i][k];
                    }
    }

    public int dist(int a, int b) {
        return dist[a][b];
    }

    public boolean reachable(int a, int b) {
        return dist[a][b] < 1000;
    }

    public int edgeDays(int a, int b) {
        return days[a][b];
    }

    public double danger(int a, int b) {
        return danger[a][b];
    }

    /** Маршрут включая начальную и конечную точки. Если пути нет, вернёт только начальную точку. */
    public int[] path(int a, int b) {
        if (dist[a][b] >= INF) return new int[] {a};
        List<Integer> p = new ArrayList<>();
        int cur = a;
        p.add(cur);
        while (cur != b) {
            cur = next[cur][b];
            p.add(cur);
        }
        return p.stream().mapToInt(Integer::intValue).toArray();
    }
}
