package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Э1-16: территория атласа ({@code kind: zone}) в симуляции — потенциал, занятие группировкой,
 * локальная задача и конечный природный запас с восстановлением.
 *
 * <p>Территория <b>не узел</b> (решение б3): гарнизона, населения и склада товаров у неё нет,
 * владельцем узла она не становится и базой не превращается. Принадлежность территории хранится
 * отдельно от {@code Node.owner} и не смешивается с численностью гарнизонов.
 *
 * <p>Запас территории — природный резерв (единицы ресурса зоны), а не товар {@link Good}:
 * в {@code Ledger} и {@code Simulation.totals()} он не входит. Добыча уменьшает запас; превращение
 * добытого в товары и запись прихода в Ledger — производство секторов (Э5-02, Э8-07), не эта задача.
 * Само владение территорией ничего не производит: людей, денег, оружия и товаров не создаёт.
 */
public final class Territory {
    /** Единиц природного запаса на единицу силы потенциала. */
    public static final int CAPACITY_PER_STRENGTH = 100;
    /** Дней без добычи до начала восстановления запаса. */
    public static final int REGEN_DELAY_DAYS = 7;
    /** Доля запаса, восстанавливаемая за день после задержки. */
    public static final double REGEN_PER_DAY = 0.05;
    /** Единиц ресурса за день работы одного человека. */
    public static final int YIELD_PER_WORKER = 2;

    public final String id;
    public final TerritoryKind kind;
    public final String name;
    /** Потенциал из атласа (сила 0..1 по ключам {@link TerritoryPotential#KEYS}); не меняется. */
    public final Map<String, Double> potential;
    /** Полный запас по ключам potential в порядке KEYS. */
    public final double[] capacity = new double[TerritoryPotential.KEYS.size()];
    /** Текущий природный запас по ключам. */
    public final double[] reserve = new double[TerritoryPotential.KEYS.size()];
    /** День последней добычи по ключам (для задержки восстановления). */
    public final int[] lastTake = new int[TerritoryPotential.KEYS.size()];

    /** Группировка-владелец (индекс в Simulation.factions), -1 — ничья. */
    public int owner = -1;
    /** Локальная задача; null — задачи нет. */
    public TerritoryTask task;
    /** День занятия, -1 — не занималась. */
    public int occupiedDay = -1;

    public Territory(String id, TerritoryKind kind, String name, Map<String, Double> potential) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.potential = Collections.unmodifiableMap(new LinkedHashMap<>(potential));
        for (int i = 0; i < capacity.length; i++) {
            capacity[i] = Math.round(TerritoryPotential.at(potential, TerritoryPotential.KEYS.get(i))
                    * CAPACITY_PER_STRENGTH);
            reserve[i] = capacity[i];
            lastTake[i] = -REGEN_DELAY_DAYS;
        }
    }

    public boolean occupied() { return owner >= 0; }

    public double potential(String key) { return TerritoryPotential.at(potential, key); }

    public double capacity(String key) { return capacity[index(key)]; }

    public double reserve(String key) { return reserve[index(key)]; }

    /** Пригодность к виду деятельности по потенциалу территории. */
    public boolean suitable(TerritoryTask t) { return TerritoryPotential.suitable(potential, t); }

    /** Есть ли что использовать: хотя бы один ключ потенциала больше нуля. */
    public boolean useful() { return !potential.isEmpty(); }

    /** Стратегическая ценность территории (для оценки группировкой, Э7-04). */
    public double value() { return TerritoryPotential.strategicValue(potential); }

    /** Восстановление запаса с задержкой; ничего не производит и в Ledger не пишет. */
    public void daily(int day) {
        for (int i = 0; i < reserve.length; i++) {
            if (capacity[i] <= 0 || reserve[i] >= capacity[i]) continue;
            if (day - lastTake[i] < REGEN_DELAY_DAYS) continue;
            reserve[i] = Math.min(capacity[i], reserve[i] + capacity[i] * REGEN_PER_DAY);
        }
    }

    /**
     * Добыча ресурса зоны людьми по локальной задаче. Возвращает реально добытые единицы и уменьшает
     * запас территории. Без владельца, без задачи, без людей или не по потенциалу — добычи нет (0).
     * Запас конечен: больше, чем есть в зоне, добыть нельзя.
     */
    public double take(TerritoryTask t, int workers, int want, int day) {
        if (!occupied() || task != t || t == null || !suitable(t) || workers <= 0 || want <= 0) return 0;
        int i = index(t.key);
        double got = Math.min(Math.min(want, reserve[i]), (double) workers * YIELD_PER_WORKER);
        if (got <= 0) return 0;
        reserve[i] -= got;
        lastTake[i] = day;
        return got;
    }

    private static int index(String key) {
        int i = TerritoryPotential.KEYS.indexOf(key);
        if (i < 0) throw new IllegalArgumentException("неизвестный ключ потенциала: " + key);
        return i;
    }

    // ------------------------------------------------------------------ сохранение

    void write(DataOutputStream out) throws IOException {
        out.writeUTF(id);
        out.writeUTF(kind.id);
        out.writeUTF(name);
        for (String k : TerritoryPotential.KEYS) out.writeDouble(potential(k));
        out.writeInt(owner);
        out.writeInt(task == null ? -1 : task.ordinal());
        out.writeInt(occupiedDay);
        for (int i = 0; i < reserve.length; i++) {
            out.writeDouble(reserve[i]);
            out.writeInt(lastTake[i]);
        }
    }

    static Territory read(DataInputStream in) throws IOException {
        String id = in.readUTF();
        TerritoryKind kind = TerritoryKind.byId(in.readUTF());
        String name = in.readUTF();
        Map<String, Double> pot = new LinkedHashMap<>();
        for (String k : TerritoryPotential.KEYS) {
            double v = in.readDouble();
            if (v > 0) pot.put(k, v);
        }
        if (kind == null) throw new IOException("неизвестный тип территории: " + id);
        Territory t = new Territory(id, kind, name, pot);
        t.owner = in.readInt();
        int task = in.readInt();
        t.task = task < 0 ? null : TerritoryTask.values()[task];
        t.occupiedDay = in.readInt();
        for (int i = 0; i < t.reserve.length; i++) {
            t.reserve[i] = in.readDouble();
            t.lastTake[i] = in.readInt();
        }
        return t;
    }
}
