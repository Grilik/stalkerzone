package ru.stalkerzone.sim.tactics;

/**
 * Боевой дух бойца в перестрелке (v1.15). Не путать с долгим стрессом симуляции: мораль живёт минуты.
 * Падает от ранений, гибели товарищей и лидера, подавляющего огня и численного перевеса врага,
 * восстанавливается в укрытии и рядом с командиром. Храбрость (−1..1) смягчает удары, фанатики не ломаются.
 * <pre>
 *   ≥60 собран  — стреляет как обучен
 *   35..60 дрогнул — разброс больше
 *   15..35 паника — отходит в дальнее укрытие, палит не глядя
 *   &lt;15 сломлен — бежит на базу, не стреляет
 * </pre>
 * Из паники выходит только выше 45, из бегства выше 30 (гистерезис, чтобы не «мигал»).
 */
public final class Morale {
    public enum State { STEADY, SHAKEN, PANIC, BROKEN }

    private double value;
    private final double bravery;
    private final boolean fanatic;
    private State state = State.STEADY;

    public Morale(double bravery, boolean fanatic, double start) {
        this.bravery = Math.max(-1, Math.min(1, bravery));
        this.fanatic = fanatic;
        this.value = clamp(start);
        update();
    }

    public double value() { return value; }
    public State state() { return state; }
    public boolean fanatic() { return fanatic; }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    /** Множитель удара по морали: храбрые держатся, трусливые сыплются. */
    private double k() { return 1.0 - 0.45 * bravery; }

    private void drop(double v) {
        if (fanatic) return;
        value = clamp(value - v * k());
        update();
    }

    /** Ранение: frac — доля потерянного здоровья (0..1). */
    public void hit(double frac) { drop(5 + 35 * Math.max(0, Math.min(1, frac))); }

    /** Рядом погиб товарищ (dist в блоках). Чем ближе, тем страшнее. */
    public void allyDown(double dist) { drop(dist < 6 ? 14 : dist < 14 ? 9 : 5); }

    /** Погиб командир отряда или лидер. */
    public void leaderDown() { drop(26); }

    /** Над головой свистят пули. */
    public void suppressed() { drop(1.6); }

    /** Враг повержен: бодрит. */
    public void enemyDown() {
        value = clamp(value + 7);
        update();
    }

    /**
     * Секунда боя. ratio — перевес врага (их / нас), inCover, leaderNear — укрытие и командир рядом.
     * Без давления мораль постепенно возвращается к спокойному уровню.
     */
    public void second(double ratio, boolean inCover, boolean leaderNear, boolean underFire) {
        if (fanatic) return;
        double d = 0;
        if (ratio > 1.4) d -= Math.min(3.0, (ratio - 1.0) * 1.2) * k();
        if (inCover) d += 1.2;
        if (leaderNear) d += 1.4;
        if (!underFire) d += 0.8 + 0.6 * Math.max(0, bravery);
        value = clamp(value + d);
        update();
    }

    private void update() {
        if (fanatic) { state = State.STEADY; return; }
        State s;
        if (value >= 60) s = State.STEADY;
        else if (value >= 35) s = State.SHAKEN;
        else if (value >= 15) s = State.PANIC;
        else s = State.BROKEN;
        // гистерезис
        if (state == State.PANIC && s == State.SHAKEN && value < 45) s = State.PANIC;
        if (state == State.BROKEN && s != State.BROKEN && value < 30) s = State.BROKEN;
        state = s;
    }
}
