package ru.stalkerzone.sim;

/** Единая точка выбора сценария симуляции. Текущий id берётся из системного свойства stalkerzone.scenario. */
public final class Scenarios {
    public static final String ZONE = "zone";
    public static final String KORDON = "kordon";
    /** Сценарий по умолчанию — «Кордон 1.0». */
    public static final String DEFAULT = KORDON;

    private Scenarios() {}

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
