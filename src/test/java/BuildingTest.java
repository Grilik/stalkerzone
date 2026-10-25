import ru.stalkerzone.sim.*;
import java.util.*;

public class BuildingTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        boolean audit = true;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation s = ZoneScenario.create(seed);
            int ups = 0, downs = 0;
            int[] lvl = new int[4];
            double minAvgHp = 100;
            for (int d = 0; d < 1440; d++) {
                s.step();
                if (!s.audit().isEmpty()) { audit = false; System.out.println("audit fail seed " + seed + " day " + s.day); break; }
            }
            Map<String, double[]> perFaction = new TreeMap<>();
            for (Building b : s.buildings) {
                lvl[b.level]++;
                Node n = s.nodes.get(b.node);
                String o = n.owner < 0 ? "ничья" : s.factions.get(n.owner).name;
                double[] x = perFaction.computeIfAbsent(o, k -> new double[3]);
                x[0] += b.level; x[1] += b.hp; x[2]++;
            }
            StringBuilder sb = new StringBuilder("seed " + seed + ": уровни 0/1/2/3 = " + Arrays.toString(lvl) + " | ");
            perFaction.forEach((k, v) -> sb.append(String.format("%s ср.ур %.1f ср.hp %.0f%%; ", k, v[0] / v[2], v[1] / v[2])));
            System.out.println(sb);
        }
        ck(audit, "аудит сходится на 6 сидах x 1440 дней с постройками");

        Simulation s1 = ZoneScenario.create(5);
        s1.step(400);
        Simulation s2 = Simulation.load(s1.save());
        s1.step(200); s2.step(200);
        ck(s1.save().equals(s2.save()), "сохранение с постройками идентично");

        Simulation s = ZoneScenario.create(2);
        s.step(600);
        System.out.println(Report.buildings(s));
        long ups = s.events.stream().filter(e -> e.contains("улучшена до уровня")).count();
        long downs = s.events.stream().filter(e -> e.contains("разрушена до уровня")).count();
        System.out.println("события (последние 300): улучшений " + ups + ", разрушений " + downs);
        System.exit(f);
    }
}
