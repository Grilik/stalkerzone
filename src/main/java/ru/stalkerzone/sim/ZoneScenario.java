package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;

/** Стартовый мир: карта Зоны, семь фракций, три торговца с выходом на Большую землю, север с Выжигателем. */
public final class ZoneScenario {
    public static final int LONERS = 0, DUTY = 1, FREEDOM = 2, BANDITS = 3, SCIENTISTS = 4, MILITARY = 5, MONOLITH = 6,
            MERCS = 7, CLEAR_SKY = 8, SIN = 9;

    private ZoneScenario() {}

    public static Simulation create(long seed) {
        List<Node> nodes = new ArrayList<>();

        // v1.14: координаты по глобальной карте ТЧ в масштабе 1:1 (×10 блоков): центры уровней из game_maps_single,
        // Кордон — деревня новичков. Свои места (Болота, посты, хутора) расставлены по канону между уровнями.
        // Карта по оригиналу (север это −Z): Кордон на юге, над ним Свалка, Агропром к западу, Тёмная долина к востоку,
        // дальше Бар, к западу Дикая территория и Янтарь, к северу Армейские склады, Радар с Выжигателем, Припять и ЧАЭС.
        // Порядок точек не менять: на номера опираются базы фракций и сохранения.
        Node kordon = node(nodes, "Кордон", NodeType.BASE, 0, 0);
        kordon.farmCap = 30; kordon.partsCap = 5; kordon.artifactCap = 1; kordon.housing = 50;
        trader(kordon, 1.08, 0.95, caps(50, 15, 120, 15, 3, 40));

        Node svalka = node(nodes, "Свалка", NodeType.RUINS, 26, -147);
        svalka.farmCap = 5; svalka.partsCap = 30; svalka.artifactCap = 1; svalka.housing = 15; svalka.defense = 18;

        Node agroprom = node(nodes, "Агропром", NodeType.RUINS, -45, -128);
        agroprom.farmCap = 10; agroprom.partsCap = 25; agroprom.artifactCap = 2; agroprom.housing = 15; agroprom.defense = 24;

        Node dark = node(nodes, "Тёмная долина", NodeType.BASE, 133, -160);
        dark.farmCap = 25; dark.partsCap = 10; dark.artifactCap = 1; dark.housing = 40;

        Node bar = node(nodes, "Бар", NodeType.BASE, 29, -248);
        bar.farmCap = 20; bar.housing = 45; bar.partsCap = 5; bar.artifactCap = 1;
        trader(bar, 1.08, 0.95, caps(70, 10, 100, 12, 2, 35));

        Node wild = node(nodes, "Дикая территория", NodeType.SETTLEMENT, -12, -248);
        wild.farmCap = 30; wild.partsCap = 5; wild.artifactCap = 1; wild.housing = 20; wild.defense = 22;

        Node army = node(nodes, "Армейские склады", NodeType.BASE, 40, -332);
        army.farmCap = 30; army.partsCap = 25; army.artifactCap = 1; army.housing = 40;

        Node yantar = node(nodes, "Янтарь", NodeType.ANOMALY, -72, -220);
        yantar.farmCap = 20; yantar.partsCap = 5; yantar.artifactCap = 5; yantar.housing = 30; yantar.defense = 30;
        trader(yantar, 1.15, 0.90, caps(25, 5, 40, 5, 1, 15));

        // Радар: источник пси-излучения Выжигателя (под ним X-10)
        Node radar = node(nodes, "Радар", NodeType.PSI, 87, -404);
        radar.partsCap = 5; radar.artifactCap = 6; radar.housing = 10; radar.defense = 45;

        // Припять: город из нескольких районов, за Радаром; дорога закрыта, пока работает Выжигатель
        Node center = node(nodes, "Припять: центр", NodeType.CITY, 80, -512);
        center.farmCap = 5; center.partsCap = 30; center.artifactCap = 3; center.housing = 35; center.defense = 55;

        Node chaes = node(nodes, "ЧАЭС", NodeType.BASE, 36, -668);
        chaes.farmCap = 50; chaes.partsCap = 40; chaes.artifactCap = 8; chaes.housing = 130;

        // запад: база армии и блокпосты на дороге к учёным
        Node perimeter = node(nodes, "Военная база «Периметр»", NodeType.BASE, -80, -30);
        perimeter.farmCap = 80; perimeter.partsCap = 30; perimeter.housing = 90;

        Node post1 = node(nodes, "Блокпост «Лесной»", NodeType.OUTPOST, -95, -95);
        post1.housing = 6; post1.defense = 18;

        Node post2 = node(nodes, "Блокпост «Болотный»", NodeType.OUTPOST, -100, -160);
        post2.housing = 6; post2.defense = 18;

        Node prometheus = node(nodes, "Припять: «Прометей»", NodeType.CITY, 6, -488);
        prometheus.farmCap = 5; prometheus.partsCap = 25; prometheus.artifactCap = 2; prometheus.housing = 25; prometheus.defense = 50;

        Node lazurny = node(nodes, "Припять: «Лазурный»", NodeType.CITY, 154, -492);
        lazurny.farmCap = 5; lazurny.partsCap = 25; lazurny.artifactCap = 3; lazurny.housing = 25; lazurny.defense = 50;

        Node hospital = node(nodes, "Припять: больница", NodeType.CITY, 86, -568);
        hospital.farmCap = 5; hospital.partsCap = 20; hospital.artifactCap = 2; hospital.housing = 25; hospital.defense = 55;

        // v1.10: полевые базы армии на дороге от Агропрома к Янтарю (меньше КПП): через них идёт плата учёных
        Node fbAgro = node(nodes, "База военных «Агропром»", NodeType.OUTPOST, -80, -120);
        fbAgro.housing = 14; fbAgro.defense = 22; fbAgro.farmCap = 4;
        Node fbYantar = node(nodes, "База военных «Янтарь»", NodeType.OUTPOST, -92, -190);
        fbYantar.housing = 14; fbYantar.defense = 22; fbYantar.partsCap = 3;

        // v1.14: новые локации (только в конец списка)
        Node swamps = node(nodes, "Болота", NodeType.BASE, -110, 20);
        swamps.farmCap = 20; swamps.partsCap = 5; swamps.artifactCap = 2; swamps.housing = 35;
        trader(swamps, 1.15, 0.90, caps(25, 5, 40, 8, 1, 15));
        Node deadCity = node(nodes, "Мёртвый город", NodeType.RUINS, -89, -319);
        deadCity.farmCap = 5; deadCity.partsCap = 25; deadCity.artifactCap = 2; deadCity.housing = 30; deadCity.defense = 30;
        Node dogVillage = node(nodes, "Собачья деревня", NodeType.RUINS, 120, 0);
        dogVillage.farmCap = 8; dogVillage.partsCap = 10; dogVillage.artifactCap = 1; dogVillage.housing = 10; dogVillage.defense = 26;
        Node forester = node(nodes, "Кордон Лесника", NodeType.SETTLEMENT, -20, -390);
        forester.farmCap = 6; forester.partsCap = 3; forester.artifactCap = 3; forester.housing = 6; forester.defense = 30;
        Node doctor = node(nodes, "Хутор Болотного Доктора", NodeType.SETTLEMENT, -140, 45);
        doctor.farmCap = 6; doctor.housing = 4; doctor.defense = 40;

        ZoneGraph g = new ZoneGraph(nodes.size());
        g.connect(0, 1, 1, 0.02);    // Кордон - Свалка
        g.connect(0, 2, 2, 0.03);    // Кордон - Агропром
        g.connect(1, 2, 1, 0.03);    // Свалка - Агропром
        g.connect(1, 3, 1, 0.04);    // Свалка - Тёмная долина
        g.connect(1, 4, 2, 0.04);    // Свалка - Бар
        g.connect(3, 4, 3, 0.05);    // Тёмная долина - Бар (окольная тропа)
        g.connect(4, 5, 1, 0.03);    // Бар - Дикая территория
        g.connect(5, 7, 2, 0.06);    // Дикая территория - Янтарь
        g.connect(4, 6, 2, 0.05);    // Бар - Армейские склады
        g.connect(6, 8, 2, 0.08);    // Армейские склады - Радар
        g.connect(7, 8, 3, 0.10);    // Янтарь - Радар (через Рыжий лес)
        g.connect(8, 9, 2, 0.15);    // Радар - Припять (закрыта до отключения Выжигателя)
        g.connect(9, 10, 2, 0.12);   // Припять - ЧАЭС
        g.connect(9, 14, 1, 0.08);   // центр - «Прометей»
        g.connect(9, 15, 1, 0.08);   // центр - «Лазурный»
        g.connect(9, 16, 1, 0.08);   // центр - больница
        g.connect(14, 16, 1, 0.08);  // «Прометей» - больница
        g.connect(15, 16, 1, 0.08);  // «Лазурный» - больница
        g.connect(16, 10, 1, 0.12);  // больница - ЧАЭС
        g.connect(11, 0, 2, 0.03);   // Периметр - Кордон
        g.connect(11, 2, 2, 0.03);   // Периметр - Агропром
        g.connect(11, 12, 1, 0.04);  // Периметр - Лесной
        g.connect(12, 13, 1, 0.04);  // Лесной - Болотный
        g.connect(13, 7, 1, 0.05);   // Болотный - Янтарь
        g.connect(2, 17, 1, 0.03);   // Агропром - база военных «Агропром»
        g.connect(11, 17, 2, 0.02);  // КПП «Периметр» - база «Агропром» (военная дорога)
        g.connect(17, 18, 1, 0.03);  // база «Агропром» - база «Янтарь»
        g.connect(18, 7, 1, 0.03);   // база «Янтарь» - Янтарь
        g.connect(19, 0, 2, 0.04);   // Болота - Кордон
        g.connect(19, 2, 2, 0.05);   // Болота - Агропром
        g.connect(19, 23, 1, 0.05);  // Болота - хутор Доктора
        g.connect(20, 5, 2, 0.07);   // Мёртвый город - Дикая территория
        g.connect(20, 7, 2, 0.07);   // Мёртвый город - Янтарь
        g.connect(20, 22, 2, 0.08);  // Мёртвый город - Лесник
        g.connect(21, 0, 1, 0.05);   // Собачья деревня - Кордон
        g.connect(21, 3, 2, 0.05);   // Собачья деревня - Тёмная долина
        g.connect(22, 8, 1, 0.09);   // Лесник - Радар
        g.connect(22, 7, 2, 0.08);   // Лесник - Янтарь
        g.build();

        List<Faction> factions = new ArrayList<>();
        factions.add(new Faction(LONERS, "Одиночки", 0, 0.25, false, 40, new Doctrine(0.6, 0.4, 0.8, 0.8, 0.1, 0.35)));
        factions.add(new Faction(DUTY, "Долг", 4, 0.55, false, 45, new Doctrine(0.9, 0.7, 0.6, 0.3, 1.0, 0.9)));
        factions.add(new Faction(FREEDOM, "Свобода", 6, 0.55, false, 40, new Doctrine(1.0, 0.8, 0.3, 0.6, 0.0, 0.6)));
        factions.add(new Faction(BANDITS, "Бандиты", 3, 0.75, true, 35, new Doctrine(1.1, 0.9, 0.2, 0.2, 0.0, 0.3)));
        factions.add(new Faction(SCIENTISTS, "Учёные", 7, 0.0, false, 24, new Doctrine(0.1, 0.0, 0.9, 0.9, 0.0, 0.1)));
        factions.add(new Faction(MILITARY, "Военные", 11, 0.25, false, 70, new Doctrine(0.3, 0.5, 0.7, 0.3, 1.0, 1.0)));
        factions.add(new Faction(MONOLITH, "Монолит", 10, 0.9, true, 55, new Doctrine(0.5, 0.0, 0.2, 0.0, 0.0, 1.0)));
        // v1.14: наёмники в Дикой территории (как в ТЧ), Чистое небо на Болотах, секта «Грех» в Мёртвом городе
        factions.add(new Faction(MERCS, "Наёмники", 5, 0.6, false, 30, new Doctrine(1.0, 0.8, 0.4, 0.5, 0.3, 0.7)));
        factions.add(new Faction(CLEAR_SKY, "Чистое небо", 19, 0.3, false, 30, new Doctrine(0.5, 0.4, 0.8, 0.8, 0.2, 0.4)));
        factions.add(new Faction(SIN, "Грех", 20, 0.85, true, 25, new Doctrine(1.2, 1.0, 0.1, 0.1, 0.0, 0.5)));
        factions.get(LONERS).mercenary = true;
        // кого притягивает фракция: преданность и жадность характера новичка
        double[][] ideals = {{0.0, 0.0}, {0.7, -0.4}, {-0.6, 0.1}, {-0.3, 0.8}};
        for (int i = 0; i < ideals.length; i++) {
            factions.get(i).takesNewcomers = true;
            factions.get(i).idealLoyalty = ideals[i][0];
            factions.get(i).idealGreed = ideals[i][1];
        }
        factions.get(SCIENTISTS).passive = true;
        factions.get(SCIENTISTS).scientist = true;
        factions.get(MILITARY).military = true;
        factions.get(MONOLITH).fanatic = true;
        // профессиональные группы (v1.12): кому и сколько нужно заводского под особые задачи
        factions.get(LONERS).eliteShare = 0.06;    // проводники и охотники за артефактами
        factions.get(DUTY).eliteShare = 0.15;      // штурмовые группы и снайперы
        factions.get(FREEDOM).eliteShare = 0.15;   // ударные группы на западном оружии
        factions.get(BANDITS).eliteShare = 0.05;   // ближний круг пахана
        factions.get(SCIENTISTS).eliteShare = 0.3; // охрана экспедиций
        factions.get(MONOLITH).eliteShare = 0;     // фанатики заводского не покупают
        // своих мастерских почти нет: прототипы у торговцев закрывают часть снаряжения
        factions.get(LONERS).protoBuyer = true;
        factions.get(BANDITS).protoBuyer = true;
        factions.get(MERCS).eliteShare = 0.3;       // профессионалы на импортном
        factions.get(CLEAR_SKY).eliteShare = 0.1;
        factions.get(CLEAR_SKY).protoBuyer = true;
        factions.get(SIN).eliteShare = 0.05;
        factions.get(SIN).protoBuyer = true;

        double[][] h = new double[10][10];
        rel(h, DUTY, FREEDOM, 1.0);
        rel(h, BANDITS, LONERS, 0.7);
        rel(h, BANDITS, DUTY, 0.8);
        rel(h, BANDITS, FREEDOM, 0.6);
        rel(h, LONERS, DUTY, 0.1);
        rel(h, LONERS, FREEDOM, 0.1);
        rel(h, MILITARY, BANDITS, 0.9);
        rel(h, MILITARY, FREEDOM, 0.25);
        rel(h, MILITARY, LONERS, 0.2);
        rel(h, MILITARY, DUTY, 0.1);
        for (int i = 0; i < 6; i++) rel(h, MONOLITH, i, 1.0);
        // v1.14: Долг и Свобода — главная вражда (уже 1.0); наёмники работают на всех и против всех
        rel(h, MERCS, DUTY, 0.6);
        rel(h, MERCS, LONERS, 0.4);
        rel(h, MERCS, MILITARY, 0.5);
        rel(h, MERCS, FREEDOM, 0.3);
        rel(h, MERCS, BANDITS, 0.3);
        rel(h, MERCS, SCIENTISTS, 0.2);
        rel(h, MERCS, CLEAR_SKY, 0.6);
        rel(h, CLEAR_SKY, BANDITS, 0.7);
        rel(h, CLEAR_SKY, MILITARY, 0.3);
        rel(h, CLEAR_SKY, DUTY, 0.1);
        rel(h, CLEAR_SKY, FREEDOM, 0.1);
        for (int i = 0; i < 9; i++) if (i != MONOLITH) rel(h, SIN, i, 1.0);
        rel(h, SIN, MONOLITH, 0.5);
        rel(h, MONOLITH, MERCS, 1.0);
        rel(h, MONOLITH, CLEAR_SKY, 1.0);

        // начальное владение
        kordon.owner = LONERS;
        svalka.owner = LONERS;
        bar.owner = DUTY;
        wild.owner = MERCS;
        swamps.owner = CLEAR_SKY;
        deadCity.owner = SIN;
        army.owner = FREEDOM;
        yantar.owner = SCIENTISTS;
        dark.owner = BANDITS;
        agroprom.owner = BANDITS;
        chaes.owner = MONOLITH;
        center.owner = MONOLITH;
        perimeter.owner = MILITARY;
        fbAgro.owner = MILITARY;
        fbYantar.owner = MILITARY;

        // стартовые склады
        for (Faction f : factions) {
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
            if (f.scientist) f.stock.add(Good.PROTO, 3);
            f.stock.add(Good.MEDS, m * 2);
            f.stock.add(Good.MATERIALS, f.fanatic ? 0 : m);
            f.stock.money = f.fanatic ? 0 : f.military ? 6000 : f.scientist ? 4000 : 3000;
        }
        // стартовые рынки торговцев
        for (Node n : nodes) {
            if (!n.trader) continue;
            for (Good gd : Good.ALL) n.market.qty[gd.ordinal()] = (int) (n.target[gd.ordinal()] * 0.8);
            n.market.money = 20000;
        }

        Simulation sim = new Simulation(seed, nodes, g, factions, h);
        sim.finishSetup();
        return sim;
    }

    private static void rel(double[][] h, int a, int b, double v) {
        h[a][b] = h[b][a] = v;
    }

    private static Node node(List<Node> list, String name, NodeType t, int x, int z) {
        Node n = new Node(list.size(), name, t, x, z);
        list.add(n);
        return n;
    }

    private static int[] caps(int food, int parts, int ammo, int meds, int gear, int materials) {
        int[] c = new int[Good.ALL.length];
        c[Good.FOOD.ordinal()] = food;
        c[Good.PARTS.ordinal()] = parts;
        // с Большой земли торговцы завозят только заводское; кустарное скупают у группировок
        c[Good.AMMO_PRO.ordinal()] = ammo;
        c[Good.MEDS.ordinal()] = meds;
        c[Good.GEAR_PRO.ordinal()] = gear;
        c[Good.MATERIALS.ordinal()] = materials;
        return c;
    }

    private static void trader(Node n, double importMarkup, double exportRate, int[] importCap) {
        n.trader = true;
        n.importMarkup = importMarkup;
        n.exportRate = exportRate;
        System.arraycopy(importCap, 0, n.importCap, 0, importCap.length);
        n.target[Good.FOOD.ordinal()] = 300;
        n.target[Good.PARTS.ordinal()] = 200;
        n.target[Good.AMMO.ordinal()] = 600;
        n.target[Good.AMMO_PRO.ordinal()] = 250;
        n.target[Good.PROTO.ordinal()] = 6;
        n.target[Good.GEAR_PRO.ordinal()] = 3;
        n.target[Good.MEDS.ordinal()] = 120;
        n.target[Good.GEAR.ordinal()] = 16;
        n.target[Good.ARTIFACT.ordinal()] = 3;
        n.target[Good.MATERIALS.ordinal()] = 220;
    }
}
