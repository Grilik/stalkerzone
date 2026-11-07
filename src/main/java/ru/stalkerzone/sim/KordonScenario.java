package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Сценарий «Кордон 1.0»: играбельный срез из одной локации с тремя группировками — Одиночки, Военные и Бандиты.
 * Остальные фракции присутствуют в списке (те же id и матрица вражды, что и в ZoneScenario), но без узлов и людей.
 *
 * Координаты в единицах ×10 блоков, деревня новичков = (0,0), север = −Z.
 * v1.18 (карточка 004): координаты — центры узлов из kordon_points.json (уровень Anomaly l01_escape) / 10.
 */
public final class KordonScenario {
    // --- индексы узлов: порядок не менять, на номера опираются сохранения. Новые узлы только в конец списка.
    private static final int VILLAGE = 0, BUNKER = 1, SOUTH_POST = 2, BIG_LAND = 3, ELEVATOR = 4, ATP = 5,
            DARK_EXIT = 6, TUNNEL = 7, BRIDGE = 8, HOLE = 9, FAR_POST = 11, SVALKA_EXIT = 12;

    /** Свиноферма — именованный узел-цель налёта Бандитов (карточка 022). */
    public static final int FARM = 10;

    // v14: узлы логов (карточка 007), координаты примерные — уточнить по меткам игрока
    private static final int HILL = 13, FIELDS = 14, FOREST = 15, RAVINE = 16, MEADOW = 17, EDGE = 18;

    // v19 (карточка 044): узлы «Малого Кордона» — лагерь у вагончика (О7), свалка техники (А6/О12),
    // лес у свалки техники (логово Л3). Новые узлы только в конец списка.
    public static final int WAGON = 19, DUMP = 20, DUMP_FOREST = 21;

    // v20 (карточка 059): заставы одиночек у входов туннеля — южный (со стороны деревни) и северный (со стороны свинофермы).
    public static final int TUNNEL_SOUTH = 22, TUNNEL_NORTH = 23;

    /** Карточка 046: малые заставы — 2–4 человека, логово там не заводится. */
    public static final int[] SMALL_OUTPOSTS = {WAGON, DUMP, TUNNEL_SOUTH, TUNNEL_NORTH};

    /** Является ли узел малой заставой (2–4 человека, без логова). */
    public static boolean isSmallOutpost(int id) {
        for (int i : SMALL_OUTPOSTS) if (i == id) return true;
        return false;
    }

    /** Координаты узлов (x, z) в единицах ×10 блоков: center / 10 из data/stalkerzone/kordon_points.json. */
    public static final int[][] COORDS = {
            {0, 0}, {-4, -1}, {6, 22}, {8, 33}, {20, -22}, {33, -12}, {58, -8},
            {13, -26}, {27, -30}, {43, -35}, {21, -42}, {23, -81}, {24, -84},
            {20, 5}, {8, -36}, {26, -51}, {42, -21}, {35, -38}, {28, -76},
            {12, -7}, {-4, -12}, {5, -17},
            {13, -23}, {13, -29}
    };

    /**
     * v20 (карточка 059): таблица «аномальная зона → её заставы». Первый узел строки — сама зона, дальше заставы.
     * У А6 (узел 20) застава О12 на том же узле; у туннеля (узел 7) две заставы — 22 (южный вход) и 23 (северный).
     */
    public static final int[][] ZONE_OUTPOSTS = {{DUMP, DUMP}, {TUNNEL, TUNNEL_SOUTH, TUNNEL_NORTH}};

    /** Узлы — выходы за карту: логова и цели налётов их исключают. */
    public static final int[] EXITS = {BIG_LAND, DARK_EXIT, SVALKA_EXIT};

    /**
     * Узлы, где могут быть логова (старт и возрождение). Захват фракциями исключён.
     * v21 (карточка 060): туннель — аномальная зона с двумя заставами, логов там нет.
     */
    public static final int[] LAIR_SITES = {HILL, FIELDS, FOREST, RAVINE, MEADOW, EDGE, HOLE, DUMP_FOREST};

    /** Стартовая численность трёх живых группировок — баланс уточняет карточка 005. */
    public static final int LONER_MEMBERS = 40, MILITARY_MEMBERS = 24, BANDIT_MEMBERS = 28;

    /** v16 (карточка 003): опасность проходов при выборе маршрута — туннель и дыра опаснее моста. */
    public static final double PASS_RISK_TUNNEL = 0.14, PASS_RISK_BRIDGE = 0.05, PASS_RISK_HOLE = 0.24;

    private KordonScenario() {}

    public static Simulation create(long seed) {
        return create(seed, false);
    }

    /**
     * Карточка 054: {@code war = false} — «Малый Кордон» (военные только гарнизон южного блокпоста, мост ничей);
     * {@code war = true} — полная война трёх группировок (прежнее поведение сценария «kordon»).
     */
    public static Simulation create(long seed, boolean war) {
        List<Node> nodes = new ArrayList<>();

        Node village = node(nodes, "Деревня новичков", NodeType.BASE, 0, 0);
        village.existingBuildings = true;
        village.farmCap = 45; village.partsCap = 10; village.artifactCap = 3; village.housing = 55;
        village.defense = 18;

        Node bunker = node(nodes, "Бункер Сидоровича", NodeType.BASE, -4, -1);
        bunker.existingBuildings = true;
        bunker.housing = 10;
        ZoneScenario.trader(bunker, 1.08, 0.95, ZoneScenario.caps(200, 30, 120, 30, 6, 60));

        Node southPost = node(nodes, "Южный блокпост", NodeType.BASE, 6, 22);
        southPost.existingBuildings = true;
        southPost.farmCap = 10; southPost.housing = 22; southPost.defense = 32;

        Node bigLand = node(nodes, "Выход: Большая земля", NodeType.OUTPOST, 8, 33);
        bigLand.existingBuildings = true;
        bigLand.housing = 2; bigLand.defense = 60;

        Node elevator = node(nodes, "Элеватор", NodeType.RUINS, 20, -22);
        elevator.existingBuildings = true;
        elevator.partsCap = 24; elevator.artifactCap = 3; elevator.housing = 12; elevator.defense = 14;

        Node atp = node(nodes, "АТП", NodeType.BASE, 33, -12);
        atp.existingBuildings = true;
        atp.farmCap = 12; atp.partsCap = 25; atp.housing = 35; atp.defense = 25;

        Node darkExit = node(nodes, "Выход: Тёмная долина", NodeType.OUTPOST, 58, -8);
        darkExit.existingBuildings = true;
        darkExit.housing = 2; darkExit.defense = 60;

        Node tunnel = node(nodes, "Туннель с электрами", NodeType.ANOMALY, 13, -26);
        tunnel.existingBuildings = true;
        tunnel.artifactCap = 4; tunnel.defense = 30;

        Node bridge = node(nodes, "Разрушенный мост", NodeType.BASE, 27, -30);
        bridge.existingBuildings = true;
        bridge.housing = 10; bridge.defense = 28;

        Node hole = node(nodes, "Дыра в заборе", NodeType.RUINS, 43, -35);
        hole.existingBuildings = true;
        hole.artifactCap = 3; hole.housing = 6; hole.defense = 28;
        // v22 (карточка 061): вытянутая территория логова «Дыры» — к старому месту холма, мимо АТП
        // и элеватора; с Лысым холмом не соединяется.
        hole.reach = new int[][]{{433, -295}, {410, -225}, {340, -200}, {270, -172}};
        hole.reachWidth = 34;

        Node farm = node(nodes, "Свиноферма", NodeType.SETTLEMENT, 21, -42);
        farm.existingBuildings = true;
        farm.farmCap = 45; farm.housing = 20; farm.defense = 22;

        Node farPost = node(nodes, "Дальний блокпост", NodeType.BASE, 23, -81);
        farPost.existingBuildings = true;
        farPost.housing = 10; farPost.defense = 24;

        Node svalkaExit = node(nodes, "Выход: Свалка", NodeType.OUTPOST, 24, -84);
        svalkaExit.existingBuildings = true;
        svalkaExit.housing = 2; svalkaExit.defense = 60;

        // v14: узлы логов (карточка 007); v21 (карточка 060): холм переехал к южному блокпосту
        Node hill = node(nodes, "Лысый холм", NodeType.RUINS, 20, 5);
        // v21 (карточка 060): логово «Лысого холма» — между О7, АТП и южным блокпостом
        hill.den = new int[]{200, 45};
        Node fields = node(nodes, "Поля у свинофермы", NodeType.RUINS, 8, -36);
        Node forest = node(nodes, "Лес у дальнего блокпоста", NodeType.RUINS, 26, -51);
        Node ravine = node(nodes, "Овраг у АТП", NodeType.RUINS, 42, -21);
        Node meadow = node(nodes, "Луг у фермы", NodeType.RUINS, 35, -38);
        Node edge = node(nodes, "Опушка у пути на Свалку", NodeType.RUINS, 28, -76);

        // v19 (карточка 044): «Малый Кордон» — лагерь у вагончика (О7), свалка техники (А6/О12), логово Л3
        Node wagon = node(nodes, "Лагерь у вагончика", NodeType.OUTPOST, 12, -7);
        wagon.existingBuildings = true;
        wagon.housing = 8; wagon.defense = 20;

        Node dump = node(nodes, "Свалка техники", NodeType.ANOMALY, -4, -12);
        dump.existingBuildings = true;

        // v21 (карточка 060): «Собачий лес» — лес между деревней, О7, А6/О12 и туннелем; логово псов у (45, −175)
        Node dumpForest = node(nodes, "Собачий лес", NodeType.RUINS, 5, -17);
        dumpForest.den = new int[]{45, -175};

        // v20 (карточка 059): заставы одиночек у входов туннеля с электрами — по одной с каждой стороны насыпи
        Node tunnelSouth = node(nodes, "Застава у южного входа в туннель", NodeType.OUTPOST, 13, -23);
        tunnelSouth.existingBuildings = true;
        tunnelSouth.housing = 6;

        Node tunnelNorth = node(nodes, "Застава у северного входа в туннель", NodeType.OUTPOST, 13, -29);
        tunnelNorth.existingBuildings = true;
        tunnelNorth.housing = 6;

        ZoneGraph g = new ZoneGraph(nodes.size());
        // южная часть
        g.connect(VILLAGE, BUNKER, 1, 0.02);
        g.connect(VILLAGE, SOUTH_POST, 2, 0.03);
        g.connect(SOUTH_POST, BIG_LAND, 1, 0.02);
        g.connect(SOUTH_POST, ELEVATOR, 3, 0.04);
        g.connect(BUNKER, ELEVATOR, 2, 0.04);
        g.connect(ELEVATOR, ATP, 3, 0.04);
        g.connect(ATP, DARK_EXIT, 3, 0.05);
        // северная часть
        g.connect(FARM, FAR_POST, 2, 0.04);
        g.connect(FAR_POST, SVALKA_EXIT, 1, 0.02);
        // три прохода между частями: туннель и дыра опаснее, мост безопаснее
        g.connect(VILLAGE, TUNNEL, 2, 0.06);
        g.connect(TUNNEL, FARM, 2, 0.10);
        g.connect(ELEVATOR, BRIDGE, 2, 0.02);
        g.connect(BRIDGE, FARM, 2, 0.03);
        g.connect(ATP, HOLE, 2, 0.10);
        g.connect(HOLE, FAR_POST, 2, 0.12);
        // v14: дороги логовых узлов — только внутри своей части, проходов через насыпь три;
        // v21 (карточка 060): «Лысый холм» — к О7 и южному блокпосту, к элеватору дороги больше нет
        g.connect(HILL, WAGON, 1, 0.03);
        g.connect(HILL, SOUTH_POST, 2, 0.03);
        g.connect(RAVINE, ATP, 1, 0.03);
        g.connect(FIELDS, FARM, 1, 0.03);
        g.connect(FOREST, FAR_POST, 1, 0.03);
        g.connect(MEADOW, FARM, 1, 0.03);
        g.connect(EDGE, FAR_POST, 1, 0.03);
        // v19 (карточка 044): дороги «Малого Кордона»
        g.connect(VILLAGE, WAGON, 1, 0.02);
        g.connect(VILLAGE, DUMP, 2, 0.04);
        g.connect(DUMP, DUMP_FOREST, 1, 0.03);
        // v21 (карточка 060): «Собачий лес» — на перекрёстке дорог деревни, О7, свалки и южной заставы туннеля
        g.connect(VILLAGE, DUMP_FOREST, 1, 0.02);
        g.connect(WAGON, DUMP_FOREST, 1, 0.02);
        g.connect(DUMP_FOREST, TUNNEL_SOUTH, 1, 0.02);
        // v20 (карточка 059): дороги к заставам у входов туннеля (юг — от деревни, север — к свиноферме)
        g.connect(VILLAGE, TUNNEL_SOUTH, 2, 0.02);
        g.connect(TUNNEL_SOUTH, TUNNEL, 1, 0.02);
        g.connect(TUNNEL, TUNNEL_NORTH, 1, 0.02);
        g.connect(TUNNEL_NORTH, FARM, 2, 0.02);
        g.build();

        // baseNode непустых фракций; пустые фракции получают 0, чтобы их ИИ не падал на разыменовании
        int[] baseNodes = {VILLAGE, 0, 0, ATP, 0, SOUTH_POST, 0, 0, 0, 0, BUNKER};
        int[] members = {LONER_MEMBERS, 0, 0, BANDIT_MEMBERS, 0, MILITARY_MEMBERS, 0, 0, 0, 0, 5};
        List<Faction> factions = ZoneScenario.factions(baseNodes, members);

        // живые фракции сохраняют свой характер из ZoneScenario
        factions.get(ZoneScenario.LONERS).mercenary = true;
        factions.get(ZoneScenario.LONERS).takesNewcomers = true;
        factions.get(ZoneScenario.LONERS).idealLoyalty = 0.0;
        factions.get(ZoneScenario.LONERS).idealGreed = 0.0;
        factions.get(ZoneScenario.LONERS).eliteShare = 0.06;
        factions.get(ZoneScenario.LONERS).protoBuyer = true;
        factions.get(ZoneScenario.LONERS).brain = new KordonBalance.LonerBrain();
        factions.get(ZoneScenario.BANDITS).takesNewcomers = true;
        factions.get(ZoneScenario.BANDITS).idealLoyalty = -0.3;
        factions.get(ZoneScenario.BANDITS).idealGreed = 0.8;
        factions.get(ZoneScenario.BANDITS).eliteShare = 0.05;
        factions.get(ZoneScenario.BANDITS).protoBuyer = true;
        factions.get(ZoneScenario.BANDITS).brain = new KordonBalance.BanditBrain();
        factions.get(ZoneScenario.MILITARY).military = true;
        factions.get(ZoneScenario.MILITARY).takesNewcomers = true;
        // карточка 054: без полной войны военные — только гарнизон южного блокпоста, ИИ не действует
        factions.get(ZoneScenario.MILITARY).garrisonOnly = !war;
        factions.get(ZoneScenario.MILITARY).idealLoyalty = 0.6;
        factions.get(ZoneScenario.MILITARY).idealGreed = -0.2;
        factions.get(ZoneScenario.MILITARY).brain = new KordonBalance.MilitaryBrain();
        factions.get(ZoneScenario.TRADERS).passive = true;

        // доктрины Кордона (уточняют характер для локального среза)
        factions.get(ZoneScenario.LONERS).doctrine = KordonBalance.LONER_DOCTRINE;
        factions.get(ZoneScenario.BANDITS).doctrine = KordonBalance.BANDIT_DOCTRINE;
        factions.get(ZoneScenario.MILITARY).doctrine = KordonBalance.MILITARY_DOCTRINE;

        double[][] h = ZoneScenario.hostility();

        village.owner = ZoneScenario.LONERS;
        farm.owner = ZoneScenario.LONERS;
        atp.owner = ZoneScenario.BANDITS;
        farPost.owner = ZoneScenario.BANDITS;
        southPost.owner = ZoneScenario.MILITARY;
        // карточка 054: мост военные держат только в полной войне, в «Малом Кордоне» он ничей
        if (war) bridge.owner = ZoneScenario.MILITARY;
        bunker.owner = ZoneScenario.TRADERS;
        // v19 (карточка 044): застава О12 на краю свалки с начала, Л3 ничей
        wagon.owner = ZoneScenario.LONERS;
        dump.owner = ZoneScenario.LONERS;
        // v20 (карточка 059): заставы у входов туннеля — одиночек с начала
        tunnelSouth.owner = ZoneScenario.LONERS;
        tunnelNorth.owner = ZoneScenario.LONERS;

        // стартовые склады трёх живых группировок
        for (Faction f : factions) {
            if (f.members == 0) continue;
            int m = f.members;
            f.stock.add(Good.FOOD, m * 5);
            f.stock.add(Good.PARTS, m * 8);
            if (f.military) {
                f.stock.add(Good.AMMO, m * 4);
                f.stock.add(Good.AMMO_PRO, m * 30);
                f.stock.add(Good.GEAR_PRO, m / 2);
            } else {
                f.stock.add(Good.AMMO, m * 30);
                f.stock.add(Good.GEAR, m / 2);
                int elite = Arms.elite(f);
                f.stock.add(Good.AMMO_PRO, elite * 15);
                f.stock.add(Good.GEAR_PRO, elite / 3);
            }
            f.stock.add(Good.MEDS, m * 2);
            f.stock.add(Good.MATERIALS, m * KordonBalance.HOMELAND_MATERIALS_PER_MEMBER);
            f.stock.money = f.raider ? KordonBalance.BANDIT_START_MONEY : f.military ? 12000 : 8000;
        }
        // стартовые рынки торговцев
        for (Node n : nodes) {
            if (!n.trader) continue;
            for (Good gd : Good.ALL) n.market.qty[gd.ordinal()] = (int) (n.target[gd.ordinal()] * 0.8);
        }

        Simulation sim = new Simulation(seed, nodes, g, factions, h);
        sim.scenario = war ? Scenarios.KORDON_WAR : Scenarios.KORDON;

        // v20 (карточка 025): касса торговцев Кордона — минимум на 3 артефакта уровня 2 и 10 частей мутантов
        // по текущим ценам скупки плюс оборотный запас; выручка с Большой земли держит кассу на плаву месяц.
        for (Node n : nodes) {
            if (!n.trader) continue;
            n.market.money = KordonBalance.traderStartCash(sim, n);
            n.cashReserve = KordonBalance.TRADER_CASH_RESERVE;
            n.mainlandRevenue = KordonBalance.TRADER_MAINLAND_INCOME;
        }

        // v16 (карточка 005): шлюз Большой земли и выходы
        sim.gatewayNode = KordonBalance.GATEWAY_NODE;
        sim.exitNodes = KordonBalance.EXIT_NODES;

        // v18 (карточка 010): точка появления игрока и возрождения одиночек — деревня новичков
        sim.spawnNode = VILLAGE;

        // v14: настройка логов для Кордона
        sim.lairExits = EXITS;
        // v22 (карточка 046): на малых заставах логово не заводится
        sim.lairForbidden = SMALL_OUTPOSTS;
        // EXITS + LAIR_SITES — фракции не захватывают
        int[] nc = new int[EXITS.length + LAIR_SITES.length];
        System.arraycopy(EXITS, 0, nc, 0, EXITS.length);
        System.arraycopy(LAIR_SITES, 0, nc, EXITS.length, LAIR_SITES.length);
        sim.lairNoCapture = nc;
        sim.lairStartDefs = KordonBalance.START_LAIRS;
        sim.lairRandomSpawnDisabled = true;
        sim.lairRaidMin = KordonBalance.LAIR_RAID_MIN;
        sim.lairRaidWeights = KordonBalance.RAID_TARGET_WEIGHT;
        // v16 (карточка 003): три прохода через насыпь; пошлина берётся только за мост.
        // Опасность: туннель (электры) и дыра (аномалии, радиация, мутанты) — высокая, мост — низкая.
        sim.configureCrossings(new int[]{TUNNEL, BRIDGE, HOLE}, BRIDGE,
                new double[]{PASS_RISK_TUNNEL, PASS_RISK_BRIDGE, PASS_RISK_HOLE});

        // v19 (карточка 013): районы аномалий Кордона. Состав поля задаёт район: случайность — только место
        // внутри района и число полей. После выброса район остаётся своим по составу.
        // v21 (карточка 029): у каждого района своё поле фиксированного радиуса — после выброса аномалии
        // ходят только внутри него, а не разбредаются по всей точке.
        sim.anomalyDistricts.add(new AnomalyDistrict(TUNNEL, "Туннель с электрами", 6, 9, 3.0, 30, AnomalyType.ELECTRA));
        sim.anomalyDistricts.add(new AnomalyDistrict(HOLE, "Дыра в заборе", 5, 7, 1.0, 30,
                AnomalyType.SPRINGBOARD, AnomalyType.WHIRLIGIG, AnomalyType.MOSQUITO));
        sim.anomalyDistricts.add(new AnomalyDistrict(EDGE, "Трамплины у дороги на Свалку", 2, 4, 1.0, 24,
                AnomalyType.SPRINGBOARD, AnomalyType.MOSQUITO));
        sim.anomalyDistricts.add(new AnomalyDistrict(ELEVATOR, "Жарки у сгоревших домов", 1, 2, 1.0, 18, AnomalyType.FRY));
        sim.anomalyDistricts.add(new AnomalyDistrict(RAVINE, "Туман в овраге", 1, 1, 1.0, 16, AnomalyType.FOG));
        // карточка 048: узел 20 «Свалка техники» (А6) — поле в чаше: «комариные плеши» и трамплины,
        // центр поля смещён в чашу (−35, −120) = узел (−40, −120) + (5, 0).
        AnomalyDistrict dumpDistrict = new AnomalyDistrict(DUMP, "Свалка техники А6", 6, 9, 1.0, 20,
                AnomalyType.MOSQUITO, AnomalyType.SPRINGBOARD);
        dumpDistrict.setCenter(5, 0);
        sim.anomalyDistricts.add(dumpDistrict);
        // v19 (карточка 013): радиоактивные пятна — насыпь с цистернами (мост), техника у дыры, свиноферма;
        // карточка 048: чаша свалки техники (А6) тоже радиоактивна.
        sim.radiationNodes = new int[]{BRIDGE, HOLE, FARM, DUMP};

        // сохранить базовую опасность проходов
        for (int i = 0; i < KordonBalance.PASS_EDGES.length; i++) {
            int a = KordonBalance.PASS_EDGES[i][0];
            int b = KordonBalance.PASS_EDGES[i][1];
            KordonBalance.PASS_BASE_DANGER[i] = g.danger(a, b);
        }

        sim.finishSetup();
        sim.recalcPassDanger();
        return sim;
    }

    private static Node node(List<Node> list, String name, NodeType t, int x, int z) {
        Node n = new Node(list.size(), name, t, x, z);
        list.add(n);
        return n;
    }
}
