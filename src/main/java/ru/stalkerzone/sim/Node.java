package ru.stalkerzone.sim;

/** Локация карты Зоны. Статические параметры задаются сценарием, динамические (owner, market) сохраняются. */
public final class Node {
    public final int id;
    public final String name;
    public final NodeType type;
    /** Координаты на карте КПК (условные). */
    public final int x, z;

    // --- динамика ---
    public int owner = -1;
    public final Stock market = new Stock();
    /** Склад и сейф точки: товары и наличные лежат здесь физически и достаются тому, кто владеет точкой. */
    public final Stock depot = new Stock();
    /** Снабжённость гарнизона 0..1 (сглаженная): еда, жалованье, патроны, медикаменты. */
    public double supply = 1;
    /** Сепаратизм гарнизона 0..1: при 1 точка откалывается от фракции. */
    public double separatism;

    // --- статика ---
    /** Сколько еды в день можно вырастить на этой локации. */
    public int farmCap;
    /** Сколько деталей в день можно собрать на руинах. */
    public int partsCap;
    /** Сколько артефактов в день рождают аномалии. */
    public int artifactCap;
    /** Сколько людей размещается. */
    public int housing;
    /** Сила мутантов/аномалий при захвате нейтральной локации. */
    public double defense = 20;

    // торговец
    public boolean trader;
    public final int[] target = new int[Good.ALL.length];
    public final int[] importCap = new int[Good.ALL.length];
    public double importMarkup = 1.1;
    public double exportRate = 0.9;
    /**
     * Неснижаемый запас кассы торговца, руб. (карточка 025): ниже него торговец не тратит деньги на закупки
     * у Большой земли и не отправляет их с курьерами — это оборотные деньги на скупку у сталкеров.
     * Задаётся сценарием; 0 — торговец тратит кассу как раньше.
     */
    public long cashReserve;
    /** Приток наличных с Большой земли, руб. в день (карточка 025): выручка за вывезенный за Периметр товар. */
    public long mainlandRevenue;

    /** Постройки и рельеф узла уже есть на уровне оригинала (1:1 из Anomaly); генератор свои не ставит. */
    public boolean existingBuildings;

    public Node(int id, String name, NodeType type, int x, int z) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.x = x;
        this.z = z;
    }
}
