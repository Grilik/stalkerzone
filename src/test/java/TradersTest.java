import ru.stalkerzone.sim.*;

/** Проверки Торговцев (карточка 002). */
public class TradersTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        noWars();
        couriers();
        noDirectTrade();
        markup();
        persistence();
        auditCheck();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Торговцы ни с кем не воюют. */
    static void noWars() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            Faction t = s.traders();
            ck(t != null, "фракция Торговцев есть (seed " + seed + ")");
            ck(t.passive, "Торговцы пассивны (seed " + seed + ")");
            for (int d = 0; d < 15; d++) s.step();
            ck(t.warsStarted == 0, "Торговцы ни с кем не начали войну (seed " + seed + ")");
            // никто не воюет с Торговцами
            for (Faction g : s.factions) {
                if (g == t) continue;
                ck(s.hostility[g.id][t.id] == 0 && s.hostility[t.id][g.id] == 0,
                        "вражды между «" + g.name + "» и Торговцами нет (seed " + seed + ")");
            }
        }
    }

    /** За 10 дней на Кордоне есть ≥1 доставка курьером каждой из трёх группировок. */
    static void couriers() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            int lon = 0, ban = 0, mil = 0;
            for (int d = 0; d < 10; d++) {
                s.step();
                for (Caravan c : s.caravans) {
                    if (c.courierFor >= 0 && c.returning) {
                        if (c.courierFor == ZoneScenario.LONERS) lon = 1;
                        if (c.courierFor == ZoneScenario.BANDITS) ban = 1;
                        if (c.courierFor == ZoneScenario.MILITARY) mil = 1;
                    }
                }
            }
            ck(lon == 1, "курьер доставил товар Одиночкам (seed " + seed + ")");
            ck(ban == 1, "курьер доставил товар Бандитам (seed " + seed + ")");
            ck(mil == 1, "курьер доставил товар Военным (seed " + seed + ")");
        }
    }

    /** Ни одна группировка не входит в бункер торговать сама. */
    static void noDirectTrade() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            int bad = 0;
            for (int d = 0; d < 15; d++) {
                s.step();
                for (Caravan c : s.caravans) {
                    if (c.owner != ZoneScenario.TRADERS) {
                        for (int r : c.route) if (r == 1) bad++;
                    }
                }
            }
            ck(bad == 0, "группировки не заходят в бункер сами (seed " + seed + ")");
        }
    }

    /** Цена для бандитов выше, чем для одиночек, ровно на множитель наценки. */
    static void markup() {
        double neu = ZoneScenario.tradersMarkup(ZoneScenario.LONERS);
        double sus = ZoneScenario.tradersMarkup(ZoneScenario.BANDITS);
        ck(neu == ZoneScenario.MARKUP_NEUTRAL && sus == ZoneScenario.MARKUP_SUSPICIOUS,
                "константы наценки: нейтрал " + neu + ", подозрительный " + sus);
        ck(sus / neu == ZoneScenario.MARKUP_SUSPICIOUS / ZoneScenario.MARKUP_NEUTRAL,
                "наценка для бандитов выше нейтральной ровно на 15% (" + sus + " / " + neu + " = " + String.format("%.4f", sus / neu) + ")");
    }

    /** Сохранение v15 загружается с фракцией Торговцев. */
    static void persistence() {
        Simulation s1 = Scenarios.create("kordon", 7);
        s1.step(5);
        Faction t1 = s1.traders();
        ck(t1 != null && t1.id == ZoneScenario.TRADERS, "Торговцы есть до сохранения");
        int m1 = t1.members;

        Simulation s2 = Simulation.load(s1.save());
        Faction t2 = s2.traders();
        ck(t2 != null && t2.id == ZoneScenario.TRADERS, "Торговцы есть после загрузки");
        ck(t2.members == m1, "численность Торговцев сохранилась: " + m1 + " → " + t2.members);
        ck(s2.save().equals(s1.save()), "повторное сохранение идентично");
    }

    /** Аудит сходится. */
    static void auditCheck() {
        boolean auditOk = true;
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int d = 0; d < 15; d++) {
                s.step();
                String a = s.audit();
                if (!a.isEmpty()) {
                    auditOk = false;
                    System.out.println("FAIL аудит seed " + seed + " день " + s.day + ":\n" + a);
                    break;
                }
            }
        }
        ck(auditOk, "аудит сходится 15 дней на трёх сидах");
    }
}