import ru.stalkerzone.sim.*;

/**
 * Карточка 010: выбор точки возрождения и состав стартового набора новичка (без Minecraft, как BoundsTest).
 */
public class PlayerStartTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = KordonScenario.create(0);

        // узел появления задан сценарием (деревня новичков), а не захардкожен в коде мира
        ck(s.spawnNode >= 0 && s.spawnNode < s.nodes.size(), "узел появления задан сценарием");
        ck("Деревня новичков".equals(s.nodes.get(s.spawnNode).name), "узел появления — деревня новичков");

        // без фракции — деревня
        ck(PlayerStart.respawnNode(s, -1) == s.spawnNode, "без фракции — деревня");
        ck(PlayerStart.respawnNode(s, 999) == s.spawnNode, "некорректная фракция — деревня");

        // во фракции — её главная база
        int bandits = ZoneScenario.BANDITS;
        int base = s.factions.get(bandits).baseNode;
        ck(s.nodes.get(base).owner == bandits, "главная база бандитов им и принадлежит");
        ck(PlayerStart.respawnNode(s, bandits) == base, "во фракции — на её главной базе");
        ck(s.nodes.get(base).name.equals("АТП"), "база бандитов — АТП");

        // у одиночек главная база — деревня (тот же узел появления)
        ck(PlayerStart.respawnNode(s, ZoneScenario.LONERS) == s.spawnNode, "одиночки возрождаются в деревне");

        // база потеряна — деревня
        s.nodes.get(base).owner = -1;
        ck(PlayerStart.respawnNode(s, bandits) == s.spawnNode, "база потеряна — деревня");

        // стартовый набор: состав по списку констант
        int pm = 0, mags = 0, knife = 0, jacket = 0, bandage = 0, medkit = 0, bread = 0, canned = 0, pda = 0, bolt = 0, det = 0, rub = 0;
        for (PlayerStart.Kit k : PlayerStart.KIT) {
            switch (k.kind()) {
                case PM -> pm += k.count();
                case PM_MAG -> mags += k.count();
                case KNIFE -> knife += k.count();
                case JACKET -> jacket += k.count();
                case BANDAGE -> bandage += k.count();
                case MEDKIT -> medkit += k.count();
                case BREAD -> bread += k.count();
                case CANNED -> canned += k.count();
                case PDA -> pda += k.count();
                case BOLT -> bolt += k.count();
                case DETECTOR_ECHO -> det += k.count();
                case RUBLES -> rub += k.count();
            }
        }
        ck(pm == 1, "ПМ — 1");
        ck(mags == 2, "магазинов к ПМ — 2");
        ck(knife == 1, "нож — 1");
        ck(jacket == 1, "кожаная куртка — 1");
        ck(bandage == 2, "бинтов — 2");
        ck(medkit == 1, "аптечка — 1");
        ck(bread == 1, "хлеб — 1");
        ck(canned == 1, "консервы — 1");
        ck(pda == 1, "КПК — 1");
        ck(bolt == 10, "болтов — 10");
        ck(det == 1, "детектор «Отклик» — 1");
        ck(rub == 500, "денег — 500 руб.");
        ck(PlayerStart.KIT.length == 12, "в наборе 12 позиций");
        boolean noEmpty = true;
        for (PlayerStart.Kit k : PlayerStart.KIT) if (k.count() <= 0) noEmpty = false;
        ck(noEmpty, "у каждой позиции набора ненулевое количество");

        // вступление и подсказка про КПК
        ck(PlayerStart.INTRO.length >= 2 && PlayerStart.INTRO.length <= 3, "вступление — 2–3 строки");
        boolean lines = true;
        for (String line : PlayerStart.INTRO) if (line == null || line.isBlank()) lines = false;
        ck(lines, "строки вступления заполнены");
        ck(PlayerStart.PDA_HINT.contains("КПК"), "подсказка про КПК на месте");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
