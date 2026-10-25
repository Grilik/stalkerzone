package ru.stalkerzone.sim;

/** Караван фракции: везёт товары и деньги к торговцу и обратно. Груз физически лежит в cargo. */
public final class Caravan {
    public final int owner;
    public int[] route;
    /** Индекс узла маршрута, из которого идёт караван. */
    public int leg;
    public int daysLeft;
    public boolean returning;
    public int guards;
    public final Stock cargo = new Stock();
    /** Сколько единиц продавать торговцу (уже лежит в cargo). */
    public final int[] sell = new int[Good.ALL.length];
    /** Сколько единиц закупить у торговца. */
    public final int[] want = new int[Good.ALL.length];

    public Caravan(int owner, int[] route, int guards) {
        this.owner = owner;
        this.route = route;
        this.guards = guards;
    }

    public int hub() {
        return returning ? route[0] : route[route.length - 1];
    }
}
