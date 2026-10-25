package ru.stalkerzone.sim;

/** Аномалия: привязана к локации, смещена от её центра на (dx, dz) блоков. После выброса меняет положение. */
public final class Anomaly {
    public final int node;
    public int dx, dz;
    public AnomalyType type;
    /** Копит заряд, при полном заряде рождается артефакт. */
    public double charge;
    /** Сколько артефактов уже созрело и ждёт появления в мире (не больше 3). */
    public int pending;

    public Anomaly(int node, int dx, int dz, AnomalyType type) {
        this.node = node;
        this.dx = dx;
        this.dz = dz;
        this.type = type;
    }
}
