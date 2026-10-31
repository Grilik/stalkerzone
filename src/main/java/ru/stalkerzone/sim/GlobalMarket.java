package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Глобальный рынок Большой земли (v1.11). Находится вне карты; торговать с ним могут только торговцы Зоны
 * и учёные (через институт). Остальные группировки получают товары с Большой земли только через них:
 * караванами у торговцев или по контракту учёных (армия).
 *
 * <p>Цена каждого товара = база × событие × давление спроса × медленный дрейф. Каждая покупка поднимает давление
 * (цену), продажа опускает; давление со временем рассасывается. События Большой земли (дефицит, урожай,
 * оборонзаказ, интерес НИИ к артефактам) на недели сдвигают цены.
 *
 * <p>Покупатель не берёт всё сразу: {@link #decideBuy} сравнивает цену с базовой и срочностью нужды.
 * Если цена высока, а нужда терпит, он ждёт; если цена растёт, а нужда заметная, берёт сейчас;
 * если падает, откладывает. Заказ приходит через несколько дней (доставка), деньги уходят сразу.
 */
public final class GlobalMarket {
    static final int G = Good.ALL.length;
    /** Объём, который сдвигает цену примерно в e^0.6 раз. */
    static final double[] DEPTH = new double[G];
    static {
        DEPTH[Good.FOOD.ordinal()] = 2500;
        DEPTH[Good.PARTS.ordinal()] = 1800;
        DEPTH[Good.AMMO.ordinal()] = 7000;
        DEPTH[Good.MEDS.ordinal()] = 500;
        DEPTH[Good.GEAR.ordinal()] = 50;
        DEPTH[Good.ARTIFACT.ordinal()] = 150;
        DEPTH[Good.MATERIALS.ordinal()] = 1800;
        // кустарное и прототипы на Большой земле не торгуются (глубина условная, цена стоит на базовой)
        DEPTH[Good.PROTO.ordinal()] = 1e9;
        DEPTH[Good.AMMO_PRO.ordinal()] = 5000;
        DEPTH[Good.GEAR_PRO.ordinal()] = 70;
    }

    /** Событие Большой земли: сдвиг цены одного товара на время. */
    public static final class Event {
        public final String title;
        public final int good;
        public final double factor;
        public int until;
        Event(String title, int good, double factor, int until) { this.title = title; this.good = good; this.factor = factor; this.until = until; }
    }

    /** Заказ в пути: деньги уплачены, товар приедет через days дней. dest: -1 — склад фракции, иначе склад точки или рынок торговца. */
    public static final class Order {
        public final int buyer, node, good, qty;
        public final boolean trader;
        /** Заказ учёных для армии: приходит в оборонный сейф бункера, оттуда уезжает с платой по контракту. */
        public boolean army;
        public int days;
        Order(int buyer, int node, int good, int qty, boolean trader, int days) {
            this.buyer = buyer; this.node = node; this.good = good; this.qty = qty; this.trader = trader; this.days = days;
        }
    }

    static final String[][] EVENTS = {
        {"Дефицит медикаментов в больницах Большой земли", "MEDS", "1.8"},
        {"Оборонзаказ: заводы гонят патроны армии", "AMMO_PRO", "1.5"},
        {"Склады мобрезерва распродают патроны", "AMMO_PRO", "0.7"},
        {"Хороший урожай: еда дешевеет", "FOOD", "0.65"},
        {"Неурожай: еда дорожает", "FOOD", "1.6"},
        {"НИИ скупают артефакты: спрос вырос", "ARTIFACT", "1.7"},
        {"Скандал с контрабандой артефактов: спрос упал", "ARTIFACT", "0.6"},
        {"Стройка века на Большой земле: стройматериалы дорожают", "MATERIALS", "1.7"},
        {"Конверсия: армейское снаряжение распродают дёшево", "GEAR_PRO", "0.7"},
        {"Новые бронежилеты уходят в войска: снаряжение в дефиците", "GEAR_PRO", "1.5"},
        {"Санкции на комплектующие: детали дорожают", "PARTS", "1.6"},
    };

    public final double[] price = new double[G];
    public final double[] pressure = new double[G];
    public final double[] drift = new double[G];
    /** Сглаженное относительное изменение цены за день (+ растёт, − падает). */
    public final double[] trend = new double[G];
    public final List<Event> events = new ArrayList<>();
    public final List<Order> orders = new ArrayList<>();
    public long turnoverBuy, turnoverSell;
    /** Сколько событий Большой земли было за игру (не сохраняется, для проверок). */
    public int eventsTotal;
    public int waited, bought;

    public GlobalMarket() {
        for (Good g : Good.ALL) price[g.ordinal()] = g.base;
    }

    private double eventFactor(int g) {
        double f = 1;
        for (Event e : events) if (e.good == g) f *= e.factor;
        return f;
    }

    void daily(Simulation s, Random r) {
        for (Iterator<Event> it = events.iterator(); it.hasNext(); ) {
            Event e = it.next();
            if (s.day >= e.until) { it.remove(); s.log("Большая земля: закончилось — «" + e.title + "»"); }
        }
        if (events.size() < 2 && r.nextDouble() < 0.025) {
            String[] ev = EVENTS[r.nextInt(EVENTS.length)];
            int g = Good.valueOf(ev[1]).ordinal();
            boolean dup = false;
            for (Event e : events) if (e.good == g) dup = true;
            if (!dup) {
                events.add(new Event(ev[0], g, Double.parseDouble(ev[2]), s.day + 20 + r.nextInt(30)));
                eventsTotal++;
                s.log("Большая земля: " + ev[0]);
            }
        }
        for (Good gd : Good.ALL) {
            int g = gd.ordinal();
            if (!gd.mainland) { price[g] = gd.base; trend[g] = 0; continue; }
            pressure[g] *= 0.92;
            drift[g] = Math.max(-0.25, Math.min(0.25, drift[g] * 0.98 + r.nextGaussian() * 0.012));
            double p = gd.base * eventFactor(g) * Math.exp(0.6 * pressure[g] / DEPTH[g] + drift[g]);
            p = Math.max(gd.base * 0.35, Math.min(gd.base * 4.0, p));
            double change = price[g] > 0 ? (p - price[g]) / price[g] : 0;
            trend[g] = trend[g] * 0.8 + change * 0.2;
            price[g] = p;
        }
        // доставка заказов
        for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (--o.days > 0) continue;
            it.remove();
            Stock to = destination(s, o);
            if (to == null) continue; // получатель исчез: товар не дошёл, деньги уже ушли
            to.add(Good.ALL[o.good], o.qty);
            s.ledger.imported[o.good] += o.qty;
        }
    }

    private Stock destination(Simulation s, Order o) {
        if (o.trader) return s.nodes.get(o.node).market;
        Faction f = s.factions.get(o.buyer);
        if (o.army) { Node lab = s.nodes.get(f.baseNode); return lab.owner == f.id ? lab.depot : null; }
        if (o.node < 0) return s.nodes.get(f.baseNode).owner == f.id ? f.stock : null;
        Node n = s.nodes.get(o.node);
        return n.owner == f.id ? (n.id == f.baseNode ? f.stock : n.depot) : null;
    }

    /** Цена партии с учётом того, что сама покупка (или продажа) сдвигает цену. */
    public long quote(Good g, int qty, boolean buy) {
        if (qty <= 0) return 0;
        int i = g.ordinal();
        double p0 = price[i], p1 = p0 * Math.exp(0.6 * (buy ? qty : -qty) / DEPTH[i]);
        return Math.round((p0 + p1) / 2 * qty);
    }

    /**
     * Решение покупателя: сколько брать сейчас. urgency 0..1 — насколько товар нужен (1 — без него беда).
     * Возвращает 0, если выгоднее подождать.
     */
    public int decideBuy(Good g, int need, double urgency, long budget) {
        if (need <= 0 || budget <= 0) return 0;
        int i = g.ordinal();
        double ratio = price[i] / g.base;
        double tolerance = 1.2 + 1.8 * urgency * urgency;
        if (trend[i] > 0.01 && urgency > 0.35) tolerance += 0.3;   // дорожает: лучше взять сейчас
        if (trend[i] < -0.01 && urgency < 0.6) tolerance -= 0.25;  // дешевеет: подождать
        if (ratio > tolerance && urgency < 0.95) { waited++; return 0; }
        double share = urgency >= 0.95 ? 1 : Math.min(1, 0.35 + Math.max(0, tolerance - ratio));
        int q = (int) Math.ceil(need * share);
        while (q > 0 && quote(g, q, true) > budget) q = q * 3 / 4;
        if (q > 0) bought++;
        return q;
    }

    /** Решение продавца: сколько продать из излишка. force 0..1 — насколько надо избавиться (склад переполнен, нужны деньги). */
    public int decideSell(Good g, int surplus, double force) {
        if (surplus <= 0) return 0;
        double ratio = price[g.ordinal()] / g.base;
        double floor = 0.95 - 0.5 * force;
        if (ratio < floor) return 0;
        double share = Math.min(1, 0.4 + (ratio - floor));
        return (int) Math.ceil(surplus * share);
    }

    /** Купить с доставкой: деньги списываются сразу (уходят из Зоны), товар приедет через days дней. */
    public long order(Simulation s, Stock wallet, Good g, int qty, int buyer, int node, boolean trader, int days) {
        return order(s, wallet, g, qty, buyer, node, trader, days, false);
    }

    public long order(Simulation s, Stock wallet, Good g, int qty, int buyer, int node, boolean trader, int days, boolean army) {
        if (qty <= 0 || !g.mainland) return 0;
        long cost = quote(g, qty, true);
        if (wallet.money < cost) return 0;
        wallet.money -= cost;
        s.ledger.moneyBurned += cost;
        pressure[g.ordinal()] += qty;
        turnoverBuy += cost;
        Order o = new Order(buyer, node, g.ordinal(), qty, trader, Math.max(1, days));
        o.army = army;
        orders.add(o);
        return cost;
    }

    /** Продать на Большую землю (комиссия 10%): товар уходит из Зоны, деньги приходят сразу. */
    public long sell(Simulation s, Stock from, Good g, int qty) {
        if (!g.mainland) return 0;
        qty = from.take(g, qty);
        if (qty <= 0) return 0;
        long got = Math.round(quote(g, qty, false) * 0.9);
        from.money += got;
        s.ledger.exported[g.ordinal()] += qty;
        s.ledger.moneyMinted += got;
        pressure[g.ordinal()] -= qty;
        turnoverSell += got;
        return got;
    }

    /** Сколько товара уже едет этому получателю. */
    public int inTransit(int buyer, int node, Good g, boolean trader) {
        int q = 0;
        for (Order o : orders) if (o.buyer == buyer && o.node == node && o.trader == trader && o.good == g.ordinal() && !o.army) q += o.qty;
        return q;
    }

    public int armyInTransit(Good g) {
        int q = 0;
        for (Order o : orders) if (o.army && o.good == g.ordinal()) q += o.qty;
        return q;
    }

    // ------------------------------------------------------------------ участники рынка

    /** Учёные заказывают через институт то, чего не хватает для исследований и жизни бункера. */
    void scientistsOrder(Simulation s, Faction sci, Random r) {
        if (sci == null || sci.members < 3 || s.nodes.get(sci.baseNode).owner != sci.id) return;
        long budget = (sci.stock.money - 800) / 2;
        for (Good g : new Good[] {Good.FOOD, Good.MEDS, Good.PARTS, Good.MATERIALS}) {
            if (budget <= 0) return;
            int target = g == Good.PARTS ? Math.max(sci.targetOf(g), 120) : sci.targetOf(g);
            int have = sci.stock.get(g) + inTransit(sci.id, -1, g, false);
            int need = target - have;
            if (need <= 0) continue;
            double urgency = Math.min(1, (double) need / Math.max(1, target));
            int q = decideBuy(g, need, urgency, budget);
            budget -= order(s, sci.stock, g, q, sci.id, -1, false, 3 + r.nextInt(3));
        }
    }

    /** Торговец: продаёт излишки на Большую землю, когда цена устраивает, и завозит недостающее с оценкой срочности. */
    void traderDaily(Simulation s, Node n, Random r) {
        Stock m = n.market;
        for (Good g : Good.ALL) {
            if (!g.mainland) continue; // кустарное и прототипы торговец берёт только у группировок
            int i = g.ordinal();
            int t = n.target[i];
            int hi = g == Good.ARTIFACT ? t : (int) (t * 1.6);
            if (m.qty[i] > hi) {
                double force = Math.min(1, (m.qty[i] - hi) / (double) Math.max(1, t)) * (g == Good.ARTIFACT ? 0.6 : 1) + (g == Good.ARTIFACT ? 0.4 : 0);
                sell(s, m, g, decideSell(g, m.qty[i] - hi, force));
            } else if (n.importCap[i] > 0) {
                int have = m.qty[i] + inTransit(-1, n.id, g, true);
                if (have >= t * 0.9) continue;
                int need = Math.min(n.importCap[i], t - have);
                double urgency = Math.min(1, 1 - (double) have / Math.max(1, t));
                int q = decideBuy(g, need, urgency, (long) (Math.max(0, m.money - n.cashReserve) * 0.6));
                order(s, m, g, q, -1, n.id, true, 2 + r.nextInt(3));
            }
        }
    }

    void write(DataOutputStream out) throws IOException {
        for (int i = 0; i < G; i++) { out.writeDouble(price[i]); out.writeDouble(pressure[i]); out.writeDouble(drift[i]); out.writeDouble(trend[i]); }
        out.writeInt(events.size());
        for (Event e : events) { out.writeUTF(e.title); out.writeInt(e.good); out.writeDouble(e.factor); out.writeInt(e.until); }
        out.writeInt(orders.size());
        for (Order o : orders) {
            out.writeInt(o.buyer); out.writeInt(o.node); out.writeInt(o.good); out.writeInt(o.qty); out.writeBoolean(o.trader); out.writeInt(o.days);
            out.writeBoolean(o.army);
        }
        out.writeLong(turnoverBuy);
        out.writeLong(turnoverSell);
        out.writeInt(waited);
        out.writeInt(bought);
    }

    void read(DataInputStream in) throws IOException {
        for (int i = 0; i < G; i++) {
            if (i >= Good.readCount) { price[i] = Good.ALL[i].base; pressure[i] = drift[i] = trend[i] = 0; continue; }
            price[i] = in.readDouble(); pressure[i] = in.readDouble(); drift[i] = in.readDouble(); trend[i] = in.readDouble();
        }
        events.clear();
        int n = in.readInt();
        for (int k = 0; k < n; k++) events.add(new Event(in.readUTF(), in.readInt(), in.readDouble(), in.readInt()));
        orders.clear();
        n = in.readInt();
        for (int k = 0; k < n; k++) {
            Order o = new Order(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readBoolean(), in.readInt());
            o.army = in.readBoolean();
            orders.add(o);
        }
        turnoverBuy = in.readLong();
        turnoverSell = in.readLong();
        waited = in.readInt();
        bought = in.readInt();
    }
}
