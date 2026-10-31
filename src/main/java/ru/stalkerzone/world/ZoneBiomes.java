package ru.stalkerzone.world;

import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.Simulation;

import java.util.List;

/**
 * Какой биом в какой точке мира: ближайшая локация с извилистыми границами, Рыжий лес между Армейскими складами
 * и Радаром, дальние окраины. Порядок совпадает со списком "biomes" в world_preset/zone.json.
 */
public final class ZoneBiomes {
    public static final String[] IDS = {"kordon", "garbage", "agroprom", "dark_valley", "bar", "wild_territory", "army_warehouses",
            "yantar", "radar", "psi_field", "chaes", "perimeter", "red_forest", "outskirts", "pripyat", "swamps"};
    public static final int KORDON = 0, GARBAGE = 1, AGROPROM = 2, DARK = 3, BAR = 4, WILD = 5, ARMY = 6, YANTAR = 7, RADAR = 8,
            PSI = 9, CHAES = 10, PERIMETER = 11, RED_FOREST = 12, OUTSKIRTS = 13, PRIPYAT = 14, SWAMPS = 15;
    /** Источник пси-излучения Выжигателя: к северу от Радара. Смертельная зона и «выжженная» земля вокруг. */
    public static final int PSI_OFFSET_Z = ru.stalkerzone.sim.Psi.OFFSET_Z,
            PSI_LETHAL = ru.stalkerzone.sim.Psi.LETHAL, PSI_REACH = ru.stalkerzone.sim.Psi.REACH;

    private static int[] nx, nz, nb;
    private static int armyX, armyZ, radarX, radarZ, yantarX, yantarZ;

    private ZoneBiomes() {}

    private static synchronized void init() {
        if (nx != null) return;
        Simulation s = Scenarios.create(0);
        List<Node> nodes = s.nodes;
        int[] x = new int[nodes.size()], z = new int[nodes.size()], b = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            x[i] = n.x * ZoneBounds.SCALE;
            z[i] = n.z * ZoneBounds.SCALE;
            b[i] = switch (n.name) {
                case "Кордон" -> KORDON;
                case "Свалка" -> GARBAGE;
                case "Агропром" -> AGROPROM;
                case "Тёмная долина" -> DARK;
                case "Бар" -> BAR;
                case "Дикая территория" -> WILD;
                case "Армейские склады" -> ARMY;
                case "Янтарь" -> YANTAR;
                case "Радар" -> RADAR;
                case "ЧАЭС" -> CHAES;
                case "Военная база «Периметр»" -> PERIMETER;
                case "База военных «Агропром»" -> AGROPROM;
                case "База военных «Янтарь»" -> YANTAR;
                case "Болота", "Хутор Болотного Доктора" -> SWAMPS;
                case "Мёртвый город" -> WILD;
                case "Собачья деревня" -> KORDON;
                case "Кордон Лесника" -> RED_FOREST;
                default -> n.type == ru.stalkerzone.sim.NodeType.CITY ? PRIPYAT : OUTSKIRTS;
            };
            if (n.name.equals("Армейские склады")) { armyX = x[i]; armyZ = z[i]; }
            if (n.name.equals("Радар")) { radarX = x[i]; radarZ = z[i]; }
            if (n.name.equals("Янтарь")) { yantarX = x[i]; yantarZ = z[i]; }
        }
        nz = z;
        nb = b;
        nx = x;
    }

    /** Гладкий шум 0..1 по сетке (для извилистых границ). */
    private static double noise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uz;
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535.0;
    }

    /** Координаты источника пси-излучения в блоках. */
    public static int[] psiSource() {
        init();
        return new int[] {radarX, radarZ + PSI_OFFSET_Z};
    }

    public static int index(int x, int z) {
        init();
        double wx = x + 140 * (noise(x / 260.0, z / 260.0) - 0.5) + 40 * (noise(x / 70.0 + 13, z / 70.0) - 0.5);
        double wz = z + 140 * (noise(x / 260.0 + 7, z / 260.0 + 3) - 0.5) + 40 * (noise(x / 70.0, z / 70.0 + 9) - 0.5);
        // выжженная земля вокруг источника пси-излучения
        if (Math.hypot(wx - radarX, wz - (radarZ + PSI_OFFSET_Z)) < PSI_LETHAL + 30) return PSI;
        // Рыжий лес: полоса между Янтарём и Радаром
        double sx = radarX - yantarX, sz = radarZ - yantarZ, len2 = sx * sx + sz * sz;
        double t = len2 > 0 ? ((wx - yantarX) * sx + (wz - yantarZ) * sz) / len2 : -1;
        if (t > 0.2 && t < 0.8 && Math.hypot(wx - yantarX, wz - yantarZ) > 460) {
            double px = yantarX + sx * t, pz = yantarZ + sz * t;
            if (Math.hypot(wx - px, wz - pz) < 340) return RED_FOREST;
        }
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < nx.length; i++) {
            double d = (wx - nx[i]) * (wx - nx[i]) + (wz - nz[i]) * (wz - nz[i]);
            if (d < bd) { bd = d; best = i; }
        }
        if (Math.sqrt(bd) > 950) return OUTSKIRTS;
        return nb[best];
    }
}
