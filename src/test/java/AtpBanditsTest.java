import ru.stalkerzone.sim.*;

/**
 * Карточка 057 (решение пользователя 06.11, 23:50): в «Малом Кордоне» бандиты включены только на АТП (узел 5):
 * база бандитов с гарнизоном по уровню базы, без отрядов и захвата точек (Faction.garrisonOnly). Дальний блокпост
 * (узел 11) ничей. Население АТП — от уровня построек базы: NodePopulation.number в пределах
 * [2, NodePopulation.BASE_CAP] и не меньше, чем на уровне ниже. Полная война (kordon_war) не меняется.
 */
public class AtpBanditsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            Faction band = s.factions.get(ZoneScenario.BANDITS);
            ck(band.garrisonOnly, "сид " + seed + ": бандиты — только гарнизон (garrisonOnly)");
            boolean atp = true, others = true, squads = true, pop = true;
            for (int d = 0; d < 60; d++) {
                s.step();
                Node n = s.nodes.get(5);
                if (n.owner != band.id) atp = false;
                else {
                    int p = NodePopulation.number(s, band, n);
                    if (p < 2 || p > NodePopulation.BASE_CAP) pop = false;
                }
                for (Node x : s.nodes) if (x.id != 5 && x.owner == band.id) others = false;
                for (Convoy c : s.logistics.convoys) if (c.owner == band.id) squads = false;
                for (Caravan c : s.caravans) if (c.owner == band.id) squads = false;
            }
            ck(atp, "сид " + seed + ": 60 дней АТП — база бандитов");
            ck(others, "сид " + seed + ": других узлов бандитов нет (дальний блокпост ничей)");
            ck(squads, "сид " + seed + ": у бандитов нет отрядов в пути");
            ck(pop, "сид " + seed + ": население АТП в [2, " + NodePopulation.BASE_CAP + "]");
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит сходится");
        }
        // гарнизон растёт с уровнем базы
        int lo = NodePopulation.garrisonByLevel(0), mid = NodePopulation.garrisonByLevel(1), hi = NodePopulation.garrisonByLevel(3);
        ck(lo >= 2 && lo <= mid && mid < hi && hi <= NodePopulation.BASE_CAP, "гарнизон по уровню базы: " + lo + " ≤ " + mid + " < " + hi);
        Simulation s = Scenarios.create("kordon", 4);
        int lvl = 0;
        for (Building b : s.buildingsAt(5)) lvl = Math.max(lvl, b.level);
        Faction band = s.factions.get(ZoneScenario.BANDITS);
        ck(NodePopulation.number(s, band, s.nodes.get(5)) <= NodePopulation.garrisonByLevel(lvl),
                "население АТП не больше гарнизона её уровня (" + lvl + ")");

        Simulation w = Scenarios.create("kordon_war", 1);
        ck(w.nodes.get(11).owner == ZoneScenario.BANDITS && !w.factions.get(ZoneScenario.BANDITS).garrisonOnly,
                "kordon_war: бандиты на дальнем блокпосте, ИИ включён");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
