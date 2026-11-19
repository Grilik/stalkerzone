package ru.stalkerzone.sim;

/**
 * Э1-16: локальная задача занятой территории. Задача соответствует потенциалу зоны
 * ({@link TerritoryPotential#suitable}): лес — заготовка, свалка — разборка, поле — артефакты,
 * логово — охота, корпус — мастерская, развилка — торговля и пошлина, подвал — пост и укрытие.
 *
 * <p>Задач «еда» и «вода» нет: территория их не даёт. Порядок значений входит в формат сохранения.
 */
public enum TerritoryTask {
    WOOD("wood", "Заготовка дерева"),
    PARTS("parts", "Разборка техники"),
    ARTIFACTS("artifacts", "Сбор артефактов"),
    MUTANT("mutant", "Охота на мутантов"),
    WORKSHOP("workshop", "Мастерская"),
    STORAGE("storage", "Склад"),
    TRADE("trade", "Торговля и пошлина"),
    SHELTER("shelter", "Пост и укрытие");

    /** Ключ потенциала, который нужен задаче. */
    public final String key;
    public final String ru;

    TerritoryTask(String key, String ru) {
        this.key = key;
        this.ru = ru;
    }

    /** Задача по ключу потенциала; неизвестный ключ — {@code null}. */
    public static TerritoryTask byKey(String key) {
        for (TerritoryTask t : values()) if (t.key.equals(key)) return t;
        return null;
    }
}
