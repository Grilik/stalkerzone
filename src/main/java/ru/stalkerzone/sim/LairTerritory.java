package ru.stalkerzone.sim;

/**
 * Территория логова (карточка 061): пустырь, где бродят мутанты логова. Чистая Java, без Minecraft.
 * Круг вокруг логова (радиус по виду) плюс вытянутая часть {@link Node#reach}, минус зоны людей
 * (круги у занятых узлов). Пока заготовка: расчёт добавляет карточка 061 ({@code docs/tasks/061-lair-territory.md}).
 */
public final class LairTerritory {
    private LairTerritory() {}

    /** Радиус территории вокруг логова, блоков. */
    public static int radius(LairKind kind) {
        return 0;
    }

    /**
     * Грызня видов (карточка 064): мутанты разных видов враждебны друг другу, только если людей
     * поблизости нет. Один вид своих не трогает; при людях рядом все бьют людей.
     */
    public static boolean hostile(LairKind a, LairKind b, boolean humansNear) {
        return a != b && !humansNear;
    }

    /** Точка (x, z) — зона людей: рядом с занятым узлом (кроме узла exceptNode). */
    public static boolean humanZone(Simulation s, int x, int z, int exceptNode) {
        return false;
    }

    /** Точка (x, z) в блоках мира — территория логова l. */
    public static boolean contains(Simulation s, Lair l, int x, int z) {
        return false;
    }
}
