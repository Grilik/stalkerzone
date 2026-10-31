import ru.stalkerzone.sim.*;

/** Проверки бункера Сидоровича и торговли игрока (карточка 011). */
public class SidorovichTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        playerMarkup();
        artifactSell();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Наценка для игрока: бандиту ровно на множитель дороже одиночки; без фракции — как нейтрал. */
    static void playerMarkup() {
        Simulation s = Scenarios.create("kordon", 1);
        Node bunker = traderNode(s);
        ck(bunker != null, "у сценария есть торговый узел (бункер)");
        if (bunker == null) return;

        double none = PlayerCareer.traderBuyFactor(0, false, 0, PlayerCareer.NONE);
        double loner = PlayerCareer.traderBuyFactor(0, false, 0, ZoneScenario.LONERS);
        double bandit = PlayerCareer.traderBuyFactor(0, false, 0, ZoneScenario.BANDITS);
        ck(none == ZoneScenario.MARKUP_NEUTRAL, "без фракции цена как у нейтрала: " + none);
        ck(loner == ZoneScenario.MARKUP_NEUTRAL, "одиночке цена нейтральная: " + loner);
        ck(bandit / loner == ZoneScenario.MARKUP_SUSPICIOUS / ZoneScenario.MARKUP_NEUTRAL,
                "игроку-бандиту дороже одиночки ровно на множитель (x" + String.format("%.4f", bandit / loner) + ")");

        // та же разница и в цене на товар: цена = базовая цена торговца × множитель
        Good g = Good.MEDS;
        long base = s.sellPrice(bunker, g);
        long forLoner = Math.max(1, Math.round(base * loner));
        long forBandit = Math.max(1, Math.round(base * bandit));
        ck(forBandit > forLoner, "цена покупки для бандита выше (" + forBandit + " > " + forLoner + " руб.)");
    }

    /** Продажа артефакта пополняет склад Торговцев и списывает деньги через Ledger. */
    static void artifactSell() {
        Simulation s = Scenarios.create("kordon", 1);
        Node bunker = traderNode(s);
        ck(bunker != null, "торговый узел найден для продажи артефакта");
        if (bunker == null) return;

        int onStock = bunker.market.get(Good.ARTIFACT);
        long cash = bunker.market.money;
        long burned = s.ledger.moneyBurned;
        long imported = s.ledger.imported[Good.ARTIFACT.ordinal()];

        Simulation.Deal d = s.playerSell(bunker, Good.ARTIFACT, 1);
        ck(d.qty() == 1 && d.money() > 0, "артефакт куплен у игрока за " + d.money() + " руб.");
        ck(bunker.market.get(Good.ARTIFACT) == onStock + 1, "склад Торговцев пополнился артефактом");
        ck(bunker.market.money == cash - d.money(), "деньги списаны из кассы торговца");
        ck(s.ledger.moneyBurned == burned + d.money(), "списание прошло через Ledger.moneyBurned");
        ck(s.ledger.imported[Good.ARTIFACT.ordinal()] == imported + 1, "приход артефакта учтён в Ledger.imported");
    }

    /** Узел с торговцем. */
    static Node traderNode(Simulation s) {
        for (Node n : s.nodes) if (n.trader) return n;
        return null;
    }
}
