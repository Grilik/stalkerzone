package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок базы для пульта базы (П-09, как ратуша MineColonies): обзор, жители, постройки, склад, журнал.
 * Чистая Java: сервер собирает строку, клиент рисует окно. Формат строк — в карточке 052
 * ({@code docs/tasks/052-base-snapshot.md}).
 *
 * <p>Строки (поля через табуляцию, первое поле — тип строки):
 * <ul>
 *   <li>{@code O} — обзор: узел, название, владелец, людей (NodePopulation.number), максимум (housing), касса группировки, фаза;</li>
 *   <li>{@code R} — житель: имя, роль, класс, чем занят, здоровье 0–100;</li>
 *   <li>{@code B} — постройка: тип, уровень, максимум уровня;</li>
 *   <li>{@code S} — склад: товар, количество;</li>
 *   <li>{@code J} — журнал: до 10 последних событий про эту базу или её группировку.</li>
 * </ul>
 */
public final class BaseSnapshot {
    private BaseSnapshot() {}

    /** Снимок базы на узле node (строки через перевод строки, поля через табуляцию). */
    public static String build(Simulation s, int node) {
        if (s == null || node < 0 || node >= s.nodes.size()) return "";
        Node n = s.nodes.get(node);
        if (n.owner < 0 || n.owner >= s.factions.size()) return "";
        Faction f = s.factions.get(n.owner);
        StringBuilder sb = new StringBuilder();
        sb.append("O\t").append(n.id).append('\t').append(clean(n.name)).append('\t').append(n.owner).append('\t')
                .append(NodePopulation.number(s, f, n)).append('\t').append(n.housing).append('\t')
                .append(f.stock.money).append('\t').append(f.phase.name()).append('\n');
        for (Person p : s.people.of(f.id)) {
            int hp = clamp(100 - p.injury * 25, 0, 100);
            sb.append("R\t").append(clean(p.name)).append('\t').append(p.role.ru).append('\t').append(p.cls.ru).append('\t')
                    .append(p.injury > 0 ? "ранен, медпункт" : "на базе").append('\t').append(hp).append('\n');
        }
        for (Building b : s.buildingsAt(node)) {
            sb.append("B\t").append(b.type.name()).append('\t').append(b.level).append('\t')
                    .append(Building.MAX_LEVEL).append('\n');
        }
        for (Good g : Good.ALL) {
            sb.append("S\t").append(g.name()).append('\t').append(f.stock.get(g)).append('\n');
        }
        List<String> journal = new ArrayList<>();
        for (String ev : s.events) {
            if (ev.contains(n.name) || ev.contains(f.name)) journal.add(ev);
        }
        int from = Math.max(0, journal.size() - 10);
        for (int i = from; i < journal.size(); i++) {
            sb.append("J\t").append(clean(journal.get(i))).append('\n');
        }
        return sb.toString();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}

