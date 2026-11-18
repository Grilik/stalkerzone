import ru.stalkerzone.sim.*;

/**
 * К1 (Э4-07): массивы симуляции размером по числу узлов, а не жёстко на 64.
 *
 * Сейчас People.zombies, Politics.cutDays и Simulation.knowledge созданы как new ...[64], а в знании
 * цикл идёт по knowledge.length — на сценарии из 300 узлов это даст ArrayIndexOutOfBounds.
 * Тест красный до правки: на синтетическом атласе 300 узлов 30 дней жизни.
 */
public class NodeCountTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        kordonFits();
        synthetic300();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** На реальном Кордоне (24 узла) массивы должны быть не больше числа узлов, но и не меньше. */
    static void kordonFits() {
        Simulation s = KordonScenario.create(42L);
        int n = s.nodes.size();
        ck(n > 0 && n < 64, "Кордон: узлов " + n + " (меньше 64 — жёсткий размер не виден)");
        ck(s.people.zombies.length == n, "People.zombies длиной " + n + ", а не " + s.people.zombies.length);
        ck(s.politics.cutDays.length == n, "Politics.cutDays длиной " + n + ", а не " + s.politics.cutDays.length);
        ck(s.knowledge.length == n, "Simulation.knowledge длиной " + n + ", а не " + s.knowledge.length);
    }

    /** Главная проверка К1: 300 узлов живут 30 дней без выхода за границы массивов. */
    static void synthetic300() {
        BigAtlas atlas = new BigAtlas(20, 15, 3); // 20 × 15 = 300 узлов

        boolean ok = true;
        String err = "";
        Simulation s = null;
        try {
            s = atlas.create(7L);
            s.step(30);
        } catch (RuntimeException e) {
            ok = false;
            err = e.toString();
        }
        ck(ok, "300 узлов × 30 дней без исключений" + (ok ? "" : ": " + err));
        if (!ok) return;

        ck(s.nodes.size() == 300, "синтетический атлас: 300 узлов");

        ck(s.people.zombies.length >= 300, "People.zombies покрывает 300 узлов: " + s.people.zombies.length);
        ck(s.politics.cutDays.length >= 300, "Politics.cutDays покрывает 300 узлов: " + s.politics.cutDays.length);
        ck(s.knowledge.length >= 300, "Simulation.knowledge покрывает 300 узлов: " + s.knowledge.length);

        ck(s.audit().isEmpty(), "книги Ledger сходятся за 30 дней" + (s.audit().isEmpty() ? "" : ": " + s.audit()));
    }
}
