package ru.stalkerzone.zonemap;

import java.util.ArrayList;
import java.util.List;

/** Локация карты (как уровень в Call of Chernobyl): прямоугольник, базовая высота, стиль земли и всё, что на ней стоит. */
public final class Region {
    public enum Style { MEADOW, SWAMP, FOREST, JUNK, URBAN, WASTE, RED_FOREST, INDUSTRIAL }

    public final String id, name;
    public final int x0, z0, x1, z1;
    public final int baseY;
    public final Style style;
    /** Размах мелкого рельефа в блоках. */
    public final double rough;
    /** Детально прорисована (иначе черновик: рельеф, дороги и главные точки). */
    public boolean detailed;

    public final List<Features.Path> paths = new ArrayList<>();
    public final List<Features.Hill> hills = new ArrayList<>();
    public final List<Features.Pond> ponds = new ArrayList<>();
    public final List<Features.River> rivers = new ArrayList<>();
    public final List<Features.Forest> forests = new ArrayList<>();
    public final List<Features.GroundArea> grounds = new ArrayList<>();
    public final List<Features.Placement> placements = new ArrayList<>();
    public final List<Features.Smart> smarts = new ArrayList<>();

    public Region(String id, String name, int x0, int z0, int x1, int z1, int baseY, Style style, double rough) {
        this.id = id;
        this.name = name;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.baseY = baseY;
        this.style = style;
        this.rough = rough;
    }

    public boolean contains(double x, double z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    public int cx() { return (x0 + x1) / 2; }
    public int cz() { return (z0 + z1) / 2; }

    // удобные построители для схем
    public Region path(Features.PathType t, double w, double... pts) { paths.add(new Features.Path(t, w, 0, pts)); return this; }
    public Region rail(double embank, double... pts) { paths.add(new Features.Path(Features.PathType.RAIL, 5, embank, pts)); return this; }
    public Region hill(double x, double z, double r, double h) { hills.add(new Features.Hill(x, z, r, h)); return this; }
    public Region pond(double x, double z, double rx, double rz, int depth) { ponds.add(new Features.Pond(x, z, rx, rz, depth)); return this; }
    public Region river(double w, int depth, double... pts) { rivers.add(new Features.River(w, depth, pts)); return this; }
    public Region forest(Features.ForestKind k, double density, double... poly) { forests.add(new Features.Forest(k, density, poly)); return this; }
    public Region ground(Features.Ground g, double... poly) { grounds.add(new Features.GroundArea(g, poly)); return this; }
    public Region put(String kind, int x, int z, int rot, int padW, int padD) { placements.add(new Features.Placement(kind, x, z, rot, padW, padD)); return this; }
    public Region smart(String id, String name, Features.StType t, int x, int z, int r, String owner, int cap) {
        smarts.add(new Features.Smart(id, name, t, x, z, r, owner, cap));
        return this;
    }
}
