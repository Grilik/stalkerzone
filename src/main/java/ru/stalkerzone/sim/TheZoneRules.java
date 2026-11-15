package ru.stalkerzone.sim;

/**
 * Э2-20: что мод делает в мире TheZone (режим THEZONE, Э1-09) до привязки симуляции к его координатам (Э2-05).
 * Узлы симуляции стоят в координатах старого Кордона 1:1 (Node.x×10, деревня = 0, 0), а в TheZone там Генераторы —
 * поэтому NPC симуляции не появляются, а уровни оригинала (.szl) и их площадки не грузятся.
 * Класс без Minecraft: режим ставит {@code WorldMode.detect}, проверяет тест {@code TheZoneNpcTest}.
 */
public final class TheZoneRules {
    private static volatile boolean theZone;

    private TheZoneRules() {}

    public static void set(boolean v) { theZone = v; }

    public static boolean theZone() { return theZone; }

    /** NPC симуляции (сталкеры узлов, караваны, встречи, зомби, отшельники) ставятся в мир. */
    public static boolean simNpcSpawn() { return !theZone; }

    /** Уровни оригинала 1:1 (stalkerzone/levels/*.szl) и площадки старого Кордона грузятся. */
    public static boolean originalLevels() { return !theZone; }
}
