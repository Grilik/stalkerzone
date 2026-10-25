package ru.stalkerzone.zonemap;

/** Геометрия и детерминированный шум. */
public final class Geo {
    private Geo() {}

    /** Расстояние от точки до отрезка и параметр t проекции. Возвращает {dist, t}. */
    public static double[] segment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
        double cx = ax + dx * t, cz = az + dz * t;
        return new double[] {Math.hypot(px - cx, pz - cz), t};
    }

    public static double hash(long x, long z, long salt) {
        long h = x * 374761393L + z * 668265263L + salt * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xffffff) / (double) 0xffffff;
    }

    /** Гладкий шум 0..1. */
    public static double noise(double x, double z, long salt) {
        long x0 = (long) Math.floor(x), z0 = (long) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double a = hash(x0, z0, salt), b = hash(x0 + 1, z0, salt), c = hash(x0, z0 + 1, salt), d = hash(x0 + 1, z0 + 1, salt);
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double top = a + (b - a) * ux, bot = c + (d - c) * ux;
        return top + (bot - top) * uz;
    }

    /** Фрактальный шум −1..1 (несколько октав). */
    public static double fbm(double x, double z, double scale, int oct, long salt) {
        double sum = 0, amp = 1, norm = 0, f = 1 / scale;
        for (int i = 0; i < oct; i++) {
            sum += amp * (noise(x * f, z * f, salt + i * 101) * 2 - 1);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    public static boolean inPolygon(double x, double z, double[] poly) {
        boolean in = false;
        int n = poly.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly[2 * i], zi = poly[2 * i + 1], xj = poly[2 * j], zj = poly[2 * j + 1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    public static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
