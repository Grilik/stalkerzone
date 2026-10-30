package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Логистика группировок (v1.10). Всё физическое: у каждой точки свой склад и сейф ({@link Node#depot}),
 * гарнизон ест, получает жалованье и тратит патроны именно оттуда, а привозят всё конвои ({@link Convoy}).
 *
 * <p>Цепочки снабжения:
 * <ul>
 *   <li>база фракции → точки: еда, патроны, медикаменты, наличные на жалованье, стройматериалы;</li>
 *   <li>точки → база: урожай, детали, артефакты, лишние деньги (ресурсные точки кормят базу);</li>
 *   <li>военные: учёные → ближайшая полевая база (деньги, детали, еда, медицина по контракту) → КПП «Периметр»;
 *       на КПП деньги превращаются в патроны, снаряжение и новобранцев с Большой земли, которые конвоями уходят на базы.</li>
 * </ul>
 * Недостаток снабжения копит сепаратизм гарнизона и вызывает дезертирство; дезертиры уносят из сейфа деньги и патроны.
 */
public final class Logistics {
    /** Жалованье бойца гарнизона, руб. в день. */
    public static final int WAGE = 4;
    /** На сколько дней точка держит запас. */
    static final int STOCK_DAYS = 8;
    /** Оборонный грант учёным на содержание армии по контракту (руб. в день, приходит в бункер учёных). */
    public static final int DEFENSE_GRANT = 300;
    /** Мировые цены Большой земли на КПП. */
    static final int MAIN_AMMO = 12, MAIN_GEAR = 280, MAIN_MEDS = 45, MAIN_RECRUIT = 400;

    public final List<Convoy> convoys = new ArrayList<>();
    /** Конвои, развёрнутые по дороге (добавляются после обхода списка). */
    private final List<Convoy> rerouted = new ArrayList<>();

    // контракт учёных с армией
    public boolean contractActive = true;
    /** Большая земля заморозила оборонный грант (событие или проверка). */
    public boolean grantFrozen;
    /** Сколько конвоев с платой учёных дошло до полевых баз армии. */
    public int contractDelivered;
    public int contractNextDay = 4;
    public int contractPaid, contractMissed, contractMissedRow;
    public long contractMoney;
    /** Выполнение обязательств армией: доля удерживаемых полевых баз на дороге к учёным. */
    public double contractDuty = 1;

    // статистика
    public int convoysSent, convoysArrived, convoysLost, convoysRobbed, deserters, secessions, recruitsBought;
    /** Доходы группировок по их цепочкам: охрана Бара, рэкет бандитов, контрабанда Свободы (всего, руб.). */
    public long dutyProtection, banditRacket, freedomSmuggled;
    /** Сколько учёные потратили на Большой земле на патроны, медицину и снаряжение для армии. */
    public long armySpent;
    public long cashDelivered, cashRobbed;

    // ------------------------------------------------------------------ гарнизоны

    /** Гарнизон точки (бойцы, живущие на ней постоянно). База фракции снабжается со склада фракции, у неё гарнизона нет. */
    public static int garrison(Simulation s, Faction f, Node n) {
        if (n.owner != f.id || n.id == f.baseNode || f.fanatic) return 0;
        int owned = Math.max(1, s.ownedCount(f));
        int g = Math.max(1, (int) Math.round(f.members * 0.35 / owned));
        // полевые базы армии держат усиленный гарнизон
        if (f.military && isFieldBaseSite(n)) g = Math.max(g, Math.min(10, f.members / 7));
        return g;
    }

    /** Сколько людей фракции живёт вне базы (едят со складов точек). */
    public static int outside(Simulation s, Faction f) {
        if (f.fanatic) return 0;
        int c = 0;
        for (Node n : s.nodes) c += garrison(s, f, n);
        return Math.min(c, Math.max(0, f.members - 3));
    }

    /** Полевые базы армии: её точки, кроме КПП. */
    public static boolean fieldBase(Simulation s, Node n) {
        Faction m = s.military();
        return m != null && n.owner == m.id && n.id != m.baseNode && n.type == NodeType.OUTPOST;
    }

    /** Стартовые склады точек: часть запасов фракции уже развезена по гарнизонам. */
    void seedDepots(Simulation s) {
        for (Node n : s.nodes) {
            if (n.owner < 0) continue;
            Faction f = s.factions.get(n.owner);
            int g = garrison(s, f, n);
            if (g <= 0) continue;
            Stock.move(f.stock, n.depot, Good.FOOD, g * 6);
            Stock.move(f.stock, n.depot, f.military ? Good.AMMO_PRO : Good.AMMO, g * 12);
            Stock.move(f.stock, n.depot, Good.MEDS, Math.max(1, g / 2));
            if (f.military) Stock.move(f.stock, n.depot, Good.GEAR_PRO, g / 3);
            Stock.moveMoney(f.stock, n.depot, (long) g * WAGE * STOCK_DAYS);
        }
    }

    // ------------------------------------------------------------------ день

    void daily(Simulation s, Random r) {
        for (Node n : s.nodes) if (n.owner >= 0) garrisonDay(s, s.factions.get(n.owner), n, r);
        contract(s, r);
        if (s.day % 7 == 3) factionIncome(s, r);
        Faction m = s.military();
        if (m != null && m.members > 3 && s.day % 3 == 0) mainland(s, m, r);
        for (Faction f : s.factions) {
            if (f.fanatic || f.members < 3 || f.isService()) continue;
            dispatchSupply(s, f, r);
            dispatchReturn(s, f, r);
        }
        advance(s, r);
    }

    /** Гарнизон ест, получает жалованье и тратит патроны со склада точки. Отсюда снабжённость, сепаратизм, дезертирство. */
    private void garrisonDay(Simulation s, Faction f, Node n, Random r) {
        int g = garrison(s, f, n);
        if (g <= 0) { n.supply = Math.min(1, n.supply + 0.05); n.separatism = Math.max(0, n.separatism - 0.02); return; }
        Stock d = n.depot;
        int needFood = g, needAmmo = s.randomRound(g * 0.3), needMeds = s.randomRound(g * 0.04);
        long needCash = (long) g * WAGE;
        int food = d.take(Good.FOOD, needFood), meds = d.take(Good.MEDS, needMeds);
        int ammo = (int) Math.floor(Arms.spendAmmo(s, d, needAmmo, f.military ? 1 : 0));
        s.ledger.destroyed[Good.FOOD.ordinal()] += food;
        s.ledger.destroyed[Good.MEDS.ordinal()] += meds;
        long cash = Math.min(d.money, needCash);
        d.money -= cash;
        s.ledger.moneyBurned += cash;
        // нехватка за день: голод и невыплата жалованья бьют сильнее всего
        double lack = 0.42 * (1 - ratio(food, needFood)) + 0.36 * (1 - ratio(cash, needCash))
                + 0.14 * (1 - ratio(ammo, needAmmo)) + 0.08 * (1 - ratio(meds, needMeds));
        n.supply += (1 - lack - n.supply) * 0.15;

        double discipline = f.military ? 0.6 : f.scientist ? 0.5 : 1.0;
        double hardship = Math.max(0, 0.85 - n.supply);
        if (hardship > 0) n.separatism += hardship * 0.08 * discipline * (1.35 - f.unity);
        else n.separatism = Math.max(0, n.separatism - 0.02);
        n.separatism = Math.min(1, n.separatism);

        if (hardship > 0) {
            int k = Math.min(s.randomRound(g * hardship * 0.025 * discipline), Math.max(0, f.members - 5));
            if (k > 0) desert(s, f, n, k, r);
        }
        if (n.separatism >= 1) secede(s, f, n, r);
    }

    private static double ratio(long got, long need) { return need <= 0 ? 1 : (double) got / need; }

    /** Дезертиры уходят с точки и уносят деньги и патроны из сейфа: к бандитам, к одиночкам или из Зоны. */
    private void desert(Simulation s, Faction f, Node n, int k, Random r) {
        f.members -= k;
        deserters += k;
        Faction to = null;
        for (Faction g : s.factions) {
            if (g == f || !g.takesNewcomers || g.members < 3 || s.housingOf(g) - g.members < k) continue;
            boolean bandits = g.raider;
            if (bandits && r.nextDouble() < 0.45 || !bandits && to == null) to = g;
        }
        Stock loot = new Stock();
        Stock.moveMoney(n.depot, loot, Math.min(n.depot.money, 40L * k));
        Stock.move(n.depot, loot, Good.AMMO, 20 * k);
        Stock.move(n.depot, loot, Good.AMMO_PRO, 12 * k);
        Stock.move(n.depot, loot, Good.GEAR_PRO, k / 2);
        Stock.move(n.depot, loot, Good.FOOD, 3 * k);
        String where;
        if (to != null) {
            to.members += k;
            for (Good g : Good.ALL) Stock.move(loot, to.stock, g, loot.get(g));
            Stock.moveMoney(loot, to.stock, loot.money);
            where = "ушли к «" + to.name + "»";
        } else {
            for (Good g : Good.ALL) s.ledger.exported[g.ordinal()] += loot.take(g, loot.get(g));
            s.ledger.moneyBurned += loot.money;
            loot.money = 0;
            where = "ушли из Зоны";
        }
        if (k >= 2 || f.military) s.log("ДЕЗЕРТИРСТВО: " + k + " бойцов «" + f.name + "» с «" + n.name + "» без снабжения " + where + ", прихватив деньги и патроны");
    }

    /** Сепаратизм дошёл до предела: гарнизон откалывается вместе со складом точки. */
    private void secede(Simulation s, Faction f, Node n, Random r) {
        int g = Math.min(garrison(s, f, n), Math.max(0, f.members - 5));
        Faction to = null;
        double best = 0.4;
        for (Faction o : s.factions) {
            if (o == f || o.fanatic || o.isService() || o.members < 5 || s.minDistFromOwned(o, n.id) > 1) continue;
            double h = s.hostilityTo(f, o.id);
            if (h < best) { best = h; to = o; }
        }
        secessions++;
        f.nodesLost++;
        f.members -= g;
        f.unity = Math.max(0, f.unity - 0.06);
        n.separatism = 0;
        n.supply = 0.6;
        if (to != null) {
            n.owner = to.id;
            to.members += g;
            to.nodesCaptured++;
            s.log("СЕПАРАТИЗМ: гарнизон «" + f.name + "» на «" + n.name + "» без снабжения перешёл к «" + to.name + "» (" + g + " чел., склад точки тоже)");
        } else {
            n.owner = -1;
            s.log("СЕПАРАТИЗМ: гарнизон «" + f.name + "» на «" + n.name + "» без снабжения разошёлся, склад брошен");
        }
        s.dropProjects(n.id);
    }

    // ------------------------------------------------------------------ армия: контракт с учёными и Большая земля

    /** Раз в неделю учёные отправляют армии плату по контракту на ближайшую полевую базу. */
    private void contract(Simulation s, Random r) {
        Faction sci = s.scientists(), m = s.military();
        if (sci == null || m == null) return;
        // оборонный грант приходит в отдельный сейф бункера учёных: из него платят армии
        Node lab = s.nodes.get(sci.baseNode);
        if (sci.members > 0 && lab.owner == sci.id && !grantFrozen) {
            lab.depot.money += DEFENSE_GRANT;
            s.ledger.moneyMinted += DEFENSE_GRANT;
        }
        if (lab.owner == sci.id && lab.depot.money > 4000) Stock.moveMoney(lab.depot, sci.stock, lab.depot.money - 4000);
        if (s.day < contractNextDay) return;
        contractNextDay = s.day + 7;
        // обязательства армии: держать полевые базы на дороге к учёным
        int bases = 0, held = 0;
        for (Node n : s.nodes) {
            if (!isFieldBaseSite(n)) continue;
            bases++;
            if (n.owner == m.id) held++;
        }
        contractDuty = bases == 0 ? 1 : (double) held / bases;
        if (!contractActive) {
            if (sci.stock.money >= 3500 && held > 0) {
                contractActive = true;
                contractMissedRow = 0;
                s.log("Учёные возобновили контракт с армией");
            }
            return;
        }
        Node dest = nearestFieldBase(s, m, sci.baseNode);
        if (dest == null) dest = s.nodes.get(m.baseNode);
        if (!s.graph.reachable(sci.baseNode, dest.id)) return;
        Stock pay = new Stock();
        long due = Math.round(DEFENSE_GRANT * 7 * (0.4 + 0.6 * contractDuty));
        // на Большой земле покупают для армии только учёные: заказ приходит в оборонный сейф и уезжает со следующей платой
        // бюджет закупки: доля недельной платы плюс то, что скопилось в оборонном сейфе (дорогой комплект берут, когда накопят)
        long spent = armyOrder(s, sci, m, lab, (long) (due * 0.75) + Math.max(0, lab.depot.money - due * 2), r);
        // платят только из оборонного сейфа; что армия не заработала невыполнением обязательств, остаётся там
        Stock.moveMoney(lab.depot, pay, Math.max(0, due - spent));
        for (Good g : new Good[] {Good.AMMO_PRO, Good.MEDS, Good.GEAR_PRO, Good.AMMO, Good.GEAR}) Stock.move(lab.depot, pay, g, lab.depot.get(g));
        // товары — только излишки учёных: свои исследования и люди важнее
        Stock.move(sci.stock, pay, Good.FOOD, Math.min(60, sci.stock.get(Good.FOOD) - sci.members * 10));
        Stock.move(sci.stock, pay, Good.PARTS, Math.min(30, sci.stock.get(Good.PARTS) - 80 - sci.members * 6));
        Stock.move(sci.stock, pay, Good.MEDS, Math.min(8, sci.stock.get(Good.MEDS) - sci.targetOf(Good.MEDS) * 2));
        Stock.move(sci.stock, pay, Good.MATERIALS, Math.min(20, sci.stock.get(Good.MATERIALS) - sci.targetOf(Good.MATERIALS)));
        long value = pay.money;
        for (Good g : Good.ALL) value += (long) pay.get(g) * g.base;
        if (value < 300) {
            contractMissed++;
            contractMissedRow++;
            m.unity = Math.max(0, m.unity - 0.04);
            s.log("Учёные не смогли заплатить армии по контракту (" + pay.money + " руб.)");
            if (contractMissedRow >= 3) {
                contractActive = false;
                s.log("КОНТРАКТ РАЗОРВАН: армия больше не получает денег от учёных, гарнизоны переходят на старые запасы");
            }
        } else {
            contractPaid++;
            contractMissedRow = 0;
        }
        contractMoney += pay.money;
        int[] route = s.graph.path(sci.baseNode, dest.id);
        Convoy c = new Convoy(m.id, Convoy.Kind.CONTRACT, route, Math.min(4, Math.max(1, m.members / 15)));
        for (Good g : Good.ALL) Stock.move(pay, c.cargo, g, pay.get(g));
        Stock.moveMoney(pay, c.cargo, pay.money);
        launch(s, c);
    }

    /** Заказ учёных на Большой земле для армии: патроны, медицина, снаряжение по нужде армии и ценам рынка. */
    private long armyOrder(Simulation s, Faction sci, Faction m, Node lab, long budget, Random r) {
        long spent = 0;
        // сначала табельное снаряжение и медицина, патроны на остаток бюджета
        for (Good g : new Good[] {Good.GEAR_PRO, Good.MEDS, Good.AMMO_PRO}) {
            int have = m.stock.get(g) + lab.depot.get(g) + s.world.armyInTransit(g);
            for (Node n : s.nodes) if (n.owner == m.id) have += n.depot.get(g);
            int target = m.targetOf(g);
            int need = target - have;
            if (need <= 0) continue;
            double urgency = Math.min(1, (double) need / Math.max(1, target));
            int q = s.world.decideBuy(g, need, urgency, Math.min(budget - spent, lab.depot.money));
            spent += s.world.order(s, lab.depot, g, q, sci.id, sci.baseNode, false, 3 + r.nextInt(3), true);
        }
        armySpent += spent;
        return spent;
    }

    /** Точки, которые армия по контракту держит на дороге к учёным (полевые базы сценария). */
    public static boolean isFieldBaseSite(Node n) {
        return n.name.startsWith("База военных");
    }

    private static Node nearestFieldBase(Simulation s, Faction m, int from) {
        Node best = null;
        int bd = Integer.MAX_VALUE;
        for (Node n : s.nodes) {
            if (n.owner != m.id || n.id == m.baseNode) continue;
            int d = s.graph.dist(from, n.id);
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }

    /** КПП: деньги армии уходят на призыв — государство присылает новобранцев. */
    private void mainland(Simulation s, Faction m, Random r) {
        if (s.nodes.get(m.baseNode).owner != m.id) return;
        Stock st = m.stock;
        long reserve = m.members * 8L + (long) outside(s, m) * WAGE * STOCK_DAYS;
        long budget = st.money - reserve;
        if (budget <= 0) return;
        // патроны и снаряжение армия получает только через учёных (глобальный рынок им недоступен);
        // на КПП деньги идут на новобранцев от государства
        int room = s.housingOf(m) - m.members;
        int recruits = (int) Math.min(Math.min(room, 3), budget / MAIN_RECRUIT);
        if (recruits > 0 && st.get(Good.FOOD) >= m.members) {
            long cost = (long) recruits * MAIN_RECRUIT;
            st.money -= cost;
            s.ledger.moneyBurned += cost;
            m.exp = (m.exp * m.members + 25.0 * recruits) / (m.members + recruits);
            m.members += recruits;
            recruitsBought += recruits;
            // новобранец приходит с табельным: автомат, бронежилет и боекомплект (входит в цену призыва)
            int kits = recruits;
            st.add(Good.GEAR_PRO, kits);
            s.ledger.imported[Good.GEAR_PRO.ordinal()] += kits;
            st.add(Good.AMMO_PRO, recruits * 20);
            s.ledger.imported[Good.AMMO_PRO.ordinal()] += recruits * 20L;
        }
    }

    private static long buy(Simulation s, Stock st, Good g, int q, int price) {
        if (q <= 0) return 0;
        long cost = (long) q * price;
        st.money -= cost;
        s.ledger.moneyBurned += cost;
        st.add(g, q);
        s.ledger.imported[g.ordinal()] += q;
        return cost;
    }

    // ------------------------------------------------------------------ свои цепочки у каждой группировки

    /**
     * Еженедельные доходы, у каждой группировки свой источник (деньги везут конвоем, их можно перехватить):
     * Долг берёт с торговца Бара плату за охрану, бандиты собирают дань с соседних точек одиночек,
     * Свобода через армейские склады закупает контрабандой патроны и снаряжение. Одиночки живут торговлей
     * артефактами у Сидоровича (караваны), учёные — грантами, армия — контрактом, Монолит — паломниками.
     */
    private void factionIncome(Simulation s, Random r) {
        for (Faction f : s.factions) {
            if (f.members < 5 || f.fanatic || f.isService() || s.nodes.get(f.baseNode).owner != f.id) continue;
            Node base = s.nodes.get(f.baseNode);
            if (f.name.equals("Долг") && base.trader) {
                long fee = Math.min(500, base.market.money / 40);
                if (fee > 0) {
                    Stock.moveMoney(base.market, f.stock, fee);
                    dutyProtection += fee;
                }
            } else if (f.raider) {
                racket(s, f, r);
            } else if (f.name.equals("Свобода") && base.name.equals("Армейские склады")) {
                // контрабанда: продажные прапорщики армии сбывают Свободе излишки со складов по цене Большой земли с наценкой
                Faction m = s.military();
                if (m == null || m.members < 5) continue;
                long budget = Math.min(f.stock.money - f.members * 15L, 1200);
                if (budget < 200) continue;
                long spent = 0;
                // заводское: Свобода вооружает им свои ударные группы
                for (Good g : new Good[] {Good.AMMO_PRO, Good.GEAR_PRO}) {
                    int spare = m.stock.get(g) - m.targetOf(g);
                    int want = f.targetOf(g) - f.stock.get(g);
                    long unit = Math.round(s.world.price[g.ordinal()] * 1.3);
                    int q = (int) Math.min(Math.min(spare, want), (budget - spent) / Math.max(1, unit));
                    if (q <= 0) continue;
                    Stock.move(m.stock, f.stock, g, q);
                    Stock.moveMoney(f.stock, m.stock, q * unit);
                    spent += q * unit;
                }
                freedomSmuggled += spent;
            }
        }
    }

    /** Рэкет: бандиты отправляют сборщиков к ближайшей чужой точке, где сила на их стороне; дань едет к ним конвоем. */
    private void racket(Simulation s, Faction b, Random r) {
        Node victim = null;
        int bd = 99;
        for (Node n : s.nodes) {
            if (n.owner < 0 || n.owner == b.id) continue;
            Faction o = s.factions.get(n.owner);
            if (o.fanatic || o.military || o.scientist || s.politics.truce(b.id, o.id)) continue;
            if (o.members > b.members * 1.4) continue;
            int d = s.minDistFromOwned(b, n.id);
            if (d < bd && d <= 2) { bd = d; victim = n; }
        }
        if (victim == null) return;
        Faction o = s.factions.get(victim.owner);
        Stock from = victim.id == o.baseNode ? o.stock : victim.depot;
        long take = Math.min(from.money / 10, 600);
        if (take < 50) return;
        int[] route = s.graph.path(victim.id, b.baseNode);
        Convoy c = new Convoy(b.id, Convoy.Kind.TRIBUTE, route, Math.min(4, Math.max(1, b.members / 12)));
        Stock.moveMoney(from, c.cargo, take);
        Stock.move(from, c.cargo, Good.FOOD, Math.min(from.get(Good.FOOD) / 20, 30));
        o.resent(b.id, 0.3);
        banditRacket += take;
        s.log("Бандиты собрали дань с «" + o.name + "» на «" + victim.name + "»: " + take + " руб.");
        launch(s, c);
    }

    // ------------------------------------------------------------------ отправка конвоев

    private int active(Faction f) {
        int c = 0;
        for (Convoy cv : convoys) if (cv.owner == f.id) c++;
        return c;
    }

    private boolean headingTo(Faction f, int node) {
        for (Convoy cv : convoys) if (cv.owner == f.id && cv.dest() == node && cv.kind != Convoy.Kind.RETURN) return true;
        return false;
    }

    private boolean leaving(Faction f, int node) {
        for (Convoy cv : convoys) if (cv.owner == f.id && cv.from() == node && cv.kind == Convoy.Kind.RETURN) return true;
        return false;
    }

    /** Желаемый запас точки на STOCK_DAYS дней. */
    static int want(Simulation s, Faction f, Node n, Good g) {
        int gar = garrison(s, f, n);
        return switch (g) {
            case FOOD -> gar * STOCK_DAYS;
            case AMMO -> f.military ? 0 : gar * 12;
            case AMMO_PRO -> f.military ? gar * 12 : 0;
            case MEDS -> Math.max(1, gar / 2);
            case GEAR_PRO -> f.military ? gar / 3 : 0;
            case MATERIALS -> s.projectNeed(n.id, true);
            case PARTS -> s.projectNeed(n.id, false);
            default -> 0;
        };
    }

    /** База отправляет конвой на самую плохо снабжённую точку. */
    private void dispatchSupply(Simulation s, Faction f, Random r) {
        if (s.nodes.get(f.baseNode).owner != f.id) return;
        int max = 1 + s.ownedCount(f) / 3 + (f.military ? 1 : 0);
        if (active(f) >= max) return;
        Node pick = null;
        double worst = 2;
        for (Node n : s.nodes) {
            if (n.owner != f.id || n.id == f.baseNode || headingTo(f, n.id)) continue;
            if (!s.graph.reachable(f.baseNode, n.id)) continue;
            int gar = garrison(s, f, n);
            boolean low = n.depot.get(Good.FOOD) < gar * 3 || n.depot.money < (long) gar * WAGE * 3 || Arms.ammoValue(n.depot) < gar * 3
                    || n.depot.get(Good.MATERIALS) < want(s, f, n, Good.MATERIALS) || n.depot.get(Good.PARTS) < want(s, f, n, Good.PARTS);
            if (!low) continue;
            double score = n.supply - (f.military && fieldBase(s, n) ? 0.1 : 0);
            if (score < worst) { worst = score; pick = n; }
        }
        if (pick == null) return;
        Stock st = f.stock;
        int hq = Math.max(1, f.members - outside(s, f));
        int[] reserve = new int[Good.ALL.length];
        reserve[Good.FOOD.ordinal()] = hq * 3;
        reserve[Good.AMMO.ordinal()] = f.military ? 0 : f.members * 10;
        reserve[Good.AMMO_PRO.ordinal()] = f.military ? f.members * 10 : f.stock.get(Good.AMMO_PRO); // заводское — для своих групп на базе
        reserve[Good.MEDS.ordinal()] = 2;
        reserve[Good.GEAR.ordinal()] = f.members / 4;
        reserve[Good.PROTO.ordinal()] = f.stock.get(Good.PROTO);
        reserve[Good.GEAR_PRO.ordinal()] = f.military ? f.members / 4 : f.stock.get(Good.GEAR_PRO);
        reserve[Good.MATERIALS.ordinal()] = s.projectNeed(f.baseNode, true);
        reserve[Good.PARTS.ordinal()] = f.members * 3 + s.projectNeed(f.baseNode, false);
        int guards = Math.min(4, Math.max(1, f.members / 15));
        Convoy c = new Convoy(f.id, Convoy.Kind.SUPPLY, s.graph.path(f.baseNode, pick.id), guards);
        for (Good g : Good.ALL) {
            if (g == Good.ARTIFACT) continue;
            int need = want(s, f, pick, g) - pick.depot.get(g);
            int can = st.get(g) - reserve[g.ordinal()];
            if (need > 0 && can > 0) Stock.move(st, c.cargo, g, Math.min(need, can));
        }
        long needCash = (long) garrison(s, f, pick) * WAGE * STOCK_DAYS - pick.depot.money;
        long canCash = st.money - f.members * 6L;
        if (needCash > 0 && canCash > 0) Stock.moveMoney(st, c.cargo, Math.min(needCash, canCash));
        if (c.value() < 60) { unloadTo(s, c, f.stock); return; }
        // с КПП вместе с грузом едут бойцы пополнения
        if (f.military && fieldBase(s, pick)) c.troops = Math.min(4, Math.max(0, f.members / 20));
        launch(s, c);
    }

    /** Точки отправляют на базу урожай, детали, артефакты и лишние деньги. Полевые базы армии везут на КПП всё, что пришло от учёных. */
    private void dispatchReturn(Simulation s, Faction f, Random r) {
        if (s.nodes.get(f.baseNode).owner != f.id) return;
        for (Node n : s.nodes) {
            if (n.owner != f.id || n.id == f.baseNode || leaving(f, n.id) || headingTo(f, n.id)) continue;
            if (!s.graph.reachable(n.id, f.baseNode)) continue;
            boolean field = f.military && fieldBase(s, n);
            int gar = garrison(s, f, n);
            Stock d = n.depot;
            Convoy c = new Convoy(f.id, Convoy.Kind.RETURN, s.graph.path(n.id, f.baseNode), Math.min(3, Math.max(1, gar / 2)));
            for (Good g : Good.ALL) {
                int keep = g == Good.ARTIFACT ? 0 : (int) Math.ceil(want(s, f, n, g) * (field ? 1.0 : 1.5));
                int extra = d.get(g) - keep;
                if (extra > (g == Good.ARTIFACT ? 0 : 10)) Stock.move(d, c.cargo, g, extra);
            }
            long keepCash = (long) gar * WAGE * (field ? STOCK_DAYS : STOCK_DAYS * 2L);
            if (d.money - keepCash > 100) Stock.moveMoney(d, c.cargo, d.money - keepCash);
            if (c.value() < (field ? 150 : 400)) { unloadTo(s, c, d); continue; }
            launch(s, c);
        }
    }

    private void launch(Simulation s, Convoy c) {
        convoysSent++;
        if (c.route.length <= 1) { arrive(s, c); return; }
        c.leg = 0;
        c.daysLeft = s.graph.edgeDays(c.route[0], c.route[1]);
        convoys.add(c);
    }

    // ------------------------------------------------------------------ движение

    private void advance(Simulation s, Random r) {
        for (Iterator<Convoy> it = convoys.iterator(); it.hasNext(); ) {
            Convoy c = it.next();
            Faction f = s.factions.get(c.owner);
            int from = c.route[c.leg], to = c.route[c.leg + 1];
            double lair = 1 + Math.min(1.0, 0.01 * (s.nodeLairPower(from) + s.nodeLairPower(to)));
            double field = 1 + 0.4 * ((1 - s.knowledgeOf(from)) + (1 - s.knowledgeOf(to)));
            double pHit = s.graph.danger(from, to) * lair * field * (1 - Math.min(0.6, 0.08 * (c.guards + c.troops)));
            if (r.nextDouble() < pHit) mutants(s, c, f, r);
            if (--c.daysLeft > 0) continue;
            c.leg++;
            if (ambush(s, c, f, c.at(), r)) { it.remove(); continue; }
            if (c.leg < c.route.length - 1) {
                c.daysLeft = s.graph.edgeDays(c.route[c.leg], c.route[c.leg + 1]);
                continue;
            }
            it.remove();
            arrive(s, c);
        }
        convoys.addAll(rerouted);
        rerouted.clear();
    }

    private void mutants(Simulation s, Convoy c, Faction f, Random r) {
        double loss = 0.1 + 0.25 * r.nextDouble();
        for (Good g : Good.ALL) {
            int q = (int) (c.cargo.get(g) * loss * (g == Good.FOOD ? 1.5 : 1));
            s.ledger.destroyed[g.ordinal()] += c.cargo.take(g, q);
        }
        int dead = Math.min(c.guards + c.troops, r.nextInt(2 + (c.guards + c.troops) / 3));
        int fromTroops = Math.min(dead, c.troops);
        c.troops -= fromTroops;
        c.guards -= dead - fromTroops;
        dead = Math.min(dead, Math.max(0, f.members - 1));
        f.members -= dead;
        f.deaths += dead;
    }

    /** Засада у чужой точки: бандиты грабят всех, враждебные фракции отбивают груз у врагов. */
    private boolean ambush(Simulation s, Convoy c, Faction f, int nodeId, Random r) {
        Node n = s.nodes.get(nodeId);
        if (n.owner < 0 || n.owner == f.id) return false;
        Faction o = s.factions.get(n.owner);
        if (o.fanatic && !s.monolithActive) return false;
        double h = s.hostilityTo(o, f.id);
        double p = o.raider ? 0.3 : h >= 0.8 ? 0.15 : 0;
        p *= 1 - Math.min(0.75, 0.07 * (c.guards + c.troops));
        if (p <= 0 || r.nextDouble() >= p) return false;
        long cash = (long) (c.cargo.money * (o.raider ? 0.8 : 1.0));
        Stock.moveMoney(c.cargo, o.stock, cash);
        int units = 0;
        for (Good g : Good.ALL) units += Stock.move(c.cargo, o.stock, g, c.cargo.get(g));
        int dead = Math.min(c.guards + c.troops, 1 + r.nextInt(2 + (c.guards + c.troops) / 2));
        dead = Math.min(dead, Math.max(0, f.members - 1));
        f.members -= dead;
        f.deaths += dead;
        f.caravansLost++;
        f.resent(o.id, 1.0);
        convoysRobbed++;
        cashRobbed += cash;
        // наличные, которые не нашли, уцелевшие уносят обратно
        Node home = s.nodes.get(c.from());
        if (home.owner == f.id && home.id != f.baseNode) Stock.moveMoney(c.cargo, home.depot, c.cargo.money);
        else Stock.moveMoney(c.cargo, f.stock, c.cargo.money);
        s.log(o.name + " перехватили конвой «" + f.name + "» (" + c.kind.ru + ") у «" + n.name + "»: " + units + " ед., " + cash + " руб., погибло " + dead);
        return true;
    }

    private void arrive(Simulation s, Convoy c) {
        Faction f = s.factions.get(c.owner);
        Node d = s.nodes.get(c.dest());
        if (d.owner != f.id) {
            // точку потеряли, пока ехали: везём обратно на базу
            Node base = s.nodes.get(f.baseNode);
            if (base.owner == f.id && base.id != d.id && s.graph.reachable(d.id, base.id)) {
                c.kind = Convoy.Kind.RETURN;
                c.route = s.graph.path(d.id, base.id);
                c.leg = 0;
                if (c.route.length > 1) { c.daysLeft = s.graph.edgeDays(c.route[0], c.route[1]); rerouted.add(c); return; }
            }
            convoysLost++;
            for (Good g : Good.ALL) s.ledger.destroyed[g.ordinal()] += c.cargo.take(g, c.cargo.get(g));
            s.ledger.moneyBurned += c.cargo.money;
            c.cargo.money = 0;
            return;
        }
        convoysArrived++;
        cashDelivered += c.cargo.money;
        if (c.kind == Convoy.Kind.CONTRACT && fieldBase(s, d)) contractDelivered++;
        unloadTo(s, c, d.id == f.baseNode ? f.stock : d.depot);
    }

    private static void unloadTo(Simulation s, Convoy c, Stock to) {
        for (Good g : Good.ALL) Stock.move(c.cargo, to, g, c.cargo.get(g));
        Stock.moveMoney(c.cargo, to, c.cargo.money);
    }

    // ------------------------------------------------------------------ аудит и сохранение

    long totals(long[] out) {
        long m = 0;
        for (Convoy c : convoys) {
            for (int i = 0; i < out.length; i++) out[i] += c.cargo.qty[i];
            m += c.cargo.money;
        }
        return m;
    }

    /** Экспедицию к Выжигателю по контракту сопровождает армия, иначе наёмники. */
    public Faction escortFor(Simulation s) {
        Faction m = s.military();
        if (contractActive && m != null && m.members >= 30 && s.readiness(m) >= 0.6) return m;
        return s.mercenaries();
    }

    void write(DataOutputStream out) throws IOException {
        out.writeBoolean(contractActive);
        out.writeInt(contractNextDay);
        out.writeInt(contractPaid);
        out.writeInt(contractMissed);
        out.writeInt(contractMissedRow);
        out.writeLong(contractMoney);
        out.writeDouble(contractDuty);
        out.writeBoolean(grantFrozen);
        out.writeInt(contractDelivered);
        for (int v : new int[] {convoysSent, convoysArrived, convoysLost, convoysRobbed, deserters, secessions, recruitsBought}) out.writeInt(v);
        out.writeLong(dutyProtection);
        out.writeLong(banditRacket);
        out.writeLong(freedomSmuggled);
        out.writeLong(armySpent);
        out.writeLong(cashDelivered);
        out.writeLong(cashRobbed);
        out.writeInt(convoys.size());
        for (Convoy c : convoys) {
            out.writeInt(c.owner);
            out.writeInt(c.kind.ordinal());
            out.writeInt(c.route.length);
            for (int v : c.route) out.writeInt(v);
            out.writeInt(c.leg);
            out.writeInt(c.daysLeft);
            out.writeInt(c.guards);
            out.writeInt(c.troops);
            c.cargo.write(out);
        }
    }

    void read(DataInputStream in) throws IOException {
        contractActive = in.readBoolean();
        contractNextDay = in.readInt();
        contractPaid = in.readInt();
        contractMissed = in.readInt();
        contractMissedRow = in.readInt();
        contractMoney = in.readLong();
        contractDuty = in.readDouble();
        grantFrozen = in.readBoolean();
        contractDelivered = in.readInt();
        convoysSent = in.readInt(); convoysArrived = in.readInt(); convoysLost = in.readInt(); convoysRobbed = in.readInt();
        deserters = in.readInt(); secessions = in.readInt(); recruitsBought = in.readInt();
        dutyProtection = in.readLong();
        banditRacket = in.readLong();
        freedomSmuggled = in.readLong();
        armySpent = in.readLong();
        cashDelivered = in.readLong();
        cashRobbed = in.readLong();
        convoys.clear();
        int n = in.readInt();
        for (int k = 0; k < n; k++) {
            int owner = in.readInt();
            Convoy.Kind kind = Convoy.Kind.values()[in.readInt()];
            int[] route = new int[in.readInt()];
            for (int i = 0; i < route.length; i++) route[i] = in.readInt();
            Convoy c = new Convoy(owner, kind, route, 0);
            c.leg = in.readInt();
            c.daysLeft = in.readInt();
            c.guards = in.readInt();
            c.troops = in.readInt();
            c.cargo.read(in);
            convoys.add(c);
        }
    }
}
