package ru.stalkerzone.sim;

/**
 * Э1-16: тип территории атласа (объект {@code kind: zone}, поле {@code type}).
 * Тип задаёт, что территория вообще может дать; силу потенциала не хранит — её выводит
 * {@link TerritoryPotential} из типа и особенностей (съёмка Э1-04, постройки и дороги атласа).
 */
public enum TerritoryKind {
    FOREST("forest", "Лес"),
    SCRAP("scrap", "Свалка и техника"),
    ANOMALY_FIELD("anomaly_field", "Аномальное поле"),
    LAIR("lair", "Логово"),
    INDUSTRIAL("industrial", "Заводской корпус"),
    RUINS("ruins", "Руины"),
    CROSSROADS("crossroads", "Развилка дорог"),
    UNDERGROUND("underground", "Подвал и бункер");

    /** Идентификатор типа как в атласе. */
    public final String id;
    public final String ru;

    TerritoryKind(String id, String ru) {
        this.id = id;
        this.ru = ru;
    }

    /** Тип по идентификатору атласа; неизвестный — {@code null} (валидатор атласа ловит это раньше). */
    public static TerritoryKind byId(String id) {
        for (TerritoryKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
