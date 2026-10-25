package ru.stalkerzone.sim;

/** Типы построек лагеря. Эффект каждого считается как уровень × состояние (hp). */
public enum BuildingType {
    HQ("Штаб", "ограничивает уровень остальных построек (не выше уровня штаба + 1)"),
    BARRACKS("Казарма", "+6 мест для жителей за уровень"),
    WORKSHOP("Мастерская", "+18% выпуска патронов, снаряжения и аптечек за уровень"),
    CLINIC("Медпункт", "−10% боевых потерь за уровень (не больше −40%)"),
    TOWER("Вышка и укрепления", "+12% к силе гарнизона за уровень");

    public final String ru;
    public final String effect;

    BuildingType(String ru, String effect) {
        this.ru = ru;
        this.effect = effect;
    }
}
