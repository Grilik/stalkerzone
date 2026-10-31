import ru.stalkerzone.sim.*;
import java.util.*;

/**
 * Темп развития группировок Кордона (карточка 034): 24 дня сценария kordon, 6 сидов.
 * Проверяет, что за это время Зона заметно живёт, а не «промотана»: у каждой основной группировки
 * есть улучшение постройки, идут попытки захвата узлов и смены владельца, население растёт,
 * закрываются контракты. Цели сравниваются средним по сидам.
 */
public class KordonPaceTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        int seeds = 6;
        int days = 24;

        double[] upL = new double[seeds], upB = new double[seeds], upM = new double[seeds];
        double[] popL = new double[seeds], popB = new double[seeds], popM = new double[seeds];
        int[] wars = new int[seeds], ownerChanges = new int[seeds], contracts = new int[seeds];
        int auditFails = 0;

        for (int s = 0; s < seeds; s++) {
            long seed = s * 7 + 1;
            Simulation sim = Scenarios.create("kordon", seed);
            Faction lon = sim.factions.get(ZoneScenario.LONERS);
            Faction ban = sim.factions.get(ZoneScenario.BANDITS);
            Faction mil = sim.factions.get(ZoneScenario.MILITARY);
            int startL = lon.members, startB = ban.members, startM = mil.members;

            // стартовые уровни построек и владельцы узлов
            int[] lvl = new int[sim.buildings.size()];
            for (int i = 0; i < lvl.length; i++) lvl[i] = sim.buildings.get(i).level;
            int[] owners = new int[sim.nodes.size()];
            for (Node n : sim.nodes) owners[n.id] = n.owner;

            for (int d = 1; d <= days; d++) {
                sim.step();

                String audit = sim.audit();
                if (!audit.isEmpty()) {
                    auditFails++;
                    System.out.println("FAIL аудит сид " + seed + " день " + d + ":\n" + audit);
                }

                for (int i = 0; i < sim.buildings.size(); i++) {
                    Building b = sim.buildings.get(i);
                    if (b.level > lvl[i]) {
                        int o = sim.nodes.get(b.node).owner;
                        if (o == ZoneScenario.LONERS) upL[s]++;
                        else if (o == ZoneScenario.BANDITS) upB[s]++;
                        else if (o == ZoneScenario.MILITARY) upM[s]++;
                    }
                    lvl[i] = b.level;
                }

                for (Node n : sim.nodes) {
                    if (n.owner != owners[n.id]) { ownerChanges[s]++; owners[n.id] = n.owner; }
                }
            }

            for (Faction fa : sim.factions) wars[s] += fa.warsStarted;
            contracts[s] = sim.contracts.doneByNpc + sim.contracts.doneByPlayer;
            popL[s] = (double) lon.members / startL;
            popB[s] = (double) ban.members / startB;
            popM[s] = (double) mil.members / startM;

            System.out.println("сид " + seed + ": улучшения Л/Б/В=" + (int) upL[s] + "/" + (int) upB[s] + "/" + (int) upM[s]
                    + ", штурмы=" + wars[s] + ", смены владельца=" + ownerChanges[s]
                    + ", контракты=" + contracts[s]
                    + ", население Л/Б/В=" + String.format("%.2f", popL[s]) + "/" + String.format("%.2f", popB[s])
                    + "/" + String.format("%.2f", popM[s]));
        }

        ck(auditFails == 0, "закон сохранения (audit) не нарушен за " + seeds + " сидов × " + days + " дней");

        ck(mean(upL) >= 1, "одиночки: улучшений построек в среднем " + fmt(mean(upL)) + " (нужно ≥1)");
        ck(mean(upB) >= 1, "бандиты: улучшений построек в среднем " + fmt(mean(upB)) + " (нужно ≥1)");
        ck(mean(upM) >= 1, "военные: улучшений построек в среднем " + fmt(mean(upM)) + " (нужно ≥1)");

        ck(mean(wars) >= 2, "попыток захвата узлов в среднем " + fmt(mean(wars)) + " (нужно ≥2)");
        ck(mean(ownerChanges) >= 1, "смен владельца узла в среднем " + fmt(mean(ownerChanges)) + " (нужно ≥1)");

        ck(mean(popL) >= 1.15, "одиночки: население выросло до " + pct(mean(popL)) + " (нужно ≥115%)");
        ck(mean(popB) >= 1.15, "бандиты: население выросло до " + pct(mean(popB)) + " (нужно ≥115%)");
        ck(mean(popM) >= 1.15, "военные: население выросло до " + pct(mean(popM)) + " (нужно ≥115%)");

        ck(mean(contracts) >= 8, "выполненных контрактов в среднем " + fmt(mean(contracts)) + " (нужно ≥8)");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    static double mean(double[] x) {
        double t = 0;
        for (double v : x) t += v;
        return t / x.length;
    }

    static double mean(int[] x) {
        double t = 0;
        for (int v : x) t += v;
        return t / x.length;
    }

    static String fmt(double v) { return String.format("%.2f", v); }

    static String pct(double v) { return String.format("%.0f%%", v * 100); }
}
