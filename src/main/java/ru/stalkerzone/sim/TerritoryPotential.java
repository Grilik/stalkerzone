package ru.stalkerzone.sim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Э1-16: потенциал территории вместо мини-баз (решение б1, {@code docs/claude/decisions/b-atlas.md}).
 *
 * <p>Потенциал выводится из <b>типа</b> территории ({@link TerritoryKind}) и её <b>особенностей</b>
 * ({@link Features}: лес, существующие постройки, подземные объёмы, дороги, логова и поля атласа).
 * Произвольных значений по зонам нет: атлас хранит результат этого вывода, {@code TerritoryPotentialTest}
 * сверяет записанное с выведенным.
 *
 * <p><b>Еды и воды территория не даёт</b> (пользователь, 15.11 19:14): ключи {@code food} и {@code water}
 * запрещены и в выводе, и в атласе. Еда и питьевая вода — только привозные (Э8-08), мясо мутантов —
 * охота и разделка (Э9-06), а не производство территории.
 *
 * <p>Запас ресурса и его восстановление — состояние симуляции ({@link Territory}), не атласа.
 * Числа ниже — стартовые силы и нормы (как веса доктрин), баланс уточняется в Э7-01/Э8-07.
 */
public final class TerritoryPotential {
    /** Ключи потенциала в порядке записи в сохранение. Порядок не менять: он входит в формат. */
    public static final List<String> KEYS =
            List.of("wood", "parts", "artifacts", "mutant", "workshop", "storage", "trade", "shelter");
    /** Запрещённые ключи: территория не производит еду и воду. */
    public static final Set<String> FORBIDDEN = Set.of("food", "water");

    /** Колонок-построек на полную промышленную площадку (мастерская, склад). */
    public static final int BUILDINGS_NORM = 1000;
    /** Колонок подземных объёмов на полное укрытие от выброса. */
    public static final int UNDERGROUND_NORM = 500;
    /** Дорог, сходящихся в полноценной развилке. */
    public static final int ROADS_NORM = 3;
    /** Колонок-построек на свалке, дающих детали разборкой. */
    public static final int SCRAP_NORM = 500;
    /** Колонок-построек в руинах, дающих детали разборкой. */
    public static final int RUINS_NORM = 300;

    private TerritoryPotential() {}

    /**
     * Особенности территории — измеряемые величины (съёмка Э1-04 и атлас), не оценки на глаз.
     *
     * @param forest          доля леса в зоне, 0..1 (класс «лес» съёмки: листва, стволы, sapling)
     * @param buildingArea    колонок с постройками (≥ 3 искусственных блоков над землёй)
     * @param undergroundArea колонок с подземными объёмами (подвалы, бункеры, туннели)
     * @param roads           дорог атласа, проходящих через зону
     * @param lairs           логов атласа внутри зоны
     * @param fields          аномальных полей атласа внутри зоны
     */
    public record Features(double forest, int buildingArea, int undergroundArea, int roads, int lairs, int fields) {
        public static final Features EMPTY = new Features(0, 0, 0, 0, 0, 0);
    }

    /** Потенциал территории: только ключи с силой больше нуля, сила 0..1 с шагом 0,01. */
    public static Map<String, Double> derive(TerritoryKind kind, Features ft) {
        double f = clamp(ft.forest());
        double b = norm(ft.buildingArea(), BUILDINGS_NORM);
        double u = norm(ft.undergroundArea(), UNDERGROUND_NORM);
        double r = norm(ft.roads(), ROADS_NORM);
        double l = norm(ft.lairs(), 1);
        double p = norm(ft.fields(), 1);

        // лес — дерево и стройматериалы; лесная территория промысла даёт больше
        double wood = kind == TerritoryKind.FOREST ? 0.3 + 0.7 * f : 0.7 * f;
        // металлолом, техника, свалки и руины — детали; заводской корпус — разборка
        double parts = switch (kind) {
            case SCRAP -> 0.5 + 0.5 * norm(ft.buildingArea(), SCRAP_NORM);
            case INDUSTRIAL -> 0.3 * b;
            case RUINS -> 0.3 + 0.4 * norm(ft.buildingArea(), RUINS_NORM);
            default -> 0;
        };
        // аномальное поле — артефакты (после выбросов), логово — части и мясо мутантов
        double artifacts = kind == TerritoryKind.ANOMALY_FIELD ? 0.4 + 0.6 * p : 0;
        double mutant = kind == TerritoryKind.LAIR ? 0.4 + 0.6 * l : 0.4 * l;
        // существующие постройки TheZone — мастерская и склад; подземные объёмы — укрытие
        double workshop = kind == TerritoryKind.INDUSTRIAL ? 0.4 + 0.6 * b : 0.5 * b;
        double storage = 0.6 * b + 0.3 * u;
        double shelter = kind == TerritoryKind.UNDERGROUND ? 0.4 + 0.6 * u : 0.6 * u + 0.4 * b;
        // развилка дорог и переход — торговля или пошлина
        double trade = kind == TerritoryKind.CROSSROADS ? 0.3 + 0.7 * r : 0.4 * r;

        Map<String, Double> out = new LinkedHashMap<>();
        put(out, "wood", wood);
        put(out, "parts", parts);
        put(out, "artifacts", artifacts);
        put(out, "mutant", mutant);
        put(out, "workshop", workshop);
        put(out, "storage", storage);
        put(out, "trade", trade);
        put(out, "shelter", shelter);
        return out;
    }

    /**
     * Потенциальная стратегическая ценность 0..1: контроль дорог и переходов, артефакты, укрытие,
     * мастерская и детали. Хозяйственные дерево и склад ценность не дают — это снабжение, не позиция.
     */
    public static double strategicValue(Map<String, Double> potential) {
        return round(0.30 * at(potential, "trade") + 0.25 * at(potential, "artifacts")
                + 0.20 * at(potential, "shelter") + 0.15 * at(potential, "workshop")
                + 0.10 * at(potential, "parts"));
    }

    /** Пригодность территории к виду деятельности: задача соответствует потенциалу, если сила больше нуля. */
    public static boolean suitable(Map<String, Double> potential, TerritoryTask task) {
        return task != null && at(potential, task.key) > 0;
    }

    public static double at(Map<String, Double> potential, String key) {
        Double v = potential.get(key);
        return v == null ? 0 : v;
    }

    private static void put(Map<String, Double> out, String key, double v) {
        double r = round(clamp(v));
        if (r > 0) out.put(key, r);
    }

    private static double norm(int value, int norm) {
        return clamp(norm <= 0 ? 0 : (double) value / norm);
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
