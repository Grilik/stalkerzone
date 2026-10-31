package ru.stalkerzone.sim;

/**
 * Старт и возрождение игрока на локации (карточка 010). Чистая логика без Minecraft: выбор узла возрождения
 * и состав стартового набора задаются данными сценария, мир только превращает их в предметы и координаты.
 * Список набора — единственное место, где он меняется.
 */
public final class PlayerStart {
    private PlayerStart() {}

    /** Позиции стартового набора новичка (как в ТЧ). {@code count} — сколько штук за раз. */
    public enum KitItem {
        PM("ПМ"),
        PM_MAG("магазин к ПМ"),
        KNIFE("нож"),
        JACKET("кожаная куртка"),
        BANDAGE("бинт"),
        MEDKIT("аптечка"),
        BREAD("хлеб"),
        CANNED("консервы"),
        PDA("КПК"),
        BOLT("болт"),
        DETECTOR_ECHO("детектор «Отклик»"),
        RUBLES("рубли");

        public final String ru;

        KitItem(String ru) { this.ru = ru; }
    }

    /** Одна позиция набора: что и сколько. */
    public record Kit(KitItem kind, int count) {}

    /** Стартовый набор новичка: ПМ с двумя магазинами, нож, куртка, бинты, аптечка, еда, КПК, болты, детектор, деньги. */
    public static final Kit[] KIT = {
            new Kit(KitItem.PM, 1),
            new Kit(KitItem.PM_MAG, 2),
            new Kit(KitItem.KNIFE, 1),
            new Kit(KitItem.JACKET, 1),
            new Kit(KitItem.BANDAGE, 2),
            new Kit(KitItem.MEDKIT, 1),
            new Kit(KitItem.BREAD, 1),
            new Kit(KitItem.CANNED, 1),
            new Kit(KitItem.PDA, 1),
            new Kit(KitItem.BOLT, 10),
            new Kit(KitItem.DETECTOR_ECHO, 1),
            new Kit(KitItem.RUBLES, 500),
    };

    /** Вступление при первом входе (стиль ТЧ). */
    public static final String[] INTRO = {
            "Зона встретила тебя туманом и тишиной. Здесь каждый сам за себя, но новичков пока не трогают.",
            "Ты в деревне новичков: у костра ждут харчи и первые слухи, рядом в бункере — торговец и работа.",
            "Держи ухо востро: аномалии, мутанты и люди одинаково не любят самоуверенных.",
    };

    /** Подсказка про КПК в конце вступления. */
    public static final String PDA_HINT = "КПК — ПКМ в руке (или /zone pda): карта Зоны, задания, новости.";

    /** Узел, где появляется новый игрок и возрождаются без фракции (задаётся сценарием). */
    public static int spawnNode(Simulation sim) {
        if (sim == null || sim.spawnNode < 0 || sim.spawnNode >= sim.nodes.size()) return 0;
        return sim.spawnNode;
    }

    /**
     * Где игрок очнётся после смерти: без фракции — деревня (spawnNode); во фракции — её главная база,
     * но если база потеряна (узел больше не её) — снова деревня.
     */
    public static int respawnNode(Simulation sim, int faction) {
        if (sim == null) return 0;
        if (faction >= 0 && faction < sim.factions.size()) {
            int base = sim.factions.get(faction).baseNode;
            if (base >= 0 && base < sim.nodes.size() && sim.nodes.get(base).owner == faction) return base;
        }
        return spawnNode(sim);
    }
}
