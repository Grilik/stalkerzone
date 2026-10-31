import ru.stalkerzone.sim.*;

/**
 * Карточка 025: касса торговцев Кордона и стартовые деньги игрока.
 * Стартовая касса считается от цен скупки (3 артефакта уровня 2 и 10 частей мутантов), а за месяц
 * без игрока она не пересыхает: хватает на те же 3 артефакта, закон сохранения денег не нарушен.
 */
public class TraderCashTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    static Node bunker(Simulation s) {
        for (Node n : s.nodes) if (n.trader) return n;
        return null;
    }

    /** Минимальная закупка торговца Кордона по текущим ценам. */
    static long minBatch(Simulation s, Node n) {
        return (long) KordonBalance.TRADER_ARTIFACTS_MIN * s.buyPrice(n, Good.ARTIFACT)
                + (long) KordonBalance.TRADER_MUTANT_PARTS_MIN * s.buyPrice(n, Good.MEDS);
    }

    /** На старте кассы хватает на 3 артефакта уровня 2 и 10 частей мутантов подряд. */
    static void startCash() {
        Simulation s = Scenarios.create("kordon", 1);
        Node b = bunker(s);
        ck(b != null, "у сценария есть торговый узел (бункер)");
        if (b == null) return;
        long need = minBatch(s, b);
        ck(b.market.money >= need, "стартовая касса " + b.market.money + " руб. покрывает закупку " + need + " руб.");
    }

    /** Через 30 дней без игрока касса всё ещё тянет 3 артефакта уровня 2, аудит сходится. */
    static void monthRun() {
        boolean auditOk = true, cashOk = true;
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            Node b = bunker(s);
            long min = Long.MAX_VALUE;
            for (int d = 0; d < 30; d++) {
                s.step();
                if (!s.audit().isEmpty()) {
                    auditOk = false;
                    System.out.println("FAIL аудит seed " + seed + " день " + s.day + ":\n" + s.audit());
                    break;
                }
                if (b.market.money < min) min = b.market.money;
            }
            long need = (long) KordonBalance.TRADER_ARTIFACTS_MIN * s.buyPrice(b, Good.ARTIFACT);
            boolean ok = b.market.money >= need;
            if (!ok) cashOk = false;
            System.out.println("  seed " + seed + ": касса через 30 дней " + b.market.money + " руб., минимум " + min
                    + ", нужно на 3 артефакта " + need + " руб.");
        }
        ck(auditOk, "закон сохранения денег за 30 дней не нарушен");
        ck(cashOk, "касса торговца бункера через 30 дней ≥ стоимости 3 артефактов уровня 2");
    }

    public static void main(String[] a) {
        startCash();
        monthRun();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
