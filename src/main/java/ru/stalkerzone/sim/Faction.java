package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/** Группировка: люди, склад с кошельком, фаза кампании, характер и память командования. */
public final class Faction {
    public final int id;
    public final String name;
    public final int baseNode;
    public final double aggression;
    /** Грабит чужие караваны на своих территориях. */
    public final boolean raider;
    /** Служебная фракция (напр. Торговцы): не участвует в симуляции, не имеет ИИ. */
    public boolean service;
    /** Характер и идеология фракции: задаётся сценарием, может уточняться после создания. */
    public Doctrine doctrine;

    // --- роль в сюжете (задаётся сценарием, не сохраняется) ---
    /** Никогда не начинает войн (учёные). */
    public boolean passive;
    /** Учёные: ведут исследование Выжигателя, цель Монолита. */
    public boolean scientist;
    /** Армия: приходит на помощь учёным. */
    public boolean military;
    /** Монолит: спит, пока работает Выжигатель; не торгует, ест вдвое меньше, пополняется бесплатно. */
    public boolean fanatic;
    /** Наёмники: сопровождают экспедицию к Выжигателю за плату. */
    public boolean mercenary;
    /** Карточка 054: только гарнизон южного блокпоста — ИИ не действует (ни войн, ни отрядов, ни захвата). */
    public boolean garrisonOnly;

    public int members;
    /** Э2-17 (К10): группы фракции; members — сумма по группам (синхронизация в конце дня). */
    public final List<Group> groups = new ArrayList<>();
    /** Средний опыт бойцов 0..100: растёт в боях, новички его разбавляют. */
    public double exp = 20;
    /** Средний стресс 0..100: растёт в войнах и от потерь, снимается на базе. */
    public double stress = 15;
    /** Раненые (входят в members, но не воюют): их лечит медпункт. */
    public int wounded;
    /** Единство 0..1: при низком отрезанные гарнизоны откалываются. */
    public double unity = 0.85;
    /** Какой характер тянется к фракции (для новичков): преданность и жадность, −1..1. Задаётся сценарием. */
    public double idealLoyalty, idealGreed;
    /** Получает людей из потока новичков через Кордон (иначе пополняется сам: армия, учёные, Монолит). */
    public boolean takesNewcomers;
    public final Stock stock = new Stock();
    /** Сколько стройматериалов нужно на текущие стройки (пересчитывается каждый день, не сохраняется). */
    public int matWant;
    /** Доля бойцов в профессиональных группах (снайперы, штурмовики, экспедиции). Задаётся сценарием. */
    public double eliteShare = 0.08;
    /** Своя мастерская слабая: докупает прототипы у торговцев вместо части кустарного снаряжения. Задаётся сценарием. */
    public boolean protoBuyer;

    public Phase phase = Phase.ACCUMULATE;
    public int phaseDays;
    public int target = -1;
    public int warNode = -1;
    public double siege;
    public int lastDispatch = -100;

    // --- командование ---
    /** Не сохраняется: мозг не хранит состояния. Если не задан, симуляция поставит UtilityBrain. */
    public FactionBrain brain;
    public Goal goal = Goal.FORTIFY;
    public int goalTarget = -1;
    public int goalSince = -100;
    /** Принимать бой с Монолитом (иначе отходить без боя). Решает командование, сохраняется. */
    public boolean fightMonolith;
    /** Приоритеты производства: патроны, снаряжение, медикаменты. */
    public final double[] prio = {1, 1, 1};
    /** Обиды на другие фракции (растут от нападений и ограблений, со временем затухают). */
    public double[] grudge;
    /** Память о провалившихся штурмах по локациям. */
    public double[] nodeFailure;
    /** Память об опасности торговых маршрутов: куда караваны не дошли. */
    public double[] hubRisk;

    // статистика
    public int deaths;
    public int warsStarted;
    public int nodesCaptured;
    public int nodesLost;
    public int caravansLost;
    /**
     * К2: отметка, что событие уничтожения уже зарегистрировано (журнал). Не сохраняется:
     * при загрузке восстанавливается из members, чтобы уничтожение не регистрировалось повторно.
     */
    boolean destructionNoted;

    public Faction(int id, String name, int baseNode, double aggression, boolean raider, int members, Doctrine doctrine) {
        this.id = id;
        this.name = name;
        this.baseNode = baseNode;
        this.aggression = aggression;
        this.raider = raider;
        this.members = members;
        this.doctrine = doctrine;
    }

    void initMemory(int nodeCount, int factionCount) {
        grudge = new double[factionCount];
        nodeFailure = new double[nodeCount];
        hubRisk = new double[nodeCount];
    }

    public boolean isService() { return service; }

    /**
     * К2 (правило B HQ): группировка с нулевой численностью уничтожена. Состояние производное от members,
     * отдельного поля в сохранении не требует. Служебные фракции (Торговцы) не участвуют в симуляции
     * и уничтоженными не считаются. Возрождение — только отдельным событием вольных сталкеров
     * ({@link Simulation#reviveByFreeStalkers}), ежедневные циклы его не запускают.
     */
    public boolean isDestroyed() { return !service && members <= 0; }

    /**
     * К2 (правило B HQ): фактическая гибель участников. Гибнет не больше, чем есть людей (расчётные
     * потери сверх численности не завышают deaths); раненые входят в members и не могут пережить
     * всех остальных. Возвращает фактическое число погибших.
     */
    public int killMembers(int n) {
        int actual = Math.min(Math.max(0, n), members);
        members -= actual;
        deaths += actual;
        wounded = Math.min(wounded, members);
        return actual;
    }

    /** Э2-17 (К10): сумма членов групп фракции. */
    public int groupMembers() {
        int s = 0;
        for (Group g : groups) s += g.members;
        return s;
    }

    /**
     * Э2-17 (К10): численность фракции распределяется по группам пропорционально их размеру
     * (метод наибольших остатков, детерминированно, без случайности). Вызывается в конце дня:
     * сумма по группам равна members. Поведение ежедневных циклов фракции не меняется —
     * группы отражают численность, а не управляют ею.
     */
    public void syncGroupMembers() {
        if (groups.isEmpty()) return;
        int total = groupMembers();
        int delta = members - total;
        if (delta == 0) return;
        if (groups.size() == 1 || total == 0) {
            groups.get(0).members = members;
            return;
        }
        int n = groups.size();
        int[] base = new int[n];
        double[] frac = new double[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            double exact = (double) delta * groups.get(i).members / total;
            base[i] = (int) Math.floor(exact);
            frac[i] = exact - base[i];
            assigned += base[i];
        }
        int rem = delta - assigned;
        // остаток — большим дробным частям первым, при равенстве — раньше в списке
        for (int k = 0; k < rem; k++) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (frac[i] <= 0) continue;
                if (best < 0 || frac[i] > frac[best]) best = i;
            }
            if (best < 0) break;
            base[best]++;
            frac[best] = 0;
        }
        for (int i = 0; i < n; i++) groups.get(i).members += base[i];
    }

    void decayMemory() {
        for (int i = 0; i < grudge.length; i++) grudge[i] *= 0.985;
        for (int i = 0; i < nodeFailure.length; i++) nodeFailure[i] *= 0.99;
        for (int i = 0; i < hubRisk.length; i++) hubRisk[i] *= 0.985;
    }

    /** Запомнить обиду на другую фракцию. */
    public void resent(int other, double amount) {
        if (other >= 0 && other != id) grudge[other] += amount;
    }

    /** Желаемый запас каждого товара на складе — зависит от численности. */
    public int targetOf(Good g) {
        int elite = Arms.elite(this);
        boolean need = Arms.specialNeed(this);
        switch (g) {
            case FOOD: return members * 6;
            case PARTS: return members * 12;
            // армия воюет заводским, кустарные держит только на крайний случай
            case AMMO: return military ? members * 6 : members * 45;
            case MEDS: return members * 3;
            case GEAR: return military ? 0 : (int) Math.ceil(members * (protoBuyer ? 0.5 : 0.7));
            case MATERIALS: return matWant + members / 2;
            case PROTO: return scientist ? 3 : protoBuyer ? (int) Math.ceil(members * 0.2) : 0;
            case AMMO_PRO: return military ? members * 22 : need ? elite * 40 : 0;
            case GEAR_PRO: return military ? (int) Math.ceil(members * 0.35) : need ? (int) Math.ceil(elite * 0.8) : 0;
            default: return 0;
        }
    }
}
