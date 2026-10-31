import ru.stalkerzone.sim.*;
import java.util.*;

/** Проверки трёх проходов через насыпь Кордона (карточка 003). */
public class CrossingsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static final int TUNNEL = 7, BRIDGE = 8, HOLE = 9;

    public static void main(String[] a) {
        access();
        passChoice();
        eachPassUsed();
        tolls();
        tollFormula();
        persistence();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Сценарий Кордона, у которого северные точки не отданы мутантам: между югом и севером ходит транспорт. */
    static Simulation sim(long seed) {
        Simulation s = Scenarios.create("kordon", seed);
        for (int ln : new int[]{TUNNEL, HOLE, 14, 15}) {
            Lair l = s.lairAt(ln);
            if (l != null) s.mutantKilled(l, 100, false);
        }
        return s;
    }

    static boolean routeHas(int[] route, int node) {
        for (int n : route) if (n == node) return true;
        return false;
    }

    /** Допуск на мосту: врагов владельца не пускают; при смене владельца правило меняется. */
    static void access() {
        boolean banditOnBridge = false;
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = sim(seed);
            Faction ban = s.factions.get(ZoneScenario.BANDITS);
            Faction mil = s.military();
            ck(s.crossingBlocked(ban, BRIDGE), "бандитам закрыт мост военных (seed " + seed + ")");
            ck(!s.crossingBlocked(ban, TUNNEL) && !s.crossingBlocked(ban, HOLE),
                    "туннель и дыра открыты бандитам (seed " + seed + ")");
            ck(!s.crossingBlocked(mil, BRIDGE), "военным открыт их собственный мост (seed " + seed + ")");
            int[] r = s.route(ban, 2, 10, new Random(seed));
            ck(!routeHas(r, BRIDGE), "маршрут бандитов с юга на север не идёт через мост: " + Arrays.toString(r));
            for (int d = 0; d < 10; d++) {
                s.step();
                if (s.nodes.get(BRIDGE).owner != ZoneScenario.MILITARY) continue;
                for (Convoy c : s.logistics.convoys)
                    if (c.owner == ZoneScenario.BANDITS && routeHas(c.route, BRIDGE)) banditOnBridge = true;
                for (Caravan c : s.caravans)
                    if (c.owner == ZoneScenario.BANDITS && routeHas(c.route, BRIDGE)) banditOnBridge = true;
            }
        }
        ck(!banditOnBridge, "бандиты за 10 дней ни разу не прошли через мост, пока он у военных");

        // мост перешёл к бандитам — теперь военных через него не пускают
        Simulation s = sim(1);
        s.nodes.get(BRIDGE).owner = ZoneScenario.BANDITS;
        Faction mil = s.military();
        ck(s.crossingBlocked(mil, BRIDGE), "после перехода моста к бандитам военным он закрыт");
        int[] r = s.route(mil, 2, 10, new Random(3));
        ck(!routeHas(r, BRIDGE), "маршрут военных больше не идёт через чужой мост: " + Arrays.toString(r));
        boolean milOnBridge = false;
        for (int d = 0; d < 10; d++) {
            s.step();
            if (s.nodes.get(BRIDGE).owner != ZoneScenario.BANDITS) continue;
            for (Convoy c : s.logistics.convoys)
                if (c.owner == ZoneScenario.MILITARY && routeHas(c.route, BRIDGE)) milOnBridge = true;
        }
        ck(!milOnBridge, "военные за 10 дней не прошли через мост, принадлежащий бандитам");
    }

    /** Богатые и осторожные чаще идут через мост, бедные и смелые — через туннель/дыру. */
    static void passChoice() {
        Simulation s = sim(1);
        s.step();
        Faction lon = s.factions.get(ZoneScenario.LONERS);
        lon.stock.money = 100000;
        double richBridge = share(s, lon, 0, 10, BRIDGE);
        double richTunnel = share(s, lon, 0, 10, TUNNEL);
        lon.stock.money = 0;
        double poorBridge = share(s, lon, 0, 10, BRIDGE);
        double poorTunnel = share(s, lon, 0, 10, TUNNEL);
        System.out.printf("     одиночка 0->10: богатый мост %.2f / туннель %.2f, бедный мост %.2f / туннель %.2f%n",
                richBridge, richTunnel, poorBridge, poorTunnel);
        ck(richBridge > poorBridge, "богатые чаще выбирают мост");
        ck(poorTunnel > richTunnel, "бедные чаще выбирают туннель");

        Faction ban = s.factions.get(ZoneScenario.BANDITS);
        double banBridge = share(s, ban, 5, 11, BRIDGE);
        double banHole = share(s, ban, 5, 11, HOLE);
        ck(banBridge == 0, "бандитам мост недоступен при выборе прохода");
        ck(banHole > 0 && share(s, ban, 5, 11, TUNNEL) > 0, "бандиты используют дыру и туннель");
    }

    /** Доля маршрутов from-to, проходящих через заданный проход (по многим броскам). */
    static double share(Simulation s, Faction fac, int from, int to, int pass) {
        int c = 0, n = 400;
        for (int i = 0; i < n; i++)
            if (routeHas(s.route(fac, from, to, new Random(i * 131L + 7)), pass)) c++;
        return c / (double) n;
    }

    /** За 10 дней задействованы все три прохода. */
    static void eachPassUsed() {
        boolean t = false, b = false, h = false;
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = sim(seed);
            for (int d = 0; d < 10; d++) s.step();
            System.out.println("     seed " + seed + ": туннель=" + s.crossingUse[TUNNEL]
                    + " мост=" + s.crossingUse[BRIDGE] + " дыра=" + s.crossingUse[HOLE]);
            if (s.crossingUse[TUNNEL] > 0) t = true;
            if (s.crossingUse[BRIDGE] > 0) b = true;
            if (s.crossingUse[HOLE] > 0) h = true;
        }
        ck(t, "туннель используется хотя бы раз за 10 дней");
        ck(b, "мост используется хотя бы раз за 10 дней");
        ck(h, "дыра используется хотя бы раз за 10 дней");
    }

    /** Пошлина идёт военным, деньги в Зоне сохраняются. */
    static void tolls() {
        Simulation s = sim(1);
        Faction lon = s.factions.get(ZoneScenario.LONERS);
        Faction mil = s.military();
        long mil0 = mil.stock.money;
        long total0 = s.totalMoney();
        s.crossPass(lon, BRIDGE, lon.stock);
        long toll = s.tollCollected;
        ck(toll > 0, "одиночка заплатил пошлину за мост: " + toll + " руб.");
        ck(mil.stock.money == mil0 + toll, "деньги военных выросли ровно на пошлину (" + mil0 + " -> " + mil.stock.money + ")");
        ck(s.totalMoney() == total0, "общая сумма денег в Зоне сохранилась");
        ck(s.audit().isEmpty(), "аудит сходится после пошлины");

        long mil1 = mil.stock.money;
        s.crossPass(mil, BRIDGE, mil.stock);
        ck(mil.stock.money == mil1, "военные проходят свой мост без пошлины");

        long live = 0;
        boolean audit = true;
        for (long seed : new long[]{1, 2, 3}) {
            Simulation t = sim(seed);
            for (int d = 0; d < 10; d++) {
                t.step();
                if (!t.audit().isEmpty()) { audit = false; break; }
            }
            live += t.tollCollected;
        }
        ck(live > 0, "за 10 дней на трёх сидах на мосту собрана пошлина: " + live + " руб.");
        ck(audit, "аудит сходится с пошлинами 10 дней на трёх сидах");
    }

    /** Пошлина для игрока: хорошая репутация и звание дешевле. */
    static void tollFormula() {
        Simulation s = sim(1);
        long novice = s.tollFor(0, 0);
        long veteran = s.tollFor(50, 4);
        long hostile = s.tollFor(-40, 0);
        System.out.println("     пошлина: новичок " + novice + ", ветеран с репутацией " + veteran + ", враг " + hostile);
        ck(veteran < novice, "одиночке с хорошей репутацией и высоким званием пошлина меньше, чем новичку");
        ck(hostile > novice, "плохая репутация повышает пошлину");
        ck(veteran > 0 && novice > 0, "пошлина всегда положительна");
    }

    static void persistence() {
        Simulation s1 = sim(7);
        s1.step(5);
        Simulation s2 = Simulation.load(s1.save());
        ck(s2.save().equals(s1.save()), "сохранение с проходами идентично");
        ck(s2.crossingNodes.length == 3 && s2.tollCrossing == BRIDGE, "после загрузки проходы восстановлены из сценария");
    }
}
