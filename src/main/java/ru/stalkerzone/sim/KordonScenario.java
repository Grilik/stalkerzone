package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Сценарий «Кордон 1.0»: играбельный срез из одной локации с тремя группировками — Одиночки, Военные и Бандиты.
 * Остальные фракции присутствуют в списке (те же id и матрица вражды, что и в ZoneScenario), но без узлов и людей.
 *
 * Координаты в единицах ×10 блоков, деревня новичков = (0,0), север = −Z.
 * Координаты ниже примерные — уточнить по меткам игрока (карточки 004, 009).
 */
public final class KordonScenario {
    // --- индексы узлов: порядок не менять, на номера опираются сохранения. Новые узлы только в конец списка.
    private static final int VILLAGE = 0, BUNKER = 1, SOUTH_POST = 2, BIG_LAND = 3, ELEVATOR = 4, ATP = 5,
            DARK_EXIT = 6, TUNNEL = 7, BRIDGE = 8, HOLE = 9, FARM = 10, FAR_POST = 11, SVALKA_EXIT = 12;

    /** Координаты узлов (x, z) в единицах ×10 блоков — уточнить по меткам игрока. */
    public static final int[][] COORDS = {
            {0, 0}, {12, 13}, {27, 20}, {28, 31}, {21, -29}, {61, -25}, {96, -23},
            {0, -48}, {27, -48}, {55, -48}, {1, -70}, {30, -96}, {30, -107}
    };

    /** Стартовая численность трёх живых группировок — баланс уточняет карточка 005. */
    public static final int LONER_MEMBERS = 30, MILITARY_MEMBERS = 14, BANDIT_MEMBERS = 20;

    private KordonScenario() {}

    public static Simulation create(long seed) {
        List<Node> nodes = new ArrayList<>();

        Node village = node(nodes, "Деревня новичков", NodeType.BASE, 0, 0);
        village.farmCap = 20; village.partsCap = 5; village.artifactCap = 1; village.housing = 40;

        Node bunker = node(nodes, "Бункер Сидоровича", NodeType.BASE, 12, 13);
        bunker.housing = 10;
        ZoneScenario.trader(bunker, 1.08, 0.95, ZoneScenario.caps(50, 15, 120, 15, 3, 40));

        Node southPost = node(nodes, "Южный блокпост", NodeType.BASE, 27, 20);
        southPost.farmCap = 3; southPost.housing = 12; southPost.defense = 26;

        Node bigLand = node(nodes, "Выход: Большая земля", NodeType.OUTPOST, 28, 31);
        bigLand.housing = 2; bigLand.defense = 60;

        Node elevator = node(nodes, "Элеватор", NodeType.RUINS, 21, -29);
        elevator.partsCap = 18; elevator.artifactCap = 1; elevator.housing = 8; elevator.defense = 24;

        Node atp = node(nodes, "АТП", NodeType.BASE, 61, -25);
        atp.farmCap = 6; atp.partsCap = 20; atp.housing = 25; atp.defense = 22;

        Node darkExit = node(nodes, "Выход: Тёмная долина", NodeType.OUTPOST, 96, -23);
        darkExit.housing = 2; darkExit.defense = 60;

        Node tunnel = node(nodes, "Туннель с электрами", NodeType.ANOMALY, 0, -48);
        tunnel.artifactCap = 4; tunnel.defense = 30;

        Node bridge = node(nodes, "Разрушенный мост", NodeType.BASE, 27, -48);
        bridge.housing = 6; bridge.defense = 20;

        Node hole = node(nodes, "Дыра в заборе", NodeType.RUINS, 55, -48);
        hole.artifactCap = 3; hole.housing = 6; hole.defense = 28;

        Node farm = node(nodes, "Свиноферма", NodeType.SETTLEMENT, 1, -70);
        farm.farmCap = 25; farm.housing = 15; farm.defense = 18;

        Node farPost = node(nodes, "Дальний блокпост", NodeType.BASE, 30, -96);
        farPost.housing = 10; farPost.defense = 24;

        Node svalkaExit = node(nodes, "Выход: Свалка", NodeType.OUTPOST, 30, -107);
        svalkaExit.housing = 2; svalkaExit.defense = 60;

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
        g.build();

        // baseNode непустых фракций; пустые фракции получают 0, чтобы их ИИ не падал на разыменовании
        int[] baseNodes = {VILLAGE, 0, 0, ATP, 0, SOUTH_POST, 0, 0, 0, 0};
        int[] members = {LONER_MEMBERS, 0, 0, BANDIT_MEMBERS, 0, MILITARY_MEMBERS, 0, 0, 0, 0};
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

        double[][] h = ZoneScenario.hostility();

        village.owner = ZoneScenario.LONERS;
        farm.owner = ZoneScenario.LONERS;
        atp.owner = ZoneScenario.BANDITS;
        farPost.owner = ZoneScenario.BANDITS;
        southPost.owner = ZoneScenario.MILITARY;
        bridge.owner = ZoneScenario.MILITARY;

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
        sim.finishSetup();
        return sim;
    }

    private static Node node(List<Node> list, String name, NodeType t, int x, int z) {
        Node n = new Node(list.size(), name, t, x, z);
        list.add(n);
        return n;
    }
}
