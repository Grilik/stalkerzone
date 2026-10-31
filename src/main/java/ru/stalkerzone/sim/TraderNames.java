package ru.stalkerzone.sim;

/**
 * Имя торговца точки (карточка 011): единый источник правды для мира и КПК (карточка 015).
 * Живёт в симуляции, потому что на имена опираются и снимок КПК, и NPC торговца в мире.
 */
public final class TraderNames {
    private TraderNames() {}

    public static String name(Node n) {
        return switch (n.name) {
            case "Кордон", "Бункер Сидоровича" -> "Сидорович";
            case "Бар" -> "Бармен";
            case "Янтарь" -> "Профессор Сахаров";
            default -> "Торговец";
        };
    }
}
