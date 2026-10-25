package ru.stalkerzone.sim;

/**
 * Подземная лаборатория из оригинальной игры. Данные хранятся на терминалах (parts); каждый терминал за цикл
 * можно скачать один раз. Цикл сбрасывается, когда побеждает Монолит: данные пропадают, всё заново.
 */
public final class Lab {
    public final int id;
    public final String name;
    public final String code;
    public final int node;
    /** Сложность 1..5. */
    public final int tier;
    public final int parts;
    /** Смещение входа от центра точки, блоки. */
    public final int dx, dz;
    public final String lore;
    /** Какие терминалы уже скачаны в этом цикле (кем угодно). */
    public int recovered;
    /** Какие данные уже изучают учёные. */
    public int delivered;

    public Lab(int id, String code, String name, int node, int tier, int parts, int dx, int dz, String lore) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.node = node;
        this.tier = tier;
        this.parts = parts;
        this.dx = dx;
        this.dz = dz;
        this.lore = lore;
    }

    public boolean taken(int part) { return (recovered & (1 << part)) != 0; }

    public boolean complete() { return Integer.bitCount(recovered) >= parts; }

    public int recoveredCount() { return Integer.bitCount(recovered); }

    public int deliveredCount() { return Integer.bitCount(delivered); }

    /** Награда институтов игроку за одну часть данных. */
    public long playerReward() {
        return switch (tier) {
            case 1 -> 3000;
            case 2 -> 6000;
            case 3 -> 9000;
            case 4 -> 14000;
            default -> 25000;
        };
    }

    /** Нужен накопитель большой ёмкости (диск), флешки не хватит. */
    public boolean needsDisk() { return tier >= 4; }
}
