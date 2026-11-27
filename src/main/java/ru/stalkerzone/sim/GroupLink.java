package ru.stalkerzone.sim;

import java.util.Random;

/**
 * Э2-21: связь «ведущая → зависимая» группа Кордона (docs/thezone/11-group-links.md,
 * docs/spec/faction-hierarchy.md §1). Ведущая даёт людей, снабжение и защиту и берёт долю;
 * зависимая по агрессивности, идеологии и обстоятельствам может отколоться или напасть.
 *
 * <p>Тип связи и норма доли — по идеологии ведущей силы: приказ (военные) — без доли, снабжение
 * сверху, добыча на склад; дань (бандиты) — 30–50 %; договор (одиночки) — 10–20 %; договор защиты
 * (нейтральный хаб) — без доли и подчинения. Напряжение 0..1 растёт от доли выше нормы, непоставленной
 * помощи, слабости ведущей, амбиций командира и агрессивности; падает от помощи, побед, повышений и
 * общей угрозы. При пороге — событие своего типа: договор — мирный уход, дань — мятеж, приказ —
 * дезертирство поодиночке (группа не откалывается), защита — расторжение договора.
 *
 * <p>Доля и поддержка физичны: кассы групп ({@link Group#stock}), существующие конвои
 * ({@link Convoy#forGroup}) по маршрутам {@link Simulation#route}; у неподвижных гарнизонов
 * (карточки 054/057) — курьером, без конвоев. Приход услуги зависимой точки учитывается в
 * {@link Ledger#moneyMinted} (как выручка торговцев с Большой земли); передача доли сумму не меняет.
 * События ограничены кулдауном и пределом событий — снежного кома нет. Набег мутантов — Э2-22,
 * нейтральная база деревни — Э2-23, раскол по гибели лидера — Э7-02, звания 14 сил — Э7-10.
 */
public final class GroupLink {
    /** Тип связи по идеологии ведущей силы; maxShare — верхняя граница нормы доли. */
    public enum Kind {
        ORDER("приказ", 0), TRIBUTE("дань", 0.5), CONTRACT("договор", 0.2), PROTECTION("договор защиты", 0);
        public final String ru;
        public final double maxShare;
        Kind(String ru, double maxShare) { this.ru = ru; this.maxShare = maxShare; }
    }

    /** Порог напряжения, при котором происходит событие связи. */
    public static final double THRESHOLD = 0.85;
    /** Дней между событиями одной связи. */
    public static final int COOLDOWN = 30;
    /** Предел событий дани: после него мятеж разрывает связь окончательно. */
    public static final int MAX_EVENTS = 3;

    /** Ведущая группа (id); при мятеже-перевороте роли меняются. */
    public String leader;
    /** Зависимая группа (id). */
    public String dependent;
    public final Kind kind;
    /** Доля ведущей 0..1 (приказ и защита — 0). */
    public double share;
    /** Поддержка: люди при нехватке. */
    public boolean supportPeople;
    /** Поддержка: снабжение (деньги на еду и патроны; физические конвои фракции — существующие). */
    public boolean supportSupply;
    /** Поддержка: защита (дозор и подмога; для нейтрального хаба). */
    public boolean supportProtection;
    /** Склонность к отколу 0..1 (проводники и Дальний блокпост — высокая). */
    public double splitTendency;
    /** Напряжение 0..1. */
    public double tension;
    /** День последнего события (кулдаун); стартовое значение разрешает первое событие сразу. */
    public int lastEventDay = -1000;
    /** Всего передано ведущей (руб): доля, дань, сданная добыча. */
    public long sharePaid;
    /** Всего получено поддержки от ведущей (руб). */
    public long supportReceived;
    /** Число пороговых событий связи. */
    public int events;
    /** Недельный приход услуги зависимой точки (руб): плата за проход/дозор/проводку, пошлина. */
    public long incomeRate;
    /** Победы ведущей на прошлой сверке (фактор спада напряжения). */
    public int lastLeaderCaptures;
    /** Звание командира зависимой на прошлой сверке (фактор спада напряжения). */
    public int lastLeaderRank;

    public GroupLink(String leader, String dependent, Kind kind) {
        this.leader = leader;
        this.dependent = dependent;
        this.kind = kind;
    }

    /** Параметры связи одним вызовом (сценарий). */
    public GroupLink share(double v) { this.share = clamp01(v); return this; }
    public GroupLink support(boolean people, boolean supply, boolean protection) {
        this.supportPeople = people; this.supportSupply = supply; this.supportProtection = protection;
        return this;
    }
    public GroupLink splitTendency(double v) { this.splitTendency = clamp01(v); return this; }
    public GroupLink income(long rate) { this.incomeRate = Math.max(0, rate); return this; }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    /**
     * Связь работает, когда обе группы живы и точка зависимой под своей фракцией. Договор допускает
     * ничейную точку (проход, лагерь); приказ и дань требуют рабочую точку; защита — живой хаб.
     */
    private boolean active(Simulation s, Group lead, Group dep) {
        if (lead == null || dep == null || lead.members <= 0 || dep.members <= 0) return false;
        if (kind == Kind.PROTECTION) return true;
        int owner = s.nodes.get(dep.baseNode).owner;
        if (kind == Kind.CONTRACT) return owner == dep.faction || owner < 0;
        return owner == dep.faction;
    }

    /**
     * День связи: факторы напряжения, недельный расчёт (день % 7 == 1), пороговое событие.
     * Возвращает false, когда связь распалась (вызывающий убирает её из списка).
     */
    public boolean daily(Simulation s, Random r) {
        Group lead = s.groupById(leader), dep = s.groupById(dependent);
        if (!active(s, lead, dep)) return true;
        // рост: доля выше нормы идеологии, агрессия и амбиции зависимой, слабость ведущей, голод без помощи
        if (share > kind.maxShare) tension += 0.04 * (share - kind.maxShare);
        tension += 0.006 * dep.aggression * (0.5 + splitTendency);
        tension += 0.006 * dep.leaderInfluence;
        if (lead.members < dep.members) tension += 0.02;
        if (supportSupply) {
            Node n = s.nodes.get(dep.baseNode);
            if (n.owner == dep.faction && n.depot.get(Good.FOOD) < dep.members) tension += 0.015;
        }
        if (supportProtection && lead.members < 5) tension += 0.02;
        // спад: общая угроза (мутанты у точки, недавний выброс)
        if (s.nodeLairPower(dep.baseNode) > 0 || (s.lastEmissionDay >= 0 && s.day - s.lastEmissionDay <= 3)) tension -= 0.015;
        tension = clamp01(tension);
        if (s.day % 7 == 1) settle(s, r);
        if (tension >= THRESHOLD && s.day - lastEventDay >= COOLDOWN) return event(s, r);
        return true;
    }

    /**
     * Недельный расчёт связи: приход услуги зависимой точки, поддержка ведущей, доля вверх,
     * победы и повышения. Всё физично: кассы групп и склад фракции, приход — через Ledger.
     */
    public void settle(Simulation s, Random r) {
        Group lead = s.groupById(leader), dep = s.groupById(dependent);
        if (!active(s, lead, dep)) return;
        // 1. приход услуги: договор — плата за дозор/проход/проводку; дань — пошлина с перехода (точка работает)
        long income = 0;
        if (incomeRate > 0 && (kind == Kind.CONTRACT
                || (kind == Kind.TRIBUTE && s.nodes.get(dep.baseNode).owner == dep.faction))) {
            income = incomeRate;
            dep.stock.money += income;
            s.ledger.moneyMinted += income;
        }
        // 2. приказ: добыча гарнизона сдаётся на склад ведущей вся, до нового снабжения (доли нет);
        // пошлина моста уже на складе фракции ведущей (crossPass не меняется)
        if (kind == Kind.ORDER) {
            long give = dep.stock.money;
            if (give > 0) {
                Stock.moveMoney(dep.stock, lead.stock, give);
                sharePaid += give;
                s.log("СКЛАД: «" + dep.name + "» сдала добычу на склад «" + lead.name + "»: " + give + " руб. (приказ)");
            }
        }
        // 3. поддержка ведущей
        if (supportProtection) {
            if (lead.members >= 5) tension -= 0.03;
            else tension += 0.02;
        }
        if (supportSupply) {
            long need = dep.members * 2L;
            long pay = Math.min(need, lead.stock.money);
            if (pay == 0 && kind == Kind.ORDER && lead.faction >= 0) {
                // приказ: снабжение сверху со склада фракции ведущей (сверх оперативного резерва)
                Faction lf = s.factions.get(lead.faction);
                pay = Math.min(need, Math.max(0, lf.stock.money - lf.members * 8L));
                if (pay > 0) { lf.stock.money -= pay; lead.stock.money += pay; }
            }
            if (pay > 0) {
                // приказ: снабжение сверху — внутренний перенос со склада ведущей, без конвоя
                if (kind == Kind.ORDER) Stock.moveMoney(lead.stock, dep.stock, pay);
                else transfer(s, r, lead, dep, pay, Convoy.Kind.SUPPLY);
                supportReceived += pay;
                tension -= 0.06;
            } else {
                tension += 0.05;
                s.log("ПОДДЕРЖКА НЕ ПРИШЛА: «" + lead.name + "» не смогла помочь «" + dep.name + "»");
            }
        }
        if (supportPeople && dep.faction == lead.faction && dep.members < 4 && lead.members > 8) {
            int k = Math.min(2, Math.min(lead.members - 8, 4 - dep.members));
            if (k > 0) {
                lead.members -= k;
                dep.members += k;
                s.log("ПОПОЛНЕНИЕ: " + k + " бойцов из «" + lead.name + "» в «" + dep.name + "»");
            }
        }
        // 4. доля вверх (договор и дань): часть прихода услуги — после полученной поддержки
        if (kind != Kind.ORDER && share > 0 && income > 0) {
            long due = Math.min(Math.round(income * share), dep.stock.money);
            if (due > 0) {
                transfer(s, r, dep, lead, due, Convoy.Kind.TRIBUTE);
                sharePaid += due;
                s.log("ДОЛЯ: «" + dep.name + "» платит «" + lead.name + "» " + due + " руб. (" + kind.ru + ")");
            }
        }
        // 5. победы ведущей и повышение командира зависимой
        if (lead.faction >= 0) {
            Faction lf = s.factions.get(lead.faction);
            if (lf.nodesCaptured > lastLeaderCaptures) tension -= 0.05;
            lastLeaderCaptures = lf.nodesCaptured;
        }
        if (dep.leader != null) {
            if (dep.leader.rank() > lastLeaderRank) tension -= 0.04;
            lastLeaderRank = dep.leader.rank();
        }
        tension = clamp01(tension);
    }

    /** Перенос денег между кассами групп: конвоем по существующему маршруту, у неподвижного гарнизона — курьером. */
    private void transfer(Simulation s, Random r, Group from, Group to, long amount, Convoy.Kind k) {
        if (amount <= 0) return;
        Faction f = from.faction >= 0 ? s.factions.get(from.faction) : null;
        boolean byConvoy = f != null && !f.garrisonOnly && from.baseNode != to.baseNode
                && s.graph.reachable(from.baseNode, to.baseNode);
        if (byConvoy) {
            Convoy c = new Convoy(f.id, k, s.route(f, from.baseNode, to.baseNode, r),
                    Math.min(2, Math.max(1, from.members / 10)));
            c.forGroup = to.id;
            Stock.moveMoney(from.stock, c.cargo, amount);
            s.logistics.launch(s, c);
        } else {
            Stock.moveMoney(from.stock, to.stock, amount);
        }
    }

    /** Пороговое событие по типу связи. Возвращает false, когда связь распалась. */
    private boolean event(Simulation s, Random r) {
        Group lead = s.groupById(leader), dep = s.groupById(dependent);
        if (lead == null || dep == null) {
            if (dep != null) dep.dependsOn = null;
            return false;
        }
        lastEventDay = s.day;
        events++;
        switch (kind) {
            case CONTRACT -> {
                s.log("УХОД: «" + dep.name + "» расторгла договор с «" + lead.name + "» и стала самостоятельной");
                dep.dependsOn = null;
                return false;
            }
            case PROTECTION -> {
                s.log("ЗАЩИТА: договор защиты «" + dep.name + "» с «" + lead.name + "» расторгнут — хаб нейтрален");
                dep.dependsOn = null;
                return false;
            }
            case TRIBUTE -> {
                if (dep.members > lead.members && events < MAX_EVENTS) {
                    // зависимая сильнее — захват ведущей роли (роль ведущей меняется, группа не плодится)
                    String old = leader;
                    leader = dependent;
                    dependent = old;
                    Group newLead = s.groupById(leader), newDep = s.groupById(dependent);
                    if (newLead != null) newLead.dependsOn = null;
                    if (newDep != null) newDep.dependsOn = leader;
                    incomeRate = 0;
                    tension = 0.2;
                    s.log("МЯТЕЖ: «" + (newLead == null ? leader : newLead.name) + "» свергла «"
                            + (newDep == null ? dependent : newDep.name) + "» и забрала ведущую роль (дань)");
                    return true;
                }
                int kL = Math.min(2 + r.nextInt(2), Math.max(0, lead.members - 1));
                int kD = Math.min(1 + r.nextInt(2), Math.max(0, dep.members - 1));
                if (lead.faction == dep.faction && lead.faction >= 0) {
                    Faction f = s.factions.get(lead.faction);
                    f.killMembers(kL + kD);
                    lead.members -= kL;
                    dep.members -= kD;
                    f.unity = Math.max(0, f.unity - 0.08);
                }
                s.log("МЯТЕЖ: «" + dep.name + "» напала на «" + lead.name + "» (дань): погибло " + (kL + kD) + ", связь разорвана");
                dep.dependsOn = null;
                return false;
            }
            case ORDER -> {
                // приказ: дезертирство поодиночке, группа не откалывается
                int k = Math.min(1 + (tension > 0.95 ? 1 : 0), Math.max(0, dep.members - 1));
                if (k > 0 && dep.faction >= 0) {
                    Faction f = s.factions.get(dep.faction);
                    dep.members -= k;
                    f.members -= k;
                    f.wounded = Math.min(f.wounded, f.members);
                    Faction to = null;
                    for (Faction g : s.factions) {
                        if (g == f || g.fanatic || !g.takesNewcomers || g.members < 3) continue;
                        if (g.raider && r.nextDouble() < 0.45 || !g.raider && to == null) to = g;
                    }
                    String where;
                    if (to != null) { to.members += k; where = "ушли к «" + to.name + "»"; }
                    else where = "ушли из Зоны";
                    s.log("ДЕЗЕРТИРСТВО: " + k + " бойцов «" + dep.name + "» " + where + " (приказ)");
                }
                tension = 0.4;
                return true;
            }
        }
        return true;
    }
}