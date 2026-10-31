package ru.stalkerzone.sim;

import ru.stalkerzone.world.ZoneBounds;

/**
 * Логово мутантов. Мутанты как условная фракция: копят силу, нападают на соседние территории,
 * захватывают слабо охраняемые точки и могут разгромить базу. Логово можно уничтожить, но появятся новые.
 */
public final class Lair {
    /** Расстояние логова от центра точки в мировых блоках. */
    public static final int DEN_DIST = 55;
    /** Сколько мутантов логова может одновременно бродить в мире (то же, что в MutantManager). */
    public static final int MAX_MUTANTS = 6;

    public int node;
    public LairKind kind;
    public double strength;
    /** Лаборатория: растёт быстрее, после зачистки даёт больше артефактов. */
    public boolean lab;
    public int raidCooldown;
    public int age;

    public Lair(int node, LairKind kind, double strength, boolean lab) {
        this.node = node;
        this.kind = kind;
        this.strength = strength;
        this.lab = lab;
    }

    public double cap() { return kind.cap * (lab ? 1.15 : 1.0); }

    public double growth() { return kind.growth * (lab ? 1.4 : 1.0); }

    public String title() { return kind.ru + (lab ? " (лаборатория)" : ""); }

    /**
     * Сколько мутантов сейчас у логова: сила, делённая на среднюю «цену» мутанта вида (карточка 026).
     * Та же формула, что в мире у {@code MutantManager.wanted}, с тем же потолком {@link #MAX_MUTANTS}.
     */
    public int mutantCount() {
        int n = (int) Math.ceil(strength / kind.mutantValue);
        return Math.max(1, Math.min(MAX_MUTANTS, n));
    }

    /**
     * Логово стоит в стороне от центра точки, чтобы не лезть в лагерь. Направление постоянно для точки.
     * Одна формула на мир ({@code MutantManager}) и на метку КПК ({@code PdaSnapshot}), чтобы не разошлись.
     *
     * @return мировые координаты {x, z} без обрезки по границам Зоны
     */
    public static int[] denXZ(Node n) {
        double ang = ((n.id * 2654435761L) >>> 8) % 360 * Math.PI / 180;
        return new int[]{
                n.x * ZoneBounds.SCALE + (int) Math.round(Math.cos(ang) * DEN_DIST),
                n.z * ZoneBounds.SCALE + (int) Math.round(Math.sin(ang) * DEN_DIST)
        };
    }
}
