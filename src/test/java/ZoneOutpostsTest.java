import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 059 (решение пользователя 06.11, 18:05): аномальные зоны без уровней — детализированный объект,
 * рядом с каждой зоной застава одиночек. А6 «Свалка техники» (узел 20) — застава О12 на том же узле;
 * «Туннель с электрами» (узел 7, под ж/д насыпью) — 2 заставы, по одной у каждого входа: узлы 22 (южный вход,
 * со стороны деревни) и 23 (северный, со стороны свинофермы) в конце KordonScenario.
 * Таблица KordonScenario.ZONE_OUTPOSTS: {зона, застава, застава…}.
 */
public class ZoneOutpostsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 1);
        ck(s.nodes.size() == 24, "узлов Кордона 24: " + s.nodes.size());
        if (s.nodes.size() < 24) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }
        int[][] zo = KordonScenario.ZONE_OUTPOSTS;
        int[] tunnel = null, dump = null;
        for (int[] z : zo) { if (z[0] == 7) tunnel = z; if (z[0] == 20) dump = z; }
        ck(dump != null && dump.length >= 2, "у А6 (узел 20) есть застава");
        ck(tunnel != null && tunnel.length == 3, "у туннеля (узел 7) ровно 2 заставы");
        for (int[] z : zo) {
            ck(s.nodes.get(z[0]).type == NodeType.ANOMALY, "зона " + z[0] + " — тип «аномальная зона»");
            for (int i = 1; i < z.length; i++) {
                Node o = s.nodes.get(z[i]);
                ck(o.owner == ZoneScenario.LONERS, "застава " + o.id + " «" + o.name + "» — одиночки");
                ck(o.id == z[0] || s.graph.open(o.id, z[0]), "застава " + o.id + " на дороге к зоне " + z[0]);
            }
        }
        if (tunnel != null && tunnel.length == 3) {
            Node t = s.nodes.get(7), s22 = s.nodes.get(22), n23 = s.nodes.get(23);
            ck(tunnel[1] == 22 && tunnel[2] == 23, "заставы туннеля — узлы 22 и 23");
            ck(s22.type == NodeType.OUTPOST && n23.type == NodeType.OUTPOST, "22 и 23 — OUTPOST");
            ck(s22.z > t.z && n23.z < t.z, "22 южнее туннеля, 23 севернее (по разные стороны насыпи)");
            ck(s.graph.open(22, 0) && s.graph.open(23, 10), "22 — дорога к деревне, 23 — к свиноферме");
            ck(!s.graph.open(22, 10) && !s.graph.open(23, 0), "заставы не соединены через насыпь мимо туннеля");
            ck(Math.abs(s22.x - t.x) <= 3 && Math.abs(n23.x - t.x) <= 3 && t.z - n23.z <= 5 && s22.z - t.z <= 5,
                    "заставы у входов туннеля (не дальше 50 блоков)");
        }
        for (int i = 0; i < 22; i++) if (i == 7) ck(s.nodes.get(7).name.contains("Туннель"), "номера прежних узлов не сдвинулись");

        // уровень зоны не меняется: построек (а значит и уровня) у зон нет весь срок
        boolean noLevel = true;
        for (long seed = 1; seed <= 3; seed++) {
            Simulation t = Scenarios.create("kordon", seed);
            for (int d = 0; d < 60; d++) {
                t.step();
                for (int[] z : zo) if (!t.buildingsAt(z[0]).isEmpty()) noLevel = false;
            }
        }
        ck(noLevel, "60 дней × 3 сида: у аномальных зон нет построек и уровня");

        String save = s.save();
        byte[] raw = Base64.getDecoder().decode(save);
        int version = ((raw[0] & 255) << 24) | ((raw[1] & 255) << 16) | ((raw[2] & 255) << 8) | (raw[3] & 255);
        ck(version == 20, "версия сохранения 20: " + version);
        raw[3] = 19;
        Simulation old = null;
        try { old = Simulation.load(Base64.getEncoder().encodeToString(raw)); } catch (RuntimeException e) { e.printStackTrace(); }
        ck(old != null && old.nodes.size() == 24 && old.day == 0, "сохранение v19 Кордона — новый мир с тем же сидом");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
