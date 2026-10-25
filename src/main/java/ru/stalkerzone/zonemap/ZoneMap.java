package ru.stalkerzone.zonemap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Готовая карта Зоны: локации по схеме, рельеф, дороги, железная дорога, вода, леса, площадки под объекты.
 * Всё детерминировано: одна и та же точка всегда даёт одну и ту же высоту и покрытие.
 */
public final class ZoneMap {
    public static final int SEA = 62;
    public static final int WILD_Y = 67;
    private static final int CELL = 64;

    public final int minX, minZ, maxX, maxZ;
    public final List<Region> regions = new ArrayList<>();
    /** Дороги между локациями. */
    public final List<Features.Path> links = new ArrayList<>();

    // пространственный индекс: клетка 64×64 -> объекты, влияющие на неё
    private final Map<Long, List<Object>> index = new HashMap<>();

    public ZoneMap(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public Region add(Region r) { regions.add(r); return r; }

    public Region region(String id) {
        for (Region r : regions) if (r.id.equals(id)) return r;
        return null;
    }

    public Region regionAt(double x, double z) {
        for (Region r : regions) if (r.contains(x, z)) return r;
        return null;
    }

    // ---------------------------------------------------------------- индекс

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private void indexBox(Object o, double x0, double z0, double x1, double z1) {
        for (int cx = (int) Math.floor(x0 / CELL); cx <= (int) Math.floor(x1 / CELL); cx++)
            for (int cz = (int) Math.floor(z0 / CELL); cz <= (int) Math.floor(z1 / CELL); cz++)
                index.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(o);
    }

    private static double[] bbox(double[] pts) {
        double a = Double.MAX_VALUE, b = Double.MAX_VALUE, c = -Double.MAX_VALUE, d = -Double.MAX_VALUE;
        for (int i = 0; i < pts.length; i += 2) {
            a = Math.min(a, pts[i]); b = Math.min(b, pts[i + 1]);
            c = Math.max(c, pts[i]); d = Math.max(d, pts[i + 1]);
        }
        return new double[] {a, b, c, d};
    }

    /** Вызвать после заполнения схемы. */
    public ZoneMap build() {
        index.clear();
        List<Features.Path> all = new ArrayList<>(links);
        for (Region r : regions) all.addAll(r.paths);
        for (Features.Path p : all) {
            double[] b = bbox(p.pts());
            double m = p.width() / 2 + p.embank() * 2 + 8;
            indexBox(p, b[0] - m, b[1] - m, b[2] + m, b[3] + m);
        }
        for (Region r : regions) {
            for (Features.Hill h : r.hills) indexBox(h, h.x() - h.r() * 2.2, h.z() - h.r() * 2.2, h.x() + h.r() * 2.2, h.z() + h.r() * 2.2);
            for (Features.Pond p : r.ponds) indexBox(p, p.x() - p.rx() - 12, p.z() - p.rz() - 12, p.x() + p.rx() + 12, p.z() + p.rz() + 12);
            for (Features.River v : r.rivers) {
                double[] b = bbox(v.pts());
                indexBox(v, b[0] - v.width() - 10, b[1] - v.width() - 10, b[2] + v.width() + 10, b[3] + v.width() + 10);
            }
            for (Features.Forest f : r.forests) {
                double[] b = bbox(f.poly());
                indexBox(f, b[0], b[1], b[2], b[3]);
            }
            for (Features.GroundArea g : r.grounds) {
                double[] b = bbox(g.poly());
                indexBox(g, b[0], b[1], b[2], b[3]);
            }
            for (Features.Placement p : r.placements) {
                double m = Math.max(p.padW(), p.padD()) / 2.0 + 10;
                indexBox(p, p.x() - m, p.z() - m, p.x() + m, p.z() + m);
            }
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> near(double x, double z, Class<T> c) {
        List<Object> l = index.get(key((int) Math.floor(x / CELL), (int) Math.floor(z / CELL)));
        List<T> out = new ArrayList<>();
        if (l != null) for (Object o : l) if (c.isInstance(o)) out.add((T) o);
        return out;
    }

    // ---------------------------------------------------------------- рельеф

    private int baseOf(double x, double z) {
        Region r = regionAt(x, z);
        return r != null ? r.baseY : WILD_Y;
    }

    private double roughOf(double x, double z) {
        Region r = regionAt(x, z);
        return r != null ? r.rough : 3;
    }

    /** Базовая высота с плавными переходами между локациями (сглаживание по сетке 160 блоков). */
    private double base(double x, double z) {
        double g = 160;
        double gx = Math.floor(x / g), gz = Math.floor(z / g);
        double fx = x / g - gx, fz = z / g - gz;
        double a = baseOf(gx * g, gz * g), b = baseOf((gx + 1) * g, gz * g), c = baseOf(gx * g, (gz + 1) * g), d = baseOf((gx + 1) * g, (gz + 1) * g);
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double top = a + (b - a) * ux, bot = c + (d - c) * ux;
        return top + (bot - top) * uz;
    }

    /** Природный рельеф без дорог, площадок и воды. */
    public double terrain(double x, double z) {
        double h = base(x, z);
        for (Features.Hill hl : near(x, z, Features.Hill.class)) {
            double d2 = ((x - hl.x()) * (x - hl.x()) + (z - hl.z()) * (z - hl.z())) / (hl.r() * hl.r());
            h += hl.h() * Math.exp(-d2 * 1.6);
        }
        double rough = roughOf(x, z);
        h += rough * Geo.fbm(x, z, 90, 3, 7) + rough * 0.35 * Geo.fbm(x, z, 22, 2, 11);
        return h;
    }

    /** Крупный рельеф для профиля дорог (без мелкого шума). */
    private double coarse(double x, double z) {
        double h = base(x, z);
        for (Features.Hill hl : near(x, z, Features.Hill.class)) {
            double d2 = ((x - hl.x()) * (x - hl.x()) + (z - hl.z()) * (z - hl.z())) / (hl.r() * hl.r());
            h += hl.h() * Math.exp(-d2 * 1.6);
        }
        return h + roughOf(x, z) * 0.6 * Geo.fbm(x, z, 260, 2, 7);
    }

    /** Ближайшая точка на линии: {расстояние, x, z}. */
    private static double[] nearest(double[] pts, double x, double z) {
        double best = Double.MAX_VALUE, bx = 0, bz = 0;
        for (int i = 0; i + 3 < pts.length; i += 2) {
            double[] s = Geo.segment(x, z, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
            if (s[0] < best) {
                best = s[0];
                bx = pts[i] + (pts[i + 2] - pts[i]) * s[1];
                bz = pts[i + 1] + (pts[i + 3] - pts[i + 1]) * s[1];
            }
        }
        return new double[] {best, bx, bz};
    }

    /** Результат для колонки: высота поверхности, покрытие, уровень воды. */
    public static final class Column {
        public int height;
        public String top = "minecraft:grass_block";
        public String under = "minecraft:dirt";
        public int water = Integer.MIN_VALUE;
        /** Рельсы и шпалы над поверхностью (null если нет). */
        public String deco;
        public Features.PathType path;
    }

    public Column column(int x, int z) {
        Column c = new Column();
        double h = terrain(x, z);
        Region reg = regionAt(x, z);
        // площадки под объекты: ровно по высоте точки объекта, края плавно
        for (Features.Placement p : near(x, z, Features.Placement.class)) {
            if (p.padW() <= 0) continue;
            double hw = p.padW() / 2.0, hd = p.padD() / 2.0;
            boolean swap = p.rot() == 90 || p.rot() == 270;
            double ax = Math.abs(x - p.x()) - (swap ? hd : hw), az = Math.abs(z - p.z()) - (swap ? hw : hd);
            double out = Math.max(ax, az);
            if (out > 10) continue;
            double ph = Math.round(coarse(p.x(), p.z()));
            double t = out <= 0 ? 1 : 1 - Geo.smooth(0, 10, out);
            h = h + (ph - h) * t;
        }
        // дороги и насыпь
        Features.Path onPath = null;
        double pathH = 0;
        for (Features.Path p : near(x, z, Features.Path.class)) {
            double[] n = nearest(p.pts(), x, z);
            double half = p.width() / 2;
            if (p.type() == Features.PathType.RAIL) {
                double crest = coarse(n[1], n[2]) + p.embank();
                double slope = Math.max(0, n[0] - half - 1);
                double rail = crest - slope * 0.8;
                if (rail > h) h = rail;
                if (n[0] <= half + 1) { onPath = p; pathH = crest; }
            } else {
                double target = coarse(n[1], n[2]);
                double t = 1 - Geo.smooth(half, half + 6, n[0]);
                if (t > 0) h = h + (target - h) * t;
                if (n[0] <= half && (onPath == null || onPath.type() != Features.PathType.RAIL)) { onPath = p; pathH = target; }
            }
        }
        c.height = (int) Math.round(h);
        // покрытие
        String top = "minecraft:grass_block";
        if (reg != null) {
            top = switch (reg.style) {
                case SWAMP -> Geo.noise(x / 9.0, z / 9.0, 3) < 0.45 ? "minecraft:mud" : "minecraft:grass_block";
                case JUNK -> Geo.noise(x / 6.0, z / 6.0, 5) < 0.4 ? "minecraft:gravel" : Geo.noise(x / 5.0, z / 5.0, 9) < 0.35 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                case WASTE -> Geo.noise(x / 7.0, z / 7.0, 5) < 0.5 ? "minecraft:coarse_dirt" : "minecraft:gravel";
                case RED_FOREST -> Geo.noise(x / 6.0, z / 6.0, 5) < 0.55 ? "minecraft:podzol" : "minecraft:coarse_dirt";
                case URBAN, INDUSTRIAL -> Geo.noise(x / 8.0, z / 8.0, 5) < 0.3 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                default -> Geo.noise(x / 23.0, z / 23.0, 5) < 0.06 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
            };
        }
        for (Features.GroundArea g : near(x, z, Features.GroundArea.class)) {
            if (!Geo.inPolygon(x, z, g.poly())) continue;
            top = switch (g.ground()) {
                case FIELD -> (Math.floorMod(x + z, 4) == 0) ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                case MUD -> "minecraft:mud";
                case GRAVEL -> "minecraft:gravel";
                case ASH -> "minecraft:coarse_dirt";
                case PODZOL -> "minecraft:podzol";
                case CONCRETE -> Geo.hash(x, z, 3) < 0.12 ? "minecraft:cracked_stone_bricks" : "minecraft:smooth_stone";
                case MARSH -> Geo.noise(x / 5.0, z / 5.0, 8) < 0.5 ? "minecraft:mud" : "minecraft:moss_block";
                default -> top;
            };
        }
        if (onPath != null) {
            c.path = onPath.type();
            c.height = (int) Math.round(pathH);
            double r = Geo.hash(x, z, 17);
            switch (onPath.type()) {
                case ASPHALT -> { top = r < 0.06 ? "minecraft:coarse_dirt" : r < 0.1 ? "minecraft:andesite" : r < 0.55 ? "minecraft:gray_concrete" : "minecraft:black_concrete"; c.under = "minecraft:stone"; }
                case DIRT -> { top = r < 0.15 ? "minecraft:gravel" : r < 0.3 ? "minecraft:grass_block" : "minecraft:dirt_path"; }
                case TRAIL -> top = r < 0.55 ? "minecraft:dirt_path" : "minecraft:grass_block";
                case RAIL -> {
                    top = "minecraft:gravel";
                    c.under = "minecraft:gravel";
                    c.deco = railDeco(onPath, x, z);
                }
            }
        }
        // вода: пруды и реки опускают дно, вода до своего уровня
        for (Features.Pond p : near(x, z, Features.Pond.class)) {
            double d = Math.hypot((x - p.x()) / p.rx(), (z - p.z()) / p.rz());
            if (d >= 1.25) continue;
            int level = (int) Math.round(coarse(p.x(), p.z())) - 1;
            if (d < 1) {
                int bottom = level - (int) Math.round(p.depth() * (1 - d * d)) - 1;
                c.height = Math.min(c.height, bottom + 1);
                c.water = level;
                top = d > 0.8 ? "minecraft:mud" : "minecraft:clay";
            } else {
                c.height = (int) Math.min(c.height, level + 1 + Math.round((d - 1) * 8));
                if (d < 1.08) top = "minecraft:mud";
            }
        }
        for (Features.River v : near(x, z, Features.River.class)) {
            double[] n = nearest(v.pts(), x, z);
            double half = v.width() / 2;
            if (n[0] > half + 6) continue;
            int level = Math.min(SEA + 3, (int) Math.round(coarse(n[1], n[2])) - 2);
            if (n[0] <= half) {
                c.height = Math.min(c.height, level - v.depth() + (int) Math.round(v.depth() * (n[0] / half) * 0.6) + 1);
                c.water = level;
                top = "minecraft:sand";
                c.deco = null;
            } else {
                c.height = (int) Math.min(c.height, level + 1 + Math.round((n[0] - half) * 0.7));
            }
        }
        if (reg != null && reg.style == Region.Style.SWAMP && c.water == Integer.MIN_VALUE && c.height <= SEA + 1 && Geo.noise(x / 14.0, z / 14.0, 21) < 0.45) {
            c.water = SEA + 1;
            c.height = Math.min(c.height, SEA);
            top = "minecraft:mud";
        }
        c.top = top;
        return c;
    }

    private String railDeco(Features.Path p, int x, int z) {
        double[] pts = p.pts();
        double best = Double.MAX_VALUE, dirX = 1, dirZ = 0;
        for (int i = 0; i + 3 < pts.length; i += 2) {
            double[] s = Geo.segment(x, z, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
            if (s[0] < best) { best = s[0]; dirX = pts[i + 2] - pts[i]; dirZ = pts[i + 3] - pts[i + 1]; }
        }
        boolean ew = Math.abs(dirX) >= Math.abs(dirZ);
        // две колеи: рельсы на расстоянии ~1 от оси, между ними шпалы-полублоки
        if (best < 0.5) return "minecraft:rail[shape=" + (ew ? "east_west" : "north_south") + "]";
        if (best < 1.6 && Math.floorMod(ew ? x : z, 2) == 0) return "minecraft:spruce_slab[type=bottom]";
        return null;
    }

    // ---------------------------------------------------------------- деревья

    public record Tree(int x, int z, Features.ForestKind kind, int height, long seed) {}

    /** Дерево в клетке сетки 5×5 (если есть). */
    public Tree treeInCell(int cx, int cz) {
        int x = cx * 5 + (int) (Geo.hash(cx, cz, 41) * 5), z = cz * 5 + (int) (Geo.hash(cx, cz, 43) * 5);
        double best = 0;
        Features.ForestKind kind = null;
        for (Features.Forest f : near(x, z, Features.Forest.class)) {
            if (f.density() > best && Geo.inPolygon(x, z, f.poly())) { best = f.density(); kind = f.kind(); }
        }
        // одиночные деревья среди лугов
        Region reg = regionAt(x, z);
        if (kind == null && reg != null) {
            double sparse = switch (reg.style) {
                case MEADOW -> 0.012; case SWAMP -> 0.05; case FOREST -> 0.35; case RED_FOREST -> 0.4; case WASTE -> 0.01; default -> 0.02;
            };
            if (Geo.hash(cx, cz, 47) < sparse) {
                best = 1;
                kind = switch (reg.style) {
                    case SWAMP -> Features.ForestKind.SWAMP_WILLOW;
                    case RED_FOREST -> Features.ForestKind.RED;
                    case WASTE -> Features.ForestKind.DEAD;
                    case FOREST -> Features.ForestKind.PINE;
                    default -> Geo.hash(cx, cz, 49) < 0.5 ? Features.ForestKind.BIRCH : Features.ForestKind.MIXED;
                };
            } else return null;
        }
        if (kind == null) return null;
        // лес гуще в середине, просветы по шуму
        double clearing = Geo.noise(x / 40.0, z / 40.0, 53);
        if (Geo.hash(cx, cz, 51) > best * (0.55 + 0.6 * clearing)) return null;
        // не на дорогах и площадках
        for (Features.Path p : near(x, z, Features.Path.class)) if (nearest(p.pts(), x, z)[0] < p.width() / 2 + 3 + p.embank()) return null;
        for (Features.Placement p : near(x, z, Features.Placement.class)) {
            if (Math.abs(x - p.x()) < p.padW() / 2.0 + 4 && Math.abs(z - p.z()) < p.padD() / 2.0 + 4) return null;
            if (Math.abs(x - p.x()) < p.padD() / 2.0 + 4 && Math.abs(z - p.z()) < p.padW() / 2.0 + 4) return null;
        }
        for (Features.Pond p : near(x, z, Features.Pond.class)) if (Math.hypot((x - p.x()) / p.rx(), (z - p.z()) / p.rz()) < 1.1) return null;
        int h = 6 + (int) (Geo.hash(cx, cz, 55) * 6);
        return new Tree(x, z, kind, h, (long) cx * 7919 + cz);
    }

    /** Объекты, площадка которых пересекает прямоугольник. */
    public List<Features.Placement> placementsIn(int x0, int z0, int x1, int z1, int margin) {
        List<Features.Placement> out = new ArrayList<>();
        for (Region r : regions) for (Features.Placement p : r.placements) {
            int ext = Math.max(p.padW(), p.padD()) / 2 + margin;
            if (p.x() + ext >= x0 && p.x() - ext <= x1 && p.z() + ext >= z0 && p.z() - ext <= z1) out.add(p);
        }
        return out;
    }

    public List<Features.Path> allPaths() {
        List<Features.Path> all = new ArrayList<>(links);
        for (Region r : regions) all.addAll(r.paths);
        return all;
    }
}
