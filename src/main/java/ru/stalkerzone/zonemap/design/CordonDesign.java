package ru.stalkerzone.zonemap.design;

import ru.stalkerzone.zonemap.Features.ForestKind;
import ru.stalkerzone.zonemap.Features.Ground;
import ru.stalkerzone.zonemap.Features.PathType;
import ru.stalkerzone.zonemap.Features.StType;
import ru.stalkerzone.zonemap.Region;

/**
 * Кордон по мотивам «Тени Чернобыля» и Call of Chernobyl (1300 × 1200 блоков, север это −Z).
 * Юг: военный КПП на трассе и периметр с колючкой. Юго-запад: деревня новичков и бункер Сидоровича в холме.
 * Середина: железнодорожная насыпь с запада на восток, мост над трассой с военным постом, на востоке тоннель с электрами.
 * Север: АТП на северо-западе, элеватор и фермы на северо-востоке, застава на дороге к Свалке. ЛЭП через всю локацию.
 */
public final class CordonDesign {
    private CordonDesign() {}

    public static void fill(Region r) {
        r.detailed = true;

        // ---------------- рельеф
        r.hill(-415, 305, 55, 8);       // холм с бункером Сидоровича
        r.hill(-470, -300, 160, 11);     // холмы на северо-западе
        r.hill(-170, -470, 130, 9);
        r.hill(530, -110, 150, 13);      // гряда у тоннеля
        r.hill(420, 360, 190, 5);        // пологий подъём на юго-востоке
        r.hill(-120, -205, 70, 9);       // аномальный холм
        r.hill(-520, 460, 130, -3);      // низина с прудом
        r.hill(250, 520, 120, 4);
        r.hill(-250, 520, 160, 6);

        // ---------------- вода
        r.pond(-520, 465, 70, 45, 4);
        r.pond(390, -390, 30, 22, 3);
        r.pond(-40, 380, 22, 16, 2);

        // ---------------- дороги
        // трасса с юга (из-за периметра) на север к Свалке
        r.path(PathType.ASPHALT, 8, 40, 700, 40, 560, 35, 470, 45, 330, 70, 160, 85, 40, 90, -20, 110, -140, 130, -300, 115, -450, 100, -600);
        // в деревню
        r.path(PathType.DIRT, 5, 45, 300, -80, 285, -200, 270, -330, 255, -430, 240);
        r.path(PathType.DIRT, 4, -250, 265, -255, 200);
        r.path(PathType.DIRT, 4, -320, 255, -335, 335);
        // к АТП (вдоль насыпи с севера)
        r.path(PathType.DIRT, 5, 95, -80, 60, -95, -120, -110, -260, -200, -340, -270);
        // к элеватору
        r.path(PathType.DIRT, 5, 120, -200, 230, -230, 300, -275);
        // на запад к Болотам и на восток к Тёмной лощине
        r.path(PathType.DIRT, 5, -430, 240, -560, 200, -650, 170);
        r.path(PathType.DIRT, 5, 62, 240, 200, 225, 400, 180, 650, 160);
        // на северо-запад к Агропрому
        r.path(PathType.DIRT, 4, -340, -330, -480, -440, -650, -520);
        // тропы
        r.path(PathType.TRAIL, 2, -250, 200, -150, 90, -40, 10, 60, -10);       // деревня - под мост
        r.path(PathType.TRAIL, 2, 330, -255, 400, -130, 450, -30);             // элеватор - тоннель
        r.path(PathType.TRAIL, 2, -210, 235, -170, 0, -130, -160);              // деревня - аномальный холм
        r.path(PathType.TRAIL, 2, 470, 30, 500, 250, 520, 400);                 // тоннель - юго-восточные поля
        r.path(PathType.TRAIL, 2, -460, 230, -520, 380);                         // к пруду

        // ---------------- железная дорога на насыпи
        r.rail(5, -700, -60, -400, -50, -150, -40, 90, -25, 400, -10, 700, 10);

        // ---------------- леса и поля
        r.forest(ForestKind.PINE, 0.75, -650, -600, -480, -600, -430, -350, -520, 0, -470, 150, -560, 330, -650, 380);
        r.forest(ForestKind.MIXED, 0.7, 300, -600, 650, -600, 650, -200, 560, -160, 430, -330, 300, -430);
        r.forest(ForestKind.BIRCH, 0.6, 480, 270, 630, 300, 640, 520, 500, 560, 430, 420);
        r.forest(ForestKind.BIRCH, 0.55, -60, 80, 40, 90, 20, 170, -80, 160);
        r.forest(ForestKind.BIRCH, 0.55, 180, 30, 260, 40, 270, 115, 190, 110);
        r.forest(ForestKind.MIXED, 0.6, -390, 320, -300, 335, -300, 400, -400, 395);
        r.forest(ForestKind.BIRCH, 0.5, -300, -125, -150, -125, -150, -80, -300, -80);
        r.forest(ForestKind.DEAD, 0.4, -170, -260, -70, -260, -70, -150, -170, -150);
        r.forest(ForestKind.MIXED, 0.55, -650, 520, -400, 540, -380, 600, -650, 600);
        r.ground(Ground.FIELD, 150, 200, 420, 170, 450, 420, 180, 450);
        r.ground(Ground.FIELD, 180, -400, 290, -420, 300, -330, 180, -330);
        r.ground(Ground.FIELD, -160, 330, -60, 330, -60, 440, -160, 440);

        // ---------------- объекты
        // юг: периметр и военные
        r.put("kpp", 40, 484, 0, 84, 46);
        r.put("military_camp", 150, 525, 0, 64, 40);
        for (int x = -640; x <= 640; x += 80) if (Math.abs(x - 40) > 40) r.put("fence_segment", x, 505, 0, 0, 0);
        r.put("watchtower", -150, 495, 0, 8, 8);
        r.put("watchtower", 300, 495, 0, 8, 8);
        // деревня новичков
        int[][] houses = {{-180, 245, 180}, {-215, 298, 0}, {-268, 238, 180}, {-285, 296, 0}, {-345, 228, 180}, {-372, 282, 0}, {-205, 345, 0}, {-160, 300, 90}};
        for (int[] h : houses) r.put("rookie_house", h[0], h[1], h[2], 26, 30);
        r.put("sidorovich_bunker", -418, 312, 90, 32, 36);
        r.put("village_fire", -250, 270, 0, 12, 12);
        r.put("watchtower", -140, 220, 0, 8, 8);
        r.put("watchtower", -455, 205, 0, 8, 8);
        r.put("hut", -560, 195, 270, 12, 10);
        // середина: мост, тоннель, лагерь у моста
        r.put("rail_bridge", 90, -25, 0, 0, 0);
        r.put("rail_tunnel", 455, -11, 0, 0, 0);
        r.put("tent_camp", 55, -78, 0, 16, 14);
        r.put("tent_camp", 445, 40, 0, 14, 12);
        r.put("tent_camp", -95, -170, 0, 14, 12);
        // север: АТП, элеватор, застава
        r.put("atp", -380, -310, 0, 130, 102);
        r.put("farm_elevator", 335, -300, 0, 122, 98);
        r.put("road_post", 106, -560, 0, 46, 32);
        // ЛЭП через север Кордона
        for (int i = 0; i <= 10; i++) r.put("pylon", -640 + i * 128, -420 + i * 4, 0, 0, 0);
        // брошенные машины и хлам
        int[][] cars = {{60, 120, 0}, {30, 410, 90}, {120, -380, 0}, {-60, 280, 90}, {200, 230, 90}, {-330, -230, 0}, {105, -500, 0}, {-520, 215, 90}, {350, 175, 90}};
        for (int[] c : cars) r.put("car_wreck", c[0], c[1], c[2], 0, 0);
        r.put("barrels", 20, 140, 0, 0, 0).put("barrels", -300, 330, 0, 0, 0).put("barrels", 470, 60, 0, 0, 0);
        r.put("lair_den", 520, -385, 0, 0, 0);
        r.put("lair_den", 525, 425, 0, 0, 0);

        // вагоны на путях к востоку от моста, сгоревшие хутора, остановка, столбы вдоль трассы, стога, сараи
        r.put("rail_wagons", 240, -17, 0, 0, 0);
        r.put("rail_wagons", -470, -52, 0, 0, 0);
        r.put("ruin_house", -150, -335, 0, 13, 11).put("ruin_house", -130, -360, 90, 13, 11).put("ruin_house", -175, -365, 180, 13, 11);
        r.put("ruin_house", 410, 255, 0, 13, 11).put("ruin_house", 440, 230, 270, 13, 11);
        r.put("ruin_house", 250, -500, 90, 13, 11);
        r.put("bus_stop", 58, 312, 270, 8, 6);
        r.put("bus_stop", 118, -190, 90, 8, 6);
        for (int z = 440; z >= -560; z -= 45) r.put("wood_pole", 60 + (int) Math.round(z * -0.03), z, 0, 0, 0);
        for (int x = -400; x <= -60; x += 45) r.put("wood_pole", x, 252 + (int) Math.round((x + 60) * -0.05), 0, 0, 0);
        int[][] hay = {{220, 260}, {300, 300}, {360, 380}, {250, 400}, {200, -370}, {270, -360}, {-120, 380}};
        for (int[] h : hay) r.put("haystack", h[0], h[1], 0, 0, 0);
        r.put("shed", -235, 335, 0, 8, 7).put("shed", -305, 205, 180, 8, 7).put("shed", -390, 250, 90, 8, 7);
        r.put("well", -240, 262, 0, 4, 4);
        r.put("stalker_tents", -300, 270, 0, 14, 10);

        // ---------------- смарт-террены (как в Call of Chernobyl)
        r.smart("esc_village", "Деревня новичков", StType.BASE, -270, 275, 110, "loners", 20);
        r.smart("esc_kpp", "Блокпост военных", StType.BASE, 80, 495, 90, "military", 14);
        r.smart("esc_bridge", "Мост", StType.CHECKPOINT, 90, -25, 35, "military", 6);
        r.smart("esc_atp", "АТП", StType.OUTPOST, -380, -310, 60, "bandits", 12);
        r.smart("esc_elevator", "Элеватор", StType.OUTPOST, 335, -300, 60, null, 12);
        r.smart("esc_north_post", "Застава у Свалки", StType.CHECKPOINT, 106, -560, 30, null, 6);
        r.smart("esc_tunnel", "Тоннель с электрами", StType.RESOURCE, 455, -11, 40, null, 4);
        r.smart("esc_hill", "Аномальный холм", StType.RESOURCE, -120, -205, 40, null, 4);
        r.smart("esc_bridge_camp", "Лагерь у моста", StType.TERRITORY, 55, -78, 25, null, 6);
        r.smart("esc_farmstead", "Хутор у болот", StType.TERRITORY, -560, 195, 25, null, 4);
        r.smart("esc_lair_dogs", "Логово слепых псов", StType.LAIR, 520, -385, 40, null, 0);
        r.smart("esc_lair_boars", "Логово кабанов", StType.LAIR, 525, 425, 40, null, 0);
    }
}
