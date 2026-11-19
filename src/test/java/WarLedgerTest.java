import ru.stalkerzone.sim.*;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * К5 (аудит 18.11): закон сохранения в войне.
 * <ul>
 *   <li>захват территории: лут 8% склада и кассы, ничего не создаётся и не исчезает;</li>
 *   <li>разгром базы: 25% денег атакующему, 20% склада сгорает через Ledger (у учёных 70% людей и 50% склада),
 *       база не захватывается, отрицательных остатков нет;</li>
 *   <li>захват по контракту: смена владельца без движения товаров и денег;</li>
 *   <li>длительный прогон kordon_war 365 дней × 4 сида: ежедневный audit, книга мутантов,
 *       отсутствие отрицательных остатков, война действительно идёт;</li>
 *   <li>воспроизводимость: повторный прогон сида даёт идентичное сохранение.</li>
 * </ul>
 * Точные доли лута проверяются вызовом приватных capture/rout через рефлексию (прецедент: AnomalyRadiusTest
 * читает приватный rng) — иначе доли не проверить детерминированно. Производственный код не меняется.
 */
public class WarLedgerTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) throws Exception {
        long t0 = System.currentTimeMillis();
        captureLoot();
        routBase();
        contractCaptureChecks();
        soak();
        reproducible();
        System.out.println("время: " + (System.currentTimeMillis() - t0) / 1000.0 + " с");
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------ helpers

    /** Две живые неслужебные не-Монолит фракции. */
    static Faction[] pair(Simulation s) {
        Faction x = null, y = null;
        for (Faction f : s.factions) {
            if (f.isService() || f.fanatic || f.members < 1) continue;
            if (x == null) x = f;
            else if (y == null) { y = f; break; }
        }
        return new Faction[]{x, y};
    }

    /** Выставить склад в известные значения, проводя разницу через Ledger — audit остаётся пустым. */
    static void forceStock(Simulation s, Stock st, int qtyEach, long money) {
        for (Good g : Good.ALL) {
            int delta = qtyEach - st.get(g);
            if (delta > 0) { st.add(g, delta); s.ledger.created[g.ordinal()] += delta; }
            else if (delta < 0) { st.take(g, -delta); s.ledger.destroyed[g.ordinal()] += -delta; }
        }
        long dm = money - st.money;
        if (dm > 0) { st.money += dm; s.ledger.moneyMinted += dm; }
        else if (dm < 0) { st.money += dm; s.ledger.moneyBurned += -dm; }
    }

    static boolean noNegative(Simulation s) {
        for (Faction f : s.factions) {
            if (f.stock.money < 0) return false;
            for (Good g : Good.ALL) if (f.stock.get(g) < 0) return false;
        }
        for (Node n : s.nodes) {
            if (n.market.money < 0 || n.depot.money < 0) return false;
            for (Good g : Good.ALL) if (n.market.get(g) < 0 || n.depot.get(g) < 0) return false;
        }
        for (Caravan c : s.caravans) {
            if (c.cargo.money < 0) return false;
            for (Good g : Good.ALL) if (c.cargo.get(g) < 0) return false;
        }
        for (Convoy c : s.logistics.convoys) {
            if (c.cargo.money < 0) return false;
            for (Good g : Good.ALL) if (c.cargo.get(g) < 0) return false;
        }
        for (Building b : s.buildings) if (b.haveMat < 0 || b.haveParts < 0) return false;
        return true;
    }

    static int mutants(Simulation s) {
        int sum = 0;
        for (Lair l : s.lairs) sum += l.mutantCount();
        return sum;
    }

    static Method privateMethod(String name) throws Exception {
        Method m = Simulation.class.getDeclaredMethod(name, Faction.class, Faction.class, Node.class);
        m.setAccessible(true);
        return m;
    }

    // ------------------------------------------------------------------ 1. захват: лут 8%

    static void captureLoot() throws Exception {
        Simulation s = ZoneScenario.create(5);
        s.step(20);
        ck(s.audit().isEmpty(), "захват: audit пуст до");
        Faction[] p = pair(s);
        Faction a = p[0], d = p[1];
        Node n = null;
        for (Node t : s.nodes) {
            if (t.id != a.baseNode && t.id != d.baseNode && !t.trader) { n = t; break; }
        }
        n.owner = d.id;
        forceStock(s, d.stock, 1000, 100_000);
        forceStock(s, a.stock, 0, 0);
        ck(s.audit().isEmpty(), "захват: audit пуст после подготовки");
        long moneyBefore = s.totalMoney();
        long[] created = s.ledger.created.clone(), destroyed = s.ledger.destroyed.clone();
        long minted = s.ledger.moneyMinted, burned = s.ledger.moneyBurned;
        int captured = a.nodesCaptured, lost = d.nodesLost;

        privateMethod("capture").invoke(s, a, d, n);

        ck(n.owner == a.id, "захват: владелец сменился на атакующего");
        for (Good g : Good.ALL) {
            ck(a.stock.get(g) == 80 && d.stock.get(g) == 920,
                    "захват: лут 8% «" + g.ru + "» (80 из 1000)");
        }
        ck(a.stock.money == 8000 && d.stock.money == 92_000, "захват: 8% кассы (8000 из 100000)");
        ck(Arrays.equals(created, s.ledger.created) && Arrays.equals(destroyed, s.ledger.destroyed)
                && minted == s.ledger.moneyMinted && burned == s.ledger.moneyBurned,
                "захват: ничего не создано и не уничтожено");
        ck(s.totalMoney() == moneyBefore, "захват: сумма денег не изменилась");
        ck(s.audit().isEmpty(), "захват: audit пуст после");
        ck(noNegative(s), "захват: отрицательных остатков нет");
        ck(a.nodesCaptured == captured + 1 && d.nodesLost == lost + 1, "захват: счётчики захватов/потерь");
    }

    // ------------------------------------------------------------------ 2. разгром базы

    static void routBase() throws Exception {
        Method rout = privateMethod("rout");

        Simulation s = ZoneScenario.create(6);
        s.step(20);
        Faction[] p = pair(s);
        Faction a = p[0], d = p[1];
        Node n = s.nodes.get(d.baseNode);
        forceStock(s, d.stock, 1000, 100_000);
        forceStock(s, a.stock, 0, 0);
        d.members = 100; d.wounded = 0; d.deaths = 0; d.scientist = false;
        long moneyBefore = s.totalMoney();
        long[] destroyed = s.ledger.destroyed.clone();

        rout.invoke(s, a, d, n);

        ck(d.members == 65 && d.deaths == 35, "разгром: погибло 35% (100 → 65)");
        for (Good g : Good.ALL) {
            ck(d.stock.get(g) == 800, "разгром: сгорело 20% «" + g.ru + "»");
            ck(s.ledger.destroyed[g.ordinal()] == destroyed[g.ordinal()] + 200,
                    "разгром: сгоревшее учтено в Ledger «" + g.ru + "»");
        }
        ck(a.stock.money == 25_000 && d.stock.money == 75_000, "разгром: 25% денег атакующему");
        ck(s.totalMoney() == moneyBefore, "разгром: сумма денег не изменилась");
        ck(n.owner == d.id, "разгром: база не захвачена");
        ck(s.audit().isEmpty(), "разгром: audit пуст");
        ck(noNegative(s), "разгром: отрицательных остатков нет");

        // учёные: 70% людей, 50% склада, исследования уничтожены
        Simulation s2 = ZoneScenario.create(7);
        s2.step(20);
        Faction[] p2 = pair(s2);
        Faction a2 = p2[0], d2 = p2[1];
        Node n2 = s2.nodes.get(d2.baseNode);
        forceStock(s2, d2.stock, 1000, 100_000);
        forceStock(s2, a2.stock, 0, 0);
        d2.members = 100; d2.wounded = 0; d2.deaths = 0; d2.scientist = true;
        s2.scorcher.research = 50; s2.scorcher.psiTech = 40;

        rout.invoke(s2, a2, d2, n2);

        ck(d2.members == 30 && d2.deaths == 70, "разгром учёных: погибло 70% (100 → 30)");
        for (Good g : Good.ALL) ck(d2.stock.get(g) == 500, "разгром учёных: сгорело 50% «" + g.ru + "»");
        ck(a2.stock.money == 25_000 && d2.stock.money == 75_000, "разгром учёных: 25% денег атакующему");
        ck(s2.scorcher.research == 0 && s2.scorcher.psiTech == 0, "разгром учёных: исследования потеряны");
        ck(s2.audit().isEmpty(), "разгром учёных: audit пуст");
        ck(noNegative(s2), "разгром учёных: отрицательных остатков нет");
    }

    // ------------------------------------------------------------------ 3. захват по контракту

    static void contractCaptureChecks() {
        Simulation s = ZoneScenario.create(8);
        s.step(10);
        Faction t = null, c = null, o = null;
        for (Faction f : s.factions) {
            if (f.isService() || f.fanatic || f.members < 1) continue;
            if (t == null) t = f;
            else if (c == null) c = f;
            else if (o == null) { o = f; break; }
        }
        Node n = null, m = null;
        for (Node x : s.nodes) {
            if (x.id == t.baseNode || x.id == c.baseNode || x.id == o.baseNode || x.trader) continue;
            if (n == null) n = x;
            else if (m == null) { m = x; break; }
        }
        n.owner = o.id;
        n.supply = 0.8;
        m.owner = o.id;
        ck(s.audit().isEmpty(), "контракт-захват: audit пуст до");
        long moneyBefore = s.totalMoney();
        long[] created = s.ledger.created.clone(), destroyed = s.ledger.destroyed.clone();
        long minted = s.ledger.moneyMinted, burned = s.ledger.moneyBurned;
        int captured = c.nodesCaptured, lost = o.nodesLost, execCaptured = t.nodesCaptured;

        s.contractCapture(t, c.id, n.id);

        ck(n.owner == c.id, "контракт-захват: узел перешёл заказчику");
        ck(c.nodesCaptured == captured + 1 && o.nodesLost == lost + 1, "контракт-захват: счётчики");
        ck(n.supply == 0.4, "контракт-захват: снабжённость уменьшена вдвое (0.8 → 0.4)");
        ck(s.totalMoney() == moneyBefore, "контракт-захват: деньги не двигались");
        ck(Arrays.equals(created, s.ledger.created) && Arrays.equals(destroyed, s.ledger.destroyed)
                && minted == s.ledger.moneyMinted && burned == s.ledger.moneyBurned,
                "контракт-захват: товары не создавались и не исчезали");
        ck(s.audit().isEmpty(), "контракт-захват: audit пуст после");
        ck(noNegative(s), "контракт-захват: отрицательных остатков нет");

        s.contractCapture(t, -1, m.id);
        ck(m.owner == t.id && t.nodesCaptured == execCaptured + 1,
                "контракт-захват: без заказчика узел достаётся исполнителю");
        ck(s.audit().isEmpty(), "контракт-захват: audit пуст после второго");
    }

    // ------------------------------------------------------------------ 4. соак kordon_war 365×4

    static void soak() {
        int totalWars = 0, totalCaptures = 0;
        for (long seed : new long[]{21, 22, 23, 24}) {
            Simulation s = KordonWar.create(seed);
            int start = mutants(s);
            String auditBad = null, bookBad = null, negBad = null;
            for (int d = 0; d < 365; d++) {
                s.step();
                String aud = s.audit();
                if (!aud.isEmpty() && auditBad == null) auditBad = "д" + s.day + ": " + aud.trim();
                int now = mutants(s);
                if (now != start + s.ledger.mutantsBorn - s.ledger.mutantsDied && bookBad == null) {
                    bookBad = "д" + s.day + ": " + now + " != " + start + "+" + s.ledger.mutantsBorn + "-" + s.ledger.mutantsDied;
                }
                if (!noNegative(s) && negBad == null) negBad = "д" + s.day;
            }
            ck(auditBad == null, "соак seed " + seed + ": ежедневный audit пуст" + (auditBad == null ? "" : " — " + auditBad));
            ck(bookBad == null, "соак seed " + seed + ": книга мутантов сходится ежедневно" + (bookBad == null ? "" : " — " + bookBad));
            ck(negBad == null, "соак seed " + seed + ": отрицательных остатков нет" + (negBad == null ? "" : " — " + negBad));
            int wars = 0, caps = 0;
            for (Faction f : s.factions) { wars += f.warsStarted; caps += f.nodesCaptured; }
            ck(wars > 0 && caps > 0, "соак seed " + seed + ": война шла (войн " + wars + ", захватов " + caps + ")");
            ck(s.audit().isEmpty(), "соак seed " + seed + ": итоговая согласованность ресурсов");
            totalWars += wars;
            totalCaptures += caps;
            System.out.println("kordon_war seed " + seed + " 365 дн: войн " + wars + ", захватов " + caps
                    + ", мутанты родилось/погибло " + s.ledger.mutantsBorn + "/" + s.ledger.mutantsDied);
        }
        ck(totalWars >= 8, "суммарно за 4 сида войн не меньше 8 (факт " + totalWars + ")");
        ck(totalCaptures >= 8, "суммарно за 4 сида захватов не меньше 8 (факт " + totalCaptures + ")");
    }

    // ------------------------------------------------------------------ 5. воспроизводимость

    static void reproducible() {
        Simulation s1 = KordonWar.create(21);
        s1.step(365);
        Simulation s2 = KordonWar.create(21);
        s2.step(365);
        ck(s1.save().equals(s2.save()), "повторный прогон сида 21 даёт идентичное сохранение");
    }
}