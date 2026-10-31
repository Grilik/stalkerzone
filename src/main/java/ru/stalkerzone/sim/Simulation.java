package ru.stalkerzone.sim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Симуляция Зоны. Один step() = один игровой день.
 *
 * Экономика замкнутая: товары рождаются только на фермах, руинах, аномалиях и в мастерских,
 * исчезают только при потреблении, потерях и вывозе на Большую землю. Всё остальное — переносы
 * между складами фракций, рынками торговцев и грузом караванов. См. {@link #audit()}.
 */
public final class Simulation {
    public static final int G = Good.ALL.length;
    private static final int[] BUY_ORDER = {
        Good.FOOD.ordinal(), Good.AMMO.ordinal(), Good.MEDS.ordinal(), Good.PARTS.ordinal(), Good.MATERIALS.ordinal(), Good.GEAR.ordinal(),
        Good.PROTO.ordinal(), Good.AMMO_PRO.ordinal(), Good.GEAR_PRO.ordinal()
    };

    public final long seed;
    public int day;
    /** Идентификатор сценария, из которого создан мир; сохраняется вместе с состоянием. */
    public String scenario = "zone";
    public final List<Node> nodes;
    public final ZoneGraph graph;
    public final List<Faction> factions;
    public final double[][] hostility;
    public final List<Caravan> caravans = new ArrayList<>();
    public final Ledger ledger = new Ledger();
    public final long[] initialGoods = new long[G];
    public long initialMoney;
    public final ArrayDeque<String> events = new ArrayDeque<>();

    /** Проект «Выжигатель»: исследования, экспедиции, отключение. */
    public final ScorcherProject scorcher = new ScorcherProject();
    /** Монолит проснулся: Выжигатель отключён, дорога к ЧАЭС открыта. */
    public boolean monolithActive;
    private int nextPostDay;

    /** Логова мутантов. */
    public final List<Building> buildings = new ArrayList<>();
    public final List<Anomaly> anomalies = new ArrayList<>();
    /** Насколько изучены аномалии точки (0..1): разведчики поднимают, выброс сбрасывает. */
    public final double[] knowledge = new double[64];
    public int lastEmissionDay = -1;
    public int anomalyDeaths, artifactsWild;
    /** Сколько прототипов собрали учёные (статистика, не сохраняется). */
    public int protoMade;
    /** Сколько единиц кустарного и прототипов перебросили торговцы друг другу (статистика). */
    public int wholesale;
    /** Запас прототипов у учёных, сверх которого не собирают (излишек уходит торговцам). */
    static final int PROTO_STOCK = 10;
    public final List<Lair> lairs = new ArrayList<>();
    public static final int MAX_LAIRS = 9;
    public int lairsDestroyed, lairsSpawned;

    /** v14: узлы, где логова недопустимы (выходы за карту). */
    public int[] lairExits = {};
    /** v14: узлы, которые фракции не могут захватить (выходы и логовища). */
    public int[] lairNoCapture = {};
    /** v14: сценарий задал фиксированный стартовый набор логов — seedLairs использует его вместо случайного. */
    public KordonBalance.LairDef[] lairStartDefs = null;
    /** v14: сценарий запрещает случайное появление логов (только через respawnQueue). */
    public boolean lairRandomSpawnDisabled = false;
    /** v14: веса целей для налётов (индекс узла → множитель). null = без весов (зона). */
    public double[] lairRaidWeights = null;

    /** v14: минимальная сила логова для налёта. 0 = стандартный порог 30. */
    public double lairRaidMin = 0;

    // --- v16 (карточка 003): три прохода через насыпь Кордона ---------------------------------
    /** Узлы-проходы (туннель, мост, дыра). Пусто — сценарий без проходов, работают обычные дороги. */
    public int[] crossingNodes = {};
    /** Узел-проход, за который берут пошлину (мост). -1 — пошлин нет. */
    public int tollCrossing = -1;
    /** Опасность каждого прохода (параллельно crossingNodes): у туннеля и дыры высокая, у моста низкая. */
    public double[] crossingRisk = {};
    /** Сколько раз каждый узел-проход был пройден (статистика, не сохраняется). */
    public int[] crossingUse = new int[0];
    /** Всего собрано пошлин и по фракциям-плательщикам (статистика, не сохраняется). */
    public long tollCollected;
    public final long[] tollPaidBy = new long[16];
    /** v16 (карточка 005): узел-вход с Большой земли (0 — без шлюза, arrivals используют узел 0). */
    public int gatewayNode = 0;
    /** v16 (карточка 005): узлы-выходы, через которые люди покидают срез. */
    public int[] exitNodes = {};
    /** v14: очередь возрождения логов (срок, район, прежний cap, прежний вид). */
    public final List<LairRespawn> lairRespawnQueue = new ArrayList<>();
    public static final class LairRespawn {
        public int dueDay, district, shift;
        public double prevCap;
        public LairKind prevKind;
        public LairRespawn(int dueDay, int district, double prevCap, LairKind prevKind, int shift) {
            this.dueDay = dueDay; this.district = district; this.prevCap = prevCap;
            this.prevKind = prevKind; this.shift = shift;
        }
    }
    /** v14: сдвиг окна cap по районам. */
    public final int[] lairDistrictShift = new int[3];
    /** v14: день последней зачистки в районе (для сброса сдвига). */
    public final int[] lairDistrictLastClear = new int[3];

    /** Люди: новички, именные сталкеры, раненые, зомби (v1.5–1.6). */
    public final People people = new People();
    /** Власть: встречи, перемирия, раскол (v1.7). */
    public final Politics politics = new Politics();
    /** Контракты (v1.4). */
    public final ContractBoard contracts = new ContractBoard();
    /** Лаборатории и данные для учёных. */
    public final Labs labs;
    /** Глобальный рынок Большой земли: доступ только у торговцев и учёных (v1.11). */
    public final GlobalMarket world = new GlobalMarket();
    /** Склады точек, конвои снабжения, контракт учёных с армией (v1.10). */
    public final Logistics logistics = new Logistics();

    Random rng = new Random();

    public Simulation(long seed, List<Node> nodes, ZoneGraph graph, List<Faction> factions, double[][] hostility) {
        this.seed = seed;
        this.nodes = nodes;
        this.graph = graph;
        this.factions = factions;
        this.hostility = hostility;
        this.labs = new Labs(this);
        for (Faction f : factions) {
            f.initMemory(nodes.size(), factions.size());
            if (f.brain == null) f.brain = new UtilityBrain();
        }
        applyGate();
    }

    // ------------------------------------------------------------------ сюжет: Выжигатель и Монолит

    private Faction byRole(java.util.function.Predicate<Faction> role) {
        for (Faction f : factions) if (role.test(f)) return f;
        return null;
    }

    public Faction scientists() { return byRole(f -> f.scientist); }
    public Faction military() { return byRole(f -> f.military); }
    public Faction monolith() { return byRole(f -> f.fanatic); }
    public Faction mercenaries() { return byRole(f -> f.mercenary); }
    public Faction traders() { for (Faction f : factions) if (f.id == ZoneScenario.TRADERS) return f; return null; }


    /** Дорога от пси-поля к ЧАЭС закрыта, пока Выжигатель работает. */
    private void applyGate() {
        Faction mono = monolith();
        for (Node n : nodes) {
            if (n.type != NodeType.PSI || mono == null) continue;
            // пси-поле Выжигателя перекрывает дорогу от Радара в Припять
            for (Node c : nodes) if (c.type == NodeType.CITY && graph.edgeDays(n.id, c.id) > 0) graph.setClosed(n.id, c.id, !monolithActive);
        }
        // v1.15: второй путь на север (Лиманск — Госпиталь) упирается в ту же завесу у самой станции
        if (mono != null) for (Node c : nodes)
            if (c.name.equals("Госпиталь") && graph.edgeDays(c.id, mono.baseNode) > 0) graph.setClosed(c.id, mono.baseNode, !monolithActive);
        graph.build();
    }

    /** Выжигатель отключён: открыть дорогу и перевести Монолит в активную фазу. */
    public void activateMonolith() {
        if (monolithActive) return;
        monolithActive = true;
        scorcher.disabled = true;
        applyGate();
        Faction m = monolith();
        if (m != null) m.members += 42;
        nextPostDay = day + 2;
        log("МОНОЛИТ ПРОБУДИЛСЯ: дорога к ЧАЭС открыта, начинается поход против группировок и учёных");
    }

    /** Монолит перекрывает дороги к учёным, занимая свободные блокпосты по одному раз в 4 дня. */
    private void monolithPosts() {
        if (!monolithActive || day < nextPostDay) return;
        Faction m = monolith();
        if (m == null) return;
        for (Node n : nodes) {
            if (n.type == NodeType.OUTPOST && n.owner < 0) {
                n.owner = m.id;
                nextPostDay = day + 4;
                log(m.name + " заняли «" + n.name + "»");
                return;
            }
        }
    }

    /** Зафиксировать стартовые запасы как точку отсчёта для аудита. */
    public void finishSetup() {
        logistics.seedDepots(this);
        long[] g = new long[G];
        long m = totals(g);
        System.arraycopy(g, 0, initialGoods, 0, G);
        initialMoney = m;
        seedLairs();
        seedBuildings();
        seedAnomalies();
    }

    // ------------------------------------------------------------------ аномалии

    private boolean hasAnomalies(Node n) {
        return n.type == NodeType.ANOMALY || n.type == NodeType.PSI || n.type == NodeType.RUINS || n.type == NodeType.CITY;
    }

    private AnomalyType pickAnomaly(Node n, Random r) {
        AnomalyType[] pool;
        switch (n.type) {
            case PSI: pool = new AnomalyType[] {AnomalyType.ELECTRA, AnomalyType.MOSQUITO, AnomalyType.GRINDER, AnomalyType.FOG, AnomalyType.ELECTRA, AnomalyType.WEB, AnomalyType.LIFT}; break;
            case RUINS: pool = new AnomalyType[] {AnomalyType.FRY, AnomalyType.FLUFF, AnomalyType.MOSQUITO, AnomalyType.SPRINGBOARD, AnomalyType.RUST, AnomalyType.BRAZIER}; break;
            case CITY: pool = new AnomalyType[] {AnomalyType.ELECTRA, AnomalyType.WHIRLIGIG, AnomalyType.FRY, AnomalyType.SPRINGBOARD, AnomalyType.FOG, AnomalyType.COMET, AnomalyType.RUST}; break;
            default: pool = new AnomalyType[] {AnomalyType.SPRINGBOARD, AnomalyType.WHIRLIGIG, AnomalyType.GRINDER, AnomalyType.MOSQUITO,
                    AnomalyType.FRY, AnomalyType.FLUFF, AnomalyType.ELECTRA, AnomalyType.FOG, AnomalyType.JELLY, AnomalyType.LIFT, AnomalyType.WEB, AnomalyType.COMET};
        }
        return pool[r.nextInt(pool.length)];
    }

    private int anomalyCount(Node n) {
        if (!hasAnomalies(n)) return n.name.equals("ЧАЭС") ? 0 : 2;
        return (int) Math.round(Math.max(2, n.artifactCap * 1.6));
    }

    /** Подобрать место для аномалии вокруг точки, подальше от лагеря и от соседних аномалий. */
    private void placeAnomaly(Anomaly a, Node n, Random r) {
        int cls = campClass(n);
        int rmin = cls == 2 ? 46 : cls == 1 ? 34 : 26;
        int rmax = rmin + 55;
        for (int attempt = 0; attempt < 30; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = rmin + r.nextDouble() * (rmax - rmin);
            int dx = (int) Math.round(Math.cos(ang) * rad), dz = (int) Math.round(Math.sin(ang) * rad);
            boolean ok = true;
            for (Anomaly o : anomalies) {
                if (o != a && o.node == n.id && Math.hypot(o.dx - dx, o.dz - dz) < 14) { ok = false; break; }
            }
            a.dx = dx;
            a.dz = dz;
            if (ok) return;
        }
    }

    private void seedAnomalies() {
        anomalies.clear();
        Random r = new Random(seed * 131L + 7);
        for (Node n : nodes) {
            if (anomalyCount(n) == 0) continue;
            for (int i = 0; i < anomalyCount(n); i++) {
                Anomaly a = new Anomaly(n.id, 0, 0, pickAnomaly(n, r));
                placeAnomaly(a, n, r);
                a.charge = r.nextDouble();
                anomalies.add(a);
            }
            knowledge[n.id] = hasAnomalies(n) ? 0.7 : 0.95;
        }
        for (int i = 0; i < knowledge.length; i++) if (knowledge[i] == 0) knowledge[i] = 1;
    }

    /**
     * Сила аномалии: от 1 (окраины, Кордон) до 3.4 (у самой ЧАЭС). Зависит от расстояния до станции
     * и немного от места, поэтому после выброса поле может стать слабее или сильнее. Множитель урона,
     * радиуса, скорости рождения артефактов и опасности для людей.
     */
    public double anomalyPower(Anomaly a) {
        Node n = nodes.get(a.node);
        Node c = chaes();
        double ax = n.x + a.dx / 10.0, az = n.z + a.dz / 10.0;
        double d = Math.hypot(ax - c.x, az - c.z);
        double closeness = Math.max(0, 1 - d / 330.0);
        int h = (a.node * 73856093) ^ (a.dx * 19349663) ^ (a.dz * 83492791) ^ (a.type.ordinal() * 2654435);
        double jitter = ((h >>> 8) % 1000) / 1000.0 * 0.5 - 0.25;
        return Math.max(1, Math.min(3.4, 1 + 2.4 * closeness + jitter));
    }

    /** 0 слабая, 1 средняя, 2 сильная, 3 смертельная. */
    public int anomalyTier(Anomaly a) {
        double p = anomalyPower(a);
        return p < 1.5 ? 0 : p < 2.1 ? 1 : p < 2.8 ? 2 : 3;
    }

    public static final String[] TIER_RU = {"слабая", "средняя", "сильная", "смертельная"};

    private Node chaesNode;

    private Node chaes() {
        if (chaesNode == null) {
            chaesNode = nodes.get(nodes.size() - 1);
            for (Node n : nodes) if (n.name.equals("ЧАЭС")) chaesNode = n;
        }
        return chaesNode;
    }

    public List<Anomaly> anomaliesAt(int node) {
        List<Anomaly> out = new ArrayList<>();
        for (Anomaly a : anomalies) if (a.node == node) out.add(a);
        return out;
    }

    private double knowledgeAt(int node) {
        return node >= 0 && node < knowledge.length ? knowledge[node] : 1;
    }

    /** После выброса аномалии смещаются и частично меняют вид, а изученность падает: маршруты придётся прокладывать заново. */
    private void relocateAnomalies() {
        boolean[] touched = new boolean[nodes.size()];
        for (Anomaly a : anomalies) {
            if (rng.nextDouble() < 0.75) {
                placeAnomaly(a, nodes.get(a.node), rng);
                if (rng.nextDouble() < 0.35) a.type = pickAnomaly(nodes.get(a.node), rng);
                touched[a.node] = true;
            }
        }
        for (Node n : nodes) {
            if (!touched[n.id]) continue;
            knowledge[n.id] = Math.min(knowledge[n.id], 0.25);
            log("После выброса аномалии у «" + n.name + "» сменили положение: маршруты нужно прокладывать заново");
        }
    }

    /** Ежедневно: заряд аномалий, работа разведчиков, гибель людей в неизученных полях. */
    private void anomaliesDaily() {
        for (Anomaly a : anomalies) {
            a.charge += 0.02 * (0.55 + 0.3 * anomalyPower(a));
            if (a.charge >= 1) {
                a.charge -= 1;
                if (a.pending < 3) a.pending++;
            }
        }
        for (Node n : nodes) {
            if (anomaliesAt(n.id).isEmpty()) continue;
            double k = knowledge[n.id];
            double count = anomalies.stream().filter(a -> a.node == n.id).mapToDouble(this::anomalyPower).sum();
            // разведчики: ближайшая живая фракция, имеющая здесь или рядом территорию
            if (k < 1) {
                Faction scout = null;
                for (Faction f : factions) {
                    if (f.members < 10 || f.fanatic || f.passive) continue;
                    if (n.owner == f.id || minDistFromOwned(f, n.id) <= 1) {
                        if (scout == null || f.members > scout.members) scout = f;
                    }
                }
                if (scout != null) {
                    knowledge[n.id] = Math.min(1, k + 0.07);
                    double guard = 1 + 0.6 * (1 - Arms.anomalyGuard(scout.stock));
                    if (rng.nextDouble() < 0.05 * guard * (1 - k) * Math.min(2.5, count / Math.max(1, anomaliesAt(n.id).size()))) {
                        scout.members = Math.max(1, scout.members - 1);
                        scout.deaths++;
                        anomalyDeaths++;
                        log(scout.name + ": разведчик погиб в аномалии у «" + n.name + "»");
                    }
                }
            }
            // жители владельца изредка попадают в аномалии, особенно пока поле не изучено
            if (n.owner >= 0 && count > 0) {
                Faction f = factions.get(n.owner);
                // заводская броня хуже держит аномалии: у армии и «импортных» гарнизонов потери выше
                double expect = f.members / (double) Math.max(1, ownedCount(f)) * 0.0006 * (1 - k) * count / 7.0
                        * (1 + 0.6 * (1 - Arms.anomalyGuard(f.stock)));
                int dead = Math.min(f.members - 1, randomRound(expect));
                if (dead > 0) {
                    f.members -= dead;
                    f.deaths += dead;
                    anomalyDeaths += dead;
                    log(f.name + ": " + dead + " чел. погибло в аномалиях у «" + n.name + "»");
                }
            }
        }
    }

    /** Игровой слой забирает созревшие артефакты аномалии. Они выходят из Зоны через учёт: создаются и сразу вывозятся, дальше их судьба в руках игрока. */
    /**
     * v1.14: активность мутантов по суткам после выброса (канон ТЧ): сразу после — затишье (они пережидают и
     * разбредаются после гона), к 3–4-м суткам — пик, потом обычный уровень.
     */
    public double mutantActivity() {
        if (lastEmissionDay < 0) return 1.0;
        return switch (day - lastEmissionDay) {
            case 0 -> 0.5;
            case 1 -> 0.7;
            case 2 -> 1.1;
            case 3, 4 -> 1.6;
            case 5 -> 1.25;
            default -> 1.0;
        };
    }

    public int takePendingArtifacts(Anomaly a) { return takePendingArtifacts(a, Integer.MAX_VALUE); }

    /** Забрать из поля в мир не больше max созревших артефактов (остальные ждут). */
    public int takePendingArtifacts(Anomaly a, int max) {
        int n = Math.min(a.pending, max);
        if (n <= 0) return 0;
        a.pending -= n;
        ledger.created[Good.ARTIFACT.ordinal()] += n;
        ledger.exported[Good.ARTIFACT.ordinal()] += n;
        artifactsWild += n;
        return n;
    }

    /** Принудительный выброс (для проверки). */
    public void triggerEmission() {
        emission();
        lastEmissionDay = day;
    }

    // ------------------------------------------------------------------ постройки

    public List<Building> buildingsAt(int node) {
        List<Building> out = new ArrayList<>();
        for (Building b : buildings) if (b.node == node) out.add(b);
        return out;
    }

    private void seedBuildings() {
        buildings.clear();
        for (Node n : nodes) {
            if (n.existingBuildings) continue;   // карточка 009: постройки уже есть на уровне 1:1
            BuildingType[] set;
            if (isAnyBase(n.id) || n.type == NodeType.BASE) {
                // главная база: полный набор
                set = new BuildingType[] {BuildingType.HQ, BuildingType.BARRACKS, BuildingType.WORKSHOP, BuildingType.CLINIC, BuildingType.TOWER};
            } else if (n.type == NodeType.SETTLEMENT || n.type == NodeType.CITY) {
                // посёлок или район города: жильё, небольшая мастерская и вышка
                set = new BuildingType[] {BuildingType.BARRACKS, BuildingType.WORKSHOP, BuildingType.TOWER};
            } else if (n.type == NodeType.OUTPOST) {
                set = new BuildingType[] {BuildingType.TOWER, BuildingType.BARRACKS};
            } else if (n.type == NodeType.RUINS) {
                // руины: только наблюдательный пост
                set = new BuildingType[] {BuildingType.TOWER};
            } else {
                continue;
            }
            Faction owner = n.owner >= 0 ? factions.get(n.owner) : null;
            for (BuildingType t : set) {
                int level = 1;
                double hp = 100;
                if (owner == null) { level = 1; hp = 55; }
                else if (owner.military && (t == BuildingType.HQ || t == BuildingType.TOWER)) level = 2;
                else if (owner.scientist && (t == BuildingType.WORKSHOP || t == BuildingType.HQ)) level = 2;
                buildings.add(new Building(n.id, t, level, hp));
            }
        }
    }

    /** Размер лагеря: 0 аванпост, 1 посёлок, 2 главная база фракции. */
    public int campClass(Node n) {
        if (isAnyBase(n.id) || n.type == NodeType.BASE) return 2;
        if (n.type == NodeType.SETTLEMENT || n.type == NodeType.CITY) return 1;
        return 0;
    }

    private double nodeQuality(int node, BuildingType t) {
        double q = 0;
        for (Building b : buildings) if (b.node == node && b.type == t) q += b.quality();
        return q;
    }

    private double factionQuality(Faction f, BuildingType t) {
        double q = 0;
        for (Building b : buildings) if (b.type == t && nodes.get(b.node).owner == f.id) q += b.quality();
        return q;
    }

    private int hqLevel(Faction f) {
        int l = 0;
        for (Building b : buildings) if (b.type == BuildingType.HQ && nodes.get(b.node).owner == f.id) l = Math.max(l, b.level);
        return l;
    }

    /** Множитель силы гарнизона от вышек и укреплений на точке. */
    public double fortMultiplier(int node) {
        return 1 + 0.12 * nodeQuality(node, BuildingType.TOWER);
    }

    private double workshopEff(Faction f) {
        return 1 + 0.18 * factionQuality(f, BuildingType.WORKSHOP);
    }

    /** Нанести урон постройкам точки. Постройка с нулевым hp теряет уровень и восстанавливается до 35%. */
    private void damageNode(int node, double dmg) {
        for (Building b : buildings) {
            if (b.node != node) continue;
            b.hp -= dmg * (0.7 + 0.6 * rng.nextDouble());
            if (b.hp <= 0) {
                if (b.level > 0) {
                    b.level--;
                    b.hp = 35;
                    log("«" + b.type.ru + "» на «" + nodes.get(node).name + "» разрушена до уровня " + b.level);
                } else {
                    b.hp = 0;
                }
            }
            b.hp = Math.min(100, b.hp);
        }
    }

    // ------------------------------------------------------------------ стройка (как в MineColonies)

    /** Что нужно на подъём постройки до уровня 1, 2, 3: стройматериалы, детали, работа строителей, жалованье бригады в день. */
    public static final int[] UP_MAT = {0, 30, 90, 220};
    public static final int[] UP_PARTS = {0, 12, 40, 100};
    public static final int[] UP_WORK = {0, 6, 14, 28};
    public static final int[] UP_GEAR = {0, 0, 0, 3};
    static final int BUILDER_WAGE = 12;

    /** Склад, из которого берутся материалы на точке: на базе — склад фракции, иначе склад точки. */
    private Stock siteStock(Faction f, int node) {
        return node == f.baseNode ? f.stock : nodes.get(node).depot;
    }

    /** Сколько ещё материалов (или деталей) надо завезти на стройки точки. */
    public int projectNeed(int node, boolean materials) {
        int need = 0;
        for (Building b : buildings) {
            if (b.node != node || !b.building()) continue;
            need += materials ? b.needMat - b.haveMat : b.needParts - b.haveParts;
        }
        return Math.max(0, need);
    }

    /** Бросить стройки точки (точку потеряли): завезённое остаётся на её складе. */
    void dropProjects(int node) {
        for (Building b : buildings) {
            if (b.node != node || !b.building()) continue;
            Node n = nodes.get(node);
            n.depot.add(Good.MATERIALS, b.haveMat);
            n.depot.add(Good.PARTS, b.haveParts);
            log("Стройка «" + b.type.ru + "» на «" + n.name + "» брошена");
            clearProject(b);
        }
    }

    private static void clearProject(Building b) {
        b.target = b.level;
        b.needMat = b.needParts = b.haveMat = b.haveParts = 0;
        b.work = b.workNeed = 0;
        b.builder = -1;
    }

    /** Строители фракции: техники плюс подсобные рабочие, не больше пяти на стройку. */
    public double builders(Faction f) {
        int techs = 0;
        for (Person p : people.of(f.id)) if (p.cls == Person.Cls.TECH && p.injury == 0) techs++;
        return Math.min(5, 1 + techs + f.members / 30.0);
    }

    /** Фракция чинит повреждённое, ведёт стройки и закладывает новые, когда есть деньги на бригаду. */
    private void buildingsDaily(Faction f) {
        if (f.members < 5) { f.matWant = 0; return; }   // иначе устаревшая нужда в материалах живёт до загрузки

        // ремонт: по 10% за шаг; на ремонт идут стройматериалы, а без них детали
        for (Building b : buildings) {
            if (nodes.get(b.node).owner != f.id || b.hp >= 100 || b.level == 0 || b.building()) continue;
            Stock st = siteStock(f, b.node);
            double step = Math.min(10, 100 - b.hp);
            int units = (int) Math.ceil(step / 10.0 * (1 + b.level));
            long money = (long) (step / 10.0 * 15 * b.level);
            Good g = st.get(Good.MATERIALS) >= units ? Good.MATERIALS : Good.PARTS;
            if (st.get(g) < units + (g == Good.PARTS ? f.members : 0) || !f.fanatic && st.money < money) continue;
            ledger.destroyed[g.ordinal()] += st.take(g, units);
            if (!f.fanatic) { st.money -= money; ledger.moneyBurned += money; }
            b.hp += step;
        }
        // стройки: завоз материалов на площадку и работа бригады
        int active = 0;
        f.matWant = 0;
        for (Building b : buildings) {
            if (!b.building()) continue;
            if (nodes.get(b.node).owner != b.builder) { dropProjects(b.node); continue; }
            if (b.builder != f.id) continue;
            active++;
            Stock st = siteStock(f, b.node);
            b.haveMat += st.take(Good.MATERIALS, b.needMat - b.haveMat);
            b.haveParts += st.take(Good.PARTS, b.needParts - b.haveParts);
            if (b.node == f.baseNode) f.matWant += b.needMat - b.haveMat;
            else f.matWant += Math.max(0, b.needMat - b.haveMat - nodes.get(b.node).depot.get(Good.MATERIALS));
            // строить можно настолько, насколько хватает завезённого
            double ready = Math.min(b.needMat == 0 ? 1 : (double) b.haveMat / b.needMat, b.needParts == 0 ? 1 : (double) b.haveParts / b.needParts);
            if (b.work >= b.workNeed * ready - 1e-9 || f.phase == Phase.WAR && b.node == f.warNode) continue;
            double crew = builders(f);
            long wage = Math.round(crew * BUILDER_WAGE);
            if (!f.fanatic) {
                if (st.money < wage) continue;
                st.money -= wage;
                ledger.moneyBurned += wage;
            }
            b.work = Math.min(b.workNeed * ready, b.work + crew);
            if (b.work >= b.workNeed - 1e-9) {
                ledger.destroyed[Good.MATERIALS.ordinal()] += b.haveMat;
                ledger.destroyed[Good.PARTS.ordinal()] += b.haveParts;
                b.level = b.target;
                b.hp = 100;
                log(f.name + ": строители закончили «" + b.type.ru + "» на «" + nodes.get(b.node).name + "», уровень " + b.level
                        + " (" + (day - b.startedDay) + " дн.)");
                clearProject(b);
            }
        }
        if ((day + f.id * 3) % 6 != 0 || f.phase == Phase.WAR || f.passive && !f.scientist || f.fanatic) return;
        if (active >= (f.members > 60 ? 2 : 1)) return;
        Building best = null;
        double bestScore = 0;
        int hq = hqLevel(f);
        for (Building b : buildings) {
            if (nodes.get(b.node).owner != f.id || b.building()) continue;
            int target = b.level + 1;
            if (target > Building.MAX_LEVEL) continue;
            if (b.type != BuildingType.HQ && target > hq + 1) continue;
            if (b.level > 0 && b.hp < 80) continue;
            // закладывают стройку, когда есть на что держать бригаду; материалы докупят и довезут
            long payroll = (long) UP_WORK[target] * BUILDER_WAGE * 2;
            if (f.stock.money < payroll + f.members * 10L) continue;
            if (Arms.gearUnits(f.stock) < UP_GEAR[target]) continue;
            Doctrine d = f.doctrine;
            double w = switch (b.type) {
                case TOWER -> 1 + d.prudence * 2 + d.courage * 0.5;
                case WORKSHOP -> 1 + d.mercantile * 2;
                case BARRACKS -> 1 + d.expansion;
                case CLINIC -> 1 + d.prudence;
                case HQ -> 1.3;
            };
            double score = w / (1 + b.level) * (0.8 + 0.4 * rng.nextDouble());
            if (b.level == 0) score *= 2;
            if (b.node == f.baseNode) score *= 1.3;
            if (score > bestScore) { bestScore = score; best = b; }
        }
        if (best == null) return;
        int target = best.level + 1;
        if (UP_GEAR[target] > 0) Arms.loseGear(this, f.stock, UP_GEAR[target]);
        best.target = target;
        best.builder = f.id;
        best.needMat = UP_MAT[target];
        best.needParts = UP_PARTS[target];
        best.workNeed = UP_WORK[target];
        best.work = 0;
        best.startedDay = day;
        log(f.name + ": заложена стройка «" + best.type.ru + "» на «" + nodes.get(best.node).name + "» до уровня " + target
                + " (нужно " + best.needMat + " стройматериалов и " + best.needParts + " деталей)");
    }

    // ------------------------------------------------------------------ логова мутантов

    // ------------------------------------------------------------------ сделки игрока

    /** Результат сделки игрока: сколько штук и на какую сумму. */
    public record Deal(int qty, long money) {}

    /**
     * Множители цен для текущей сделки игрока (v1.16: скидки по репутации и званию, PlayerCareer).
     * Игровой слой ставит их перед сделкой и сбрасывает после; деньги всё равно идут через кассу и учёт.
     */
    public double playerBuyFactor = 1.0, playerSellFactor = 1.0;

    /** Игрок продаёт торговцу. Цена пересчитывается за каждую штуку, поэтому большой пакет сам сбивает цену. */
    public Deal playerSell(Node n, Good g, int qty) {
        if (!n.trader || qty <= 0) return new Deal(0, 0);
        int done = 0;
        long paid = 0;
        while (done < qty) {
            long p = Math.max(1, Math.round(buyPrice(n, g) * playerSellFactor));
            if (n.market.money < p) break;
            // кустарное и прототипы торговцу некуда сбыть, кроме Зоны: сверх четырёх запасов не берёт
            if (!g.mainland && n.market.get(g) >= Math.max(4, n.target[g.ordinal()] * 4)) break;
            n.market.money -= p;
            n.market.add(g, 1);
            ledger.imported[g.ordinal()]++;
            ledger.moneyBurned += p;
            paid += p;
            done++;
        }
        return new Deal(done, paid);
    }

    /** Игрок покупает у торговца на сумму не больше budget. */
    public Deal playerBuy(Node n, Good g, int qty, long budget) {
        if (!n.trader || qty <= 0) return new Deal(0, 0);
        int done = 0;
        long spent = 0;
        while (done < qty && n.market.get(g) > 0) {
            // скидка не делает перепродажу выгодной: покупка всегда дороже того, что торговец сейчас платит игроку
            long p = Math.max(Math.round(sellPrice(n, g) * playerBuyFactor), Math.round(buyPrice(n, g) * playerSellFactor) + 1);
            if (spent + p > budget) break;
            n.market.take(g, 1);
            n.market.money += p;
            ledger.exported[g.ordinal()]++;
            ledger.moneyMinted += p;
            spent += p;
            done++;
        }
        return new Deal(done, spent);
    }

    /** Покупка целиком или никак: если не хватило склада или денег, сделка откатывается. */
    public Deal playerBuyAll(Node n, Good g, int qty, long budget) {
        Deal d = playerBuy(n, g, qty, budget);
        if (d.qty() == qty) return d;
        n.market.add(g, d.qty());
        n.market.money -= d.money();
        ledger.exported[g.ordinal()] -= d.qty();
        ledger.moneyMinted -= d.money();
        return new Deal(0, 0);
    }

    /** Продажа целиком или никак: если касса не тянет, сделка откатывается. */
    public Deal playerSellAll(Node n, Good g, int qty) {
        Deal d = playerSell(n, g, qty);
        if (d.qty() == qty) return d;
        n.market.take(g, d.qty());
        n.market.money += d.money();
        ledger.imported[g.ordinal()] -= d.qty();
        ledger.moneyBurned -= d.money();
        return new Deal(0, 0);
    }

    /** Игрок платит торговцу за работу (ремонт): деньги приходят в кассу извне. */
    public void playerPay(Node n, long fee) {
        if (fee <= 0) return;
        n.market.money += fee;
        ledger.moneyMinted += fee;
    }

    /** Добыча с убитого NPC фракции: берётся с её склада и касса, поэтому выбывает из Зоны через учёт. Последний элемент: деньги. */
    public int[] npcLoot(int factionId, Random r) {
        int[] out = new int[G + 1];
        if (factionId < 0 || factionId >= factions.size()) return out;
        Faction f = factions.get(factionId);
        // армия и профессиональные группы носят заводское: с тела падает то, чем фракция воюет
        boolean pro = f.military || f.stock.get(Good.AMMO) < 5 && f.stock.get(Good.AMMO_PRO) > 5;
        Good gear = f.stock.get(Good.GEAR) > 0 && !f.military ? Good.GEAR : f.stock.get(Good.GEAR_PRO) > 0 ? Good.GEAR_PRO : Good.PROTO;
        int[][] table = {{Good.FOOD.ordinal(), 1, 2, 70}, {(pro ? Good.AMMO_PRO : Good.AMMO).ordinal(), 5, 15, 80}, {Good.PARTS.ordinal(), 1, 3, 40},
                {Good.MEDS.ordinal(), 1, 1, 30}, {gear.ordinal(), 1, 1, 8}};
        for (int[] row : table) {
            if (r.nextInt(100) >= row[3]) continue;
            int want = row[1] + r.nextInt(row[2] - row[1] + 1);
            int took = f.stock.take(Good.ALL[row[0]], want);
            ledger.exported[row[0]] += took;
            out[row[0]] = took;
        }
        long money = Math.min(f.stock.money, 20 + r.nextInt(60));
        f.stock.money -= money;
        ledger.moneyBurned += money;
        out[G] = (int) money;
        return out;
    }

    /** Погиб игровой NPC фракции: симуляция теряет одного человека. */
    public void npcKilled(int factionId) {
        if (factionId < 0 || factionId >= factions.size()) return;
        Faction f = factions.get(factionId);
        if (f.members > 1) {
            f.members--;
            f.deaths++;
        }
    }

    public Lair lairAt(int node) {
        for (Lair l : lairs) if (l.node == node) return l;
        return null;
    }

    /**
     * Мутант логова погиб в мире (от игрока или сталкера): логово слабеет на его «цену».
     * Возвращает true, если логово этим выбито до конца и уничтожено.
     */
    public boolean mutantKilled(Lair l, double value, boolean byPlayer) {
        if (!lairs.contains(l)) return false;
        l.strength = Math.max(0, l.strength - value);
        if (l.strength >= 4) return false;
        lairs.remove(l);
        lairsDestroyed++;
        onLairDestroyed(l);
        log("Логово «" + l.title() + "» у «" + nodes.get(l.node).name + "» уничтожено" + (byPlayer ? " сталкером-одиночкой (игрок)" : " в стычке"));
        return true;
    }

    /** Находка в выбитом игроком логове: артефакты (в лабораториях больше). Создаются и сразу уходят игроку. */
    public int lairClearReward(Lair l) {
        int art = 1 + (l.lab ? 2 : 0) + (l.kind == LairKind.CHIMERA || l.kind == LairKind.CONTROLLERS || l.kind == LairKind.GIANTS ? 1 : 0);
        ledger.created[Good.ARTIFACT.ordinal()] += art;
        ledger.exported[Good.ARTIFACT.ordinal()] += art;
        return art;
    }

    /** Часть мутанта, срезанная игроком: сырьё «Медикаменты», создаётся и уходит игроку. */
    public void mutantPartLoot(int units) {
        ledger.created[Good.MEDS.ordinal()] += units;
        ledger.exported[Good.MEDS.ordinal()] += units;
    }

    /** Сила мутантов на точке: логово, если оно есть, иначе остаточная угроза. */
    public double nodeDefense(Node n) {
        Lair l = lairAt(n.id);
        return l != null ? l.strength : 4;
    }

    private LairKind pickKind(NodeType t, Random r) {
        switch (t) {
            // v1.14: псевдогиганты на севере, бюреры в руинах и подвалах, полтергейсты в мёртвых домах, тушканы где угодно
            case PSI: LairKind[] p = {LairKind.CONTROLLERS, LairKind.CHIMERA, LairKind.GIANTS}; return p[r.nextInt(3)];
            case ANOMALY: LairKind[] a = {LairKind.BLOODSUCKERS, LairKind.CHIMERA, LairKind.FLESH, LairKind.GIANTS}; return a[r.nextInt(4)];
            case RUINS: LairKind[] u = {LairKind.DOGS, LairKind.BLOODSUCKERS, LairKind.BURERS, LairKind.RODENTS}; return u[r.nextInt(4)];
            case CITY: LairKind[] c = {LairKind.BLOODSUCKERS, LairKind.CONTROLLERS, LairKind.DOGS, LairKind.POLTERGEISTS, LairKind.BURERS}; return c[r.nextInt(5)];
            default: LairKind[] d = {LairKind.DOGS, LairKind.FLESH, LairKind.DOGS, LairKind.FLESH, LairKind.RODENTS}; return d[r.nextInt(5)];
        }
    }

    /** Стартовые логова на нейтральных точках; своя случайность, чтобы не сдвигать остальную симуляцию. */
    private void seedLairs() {
        lairs.clear();
        if (lairStartDefs != null) {
            // фиксированные стартовые логова из сценария
            for (KordonBalance.LairDef d : lairStartDefs) {
                Lair l = new Lair(d.node, d.kind, d.strength, false);
                lairs.add(l);
            }
            return;
        }
        Random r = new Random(seed * 7919L + 17);
        for (Node n : nodes) {
            if (n.owner >= 0 || n.type == NodeType.BASE) continue;
            LairKind kind = pickKind(n.type, r);
            if (n.name.equals("Собачья деревня")) kind = LairKind.DOGS;     // крепость слепых псов у Периметра
            if (n.name.equals("Кордон Лесника") || n.name.startsWith("Хутор")) continue;   // Лесника и Доктора звери не трогают
            Lair l = new Lair(n.id, kind, n.defense, r.nextDouble() < 0.15);
            l.strength = Math.min(l.strength, l.cap());
            lairs.add(l);
        }
    }

    private boolean isAnyBase(int nodeId) {
        for (Faction f : factions) if (f.baseNode == nodeId) return true;
        return false;
    }

    /** Может ли на этом узле быть логово (не выход, не база, не запрещён сценарием). */
    private boolean lairAllowed(int nodeId) {
        if (isAnyBase(nodeId)) return false;
        for (int e : lairExits) if (e == nodeId) return false;
        return true;
    }

    /** Захват запрещён сценарием (выходы за карту, логовища). */
    public boolean noCapture(int nodeId) {
        for (int c : lairNoCapture) if (c == nodeId) return true;
        return false;
    }

    private double lairGarrison(Faction f, Node n) {
        // у учёных своя охрана и защитные системы, иначе беззащитный Янтарь не пережил бы первых набегов
        // полевые базы армии — бетон, мешки и пулемёты; голодный и неоплаченный гарнизон держится хуже
        double field = f.military && n.type == NodeType.OUTPOST ? 2.5 : 1.0;
        double supply = n.id == f.baseNode ? 1.0 : 0.6 + 0.4 * n.supply;
        return garrisonPeople(f, n) * 0.8 * (f.scientist ? 3.0 : 1.0) * field * supply * fortMultiplier(n.id);
    }

    private double garrisonPeople(Faction f, Node n) {
        int owned = Math.max(1, ownedCount(f));
        return f.members * 0.35 / owned * (n.id == f.baseNode ? 2.0 : 1.0);
    }

    private void lairsDaily() {
        Iterator<Lair> it = lairs.iterator();
        List<Lair> born = new ArrayList<>();
        while (it.hasNext()) {
            Lair l = it.next();
            Node n = nodes.get(l.node);
            l.age++;
            if (l.raidCooldown > 0) l.raidCooldown--;
            if (n.owner < 0) {
                l.strength += (l.cap() - l.strength) * 0.012 * l.growth();
                if (l.strength > (lairRaidMin > 0 ? lairRaidMin : 30) && l.raidCooldown == 0) raid(l, born);
                continue;
            }
            Faction f = factions.get(n.owner);
            l.strength += (l.cap() - l.strength) * 0.004 * l.growth();
            if (f.fanatic) continue;
            double people = garrisonPeople(f, n);
            double garrison = lairGarrison(f, n);
            double ratio = l.strength / (l.strength + garrison);
            if (l.strength > garrison * 0.5) {
                applyCasualties(f, Math.min(f.members - 1, randomRound(people * 0.08 * ratio)));
                l.strength -= l.strength * 0.08 * (1 - ratio);
            } else {
                l.strength -= l.strength * 0.05;
            }
            if (l.strength < 4) {
                destroyLair(l, f, n, "гарнизон выжег");
                it.remove();
                continue;
            }
            if (l.strength > garrison * (n.id == f.baseNode ? 3.0 : 2.2)) overrun(l, f, n, born);
        }
        lairs.addAll(born);
        spawnLair();
        processRespawnQueue();
    }

    /** Мутанты нападают на ближайшую территорию фракции. */
    private void raid(Lair l, List<Lair> born) {
        Node target = null;
        double best = -1;
        for (Node t : nodes) {
            if (t.owner < 0 || t.id == l.node) continue;
            // мутанты не трогают Монолит: его бережёт О-Сознание
            if (factions.get(t.owner).fanatic) continue;
            int d = graph.dist(l.node, t.id);
            if (d > 2) continue;
            // v14: выходы за карту недоступны налётам
            boolean exit = false;
            for (int e : lairExits) if (e == t.id) { exit = true; break; }
            if (exit) continue;
            Faction f = factions.get(t.owner);
            double w = (t.id == f.baseNode ? 0.5 : 1.0);
            // v14: сценарий задаёт веса целей (ферма и деревня чаще)
            if (lairRaidWeights != null && t.id < lairRaidWeights.length && lairRaidWeights[t.id] > 0)
                w *= lairRaidWeights[t.id];
            double score = w / (1 + lairGarrison(f, t)) * (1 + rng.nextDouble());
            if (score > best) { best = score; target = t; }
        }
        l.raidCooldown = 12 + rng.nextInt(10);
        if (target == null) return;
        Faction f = factions.get(target.owner);
        double garrison = lairGarrison(f, target);
        double force = l.strength * 0.7;
        double ratio = force / (force + garrison);
        int dead = Math.min(f.members - 1, randomRound(garrisonPeople(f, target) * 0.15 * ratio));
        applyCasualties(f, dead);
        l.strength -= l.strength * 0.1 * (1 - ratio);
        damageNode(target.id, 3 * ratio * 2);
        log("Логово «" + l.title() + "» у «" + nodes.get(l.node).name + "» напало на «" + target.name + "» (" + f.name + "), погибло " + dead);
        if (force > garrison * (target.id == f.baseNode ? 2.6 : 1.8)) overrun(l, f, target, born);
    }

    /** Мутанты берут верх: точка переходит к ним, у базы гибнут люди и горит склад. */
    private void overrun(Lair l, Faction f, Node n, List<Lair> born) {
        damageNode(n.id, n.id == f.baseNode ? 25 : 20);
        if (n.id == f.baseNode) {
            int dead = Math.min(f.members - 1, (int) (f.members * 0.1));
            f.members -= dead;
            f.deaths += dead;
            for (Good g : Good.ALL) {
                int lost = (int) (f.stock.get(g) * 0.15);
                f.stock.take(g, lost);
                ledger.destroyed[g.ordinal()] += lost;
            }
            l.strength *= 0.6;
            l.raidCooldown = 30;
            log("Мутанты прорвались на базу «" + f.name + "» (" + n.name + "): погибло " + dead + ", склад пострадал");
            return;
        }
        n.owner = -1;
        f.nodesLost++;
        l.raidCooldown = 15;
        log("Мутанты захватили «" + n.name + "», " + f.name + " отступают");
        if (lairAt(n.id) == null && born.stream().noneMatch(b -> b.node == n.id)
                    && lairAllowed(n.id)) {
            double child = l.strength * 0.35;
            l.strength -= child * 0.5;
            born.add(new Lair(n.id, pickKind(n.type, rng), child, false));
            lairsSpawned++;
        }
    }

    private void destroyLair(Lair l, Faction winner, Node n, String how) {
        lairsDestroyed++;
        onLairDestroyed(l);
        if (winner != null) {
            int parts = (int) (l.strength * 0.4) + 3;
            int art = (int) (l.strength / 30) + (l.lab ? 2 : 0);
            winner.stock.add(Good.PARTS, parts);
            ledger.created[Good.PARTS.ordinal()] += parts;
            winner.stock.add(Good.ARTIFACT, art);
            ledger.created[Good.ARTIFACT.ordinal()] += art;
            log(winner.name + ": логово «" + l.title() + "» у «" + n.name + "» уничтожено (" + how + "), добыто деталей " + parts + ", артефактов " + art);
        } else {
            log("Логово «" + l.title() + "» у «" + n.name + "» уничтожено");
        }
    }

    /** v14: поставить уничтоженное логово в очередь возрождения, если оно в районе Кордона. */
    private void onLairDestroyed(Lair l) {
        int dist = KordonBalance.districtOf(l.node);
        if (dist < 0) return;
        // обновить день последней зачистки в районе
        lairDistrictLastClear[dist] = day;
        // сдвиг окна увеличивается на SHIFT_STEP, но ограничен сверху
        lairDistrictShift[dist] = Math.min(lairDistrictShift[dist] + KordonBalance.SHIFT_STEP,
                KordonBalance.SHIFT_MAX_CAP - (int) l.kind.cap + 20);
        int dueDay = day + KordonBalance.RESPAWN_MIN
                + (new Random(seed * 987654321L + day * 7919L + l.node).nextInt(
                        KordonBalance.RESPAWN_MAX - KordonBalance.RESPAWN_MIN + 1));
        lairRespawnQueue.add(new LairRespawn(dueDay, dist, l.kind.cap, l.kind, lairDistrictShift[dist]));
    }

    /** Новые логова появляются взамен уничтоженных, предпочтительно на нейтральной земле. */
    private void spawnLair() {
        if (lairRandomSpawnDisabled) return;
        if (lairs.size() >= MAX_LAIRS || rng.nextDouble() >= 0.04) return;
        double total = 0;
        double[] w = new double[nodes.size()];
        for (Node n : nodes) {
            if (isAnyBase(n.id) || Logistics.isFieldBaseSite(n) || lairAt(n.id) != null) continue;
            if (!lairAllowed(n.id)) continue;
            w[n.id] = n.owner < 0 ? 3 : 1;
            total += w[n.id];
        }
        if (total <= 0) return;
        double pick = rng.nextDouble() * total;
        for (Node n : nodes) {
            pick -= w[n.id];
            if (pick > 0 || w[n.id] == 0) continue;
            Lair l = new Lair(n.id, pickKind(n.type, rng), 6, rng.nextDouble() < 0.15);
            lairs.add(l);
            lairsSpawned++;
            log("В районе «" + n.name + "» появилось новое логово: " + l.title());
            return;
        }
    }

    /** v14: проверить очередь возрождения: подошёл срок — создать логово в районе. */
    private void processRespawnQueue() {
        for (int i = lairRespawnQueue.size() - 1; i >= 0; i--) {
            LairRespawn r = lairRespawnQueue.get(i);
            if (r.dueDay > day) continue;
            // сбросить сдвиг района, если зачисток не было SHIFT_RESET_DAYS
            if (day - lairDistrictLastClear[r.district] > KordonBalance.SHIFT_RESET_DAYS)
                lairDistrictShift[r.district] = 0;

            // найти свободное место в районе
            int[] sites = KordonBalance.ALL_DISTRICTS[r.district];
            int freeNode = -1;
            for (int s : sites) {
                if (!lairAllowed(s)) continue;
                if (lairAt(s) != null) continue;
                Node sn = nodes.get(s);
                if (sn.owner >= 0) continue;
                freeNode = s;
                break;
            }
            if (freeNode < 0) { r.dueDay++; continue; } // все занято, подождём

            // выбрать вид из пула с учётом окна cap
            KordonBalance.KindWeight[] pool = KordonBalance.pool(r.district);
            java.util.List<LairKind> kinds = new java.util.ArrayList<>();
            java.util.List<Integer> weights = new java.util.ArrayList<>();
            for (KordonBalance.KindWeight kw : pool) { kinds.add(kw.kind); weights.add(kw.weight); }
            int shift = lairDistrictShift[r.district];
            LairKind picked = KordonBalance.pickKindInWindow(kinds, weights, r.prevCap, shift,
                    new Random(seed * 1000003L + day + freeNode));
            if (picked == null) picked = r.prevKind;

            Node nn = nodes.get(freeNode);
            Lair nl = new Lair(freeNode, picked, 6, false);
            nl.strength = Math.min(nl.strength, nl.cap());
            lairs.add(nl);
            lairsSpawned++;
            log("В районе «" + nn.name + "» возродилось логово: " + nl.title());

            // сдвиг окна для следующих возрождений
            lairDistrictShift[r.district] = Math.min(shift + KordonBalance.SHIFT_STEP,
                    KordonBalance.SHIFT_MAX_CAP - (int) picked.cap + 20);
            lairRespawnQueue.remove(i);
        }
    }

    /** v14: пересчитать опасность рёбер-проходов по силе ближайших логов. */
    public void recalcPassDanger() {
        if (!scenario.equals("kordon")) return;
        for (int i = 0; i < KordonBalance.PASS_EDGES.length; i++) {
            int a = KordonBalance.PASS_EDGES[i][0], b = KordonBalance.PASS_EDGES[i][1];
            double base = KordonBalance.PASS_BASE_DANGER[i];
            if (base <= 0) continue;
            int passNode = (KordonBalance.districtOf(a) >= 0 ? a : b);
            double bestDist = 99, bestStr = 0, bestCap = 45;
            for (Lair l : lairs) {
                int d = graph.dist(passNode, l.node);
                if (d < bestDist || (d == bestDist && l.strength > bestStr)) {
                    bestDist = d; bestStr = l.strength; bestCap = l.kind.cap;
                }
            }
            double factor = KordonBalance.passDangerFactor(bestStr, bestCap, (int)bestDist);
            graph.setDanger(a, b, base * factor);
        }
    }

    // ------------------------------------------------------------------ проходы через насыпь (карточка 003)

    /** v16 (карточка 003): задать узлы-проходы, узел с пошлиной (мост) и опасность каждого прохода. */
    public void configureCrossings(int[] passNodes, int tollNode, double[] risk) {
        this.crossingNodes = passNodes == null ? new int[0] : passNodes;
        this.tollCrossing = tollNode;
        this.crossingRisk = risk == null ? new double[0] : risk;
        this.crossingUse = new int[nodes.size()];
    }

    /** Опасность прохода по его узлу (0 — если узел не проход). */
    public double crossingRiskOf(int node) {
        for (int i = 0; i < crossingNodes.length; i++)
            if (crossingNodes[i] == node) return i < crossingRisk.length ? crossingRisk[i] : 0;
        return 0;
    }

    /** Точки лежат по разные стороны насыпи: путь между ними проходит через один из проходов. */
    public boolean acrossCrossing(int a, int b) {
        return crossingNodes.length > 0 && crosses(graph.path(a, b));
    }

    /** Является ли узел проходом через насыпь. */
    public boolean isCrossing(int node) {
        for (int p : crossingNodes) if (p == node) return true;
        return false;
    }

    /** Проход закрыт для фракции: владелец прохода — её враг по матрице вражды. */
    public boolean crossingBlocked(Faction f, int pass) {
        if (pass < 0 || pass >= nodes.size()) return false;
        int owner = nodes.get(pass).owner;
        if (owner < 0 || owner == f.id) return false;
        return hostilityTo(f, owner) >= Crossings.ENEMY;
    }

    /**
     * Маршрут с учётом трёх проходов (карточка 003). Врагов владельца прохода не пускает;
     * проход выбирается по цене пути (время + опасность + пошлина) с учётом денег и смелости фракции.
     * Без настроенных проходов — обычный кратчайший путь.
     */
    public int[] route(Faction f, int from, int to, Random r) {
        if (crossingNodes.length == 0 || from == to) return graph.path(from, to);
        int[] base = graph.path(from, to);
        if (!crosses(base)) return base;

        int n = graph.count();
        boolean[] passMask = new boolean[n];
        for (int p : crossingNodes) if (p >= 0 && p < n) passMask[p] = true;

        int k = 0;
        int[][] routes = new int[crossingNodes.length][];
        double[] costs = new double[crossingNodes.length];
        for (int p : crossingNodes) {
            if (p < 0 || p >= n || crossingBlocked(f, p)) continue;
            int[] via = routeVia(f, from, to, p, passMask);
            if (via == null) continue;
            routes[k] = via;
            costs[k] = routeCost(f, via);
            k++;
        }
        if (k == 0) return base;

        double min = costs[0];
        for (int i = 1; i < k; i++) min = Math.min(min, costs[i]);
        double total = 0;
        double[] w = new double[k];
        for (int i = 0; i < k; i++) { w[i] = Math.exp(-(costs[i] - min) / Crossings.PASS_SPREAD); total += w[i]; }
        double v = r.nextDouble() * total;
        for (int i = 0; i < k; i++) { v -= w[i]; if (v <= 0) return routes[i]; }
        return routes[k - 1];
    }

    private boolean crosses(int[] path) {
        for (int node : path) if (isCrossing(node)) return true;
        return false;
    }

    /** Маршрут через конкретный проход: до входа с той же стороны, сам проход, от выхода до цели. */
    private int[] routeVia(Faction f, int from, int to, int pass, boolean[] passMask) {
        int n = graph.count();
        int a = -1, b = -1;
        for (int v = 0; v < n; v++)
            if (graph.open(pass, v)) { if (a < 0) a = v; else b = v; }
        if (a < 0 || b < 0) return null;
        boolean[] reach = reachAvoiding(from, passMask);
        int entry, exit;
        if (reach[a] && !reach[b]) { entry = a; exit = b; }
        else if (reach[b] && !reach[a]) { entry = b; exit = a; }
        else return null;
        int[] head = pathAvoiding(from, entry, passMask);
        int[] tail = pathAvoiding(exit, to, passMask);
        if (head == null || tail == null) return null;
        int[] r = new int[head.length + 1 + tail.length];
        System.arraycopy(head, 0, r, 0, head.length);
        r[head.length] = pass;
        System.arraycopy(tail, 0, r, head.length + 1, tail.length);
        return r;
    }

    /** Какие узлы достижимы из from, не проходя через узлы-проходы. */
    private boolean[] reachAvoiding(int from, boolean[] passMask) {
        int n = graph.count();
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        seen[from] = true;
        q.add(from);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v = 0; v < n; v++) {
                if (seen[v] || passMask[v] || !graph.open(u, v)) continue;
                seen[v] = true;
                q.add(v);
            }
        }
        return seen;
    }

    /** Кратчайший по дням путь из from в to, не заходя в узлы-проходы. null — пути нет. */
    private int[] pathAvoiding(int from, int to, boolean[] passMask) {
        if (from == to) return new int[] {from};
        int n = graph.count();
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] done = new boolean[n];
        java.util.Arrays.fill(dist, Double.MAX_VALUE);
        java.util.Arrays.fill(prev, -1);
        dist[from] = 0;
        for (int it = 0; it < n; it++) {
            int u = -1; double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) if (!done[i] && dist[i] < best) { best = dist[i]; u = i; }
            if (u < 0 || u == to) break;
            done[u] = true;
            for (int v = 0; v < n; v++) {
                if (done[v] || passMask[v] || !graph.open(u, v)) continue;
                double nd = dist[u] + graph.edgeDays(u, v);
                if (nd < dist[v]) { dist[v] = nd; prev[v] = u; }
            }
        }
        if (prev[to] < 0) return null;
        java.util.ArrayList<Integer> back = new java.util.ArrayList<>();
        for (int cur = to; cur != -1; cur = prev[cur]) back.add(0, cur);
        int[] out = new int[back.size()];
        for (int i = 0; i < out.length; i++) out[i] = back.get(i);
        return out;
    }

    /** Цена маршрута в днях: время + опасность проходов + пошлина за мост. */
    private double routeCost(Faction f, int[] route) {
        double c = 0;
        for (int i = 0; i + 1 < route.length; i++) {
            int u = route[i], v = route[i + 1];
            c += graph.edgeDays(u, v);
            double danger = 0;
            if (isCrossing(u)) danger += 0.5 * crossingRiskOf(u);
            if (isCrossing(v)) danger += 0.5 * crossingRiskOf(v);
            c += Crossings.DANGER_WEIGHT * danger * (1 - Crossings.COURAGE_DANGER * f.doctrine.courage);
            if (v == tollCrossing && !crossingBlocked(f, v)) {
                int owner = nodes.get(v).owner;
                if (owner >= 0 && owner != f.id)
                    c += Crossings.tollDays(f, Crossings.tollForFaction(this, factions.get(owner), f, 0));
            }
        }
        return c;
    }

    /** Провести сущность через узел: учесть проход и взять пошлину с наличных (или со склада фракции). */
    public void crossPass(Faction f, int node, Stock cash) {
        if (!isCrossing(node)) return;
        if (node >= 0 && node < crossingUse.length) crossingUse[node]++;
        if (node != tollCrossing) return;
        int owner = nodes.get(node).owner;
        if (owner < 0 || owner == f.id) return;
        Faction o = factions.get(owner);
        long toll = Crossings.tollForFaction(this, o, f, 0);
        long paid = cash == null ? 0 : Stock.moveMoney(cash, o.stock, toll);
        if (paid < toll) paid += Stock.moveMoney(f.stock, o.stock, toll - paid);
        if (paid <= 0) return;
        tollCollected += paid;
        if (f.id >= 0 && f.id < tollPaidBy.length) tollPaidBy[f.id] += paid;
        log(f.name + ": пошлина за проход через «" + nodes.get(node).name + "» — " + paid + " руб. «" + o.name + "»");
    }

    /** Пошлина за мост для игрока: rep — репутация у военных, rank — звание. Только данные для игрового слоя. */
    public long tollFor(int rep, int rank) {
        return Crossings.tollFor(rep, rank);
    }

    // ------------------------------------------------------------------ шаг

    public void step() {
        rng = new Random(seed * 1_000_003L + day);
        day++;
        if (day % 25 == 0) {
            emission();
            lastEmissionDay = day;
        }
        world.daily(this, rng);
        anomaliesDaily();
        lairsDaily();
        recalcPassDanger();
        for (Faction f : factions) if (!f.isService()) economy(f);
        for (Faction f : factions) if (!f.isService()) buildingsDaily(f);
        labs.daily(this, rng);
        scorcher.daily(this, rng);
        logistics.daily(this, rng);
        monolithPosts();
        advanceCaravans();
        for (Faction f : factions) if (!f.isService()) think(f);
        for (Node n : nodes) if (n.trader) traderDaily(n);
        if (day % 7 == 5) traderWholesale();
        if (day % 3 == 0) world.scientistsOrder(this, scientists(), rng);
        resolveWars();
        people.daily(this, rng);
        politics.daily(this, rng);
        contracts.daily(this, rng);
        for (Faction f : factions) {
            if (f.isService()) continue;
            phaseUpdate(f);
            recruit(f);
        }
    }

    // ------------------------------------------------------------------ доступ для подсистем

    int randomRoundPublic(double v) { return randomRound(v); }

    public int housingOf(Faction f) { return housing(f); }

    public double knowledgeOf(int node) { return knowledgeAt(node); }

    public double nodeLairPower(int node) { return nodeLairStrength(node); }

    public double qualityOf(Faction f, BuildingType t) { return factionQuality(f, t); }

    public int stock(Faction f, Good g) { return f.stock.get(g); }

    /** Игровой NPC израсходовал товар (патроны на перезарядку): берётся со склада фракции и уничтожается. */
    public int npcUseGood(int factionId, Good g, int n) {
        if (factionId < 0 || factionId >= factions.size()) return 0;
        return takeGood(factions.get(factionId), g, n);
    }

    /**
     * Игровой NPC перезаряжается: одна единица патронов со склада фракции. Профессионалы и армия берут заводские,
     * остальные кустарные; если нужных нет — какие есть. Возвращает товар, который ушёл, или null (склад пуст).
     */
    public Good npcUseAmmo(int factionId, boolean pro) {
        if (factionId < 0 || factionId >= factions.size()) return null;
        Faction f = factions.get(factionId);
        Good first = pro || f.military ? Good.AMMO_PRO : Good.AMMO, second = first == Good.AMMO ? Good.AMMO_PRO : Good.AMMO;
        if (takeGood(f, first, 1) > 0) return first;
        if (takeGood(f, second, 1) > 0) return second;
        return null;
    }

    /** Израсходовать товар со склада фракции (уничтожается через Ledger). */
    int takeGood(Faction f, Good g, int n) {
        int t = f.stock.take(g, n);
        ledger.destroyed[g.ordinal()] += t;
        return t;
    }

    void applyCasualtiesOf(Faction f, int n) { applyCasualties(f, n); }

    /** Для проверки и команды оператора: засчитать победу Монолита (сброс данных лабораторий). */
    public void forceMonolithVictory() { labs.monolithVictory(this); }

    /** Прекратить войну фракции (перемирие). */
    void stopWar(Faction a, String why) {
        if (a.phase != Phase.WAR) return;
        log(a.name + " прекращают штурм «" + nodes.get(a.warNode).name + "»: " + why);
        endWar(a);
    }

    public void step(int days) {
        for (int i = 0; i < days; i++) step();
    }

    // ------------------------------------------------------------------ экономика фракции

    private int ownedSumFarm(Faction f) {
        int s = 0;
        for (Node n : nodes) if (n.owner == f.id) s += n.farmCap;
        return s;
    }

    private int ownedSumParts(Faction f) {
        int s = 0;
        for (Node n : nodes) if (n.owner == f.id) s += n.partsCap;
        return s;
    }

    private int ownedSumArtifacts(Faction f) {
        int s = 0;
        double sum = 0;
        for (Node n : nodes) if (n.owner == f.id) sum += n.artifactCap * (0.4 + 0.6 * knowledgeAt(n.id));
        return (int) Math.round(sum);
    }

    private int housing(Faction f) {
        int s = 0;
        for (Node n : nodes) if (n.owner == f.id) s += n.housing;
        s += (int) Math.round(6 * factionQuality(f, BuildingType.BARRACKS));
        return s;
    }

    private void economy(Faction f) {
        Stock s = f.stock;

        // питание: на базе каждый ест 1 еду в день со склада фракции; гарнизоны точек едят со своих складов (Logistics)
        int need = f.fanatic ? (f.members + 1) / 2 : f.members - Logistics.outside(this, f);
        int eaten = s.take(Good.FOOD, need);
        ledger.destroyed[Good.FOOD.ordinal()] += eaten;
        int hungry = need - eaten;
        if (hungry > 0) {
            int desert = (int) Math.ceil(hungry * 0.08);
            f.members = Math.max(1, f.members - desert);
            if (desert >= 3) log(f.name + ": голод, ушли " + desert + " человек");
        }

        int m = f.members;
        double work = f.phase == Phase.WAR ? 0.4 : 1.0;

        // фермы
        double farmers = m * 0.3 * work;
        int food = (int) Math.round(Math.min(ownedSumFarm(f), farmers * 3.0) * (0.9 + 0.2 * rng.nextDouble()));
        produce(f, Good.FOOD, food, n -> n.farmCap);

        // сбор деталей
        double scav = m * 0.2 * work;
        int parts = (int) Math.round(Math.min(ownedSumParts(f), scav * 1.5) * (0.9 + 0.2 * rng.nextDouble()));
        produce(f, Good.PARTS, parts, n -> n.partsCap);

        // охота за артефактами
        double hunters = m * 0.25 * work;
        double expected = Math.min(ownedSumArtifacts(f), hunters * 0.08);
        int art = (int) expected;
        if (rng.nextDouble() < expected - art) art++;
        produce(f, Good.ARTIFACT, art, n -> n.artifactCap);
        if (rng.nextDouble() < hunters * 0.004) {
            f.members = Math.max(1, f.members - 1);
            f.deaths++;
            Arms.loseGear(this, s, 1);
        }

        // содержание гарнизонов теперь физическое: жалованье выдаётся из сейфов точек (Logistics)

        if (f.fanatic) sacrificeSurplus(f);

        craft(f);
    }

    /**
     * Добыча рождается там, где её добыли: на базе сразу в склад фракции, на остальных точках в склад точки
     * (оттуда её конвоем увозят на базу). Монолит складов точек не держит.
     */
    private void produce(Faction f, Good g, int total, java.util.function.ToIntFunction<Node> cap) {
        if (total <= 0) return;
        ledger.created[g.ordinal()] += total;
        if (f.fanatic) { f.stock.add(g, total); return; }
        int sum = 0;
        for (Node n : nodes) if (n.owner == f.id) sum += cap.applyAsInt(n);
        if (sum <= 0) { f.stock.add(g, total); return; }
        int left = total;
        for (Node n : nodes) {
            if (n.owner != f.id || n.id == f.baseNode) continue;
            int q = (int) ((long) total * cap.applyAsInt(n) / sum);
            n.depot.add(g, q);
            left -= q;
        }
        f.stock.add(g, left);
    }

    /** Монолит не копит запасы впрок: излишки уходят в жертву (уничтожаются через Ledger). */
    private void sacrificeSurplus(Faction f) {
        int[][] caps = {
            {Good.FOOD.ordinal(), f.members * 10},
            {Good.PARTS.ordinal(), f.members * 20},
            {Good.ARTIFACT.ordinal(), 30}
        };
        for (int[] c : caps) {
            int extra = f.stock.qty[c[0]] - c[1];
            if (extra > 0) {
                f.stock.qty[c[0]] -= extra;
                ledger.destroyed[c[0]] += extra;
            }
        }
    }

    public int ownedCount(Faction f) {
        int c = 0;
        for (Node n : nodes) if (n.owner == f.id) c++;
        return c;
    }

    /** Выброс: гибнут люди на открытой местности, а слабо охраняемые территории переходят к мутантам. */
    private void emission() {
        log("Выброс над Зоной");
        relocateAnomalies();
        // выброс «заряжает» поля: в каждом созревает свежий артефакт
        for (Anomaly a : anomalies) if (a.pending < 3) a.pending++;
        for (Node n : nodes) damageNode(n.id, 2);
        for (Faction f : factions) {
            if (f.isService() || f.members <= 1) continue; // сервисные и пустые фракции выброс не задевает
            if (f.fanatic && !monolithActive) continue; // спящий Монолит выброс не задевает
            int dead = randomRound(f.members * 0.02);
            dead = Math.min(dead, f.members - 1);
            f.members -= dead;
            f.deaths += dead;
            // часть застигнутых выбросом не умирает, а превращается в зомби
            if (dead > 0 && !f.fanatic) people.addZombies(f.baseNode == 0 ? 1 : f.baseNode, dead * 0.4);
            int owned = ownedCount(f);
            double garrison = f.members * 0.35 / Math.max(1, owned);
            double p = Math.max(0, Math.min(0.5, (14 - garrison) / 14 * 0.5));
            if (p <= 0) continue;
            for (Node n : nodes) {
                if (n.owner != f.id || n.id == f.baseNode) continue;
                if (f.military && n.type == NodeType.OUTPOST) continue;
                if (rng.nextDouble() < p) {
                    n.owner = -1;
                    f.nodesLost++;
                    log(f.name + " не удержали «" + n.name + "»: территорию заняли мутанты");
                    if (lairAt(n.id) == null && !isAnyBase(n.id) && lairAllowed(n.id)) {
                        Lair nl = new Lair(n.id, pickKind(n.type, rng), n.defense * 0.5, false);
                        nl.strength = Math.min(nl.strength, nl.cap());
                        lairs.add(nl);
                        lairsSpawned++;
                    }
                }
            }
        }
    }

    private void craft(Faction f) {
        Stock s = f.stock;
        int m = f.members;
        double eff = workshopEff(f);

        // патроны: 2 детали -> 10 патронов (перезаряженные гильзы, самодельный порох).
        // Армия своих мастерских патронов не держит: только на крайний случай, когда заводские кончились
        boolean armyEmergency = f.military && Arms.ammoValue(s) < m * 10;
        if ((!f.military || armyEmergency) && s.get(Good.AMMO) < f.targetOf(Good.AMMO) * 1.3) {
            int batches = Math.min(Math.max(1, (int) Math.round(m / 8.0 * f.prio[0] * eff)), s.get(Good.PARTS) / 2);
            if (batches > 0) {
                s.take(Good.PARTS, batches * 2);
                ledger.destroyed[Good.PARTS.ordinal()] += batches * 2L;
                s.add(Good.AMMO, batches * 10);
                ledger.created[Good.AMMO.ordinal()] += batches * 10L;
            }
        }
        // снаряжение: 8 деталей -> 1 комплект (оставляем запас деталей под патроны).
        // Это фракционное: костюмы и оружие под задачи своей группировки, собранные в Зоне
        // когда касса пуста, мастерская работает и на продажу: кустарное скупают торговцы
        int gearGoal = (int) Math.ceil(f.targetOf(Good.GEAR) * (s.money < m * 30L ? 1.0 + 0.6 * f.doctrine.mercantile : 1.0));
        if (!f.military && s.get(Good.GEAR) < gearGoal && s.get(Good.PARTS) >= 8 + m * 2) {
            int batches = Math.min(Math.max(1, (int) Math.round(m / 25.0 * f.prio[1] * eff)), (s.get(Good.PARTS) - m * 2) / 8);
            if (batches > 0) {
                s.take(Good.PARTS, batches * 8);
                ledger.destroyed[Good.PARTS.ordinal()] += batches * 8L;
                s.add(Good.GEAR, batches);
                ledger.created[Good.GEAR.ordinal()] += batches;
            }
        }
        // прототипы: техники учёных собирают универсальные комплекты (12 деталей -> 1), в основном на продажу торговцам
        if (f.scientist && s.get(Good.PROTO) < PROTO_STOCK && s.get(Good.PARTS) >= 40 && day % 2 == 0) {
            int batches = Math.max(1, (int) Math.round(m / 16.0 * eff));
            batches = Math.min(batches, Math.min(PROTO_STOCK - s.get(Good.PROTO), (s.get(Good.PARTS) - 28) / 12));
            if (batches > 0) {
                s.take(Good.PARTS, batches * 12);
                ledger.destroyed[Good.PARTS.ordinal()] += batches * 12L;
                s.add(Good.PROTO, batches);
                ledger.created[Good.PROTO.ordinal()] += batches;
                protoMade += batches;
            }
        }
        // медикаменты: 1 артефакт -> 15 аптечек
        if (s.get(Good.MEDS) < f.targetOf(Good.MEDS) && s.get(Good.ARTIFACT) > 0) {
            int batches = Math.min(Math.max(1, (int) Math.round(m / 20.0 * f.prio[2] * eff)), s.get(Good.ARTIFACT));
            s.take(Good.ARTIFACT, batches);
            ledger.destroyed[Good.ARTIFACT.ordinal()] += batches;
            s.add(Good.MEDS, batches * 15);
            ledger.created[Good.MEDS.ordinal()] += batches * 15L;
        }
    }

    private void recruit(Faction f) {
        int cap = housing(f);
        if (f.fanatic) {
            // к Монолиту стекаются паломники: бесплатно, во сне медленно (раз в 20 дней), в походе каждый день
            if (f.members < cap && f.stock.get(Good.FOOD) >= f.members && (monolithActive || day % 20 == 0)) f.members++;
            return;
        }
        if (f.takesNewcomers) return; // приходят только новички через Кордон (People)
        if (f.members >= cap) return;
        if (f.stock.money < 400 || f.stock.get(Good.FOOD) < f.members * 2) return;
        int add = Math.min(2, cap - f.members);
        long cost = 120L * add;
        f.stock.money -= cost;
        ledger.moneyBurned += cost;
        f.members += add;
    }

    // ------------------------------------------------------------------ цены и торговля

    public double midPrice(Node n, Good g) {
        int t = Math.max(n.target[g.ordinal()], 1);
        double ratio = (t + 5.0) / (n.market.get(g) + 5.0);
        // местная цена держится за цену Большой земли: торговцу выгодно то, что дорого «там»
        double anchor = 0.5 * g.base + 0.5 * world.price[g.ordinal()];
        return anchor * Math.max(0.5, Math.min(2.5, Math.pow(ratio, 0.75)));
    }

    /** Цена, по которой торговец покупает у сталкера. */
    public long buyPrice(Node n, Good g) {
        // v1.14: скупщик берёт артефакты дёшево — его маржа 15–20%, за Периметром они дороже
        return Math.max(1, Math.round(midPrice(n, g) * (g == Good.ARTIFACT ? 0.8 : 0.9)));
    }

    /** Цена, по которой торговец продаёт сталкеру. */
    public long sellPrice(Node n, Good g) {
        return Math.max(1, Math.round(midPrice(n, g) * (g == Good.ARTIFACT ? 1.2 : 1.1)));
    }

    /** v1.14: сколько торговцы за всё время отдали «крыше», военным и вывезли за Периметр. */
    public long traderDues, traderBribes, traderExport;

    /**
     * Касса торговца по канону течёт наружу (v1.14): дань группировке, на чьей земле лавка («крыша», охрана — Долг
     * в Баре), взятки военным за проход колонн через Периметр и вывоз прибыли на Большую землю. Первые два — переводы
     * внутри Зоны (деньги приходят группировкам), третье выводит деньги из Зоны через учёт.
     */
    private void traderDaily(Node n) {
        Stock s = n.market;
        long free = s.money - 6000;
        if (free > 0) {
            Faction roof = n.owner >= 0 && n.owner < factions.size() ? factions.get(n.owner) : null;
            if (roof != null && !roof.fanatic) {
                long dues = free * 3 / 200;
                Stock.moveMoney(s, roof.stock, dues);
                traderDues += dues;
            }
            Faction mil = military();
            if (mil != null && mil != roof) {
                long bribe = free / 100;
                Stock.moveMoney(s, mil.stock, bribe);
                traderBribes += bribe;
            }
        }
        if (s.money > 25000) {
            long out = (s.money - 25000) * 6 / 100;
            s.money -= out;
            ledger.moneyBurned += out;
            traderExport += out;
        }
        world.traderDaily(this, n, rng);
    }

    /** Игрок дал взятку на посту военных: деньги приходят в Зону извне. */
    public void playerBribe(Faction mil, long amount) {
        if (mil == null || amount <= 0) return;
        mil.stock.money += amount;
        ledger.moneyMinted += amount;
    }

    /** У игрока конфисковали хабар: товар уходит на склад военных (входит в Зону через учёт). */
    public void playerConfiscated(Faction mil, Good g, int qty) {
        if (mil == null || qty <= 0) return;
        mil.stock.add(g, qty);
        ledger.imported[g.ordinal()] += qty;
    }

    /**
     * Раз в неделю торговцы перебрасывают друг другу кустарное и прототипы (на Большую землю их не сбыть):
     * излишек едет туда, где не хватает, по местной цене. Деньги и товар переходят из кассы в кассу.
     */
    private void traderWholesale() {
        for (Good g : Good.ALL) {
            if (g.mainland) continue;
            int i = g.ordinal();
            Node rich = null, poor = null;
            double hi = 0, lo = Double.MAX_VALUE;
            for (Node n : nodes) {
                if (!n.trader || n.owner < 0) continue;
                double r = n.market.qty[i] / (double) Math.max(1, n.target[i]);
                if (r > hi) { hi = r; rich = n; }
                if (r < lo) { lo = r; poor = n; }
            }
            if (rich == null || poor == null || rich == poor || hi < 1.3 || lo > 0.8) continue;
            if (!graph.reachable(rich.id, poor.id)) continue;
            int q = Math.min(rich.market.qty[i] - rich.target[i], poor.target[i] - poor.market.qty[i]);
            long unit = Math.round((midPrice(rich, g) + midPrice(poor, g)) / 2);
            q = (int) Math.min(q, poor.market.money / 2 / Math.max(1, unit));
            if (q <= 0) continue;
            Stock.move(rich.market, poor.market, g, q);
            Stock.moveMoney(poor.market, rich.market, q * unit);
            wholesale += q;
        }
    }

    public int countCaravans(Faction f) {
        int c = 0;
        for (Caravan cv : caravans) if (cv.owner == f.id) c++;
        return c;
    }

    // ------------------------------------------------------------------ решения командования

    private void think(Faction f) {
        f.decayMemory();
        for (Command c : f.brain.decide(this, f, rng)) apply(f, c);
    }

    /** Исполнение команды. Всё, что нарушает правила или выходит за пределы склада, молча отбрасывается или обрезается. */
    private void apply(Faction f, Command c) {
        if (c instanceof Command.Trade t) {
            executeTrade(f, t);
        } else if (c instanceof Command.Attack a) {
            startWar(f, a.node());
        } else if (c instanceof Command.Stance st) {
            f.fightMonolith = st.fight() && !f.passive && !f.fanatic;
        } else if (c instanceof Command.Production p) {
            f.prio[0] = clampPrio(p.ammo());
            f.prio[1] = clampPrio(p.gear());
            f.prio[2] = clampPrio(p.meds());
        }
    }

    private static double clampPrio(double v) {
        if (Double.isNaN(v)) return 1;
        return Math.max(0, Math.min(2, v));
    }

    private void executeTrade(Faction f, Command.Trade t) {
        if (t.hub() < 0 || t.hub() >= nodes.size() || !nodes.get(t.hub()).trader) return;
        if (graph.dist(f.baseNode, t.hub()) >= 1000) return;
        if (day - f.lastDispatch < 3) return;
        if (countCaravans(f) >= (f.members > 60 ? 2 : 1)) return;
        if (t.sell() == null || t.want() == null || t.sell().length != G || t.want().length != G) return;

        Node hub = nodes.get(t.hub());
        if (hub.owner == ZoneScenario.TRADERS) {
            dispatchCourier(f, t, hub);
            return;
        }

        Stock s = f.stock;
        int guards = Math.max(1, Math.min(Math.min(8, f.members / 2), t.guards()));
        int[] route = route(f, f.baseNode, t.hub(), rng);
        Caravan c = new Caravan(f.id, route, guards);
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            c.sell[i] = Stock.move(s, c.cargo, g, Math.max(0, t.sell()[i]));
            c.want[i] = Math.max(0, Math.min(t.want()[i], 100_000));
        }
        long budget = Math.min(Math.max(0, t.budget()), (long) (s.money * 0.85));
        Stock.moveMoney(s, c.cargo, budget);
        f.lastDispatch = day;

        if (route.length == 1) {
            tradeAtHub(c, nodes.get(t.hub()));
            unload(c, f);
        } else {
            c.leg = 0;
            c.daysLeft = graph.edgeDays(route[0], route[1]);
            caravans.add(c);
        }
    }

    /** Курьер Торговцев: везёт товары от бункера до базы группировки и обратно с деньгами. */
    private void dispatchCourier(Faction buyer, Command.Trade t, Node hub) {
        Faction traders = traders();
        if (traders == null || traders.members < 1) return;
        int[] route = route(traders, hub.id, buyer.baseNode, rng);
        if (route.length < 2) return;
        int guards = Math.min(4, Math.max(1, traders.members / 3));
        Caravan c = new Caravan(traders.id, route, guards);
        c.courierFor = buyer.id;

        // курьер грузит со склада торговца товары, которые хочет группировка
        Stock mk = hub.market;
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            int w = Math.max(0, Math.min(t.want()[i], 100_000));
            c.sell[i] = 0; // курьер не продаёт товары торговца — он их доставляет
            c.want[i] = 0;
            // курьер везёт товар покупателю: берём со склада торговца
            c.cargo.qty[i] = Stock.move(mk, c.cargo, g, Math.min(w, mk.get(g)));
        }
        // курьер везёт деньги торговца, чтобы заплатить за то, что группировка продаёт
        long buyBudget = 0;
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            buyBudget += (long) Math.max(0, t.sell()[i]) * buyPrice(hub, g);
        }
        long courierBudget = Math.min(buyBudget + 200, mk.money / 3);
        Stock.moveMoney(mk, c.cargo, courierBudget);

        buyer.lastDispatch = day;
        c.leg = 0;
        c.daysLeft = graph.edgeDays(route[0], route[1]);
        caravans.add(c);
    }

    private void startWar(Faction f, int nodeId) {
        if (f.phase != Phase.ACCUMULATE || f.passive) return;
        if (f.fanatic && !monolithActive) return;
        if (nodeId < 0 || nodeId >= nodes.size()) return;
        Node n = nodes.get(nodeId);
        if (n.owner == f.id) return;
        if (noCapture(nodeId)) return; // v14: выходы и логовища не захватывают
        // базы не захватываются; только Монолит может разгромить чужую базу
        if (n.owner >= 0 && factions.get(n.owner).baseNode == n.id && !f.fanatic) return;
        if (minDistFromOwned(f, nodeId) > 3) return;
        if (readiness(f) < 0.5 || (!f.fanatic && f.stock.money < f.members * 5L)) return;

        f.phase = Phase.WAR;
        f.phaseDays = 0;
        f.warsStarted++;
        f.warNode = nodeId;
        f.target = n.owner;
        f.siege = 0;
        String who = "мутантов";
        if (n.owner >= 0) {
            factions.get(n.owner).resent(f.id, 0.3);
            who = "«" + factions.get(n.owner).name + "»";
        }
        log(f.name + " начинают наступление на «" + n.name + "» против " + who);
    }

    /** Эффективная враждебность: стартовые отношения плюс накопленная обида. */
    public double hostilityTo(Faction f, int other) {
        double g = f.grudge[other] * f.doctrine.vengeance;
        double h = Math.min(1.0, hostility[f.id][other] + 0.6 * g / (1 + g));
        return politics.truce(f.id, other) ? Math.min(h, 0.1) : h;
    }

    private void tradeAtHub(Caravan c, Node hub) {
        Stock cargo = c.cargo;
        Stock mk = hub.market;

        for (Good g : Good.ALL) {
            int i = g.ordinal();
            int q = Math.min(c.sell[i], cargo.qty[i]);
            // кустарное и прототипы торговец не может вывезти на Большую землю: сверх тройного запаса не берёт
            if (!g.mainland) q = Math.min(q, Math.max(0, hub.target[i] * 3 - mk.qty[i]));
            for (int k = 0; k < q; k++) {
                long price = buyPrice(hub, g);
                if (mk.money < price) break;
                cargo.qty[i]--;
                mk.qty[i]++;
                mk.money -= price;
                cargo.money += price;
            }
            c.sell[i] = 0;
        }
        for (int i : BUY_ORDER) {
            Good g = Good.ALL[i];
            for (int k = 0; k < c.want[i]; k++) {
                if (mk.qty[i] <= 0) break;
                long price = sellPrice(hub, g);
                if (cargo.money < price) break;
                mk.qty[i]--;
                cargo.qty[i]++;
                cargo.money -= price;
                mk.money += price;
            }
            c.want[i] = 0;
        }
    }

    /** Курьер Торговцев торгует на базе группировки: товары идут покупателю, деньги — курьеру (с наценкой). */
    private void courierTrade(Caravan c, Faction buyer, Node hub) {
        Stock cargo = c.cargo;
        Stock bs = buyer.stock;
        double markup = ZoneScenario.tradersMarkup(buyer.id);

        // курьер продаёт товары группировке (с наценкой)
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            int qty = cargo.qty[i];
            if (qty <= 0) continue;
            long unitPrice = Math.round(sellPrice(hub, g) * markup);
            for (int k = 0; k < qty; k++) {
                if (bs.money < unitPrice) break;
                cargo.qty[i]--;
                bs.add(g, 1);
                bs.money -= unitPrice;
                cargo.money += unitPrice;
            }
        }
        // курьер скупает у группировки то, что она продаёт (по обычной цене скупки)
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            int canSell = Math.min(buyer.stock.get(g), bs.get(g));
            int qty = Math.min(canSell, (int) Math.min(100_000, 50 + bs.get(g) / 2));
            long unitPrice = buyPrice(hub, g);
            for (int k = 0; k < qty; k++) {
                if (cargo.money < unitPrice) break;
                if (bs.take(g, 1) < 1) break;
                cargo.qty[i]++;
                cargo.money -= unitPrice;
                bs.money += unitPrice;
            }
        }
    }

    /** Привезено курьерских партий Торговцев: считается и когда курьер дошёл сам, и когда его сопроводили (v16). */
    public int courierDelivered;

    /** Курьер Торговцев дошёл до покупателя: учтена доставленная партия (в т.ч. по контракту сопровождения). */
    public void courierArrived(int buyerId) {
        courierDelivered++;
        String who = buyerId >= 0 && buyerId < factions.size() ? "«" + factions.get(buyerId).name + "»" : "покупателя";
        log("Курьер Торговцев дошёл до " + who);
    }

    /**
     * Захват узла по контракту (v16, карточка 006): исполнитель выбивает прежнего владельца без войны,
     * узел переходит заказчику. Смена владельца — единственное условие закрытия CAPTURE_NODE.
     */
    public void contractCapture(Faction executor, int customer, int node) {
        Node n = nodes.get(node);
        int old = n.owner;
        int to = customer >= 0 ? customer : executor.id;
        n.owner = to;
        factions.get(to).nodesCaptured++;
        n.supply = Math.max(0, n.supply * 0.5);
        if (old >= 0 && old != to) {
            Faction o = factions.get(old);
            o.nodesLost++;
            o.resent(to, 0.6);
            log(executor.name + " по контракту выбили «" + o.name + "» с «" + n.name + "»; узел перешёл к «"
                    + factions.get(to).name + "»");
        } else {
            log(executor.name + " заняли «" + n.name + "» по контракту");
        }
    }

    private void unload(Caravan c, Faction f) {
        for (Good g : Good.ALL) Stock.move(c.cargo, f.stock, g, c.cargo.get(g));
        Stock.moveMoney(c.cargo, f.stock, c.cargo.money);
    }

    // ------------------------------------------------------------------ караваны

    private void advanceCaravans() {
        Iterator<Caravan> it = caravans.iterator();
        while (it.hasNext()) {
            Caravan c = it.next();
            Faction f = factions.get(c.owner);
            int from = c.route[c.leg];
            int to = c.route[c.leg + 1];

            double lairThreat = 1 + Math.min(1.0, 0.01 * (nodeLairStrength(from) + nodeLairStrength(to)));
            double fieldThreat = 1 + 0.4 * ((1 - knowledgeAt(from)) + (1 - knowledgeAt(to)));
            double pHit = graph.danger(from, to) * lairThreat * fieldThreat * (1 - Math.min(0.6, 0.08 * c.guards));
            if (rng.nextDouble() < pHit) mutantAttack(c, f);

            c.daysLeft--;
            if (c.daysLeft > 0) continue;

            c.leg++;
            int at = c.route[c.leg];
            crossPass(f, at, c.cargo);
            banditRaid(c, f, at);

            if (c.leg < c.route.length - 1) {
                c.daysLeft = graph.edgeDays(c.route[c.leg], c.route[c.leg + 1]);
                continue;
            }
            if (!c.returning) {
                if (c.courierFor >= 0) {
                    courierTrade(c, factions.get(c.courierFor), nodes.get(c.route[0]));
                    courierDelivered++; // партия Торговцев дошла до базы покупателя
                } else {
                    tradeAtHub(c, nodes.get(at));
                }
                c.returning = true;
                int[] back = new int[c.route.length];
                for (int i = 0; i < back.length; i++) back[i] = c.route[c.route.length - 1 - i];
                c.route = back;
                c.leg = 0;
                c.daysLeft = graph.edgeDays(c.route[0], c.route[1]);
            } else {
                if (c.courierFor >= 0) {
                    // курьер возвращается в бункер: выгружаем товар и деньги обратно торговцу
                    Node hub = nodes.get(c.route[c.route.length - 1]);
                    for (Good g : Good.ALL) Stock.move(c.cargo, hub.market, g, c.cargo.get(g));
                    Stock.moveMoney(c.cargo, hub.market, c.cargo.money);
                } else {
                    unload(c, f);
                }
                it.remove();
            }
        }
    }

    private double nodeLairStrength(int node) {
        Lair l = lairAt(node);
        return l != null ? l.strength : 0;
    }

    private void mutantAttack(Caravan c, Faction f) {
        double r = 0.2 + 0.4 * rng.nextDouble();
        int lostUnits = 0;
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            int loss = (int) (c.cargo.qty[i] * r);
            c.cargo.qty[i] -= loss;
            ledger.destroyed[i] += loss;
            lostUnits += loss;
            c.sell[i] = Math.min(c.sell[i], c.cargo.qty[i]);
        }
        int gd = Math.min(c.guards, rng.nextInt(c.guards / 2 + 2));
        c.guards -= gd;
        f.members = Math.max(1, f.members - gd);
        f.deaths += gd;
        f.caravansLost++;
        f.hubRisk[c.hub()] += r * 0.6;
        if (lostUnits > 50 || gd > 0) log(f.name + ": караван попал на мутантов (потери: " + lostUnits + " ед., люди: " + gd + ")");
    }

    private void banditRaid(Caravan c, Faction f, int nodeId) {
        Node n = nodes.get(nodeId);
        if (n.owner < 0 || n.owner == f.id) return;
        Faction raider = factions.get(n.owner);
        if (!raider.raider) return;
        double p = 0.3 * (1 - Math.min(0.7, 0.07 * c.guards));
        if (rng.nextDouble() >= p) return;
        int units = 0;
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            units += Stock.move(c.cargo, raider.stock, g, c.cargo.qty[i]);
            c.sell[i] = 0;
        }
        long cash = Stock.moveMoney(c.cargo, raider.stock, (long) (c.cargo.money * 0.7));
        int gd = Math.min(c.guards, rng.nextInt(c.guards / 2 + 2));
        c.guards -= gd;
        f.members = Math.max(1, f.members - gd);
        f.deaths += gd;
        f.caravansLost++;
        f.resent(raider.id, 0.8);
        f.hubRisk[c.hub()] += 0.5;
        log(raider.name + " ограбили караван «" + f.name + "» у «" + n.name + "» (" + units + " ед., " + cash + " руб.)");
    }

    // ------------------------------------------------------------------ фазы и война

    public double readiness(Faction f) {
        int m = Math.max(1, f.members);
        double ammo = Arms.ammoValue(f.stock) / (35.0 * m);
        double gear = f.fanatic ? 1.0 : Arms.gearCombat(f.stock) / (0.5 * m);
        double meds = f.stock.get(Good.MEDS) / (2.0 * m);
        double food = f.stock.get(Good.FOOD) / (3.0 * m);
        return Math.min(Math.min(ammo, gear), Math.min(meds, food));
    }

    private void phaseUpdate(Faction f) {
        f.phaseDays++;
        switch (f.phase) {
            case ACCUMULATE:
                break;
            case RECOVER:
                if (f.phaseDays >= (f.fanatic ? 2 : 6) && readiness(f) >= (f.fanatic ? 0.55 : 0.75)) {
                    f.phase = Phase.ACCUMULATE;
                    f.phaseDays = 0;
                }
                break;
            default:
                break;
        }
    }

    public int minDistFromOwned(Faction f, int node) {
        int best = Integer.MAX_VALUE;
        for (Node n : nodes) if (n.owner == f.id) best = Math.min(best, graph.dist(n.id, node));
        return best;
    }

    private double combatPower(Faction f, double committedFrac) {
        // чем больше территорий, тем тоньше растянуты силы
        double spread = Math.max(0.5, 1 - 0.08 * (ownedCount(f) - 1));
        double committed = Math.max(0, f.members - f.wounded) * committedFrac * spread * people.combatQuality(f);
        double need = committed * 1.2;
        // профессиональные группы (у армии все) стреляют заводскими патронами
        double proFirst = f.military ? 1 : Arms.elite(f) / (double) Math.max(1, f.members);
        int proBefore = f.stock.get(Good.AMMO_PRO);
        double got = Arms.spendAmmo(this, f.stock, need, proFirst);
        double ammoRatio = need > 0 ? Math.min(1, got / need) : 0;
        // заводские патроны: меньше осечек и выше пробитие — бонус по доле, которую они закрыли
        double proFired = (proBefore - f.stock.get(Good.AMMO_PRO)) * Arms.AMMO_PRO_VALUE;
        double ammoQ = 1 + 0.15 * (got > 0 ? Math.min(1, proFired / got) : 0);
        double gearRatio = f.fanatic ? 0.6 : Math.min(1.6, Arms.gearCombat(f.stock) / Math.max(1, f.members));
        return committed * (0.35 + 0.65 * ammoRatio) * ammoQ * (1 + 0.5 * Math.min(1, gearRatio) + 0.25 * Math.max(0, gearRatio - 1));
    }

    int randomRound(double v) {
        int base = (int) v;
        return base + (rng.nextDouble() < v - base ? 1 : 0);
    }

    private void applyCasualties(Faction f, int deaths) {
        deaths = (int) Math.round(deaths * (1 - Math.min(0.4, 0.1 * factionQuality(f, BuildingType.CLINIC))));
        int meds = f.stock.take(Good.MEDS, deaths);
        ledger.destroyed[Good.MEDS.ordinal()] += meds;
        deaths = Math.max(0, deaths - meds / 2);
        // часть потерь не гибнет сразу, а попадает в медпункт ранеными (v1.6)
        int wounded = f.fanatic ? 0 : (int) Math.round(deaths * 0.4);
        wounded = Math.min(wounded, Math.max(0, f.members - 1 - f.wounded));
        f.wounded += wounded;
        deaths -= wounded;
        deaths = Math.min(deaths, f.members - 1);
        f.members -= deaths;
        f.deaths += deaths;
        if (f.members > 0 && deaths > 0 && !f.fanatic) f.stress = Math.min(100, f.stress + 40.0 * deaths / f.members);
        Arms.loseGear(this, f.stock, deaths / 2);
    }

    private void resolveWars() {
        for (Faction a : factions) {
            if (a.phase != Phase.WAR) continue;
            Node n = nodes.get(a.warNode);
            if (n.owner == a.id) {
                endWar(a);
                continue;
            }
            Faction d = n.owner >= 0 ? factions.get(n.owner) : null;

            // кто не хочет драться с Монолитом, отходит с чужой (не базовой) точки без боя
            if (a.fanatic && d != null && !d.fightMonolith && n.id != d.baseNode) {
                int lost = Math.min(d.members - 1, randomRound(d.members * 0.02));
                d.members -= lost;
                d.deaths += lost;
                n.owner = a.id;
                a.nodesCaptured++;
                d.nodesLost++;
                a.nodeFailure[n.id] = 0;
                d.resent(a.id, 1.0);
                log(d.name + " отходят из «" + n.name + "» без боя: Монолит");
                endWar(a);
                continue;
            }

            // кто решил драться и дошёл до соседней с учёными точки, встаёт на их защиту
            List<Faction> helpers = new ArrayList<>();
            if (a.fanatic && d != null && d.scientist) {
                for (Faction g : factions) {
                    if (g.isService()) continue;
                    if (g != a && g != d && g.fightMonolith && minDistFromOwned(g, n.id) <= 1) helpers.add(g);
                }
            }

            if (!helpers.isEmpty() && a.phaseDays <= 1) {
                StringBuilder who = new StringBuilder();
                for (Faction g : helpers) who.append(who.length() > 0 ? ", " : "").append(g.name);
                log(who + " пришли на помощь учёным против Монолита у «" + n.name + "»");
            }

            double powA = combatPower(a, 0.8);
            double powD = d != null ? combatPower(d, 0.7) * 1.1 * fortMultiplier(n.id) * (n.id == d.baseNode ? 1 : 0.7 + 0.3 * n.supply) : nodeDefense(n);
            for (Faction g : helpers) powD += combatPower(g, 0.4);
            powA = Math.max(powA, 0.1);
            powD = Math.max(powD, 0.1);

            double rateA = 0.025 * Math.max(0.3, Math.min(3.0, powD / powA));
            applyCasualties(a, randomRound(a.members * 0.8 * rateA));
            if (d != null) {
                double rateD = 0.025 * Math.max(0.3, Math.min(3.0, powA / powD));
                applyCasualties(d, randomRound(d.members * 0.7 * rateD));
                for (Faction g : helpers) applyCasualties(g, randomRound(g.members * 0.4 * rateD));
            }

            if (d == null) {
                Lair l = lairAt(n.id);
                if (l != null) l.strength = Math.max(1, l.strength * (1 - 0.03 * Math.min(3.0, powA / powD)));
            }

            if (d != null) damageNode(n.id, 2.5 * Math.max(0.3, Math.min(3.0, powA / powD)));

            a.siege += Math.log(powA / powD) * 0.6 + (rng.nextDouble() - 0.5) * 0.3;

            if (a.siege >= 2.5) {
                if (d != null && d.baseNode == n.id) rout(a, d, n);
                else capture(a, d, n);
                endWar(a);
            } else if (a.siege <= -2.5) {
                a.nodeFailure[n.id] += 2;
                log(a.name + " отбиты у «" + n.name + "»");
                endWar(a);
            } else if (Arms.ammoValue(a.stock) < a.members * 5) {
                log(a.name + " прекращают штурм «" + n.name + "»: кончаются патроны");
                a.nodeFailure[n.id] += 1;
                endWar(a);
            } else if (a.members < 15 || a.phaseDays >= 45) {
                log(a.name + " выдохлись у «" + n.name + "»");
                a.nodeFailure[n.id] += 1;
                endWar(a);
            }
        }
    }

    private void capture(Faction a, Faction d, Node n) {
        damageNode(n.id, d != null ? 12 : 0);
        if (a.fanatic) labs.monolithSealed(this, n.id);
        n.owner = a.id;
        a.nodesCaptured++;
        a.nodeFailure[n.id] = 0;
        if (d != null) d.resent(a.id, 1.5);
        long loot = 0;
        if (d != null) {
            d.nodesLost++;
            for (Good g : Good.ALL) {
                int q = (int) (d.stock.get(g) * 0.08);
                loot += Stock.move(d.stock, a.stock, g, q);
            }
            loot += Stock.moveMoney(d.stock, a.stock, (long) (d.stock.money * 0.08));
            log(a.name + " захватили «" + n.name + "» у «" + d.name + "»");
        } else {
            Lair l = lairAt(n.id);
            if (l != null) {
                destroyLair(l, a, n, "штурм");
                lairs.remove(l);
            } else {
                log(a.name + " зачистили «" + n.name + "» от мутантов");
            }
        }
    }

    /** Разгром базы: захватить её нельзя, но люди гибнут, склад горит, у учёных пропадают исследования. */
    private void rout(Faction a, Faction d, Node n) {
        damageNode(n.id, 40);
        double killed = d.scientist ? 0.7 : 0.35;
        double burned = d.scientist ? 0.5 : 0.2;
        int dead = Math.min(d.members - 1, (int) (d.members * killed));
        d.members -= dead;
        d.deaths += dead;
        for (Good g : Good.ALL) {
            int lost = (int) (d.stock.get(g) * burned);
            d.stock.take(g, lost);
            ledger.destroyed[g.ordinal()] += lost;
        }
        Stock.moveMoney(d.stock, a.stock, (long) (d.stock.money * 0.25));
        d.resent(a.id, 3);
        a.nodeFailure[n.id] += 2.5;
        if (d.scientist) {
            scorcher.research = 0;
            scorcher.psiTech = 0;
            if (a.fanatic) labs.monolithVictory(this);
            log(a.name + " разгромили «" + n.name + "»: лаборатории уничтожены, технологии псишлема потеряны (погибло " + dead + ")");
        } else {
            log(a.name + " разгромили базу «" + d.name + "» у «" + n.name + "» (погибло " + dead + "), удержать её не смогли");
        }
    }

    private void endWar(Faction a) {
        a.phase = Phase.RECOVER;
        a.phaseDays = 0;
        a.target = -1;
        a.warNode = -1;
        a.siege = 0;
    }

    // ------------------------------------------------------------------ аудит и отчёты

    /** Сложить всё, что физически лежит в мире. Возвращает деньги, товары кладёт в out. */
    private long totals(long[] out) {
        long money = 0;
        for (Node n : nodes) {
            for (int i = 0; i < G; i++) out[i] += n.market.qty[i];
            money += n.market.money;
        }
        for (Faction f : factions) {
            for (int i = 0; i < G; i++) out[i] += f.stock.qty[i];
            money += f.stock.money;
        }
        for (Caravan c : caravans) {
            for (int i = 0; i < G; i++) out[i] += c.cargo.qty[i];
            money += c.cargo.money;
        }
        for (Node n : nodes) {
            for (int i = 0; i < G; i++) out[i] += n.depot.qty[i];
            money += n.depot.money;
        }
        for (Building b : buildings) {
            out[Good.MATERIALS.ordinal()] += b.haveMat;
            out[Good.PARTS.ordinal()] += b.haveParts;
        }
        money += logistics.totals(out);
        money += contracts.escrowTotal();
        contracts.escrowGoods(out);
        return money;
    }

    /** Проверка закона сохранения. Возвращает пустую строку, если всё сходится, иначе описание расхождения. */
    public String audit() {
        long[] now = new long[G];
        long money = totals(now);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < G; i++) {
            long expected = initialGoods[i] + ledger.created[i] + ledger.imported[i]
                    - ledger.destroyed[i] - ledger.exported[i];
            if (expected != now[i]) {
                sb.append(Good.ALL[i].ru).append(": ожидали ").append(expected).append(", по факту ").append(now[i]).append('\n');
            }
        }
        long expMoney = initialMoney + ledger.moneyMinted - ledger.moneyBurned;
        if (expMoney != money) sb.append("Деньги: ожидали ").append(expMoney).append(", по факту ").append(money).append('\n');
        return sb.toString();
    }

    public long totalMoney() {
        return totals(new long[G]);
    }

    public void log(String msg) {
        events.addLast("[день " + day + "] " + msg);
        while (events.size() > 300) events.removeFirst();
    }

    // ------------------------------------------------------------------ сохранение

    public String save() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(16); // v16: контракты Кордона — сопровождение курьера и захват узла (карточка 006)
            out.writeLong(seed);
            out.writeUTF(scenario);
            out.writeInt(day);
            ledger.write(out);
            for (long v : initialGoods) out.writeLong(v);
            out.writeLong(initialMoney);
            for (Node n : nodes) {
                out.writeInt(n.owner);
                n.market.write(out);
            }
            for (Faction f : factions) {
                out.writeInt(f.members);
                f.stock.write(out);
                out.writeInt(f.phase.ordinal());
                out.writeInt(f.phaseDays);
                out.writeInt(f.target);
                out.writeInt(f.warNode);
                out.writeDouble(f.siege);
                out.writeInt(f.lastDispatch);
                out.writeInt(f.deaths);
                out.writeInt(f.warsStarted);
                out.writeInt(f.nodesCaptured);
                out.writeInt(f.nodesLost);
                out.writeInt(f.caravansLost);
                out.writeInt(f.goal.ordinal());
                out.writeInt(f.goalTarget);
                out.writeInt(f.goalSince);
                for (double v : f.prio) out.writeDouble(v);
                for (double v : f.grudge) out.writeDouble(v);
                for (double v : f.nodeFailure) out.writeDouble(v);
                for (double v : f.hubRisk) out.writeDouble(v);
                out.writeBoolean(f.fightMonolith);
            }
            out.writeBoolean(monolithActive);
            out.writeInt(nextPostDay);
            out.writeDouble(scorcher.research);
            out.writeDouble(scorcher.psiTech);
            out.writeInt(scorcher.attempts);
            out.writeInt(scorcher.lastAttempt);
            out.writeInt(scorcher.bestStage);
            out.writeBoolean(scorcher.disabled);
            out.writeInt(lairsDestroyed);
            out.writeInt(lairsSpawned);
            out.writeInt(lairs.size());
            for (Lair l : lairs) {
                out.writeInt(l.node);
                out.writeInt(l.kind.ordinal());
                out.writeDouble(l.strength);
                out.writeBoolean(l.lab);
                out.writeInt(l.raidCooldown);
                out.writeInt(l.age);
            }
            out.writeInt(buildings.size());
            for (Building b : buildings) {
                out.writeInt(b.node);
                out.writeInt(b.type.ordinal());
                out.writeInt(b.level);
                out.writeDouble(b.hp);
            }
            out.writeInt(lastEmissionDay);
            out.writeInt(anomalyDeaths);
            out.writeInt(artifactsWild);
            for (double k : knowledge) out.writeDouble(k);
            out.writeInt(anomalies.size());
            for (Anomaly a : anomalies) {
                out.writeInt(a.node);
                out.writeInt(a.dx);
                out.writeInt(a.dz);
                out.writeInt(a.type.ordinal());
                out.writeDouble(a.charge);
                out.writeInt(a.pending);
            }
            // v7: люди, власть, контракты, лаборатории
            for (Faction f : factions) {
                out.writeDouble(f.exp);
                out.writeDouble(f.stress);
                out.writeInt(f.wounded);
                out.writeDouble(f.unity);
            }
            people.write(out);
            politics.write(out);
            contracts.write(out);
            labs.write(out);
            out.writeDouble(scorcher.labBonus);
            // v9: склады точек, стройки, логистика
            for (Node n : nodes) {
                n.depot.write(out);
                out.writeDouble(n.supply);
                out.writeDouble(n.separatism);
            }
            for (Building b : buildings) {
                out.writeInt(b.target);
                out.writeInt(b.builder);
                out.writeInt(b.needMat);
                out.writeInt(b.needParts);
                out.writeInt(b.haveMat);
                out.writeInt(b.haveParts);
                out.writeDouble(b.work);
                out.writeDouble(b.workNeed);
                out.writeInt(b.startedDay);
            }
            logistics.write(out);
            world.write(out);
            out.writeInt(caravans.size());
            for (Caravan c : caravans) {
                out.writeInt(c.owner);
                out.writeInt(c.route.length);
                for (int r : c.route) out.writeInt(r);
                out.writeInt(c.leg);
                out.writeInt(c.daysLeft);
                out.writeBoolean(c.returning);
                out.writeInt(c.guards);
                c.cargo.write(out);
                for (int v : c.sell) out.writeInt(v);
                for (int v : c.want) out.writeInt(v);
                out.writeInt(c.courierFor);
            }
            out.writeInt(events.size());
            for (String e : events) out.writeUTF(e);
            // v14: очередь возрождения логов Кордона
            out.writeInt(lairRespawnQueue.size());
            for (LairRespawn r : lairRespawnQueue) {
                out.writeInt(r.dueDay);
                out.writeInt(r.district);
                out.writeDouble(r.prevCap);
                out.writeInt(r.prevKind.ordinal());
                out.writeInt(r.shift);
            }
            for (int i = 0; i < lairDistrictShift.length; i++)
                out.writeInt(lairDistrictShift[i]);
            for (int i = 0; i < lairDistrictLastClear.length; i++)
                out.writeInt(lairDistrictLastClear[i]);
            out.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Старое сохранение (до v11): новых товаров в нём не было, их начальный запас считается нулевым. */
    void migrateGoods() {
        for (int i = Good.readCount; i < G; i++) initialGoods[i] = 0;
    }

    /** Загрузить состояние поверх свежесозданного сценария (статические параметры берутся из кода). */
    public static Simulation load(String data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            int version = in.readInt();
            if (version < 3 || version > 16) throw new IOException("unknown save version " + version);
            long seed = in.readLong();
            // v13: сохранение помнит id сценария; старые сохранения (без id) — это Зона
            String scenario = version >= 13 ? in.readUTF() : "zone";
            // v9–10: полевые базы армии, склады точек, стройматериалы, глобальный рынок; старые сохранения не переносятся
            // v12 (v1.14): новые локации и группировки — старая Зона не переносится, начинается заново
            // v14: новые узлы Кордона — старые сохранения несовместимы
            if (version < 12) return Scenarios.create(scenario, seed);
            if (version < 14 && scenario.equals("kordon")) return Scenarios.create(scenario, seed);
            // v11: кустарное, прототипы и снаряжение с Большой земли (три новых товара в конце списка)
            Good.readCount = version >= 11 ? Good.ALL.length : 7;
            Simulation sim = Scenarios.create(scenario, seed);
            sim.day = in.readInt();
            sim.ledger.read(in);
            for (int i = 0; i < Good.readCount; i++) sim.initialGoods[i] = in.readLong();
            if (Good.readCount < G) sim.migrateGoods();
            sim.initialMoney = in.readLong();
            for (Node n : sim.nodes) {
                n.owner = in.readInt();
                n.market.read(in);
            }
            // v15: в старых сохранениях фракции Торговцев не было — её узлы получают владельца при загрузке
            if (version < 15) {
                for (Node n : sim.nodes) {
                    if (n.owner < 0 && n.trader) {
                        Faction tr = sim.traders();
                        if (tr != null && tr.baseNode == n.id) n.owner = tr.id;
                    }
                }
            }
            // v14: старые сохранения без Торговцев — фракция добавляется при загрузке со свежим состоянием
            int loadFactions = version >= 15 ? sim.factions.size() : sim.factions.size() - 1;
            for (int fi = 0; fi < loadFactions; fi++) {
                Faction f = sim.factions.get(fi);
                f.members = in.readInt();
                f.stock.read(in);
                f.phase = Phase.values()[in.readInt()];
                f.phaseDays = in.readInt();
                f.target = in.readInt();
                f.warNode = in.readInt();
                f.siege = in.readDouble();
                f.lastDispatch = in.readInt();
                f.deaths = in.readInt();
                f.warsStarted = in.readInt();
                f.nodesCaptured = in.readInt();
                f.nodesLost = in.readInt();
                f.caravansLost = in.readInt();
                f.goal = Goal.values()[in.readInt()];
                f.goalTarget = in.readInt();
                f.goalSince = in.readInt();
                for (int i = 0; i < f.prio.length; i++) f.prio[i] = in.readDouble();
                for (int i = 0; i < f.grudge.length; i++) f.grudge[i] = in.readDouble();
                for (int i = 0; i < f.nodeFailure.length; i++) f.nodeFailure[i] = in.readDouble();
                for (int i = 0; i < f.hubRisk.length; i++) f.hubRisk[i] = in.readDouble();
                f.fightMonolith = in.readBoolean();
            }
            sim.monolithActive = in.readBoolean();
            sim.nextPostDay = in.readInt();
            sim.scorcher.research = in.readDouble();
            sim.scorcher.psiTech = in.readDouble();
            sim.scorcher.attempts = in.readInt();
            sim.scorcher.lastAttempt = in.readInt();
            sim.scorcher.bestStage = in.readInt();
            sim.scorcher.disabled = in.readBoolean();
            sim.applyGate();
            if (version >= 4) {
                sim.lairsDestroyed = in.readInt();
                sim.lairsSpawned = in.readInt();
                sim.lairs.clear();
                int ln = in.readInt();
                for (int i = 0; i < ln; i++) {
                    Lair l = new Lair(in.readInt(), LairKind.values()[in.readInt()], in.readDouble(), in.readBoolean());
                    l.raidCooldown = in.readInt();
                    l.age = in.readInt();
                    sim.lairs.add(l);
                }
            }
            if (version >= 5) {
                sim.buildings.clear();
                int bn = in.readInt();
                for (int i = 0; i < bn; i++) {
                    Building b = new Building(in.readInt(), BuildingType.values()[in.readInt()], in.readInt(), 0);
                    b.hp = in.readDouble();
                    sim.buildings.add(b);
                }
            }
            if (version >= 6) {
                sim.lastEmissionDay = in.readInt();
                sim.anomalyDeaths = in.readInt();
                sim.artifactsWild = in.readInt();
                for (int i = 0; i < sim.knowledge.length; i++) sim.knowledge[i] = in.readDouble();
                sim.anomalies.clear();
                int an = in.readInt();
                for (int i = 0; i < an; i++) {
                    Anomaly a = new Anomaly(in.readInt(), in.readInt(), in.readInt(), AnomalyType.values()[in.readInt()]);
                    a.charge = in.readDouble();
                    a.pending = in.readInt();
                    sim.anomalies.add(a);
                }
            }
            if (version >= 7) {
                for (int fi = 0; fi < loadFactions; fi++) {
                    Faction f = sim.factions.get(fi);
                    f.exp = in.readDouble();
                    f.stress = in.readDouble();
                    f.wounded = in.readInt();
                    f.unity = in.readDouble();
                }
                sim.people.read(in);
                sim.politics.read(in);
                sim.contracts.read(in);
                sim.labs.read(in);
                sim.scorcher.labBonus = in.readDouble();
            }
            for (Node n : sim.nodes) {
                n.depot.read(in);
                n.supply = in.readDouble();
                n.separatism = in.readDouble();
            }
            for (Building b : sim.buildings) {
                b.target = in.readInt();
                b.builder = in.readInt();
                b.needMat = in.readInt();
                b.needParts = in.readInt();
                b.haveMat = in.readInt();
                b.haveParts = in.readInt();
                b.work = in.readDouble();
                b.workNeed = in.readDouble();
                b.startedDay = in.readInt();
            }
            sim.logistics.read(in);
            sim.world.read(in);
            int cn = in.readInt();
            for (int k = 0; k < cn; k++) {
                int owner = in.readInt();
                int len = in.readInt();
                int[] route = new int[len];
                for (int i = 0; i < len; i++) route[i] = in.readInt();
                Caravan c = new Caravan(owner, route, 0);
                c.leg = in.readInt();
                c.daysLeft = in.readInt();
                c.returning = in.readBoolean();
                c.guards = in.readInt();
                c.cargo.read(in);
                for (int i = 0; i < Good.readCount; i++) c.sell[i] = in.readInt();
                for (int i = 0; i < Good.readCount; i++) c.want[i] = in.readInt();
                c.courierFor = version >= 15 ? in.readInt() : -1;
                sim.caravans.add(c);
            }
            int en = in.readInt();
            for (int i = 0; i < en; i++) sim.events.addLast(in.readUTF());
            // v14: очередь возрождения логов Кордона
            if (version >= 14) {
                sim.lairRespawnQueue.clear();
                int rn = in.readInt();
                for (int i = 0; i < rn; i++) {
                    LairRespawn r = new LairRespawn(in.readInt(), in.readInt(), in.readDouble(),
                            LairKind.values()[in.readInt()], in.readInt());
                    sim.lairRespawnQueue.add(r);
                }
                for (int i = 0; i < sim.lairDistrictShift.length; i++)
                    sim.lairDistrictShift[i] = in.readInt();
                for (int i = 0; i < sim.lairDistrictLastClear.length; i++)
                    sim.lairDistrictLastClear[i] = in.readInt();
            }
            sim.recalcPassDanger();
            return sim;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить состояние Зоны", e);
        } finally {
            Good.readCount = Good.ALL.length;
        }
    }
}
