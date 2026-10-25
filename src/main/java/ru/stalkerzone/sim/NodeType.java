package ru.stalkerzone.sim;

public enum NodeType {
    SETTLEMENT("Поселение"),
    BASE("База"),
    RUINS("Руины"),
    ANOMALY("Аномальное поле"),
    PSI("Пси-поле"),
    OUTPOST("Блокпост"),
    CITY("Город");

    public final String ru;

    NodeType(String ru) {
        this.ru = ru;
    }
}
