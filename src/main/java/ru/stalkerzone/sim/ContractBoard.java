package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Доска контрактов (v1.4). Заказчики: фракции и учёные. Исполнители: игроки и нейтральные сталкеры (Одиночки).
 * Награда берётся из кассы заказчика в залог при выдаче (виден в аудите), уходит исполнителю при выполнении
 * и возвращается заказчику при провале или истечении срока.
 */
public final class ContractBoard {
    public static final int MAX_OPEN = 12;
    public static final int MAX_PER_PLAYER = 3;

    public final List<Contract> list = new ArrayList<>();
    public int nextId = 1;
    public int doneByPlayer, doneByNpc, failed, expired;

    public List<Contract> active() {
        List<Contract> out = new ArrayList<>();
        for (Contract c : list) if (c.active()) out.add(c);
        return out;
    }

    public Contract byId(int id) {
        for (Contract c : list) if (c.id == id) return c;
        return null;
    }

    /** Награда игроку: деньги и, возможно, товар. */
    public record Reward(long money, Good good, int qty) {}

    /** Товары в залогах: часть закона сохранения. */
    public void escrowGoods(long[] out) {
        for (Contract c : list) if (c.active()) out[c.rewardGood.ordinal()] += c.rewardQty;
    }

    /** Деньги в залогах: часть закона сохранения. */
    public long escrowTotal() {
        long sum = 0;
        for (Contract c : list) if (c.active()) sum += c.escrow;
        return sum;
    }

    public List<Contract> ofPlayer(String player) {
        List<Contract> out = new ArrayList<>();
        for (Contract c : list) if (c.active() && c.taker == Contract.PLAYER && c.player.equals(player)) out.add(c);
        return out;
    }

    private int openCount() {
        int n = 0;
        for (Contract c : list) if (c.status == Contract.Status.OPEN) n++;
        return n;
    }

    private boolean has(Contract.Type t, int issuer, int node) {
        for (Contract c : list) if (c.active() && c.type == t && c.issuer == issuer && (node < 0 || c.node == node)) return true;
        return false;
    }

    private static final Good[] PAY_GOODS = {Good.GEAR_PRO, Good.PROTO, Good.GEAR, Good.ARTIFACT, Good.MEDS, Good.AMMO_PRO, Good.AMMO};

    /** Выдать контракт: деньги из кассы (не больше 60%), недостающее товаром со склада. Если не набрать и половины, не выдавать. */
    private Contract issue(Simulation s, Contract.Type t, int issuer, long reward, int days, int difficulty) {
        Faction f = s.factions.get(issuer);
        long money = Math.min(reward, (long) (f.stock.money * 0.6));
        long rest = reward - money;
        Good pg = null;
        int pq = 0;
        if (rest > 0) {
            for (Good g : PAY_GOODS) {
                int spare = (int) (f.stock.get(g) * 0.3);
                int need = (int) Math.ceil(rest / (double) g.base);
                if (spare <= 0) continue;
                int q = Math.min(spare, need);
                if (pg == null || (long) q * g.base > (long) pq * pg.base) { pg = g; pq = q; }
            }
        }
        long value = money + (pg != null ? (long) pq * pg.base : 0);
        if (value < reward * 0.5 || value <= 0) return null;
        Contract c = new Contract(nextId++, t, issuer);
        f.stock.money -= money;
        c.escrow = money;
        if (pg != null && pq > 0) {
            f.stock.take(pg, pq);
            c.rewardGood = pg;
            c.rewardQty = pq;
        }
        c.created = s.day;
        c.deadline = s.day + days;
        c.difficulty = difficulty;
        list.add(c);
        return c;
    }

    private void refund(Simulation s, Contract c) {
        Faction f = s.factions.get(c.issuer);
        f.stock.money += c.escrow;
        f.stock.add(c.rewardGood, c.rewardQty);
        c.escrow = 0;
        c.rewardQty = 0;
    }

    // ------------------------------------------------------------------ день

    void daily(Simulation s, Random r) {
        for (Contract c : list) {
            if (!c.active()) continue;
            if (c.status == Contract.Status.TAKEN && c.taker >= 0 && s.day >= c.resolveDay) npcResolve(s, c, r);
            if (c.active() && c.status != Contract.Status.READY && s.day > c.deadline) {
                c.status = c.status == Contract.Status.OPEN ? Contract.Status.EXPIRED : Contract.Status.FAILED;
                if (c.status == Contract.Status.FAILED) failed++; else expired++;
                refund(s, c);
            }
            if (c.status == Contract.Status.READY && s.day > c.deadline + 30) {
                // за наградой так и не пришли
                c.status = Contract.Status.EXPIRED;
                refund(s, c);
            }
            if (c.active() && c.type == Contract.Type.CLEAR_LAIR && s.lairAt(c.node) == null && c.status != Contract.Status.READY) {
                c.status = Contract.Status.EXPIRED;
                refund(s, c);
            }
            if (c.active() && c.type == Contract.Type.LAB_DATA && s.labs.labs.get(c.lab).complete() && c.status != Contract.Status.READY) {
                c.status = Contract.Status.EXPIRED;
                refund(s, c);
            }
        }
        npcTake(s, r);
        generate(s, r);
        // хранить историю ограниченно
        int closed = 0;
        for (Contract c : list) if (!c.active()) closed++;
        for (Iterator<Contract> it = list.iterator(); it.hasNext() && closed > 40; ) {
            if (!it.next().active()) { it.remove(); closed--; }
        }
    }

    private void generate(Simulation s, Random r) {
        if (openCount() >= MAX_OPEN) return;
        Faction sci = s.scientists();
        if (sci != null && sci.members >= 3) {
            if (!has(Contract.Type.FETCH_ARTIFACT, sci.id, -1) && r.nextDouble() < 0.25) {
                int q = 1 + r.nextInt(3);
                long price = s.sellPrice(s.nodes.get(sci.baseNode), Good.ARTIFACT);
                Contract c = issue(s, Contract.Type.FETCH_ARTIFACT, sci.id, (long) (price * q * 1.6), 20, 2);
                if (c != null) { c.node = sci.baseNode; c.qty = q; c.good = Good.ARTIFACT; }
            }
            Lab next = s.labs.nextTarget(s);
            if (next != null && !has(Contract.Type.LAB_DATA, sci.id, -1) && r.nextDouble() < 0.2) {
                long fund = Math.min(sci.stock.money / 2, 1500L * next.tier);
                Contract c = issue(s, Contract.Type.LAB_DATA, sci.id, fund, 60, Math.min(5, next.tier + 1));
                if (c != null) { c.node = sci.baseNode; c.lab = next.id; c.bonus = 0; c.qty = 1; }
            }
        }
        for (Faction f : s.factions) {
            if (f.fanatic || f.members < 8 || f.isService() || openCount() >= MAX_OPEN) continue;
            // поставки: воюющим и обедневшим не хватает патронов и медикаментов
            Good need = null;
            if (Arms.ammoValue(f.stock) < f.members * 30 || f.phase == Phase.WAR) need = f.military ? Good.AMMO_PRO : Good.AMMO;
            else if (f.stock.get(Good.MEDS) < f.members) need = Good.MEDS;
            if (need != null && !has(Contract.Type.SUPPLY, f.id, -1) && r.nextDouble() < 0.12) {
                int q = need == Good.AMMO ? 150 + r.nextInt(150) : need == Good.AMMO_PRO ? 80 + r.nextInt(80) : 10 + r.nextInt(15);
                Node trader = nearestTrader(s, f.baseNode);
                long unit = trader != null ? s.sellPrice(trader, need) : need.base;
                Contract c = issue(s, Contract.Type.SUPPLY, f.id, (long) (unit * q * 1.45), 15, 1);
                if (c != null) { c.node = f.baseNode; c.good = need; c.qty = q; }
            }
            // логова рядом со своими землями
            for (Lair l : s.lairs) {
                if (l.strength < 22 || s.minDistFromOwned(f, l.node) > 1) continue;
                if (anyLairContract(l.node) || r.nextDouble() > 0.05 * (f.doctrine.orderZeal + 0.3)) continue;
                long reward = 700 + (long) (l.strength * 35) + (l.lab ? 900 : 0);
                Contract c = issue(s, Contract.Type.CLEAR_LAIR, f.id, reward, 25, l.strength > 60 ? 4 : l.strength > 40 ? 3 : 2);
                if (c != null) c.node = l.node;
                break;
            }
        }
    }

    private boolean anyLairContract(int node) {
        for (Contract c : list) if (c.active() && c.type == Contract.Type.CLEAR_LAIR && c.node == node) return true;
        return false;
    }

    private Node nearestTrader(Simulation s, int from) {
        Node best = null;
        for (Node n : s.nodes) if (n.trader && (best == null || s.graph.dist(from, n.id) < s.graph.dist(from, best.id))) best = n;
        return best;
    }

    /** Встреча лидеров: хозяин нанимает охрану, враг заказывает засаду. */
    void meetingContracts(Simulation s, Politics.Meeting m, Random r) {
        Contract g = issue(s, Contract.Type.GUARD_MEETING, m.host, 1200 + r.nextInt(600), m.day - s.day, 3);
        if (g != null) g.node = m.venue;
        if (g != null) g.deadline = m.day;
        Faction h = s.politics.ambusher(s, m);
        if (h != null) {
            Contract a = issue(s, Contract.Type.AMBUSH_MEETING, h.id, 2000 + r.nextInt(1000), m.day - s.day, 4);
            if (a != null) { a.node = m.venue; a.deadline = m.day; a.qty = m.host * 100 + m.guest; }
        }
    }

    void meetingResolved(Simulation s, Politics.Meeting m, boolean success, boolean leaderDied) {
        for (Contract c : list) {
            if (!c.active() || c.node != m.venue || c.deadline != m.day) continue;
            if (c.type == Contract.Type.GUARD_MEETING) {
                if (c.status == Contract.Status.TAKEN && c.taker == Contract.PLAYER && m.guarded && !leaderDied) c.status = Contract.Status.READY;
                else { c.status = Contract.Status.FAILED; failed++; refund(s, c); }
            } else if (c.type == Contract.Type.AMBUSH_MEETING && c.status != Contract.Status.READY) {
                c.status = c.status == Contract.Status.TAKEN ? Contract.Status.FAILED : Contract.Status.EXPIRED;
                refund(s, c);
            }
        }
    }

    // ------------------------------------------------------------------ нейтральные сталкеры

    private void npcTake(Simulation s, Random r) {
        Faction loners = s.mercenaries();
        if (loners == null || loners.members < 10) return;
        for (Contract c : list) {
            if (c.status != Contract.Status.OPEN || c.issuer == loners.id || s.day - c.created < 3) continue;
            if (c.type != Contract.Type.FETCH_ARTIFACT && c.type != Contract.Type.SUPPLY && c.type != Contract.Type.CLEAR_LAIR) continue;
            if (r.nextDouble() > 0.12) continue;
            c.status = Contract.Status.TAKEN;
            c.taker = loners.id;
            c.resolveDay = s.day + 2 + r.nextInt(5);
        }
    }

    private void npcResolve(Simulation s, Contract c, Random r) {
        Faction t = s.factions.get(c.taker), iss = s.factions.get(c.issuer);
        double p = 0.85 - 0.13 * c.difficulty + 0.002 * t.exp;
        boolean ok = r.nextDouble() < p;
        switch (c.type) {
            case FETCH_ARTIFACT -> {
                if (ok) {
                    int moved = Stock.move(t.stock, iss.stock, Good.ARTIFACT, c.qty);
                    int rest = c.qty - moved;
                    iss.stock.add(Good.ARTIFACT, rest);
                    s.ledger.created[Good.ARTIFACT.ordinal()] += rest;
                }
            }
            case SUPPLY -> ok = ok && Stock.move(t.stock, iss.stock, c.good, c.qty) > 0;
            case CLEAR_LAIR -> {
                Lair l = s.lairAt(c.node);
                if (l == null) ok = false;
                else if (ok) s.mutantKilled(l, l.strength, false);
                else s.applyCasualtiesOf(t, 1 + r.nextInt(3));
            }
            default -> ok = false;
        }
        if (ok) {
            t.stock.money += c.escrow;
            t.stock.add(c.rewardGood, c.rewardQty);
            c.escrow = 0;
            c.rewardQty = 0;
            c.status = Contract.Status.DONE;
            doneByNpc++;
            if (c.difficulty >= 3) s.log("Одиночки выполнили контракт «" + iss.name + "»: " + c.type.ru.toLowerCase());
        } else {
            c.status = Contract.Status.OPEN;
            c.taker = Contract.NOBODY;
        }
    }

    // ------------------------------------------------------------------ игрок

    public String take(Simulation s, int id, String player) {
        Contract c = byId(id);
        if (c == null || c.status != Contract.Status.OPEN) return "Контракт недоступен";
        if (ofPlayer(player).size() >= MAX_PER_PLAYER) return "Не больше " + MAX_PER_PLAYER + " контрактов одновременно";
        c.status = Contract.Status.TAKEN;
        c.taker = Contract.PLAYER;
        c.player = player;
        return null;
    }

    public String abandon(Simulation s, int id, String player) {
        Contract c = byId(id);
        if (c == null || c.taker != Contract.PLAYER || !c.player.equals(player) || c.status != Contract.Status.TAKEN) return "Это не ваш контракт";
        c.status = Contract.Status.OPEN;
        c.taker = Contract.NOBODY;
        c.player = "";
        return null;
    }

    /** Выплата игроку: залог уходит из Зоны (к игроку), надбавка не из кассы. */
    private Reward payPlayer(Simulation s, Contract c) {
        Reward r = new Reward(c.escrow + c.bonus, c.rewardGood, c.rewardQty);
        s.ledger.moneyBurned += c.escrow;
        s.ledger.exported[c.rewardGood.ordinal()] += c.rewardQty;
        c.escrow = 0;
        c.rewardQty = 0;
        c.status = Contract.Status.DONE;
        doneByPlayer++;
        return r;
    }

    private static Reward sum(Reward a, Reward b) {
        if (a == null) return b;
        if (b == null) return a;
        // разные товары в одной выплате бывают редко: второй товар добавляем деньгами по базовой цене
        if (a.qty() > 0 && b.qty() > 0 && a.good() != b.good()) return new Reward(a.money() + b.money() + (long) b.qty() * b.good().base, a.good(), a.qty());
        return new Reward(a.money() + b.money(), a.qty() > 0 ? a.good() : b.good(), a.qty() + b.qty());
    }

    /** Получить награду за выполненный контракт (охрана встречи). */
    public Reward claim(Simulation s, int id, String player) {
        Contract c = byId(id);
        if (c == null || c.status != Contract.Status.READY || !c.player.equals(player)) return null;
        return payPlayer(s, c);
    }

    /** Сдача товаров по контракту: игровой слой уже забрал предметы у игрока. Товар поступает заказчику. */
    public Reward deliver(Simulation s, int id, String player, int qty) {
        Contract c = byId(id);
        if (c == null || c.status != Contract.Status.TAKEN || !c.player.equals(player) || qty < c.qty) return null;
        if (c.type != Contract.Type.FETCH_ARTIFACT && c.type != Contract.Type.SUPPLY) return null;
        s.factions.get(c.issuer).stock.add(c.good, c.qty);
        s.ledger.imported[c.good.ordinal()] += c.qty;
        return payPlayer(s, c);
    }

    /** Логово уничтожено игроком: закрыть его контракты на это логово. Возвращает сумму к выплате. */
    public Reward lairDestroyed(Simulation s, int node, String player) {
        Reward sum = null;
        for (Contract c : list) {
            if (c.status == Contract.Status.TAKEN && c.type == Contract.Type.CLEAR_LAIR && c.node == node && c.player.equals(player)) sum = sum(sum, payPlayer(s, c));
        }
        return sum;
    }

    /** Игрок убил лидера фракции: закрыть заказ на засаду, если он относится к этой встрече. */
    public Reward leaderKilled(Simulation s, int faction, String player) {
        Reward sum = null;
        for (Contract c : list) {
            if (c.status != Contract.Status.TAKEN || c.type != Contract.Type.AMBUSH_MEETING || !c.player.equals(player)) continue;
            if (c.qty / 100 == faction || c.qty % 100 == faction) sum = sum(sum, payPlayer(s, c));
        }
        return sum;
    }

    /** Данные лаборатории сданы учёным: закрыть заказ на эту лабораторию. */
    public Reward labDelivered(Simulation s, int lab, String player) {
        Reward sum = null;
        for (Contract c : list) {
            if (c.status == Contract.Status.TAKEN && c.type == Contract.Type.LAB_DATA && c.lab == lab && c.player.equals(player)) sum = sum(sum, payPlayer(s, c));
        }
        return sum;
    }

    /** Игрок с заказом на охрану стоит у места встречи. */
    public boolean guardPresent(Simulation s, String player) {
        Politics.Meeting m = s.politics.meeting;
        if (m == null) return false;
        for (Contract c : list) {
            if (c.status == Contract.Status.TAKEN && c.type == Contract.Type.GUARD_MEETING && c.node == m.venue && c.player.equals(player)) {
                m.guarded = true;
                return true;
            }
        }
        return false;
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(nextId);
        out.writeInt(doneByPlayer);
        out.writeInt(doneByNpc);
        out.writeInt(failed);
        out.writeInt(expired);
        out.writeInt(list.size());
        for (Contract c : list) c.write(out);
    }

    void read(DataInputStream in) throws IOException {
        nextId = in.readInt();
        doneByPlayer = in.readInt();
        doneByNpc = in.readInt();
        failed = in.readInt();
        expired = in.readInt();
        list.clear();
        int n = in.readInt();
        for (int i = 0; i < n; i++) list.add(Contract.read(in));
    }
}
