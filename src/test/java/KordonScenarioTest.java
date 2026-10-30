import ru.stalkerzone.sim.*;

/** Проверки сценария «Кордон 1.0» (карточка 001). */
public class KordonScenarioTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        nodes();
        gates();
        run();
        persistence();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    static void nodes() {
        Simulation s = Scenarios.create("kordon", 0);
        ck(s.scenario.equals("kordon"), "сценарий запомнен: " + s.scenario);
        ck(Scenarios.create(0).scenario.equals("kordon"), "сценарий по умолчанию — kordon");
        ck(s.nodes.size() == 19, "19 узлов Кордона: " + s.nodes.size());

        String[] names = {"Деревня новичков", "Бункер Сидоровича", "Южный блокпост", "Выход: Большая земля",
                "Элеватор", "АТП", "Выход: Тёмная долина", "Туннель с электрами", "Разрушенный мост",
                "Дыра в заборе", "Свиноферма", "Дальний блокпост", "Выход: Свалка",
                "Холм за элеватором", "Поля у свинофермы", "Лес у дальнего блокпоста",
                "Овраг у АТП", "Луг у фермы", "Опушка у пути на Свалку"};
        NodeType[] types = {NodeType.BASE, NodeType.BASE, NodeType.BASE, NodeType.OUTPOST, NodeType.RUINS, NodeType.BASE,
                NodeType.OUTPOST, NodeType.ANOMALY, NodeType.BASE, NodeType.RUINS, NodeType.SETTLEMENT, NodeType.BASE, NodeType.OUTPOST,
                NodeType.RUINS, NodeType.RUINS, NodeType.RUINS, NodeType.RUINS, NodeType.RUINS, NodeType.RUINS};
        int[] owners = {ZoneScenario.LONERS, ZoneScenario.TRADERS, ZoneScenario.MILITARY, -1, -1, ZoneScenario.BANDITS, -1, -1,
                ZoneScenario.MILITARY, -1, ZoneScenario.LONERS, ZoneScenario.BANDITS, -1,
                -1, -1, -1, -1, -1, -1};
        int[] xs = {0, 12, 27, 28, 21, 61, 96, 0, 27, 55, 1, 30, 30, 32, -10, 18, 72, 15, 18};
        int[] zs = {0, 13, 20, 31, -29, -25, -23, -48, -48, -48, -70, -96, -107, -29, -70, -80, -20, -75, -105};
        boolean[] trader = {false, true, false, false, false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false};

        for (int i = 0; i < 19; i++) {
            Node n = s.nodes.get(i);
            ck(n.name.equals(names[i]), "узел " + i + " имя: " + n.name);
            ck(n.type == types[i], "узел " + i + " тип: " + n.type);
            ck(n.owner == owners[i], "узел " + i + " владелец: " + n.owner);
            ck(n.x == xs[i] && n.z == zs[i], "узел " + i + " координаты (" + n.x + "," + n.z + ")");
            ck(n.trader == trader[i], "узел " + i + " trader=" + trader[i]);
        }
        // v14: 5 стартовых логов по таблице
        ck(s.lairs.size() == 5, "5 стартовых логов: " + s.lairs.size());
        ck(s.lairAt(13) != null && s.lairAt(13).kind == LairKind.DOGS, "холм за элеватором — DOGS");
        ck(s.lairAt(9) != null && s.lairAt(9).kind == LairKind.DOGS, "дыра в заборе — DOGS");
        ck(s.lairAt(14) != null && s.lairAt(14).kind == LairKind.FLESH, "поля у свинофермы — FLESH");
        ck(s.lairAt(15) != null && s.lairAt(15).kind == LairKind.FLESH, "лес у дальнего блокпоста — FLESH");
        ck(s.lairAt(7) != null && s.lairAt(7).kind == LairKind.RODENTS, "туннель с электрами — RODENTS");
    }

    static void gates() {
        int[][] tunnel = {{0, 7}, {7, 10}};
        int[][] bridge = {{4, 8}, {8, 10}};
        int[][] hole = {{5, 9}, {9, 11}};

        Simulation none = Scenarios.create("kordon", 0);
        close(none, tunnel);
        close(none, bridge);
        close(none, hole);
        ck(!none.graph.reachable(0, 10) && !none.graph.reachable(0, 11) && !none.graph.reachable(0, 12),
                "без трёх проходов юг и север не связаны");
        ck(none.graph.reachable(0, 6), "южная часть остаётся связной");
        ck(none.graph.reachable(10, 12), "северная часть остаётся связной");

        Simulation t = Scenarios.create("kordon", 0);
        close(t, bridge);
        close(t, hole);
        ck(t.graph.reachable(0, 12), "через туннель юг и север связаны");
        Simulation b = Scenarios.create("kordon", 0);
        close(b, tunnel);
        close(b, hole);
        ck(b.graph.reachable(0, 12), "через мост юг и север связаны");
        Simulation h = Scenarios.create("kordon", 0);
        close(h, tunnel);
        close(h, bridge);
        ck(h.graph.reachable(0, 12), "через дыру юг и север связаны");

        Simulation all = Scenarios.create("kordon", 0);
        boolean full = true;
        for (int i = 0; i < 19; i++)
            for (int j = 0; j < 19; j++)
                if (i != j && !all.graph.reachable(i, j)) full = false;
        ck(full, "все узлы достижимы без закрытых проходов");
    }

    static void close(Simulation s, int[][] edges) {
        for (int[] e : edges) s.graph.setClosed(e[0], e[1], true);
        s.graph.build();
    }

    static void run() {
        boolean auditOk = true;
        for (long seed : new long[] {1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int d = 0; d < 10; d++) {
                s.step();
                String a = s.audit();
                if (!a.isEmpty()) {
                    auditOk = false;
                    System.out.println("FAIL аудит seed " + seed + " день " + s.day + ":\n" + a);
                    break;
                }
            }
            Faction lon = s.factions.get(ZoneScenario.LONERS);
            Faction ban = s.factions.get(ZoneScenario.BANDITS);
            Faction mil = s.factions.get(ZoneScenario.MILITARY);
            ck(lon.members >= 1 && ban.members >= 1 && mil.members >= 1, "у трёх группировок есть люди (seed " + seed + ")");
            ck(s.ownedCount(lon) >= 1 && s.ownedCount(ban) >= 1 && s.ownedCount(mil) >= 1, "у трёх группировок есть узлы (seed " + seed + ")");
            ck(!s.people.of(ZoneScenario.LONERS).isEmpty() && !s.people.of(ZoneScenario.BANDITS).isEmpty()
                    && !s.people.of(ZoneScenario.MILITARY).isEmpty(), "у трёх группировок есть именные (seed " + seed + ")");
            for (Faction x : s.factions) {
                if (x.id == ZoneScenario.LONERS || x.id == ZoneScenario.BANDITS || x.id == ZoneScenario.MILITARY || x.id == ZoneScenario.TRADERS) continue;
                ck(x.members == 0 && s.ownedCount(x) == 0 && s.people.of(x.id).isEmpty(), "фракция «" + x.name + "» без людей и узлов");
            }
        }
        ck(auditOk, "10 дней без расхождений аудита на трёх сидах");
    }

    static void persistence() {
        Simulation s1 = Scenarios.create("kordon", 7);
        s1.step(5);
        Simulation s2 = Simulation.load(s1.save());
        ck(s2.scenario.equals("kordon"), "после загрузки сценарий kordon: " + s2.scenario);
        ck(s2.nodes.size() == s1.nodes.size(), "число узлов сохранилось: " + s2.nodes.size());
        ck(s2.save().equals(s1.save()), "повторное сохранение идентично");

        Simulation z1 = Scenarios.create("zone", 3);
        z1.step(3);
        Simulation z2 = Simulation.load(z1.save());
        ck(z2.scenario.equals("zone"), "сохранение zone грузится как zone: " + z2.scenario);
        ck(z2.nodes.size() == 29, "у zone 29 узлов: " + z2.nodes.size());
    }
}
