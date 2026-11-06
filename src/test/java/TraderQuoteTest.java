import ru.stalkerzone.sim.*;

/**
 * П-07, окно торговца: «итог сделки до подтверждения» — пробная сделка (quoteBuy/quoteSell) ничего не меняет
 * (склад, касса, учёт Ledger) и совпадает с настоящей сделкой сразу после неё; вещи из нескольких единиц
 * (костюмы, рюкзаки, оружие) продаются и покупаются только целиком.
 */
public class TraderQuoteTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static String state(Simulation s, Node n) {
        StringBuilder b = new StringBuilder().append(n.market.money);
        for (Good g : Good.ALL) b.append(',').append(n.market.get(g));
        return b.append('|').append(s.audit()).toString();
    }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 2);
        for (int d = 0; d < 20; d++) s.step();
        Node n = null;
        for (Node x : s.nodes) if (x.trader) { n = x; break; }
        ck(n != null, "в «Кордоне» есть торговец");
        if (n == null) { System.exit(1); return; }
        // пополнить склад для теста — через учёт, как завоз с Большой земли
        n.market.add(Good.MEDS, 30); s.ledger.imported[Good.MEDS.ordinal()] += 30;
        n.market.add(Good.GEAR, 12); s.ledger.imported[Good.GEAR.ordinal()] += 12;
        ck(s.audit().isEmpty(), "аудит сходится до сделок");
        String before = state(s, n);

        // покупка поштучно
        Simulation.Deal q = s.quoteBuy(n, Good.MEDS, 1, 10, 1_000_000);
        ck(state(s, n).equals(before), "пробная покупка ничего не меняет (склад, касса, учёт)");
        Simulation.Deal real = s.playerBuyPacks(n, Good.MEDS, 1, 10, 1_000_000);
        ck(q.equals(real) && real.qty() == 10, "итог пробной покупки = настоящая покупка (" + q + " / " + real + ")");
        ck(s.audit().isEmpty(), "аудит сходится после покупки");

        // бюджет ограничивает
        long one = s.quoteBuy(n, Good.MEDS, 1, 1, 1_000_000).money();
        Simulation.Deal poor = s.quoteBuy(n, Good.MEDS, 1, 10, one * 3 + one / 2);
        ck(poor.qty() == 3, "денег на 3,5 штуки — пробная покупка даёт 3 (" + poor.qty() + ")");

        // продажа поштучно
        before = state(s, n);
        Simulation.Deal qs = s.quoteSell(n, Good.FOOD, 1, 7);
        ck(state(s, n).equals(before), "пробная продажа ничего не меняет");
        Simulation.Deal rs = s.playerSellPacks(n, Good.FOOD, 1, 7);
        ck(qs.equals(rs), "итог пробной продажи = настоящая продажа (" + qs + " / " + rs + ")");
        ck(s.audit().isEmpty(), "аудит сходится после продажи");

        // вещь из 4 единиц снаряжения: только целиком
        int gear = n.market.get(Good.GEAR);
        Simulation.Deal packs = s.quoteBuy(n, Good.GEAR, 4, 100, 1_000_000_000L);
        ck(packs.qty() == gear / 4, "вещей по 4 ед. можно купить " + gear / 4 + " (склад " + gear + "), пробно — " + packs.qty());
        before = state(s, n);
        Simulation.Deal pb = s.playerBuyPacks(n, Good.GEAR, 4, 2, 1_000_000_000L);
        ck(pb.qty() == 2 && n.market.get(Good.GEAR) == gear - 8, "купить 2 вещи по 4 ед. — со склада ушло 8 ед.");
        ck(s.audit().isEmpty(), "аудит сходится после покупки снаряжения");

        // касса торговца не тянет — продать вещь нельзя, и следов не остаётся
        long cash = n.market.money;
        n.market.money = 0;
        before = state(s, n);
        Simulation.Deal broke = s.quoteSell(n, Good.GEAR, 4, 1);
        Simulation.Deal broke2 = s.playerSellPacks(n, Good.GEAR, 4, 1);
        ck(broke.qty() == 0 && broke2.qty() == 0 && state(s, n).equals(before), "пустая касса: продажа не идёт, склад и учёт без изменений");
        n.market.money = cash;

        // перепродажа без прибыли и через окно
        long bought = s.playerBuyPacks(n, Good.MEDS, 1, 5, 1_000_000).money();
        long sold = s.playerSellPacks(n, Good.MEDS, 1, 5).money();
        ck(sold < bought, "купить и тут же продать 5 аптечек — в минус (" + bought + " → " + sold + ")");
        ck(s.audit().isEmpty(), "аудит сходится в конце");

        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
