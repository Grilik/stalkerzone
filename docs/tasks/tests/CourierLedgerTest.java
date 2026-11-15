import ru.stalkerzone.sim.*;

/**
 * Э0-09: гибель курьера Торговцев в мире не нарушает закон сохранения.
 * Груз курьера уходит в ledger.destroyed, деньги — в ledger.moneyBurned; запись делает
 * Simulation.courierLost(Caravan), а не игровой код (CaravanManager).
 */
public class CourierLedgerTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int d = 0; d < 5; d++) s.step();
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит пуст до опыта");

            Faction tr = s.traders() != null ? s.traders() : s.factions.get(0);
            Faction buyer = s.factions.get(tr.id == 0 ? 1 : 0);
            Caravan c = new Caravan(tr.id, new int[]{tr.baseNode, buyer.baseNode}, 2);
            c.courierFor = buyer.id;
            s.caravans.add(c);
            long money = Stock.moveMoney(tr.stock, c.cargo, Math.min(500, tr.stock.money));
            if (money == 0) { tr.stock.money += 300; s.ledger.moneyMinted += 300; money = Stock.moveMoney(tr.stock, c.cargo, 300); }
            int[] goods = new int[Good.ALL.length];
            for (Good g : Good.ALL) goods[g.ordinal()] = Stock.move(tr.stock, c.cargo, g, Math.min(5, tr.stock.get(g)));
            ck(money > 0 && s.audit().isEmpty(), "сид " + seed + ": курьер с деньгами " + money + ", аудит пуст");

            long burned0 = s.ledger.moneyBurned;
            long[] destroyed0 = s.ledger.destroyed.clone();
            s.courierLost(c);

            ck(s.audit().isEmpty(), "сид " + seed + ": после гибели курьера аудит пуст: " + s.audit());
            ck(s.ledger.moneyBurned - burned0 == money, "сид " + seed + ": moneyBurned вырос ровно на " + money
                    + " (вырос на " + (s.ledger.moneyBurned - burned0) + ")");
            boolean goodsOk = true;
            for (Good g : Good.ALL) if (s.ledger.destroyed[g.ordinal()] - destroyed0[g.ordinal()] != goods[g.ordinal()]) goodsOk = false;
            ck(goodsOk, "сид " + seed + ": груз курьера записан в destroyed");
            ck(c.cargo.money == 0 && c.cargo.get(Good.ALL[0]) == 0, "сид " + seed + ": груз курьера пуст");

            s.courierLost(c);
            ck(s.ledger.moneyBurned - burned0 == money && s.audit().isEmpty(), "сид " + seed + ": повторный вызов ничего не списывает");
            for (int d = 0; d < 3; d++) s.step();
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит пуст через 3 дня");
        }
        System.out.println(f == 0 ? "ALL OK" : "FAILED " + f);
        if (f > 0) System.exit(1);
    }
}
