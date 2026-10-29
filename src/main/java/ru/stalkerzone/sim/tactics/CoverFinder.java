package ru.stalkerzone.sim.tactics;

import java.util.Random;

/**
 * Поиск укрытия (v1.16) по сетке блоков, без Minecraft: игровой слой подставляет Grid по миру, тест — свою.
 * Укрытие — клетка, где можно стоять, и где прямая от глаз противника до головы бойца упирается в твёрдый
 * блок (полное укрытие) или хотя бы до груди (низкое: стоит присесть). Рядом должна быть клетка, откуда
 * противник виден: из неё боец «выглядывает» и стреляет.
 */
public final class CoverFinder {
    private CoverFinder() {}

    /** Твёрдый блок (стоит на нём, пуля не пролетает). */
    public interface Grid { boolean solid(int x, int y, int z); }

    /** Найденное укрытие: клетка, низкое ли оно, клетка для выглядывания (или та же для низкого) и оценка. */
    public record Spot(int x, int y, int z, boolean low, int peekX, int peekY, int peekZ, double score) {}

    /** Чего хотим от укрытия. sideBias: 0 — прямо, ±1 — обход слева/справа (для фланга). far — отход подальше. */
    public record Wish(double idealDist, double sideBias, boolean far, int radius) {}

    static boolean standable(Grid g, int x, int y, int z) {
        return !g.solid(x, y, z) && !g.solid(x, y + 1, z) && g.solid(x, y - 1, z);
    }

    /** Есть ли твёрдый блок на отрезке (без начальной и конечной клеток). Обход по клеткам Amanatides–Woo. */
    public static boolean blocked(Grid g, double ax, double ay, double az, double bx, double by, double bz) {
        int x = (int) Math.floor(ax), y = (int) Math.floor(ay), z = (int) Math.floor(az);
        int ex = (int) Math.floor(bx), ey = (int) Math.floor(by), ez = (int) Math.floor(bz);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx != 0 ? Math.abs(1 / dx) : Double.MAX_VALUE, tdy = sy != 0 ? Math.abs(1 / dy) : Double.MAX_VALUE,
                tdz = sz != 0 ? Math.abs(1 / dz) : Double.MAX_VALUE;
        double tmx = sx > 0 ? (x + 1 - ax) * tdx : sx < 0 ? (ax - x) * tdx : Double.MAX_VALUE;
        double tmy = sy > 0 ? (y + 1 - ay) * tdy : sy < 0 ? (ay - y) * tdy : Double.MAX_VALUE;
        double tmz = sz > 0 ? (z + 1 - az) * tdz : sz < 0 ? (az - z) * tdz : Double.MAX_VALUE;
        if (x == ex && y == ey && z == ez) return false;
        for (int i = 0; i < 512; i++) {
            if (tmx < tmy && tmx < tmz) { x += sx; tmx += tdx; }
            else if (tmy < tmz) { y += sy; tmy += tdy; }
            else { z += sz; tmz += tdz; }
            if (x == ex && y == ey && z == ez) return false;
            if (g.solid(x, y, z)) return true;
        }
        return false;
    }

    /** Видит ли глаз (tx,ty,tz) точку на высоте h над клеткой (x,y,z). */
    static boolean visible(Grid g, double tx, double ty, double tz, int x, int y, int z, double h) {
        return !blocked(g, tx, ty, tz, x + 0.5, y + h, z + 0.5);
    }

    /**
     * Лучшее укрытие вокруг (mx,my,mz) от угрозы с глазами в (tx,ty,tz), или null.
     * samples — сколько клеток проверить (бюджет), остальное отсекается случайно, чтобы не грузить сервер.
     */
    public static Spot find(Grid g, int mx, int my, int mz, double tx, double ty, double tz, Wish w, int samples, Random rnd) {
        Spot best = null;
        double fx = mx + 0.5 - tx, fz = mz + 0.5 - tz;
        double fl = Math.max(0.001, Math.hypot(fx, fz));
        // единичный вектор от угрозы к нам и перпендикуляр (для фланга)
        double ux = fx / fl, uz = fz / fl, px = -uz, pz = ux;
        int r = Math.max(3, w.radius());
        for (int i = 0; i < samples; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2, dist = 1.5 + rnd.nextDouble() * (r - 1.5);
            int cx = mx + (int) Math.round(Math.cos(ang) * dist), cz = mz + (int) Math.round(Math.sin(ang) * dist);
            int cy = Integer.MIN_VALUE;
            for (int dy = 2; dy >= -3; dy--) if (standable(g, cx, my + dy, cz)) { cy = my + dy; break; }
            if (cy == Integer.MIN_VALUE) continue;
            double dt = Math.hypot(cx + 0.5 - tx, cz + 0.5 - tz);
            if (dt < 4) continue;
            boolean headHidden = !visible(g, tx, ty, tz, cx, cy, cz, 1.62);
            boolean chestHidden = !visible(g, tx, ty, tz, cx, cy, cz, 0.9);
            if (!chestHidden) continue;               // ни полного, ни низкого
            boolean low = !headHidden;
            // откуда выглядывать: у низкого укрытия — встать на месте, у полного — шаг в сторону
            int kx = cx, ky = cy, kz = cz;
            if (!low) {
                boolean found = false;
                int[][] side = {{(int) Math.round(px), (int) Math.round(pz)}, {-(int) Math.round(px), -(int) Math.round(pz)},
                        {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] s : side) {
                    int nx = cx + s[0], nz = cz + s[1];
                    if (standable(g, nx, cy, nz) && visible(g, tx, ty, tz, nx, cy, nz, 1.62)) { kx = nx; kz = nz; found = true; break; }
                }
                if (!found) continue;                 // глухая стена: оттуда не постреляешь
            } else if (!visible(g, tx, ty, tz, cx, cy, cz, 1.62)) continue;
            double score = low ? 6 : 10;
            score -= Math.hypot(cx - mx, cz - mz) * 0.35;                  // далеко бежать под огнём плохо
            score -= Math.abs(dt - w.idealDist()) * (w.far() ? 0.05 : 0.22);
            if (w.far()) score += dt * 0.25;                               // в панике — подальше от врага
            if (w.sideBias() != 0) {
                double side = ((cx + 0.5 - tx) * px + (cz + 0.5 - tz) * pz) / Math.max(1, dt);
                score += side * w.sideBias() * 6;                          // обходить с фланга
            }
            if (best == null || score > best.score()) best = new Spot(cx, cy, cz, low, kx, ky, kz, score);
        }
        return best;
    }
}
