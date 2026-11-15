import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 044: узлы «Малого Кордона» — 19 «Лагерь у вагончика» (О7), 20 «Свалка техники» (А6/О12),
 * 21 «Лес у свалки техники» (логово Л3) — в конце KordonScenario; версия сохранения +1, старое сохранение грузится.
 */
public class KordonSmallNodesTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static boolean contains(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 7);
        ck(s.nodes.size() >= 22, "узлов Кордона не меньше 22 (было 19; 059 добавит 22–23): " + s.nodes.size());
        if (s.nodes.size() < 22) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }

        Node o7 = s.nodes.get(19), a6 = s.nodes.get(20), l3 = s.nodes.get(21);
        ck(o7.name.contains("вагончик") && o7.type == NodeType.OUTPOST && o7.x == 12 && o7.z == -7,
                "19 «Лагерь у вагончика»: OUTPOST в (12, −7)");
        ck(a6.name.contains("Свалка техники") && a6.type == NodeType.ANOMALY && a6.x == -4 && a6.z == -12,
                "20 «Свалка техники»: ANOMALY в (−4, −12)");
        ck(l3.type == NodeType.RUINS && l3.den != null,
                "21 — логово Л3: RUINS с местом логова (место и название — карточка 060)");
        ck(o7.owner == ZoneScenario.LONERS, "О7 — застава одиночек с начала");
        ck(a6.owner == ZoneScenario.LONERS, "20 — застава одиночек О12 на краю свалки с начала");
        ck(l3.owner < 0, "Л3 на старте ничей");
        ck(contains(KordonScenario.LAIR_SITES, 21), "21 — место логова (LAIR_SITES)");
        ck(contains(s.lairNoCapture, 21), "21 не захватывается группировками");
        ck(s.graph.open(0, 19) && s.graph.open(0, 20) && s.graph.open(20, 21), "дороги: деревня—19, деревня—20, 20—21");
        ck(KordonBalance.RAID_TARGET_WEIGHT.length >= s.nodes.size(), "веса целей набегов по числу узлов");
        for (int i = 0; i < 19; i++) {
            Node n = s.nodes.get(i);
            if (i == 0) ck(n.name.equals("Деревня новичков"), "номера старых узлов не сдвинулись");
        }

        // ход дней на новых узлах не падает
        boolean stepped = true;
        try { for (int d = 0; d < 40; d++) s.step(); } catch (RuntimeException e) { stepped = false; e.printStackTrace(); }
        ck(stepped, "40 дней симуляции без исключений");

        // текущее сохранение: туда и обратно без потерь
        String save = s.save();
        Simulation back = Simulation.load(save);
        ck(back.nodes.size() == s.nodes.size() && back.day == s.day && back.save().equals(save), "сохранение v19 грузится без потерь");

        // старое сохранение (версия 18, 19 узлов): грузится — Кордон начинается заново с тем же сидом
        byte[] raw = Base64.getDecoder().decode(save);
        int version = ((raw[0] & 255) << 24) | ((raw[1] & 255) << 16) | ((raw[2] & 255) << 8) | (raw[3] & 255);
        ck(version >= 19, "версия сохранения поднята до 19 (059 — до 20): " + version);
        raw[0] = 0; raw[1] = 0; raw[2] = 0; raw[3] = 18;
        Simulation old = null;
        try { old = Simulation.load(Base64.getEncoder().encodeToString(raw)); } catch (RuntimeException e) { e.printStackTrace(); }
        ck(old != null && old.nodes.size() == s.nodes.size() && old.seed == s.seed && old.day == 0,
                "сохранение v18 Кордона грузится как новый мир с тем же сидом");

        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
