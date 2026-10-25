package ru.stalkerzone.sim;

/**
 * Логово мутантов. Мутанты как условная фракция: копят силу, нападают на соседние территории,
 * захватывают слабо охраняемые точки и могут разгромить базу. Логово можно уничтожить, но появятся новые.
 */
public final class Lair {
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
}
