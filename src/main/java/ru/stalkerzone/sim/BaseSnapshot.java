package ru.stalkerzone.sim;

/**
 * Снимок базы для пульта базы (П-09, как ратуша MineColonies): обзор, жители, постройки, склад, журнал.
 * Чистая Java: сервер собирает строку, клиент рисует окно. Формат строк — в карточке 052
 * ({@code docs/tasks/052-base-snapshot.md}). Пока заготовка: данные добавляет карточка 052.
 */
public final class BaseSnapshot {
    private BaseSnapshot() {}

    /** Снимок базы на узле node (строки через перевод строки, поля через табуляцию). */
    public static String build(Simulation s, int node) {
        return "";
    }
}
