package ru.stalkerzone.sim;

public enum Phase {
    ACCUMULATE("Накопление"),
    WAR("Война"),
    RECOVER("Восстановление");

    public final String ru;

    Phase(String ru) {
        this.ru = ru;
    }
}
