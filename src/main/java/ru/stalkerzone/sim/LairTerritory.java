package ru.stalkerzone.sim;

import ru.stalkerzone.world.ZoneBounds;

/**
 * Территория логова (карточка 061): пустырь, где бродят мутанты логова. Чистая Java, без Minecraft.
 * Круг вокруг логова (радиус по виду) плюс вытянутая часть {@link Node#reach}, минус зоны людей
 * (круги у занятых узлов и баз).
 */
public final class LairTerritory {
    private LairTerritory() {}

    /** Радиус территории вокруг логова, блоков. */
    public static int radius(LairKind kind) {
        switch (kind) {
            case DOGS: return 75;
            case FLESH: return 60;
            default: return 50;
        }
    }

    /**
     * Грызня видов (карточка 064): мутанты разных видов враждебны друг другу, только если людей
     * поблизости нет. Один вид своих не трогает; при людях рядом все бьют людей.
     */
    public static boolean hostile(LairKind a, LairKind b, boolean humansNear) {
        return a != b && !humansNear;
    }

    /** Радиус зоны людей вокруг узла по его типу, блоков. */
    private static int zoneRadius(NodeType t) {
        switch (t) {
            case BASE: return 70;
            case SETTLEMENT: return 50;
            default: return 30;
        }
    }

    /**
     * Точка (x, z) — зона людей: ближе радиуса зоны к центру любого занятого узла (owner ≥ 0) или
     * любой базы (даже брошенной), кроме узла exceptNode. Центр узла — {@code x × 10, z × 10}.
     */
    public static boolean humanZone(Simulation s, int x, int z, int exceptNode) {
        for (Node n : s.nodes) {
            if (n.id == exceptNode) continue;
            if (n.owner < 0 && n.type != NodeType.BASE) continue;
            int dx = x - n.x * ZoneBounds.SCALE;
            int dz = z - n.z * ZoneBounds.SCALE;
            int r = zoneRadius(n.type);
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    /** Точка (x, z) в блоках мира — территория логова l. */
    public static boolean contains(Simulation s, Lair l, int x, int z) {
        if (humanZone(s, x, z, l.node)) return false;
        Node n = s.nodes.get(l.node);
        int[] d = Lair.denXZ(n);
        int r = radius(l.kind);
        int dx = x - d[0], dz = z - d[1];
        if (dx * dx + dz * dz <= r * r) return true;
        if (n.reach == null) return false;
        for (int i = 0; i + 1 < n.reach.length; i++) {
            double dist = distToSegment(x, z, n.reach[i][0], n.reach[i][1], n.reach[i + 1][0], n.reach[i + 1][1]);
            if (dist <= n.reachWidth) return true;
        }
        return false;
    }

    /** Расстояние от точки (x, z) до отрезка (ax, az)-(bx, bz). */
    private static double distToSegment(int x, int z, int ax, int az, int bx, int bz) {
        double vx = bx - ax, vz = bz - az;
        double wx = x - ax, wz = z - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 == 0 ? 0 : (wx * vx + wz * vz) / len2;
        if (t < 0) t = 0; else if (t > 1) t = 1;
        double px = ax + t * vx, pz = az + t * vz;
        return Math.hypot(x - px, z - pz);
    }
}
