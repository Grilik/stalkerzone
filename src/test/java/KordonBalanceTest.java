import ru.stalkerzone.sim.*;

/** Проверки баланса Кордона (карточка 005): 6 сидов × 120 дней. */
public class KordonBalanceTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        int seeds = 6;
        int days = 120;
        int elevatorChanges = 0;
        int[] maxMilDays = {0, 0, 0};           // макс подряд дней владения деревней, АТП, фермой
        int[] curMilDays = {0, 0, 0};           // текущие подряд дни
        int[] elevatorSwitch = new int[seeds];

        for (int s = 0; s < seeds; s++) {
            long seed = s * 7 + 1;
            System.out.println("--- сид " + seed + " (" + (s + 1) + "/" + seeds + ") ---");
            Simulation sim = Scenarios.create("kordon", seed);

            int lonerStart = sim.factions.get(ZoneScenario.LONERS).members;
            int banditStart = sim.factions.get(ZoneScenario.BANDITS).members;
            int militaryStart = sim.factions.get(ZoneScenario.MILITARY).members;

            boolean allHaveNodesByDay60 = true;
            int elevatorPrevOwner = sim.nodes.get(4).owner;
            elevatorSwitch[s] = 0;

            for (int d = 1; d <= days; d++) {
                sim.step();

                // аудит каждые 10 дней
                if (d % 10 == 0) {
                    String audit = sim.audit();
                    if (!audit.isEmpty()) {
                        System.out.println("FAIL аудит seed " + seed + " день " + d + ":\n" + audit);
                        f++;
                    }
                }

                // потеря всех узлов до 60-го дня
                if (d <= 60) {
                    Faction lon = sim.factions.get(ZoneScenario.LONERS);
                    Faction ban = sim.factions.get(ZoneScenario.BANDITS);
                    Faction mil = sim.factions.get(ZoneScenario.MILITARY);
                    if (sim.ownedCount(lon) == 0 || sim.ownedCount(ban) == 0 || sim.ownedCount(mil) == 0) {
                        allHaveNodesByDay60 = false;
                        System.out.println("  день " + d + ": Л=" + sim.ownedCount(lon) + " Б=" + sim.ownedCount(ban) + " В=" + sim.ownedCount(mil));
                    }
                }

                // владение военными деревней (0), АТП (5), фермой (10)
                int[] checkNodes = {0, 5, 10};
                for (int mi = 0; mi < 3; mi++) {
                    int n = checkNodes[mi];
                    if (sim.nodes.get(n).owner == ZoneScenario.MILITARY) {
                        curMilDays[mi]++;
                        if (curMilDays[mi] > maxMilDays[mi]) maxMilDays[mi] = curMilDays[mi];
                    } else {
                        curMilDays[mi] = 0;
                    }
                }

                // смена владельца элеватора (4)
                int eOwner = sim.nodes.get(4).owner;
                if (eOwner != elevatorPrevOwner && eOwner >= 0) {
                    elevatorSwitch[s] = 1;
                }
                elevatorPrevOwner = eOwner;
            }

            ck(allHaveNodesByDay60, "сид " + seed + ": все три фракции имеют узлы до 60-го дня");

            // численность на 120-й день
            Faction lon = sim.factions.get(ZoneScenario.LONERS);
            Faction ban = sim.factions.get(ZoneScenario.BANDITS);
            Faction mil = sim.factions.get(ZoneScenario.MILITARY);
            double lonRatio = (double) lon.members / lonerStart;
            double banRatio = (double) ban.members / banditStart;
            double milRatio = (double) mil.members / militaryStart;
            ck(lonRatio >= 0.3 && lonRatio <= 3.0,
                    "сид " + seed + ": одиночки " + lon.members + " (" + String.format("%.0f", lonRatio * 100) + "% от " + lonerStart + ")");
            ck(banRatio >= 0.3 && banRatio <= 3.0,
                    "сид " + seed + ": бандиты " + ban.members + " (" + String.format("%.0f", banRatio * 100) + "% от " + banditStart + ")");
            ck(milRatio >= 0.3 && milRatio <= 3.0,
                    "сид " + seed + ": военные " + mil.members + " (" + String.format("%.0f", milRatio * 100) + "% от " + militaryStart + ")");

            if (elevatorSwitch[s] > 0) elevatorChanges++;
        }

        ck(elevatorChanges >= 3, "элеватор сменил владельца в " + elevatorChanges + " сидах из " + seeds + " (нужно ≥3)");
        ck(maxMilDays[0] <= 10, "военные не владели деревней дольше 10 дней подряд (макс " + maxMilDays[0] + ")");
        ck(maxMilDays[1] <= 10, "военные не владели АТП дольше 10 дней подряд (макс " + maxMilDays[1] + ")");
        ck(maxMilDays[2] <= 10, "военные не владели фермой дольше 10 дней подряд (макс " + maxMilDays[2] + ")");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}