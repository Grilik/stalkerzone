package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Балансовые константы и логика логов для сценария «Кордон 1.0» (карточки 005, 007).
 * Без импортов Minecraft, компилируется чистым javac.
 */
public final class KordonBalance {

    private KordonBalance() {}

    // --- стартовые логова: узел, вид, начальная сила ---------------------------------
    public static final LairDef[] START_LAIRS = {
            new LairDef(13, LairKind.DOGS, 20.0),
            new LairDef(9, LairKind.DOGS, 20.0),
            new LairDef(14, LairKind.FLESH, 20.0),
            new LairDef(15, LairKind.FLESH, 20.0),
            new LairDef(7, LairKind.RODENTS, 18.0),
    };

    /** Описание стартового логова. */
    public static final class LairDef {
        public final int node;
        public final LairKind kind;
        public final double strength;
        public LairDef(int node, LairKind kind, double strength) {
            this.node = node; this.kind = kind; this.strength = strength;
        }
    }

    // --- районы логов -----------------------------------------------------------------
    public static final int[] DISTRICT_SOUTH  = {13, 16, 9};
    public static final int[] DISTRICT_NORTH  = {14, 15, 17, 18};
    public static final int[] DISTRICT_TUNNEL = {7};
    public static final int[][] ALL_DISTRICTS = {DISTRICT_SOUTH, DISTRICT_NORTH, DISTRICT_TUNNEL};

    // --- пулы видов для возрождения ----------------------------------------------------
    public static final KindWeight[] POOL_SOUTH = {
            new KindWeight(LairKind.DOGS, 50), new KindWeight(LairKind.FLESH, 30),
            new KindWeight(LairKind.RODENTS, 15), new KindWeight(LairKind.BLOODSUCKERS, 5),
    };
    public static final KindWeight[] POOL_NORTH = {
            new KindWeight(LairKind.FLESH, 40), new KindWeight(LairKind.DOGS, 35),
            new KindWeight(LairKind.POLTERGEISTS, 15), new KindWeight(LairKind.BLOODSUCKERS, 10),
    };
    public static final KindWeight[] POOL_TUNNEL = {
            new KindWeight(LairKind.RODENTS, 70), new KindWeight(LairKind.POLTERGEISTS, 30),
    };
    public static final KindWeight[][] ALL_POOLS = {POOL_SOUTH, POOL_NORTH, POOL_TUNNEL};

    public static final class KindWeight {
        public final LairKind kind;
        public final int weight;
        public KindWeight(LairKind kind, int weight) { this.kind = kind; this.weight = weight; }
    }

    // --- константы возрождения ---------------------------------------------------------
    public static final int RESPAWN_MIN = 15;
    public static final int RESPAWN_MAX = 30;
    public static final int SHIFT_STEP = 5;
    public static final int SHIFT_MAX_CAP = 65; // BLOODSUCKERS
    public static final int SHIFT_RESET_DAYS = 30;

    // --- веса целей для нападений -------------------------------------------------------
    public static final double[] RAID_TARGET_WEIGHT = new double[19];
    static { RAID_TARGET_WEIGHT[0] = 1.5; RAID_TARGET_WEIGHT[10] = 1.5; }
    /** Минимальная сила логова для налёта на Кордоне. */
    public static final double LAIR_RAID_MIN = 25;

    // --- опасность проходов -------------------------------------------------------------
    public static final int[][] PASS_EDGES = {
            {0, 7}, {7, 10}, {5, 9}, {9, 11},
    };
    public static final double PASS_DANGER_FLOOR = 0.5;
    /** Базовые значения опасности проходов (заполняется сценарием до первого recalc). */
    public static final double[] PASS_BASE_DANGER = new double[PASS_EDGES.length];

    public static double passDangerFactor(double strength, double cap, int daysDist) {
        if (cap <= 0) return 1.0;
        double ratio = Math.min(1.0, strength / cap);
        return PASS_DANGER_FLOOR + (1.0 - PASS_DANGER_FLOOR) * ratio / (1.0 + daysDist);
    }
// --- поиск района и окно cap ----------------------------------------------------
    public static int districtOf(int node) {
        for (int i = 0; i < ALL_DISTRICTS.length; i++)
            for (int v : ALL_DISTRICTS[i]) if (v == node) return i;
        return -1;
    }

    public static KindWeight[] pool(int district) { return ALL_POOLS[district]; }

    public static LairKind pickKindInWindow(List<LairKind> kinds, List<Integer> weights,
                                             double prevCap, int shift, Random rng) {
        double lo = prevCap - 5 + shift;
        double hi = Math.min(prevCap + 20 + shift, SHIFT_MAX_CAP);
        int totalW = 0;
        int[] w = new int[kinds.size()];
        for (int i = 0; i < kinds.size(); i++) {
            double c = kinds.get(i).cap;
            if (c >= lo && c <= hi) { w[i] = weights.get(i); totalW += w[i]; }
        }
        if (totalW <= 0) return null;
        int pick = rng.nextInt(totalW);
        for (int i = 0; i < kinds.size(); i++) {
            pick -= w[i];
            if (pick < 0) return kinds.get(i);
        }
        return kinds.get(0);
    }

    // ====================================================================================
    //  Баланс группировок Кордона (карточка 005)
    // ====================================================================================

    /** Узел-вход с Большой земли (BIG_LAND). Новички появляются здесь и идут в деревню. */
    public static final int GATEWAY_NODE = 3;
    /** Узел деревни новичков, куда приходят люди с Большой земли. */
    public static final int VILLAGE_NODE = 0;
    /** Узлы-выходы: Большая земля и Свалка — люди покидают срез через них. */
    public static final int[] EXIT_NODES = {3, 12};

    /** Множитель притока новичков для сценария Кордон (относительно ARRIVALS_PER_DAY в People). */
    public static final double KORDON_ARRIVAL_FACTOR = 4.5;
    /** Доля новичков, которые сразу идут к бандитам (дезертиры). */
    public static final double DESERTER_SHARE = 0.15;
    /** Шанс, что сталкер уходит из Зоны через выход (за день, от численности фракции). */
    public static final double LEAVE_ZONE_RATE = 0.0002;
    /** Плата за приём новичка (сжигается через Ledger). */
    public static final long JOIN_FEE = 30;

    /** Узлы, которые военные НЕ атакуют (чужие базы): деревня, бункер, АТП, ферма, дальний пост, выходы. */
    public static final int[] MILITARY_NO_ATTACK = {0, 1, 5, 10, 11, 3, 6, 12};
    /** Узлы-дороги, где военные могут устраивать рейды на бандитов. */
    public static final int[] MILITARY_ROAD_NODES = {4, 8, 9};
    /** Военные предпочитают атаковать бандитов на этих узлах (вес выше). */
    public static final double MILITARY_ROAD_WEIGHT = 2.5;

    /** Шанс перехвата каравана военными (в день, если караван проходит через их узел). */
    public static final double MILITARY_INTERCEPT_CHANCE = 0.03;
    // ====================================================================================
    //  Касса торговцев Кордона (карточка 025)
    // ====================================================================================

    /** Сколько артефактов уровня 2 торговец Кордона обязан скупить подряд (минимальная касса). */
    public static final int TRADER_ARTIFACTS_MIN = 3;
    /** Сколько частей мутантов (в симуляции это сырьё {@link Good#MEDS}) торговец обязан скупить подряд. */
    public static final int TRADER_MUTANT_PARTS_MIN = 10;
    /**
     * Оборотные деньги торговца Кордона (руб.): неснижаемый запас кассы на скупку у сталкеров плюс
     * запас сверх минимума закупки на сдачу, ремонт и мелкие сделки. Ниже него торговец не закупается
     * у Большой земли и не отправляет деньги с курьерами — иначе касса за считанные дни проедалась в ноль
     * и торговец не мог заплатить за артефакт.
     */
    public static final long TRADER_CASH_RESERVE = 6000;
    /**
     * Приток наличных торговцам Кордона с Большой земли (руб. в день): выручка за вывезенный за Периметр товар.
     * Деньги приходят в Зону извне и проводятся через учёт ({@link Ledger#moneyMinted}). Это и есть «приток от
     * экспорта на Большую землю»: без него касса к 4-му дню проедалась в ноль, и торговец не мог заплатить
     * игроку за артефакт.
     */
    public static final long TRADER_MAINLAND_INCOME = 3000;

    /**
     * Сколько денег нужно торговцу Кордона на старте: цена {@link #TRADER_ARTIFACTS_MIN} артефактов уровня 2
     * и {@link #TRADER_MUTANT_PARTS_MIN} частей мутантов по текущим ценам скупки ({@link Simulation#buyPrice}),
     * плюс {@link #TRADER_CASH_RESERVE} на оборот. Считается от цен, а не подбирается на глаз.
     */
    public static long traderStartCash(Simulation sim, Node n) {
        return (long) TRADER_ARTIFACTS_MIN * sim.buyPrice(n, Good.ARTIFACT)
                + (long) TRADER_MUTANT_PARTS_MIN * sim.buyPrice(n, Good.MEDS)
                + TRADER_CASH_RESERVE;
    }

    // ====================================================================================
    //  Контракты Кордона (карточка 006)
    // ====================================================================================

    /** Узел-цель контракта на захват: элеватор (KordonScenario.ELEVATOR). */
    public static final int CONTRACT_CAPTURE_NODE = 4;

    /** Шанс в день, что Торговцы выложат заказ на артефакты или части мутантов. */
    public static final double CONTRACT_FETCH_CHANCE = 0.30;
    /** Шанс в день, что Торговцы выложат заказ на сопровождение курьера до базы покупателя. */
    public static final double CONTRACT_ESCORT_CHANCE = 0.16;
    /** Шанс в день, что Одиночки выложат заказ на захват элеватора. */
    public static final double CONTRACT_CAPTURE_CHANCE = 0.15;
    /** Шанс в день, что военные выложат заказ на зачистку логова у дороги. */
    public static final double CONTRACT_MILITARY_LAIR_CHANCE = 0.20;
    /** Минимальная сила логова у дороги, за которое берутся военные. */
    public static final double CONTRACT_LAIR_MIN_STRENGTH = 12;
    /** Минимальный срок контракта игрока на логово (дней): меньше — не давать, даже если выдали раньше (карточка 026). */
    public static final int CONTRACT_LAIR_PLAYER_MIN_DAYS = 5;

    /** Награда за принос: цена товара с наценкой заказа. */
    public static final double CONTRACT_FETCH_MARKUP = 1.6;
    /** Сколько штук просят за один заказ на принос. */
    public static final int CONTRACT_FETCH_MAX_QTY = 2;
    /** Награды контрактов, выдаваемых не за товар (руб.) и их случайный разброс. */
    public static final long REWARD_ESCORT = 1500, REWARD_CAPTURE = 2500;
    public static final int REWARD_ESCORT_SPREAD = 500, REWARD_CAPTURE_SPREAD = 700;
    /** Награда военным за зачистку логова у дороги: база плюс доля силы логова. */
    public static final long REWARD_MILITARY_LAIR_BASE = 800;
    public static final double REWARD_MILITARY_LAIR_PER_STRENGTH = 30;
    /** Срок исполнения контрактов Кордона (дней). */
    public static final int CONTRACT_FETCH_DAYS = 20, CONTRACT_ESCORT_DAYS = 12, CONTRACT_CAPTURE_DAYS = 20;
    /** Требуемая для NPC-исполнителя численность заказчика по типам контрактов. */
    public static final int CONTRACT_MIN_LONERS = 10, CONTRACT_MIN_MILITARY = 8;
    /** На Кордоне одиночки-исполнители берутся за контракты охотнее, чем в полной Зоне. */
    public static final double CONTRACT_NPC_TAKE_CHANCE = 0.35;
    /** Сложность контрактов Кордона, которые разбирают NPC (1 — почти всегда удача). */
    public static final int CONTRACT_DIFFICULTY = 1;

    // --- заказы Бандитов (карточка 021) --------------------------------------------------
    /** Узел-цель налёта по умолчанию: Свиноферма — именованный индекс сценария, а не сырой номер (карточка 022). */
    public static final int CONTRACT_RAID_FARM = KordonScenario.FARM;
    /** Шанс в день, что Бандиты выложат заказ на засаду против курьера Торговцев в пути. */
    public static final double CONTRACT_AMBUSH_COURIER_CHANCE = 0.12;
    /** Шанс в день, что Бандиты выложат заказ на налёт на чужую ферму. */
    public static final double CONTRACT_RAID_NODE_CHANCE = 0.12;
    /** Награды заказов Бандитов — порядка награды за сопровождение курьера (руб.) и их разброс. */
    public static final long REWARD_AMBUSH = REWARD_ESCORT, REWARD_RAID = REWARD_ESCORT;
    public static final int REWARD_AMBUSH_SPREAD = REWARD_ESCORT_SPREAD, REWARD_RAID_SPREAD = REWARD_ESCORT_SPREAD;
    /** Сроки исполнения заказов Бандитов (дней). */
    public static final int CONTRACT_AMBUSH_DAYS = CONTRACT_ESCORT_DAYS, CONTRACT_RAID_DAYS = CONTRACT_CAPTURE_DAYS;

    // --- Доктрины трёх группировок Кордона (уточняют характер для локального среза) ---

    /** Военные на Кордоне: держат юг и мост, не лезут в чужие базы, но карают бандитов. */
    public static final Doctrine MILITARY_DOCTRINE = new Doctrine(0.15, 0.6, 0.85, 0.3, 0.5, 0.7);
    /** Бандиты Кордона: агрессивны, цель — элеватор и ферма, живут грабежом. */
    public static final Doctrine BANDIT_DOCTRINE = new Doctrine(1.1, 0.9, 0.1, 0.1, 0.0, 0.35);
    /** Одиночки Кордона: осторожны, зарабатывают артефактами и охотой, обороняют своё. */
    public static final Doctrine LONER_DOCTRINE = new Doctrine(0.6, 0.35, 0.5, 0.75, 0.2, 0.4);

    // ====================================================================================
    //  Мозги группировок Кордона (FactionBrain)
    // ====================================================================================

    /** Военные Кордона: не захватывают чужие базы, атакуют бандитов на дорогах. */
    public static final class MilitaryBrain implements FactionBrain {
        private final UtilityBrain inner = new UtilityBrain();

        @Override
        public List<Command> decide(Simulation sim, Faction f, Random rng) {
            List<Command> commands = inner.decide(sim, f, rng);
            List<Command> filtered = new ArrayList<>(commands.size());
            for (Command c : commands) {
                if (c instanceof Command.Attack a) {
                    if (!isMilitaryTarget(sim, a.node())) continue;
                }
                filtered.add(c);
            }
            // иногда перехватывают караваны: рейд на бандитский дорожный узел
            if (rng.nextDouble() < MILITARY_INTERCEPT_CHANCE) {
                int banditNode = findBanditRoadNode(sim);
                if (banditNode >= 0 && !hasAttackCmd(filtered, banditNode)) {
                    filtered.add(new Command.Attack(banditNode));
                }
            }
            return filtered;
        }

        private static boolean isMilitaryTarget(Simulation sim, int node) {
            for (int n : MILITARY_NO_ATTACK) if (n == node) return false;
            Node target = sim.nodes.get(node);
            if (target.owner >= 0) {
                Faction owner = sim.factions.get(target.owner);
                return owner.raider; // воюют только с бандитами
            }
            return true; // мутанты — можно
        }

        private static int findBanditRoadNode(Simulation sim) {
            for (int n : MILITARY_ROAD_NODES) {
                if (n >= sim.nodes.size()) continue;
                Node node = sim.nodes.get(n);
                if (node.owner >= 0 && sim.factions.get(node.owner).raider) return n;
            }
            return -1;
        }

        private static boolean hasAttackCmd(List<Command> cmds, int node) {
            for (Command c : cmds) if (c instanceof Command.Attack a && a.node() == node) return true;
            return false;
        }
    }

    /** Бандиты Кордона: фокус на элеватор и ферму, живут грабежом. */
    public static final class BanditBrain implements FactionBrain {
        private final UtilityBrain inner = new UtilityBrain();

        @Override
        public List<Command> decide(Simulation sim, Faction f, Random rng) {
            List<Command> cmds = inner.decide(sim, f, rng);
            // цель — элеватор (узел 4) и ферма (узел 10): дополнительный шанс атаки
            if (f.phase == Phase.ACCUMULATE && sim.readiness(f) >= 0.5 && f.stock.money >= f.members * 5L) {
                for (int target : new int[]{4, 10}) {
                    if (target >= sim.nodes.size()) continue;
                    Node n = sim.nodes.get(target);
                    if (n.owner != f.id && sim.minDistFromOwned(f, target) <= 3 && rng.nextDouble() < 0.06) {
                        boolean already = false;
                        for (Command c : cmds) if (c instanceof Command.Attack a && a.node() == target) { already = true; break; }
                        if (!already) cmds.add(new Command.Attack(target));
                    }
                }
            }
            return cmds;
        }
    }

    /** Одиночки Кордона: оборона деревни и фермы, цель — элеватор, доход от артефактов. */
    public static final class LonerBrain implements FactionBrain {
        private final UtilityBrain inner = new UtilityBrain();

        @Override
        public List<Command> decide(Simulation sim, Faction f, Random rng) {
            List<Command> cmds = inner.decide(sim, f, rng);
            // цель — элеватор (узел 4): периодические попытки захвата
            if (f.phase == Phase.ACCUMULATE && sim.readiness(f) >= 0.6 && f.stock.money >= f.members * 5L) {
                Node elev = sim.nodes.get(4);
                if (elev.owner != f.id && sim.minDistFromOwned(f, 4) <= 3 && rng.nextDouble() < 0.04) {
                    boolean already = false;
                    for (Command c : cmds) if (c instanceof Command.Attack a && a.node() == 4) { already = true; break; }
                    if (!already) cmds.add(new Command.Attack(4));
                }
            }
            return cmds;
        }
    }
}