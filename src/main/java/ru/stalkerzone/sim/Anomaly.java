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

    /**
     * Смещение артефакта от центра аномалии (карточка 029): на кольце в 1–2 блока от границы радиуса поля,
     * поэтому за артефактом приходится лезть к самому краю — с риском задеть аномалию.
     *
     * @return {dx, dz} в блоках
     */
    public static double[] artifactOffset(AnomalyType type, java.util.Random r) {
        double ang = r.nextDouble() * Math.PI * 2;
        double ring = type.radius - 1.0 - r.nextDouble(); // 1–2 блока внутрь от границы
        return new double[]{Math.cos(ang) * ring, Math.sin(ang) * ring};
    }
}
