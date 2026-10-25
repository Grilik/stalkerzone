package ru.stalkerzone.sim;

/** Текущая стратегическая цель фракции. Пересматривается командованием раз в несколько дней. */
public enum Goal {
    FORTIFY("Укрепление"),
    TRADE("Торговля"),
    EXPAND("Расширение"),
    REVENGE("Месть"),
    CLEANSE("Зачистка"),
    /** Только когда Монолит активен: фракция решила дать бой. */
    RESIST("Отпор Монолиту"),
    /** Только для Монолита. */
    CRUSADE("Поход Монолита");

    public final String ru;

    Goal(String ru) {
        this.ru = ru;
    }
}
