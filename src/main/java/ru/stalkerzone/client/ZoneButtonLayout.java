package ru.stalkerzone.client;

/**
 * Э1-11Б: место кнопки «Зона» на экране создания мира. Чистый расчёт без Minecraft
 * (тест {@code ZoneButtonLayoutTest}): слева от ванильной «Создать новый мир»; если экран узкий —
 * справа от «Отмена»; если и там не помещается — над «Создать новый мир». Кнопка не перекрывает
 * ванильные элементы при любой ширине экрана.
 */
public final class ZoneButtonLayout {
    public static final int WIDTH = 100;
    public static final int HEIGHT = 20;
    public static final int GAP = 10;
    private static final int MARGIN = 4;

    private ZoneButtonLayout() {}

    /**
     * {@code {x, y}} кнопки «Зона». {@code createX/createY/createW} — ванильная кнопка создания,
     * {@code cancelX/cancelW} — «Отмена», {@code screenWidth} — ширина экрана.
     */
    public static int[] place(int screenWidth, int createX, int createY, int createW, int cancelX, int cancelW) {
        int x = createX - WIDTH - GAP;
        if (x >= MARGIN) return new int[] {x, createY};
        x = cancelX + cancelW + GAP;
        if (x + WIDTH <= screenWidth - MARGIN) return new int[] {x, createY};
        return new int[] {createX, createY - HEIGHT - MARGIN};
    }
}
