package ru.stalkerzone.sim;

/**
 * Вид мутантов логова: предел силы, скорость роста и средняя «цена» одного мутанта.
 * {@code mutantValue} — та же величина, что {@code MutantType.avgValue(kind)}: сколько силы логова
 * стоит один мутант (карточка 026). Держать числа в одном месте и брать их отсюда.
 */
public enum LairKind {
    DOGS("Слепые псы", 45, 1.3, 6.2),
    FLESH("Плоти и кабаны", 50, 1.0, 6.8),
    BLOODSUCKERS("Кровососы", 65, 0.8, 10.5),
    CHIMERA("Химеры", 85, 0.5, 14.0),
    CONTROLLERS("Контролёры", 90, 0.5, 10.5),
    // v1.14 (только в конец: в сохранениях номер)
    RODENTS("Тушканы", 30, 1.6, 2.0),
    BURERS("Бюреры", 70, 0.6, 15.25),
    GIANTS("Псевдогиганты", 95, 0.35, 12.0),
    POLTERGEISTS("Полтергейсты", 55, 0.6, 15.0);

    public final String ru;
    public final double cap;
    public final double growth;
    /** Средняя «цена» мутанта логова: по ней считаем, сколько их у логова (карточка 026). */
    public final double mutantValue;

    LairKind(String ru, double cap, double growth, double mutantValue) {
        this.ru = ru;
        this.cap = cap;
        this.growth = growth;
        this.mutantValue = mutantValue;
    }
}
