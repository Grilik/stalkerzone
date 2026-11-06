import ru.stalkerzone.sim.*;
import ru.stalkerzone.zonemap.build.*;

import java.util.*;

/**
 * Карточка 060 (решение пользователя 06.11, 22:50–23:20): перенос логов.
 * - Узел 21 «Собачий лес» (бывш. «Лес у свалки техники»): в лесу между деревней, О7, А6/О12 и туннелем,
 *   узел (5, −17), логово псов у (45, −175); площадка lair — там же.
 * - Узел 13 «Лысый холм» (бывш. «Холм за элеватором»): между О7, АТП и южным блокпостом, узел (20, 5), логово у (200, 45);
 *   дороги к О7 и южному блокпосту, к элеватору — больше нет.
 * - Логова тушканов в туннеле нет: туннель (узел 7) — аномальная зона с двумя заставами, логово там не заводится никогда.
 * - Версия сохранения 21.
 */
public class LairMoveTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }
    static boolean has(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 1);
        Node l3 = s.nodes.get(21), hill = s.nodes.get(13);
        ck(l3.name.equals("Собачий лес") && l3.x == 5 && l3.z == -17, "21 «Собачий лес» в (5, −17): " + l3.name + " " + l3.x + "," + l3.z);
        ck(l3.den != null && l3.den[0] == 45 && l3.den[1] == -175, "логово Собачьего леса у (45, −175)");
        ck(hill.name.equals("Лысый холм") && hill.x == 20 && hill.z == 5, "13 «Лысый холм» в (20, 5): " + hill.name + " " + hill.x + "," + hill.z);
        ck(hill.den != null && hill.den[0] == 200 && hill.den[1] == 45, "логово Лысого холма у (200, 45)");
        ck(s.graph.open(21, 0) && s.graph.open(21, 19) && s.graph.open(21, 20) && s.graph.open(21, 22),
                "Собачий лес — дороги к деревне, О7, А6/О12 и южной заставе туннеля");
        ck(s.graph.open(13, 19) && s.graph.open(13, 2) && s.graph.edgeDays(13, 4) <= 0,
                "Лысый холм — дороги к О7 и южному блокпосту, к элеватору — нет");

        ck(s.lairAt(7) == null, "в новом мире у туннеля логова нет");
        boolean rodentDef = false;
        for (KordonBalance.LairDef d : KordonBalance.START_LAIRS) if (d.node == 7 || d.kind == LairKind.RODENTS) rodentDef = true;
        ck(!rodentDef, "тушканов нет среди стартовых логов");
        ck(!has(KordonScenario.LAIR_SITES, 7), "туннель — не место логова (LAIR_SITES)");
        ck(s.lairAt(13) != null && s.lairAt(13).kind == LairKind.DOGS && s.lairAt(21) != null && s.lairAt(21).kind == LairKind.DOGS,
                "на Лысом холме и в Собачьем лесу — слепые псы");

        boolean tunnelLair = false;
        for (long seed = 1; seed <= 3; seed++) {
            Simulation t = Scenarios.create("kordon", seed);
            for (int d = 0; d < 120; d++) { t.step(); if (t.lairAt(7) != null) tunnelLair = true; }
        }
        ck(!tunnelLair, "120 дней × 3 сида: у туннеля логово не появляется");

        // площадка логова Собачьего леса
        ck(Prefabs.LAIR_L3[0] == 45 && Prefabs.LAIR_L3[1] == -175, "Prefabs.LAIR_L3 = (45, −175)");
        Site lair = Prefabs.site("l01_escape", 21);
        ck(lair != null && lair.covers(45, -175) && lair.covers(50, -170), "площадка lair на новом месте и покрывает центр узла 21");
        for (Site o : Prefabs.sites("l01_escape"))
            if (o != lair) ck(!o.covers(45, -175), "логово не внутри площадки " + o.id);

        String save = s.save();
        byte[] raw = Base64.getDecoder().decode(save);
        int version = ((raw[0] & 255) << 24) | ((raw[1] & 255) << 16) | ((raw[2] & 255) << 8) | (raw[3] & 255);
        ck(version == 21, "версия сохранения 21: " + version);
        raw[3] = 20;
        Simulation old = null;
        try { old = Simulation.load(Base64.getEncoder().encodeToString(raw)); } catch (RuntimeException e) { e.printStackTrace(); }
        ck(old != null && old.day == 0 && old.nodes.get(21).name.equals("Собачий лес"), "сохранение v20 Кордона — новый мир с тем же сидом");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
