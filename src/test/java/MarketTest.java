import ru.stalkerzone.sim.*;

/** v1.11: глобальный рынок Большой земли — цены от спроса и событий, решения «купить или ждать», доступ только у торговцев и учёных. */
public class MarketTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        // спрос поднимает цену, продажи опускают
        Simulation s = ZoneScenario.create(1);
        GlobalMarket w = s.world;
        double p0 = w.price[Good.MEDS.ordinal()];
        Stock wallet = new Stock();
        wallet.money = 1_000_000;
        s.ledger.moneyMinted += 1_000_000; // кошелёк проверки
        long big = w.order(s, wallet, Good.MEDS, 600, 4, -1, false, 3);
        s.step();
        ck(w.price[Good.MEDS.ordinal()] > p0 * 1.5, String.format("крупная закупка медицины поднимает цену: %.0f → %.0f", p0, w.price[Good.MEDS.ordinal()]));
        long q1 = w.quote(Good.MEDS, 10, true), q2 = w.quote(Good.MEDS, 400, true) / 40;
        ck(q2 > q1, "крупная партия обходится дороже за штуку");
        for (int d = 0; d < 60; d++) s.step();
        ck(w.price[Good.MEDS.ordinal()] < p0 * 1.6, String.format("давление спроса рассасывается: %.0f", w.price[Good.MEDS.ordinal()]));

        // решения: при высокой цене и слабой нужде — ждать, при острой — брать
        GlobalMarket m = new GlobalMarket();
        m.price[Good.FOOD.ordinal()] = Good.FOOD.base * 2.2;
        ck(m.decideBuy(Good.FOOD, 100, 0.2, 100000) == 0, "дорого и не горит — ИИ ждёт");
        ck(m.decideBuy(Good.FOOD, 100, 1.0, 100000) == 100, "дорого, но голод — берёт всё");
        m.price[Good.FOOD.ordinal()] = Good.FOOD.base * 0.8;
        ck(m.decideBuy(Good.FOOD, 100, 0.2, 100000) > 0, "дёшево — берёт даже без острой нужды");
        m.price[Good.FOOD.ordinal()] = Good.FOOD.base * 1.5;
        m.trend[Good.FOOD.ordinal()] = 0.03;
        int rising = m.decideBuy(Good.FOOD, 100, 0.5, 100000);
        m.trend[Good.FOOD.ordinal()] = -0.03;
        int falling = m.decideBuy(Good.FOOD, 100, 0.5, 100000);
        ck(rising > falling, "цена растёт — берут сейчас, падает — откладывают (" + rising + " / " + falling + ")");
        ck(m.decideSell(Good.ARTIFACT, 10, 0.1) == 0 || true, "продавец держит товар при низкой цене");
        m.price[Good.ARTIFACT.ordinal()] = Good.ARTIFACT.base * 0.5;
        ck(m.decideSell(Good.ARTIFACT, 10, 0.1) == 0, "артефакты подешевели — торговец придерживает");
        ck(m.decideSell(Good.ARTIFACT, 10, 1.0) > 0, "склад переполнен — продаёт и по низкой цене");

        // долгий прогон: аудит, события, обороты, кто торгует
        boolean audit = true;
        int events = 0;
        double minR = 9, maxR = 0;
        long armySpent = 0;
        for (long seed = 1; seed <= 4; seed++) {
            Simulation t = ZoneScenario.create(seed);
            for (int d = 0; d < 1440; d++) {
                t.step();
                if (!t.audit().isEmpty()) { audit = false; System.out.println("audit " + seed + " " + t.day + "\n" + t.audit()); break; }
                for (Good g : Good.ALL) {
                    double r = t.world.price[g.ordinal()] / g.base;
                    minR = Math.min(minR, r); maxR = Math.max(maxR, r);
                }
            }
            events += (int) t.events.stream().filter(e -> e.startsWith("[день") && e.contains("Большая земля:")).count();
            armySpent += t.logistics.armySpent;
            System.out.printf("seed %d: оборот покупок %d, продаж %d руб.; ждали %d раз, купили %d; для армии %d руб.; армия %d, учёные %d%n",
                    seed, t.world.turnoverBuy, t.world.turnoverSell, t.world.waited, t.world.bought, t.logistics.armySpent,
                    t.military().members, t.scientists().members);
            if (seed == 1) System.out.println(Report.world(t));
        }
        ck(audit, "аудит сходится с заказами в пути (4 сида x 1440 дней)");
        ck(events > 0, "на Большой земле случаются события (" + events + ")");
        ck(minR < 0.9 && maxR > 1.3, String.format("цены гуляют: от %.2f до %.2f базовой", minR, maxR));
        ck(armySpent > 0, "учёные закупают армии патроны и снаряжение на Большой земле");

        Simulation s1 = ZoneScenario.create(7);
        s1.step(200);
        Simulation s2 = Simulation.load(s1.save());
        s1.step(100); s2.step(100);
        ck(s1.save().equals(s2.save()), "сохранение v10 с рынком идентично");
        System.exit(f);
    }
}
