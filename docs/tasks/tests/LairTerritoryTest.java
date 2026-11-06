import ru.stalkerzone.sim.*;

/**
 * Карточка 061: территория логова — пустырь, где бродят мутанты логова (LairTerritory, чистая Java).
 * Круг вокруг логова (радиус по виду: псы 75, плоти 60, прочие 50 блоков) плюс «вытянутая» часть
 * (Node.reach — ломаная в блоках, ширина Node.reachWidth), минус зоны людей: у каждого занятого узла
 * круг по типу (BASE 70, SETTLEMENT 50, прочие 30 блоков), кроме узла самого логова.
 */
public class LairTerritoryTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        ck(LairTerritory.radius(LairKind.DOGS) == 75 && LairTerritory.radius(LairKind.FLESH) == 60
                && LairTerritory.radius(LairKind.BLOODSUCKERS) == 50, "радиус территории: псы 75, плоти 60, прочие 50");
        Simulation s = Scenarios.create("kordon", 1);
        ck(LairTerritory.humanZone(s, 0, 0, -1) && LairTerritory.humanZone(s, 330, -120, -1), "деревня и АТП — зоны людей");
        ck(!LairTerritory.humanZone(s, 45, -175, -1), "Собачий лес (45, −175) — не зона людей");

        for (Lair l : s.lairs) {
            int[] d = Lair.denXZ(s.nodes.get(l.node));
            int r = LairTerritory.radius(l.kind);
            ck(LairTerritory.contains(s, l, d[0], d[1]), "логово узла " + l.node + " — внутри своей территории");
            ck(!LairTerritory.contains(s, l, d[0] + r + 6, d[1]) || s.nodes.get(l.node).reach != null,
                    "узел " + l.node + ": за радиусом — не территория");
            boolean clean = true;
            for (Node n : s.nodes) {
                if (n.id == l.node || n.owner < 0) continue;
                if (LairTerritory.contains(s, l, n.x * 10, n.z * 10)) { clean = false; System.out.println("  в территории узла " + l.node + ": " + n.name); }
            }
            ck(clean, "узел " + l.node + ": ни одна база или застава людей не внутри территории");
        }
        Lair dogs21 = s.lairAt(21);
        if (dogs21 != null) {
            ck(LairTerritory.contains(s, dogs21, 45, -120), "Собачий лес: поле к северу от деревни (45, −120) — территория");
            ck(!LairTerritory.contains(s, dogs21, 120, -70), "Собачий лес: О7 — не территория");
        }
        Node hole = s.nodes.get(9);
        ck(hole.reach != null && hole.reach.length >= 2 && hole.reachWidth >= 20, "Дыра в заборе: территория вытянута (Node.reach)");
        Lair h = s.lairAt(9);
        if (h != null && hole.reach != null) {
            ck(LairTerritory.contains(s, h, 340, -200) && LairTerritory.contains(s, h, 275, -172),
                    "Дыра: вытянутая часть к старому месту холма — (340, −200) и (275, −172)");
            ck(!LairTerritory.contains(s, h, 330, -125), "Дыра: АТП (зона бандитов) — не территория");
            ck(!LairTerritory.contains(s, h, 200, 45), "Дыра не соединена с Лысым холмом");
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
