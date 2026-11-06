import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 052: данные пульта базы (П-09) — BaseSnapshot.build(sim, узел) для деревни новичков.
 *
 * Формат (табуляция):
 *   O  узел  название  владелец  людей  максимум  касса  фаза     — обзор (людей = NodePopulation.number, максимум = housing)
 *   R  имя  роль  класс  чем_занят  здоровье(0–100)              — жители: ростер группировки владельца
 *   B  тип  уровень  максимум_уровня                             — постройки узла (Simulation.buildingsAt)
 *   S  товар  количество                                         — склад группировки по всем товарам
 *   J  текст                                                     — журнал: до 10 последних событий про эту базу или её группировку
 */
public class BaseSnapshotTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 2);
        for (int d = 0; d < 15; d++) s.step();
        Node v = s.nodes.get(0);
        Faction lon = s.factions.get(v.owner);
        String snap = BaseSnapshot.build(s, 0);
        Map<String, List<String[]>> by = new HashMap<>();
        for (String line : snap.split("\n")) {
            String[] p = line.split("\t", -1);
            by.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p);
        }
        List<String[]> o = by.getOrDefault("O", List.of()), r = by.getOrDefault("R", List.of()),
                b = by.getOrDefault("B", List.of()), st = by.getOrDefault("S", List.of()), j = by.getOrDefault("J", List.of());
        ck(o.size() == 1, "одна строка обзора O");
        if (o.size() == 1 && o.get(0).length >= 8) {
            String[] p = o.get(0);
            ck(p[1].equals("0") && p[2].equals(v.name) && Integer.parseInt(p[3]) == v.owner, "O: узел, название, владелец");
            ck(Integer.parseInt(p[4]) == NodePopulation.number(s, lon, v), "O: людей = NodePopulation.number (" + p[4] + ")");
            ck(Integer.parseInt(p[5]) == v.housing, "O: максимум = жильё узла (" + p[5] + ")");
            ck(Long.parseLong(p[6]) == lon.stock.money, "O: касса = деньги группировки");
            ck(!p[7].isBlank(), "O: фаза группировки");
        } else ck(false, "O: 8 полей");

        int roster = s.people.of(lon.id).size();
        ck(r.size() == roster && roster > 0, "жителей R столько же, сколько в ростере одиночек (" + r.size() + " из " + roster + ")");
        boolean rOk = true;
        for (String[] p : r) {
            if (p.length < 6) { rOk = false; continue; }
            int hp = Integer.parseInt(p[5]);
            if (p[1].isBlank() || p[2].isBlank() || p[3].isBlank() || p[4].isBlank() || hp < 0 || hp > 100) rOk = false;
        }
        ck(rOk, "R: имя, роль, класс, чем занят, здоровье 0–100");

        ck(b.size() == s.buildingsAt(0).size() && !b.isEmpty(), "постройки B — все постройки узла (" + b.size() + ")");
        ck(st.size() == Good.ALL.length, "склад S — по строке на каждый товар (" + st.size() + ")");
        boolean sOk = true;
        for (String[] p : st) {
            Good g = null;
            for (Good x : Good.ALL) if (x.name().equals(p[1])) g = x;
            if (g == null || p.length < 3 || Long.parseLong(p[2]) != lon.stock.get(g)) sOk = false;
        }
        ck(sOk, "S: id товара (Good.name) и количество как на складе группировки");
        ck(j.size() <= 10, "журнал J — не больше 10 строк (" + j.size() + ")");
        boolean jOk = true;
        for (String[] p : j) if (p.length < 2 || !(p[1].contains(v.name) || p[1].contains(lon.name)) || !s.events.contains(p[1])) jOk = false;
        ck(jOk, "J: события симуляции про эту базу или её группировку");
        ck(BaseSnapshot.build(s, 12).isEmpty() || s.nodes.get(12).owner >= 0, "у ничьего узла снимок пустой");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
