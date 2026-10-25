import ru.stalkerzone.sim.*;
import java.util.Random;

public class TradeTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = ZoneScenario.create(3);
        s.step(30);
        Node k = s.nodes.get(0);
        ck(s.audit().isEmpty(), "аудит до сделок");
        long player = 0;
        // игрок продаёт много артефактов и патронов, потом всё выкупает обратно: прибыли быть не должно
        long moneyStart = 5000;
        long cash = moneyStart;
        int sellN = 40;
        Simulation.Deal d = s.playerSell(k, Good.AMMO, sellN);
        cash += d.money();
        Simulation.Deal b = s.playerBuy(k, Good.AMMO, d.qty(), cash);
        cash -= b.money();
        ck(cash <= moneyStart, "круг продажа-покупка не приносит прибыли (было " + moneyStart + ", стало " + cash + ")");
        ck(s.audit().isEmpty(), "аудит после сделок игрока");
        // продажа больше кассы
        long before = k.market.money;
        Simulation.Deal big = s.playerSell(k, Good.GEAR, 100000);
        ck(big.money() <= before && big.qty() < 100000 && k.market.money >= 0, "касса торговца ограничивает выплату (" + big.qty() + " шт. за " + big.money() + ")");
        ck(s.audit().isEmpty(), "аудит после упора в кассу");
        // добыча
        Random r = new Random(1);
        int[] loot = s.npcLoot(ZoneScenario.BANDITS, r);
        ck(s.audit().isEmpty(), "аудит после добычи с NPC (деньги " + loot[Simulation.G] + ")");
        // снаряжение целиком или никак, ремонт
        Node kk = s.nodes.get(0);
        int gearBefore = kk.market.get(Good.GEAR);
        long cashBefore = kk.market.money;
        Simulation.Deal none = s.playerBuyAll(kk, Good.GEAR, gearBefore + 5, Long.MAX_VALUE / 4);
        ck(none.qty() == 0 && kk.market.get(Good.GEAR) == gearBefore && kk.market.money == cashBefore && s.audit().isEmpty(),
                "покупка снаряжения сверх склада откатывается без следов");
        kk.market.add(Good.GEAR, 0);
        Simulation.Deal poor = s.playerBuyAll(kk, Good.PARTS, Math.max(1, kk.market.get(Good.PARTS)), 1);
        ck(poor.qty() == 0 && s.audit().isEmpty(), "покупка без денег откатывается");
        long m0 = kk.market.money;
        s.playerPay(kk, 150);
        ck(kk.market.money == m0 + 150 && s.audit().isEmpty(), "плата за ремонт идёт в кассу и учтена");
        long cashNow = kk.market.money;
        kk.market.money = 0; // не трогаем учёт: деньги уходят в «заначку» через продажу ниже
        Simulation.Deal noMoney = s.playerSellAll(kk, Good.GEAR, 3);
        ck(noMoney.qty() == 0, "продажа костюма без денег в кассе отклонена");
        kk.market.money = cashNow;
        ck(s.audit().isEmpty(), "аудит после проверок снаряжения");
        // длинный прогон с чередой сделок
        for (int d2 = 0; d2 < 400; d2++) {
            s.step();
            for (Node n : s.nodes) {
                if (!n.trader) continue;
                Simulation.Deal x = s.playerSell(n, Good.values()[d2 % 6], 5);
                s.playerBuy(n, Good.values()[(d2 + 2) % 6], 3, 5000);
            }
            if (!s.audit().isEmpty()) { ck(false, "аудит упал на дне " + s.day); break; }
        }
        ck(s.audit().isEmpty(), "аудит после 400 дней сделок");
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f);
    }
}
