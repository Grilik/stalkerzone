package ru.stalkerzone.gear;

/** Рюкзаки: открывают ряды инвентаря и поднимают предел переносимого веса. */
public enum Backpack {
    SMALL("backpack_small", "Сталкерский рюкзак", 9, 10, 1.5, 1),
    RAID("backpack_raid", "Рейдовый рюкзак", 18, 20, 2.5, 2),
    ARMY("backpack_army", "Армейский ранец", 18, 30, 3.0, 3);

    public static final Backpack[] ALL = values();

    public final String id, ru;
    public final int slots;
    public final double carryBonus, weight;
    public final int gearUnits;

    Backpack(String id, String ru, int slots, double carryBonus, double weight, int gearUnits) {
        this.id = id;
        this.ru = ru;
        this.slots = slots;
        this.carryBonus = carryBonus;
        this.weight = weight;
        this.gearUnits = gearUnits;
    }
}
