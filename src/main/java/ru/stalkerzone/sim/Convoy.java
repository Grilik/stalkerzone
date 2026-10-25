package ru.stalkerzone.sim;

/**
 * Конвой снабжения (v1.10): везёт груз и наличные между складами фракции по дорогам Зоны.
 * Груз лежит в cargo физически: его можно отбить, разграбить или потерять на мутантах.
 */
public final class Convoy {
    public enum Kind {
        SUPPLY("снабжение гарнизона"), RETURN("излишки на базу"), CONTRACT("плата по контракту"), REINFORCE("пополнение"),
        TRIBUTE("дань и плата за охрану"), SMUGGLE("контрабанда");
        public final String ru;
        Kind(String ru) { this.ru = ru; }
    }

    public final int owner;
    public Kind kind;
    public int[] route;
    public int leg;
    public int daysLeft;
    public int guards;
    /** Бойцы пополнения в пути (уже в численности фракции, гибнут при нападении). */
    public int troops;
    public final Stock cargo = new Stock();

    public Convoy(int owner, Kind kind, int[] route, int guards) {
        this.owner = owner;
        this.kind = kind;
        this.route = route;
        this.guards = guards;
    }

    public int from() { return route[0]; }

    public int dest() { return route[route.length - 1]; }

    public int at() { return route[leg]; }

    public long value() {
        long v = cargo.money;
        for (Good g : Good.ALL) v += (long) cargo.get(g) * g.base;
        return v;
    }
}
