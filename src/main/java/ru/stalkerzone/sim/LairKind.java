package ru.stalkerzone.sim;

/** Вид мутантов логова: предел силы и скорость роста. */
public enum LairKind {
    DOGS("Слепые псы", 45, 1.3),
    FLESH("Плоти и кабаны", 50, 1.0),
    BLOODSUCKERS("Кровососы", 65, 0.8),
    CHIMERA("Химеры", 85, 0.5),
    CONTROLLERS("Контролёры", 90, 0.5);

    public final String ru;
    public final double cap;
    public final double growth;

    LairKind(String ru, double cap, double growth) {
        this.ru = ru;
        this.cap = cap;
        this.growth = growth;
    }
}
