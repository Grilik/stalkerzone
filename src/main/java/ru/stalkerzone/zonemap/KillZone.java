package ru.stalkerzone.zonemap;

import java.util.List;

/**
 * Запретная полоса перед периметром блокпоста (карточка 055): военные стреляют на поражение по тому,
 * кто в полосе, и не трогают тех, кто за ней. Полоса задаётся метками «kill_line» сборщика — по 4 угла
 * на прямоугольник. Чистая Java. Пока заготовка: расчёт добавляет карточка 055 ({@code docs/tasks/055-kill-zone.md}).
 */
public final class KillZone {
    private KillZone() {}

    /** Прямоугольники {x0, z0, x1, z1} из точек {x, z}: каждые 4 подряд — углы одного прямоугольника. */
    public static List<int[]> fromPoints(List<int[]> points) {
        return List.of();
    }

    /** Точка (x, z) в одной из полос. */
    public static boolean inside(List<int[]> zones, double x, double z) {
        return false;
    }

    /** Стреляют ли военные гарнизона на поражение по игроку или одиночке в точке (x, z). */
    public static boolean militaryHostile(List<int[]> zones, double x, double z) {
        return false;
    }
}
