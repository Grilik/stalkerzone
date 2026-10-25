package ru.stalkerzone.world;

import ru.stalkerzone.sim.Node;

import java.util.List;

/**
 * Границы Зоны в блоках. Чистая Java, без Minecraft, поэтому проверяется тестами.
 * Играбельный прямоугольник [minX..maxX] x [minZ..maxZ] (включительно). По его периметру стоит забор,
 * снаружи в {@link #BARRIER_THICKNESS} слоя идёт невидимая стена из барьеров на всю высоту мира.
 */
public final class ZoneBounds {
    /** Блоков на одну условную единицу карты симуляции (север = минус Z). */
    public static final int SCALE = 10;
    /** Запас от крайних локаций до забора, блоков. */
    public static final int MARGIN = 300;
    public static final int BARRIER_THICKNESS = 2;

    public enum Kind { NONE, FENCE, BARRIER }

    public final int minX, maxX, minZ, maxZ;

    public ZoneBounds(int minX, int maxX, int minZ, int maxZ) {
        if (minX >= maxX || minZ >= maxZ) throw new IllegalArgumentException("пустые границы");
        this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
    }

    public static ZoneBounds fromNodes(List<Node> nodes) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Node n : nodes) {
            x0 = Math.min(x0, n.x * SCALE); x1 = Math.max(x1, n.x * SCALE);
            z0 = Math.min(z0, n.z * SCALE); z1 = Math.max(z1, n.z * SCALE);
        }
        return new ZoneBounds(x0 - MARGIN, x1 + MARGIN, z0 - MARGIN, z1 + MARGIN);
    }

    public boolean inside(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Что нужно поставить в колонке (x, z). */
    public Kind kind(int x, int z) {
        if (inside(x, z)) {
            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
            return edge ? Kind.FENCE : Kind.NONE;
        }
        int dx = x < minX ? minX - x : x > maxX ? x - maxX : 0;
        int dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0;
        return Math.max(dx, dz) <= BARRIER_THICKNESS ? Kind.BARRIER : Kind.NONE;
    }

    /** Пересекает ли чанк (в координатах чанков) забор или стену. */
    public boolean touchesChunk(int cx, int cz) {
        int bx0 = cx << 4, bx1 = bx0 + 15, bz0 = cz << 4, bz1 = bz0 + 15;
        int ex = BARRIER_THICKNESS;
        // чанк в расширенном прямоугольнике...
        boolean inOuter = bx1 >= minX - ex && bx0 <= maxX + ex && bz1 >= minZ - ex && bz0 <= maxZ + ex;
        if (!inOuter) return false;
        // ...и не целиком во внутренности (без периметра)
        boolean strictlyInside = bx0 > minX && bx1 < maxX && bz0 > minZ && bz1 < maxZ;
        return !strictlyInside;
    }

    /** Ближайшая точка играбельной области (для возврата игрока). */
    public int clampX(int x) { return Math.max(minX + 2, Math.min(maxX - 2, x)); }
    public int clampZ(int z) { return Math.max(minZ + 2, Math.min(maxZ - 2, z)); }

    /** Расстояние от точки внутри до ближайшего забора, блоков (0 и меньше снаружи). */
    public int distanceToFence(int x, int z) {
        return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
    }

    public int width() { return maxX - minX + 1; }
    public int depth() { return maxZ - minZ + 1; }
    /** Сторона квадрата, охватывающего прямоугольник (для запасного WorldBorder). */
    public int enclosingSquare() { return Math.max(width(), depth()) + 2 * (BARRIER_THICKNESS + 8); }
    public double centerX() { return (minX + maxX + 1) / 2.0; }
    public double centerZ() { return (minZ + maxZ + 1) / 2.0; }

    @Override
    public String toString() {
        return "X " + minX + ".." + maxX + ", Z " + minZ + ".." + maxZ + " (" + width() + "x" + depth() + " блоков)";
    }
}
