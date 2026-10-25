package ru.stalkerzone.zonemap.design;

import ru.stalkerzone.zonemap.Features.ForestKind;
import ru.stalkerzone.zonemap.Features.PathType;
import ru.stalkerzone.zonemap.Features.StType;
import ru.stalkerzone.zonemap.Region;
import ru.stalkerzone.zonemap.Region.Style;
import ru.stalkerzone.zonemap.ZoneMap;

/**
 * Глобальная карта: 20 локаций по схеме Call of Chernobyl, 5,4 × 9 км. Кордон в центре юга (0, 0), север это −Z.
 * Кордон прорисован детально (см. {@link CordonDesign}), остальные пока черновики: рельеф, дороги, смарт-террены.
 */
public final class WorldDesign {
    private static ZoneMap map;

    private WorldDesign() {}

    public static synchronized ZoneMap get() {
        if (map == null) map = create();
        return map;
    }

    public static ZoneMap create() {
        ZoneMap m = new ZoneMap(-3000, -8300, 2400, 700);
        // ряд 1: Болота, Кордон, Тёмная лощина
        Region swamps = m.add(new Region("swamps", "Болота", -2000, -600, -700, 600, 61, Style.SWAMP, 2));
        Region cordon = m.add(new Region("cordon", "Кордон", -650, -600, 650, 600, 66, Style.MEADOW, 3));
        Region hollow = m.add(new Region("hollow", "Тёмная лощина", 700, -600, 2000, 600, 70, Style.FOREST, 6));
        // ряд 2
        Region agro = m.add(new Region("agroprom", "Агропром", -2000, -1900, -700, -650, 64, Style.INDUSTRIAL, 4));
        Region garbage = m.add(new Region("garbage", "Свалка", -650, -1900, 650, -650, 66, Style.JUNK, 5));
        Region valley = m.add(new Region("darkvalley", "Тёмная долина", 700, -1900, 2000, -650, 68, Style.FOREST, 6));
        // ряд 3
        Region yantar = m.add(new Region("yantar", "Янтарь", -2900, -3200, -1700, -1950, 62, Style.SWAMP, 2));
        Region wild = m.add(new Region("wild", "Дикая территория", -1650, -3200, -700, -1950, 64, Style.INDUSTRIAL, 3));
        Region bar = m.add(new Region("bar", "Бар", -650, -3200, 650, -1950, 66, Style.INDUSTRIAL, 3));
        // ряд 4
        Region dead = m.add(new Region("deadcity", "Мёртвый город", -2900, -4450, -1700, -3250, 66, Style.URBAN, 3));
        Region red = m.add(new Region("redforest", "Рыжий лес", -1650, -4450, -700, -3250, 68, Style.RED_FOREST, 5));
        Region army = m.add(new Region("army", "Армейские склады", -650, -4450, 650, -3250, 70, Style.FOREST, 6));
        // ряд 5
        Region limansk = m.add(new Region("limansk", "Лиманск", -1650, -5700, -700, -4500, 66, Style.URBAN, 3));
        Region radar = m.add(new Region("radar", "Радар", -650, -5700, 650, -4500, 74, Style.WASTE, 7));
        Region zaton = m.add(new Region("zaton", "Затон", 700, -5700, 2300, -4500, 62, Style.MEADOW, 3));
        // ряд 6
        Region hospital = m.add(new Region("hospital", "Госпиталь", -1650, -6950, -700, -5750, 66, Style.URBAN, 3));
        Region pripyat = m.add(new Region("pripyat", "Припять", -650, -6950, 650, -5750, 66, Style.URBAN, 2));
        Region jupiter = m.add(new Region("jupiter", "Юпитер", 700, -6950, 2300, -5750, 68, Style.INDUSTRIAL, 4));
        // ряд 7
        Region gens = m.add(new Region("generators", "Генераторы", -1650, -8200, -700, -7000, 66, Style.WASTE, 4));
        Region chaes = m.add(new Region("chaes", "ЧАЭС", -650, -8200, 650, -7000, 66, Style.WASTE, 2));

        CordonDesign.fill(cordon);

        // ---------------- дороги между локациями (главная трасса с юга на север и ответвления)
        m.links.add(new ru.stalkerzone.zonemap.Features.Path(PathType.ASPHALT, 8, 0, new double[] {
                100, -600, 90, -900, 60, -1300, 20, -1700, 0, -2000, -10, -2500, 0, -3000, 20, -3400, 30, -3900, 0, -4400,
                -20, -4800, 0, -5300, 20, -5700, 0, -6200, -10, -6700, 0, -7100, 0, -7600}));
        link(m, PathType.ASPHALT, 7, 10, -1300, -400, -1250, -800, -1200, -1300, -1250);                    // Свалка - Агропром
        link(m, PathType.DIRT, 5, 60, -1250, 500, -1200, 900, -1300, 1300, -1350);                          // Свалка - Тёмная долина
        link(m, PathType.DIRT, 5, -650, 170, -900, 160, -1300, 120);                                      // Кордон - Болота
        link(m, PathType.DIRT, 5, -650, -520, -900, -700, -1250, -900);                                   // Кордон - Агропром
        link(m, PathType.DIRT, 5, 650, 160, 900, 150, 1300, 100);                                         // Кордон - Тёмная лощина
        link(m, PathType.DIRT, 5, 1300, 100, 1350, -400, 1300, -900);                                     // Тёмная лощина - Тёмная долина
        link(m, PathType.DIRT, 5, -1300, 120, -1350, -400, -1300, -900);                                  // Болота - Агропром
        link(m, PathType.ASPHALT, 7, -10, -2550, -500, -2550, -1150, -2600, -1700, -2600, -2300, -2580);    // Бар - Дикая - Янтарь
        link(m, PathType.DIRT, 5, -2300, -2580, -2300, -3300, -2300, -3850);                               // Янтарь - Мёртвый город
        link(m, PathType.DIRT, 5, -1150, -2600, -1150, -3300, -1150, -3850);                               // Дикая - Рыжий лес
        link(m, PathType.DIRT, 5, 20, -3850, -600, -3850, -1150, -3850, -2300, -3850);                     // Склады - Рыжий лес - Мёртвый город
        link(m, PathType.ASPHALT, 6, -1150, -3850, -1150, -4600, -1150, -5100, -1150, -6350, -1150, -7600); // Рыжий лес - Лиманск - Госпиталь - Генераторы
        link(m, PathType.DIRT, 5, -1150, -7600, -600, -7650, 0, -7600);                                    // Генераторы - ЧАЭС
        link(m, PathType.DIRT, 5, 30, -3900, 700, -4300, 1400, -5100);                                     // Склады - Затон
        link(m, PathType.ASPHALT, 6, 1400, -5100, 1500, -5800, 1500, -6350, 900, -6400, 0, -6300);          // Затон - Юпитер - Припять
        // железная дорога: через Болота, Кордон, Тёмную лощину; и ветка Юпитер - Припять
        m.links.add(new ru.stalkerzone.zonemap.Features.Path(PathType.RAIL, 5, 4, new double[] {-1950, -40, -1300, -60, -700, -60}));
        m.links.add(new ru.stalkerzone.zonemap.Features.Path(PathType.RAIL, 5, 4, new double[] {700, 10, 1300, 30, 1950, 0}));
        m.links.add(new ru.stalkerzone.zonemap.Features.Path(PathType.RAIL, 5, 4, new double[] {2250, -6100, 1500, -6200, 700, -6250, 300, -6450}));

        // ---------------- черновики: главные точки локаций (как смарт-террены CoC)
        draft(swamps, new Object[][] {
                {"mar_base", "База Чистого неба", StType.BASE, -1700, -300, "clear_sky", 18},
                {"mar_church", "Церковь", StType.OUTPOST, -1350, 250, "renegades", 8},
                {"mar_tower", "Водонапорная башня", StType.CHECKPOINT, -1000, -350, null, 6},
                {"mar_farm", "Хутор", StType.OUTPOST, -1500, 300, null, 8},
                {"mar_fishers", "Рыбацкая деревня", StType.TERRITORY, -900, 300, null, 6},
                {"mar_anom", "Аномальные кочки", StType.RESOURCE, -1150, -50, null, 4},
                {"mar_lair", "Логово плотей", StType.LAIR, -1800, 350, null, 0}});
        swamps.pond(-1500, -100, 160, 90, 3).pond(-1100, 150, 110, 70, 3).pond(-1800, 100, 80, 120, 3);
        swamps.forest(ForestKind.SWAMP_WILLOW, 0.35, -2000, -600, -700, -600, -700, 600, -2000, 600);
        draft(hollow, new Object[][] {
                {"hol_camp", "Лагерь в лощине", StType.BASE, 1400, -200, "renegades", 14},
                {"hol_farm", "Заброшенный хутор", StType.OUTPOST, 1100, 300, null, 8},
                {"hol_anom", "Поляна аномалий", StType.RESOURCE, 1700, 200, null, 4},
                {"hol_lair", "Логово кровососов", StType.LAIR, 1750, -400, null, 0}});
        hollow.forest(ForestKind.PINE, 0.6, 700, -600, 2000, -600, 2000, 600, 700, 600);
        hollow.hill(1300, -100, 300, 14).hill(1700, 300, 200, 10);
        draft(agro, new Object[][] {
                {"agr_nii", "НИИ Агропром", StType.BASE, -1350, -1300, "military", 22},
                {"agr_lab", "Подземелья Агропрома", StType.LAB, -1250, -1150, null, 0},
                {"agr_village", "Заброшенная деревня", StType.OUTPOST, -1750, -900, null, 8},
                {"agr_swamp", "Болото у НИИ", StType.RESOURCE, -900, -1500, null, 4},
                {"agr_lair", "Логово псов", StType.LAIR, -1800, -1700, null, 0}});
        agro.pond(-900, -1500, 140, 90, 3).hill(-1700, -1200, 220, 9);
        draft(garbage, new Object[][] {
                {"gar_depot", "Депо", StType.OUTPOST, -300, -1100, "bandits", 10},
                {"gar_hangar", "Ангар", StType.OUTPOST, 300, -1300, null, 10},
                {"gar_cemetery", "Кладбище техники", StType.RESOURCE, 0, -1600, null, 4},
                {"gar_post", "Блокпост на дороге", StType.CHECKPOINT, 60, -800, "bandits", 6},
                {"gar_wagons", "Лагерь у вагонов", StType.TERRITORY, -350, -1650, null, 6},
                {"gar_lair", "Логово слепых псов", StType.LAIR, 450, -1700, null, 0}});
        garbage.hill(-300, -1400, 120, 14).hill(250, -1050, 140, 16).hill(-450, -800, 110, 10);
        draft(valley, new Object[][] {
                {"val_factory", "База бандитов (завод)", StType.BASE, 1300, -1250, "bandits", 22},
                {"val_x18", "Лаборатория X-18", StType.LAB, 1600, -1500, null, 0},
                {"val_pigsty", "Свинарник", StType.OUTPOST, 1000, -900, null, 8},
                {"val_anom", "Аномальная низина", StType.RESOURCE, 1700, -800, null, 4},
                {"val_lair", "Логово кабанов", StType.LAIR, 900, -1700, null, 0}});
        draft(bar, new Object[][] {
                {"bar_100", "Бар «100 рентген»", StType.BASE, -150, -2550, "duty", 24},
                {"bar_post", "Блокпост Долга", StType.CHECKPOINT, 0, -2000, "duty", 8},
                {"bar_arena", "Арена", StType.TERRITORY, 200, -2700, "duty", 6},
                {"bar_rostok", "Завод «Росток»", StType.OUTPOST, 250, -2350, "duty", 10},
                {"bar_lair", "Логово псевдособак", StType.LAIR, 450, -3000, null, 0}});
        draft(wild, new Object[][] {
                {"wil_depot", "Ремонтное депо", StType.OUTPOST, -1200, -2450, null, 10},
                {"wil_bridge", "Ж/д мост", StType.CHECKPOINT, -900, -2800, null, 6},
                {"wil_anom", "Аномальные пути", StType.RESOURCE, -1400, -2900, null, 4}});
        draft(yantar, new Object[][] {
                {"yan_bunker", "Бункер учёных", StType.BASE, -2350, -2450, "ecologists", 12},
                {"yan_factory", "Завод «Янтарь»", StType.OUTPOST, -2500, -2850, null, 10},
                {"yan_x16", "Лаборатория X-16", StType.LAB, -2450, -2950, null, 0},
                {"yan_lake", "Озеро Янтарь", StType.RESOURCE, -2100, -2700, null, 4},
                {"yan_lair", "Логово зомби", StType.LAIR, -2700, -2300, null, 0}});
        yantar.pond(-2100, -2700, 220, 160, 4);
        // v1.10: военная дорога снабжения НИИ Агропром → две полевые базы армии → бункер учёных на Янтаре.
        // По ней идут конвои: плата учёных на ближайшую базу, оттуда деньги на КПП, обратно патроны и пополнение.
        link(m, PathType.DIRT, 6, -1350, -1300, -1480, -1480, -1650, -1650, -1820, -1880, -2050, -2150, -2220, -2320, -2350, -2450);
        agro.smart("agr_mbase", "База военных «Агропром»", StType.OUTPOST, -1650, -1650, 45, "military", 14);
        agro.put("military_base", -1650, -1650, 0, 54, 46);
        yantar.smart("yan_mbase", "База военных «Янтарь»", StType.OUTPOST, -2050, -2150, 45, "military", 14);
        yantar.put("military_base", -2050, -2150, 0, 54, 46);
        draft(dead, new Object[][] {
                {"dea_base", "База наёмников", StType.BASE, -2300, -3800, "mercs", 18},
                {"dea_school", "Школа", StType.OUTPOST, -2600, -3500, null, 8},
                {"dea_anom", "Аномальный квартал", StType.RESOURCE, -2000, -4200, null, 4}});
        draft(red, new Object[][] {
                {"red_forester", "Домик лесника", StType.BASE, -1300, -3500, "loners", 10},
                {"red_bridge", "Мост через реку", StType.CHECKPOINT, -1150, -4000, null, 6},
                {"red_mine", "Шахта", StType.OUTPOST, -900, -4250, null, 8},
                {"red_anom", "Аномальный лес", StType.RESOURCE, -1450, -4100, null, 4},
                {"red_lair", "Логово химер", StType.LAIR, -1500, -3400, null, 0}});
        red.river(14, 4, -1650, -4000, -1150, -4000, -700, -4050);
        draft(army, new Object[][] {
                {"mil_base", "База Свободы", StType.BASE, 150, -3800, "freedom", 24},
                {"mil_farm", "Хутор", StType.OUTPOST, -350, -3500, null, 8},
                {"mil_post", "Блокпост у Радара", StType.CHECKPOINT, 0, -4350, "freedom", 8},
                {"mil_anom", "Аномальная роща", StType.RESOURCE, 450, -4100, null, 4},
                {"mil_lair", "Логово контролёра", StType.LAIR, -450, -4250, null, 0}});
        draft(limansk, new Object[][] {
                {"lim_build", "Стройка", StType.OUTPOST, -1200, -5300, null, 10},
                {"lim_town", "Городские кварталы", StType.TERRITORY, -1100, -4900, null, 8},
                {"lim_anom", "Аномальная улица", StType.RESOURCE, -1400, -5000, null, 4}});
        draft(radar, new Object[][] {
                {"rad_x10", "Лаборатория X-10", StType.LAB, -50, -5150, null, 0},
                {"rad_duga", "«Дуга»", StType.RESOURCE, 0, -5400, null, 4},
                {"rad_post", "Блокпост Монолита", StType.CHECKPOINT, 50, -5650, "monolith", 8},
                {"rad_lair", "Логово мутантов", StType.LAIR, 400, -4800, null, 0}});
        radar.hill(-300, -5000, 250, 14).hill(350, -5300, 200, 12);
        draft(zaton, new Object[][] {
                {"zat_skadovsk", "«Скадовск»", StType.BASE, 1300, -5300, "loners", 20},
                {"zat_dredge", "Земснаряд", StType.RESOURCE, 1700, -4900, null, 4},
                {"zat_plant", "Ремзавод", StType.OUTPOST, 1000, -4700, null, 10},
                {"zat_station", "Станция переработки", StType.OUTPOST, 2000, -5500, null, 8},
                {"zat_lair", "Логово химеры", StType.LAIR, 900, -5600, null, 0}});
        zaton.river(60, 5, 700, -5100, 1300, -5150, 2300, -5050).pond(1700, -4900, 200, 120, 3);
        draft(hospital, new Object[][] {
                {"hos_main", "Госпиталь", StType.TERRITORY, -1150, -6350, null, 10},
                {"hos_anom", "Аномальные палаты", StType.RESOURCE, -1350, -6100, null, 4}});
        draft(pripyat, new Object[][] {
                {"pri_polesie", "Гостиница «Полесье»", StType.BASE, 0, -6300, "monolith", 20},
                {"pri_prometheus", "Кинотеатр «Прометей»", StType.OUTPOST, -350, -6200, null, 10},
                {"pri_lazurny", "Бассейн «Лазурный»", StType.OUTPOST, 350, -6250, null, 10},
                {"pri_hospital", "Больница", StType.OUTPOST, 50, -6650, null, 10},
                {"pri_anom", "Аномальный парк", StType.RESOURCE, 250, -6050, null, 4}});
        draft(jupiter, new Object[][] {
                {"jup_yanov", "Станция Янов", StType.BASE, 1100, -6200, "loners", 20},
                {"jup_bunker", "Бункер учёных", StType.BASE, 1700, -6100, "ecologists", 10},
                {"jup_plant", "Завод «Юпитер»", StType.OUTPOST, 1600, -6600, null, 10},
                {"jup_anom", "Аномальные карьеры", StType.RESOURCE, 2000, -6500, null, 4}});
        draft(gens, new Object[][] {
                {"gen_main", "Генераторы", StType.LAB, -1150, -7650, null, 0},
                {"gen_anom", "Аномальные поля", StType.RESOURCE, -1400, -7400, null, 4}});
        draft(chaes, new Object[][] {
                {"cha_sarc", "Саркофаг", StType.BASE, 0, -7700, "monolith", 30},
                {"cha_x2", "Лаборатория X-2", StType.LAB, 100, -7800, null, 0},
                {"cha_anom", "Аномалии у энергоблоков", StType.RESOURCE, 350, -7500, null, 4}});

        return m.build();
    }

    private static void link(ZoneMap m, PathType t, double w, double... pts) {
        m.links.add(new ru.stalkerzone.zonemap.Features.Path(t, w, 0, pts));
    }

    /** Черновая точка: смарт-террен и временный лагерь на нём. */
    private static void draft(Region r, Object[][] rows) {
        for (Object[] o : rows) {
            StType t = (StType) o[2];
            int x = (Integer) o[3], z = (Integer) o[4];
            r.smart((String) o[0], (String) o[1], t, x, z, t == StType.BASE ? 70 : 40, (String) o[5], (Integer) o[6]);
            String kind = switch (t) {
                case BASE -> "draft_base";
                case OUTPOST, CHECKPOINT -> "draft_post";
                case RESOURCE, TERRITORY -> "tent_camp";
                case LAB -> "lab_entrance";
                case LAIR -> "lair_den";
            };
            int pad = switch (t) { case BASE -> 50; case OUTPOST, CHECKPOINT -> 26; case LAB -> 16; case LAIR -> 0; default -> 14; };
            r.put(kind, x, z, 0, pad, pad);
        }
    }
}
