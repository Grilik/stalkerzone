import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 051: данные карты КПК (П-08) — снимок PdaSnapshot.build отдаёт отряды в пути (караваны и конвои симуляции)
 * строками «Q» и население узлов строками «U». Экран (П-08) рисует по ним значок группы, стрелку и линию маршрута.
 *
 * Формат (табуляция):
 *   Q  владелец  что_делают  людей  x  z  узел_цели  маршрут(id через ;)  шаг_маршрута
 *      x, z — где отряд сейчас (блоки мира, между узлами route[шаг] и route[шаг+1] или в узле);
 *   U  узел  людей  — сколько людей на занятом узле (NodePopulation.number владельца).
 */
public class PdaSquadsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 4);
        int squads = 0;
        for (int d = 0; d < 40 && squads < 2; d++) { s.step(); squads = s.caravans.size() + s.logistics.convoys.size(); }
        ck(squads >= 1, "за 40 дней в пути есть отряды: " + squads);

        String snap = PdaSnapshot.build(s, "Тестер", s.day, 0, 0, 0, 1000, null, 0);
        List<String[]> q = new ArrayList<>(), u = new ArrayList<>();
        for (String line : snap.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p[0].equals("Q")) q.add(p);
            if (p[0].equals("U")) u.add(p);
        }
        ck(q.size() == squads, "строк Q столько же, сколько отрядов в пути (" + q.size() + " из " + squads + ")");
        boolean fields = true, routeOk = true, posOk = true, targetOk = true;
        for (String[] p : q) {
            if (p.length < 9) { fields = false; continue; }
            int owner = Integer.parseInt(p[1]), people = Integer.parseInt(p[3]);
            int x = Integer.parseInt(p[4]), z = Integer.parseInt(p[5]), target = Integer.parseInt(p[6]), leg = Integer.parseInt(p[8]);
            String[] ids = p[7].split(";");
            if (owner < 0 || owner >= s.factions.size() || people <= 0 || p[2].isBlank()) fields = false;
            int[] r = Arrays.stream(ids).mapToInt(Integer::parseInt).toArray();
            for (int i = 0; i + 1 < r.length; i++) if (s.graph.edgeDays(r[i], r[i + 1]) <= 0) routeOk = false;
            if (r.length == 0 || r[r.length - 1] != target) targetOk = false;
            if (leg < 0 || leg >= r.length) { posOk = false; continue; }
            Node from = s.nodes.get(r[leg]), to = s.nodes.get(r[Math.min(leg + 1, r.length - 1)]);
            int x0 = Math.min(from.x, to.x) * 10, x1 = Math.max(from.x, to.x) * 10;
            int z0 = Math.min(from.z, to.z) * 10, z1 = Math.max(from.z, to.z) * 10;
            if (x < x0 - 1 || x > x1 + 1 || z < z0 - 1 || z > z1 + 1) posOk = false;
        }
        ck(fields, "в строке Q: владелец, что делают, число людей > 0");
        ck(routeOk, "маршрут отряда — по дорогам графа");
        ck(targetOk, "цель отряда — последний узел маршрута");
        ck(posOk, "отряд стоит на текущем отрезке маршрута");

        Faction lon = s.factions.get(ZoneScenario.LONERS);
        boolean village = false;
        for (String[] p : u) if (p.length >= 3 && p[1].equals("0") && Integer.parseInt(p[2]) == NodePopulation.number(s, lon, s.nodes.get(0))) village = true;
        ck(village, "строка U деревни: население = NodePopulation.number");
        int owned = 0;
        for (Node n : s.nodes) if (n.owner >= 0) owned++;
        ck(u.size() == owned, "строк U — по одной на занятый узел (" + u.size() + " из " + owned + ")");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
