import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 054 (решения пользователя 06.11, 23:50): «Малый Кордон» — сценарий «kordon». Из военных включён только
 * гарнизон южного блокпоста (узел 2): неподвижный, без отрядов и захвата точек (Faction.garrisonOnly); мост ничей.
 * Бандитов эта карточка не трогает (они — карточка 057). Полная война трёх группировок осталась
 * сценарием «kordon_war» (Scenarios.KORDON_WAR). Версия сохранения +1: старый Кордон — новый мир.
 */
public class SmallKordonFactionsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            Faction mil = s.factions.get(ZoneScenario.MILITARY);
            ck(mil.garrisonOnly, "сид " + seed + ": военные — только гарнизон (garrisonOnly)");
            boolean post = true, others = true, squads = true;
            for (int d = 0; d < 60; d++) {
                s.step();
                if (s.nodes.get(2).owner != ZoneScenario.MILITARY) post = false;
                for (Node n : s.nodes) {
                    if (n.id == 2) continue;
                    if (n.owner == ZoneScenario.MILITARY) others = false;
                }
                for (Convoy c : s.logistics.convoys) if (c.owner == mil.id) squads = false;
                for (Caravan c : s.caravans) if (c.owner == mil.id) squads = false;
            }
            ck(post, "сид " + seed + ": 60 дней южный блокпост (узел 2) — у военных");
            ck(others, "сид " + seed + ": других узлов военных нет");
            ck(squads, "сид " + seed + ": у военных нет отрядов в пути");
            ck(mil.members >= 10, "сид " + seed + ": гарнизон жив: " + mil.members);
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит сходится");
        }

        // полная война — отдельный сценарий
        Simulation w = Scenarios.create("kordon_war", 1);
        ck(w.scenario.equals("kordon_war"), "сценарий kordon_war есть");
        ck(w.nodes.get(5).owner == ZoneScenario.BANDITS && w.nodes.get(8).owner == ZoneScenario.MILITARY
                && !w.factions.get(ZoneScenario.MILITARY).garrisonOnly, "kordon_war: бандиты на АТП, военные на мосту, ИИ включён");
        for (int d = 0; d < 5; d++) w.step();
        Simulation wl = Simulation.load(w.save());
        ck(wl.scenario.equals("kordon_war") && wl.save().equals(w.save()), "kordon_war сохраняется и грузится без потерь");
        ck(Scenarios.isKordon("kordon") && Scenarios.isKordon("kordon_war") && !Scenarios.isKordon("zone"), "Scenarios.isKordon");

        // старое сохранение Кордона (версия −1) — новый Малый Кордон с тем же сидом
        Simulation s = Scenarios.create("kordon", 9);
        for (int d = 0; d < 3; d++) s.step();
        byte[] raw = Base64.getDecoder().decode(s.save());
        int v = ((raw[0] & 255) << 24) | ((raw[1] & 255) << 16) | ((raw[2] & 255) << 8) | (raw[3] & 255);
        raw[0] = (byte) ((v - 1) >>> 24); raw[1] = (byte) ((v - 1) >>> 16); raw[2] = (byte) ((v - 1) >>> 8); raw[3] = (byte) (v - 1);
        Simulation old = Simulation.load(Base64.getEncoder().encodeToString(raw));
        ck(old.day == 0 && old.seed == s.seed && old.factions.get(ZoneScenario.MILITARY).garrisonOnly,
                "сохранение версии " + (v - 1) + " — новый Малый Кордон с тем же сидом");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
