package ru.stalkerzone.sim;

/** Группировка: люди, склад с кошельком, фаза кампании, характер и память командования. */
public final class Faction {
    public final int id;
    public final String name;
    public final int baseNode;
    public final double aggression;
    /** Грабит чужие караваны на своих территориях. */
    public final boolean raider;
    public final Doctrine doctrine;

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

    public int members;
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
