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
            DARK_EXIT = 6, TUNNEL = 7, BRIDGE = 8, HOLE = 9, FARM = 10, FAR_POST = 11, SVALKA_EXIT = 12;

    // v14: узлы логов (карточка 007), координаты примерные — уточнить по меткам игрока
    private static final int HILL = 13, FIELDS = 14, FOREST = 15, RAVINE = 16, MEADOW = 17, EDGE = 18;

    /** Координаты узлов (x, z) в единицах ×10 блоков: center / 10 из data/stalkerzone/kordon_points.json. */
    public static final int[][] COORDS = {
            {0, 0}, {-4, -1}, {6, 22}, {8, 33}, {20, -22}, {33, -12}, {58, -8},
            {13, -26}, {27, -30}, {43, -35}, {21, -42}, {23, -81}, {24, -84},
            {24, -17}, {8, -36}, {26, -51}, {42, -21}, {35, -38}, {28, -76}
    };

    /** Узлы — выходы за карту: логова и цели налётов их исключают. */
    public static final int[] EXITS = {BIG_LAND, DARK_EXIT, SVALKA_EXIT};

    /** Узлы, где могут быть логова (старт и возрождение). Захват фракциями исключён. */
    public static final int[] LAIR_SITES = {HILL, FIELDS, FOREST, RAVINE, MEADOW, EDGE, TUNNEL, HOLE};

    /** Стартовая численность трёх живых группировок — баланс уточняет карточка 005. */
    public static final int LONER_MEMBERS = 30, MILITARY_MEMBERS = 14, BANDIT_MEMBERS = 20;

    /** v16 (карточка 003): опасность проходов при выборе маршрута — туннель и дыра опаснее моста. */
    public static final double PASS_RISK_TUNNEL = 0.14, PASS_RISK_BRIDGE = 0.05, PASS_RISK_HOLE = 0.24;

    private KordonScenario() {}

    public static Simulation create(long seed) {
        List<Node> nodes = new ArrayList<>();

        Node village = node(nodes, "Деревня новичков", NodeType.BASE, 0, 0);
        village.farmCap = 20; village.partsCap = 5; village.artifactCap = 1; village.housing = 40;

        Node bunker = node(nodes, "Бункер Сидоровича", NodeType.BASE, -4, -1);
        bunker.housing = 10;
        ZoneScenario.trader(bunker, 1.08, 0.95, ZoneScenario.caps(50, 15, 120, 15, 3, 40));

        Node southPost = node(nodes, "Южный блокпост", NodeType.BASE, 6, 22);
        southPost.farmCap = 3; southPost.housing = 12; southPost.defense = 26;

        Node bigLand = node(nodes, "Выход: Большая земля", NodeType.OUTPOST, 8, 33);
        bigLand.housing = 2; bigLand.defense = 60;

        Node elevator = node(nodes, "Элеватор", NodeType.RUINS, 20, -22);
        elevator.partsCap = 18; elevator.artifactCap = 1; elevator.housing = 8; elevator.defense = 24;

        Node atp = node(nodes, "АТП", NodeType.BASE, 33, -12);
        atp.farmCap = 6; atp.partsCap = 20; atp.housing = 25; atp.defense = 22;

        Node darkExit = node(nodes, "Выход: Тёмная долина", NodeType.OUTPOST, 58, -8);
        darkExit.housing = 2; darkExit.defense = 60;

        Node tunnel = node(nodes, "Туннель с электрами", NodeType.ANOMALY, 13, -26);
        tunnel.artifactCap = 4; tunnel.defense = 30;

        Node bridge = node(nodes, "Разрушенный мост", NodeType.BASE, 27, -30);
        bridge.housing = 6; bridge.defense = 20;

        Node hole = node(nodes, "Дыра в заборе", NodeType.RUINS, 43, -35);
        hole.artifactCap = 3; hole.housing = 6; hole.defense = 28;

        Node farm = node(nodes, "Свиноферма", NodeType.SETTLEMENT, 21, -42);
        farm.farmCap = 25; farm.housing = 15; farm.defense = 18;

        Node farPost = node(nodes, "Дальний блокпост", NodeType.BASE, 23, -81);
        farPost.housing = 10; farPost.defense = 24;

        Node svalkaExit = node(nodes, "Выход: Свалка", NodeType.OUTPOST, 24, -84);
        svalkaExit.housing = 2; svalkaExit.defense = 60;

        // v14: узлы логов (карточка 007), координаты примерные — уточнить по меткам игрока
        Node hill = node(nodes, "Холм за элеватором", NodeType.RUINS, 24, -17);
        Node fields = node(nodes, "Поля у свинофермы", NodeType.RUINS, 8, -36);
        Node forest = node(nodes, "Лес у дальнего блокпоста", NodeType.RUINS, 26, -51);
        Node ravine = node(nodes, "Овраг у АТП", NodeType.RUINS, 42, -21);
        Node meadow = node(nodes, "Луг у фермы", NodeType.RUINS, 35, -38);
        Node edge = node(nodes, "Опушка у пути на Свалку", NodeType.RUINS, 28, -76);

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
        // v14: дороги логовых узлов — только внутри своей части, проходов через насыпь три
        g.connect(HILL, ELEVATOR, 1, 0.03);
        g.connect(RAVINE, ATP, 1, 0.03);
        g.connect(FIELDS, FARM, 1, 0.03);
        g.connect(FOREST, FAR_POST, 1, 0.03);
        g.connect(MEADOW, FARM, 1, 0.03);
        g.connect(EDGE, FAR_POST, 1, 0.03);
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
        factions.get(ZoneScenario.BANDITS).takesNewcomers = true;
        factions.get(ZoneScenario.BANDITS).idealLoyalty = -0.3;
        factions.get(ZoneScenario.BANDITS).idealGreed = 0.8;
        factions.get(ZoneScenario.BANDITS).eliteShare = 0.05;
        factions.get(ZoneScenario.BANDITS).protoBuyer = true;
        factions.get(ZoneScenario.MILITARY).military = true;
        factions.get(ZoneScenario.TRADERS).passive = true;

        double[][] h = ZoneScenario.hostility();

        village.owner = ZoneScenario.LONERS;
        farm.owner = ZoneScenario.LONERS;
        atp.owner = ZoneScenario.BANDITS;
        farPost.owner = ZoneScenario.BANDITS;
        southPost.owner = ZoneScenario.MILITARY;
        bridge.owner = ZoneScenario.MILITARY;
        bunker.owner = ZoneScenario.TRADERS;

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
            f.stock.add(Good.MATERIALS, m);
            f.stock.money = f.military ? 6000 : 3000;
        }
        // стартовый рынок торговца
        for (Node n : nodes) {
            if (!n.trader) continue;
            for (Good gd : Good.ALL) n.market.qty[gd.ordinal()] = (int) (n.target[gd.ordinal()] * 0.8);
            n.market.money = 20000;
        }

        Simulation sim = new Simulation(seed, nodes, g, factions, h);

        // v14: настройка логов для Кордона
        sim.lairExits = EXITS;
        // EXITS + LAIR_SITES — фракции не захватывают
        int[] nc = new int[EXITS.length + LAIR_SITES.length];
        System.arraycopy(EXITS, 0, nc, 0, EXITS.length);
        System.arraycopy(LAIR_SITES, 0, nc, EXITS.length, LAIR_SITES.length);
        sim.lairNoCapture = nc;
        sim.lairStartDefs = KordonBalance.START_LAIRS;
        sim.lairRandomSpawnDisabled = true;
        sim.lairRaidMin = 22;
        sim.lairRaidWeights = KordonBalance.RAID_TARGET_WEIGHT;
        // v16 (карточка 003): три прохода через насыпь; пошлина берётся только за мост.
        // Опасность: туннель (электры) и дыра (аномалии, радиация, мутанты) — высокая, мост — низкая.
        sim.configureCrossings(new int[]{TUNNEL, BRIDGE, HOLE}, BRIDGE,
                new double[]{PASS_RISK_TUNNEL, PASS_RISK_BRIDGE, PASS_RISK_HOLE});

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
