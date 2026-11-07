import ru.stalkerzone.sim.*;

/**
 * Помощник тестов: полный Кордон с войной трёх группировок. После карточки 054 это сценарий «kordon_war»
 * («kordon» — Малый Кордон: военные только гарнизон южного блокпоста, бандиты выключены); до 054 — «kordon».
 */
final class KordonWar {
    private KordonWar() {}

    static final String ID = has("kordon_war") ? "kordon_war" : "kordon";

    static Simulation create(long seed) { return Scenarios.create(ID, seed); }

    private static boolean has(String id) {
        try { Scenarios.create(id, 1); return true; } catch (IllegalArgumentException e) { return false; }
    }
}
