import ru.stalkerzone.client.PdaModel;
import ru.stalkerzone.sim.PdaSnapshot;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;

/**
 * Э0-12: снимок КПК по кругу. Строки Q (отряды в пути) и U (население узлов) из {@link PdaSnapshot}
 * разбираются {@link PdaModel}: сколько строк — столько записей, поля на месте (основа меток КПК на Xaero, решение в2).
 */
public class PdaRoundTripTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    static int count(String text, String tag) {
        int n = 0;
        for (String line : text.split("\n")) if (line.startsWith(tag + "\t")) n++;
        return n;
    }

    static String first(String text, String tag) {
        for (String line : text.split("\n")) if (line.startsWith(tag + "\t")) return line;
        return null;
    }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 1);
        String text = null;
        int day = 0;
        // дни до появления хотя бы одного отряда в пути (караван или конвой)
        for (; day < 120; day++) {
            text = PdaSnapshot.build(s, "Тестер", s.day, 0, 0, 0, 0, ZoneBounds.fromNodes(s.nodes), 0);
            if (count(text, "Q") > 0) break;
            s.step();
        }
        int q = count(text, "Q"), u = count(text, "U");
        ck(q > 0, "за " + day + " дн. в снимке появились отряды в пути (строк Q: " + q + ")");
        ck(u > 0, "в снимке есть население узлов (строк U: " + u + ")");

        PdaModel m = PdaModel.parse(text);
        ck(m.squads.size() == q, "PdaModel: отрядов " + m.squads.size() + " = строк Q " + q);
        ck(m.population.size() == u, "PdaModel: записей населения " + m.population.size() + " = строк U " + u);

        // поля первой строки Q: владелец, что, людей, x, z, цель, маршрут, шаг
        String[] fq = first(text, "Q").split("\t", -1);
        if (!m.squads.isEmpty()) {
            PdaModel.SquadInfo sq = m.squads.get(0);
            ck(sq.owner() == Integer.parseInt(fq[1]) && sq.what().equals(fq[2]) && sq.people() == Integer.parseInt(fq[3]),
                "отряд: владелец, что делает, людей");
            ck(sq.x() == Integer.parseInt(fq[4]) && sq.z() == Integer.parseInt(fq[5]), "отряд: координаты");
            ck(sq.target() == Integer.parseInt(fq[6]) && sq.route().equals(fq[7]) && sq.step() == Integer.parseInt(fq[8]),
                "отряд: цель, маршрут, шаг");
        }
        String[] fu = first(text, "U").split("\t", -1);
        if (!m.population.isEmpty()) {
            PdaModel.Population p = m.population.get(0);
            ck(p.node() == Integer.parseInt(fu[1]) && p.people() == Integer.parseInt(fu[2]), "население: узел и людей");
        }

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
