import ru.stalkerzone.sim.*;

/** v1.12: кустарное, прототипы и снаряжение с Большой земли. */
public class ArmsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        // свойства качества
        Stock zone = new Stock(), pro = new Stock();
        zone.add(Good.GEAR, 10);
        pro.add(Good.GEAR_PRO, 10);
        ck(Arms.gearCombat(pro) > Arms.gearCombat(zone), "заводское снаряжение сильнее в бою");
        ck(Arms.anomalyGuard(pro) < Arms.anomalyGuard(zone), "заводское хуже держит аномалии");
        Stock proto = new Stock();
        proto.add(Good.PROTO, 10);
        ck(Arms.gearCombat(proto) > Arms.gearCombat(zone) && Arms.gearCombat(proto) < Arms.gearCombat(pro)
                && Arms.anomalyGuard(proto) > Arms.anomalyGuard(pro), "прототипы — середина между кустарным и заводским");
        ck(!Good.AMMO.mainland && !Good.GEAR.mainland && !Good.PROTO.mainland && Good.AMMO_PRO.mainland && Good.GEAR_PRO.mainland,
                "кустарное и прототипы не имеют рынка на Большой земле");

        long totalProtoSold = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = ZoneScenario.create(seed);
            boolean peaceOk = true;
            for (int d = 0; d < 720; d++) {
                s.step();
                for (Faction fa : s.factions) {
                    if (!fa.military && !Arms.specialNeed(fa) && (fa.targetOf(Good.AMMO_PRO) != 0 || fa.targetOf(Good.GEAR_PRO) != 0)) peaceOk = false;
                }
            }
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит сходится 720 дней");
            ck(peaceOk, "сид " + seed + ": без особой нужды заводское не закупают");
            ck(s.ledger.imported[Good.AMMO.ordinal()] == 0 && s.ledger.imported[Good.GEAR.ordinal()] == 0 && s.ledger.imported[Good.PROTO.ordinal()] == 0,
                    "сид " + seed + ": кустарное и прототипы не завозят с Большой земли");
            ck(s.ledger.exported[Good.GEAR.ordinal()] < 50 && s.ledger.exported[Good.PROTO.ordinal()] < 20,
                    "сид " + seed + ": кустарное и прототипы почти не выходят из Зоны (только с тел: "
                            + s.ledger.exported[Good.GEAR.ordinal()] + " / " + s.ledger.exported[Good.PROTO.ordinal()] + ")");
            ck(s.ledger.created[Good.PROTO.ordinal()] > 10, "сид " + seed + ": учёные собирают прототипы (" + s.ledger.created[Good.PROTO.ordinal()] + ")");
            int tradersProto = 0;
            for (Node n : s.nodes) if (n.trader) tradersProto += n.market.get(Good.PROTO);
            long protoInFactions = 0;
            for (Faction fa : s.factions) if (!fa.scientist) protoInFactions += fa.stock.get(Good.PROTO);
            totalProtoSold += tradersProto + protoInFactions;
            Faction m = s.military();
            ck(s.ledger.destroyed[Good.AMMO_PRO.ordinal()] > 0 && Arms.ammoValue(m.stock) > 0 || m.members < 10,
                    "сид " + seed + ": армия воюет заводскими патронами");
            ck(s.ledger.imported[Good.GEAR_PRO.ordinal()] > 20, "сид " + seed + ": с Большой земли идёт заводское снаряжение ("
                    + s.ledger.imported[Good.GEAR_PRO.ordinal()] + ")");
            String saved = s.save();
            ck(Simulation.load(saved).save().equals(saved), "сид " + seed + ": сохранение v11 идентично");
        }
        ck(totalProtoSold > 0, "прототипы доходят до торговцев и группировок (" + totalProtoSold + ")");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО: " + f);
        if (f > 0) System.exit(1);
    }
}
