import ru.stalkerzone.sim.*;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * К2 (правило A HQ): денежный баланс засады на караван.
 * <ul>
 *   <li>70% наличных захватывает налётчик (существующая логика raidCargo), весь груз — тоже;</li>
 *   <li>остаток 30% при ликвидации каравана (ambushCaravan) теряется через ledger.moneyBurned;</li>
 *   <li>одна и та же сумма не учитывается дважды (добыча + сожжённое = исходная сумма);</li>
 *   <li>повторный вызов обработки того же нападения ничего не списывает;</li>
 *   <li>нулевая и единичная суммы — граничные значения;</li>
 *   <li>обычный грабёж (banditRaid/raidCargo) не меняет семантику: остаток остаётся в караване
 *       и доходит до владельца при выгрузке;</li>
 *   <li>контракт AMBUSH_COURIER (ContractBoard.courierAmbushed): залог игроку + остаток курьера
 *       через moneyBurned; без курьера — провал и возврат залога.</li>
 * </ul>
 * Приватные raidCargo/unload вызываются через рефлексию (прецедент: WarLedgerTest, AnomalyRadiusTest).
 */
public class AmbushLedgerTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) throws Exception {
        long t0 = System.currentTimeMillis();
        ambushBurnsRemainder();
        ambushRounding();
        ambushZeroAndUnit();
        ambushRepeat();
        raidKeepsCaravanMoney();
        courierAmbushedContract();
        courierAmbushedFailed();
        System.out.println("время: " + (System.currentTimeMillis() - t0) / 1000.0 + " с");
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------ helpers

    static Simulation setup(long seed) {
        Simulation s = Scenarios.create("kordon", seed);
        s.step(5);
        return s;
    }

    /** Довести кассу до суммы через Ledger (audit остаётся пустым). */
    static void fund(Simulation s, Stock st, long money) {
        if (st.money < money) {
            long add = money - st.money;
            st.money += add;
            s.ledger.moneyMinted += add;
        }
    }

    /** Караван с точной суммой и грузом, погруженными со склада владельца через Stock.move. */
    static Caravan caravan(Simulation s, Faction owner, long money, int goodsEach) {
        fund(s, owner.stock, money);
        Caravan c = new Caravan(owner.id, new int[]{owner.baseNode, s.traders().baseNode}, 4);
        for (Good g : Good.ALL) Stock.move(owner.stock, c.cargo, g, Math.min(goodsEach, owner.stock.get(g)));
        Stock.moveMoney(owner.stock, c.cargo, money);
        s.caravans.add(c);
        return c;
    }

    static Method privateMethod(String name, Class<?>... types) throws Exception {
        Method m = Simulation.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    // ------------------------------------------------------------------ 1. засада: 70% добыча + 30% сожжено

    static void ambushBurnsRemainder() {
        Simulation s = setup(11);
        Faction victim = s.factions.get(ZoneScenario.LONERS), raider = s.factions.get(ZoneScenario.BANDITS);
        Caravan c = caravan(s, victim, 1000, 10);
        int[] loaded = c.cargo.qty.clone();
        ck(c.cargo.money == 1000 && s.audit().isEmpty(), "засада: караван с 1000 руб., audit пуст до");

        long total0 = s.totalMoney(), burned0 = s.ledger.moneyBurned;
        long raiderMoney0 = raider.stock.money;
        int[] raiderGoods0 = raider.stock.qty.clone();
        long[] destroyed0 = s.ledger.destroyed.clone();

        ck(s.ambushCaravan(c, raider), "засада: нападение состоялось");
        ck(!s.caravans.contains(c), "засада: караван снят с маршрута");
        ck(raider.stock.money - raiderMoney0 == 700, "засада: налётчик захватил 70% (700 руб.)");
        boolean goodsOk = true;
        for (Good g : Good.ALL) if (raider.stock.qty[g.ordinal()] - raiderGoods0[g.ordinal()] != loaded[g.ordinal()]) goodsOk = false;
        ck(goodsOk, "засада: весь груз ушёл налётчику (существующая логика)");
        ck(c.cargo.money == 0, "засада: в ликвидированном караване денег не осталось");
        ck(s.ledger.moneyBurned - burned0 == 300, "засада: остаток 30% (300 руб.) учтён в moneyBurned");
        ck(Arrays.equals(destroyed0, s.ledger.destroyed), "засада: товары не сгорали (они у налётчика)");
        ck(s.totalMoney() == total0 - 300, "засада: из баланса ушло ровно 300 руб., не больше");
        ck(s.audit().isEmpty(), "засада: audit пуст после: " + s.audit());
    }

    // ------------------------------------------------------------------ 2. округление: без двойного учёта

    static void ambushRounding() {
        for (long money : new long[]{999, 1001}) {
            Simulation s = setup(12);
            Faction victim = s.factions.get(ZoneScenario.LONERS), raider = s.factions.get(ZoneScenario.BANDITS);
            Caravan c = caravan(s, victim, money, 0);
            long total0 = s.totalMoney(), burned0 = s.ledger.moneyBurned, raider0 = raider.stock.money;
            s.ambushCaravan(c, raider);
            long captured = raider.stock.money - raider0, burned = s.ledger.moneyBurned - burned0;
            ck(captured == (long) (money * 0.7), "округление " + money + ": добыча " + captured + " = (long)(0.7*сумма)");
            ck(captured + burned == money, "округление " + money + ": добыча + сожжённое = исходная сумма (без двойного учёта)");
            ck(s.totalMoney() == total0 - burned && s.audit().isEmpty(), "округление " + money + ": баланс сходится");
        }
    }

    // ------------------------------------------------------------------ 3. граничные суммы

    static void ambushZeroAndUnit() {
        for (long money : new long[]{0, 1}) {
            Simulation s = setup(13);
            Faction victim = s.factions.get(ZoneScenario.LONERS), raider = s.factions.get(ZoneScenario.BANDITS);
            Caravan c = caravan(s, victim, money, 0);
            long total0 = s.totalMoney(), burned0 = s.ledger.moneyBurned, raider0 = raider.stock.money;
            ck(s.ambushCaravan(c, raider), "граница " + money + ": нападение состоялось");
            long captured = raider.stock.money - raider0, burned = s.ledger.moneyBurned - burned0;
            ck(captured + burned == money, "граница " + money + ": добыча + сожжённое = сумма");
            ck(s.totalMoney() == total0 - burned && s.audit().isEmpty(), "граница " + money + ": audit пуст");
        }
    }

    // ------------------------------------------------------------------ 4. повторная обработка

    static void ambushRepeat() {
        Simulation s = setup(14);
        Faction victim = s.factions.get(ZoneScenario.LONERS), raider = s.factions.get(ZoneScenario.BANDITS);
        Caravan c = caravan(s, victim, 1000, 5);
        s.ambushCaravan(c, raider);
        long total0 = s.totalMoney(), burned0 = s.ledger.moneyBurned, raider0 = raider.stock.money;
        int deaths0 = victim.deaths;
        ck(!s.ambushCaravan(c, raider), "повтор: второй вызов возвращает false (каравана уже нет)");
        ck(s.totalMoney() == total0 && s.ledger.moneyBurned == burned0 && raider.stock.money == raider0,
                "повтор: деньги не списываются повторно");
        ck(victim.deaths == deaths0, "повтор: смерти не начисляются повторно");
        ck(s.audit().isEmpty(), "повтор: audit пуст");
    }

    // ------------------------------------------------------------------ 5. обычный грабёж: семантика не меняется

    static void raidKeepsCaravanMoney() throws Exception {
        Simulation s = setup(15);
        Faction victim = s.factions.get(ZoneScenario.LONERS), raider = s.factions.get(ZoneScenario.BANDITS);
        Caravan c = caravan(s, victim, 1000, 10);
        long total0 = s.totalMoney(), burned0 = s.ledger.moneyBurned, victim0 = victim.stock.money;

        privateMethod("raidCargo", Caravan.class, Faction.class, Faction.class).invoke(s, c, victim, raider);
        ck(s.caravans.contains(c), "грабёж: караван остаётся на маршруте");
        ck(c.cargo.money == 300, "грабёж: остаток 30% остаётся в караване (не сгорает)");
        ck(s.ledger.moneyBurned == burned0, "грабёж: moneyBurned не изменился");
        ck(s.totalMoney() == total0 && s.audit().isEmpty(), "грабёж: деньги каравана учтены в totals, audit пуст");

        // караван доходит домой и выгружается — остаток достаётся владельцу
        s.caravans.remove(c);
        privateMethod("unload", Caravan.class, Faction.class).invoke(s, c, victim);
        ck(victim.stock.money - victim0 == 300, "грабёж: остаток 300 руб. выгружен владельцу");
        ck(c.cargo.money == 0 && s.totalMoney() == total0 && s.audit().isEmpty(),
                "грабёж: полный цикл без потерь денег, audit пуст");
    }

    // ------------------------------------------------------------------ 6. контракт AMBUSH_COURIER

    /** Курьер Торговцев с грузом и деньгами из склада бункера. */
    static Caravan courier(Simulation s, Faction buyer) {
        Faction tr = s.traders();
        Node hub = s.nodes.get(tr.baseNode);
        Caravan c = new Caravan(tr.id, new int[]{tr.baseNode, buyer.baseNode}, 2);
        c.courierFor = buyer.id;
        for (Good g : Good.ALL) Stock.move(hub.market, c.cargo, g, Math.min(5, hub.market.get(g)));
        fund(s, hub.market, 800);
        Stock.moveMoney(hub.market, c.cargo, 800);
        s.caravans.add(c);
        return c;
    }

    static Contract ambushContract(Simulation s, Faction bandits, Faction buyer) {
        Contract k = new Contract(9001, Contract.Type.AMBUSH_COURIER, bandits.id);
        k.qty = buyer.id;
        k.node = buyer.baseNode;
        k.created = s.day;
        k.deadline = s.day + 10;
        k.difficulty = 2;
        k.status = Contract.Status.TAKEN;
        k.taker = Contract.PLAYER;
        k.player = "tester";
        fund(s, bandits.stock, 400);
        Stock.moveMoney(bandits.stock, new Stock(), 400); // залог уходит из кассы в учёт доски (escrowTotal)
        k.escrow = 400;
        s.contracts.list.add(k);
        return k;
    }

    static void courierAmbushedContract() {
        Simulation s = setup(17);
        Faction buyer = s.factions.get(ZoneScenario.LONERS), bandits = s.factions.get(ZoneScenario.BANDITS);
        Caravan c = courier(s, buyer);
        Contract k = ambushContract(s, bandits, buyer);
        ck(c.cargo.money == 800 && s.audit().isEmpty(), "контракт: курьер с 800 руб. и залогом, audit пуст до");

        long total0 = s.totalMoney(), burned0 = s.ledger.moneyBurned;
        ContractBoard.Reward r = s.contracts.courierAmbushed(s, buyer.id, "tester");
        ck(r != null && r.money() == 400, "контракт: награда игроку = залог 400 руб.");
        ck(k.status == Contract.Status.DONE, "контракт: закрыт");
        ck(!s.caravans.contains(c), "контракт: курьер ликвидирован");
        long burned = s.ledger.moneyBurned - burned0;
        ck(burned == 240 + 400, "контракт: сожжено 240 (остаток курьера) + 400 (залог игроку) = 640, факт " + burned);
        ck(s.totalMoney() == total0 - 640, "контракт: из баланса ушло ровно 640 руб.");
        ck(s.audit().isEmpty(), "контракт: audit пуст после: " + s.audit());
    }

    // ------------------------------------------------------------------ 7. провал: курьера нет

    static void courierAmbushedFailed() {
        Simulation s = setup(18);
        Faction buyer = s.factions.get(ZoneScenario.LONERS), bandits = s.factions.get(ZoneScenario.BANDITS);
        Contract k = ambushContract(s, bandits, buyer);
        long bandits0 = bandits.stock.money, total0 = s.totalMoney();
        ContractBoard.Reward r = s.contracts.courierAmbushed(s, buyer.id, "tester");
        ck(r == null, "провал: награды нет (курьер уже дошёл или исчез)");
        ck(k.status == Contract.Status.FAILED, "провал: контракт провален");
        ck(bandits.stock.money == bandits0 + 400, "провал: залог возвращён заказчику");
        ck(s.totalMoney() == total0 && s.audit().isEmpty(), "провал: деньги не двигались, audit пуст");
    }
}