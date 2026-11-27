package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Э2-17 (К10): разреженная таблица отклонений отношений групп от отношений фракций.
 * Ключ — (id группы, фракция); нет записи — отношение группы равно отношению её фракции
 * ({@link Simulation#hostilityTo}). Отклонение хранится в диапазоне −1..1, эффективная
 * враждебность — 0..1. Две группы одной фракции могут по-разному относиться к третьей силе.
 *
 * <p>Отношение к другой группе выводится через фракцию цели. Вольные сталкеры (группа без фракции)
 * нейтральны: базовое отношение 0, отклонения задаются таблицей.
 */
public final class GroupRelations {
    private final Map<String, Map<Integer, Double>> dev = new TreeMap<>();

    /** Записать отклонение (−1..1); ноль убирает запись — таблица остаётся разреженной. */
    public void set(String group, int faction, double deviation) {
        double d = Math.max(-1, Math.min(1, deviation));
        Map<Integer, Double> m = dev.get(group);
        if (d == 0) {
            if (m != null) {
                m.remove(faction);
                if (m.isEmpty()) dev.remove(group);
            }
            return;
        }
        if (m == null) {
            m = new TreeMap<>();
            dev.put(group, m);
        }
        m.put(faction, d);
    }

    /** Отклонение группы к фракции; 0 — записи нет. */
    public double deviation(String group, int faction) {
        Map<Integer, Double> m = dev.get(group);
        Double d = m == null ? null : m.get(faction);
        return d == null ? 0 : d;
    }

    /** Число записей таблицы. */
    public int size() {
        int n = 0;
        for (Map<Integer, Double> m : dev.values()) n += m.size();
        return n;
    }

    /** Эффективная враждебность группы к фракции: отношение фракции + отклонение, 0..1. */
    public double hostility(Simulation s, Group g, int otherFaction) {
        double base = g.isFree() || otherFaction < 0 ? 0 : s.hostilityTo(s.factions.get(g.faction), otherFaction);
        return Math.max(0, Math.min(1, base + deviation(g.id, otherFaction)));
    }

    /** Отношение группы к другой группе — через её фракцию. */
    public double hostility(Simulation s, Group a, Group b) {
        return hostility(s, a, b.faction);
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(size());
        for (Map.Entry<String, Map<Integer, Double>> e : dev.entrySet()) {
            for (Map.Entry<Integer, Double> x : e.getValue().entrySet()) {
                out.writeUTF(e.getKey());
                out.writeInt(x.getKey());
                out.writeDouble(x.getValue());
            }
        }
    }

    void read(DataInputStream in) throws IOException {
        dev.clear();
        int n = in.readInt();
        for (int i = 0; i < n; i++) set(in.readUTF(), in.readInt(), in.readDouble());
    }
}