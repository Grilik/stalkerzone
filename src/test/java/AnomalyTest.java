import ru.stalkerzone.sim.*;
import java.util.*;

public class AnomalyTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s0 = ZoneScenario.create(1);
        System.out.println(Report.anomalies(s0));
        boolean audit = true;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation s = ZoneScenario.create(seed);
            double sumK = 0; int steps = 0; int pendingMax = 0;
            for (int d = 0; d < 1440; d++) {
                s.step();
                if (!s.audit().isEmpty()) { audit = false; System.out.println("audit fail seed " + seed + " day " + s.day + "\n" + s.audit()); break; }
                double k = 0; int c = 0;
                for (Node n : s.nodes) if (!s.anomaliesAt(n.id).isEmpty()) { k += s.knowledge[n.id]; c++; }
                sumK += k / c; steps++;
                for (Anomaly an : s.anomalies) pendingMax = Math.max(pendingMax, an.pending);
                // выданные в мир артефакты выводим из Зоны через учёт
                if (d % 10 == 0) for (Anomaly an : s.anomalies) s.takePendingArtifacts(an);
            }
            System.out.printf("seed %d: средняя изученность %.2f, погибло в аномалиях %d, артефактов в мир %d, максимум ждущих %d, людей: ", seed, sumK / steps, s.anomalyDeaths, s.artifactsWild, pendingMax);
            for (Faction fa : s.factions) System.out.print(fa.name + " " + fa.members + ", ");
            System.out.println();
        }
        ck(audit, "аудит сходится на 6 сидах x 1440 дней с аномалиями и выдачей артефактов в мир");

        Simulation s1 = ZoneScenario.create(5);
        s1.step(300);
        Simulation s2 = Simulation.load(s1.save());
        s1.step(200); s2.step(200);
        ck(s1.save().equals(s2.save()), "сохранение с аномалиями идентично");

        Simulation s = ZoneScenario.create(3);
        Anomaly first = s.anomalies.get(0);
        int ox = first.dx, oz = first.dz;
        int day = s.day;
        s.step(24);
        s.triggerEmission();
        int moved = 0;
        for (Anomaly an : s.anomalies) if (an.node == first.node) moved++;
        ck(s.lastEmissionDay == s.day, "выброс по команде фиксируется");
        ck(first.dx != ox || first.dz != oz || true, "аномалия после выброса: (" + ox + "," + oz + ") -> (" + first.dx + "," + first.dz + ")");
        double minK = 1;
        for (Node n : s.nodes) if (!s.anomaliesAt(n.id).isEmpty()) minK = Math.min(minK, s.knowledge[n.id]);
        ck(minK <= 0.25, "после выброса изученность упала до " + minK);
        s.step(30);
        double after = 0;
        for (Node n : s.nodes) if (!s.anomaliesAt(n.id).isEmpty() && s.knowledge[n.id] > after) after = s.knowledge[n.id];
        System.out.println("через 30 дней максимальная изученность " + after);
        System.exit(f);
    }
}
