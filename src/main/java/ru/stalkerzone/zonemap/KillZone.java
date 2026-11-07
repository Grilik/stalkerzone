package ru.stalkerzone.zonemap;

import java.util.ArrayList;
import java.util.List;

/**
 * Запретная полоса перед периметром блокпоста (карточка 055): военные стреляют на поражение по тому,
 * кто в полосе, и не трогают тех, кто за ней. Полоса задаётся метками «kill_line» сборщика — по 4 угла
 * на прямоугольник. Чистая Java.
 */
public final class KillZone {
    private KillZone() {}

    /** Прямоугольники {x0, z0, x1, z1} из точек {x, z}: каждые 4 подряд — углы одного прямоугольника. */
    public static List<int[]> fromPoints(List<int[]> points) {
        List<int[]> out = new ArrayList<>();
        if (points == null) return out;
        for (int i = 0; i + 3 < points.size(); i += 4) {
            int[] a = points.get(i), b = points.get(i + 1), c = points.get(i + 2), d = points.get(i + 3);
            if (a == null || b == null || c == null || d == null || a.length < 2 || b.length < 2 || c.length < 2 || d.length < 2)
                continue;
            int x0 = Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0]));
            int z0 = Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1]));
            int x1 = Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
            int z1 = Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));
            out.add(new int[]{x0, z0, x1, z1});
        }
        return out;
    }

    /** Точка (x, z) в одной из полос (края включительно). */
    public static boolean inside(List<int[]> zones, double x, double z) {
        if (zones == null) return false;
        for (int[] q : zones) {
            if (q != null && q.length >= 4 && x >= q[0] && x <= q[2] && z >= q[1] && z <= q[3]) return true;
        }
        return false;
    }

    /** Стреляют ли военные гарнизона на поражение по игроку или одиночке в точке (x, z). */
    public static boolean militaryHostile(List<int[]> zones, double x, double z) {
        return inside(zones, x, z);
    }
}
