package ru.stalkerzone.sim;

/**
 * Район аномалий локации (карточка 013): участок у узла сценария, где стоят поля одного набора типов.
 * Данные района задаёт сценарий (узлы сценария), код мира их только отражает — новая локация заработает
 * добавлением своего файла точек и узлов.
 *
 * <p>После выброса поле меняет место внутри своего района, но остаётся прежним по составу
 * (туннель остаётся полем электр), поэтому маршруты приходится прокладывать заново осмысленно.
 */
public final class AnomalyDistrict {
    /** Узел сценария, вокруг которого стоит район. */
    public final int node;
    /** Название района для отчётов. */
    public final String ru;
    /** Сколько полей в районе: от min до max включительно. */
    public final int min, max;
    /** Во сколько раз быстрее в районе зреют артефакты (карточка 013: у туннеля «Вспышка» ×3). */
    public final double artifactBoost;
    /** Допустимые в районе типы аномалий. */
    public final AnomalyType[] pool;

    public AnomalyDistrict(int node, String ru, int min, int max, double artifactBoost, AnomalyType... pool) {
        this.node = node;
        this.ru = ru;
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
        this.artifactBoost = artifactBoost;
        this.pool = pool;
    }

    /** Случайный допустимый тип района. */
    public AnomalyType pick(java.util.Random r) {
        return pool[r.nextInt(pool.length)];
    }
}
