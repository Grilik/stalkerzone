package ru.stalkerzone.sim;

/**
 * Э1-11В: отдельный simSeed каждого мира. Не смешивается с сидом генерации Minecraft:
 * копии TheZone делят один level-seed ({@code WorldMode.THEZONE_SEED}), и simSeed, выведенный
 * из него, дал бы всем мирам одну историю симуляции. Новый мир получает случайный simSeed,
 * сохранённый в данных мира ({@code ZoneData}, NBT «simSeed») и восстанавливаемый при загрузке;
 * симуляция использует его как стабильную основу случайных событий ({@code Simulation.seed}).
 *
 * <p>Старые сохранения без отдельного simSeed сохраняют историю: сид уже лежит внутри сохранения
 * симуляции ({@code Simulation.seed}) и принимается за simSeed мира. Класс без Minecraft:
 * тест {@code SimSeedTest}.
 */
public final class SimSeed {
    private SimSeed() {}

    /** Сид нового мира: случайный, не выводится из сида мира Minecraft. */
    public static long newSeed() {
        return new java.util.Random().nextLong();
    }

    /** Сид при загрузке: сохранённый, если есть; иначе сид самой симуляции (совместимость старых миров). */
    public static long resolve(boolean hasStored, long stored, long simSeed) {
        return hasStored ? stored : simSeed;
    }
}
