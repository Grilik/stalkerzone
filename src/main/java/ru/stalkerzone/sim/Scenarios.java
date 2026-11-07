package ru.stalkerzone.sim;

/** Единая точка выбора сценария симуляции. Текущий id берётся из системного свойства stalkerzone.scenario. */
public final class Scenarios {
    public static final String ZONE = "zone";
    public static final String KORDON = "kordon";
    /** Карточка 054: полная война трёх группировок Кордона — отдельный сценарий. */
    public static final String KORDON_WAR = "kordon_war";
    /** Сценарий по умолчанию — «Кордон 1.0». */
    public static final String DEFAULT = KORDON;

    private Scenarios() {}

    /** Карточка 054: Кордон в любом варианте — «малый» («kordon») или с полной войной («kordon_war»). */
    public static boolean isKordon(String id) {
        return KORDON.equals(id) || KORDON_WAR.equals(id);
    }

    /** Текущий id сценария из системного свойства stalkerzone.scenario. */
    public static String current() {
        return System.getProperty("stalkerzone.scenario", DEFAULT);
    }

    /** Создать симуляцию по id сценария; id запоминается в Simulation.scenario. */
    public static Simulation create(String id, long seed) {
        Simulation sim;
        switch (id) {
            case ZONE -> sim = ZoneScenario.create(seed);
            case KORDON -> sim = KordonScenario.create(seed);
            case KORDON_WAR -> sim = KordonScenario.create(seed, true);
            default -> throw new IllegalArgumentException("Неизвестный сценарий: " + id);
        }
        sim.scenario = id;
        return sim;
    }

    /** Создать симуляцию текущего сценария. */
    public static Simulation create(long seed) {
        return create(current(), seed);
    }
}
