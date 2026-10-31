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
    /** Шанс в день, что нейтральный сталкер возьмёт открытый контракт (сценарий «зона»). */
    static final double NPC_TAKE_CHANCE = 0.12;
    /** Сколько дней контракт должен полежать открытым, прежде чем NPC его возьмут (сценарий «зона»). */
    static final int NPC_TAKE_DELAY_DAYS = 3;

    public final List<Contract> list = new ArrayList<>();
    public int nextId = 1;
    public int doneByPlayer, doneByNpc, failed, expired;

    /** Карточка 026: личные сообщения игрокам о судьбе их контрактов, ждут доставки в игре. Не сохраняются. */
    public record Note(String player, String text) {}
    public final List<Note> notes = new ArrayList<>();

    /** Отдать накопившиеся сообщения игрокам и очистить очередь (зовёт игровой слой после шага симуляции). */
    public List<Note> drainNotes() {
        List<Note> out = new ArrayList<>(notes);
        notes.clear();
        return out;
    }

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

    /** Есть ли активный заказ этого типа у заказчика на ту же реальную цель (qty — фракция-получатель курьера). */
    private boolean hasTarget(Contract.Type t, int issuer, int qty) {
        for (Contract c : list) if (c.active() && c.type == t && c.issuer == issuer && c.qty == qty) return true;
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

    /**
     * Выдать контракт Кордона (карточка 006): награда целиком из кассы заказчика.
     * Нет денег — контракт не выставляется. Залог учитывается в законе сохранения.
     */
    private Contract issueCash(Simulation s, Contract.Type t, int issuer, long reward, int days, int difficulty) {
        Faction f = s.factions.get(issuer);
        if (reward <= 0 || f.stock.money < reward) return null;
        Contract c = new Contract(nextId++, t, issuer);
        f.stock.money -= reward;
        c.escrow = reward;
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
            if (c.active() && c.type == Contract.Type.CLEAR_LAIR && c.node >= 0 && s.lairAt(c.node) == null
                    && c.status != Contract.Status.READY) {
                // карточка 026: контракт игрока на логово не закрывается сам «в пользу» других.
                if (c.taker == Contract.PLAYER) {
                    if (c.lairKilled > 0) {
                        // игрок вложился в зачистку (убил хоть одного мутанта) — засчитываем ему
                        c.status = Contract.Status.READY;
                        notes.add(new Note(c.player, "Контракт #" + c.id + " на логово у «" + s.nodes.get(c.node).name
                                + "» выполнен: заберите награду в КПК («Задания»)."));
                    } else {
                        // логово выбил кто-то другой без игрока — контракт сорван, залог возвращается заказчику
                        c.status = Contract.Status.FAILED;
                        failed++;
                        refund(s, c);
                        notes.add(new Note(c.player, "Контракт #" + c.id + " сорван: логово у «" + s.nodes.get(c.node).name
                                + "» уничтожили без вас. Залог возвращён заказчику."));
                    }
                } else {
                    c.status = Contract.Status.EXPIRED;
                    refund(s, c);
                }
            }
            if (c.active() && c.type == Contract.Type.LAB_DATA && s.labs.labs.get(c.lab).complete() && c.status != Contract.Status.READY) {
                c.status = Contract.Status.EXPIRED;
                refund(s, c);
            }
            // v16: заказчик контракта на захват уже занял узел — заказ больше не нужен
            if (c.active() && c.type == Contract.Type.CAPTURE_NODE && s.nodes.get(c.node).owner == c.issuer
                    && c.status != Contract.Status.READY) {
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
        // v16 (карточка 006): у Кордона свои заказчики — Торговцы, Одиночки, военные
        if (s.scenario.equals(Scenarios.KORDON)) generateKordon(s, r);
    }

    /**
     * Доска Кордона (карточка 006). Торговцы заказывают артефакты, части мутантов и сопровождение курьера;
     * Одиночки — захват элеватора; военные — зачистку логова у дороги. Награда только из кассы заказчика.
     */
    private void generateKordon(Simulation s, Random r) {
        Faction traders = s.traders();
        Faction loners = s.mercenaries();
        Faction military = s.military();

        if (traders != null && traders.members > 0 && openCount() < MAX_OPEN) {
            // артефакты и части мутантов (в симуляции части мутантов — сырьё MEDS)
            Good need = r.nextDouble() < 0.5 ? Good.ARTIFACT : Good.MEDS;
            if (!has(Contract.Type.FETCH_ARTIFACT, traders.id, -1) && r.nextDouble() < KordonBalance.CONTRACT_FETCH_CHANCE) {
                int q = 1 + r.nextInt(KordonBalance.CONTRACT_FETCH_MAX_QTY);
                long unit = s.sellPrice(s.nodes.get(traders.baseNode), need);
                Contract c = issueCash(s, Contract.Type.FETCH_ARTIFACT, traders.id,
                        (long) (unit * q * KordonBalance.CONTRACT_FETCH_MARKUP), KordonBalance.CONTRACT_FETCH_DAYS,
                        KordonBalance.CONTRACT_DIFFICULTY);
                if (c != null) { c.node = traders.baseNode; c.good = need; c.qty = q; }
            }
            // сопровождение курьера до базы одной из живых группировок
            if (!has(Contract.Type.ESCORT_COURIER, traders.id, -1) && r.nextDouble() < KordonBalance.CONTRACT_ESCORT_CHANCE) {
                Faction buyer = escortBuyer(s, traders, r);
                if (buyer != null) {
                    long reward = KordonBalance.REWARD_ESCORT + r.nextInt(KordonBalance.REWARD_ESCORT_SPREAD);
                    Contract c = issueCash(s, Contract.Type.ESCORT_COURIER, traders.id, reward,
                            KordonBalance.CONTRACT_ESCORT_DAYS, KordonBalance.CONTRACT_DIFFICULTY);
                    if (c != null) { c.node = buyer.baseNode; c.qty = buyer.id; }
                }
            }
        }

        // Одиночки заказывают элеватор: выбить врага с узла
        if (loners != null && loners.members >= KordonBalance.CONTRACT_MIN_LONERS && openCount() < MAX_OPEN) {
            int target = KordonBalance.CONTRACT_CAPTURE_NODE;
            Node elev = s.nodes.get(target);
            if (elev.owner != loners.id && !s.noCapture(target)
                    && !has(Contract.Type.CAPTURE_NODE, loners.id, -1) && r.nextDouble() < KordonBalance.CONTRACT_CAPTURE_CHANCE) {
                long reward = KordonBalance.REWARD_CAPTURE + r.nextInt(KordonBalance.REWARD_CAPTURE_SPREAD);
                Contract c = issueCash(s, Contract.Type.CAPTURE_NODE, loners.id, reward,
                        KordonBalance.CONTRACT_CAPTURE_DAYS, KordonBalance.CONTRACT_DIFFICULTY);
                if (c != null) { c.node = target; c.qty = elev.owner; }
            }
        }

        // Военные чистят логова у дороги
        if (military != null && military.members >= KordonBalance.CONTRACT_MIN_MILITARY && openCount() < MAX_OPEN) {
            for (Lair l : s.lairs) {
                if (l.strength < KordonBalance.CONTRACT_LAIR_MIN_STRENGTH || !lairByRoad(s, l.node)) continue;
                if (anyLairContract(l.node) || r.nextDouble() > KordonBalance.CONTRACT_MILITARY_LAIR_CHANCE) continue;
                long reward = KordonBalance.REWARD_MILITARY_LAIR_BASE + (long) (l.strength * KordonBalance.REWARD_MILITARY_LAIR_PER_STRENGTH);
                Contract c = issueCash(s, Contract.Type.CLEAR_LAIR, military.id, reward, 25, l.strength > 60 ? 4 : 3);
                if (c != null) c.node = l.node;
                break;
            }
        }

        // v17 (карточка 021): Бандиты заказывают засаду на курьера Торговцев в пути и налёт на чужую ферму.
        // Оба заказа берёт только игрок-бандит (NPC их не берут, см. npcTake); награда — из кассы заказчика.
        Faction bandits = s.factions.get(ZoneScenario.BANDITS);
        if (bandits != null && bandits.members > 0 && openCount() < MAX_OPEN) {
            Caravan courier = courierInTransit(s);
            // v17 (карточка 022): дедупликация по реальной цели — тому же курьеру (courierFor), а не по узлу −1
            if (courier != null && !hasTarget(Contract.Type.AMBUSH_COURIER, bandits.id, courier.courierFor)
                    && r.nextDouble() < KordonBalance.CONTRACT_AMBUSH_COURIER_CHANCE) {
                long reward = KordonBalance.REWARD_AMBUSH + r.nextInt(KordonBalance.REWARD_AMBUSH_SPREAD);
                Contract c = issueCash(s, Contract.Type.AMBUSH_COURIER, bandits.id, reward,
                        KordonBalance.CONTRACT_AMBUSH_DAYS, KordonBalance.CONTRACT_DIFFICULTY);
                if (c != null) { c.node = courier.route[courier.route.length - 1]; c.qty = courier.courierFor; }
            }
        }
        if (bandits != null && bandits.members > 0 && openCount() < MAX_OPEN) {
            int farm = farmTarget(s, bandits.id);
            if (farm >= 0 && !has(Contract.Type.RAID_NODE, bandits.id, farm)
                    && r.nextDouble() < KordonBalance.CONTRACT_RAID_NODE_CHANCE
                    // v17 (карточка 022): налёт не заказывают на узел, где еды нет — иначе добыча берётся «из воздуха»
                    && farmDepot(s, bandits, farm).get(Good.FOOD) > 0) {
                long reward = KordonBalance.REWARD_RAID + r.nextInt(KordonBalance.REWARD_RAID_SPREAD);
                Contract c = issueCash(s, Contract.Type.RAID_NODE, bandits.id, reward,
                        KordonBalance.CONTRACT_RAID_DAYS, KordonBalance.CONTRACT_DIFFICULTY);
                if (c != null) { c.node = farm; c.qty = s.nodes.get(farm).owner; }
            }
        }
    }

    /** Курьер Торговцев, идущий в пути к покупателю (карточка 021). */
    private Caravan courierInTransit(Simulation s) {
        return courierTo(s, -1);
    }

    /** Курьер Торговцев в пути; buyerId >= 0 — только к этому покупателю (карточка 022). */
    private Caravan courierTo(Simulation s, int buyerId) {
        Faction traders = s.traders();
        if (traders == null) return null;
        for (Caravan c : s.caravans) if (c.owner == traders.id && c.courierFor >= 0 && !c.returning
                && (buyerId < 0 || c.courierFor == buyerId)) return c;
        return null;
    }

    /** Склад, с которого налёт берёт еду: сам узел, а для базы бандитов — их общий склад (карточка 022). */
    private Stock farmDepot(Simulation s, Faction bandits, int node) {
        return node == bandits.baseNode ? bandits.stock : s.nodes.get(node).depot;
    }

    /** Чужая ферма для налёта Бандитов: сперва Свиноферма, иначе любой чужой узел с farmCap (карточка 021). */
    private int farmTarget(Simulation s, int banditId) {
        int farm = KordonBalance.CONTRACT_RAID_FARM;
        if (farm < s.nodes.size() && s.nodes.get(farm).farmCap > 0 && s.nodes.get(farm).owner >= 0
                && s.nodes.get(farm).owner != banditId) return farm;
        for (Node n : s.nodes) if (n.farmCap > 0 && n.owner >= 0 && n.owner != banditId) return n.id;
        return -1;
    }

    /** Логово стоит на дорожном узле военных или рядом с ним. */
    private boolean lairByRoad(Simulation s, int node) {
        for (int road : KordonBalance.MILITARY_ROAD_NODES) {
            if (road >= s.nodes.size()) continue;
            if (road == node || s.graph.dist(road, node) <= 1) return true;
        }
        return false;
    }

    /** База покупателя для сопровождения курьера: живая группировка, кроме самих Торговцев. */
    private Faction escortBuyer(Simulation s, Faction traders, Random r) {
        List<Faction> pool = new ArrayList<>();
        for (Faction f : s.factions) {
            if (f == traders || f.isService() || f.members < 5) continue;
            if (f.baseNode == traders.baseNode) continue;
            pool.add(f);
        }
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
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
        // v16 (карточка 006): на Кордоне из трёх фракций у встречи почти никогда нет врага рядом,
        // поэтому засаду заказывает враждебная хозяину группировка и издалека
        if (h == null && s.scenario.equals(Scenarios.KORDON)) h = kordonAmbusher(s, m);
        if (h != null) {
            long reward = 2000 + r.nextInt(1000);
            // v17 (карточка 021): на Кордоне засаду на встречу заказчик оплачивает только из кассы;
            // нет денег — контракт не выставляется. В полной Зоне остаётся выдача товаром (issue).
            Contract a = s.scenario.equals(Scenarios.KORDON)
                    ? issueCash(s, Contract.Type.AMBUSH_MEETING, h.id, reward, m.day - s.day, 4)
                    : issue(s, Contract.Type.AMBUSH_MEETING, h.id, reward, m.day - s.day, 4);
            if (a != null) { a.node = m.venue; a.deadline = m.day; a.qty = m.host * 100 + m.guest; }
        }
    }

    /** Заказчик засады на встречу на Кордоне: враждебная хозяину группировка (заказчик нанимает исполнителя, не идёт сам). */
    private Faction kordonAmbusher(Simulation s, Politics.Meeting m) {
        Faction best = null;
        double bestScore = 0;
        for (Faction h : s.factions) {
            if (h.id == m.host || h.id == m.guest || h.passive || h.isService()) continue;
            if (h.members < KordonBalance.CONTRACT_MIN_LONERS) continue;
            double hostile = Math.max(s.hostilityTo(h, m.host), s.hostilityTo(h, m.guest));
            if (hostile < 0.6) continue;
            double score = hostile * (0.4 + h.aggression);
            if (h == s.mercenaries()) score += 10; // карточка 006: засаду на встречу заказывают прежде всего Одиночки
            if (score > bestScore) { bestScore = score; best = h; }
        }
        return best;
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
        // v16 (карточка 006): на Кордоне одиночки берутся за работу охотнее
        boolean kordon = s.scenario.equals(Scenarios.KORDON);
        double chance = kordon ? KordonBalance.CONTRACT_NPC_TAKE_CHANCE : NPC_TAKE_CHANCE;
        // карточка 034: на Кордоне заказы разбирают почти сразу, а не через три дня
        int takeDelay = kordon ? KordonBalance.CONTRACT_NPC_TAKE_DELAY_DAYS : NPC_TAKE_DELAY_DAYS;
        for (Contract c : list) {
            // своё же сопровождение/поставка не заказываются себе; захват узла одиночки берут и у себя
            if (c.issuer == loners.id && c.type != Contract.Type.CAPTURE_NODE) continue;
            // карточка 026: взятый игроком контракт NPC не отбирают (правило держим явно, а не только по статусу)
            if (c.taker == Contract.PLAYER) continue;
            if (c.status != Contract.Status.OPEN || s.day - c.created < takeDelay) continue;
            // v17 (карточка 021): AMBUSH_COURIER и RAID_NODE берут только игроки-бандиты — NPC их не разбирают
            if (c.type != Contract.Type.FETCH_ARTIFACT && c.type != Contract.Type.SUPPLY && c.type != Contract.Type.CLEAR_LAIR
                    && c.type != Contract.Type.ESCORT_COURIER && c.type != Contract.Type.CAPTURE_NODE) continue;
            if (r.nextDouble() > chance) continue;
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
                    int moved = Stock.move(t.stock, iss.stock, c.good, c.qty);
                    int rest = c.qty - moved;
                    iss.stock.add(c.good, rest);
                    s.ledger.created[c.good.ordinal()] += rest;
                }
            }
            case SUPPLY -> ok = ok && Stock.move(t.stock, iss.stock, c.good, c.qty) > 0;
            case CLEAR_LAIR -> {
                Lair l = s.lairAt(c.node);
                if (l == null) ok = false;
                else if (ok) s.mutantKilled(l, l.strength, false);
                else s.applyCasualtiesOf(t, 1 + r.nextInt(3));
            }
            // v16 (карточка 006): курьер Торговцев дошёл до базы покупателя под охраной
            case ESCORT_COURIER -> {
                if (ok) s.courierArrived(c.qty);
            }
            // v16: узел закрывается только со сменой владельца
            case CAPTURE_NODE -> ok = captureResolve(s, t, c, ok);
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

    /**
     * CAPTURE_NODE: контракт закрывается только со сменой владельца узла. Если врага уже выбили с узла —
     * цель достигнута; иначе исполнитель сам выбивает владельца (если это вообще возможно) и узел переходит заказчику.
     */
    private boolean captureResolve(Simulation s, Faction t, Contract c, boolean roll) {
        Node n = s.nodes.get(c.node);
        if (n.owner != c.qty) return true;                  // владелец сменился — заказ выполнен
        int to = c.issuer >= 0 ? c.issuer : t.id;           // узел займёт заказчик
        if (n.owner == t.id || s.noCapture(c.node)) return false; // узел уже наш или его не берут
        if (to == c.qty) return false;                      // у заказчика тот же владелец — брать нечего
        if (!roll) return false;
        s.contractCapture(t, to, c.node);
        return true;
    }

    // ------------------------------------------------------------------ игрок

    public String take(Simulation s, int id, String player) {
        Contract c = byId(id);
        if (c == null || c.status != Contract.Status.OPEN) return "Контракт недоступен";
        if (ofPlayer(player).size() >= MAX_PER_PLAYER) return "Не больше " + MAX_PER_PLAYER + " контрактов одновременно";
        c.status = Contract.Status.TAKEN;
        c.taker = Contract.PLAYER;
        c.player = player;
        if (c.type == Contract.Type.CLEAR_LAIR) {
            Lair l = s.lairAt(c.node);
            c.lairTotal = l != null ? l.mutantCount() : 0;
            c.lairKilled = 0;
            // срок контракта игрока на логово — не меньше минимума (карточка 026)
            c.deadline = Math.max(c.deadline, s.day + KordonBalance.CONTRACT_LAIR_PLAYER_MIN_DAYS);
        }
        return null;
    }

    /**
     * Карточка 026: игрок убил мутанта логова. Уменьшаем счётчик целей его контракта на это логово.
     *
     * @return короткое сообщение «Логово: осталось K» для чата или null, если у игрока нет контракта на это логово
     */
    public String lairMutantKilled(int node, String player) {
        for (Contract c : list) {
            if (c.type != Contract.Type.CLEAR_LAIR || c.node != node) continue;
            if (c.status != Contract.Status.TAKEN || c.taker != Contract.PLAYER || !c.player.equals(player)) continue;
            c.lairKilled++;
            int left = Math.max(0, c.lairTotal - c.lairKilled);
            return "Логово: осталось " + left;
        }
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

    /**
     * Курьер Торговцев дошёл до базы: закрыть контракт сопровождения.
     * Игровой слой зовёт это, когда сопровождённый NPC-курьер доходит; быть рядом не обязательно.
     */
    public Reward escortArrived(Simulation s, int id, String player) {
        Contract c = byId(id);
        if (c == null || c.status != Contract.Status.TAKEN || c.type != Contract.Type.ESCORT_COURIER
                || c.taker != Contract.PLAYER || !c.player.equals(player)) return null;
        s.courierArrived(c.qty);
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

    /**
     * Игрок-бандит устроил засаду на курьера Торговцев: караван снимается с маршрута с потерей груза
     * (как при обычном нападении), груз до получателя не доходит — контракт закрыт с наградой.
     * Если курьер уже доставил груз или исчез — контракт проваливается, награды нет.
     * Игровой слой зовёт это, когда засада на курьера удалась (карточки 021, 022).
     */
    public Reward courierAmbushed(Simulation s, int buyerId, String player) {
        Reward sum = null;
        for (Contract c : list) {
            if (c.status != Contract.Status.TAKEN || c.type != Contract.Type.AMBUSH_COURIER
                    || c.taker != Contract.PLAYER || !c.player.equals(player) || c.qty != buyerId) continue;
            // цель контракта: c.qty — покупатель (courierFor), c.node — пункт назначения курьера
            Caravan courier = courierTo(s, c.qty);
            if (courier != null && courier.route[courier.route.length - 1] == c.node
                    && s.ambushCaravan(courier, s.factions.get(c.issuer))) {
                sum = sum(sum, payPlayer(s, c));
            } else {
                // курьер уже дошёл или пропал: заказ провален, залог возвращается заказчику, награды нет
                c.status = Contract.Status.FAILED;
                failed++;
                refund(s, c);
            }
        }
        return sum;
    }

    /**
     * Игрок-бандит разорил ферму: часть запасов еды узла (не больше, чем там есть) уходит заказчику (бандитам),
     * владелец не меняется. Игровой слой зовёт это, когда налёт на узел удался (карточки 021, 022).
     */
    public Reward raidedFarm(Simulation s, int node, String player) {
        Reward sum = null;
        for (Contract c : list) {
            if (c.status != Contract.Status.TAKEN || c.type != Contract.Type.RAID_NODE
                    || c.node != node || c.taker != Contract.PLAYER || !c.player.equals(player)) continue;
            Faction bandits = s.factions.get(c.issuer);
            Stock depot = farmDepot(s, bandits, node);
            if (depot != bandits.stock) {
                int food = depot.get(Good.FOOD);
                Stock.move(depot, bandits.stock, Good.FOOD, Math.min(food, food / 3));
            }
            sum = sum(sum, payPlayer(s, c));
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
