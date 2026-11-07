import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 058 (решение пользователя 06.11, 23:50): бандиты АТП враждебны одиночкам (и игроку — в мире): вражда
 * в обе стороны не ниже Crossings.ENEMY весь срок, перемирий нет. Маршруты (Simulation.route) не идут через базу
 * вражеского неподвижного гарнизона (Faction.garrisonOnly — АТП бандитов для одиночек), если есть обход;
 * в полной войне (kordon_war, гарнизонов нет) маршруты прежние. Контракт «Разведка АТП» (Contract.Type.SCOUT — новое значение
 * в конце enum): одиночки платят игроку за разведку базы бандитов (c.node = 5); NPC его не берут;
 * ContractBoard.scouted(s, id, игрок) — разведка выполнена, оплата из залога.
 */
public class BanditHostilityTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    /** Есть ли путь from → to, не заходя в узел avoid. */
    static boolean reachableAvoiding(Simulation s, int from, int to, int avoid) {
        boolean[] seen = new boolean[s.nodes.size()];
        Deque<Integer> q = new ArrayDeque<>(List.of(from));
        seen[from] = true;
        while (!q.isEmpty()) {
            int u = q.poll();
            if (u == to) return true;
            for (int v = 0; v < s.nodes.size(); v++)
                if (!seen[v] && v != avoid && s.graph.open(u, v)) { seen[v] = true; q.add(v); }
        }
        return false;
    }

    public static void main(String[] a) {
        Contract.Type[] all = Contract.Type.values();
        ck(all[all.length - 1].name().equals("SCOUT"), "SCOUT — последнее значение Contract.Type");
        Contract.Type scout = all[all.length - 1];
        int found = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            Faction band = s.factions.get(ZoneScenario.BANDITS), lon = s.factions.get(ZoneScenario.LONERS);
            boolean hostile = true, routes = true, npcTook = false;
            Contract c = null;
            Random r = new Random(seed);
            for (int d = 0; d < 60; d++) {
                s.step();
                if (s.hostilityTo(band, lon.id) < Crossings.ENEMY || s.hostilityTo(lon, band.id) < Crossings.ENEMY) hostile = false;
                if (s.nodes.get(5).owner == band.id) {
                    for (int t = 0; t < s.nodes.size(); t++) {
                        if (t == 5 || t == 0 || !reachableAvoiding(s, 0, t, 5)) continue;
                        for (int v : s.route(lon, 0, t, r)) if (v == 5) { routes = false; System.out.println("  через АТП: 0 → " + t); }
                    }
                }
                for (Contract x : s.contracts.list) {
                    if (x.type != scout) continue;
                    if (x.taker != Contract.NOBODY && x.taker != Contract.PLAYER) npcTook = true;
                    if (c == null && x.status == Contract.Status.OPEN) c = x;
                }
                if (c != null) break;
            }
            ck(hostile, "сид " + seed + ": бандиты и одиночки — враги весь срок");
            ck(routes, "сид " + seed + ": маршруты одиночек из деревни обходят АТП");
            ck(!npcTook, "сид " + seed + ": NPC не берут «Разведку АТП»");
            if (c == null) continue;
            found++;
            ck(c.issuer == lon.id && c.node == 5 && c.escrow > 0, "сид " + seed + ": заказ одиночек на разведку АТП, награда в залоге");
            ck(s.contracts.take(s, c.id, "Тестер") == null, "сид " + seed + ": игрок взял заказ");
            ContractBoard.Reward rw = s.contracts.scouted(s, c.id, "Тестер");
            ck(rw != null && rw.money() > 0 && c.status == Contract.Status.DONE, "сид " + seed + ": разведка выполнена — оплата игроку");
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит сходится");
        }
        ck(found >= 2, "«Разведка АТП» выставлялась хотя бы в 2 сидах из 3: " + found);
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
