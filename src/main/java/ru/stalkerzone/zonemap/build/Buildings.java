package ru.stalkerzone.zonemap.build;

import static ru.stalkerzone.zonemap.build.Structures.*;

/**
 * Крупные прорисованные постройки Кордона с уровнями развития (как здания MineColonies):
 * <ul>
 *   <li>0 — руины: проломы, провалившаяся крыша, бурьян, мусор, пусто;</li>
 *   <li>1 — лагерь: дыры затянуты брезентом, окна заколочены, лежаки, костёр в бочке, мешки у входа;</li>
 *   <li>2 — обжитой: крыша и окна целые, свет, генератор, мастерская, огород, починенный забор;</li>
 *   <li>3 — укреплённый: решётки на окнах, пулемётные гнёзда, колючка, вышки, прожекторы.</li>
 * </ul>
 * Уровень и износ берутся из {@link B#lvl}/{@link B#wear}; всё детерминировано по зерну объекта.
 */
final class Buildings {
    private Buildings() {}

    static final String SANDSLAB = "minecraft:smooth_sandstone_slab[type=bottom]", TARP = "minecraft:brown_wool", CLOTH = "minecraft:green_wool",
            WHITE_BRICK = "minecraft:white_terracotta", SEA = "minecraft:sea_lantern", HANG = "minecraft:lantern[hanging=true]",
            CRATE = "stalkerzone:wooden_crate", WATER = "minecraft:water", GRASSY = "minecraft:grass", FERN = "minecraft:fern",
            BUSH = "minecraft:dead_bush", WEB = "minecraft:cobweb", MOSSC = "minecraft:moss_carpet", GRAVEL = "minecraft:gravel",
            CSLAB = "minecraft:cobblestone_slab[type=bottom]", ASPH = "minecraft:black_concrete";

    static final String WASH = "stalkerzone:whitewash", WASH_D = "stalkerzone:whitewash_damaged", SLATE = "stalkerzone:slate_roof",
            PO2 = "stalkerzone:concrete_fence", WCRATE = "stalkerzone:wooden_crate", MCRATE = "stalkerzone:military_crate",
            MEDCRATE = "stalkerzone:medical_crate", RADBARREL = "stalkerzone:radioactive_barrel";

    // ================================================================= общие детали

    /** Окно в стене: стекло в два яруса, голубые ставни, подоконник, наличник. side — куда смотрит стена. */
    static void window(B b, int x, int z, String side, int L) {
        int dx = side.equals("east") ? 1 : side.equals("west") ? -1 : 0, dz = side.equals("south") ? 1 : side.equals("north") ? -1 : 0;
        String glass = L >= 3 ? BARS : PANE;
        b.set(x, 2, z, glass);
        b.set(x, 3, z, glass);
        int ox = x + dx, oz = z + dz;
        // ставни по сторонам окна; у заброшенных половина сорвана, в лагере окна забиты
        int sx = dz != 0 ? 1 : 0, sz = dx != 0 ? 1 : 0;
        for (int sgn : new int[] {-1, 1}) {
            if (L == 0 && b.chance(0.5)) continue;
            for (int y = 2; y <= 3; y++) b.put(ox + sx * sgn, y, oz + sz * sgn, "minecraft:spruce_trapdoor[facing=" + side + ",half=bottom,open=true]");
        }
        if (L == 1) { b.put(ox, 2, oz, "minecraft:spruce_trapdoor[facing=" + side + ",half=bottom,open=true]"); }
        b.put(ox, 1, oz, "minecraft:smooth_stone_slab[type=top]");
        b.put(ox, 4, oz, "minecraft:spruce_slab[type=bottom]");
    }

    /** Мусор на полу: щебень, обломки, грязь (без износа). */
    static void rubble(B b, int x1, int z1, int x2, int z2, int y, double dens) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            if (!b.chance(dens)) continue;
            double v = b.r.nextDouble();
            b.put(x, y, z, v < 0.35 ? CSLAB : v < 0.55 ? GRAVEL : v < 0.7 ? "minecraft:stone_brick_slab[type=bottom]" : v < 0.85 ? MOSSC : WEB);
        }
    }

    /** Бурьян и папоротник. */
    static void weeds(B b, int x1, int z1, int x2, int z2, int y, double dens) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
            if (b.chance(dens)) b.put(x, y, z, b.chance(0.5) ? GRASSY : b.chance(0.5) ? FERN : BUSH);
    }

    /** Ряд мешков с песком высотой h (верх полублоком). */
    static void sandbags(B b, int x1, int z1, int x2, int z2, int y, int h) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
            for (int k = 0; k < h; k++) b.put(x, y + k, z, SAND);
            b.put(x, y + h, z, SANDSLAB);
        }
    }

    /** Пулемётное гнездо: подкова из мешков, внутри ящик и «пулемёт». */
    static void mgNest(B b, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2 || dz == 2 && Math.abs(dx) < 2) continue;
            b.put(cx + dx, y, cz + dz, SAND);
            b.put(cx + dx, y + 1, cz + dz, dz == -2 && dx == 0 ? AIR : SANDSLAB);
        }
        b.put(cx, y, cz - 1, "minecraft:dispenser[facing=north]");
        b.put(cx + 1, y, cz + 1, CRATE);
    }

    /** Прожектор на треноге. */
    static void searchlight(B b, int x, int y, int z) {
        b.put(x, y, z, BARS);
        b.put(x, y + 1, z, "minecraft:iron_block");
        b.put(x, y + 2, z, SEA);
        b.put(x, y + 3, z, "minecraft:iron_trapdoor[facing=north,half=bottom,open=false]");
    }

    /** Дизель-генератор с выхлопной трубой и бочками топлива. */
    static void genset(B b, int x, int y, int z) {
        b.put(x, y, z, "minecraft:iron_block");
        b.put(x + 1, y, z, "minecraft:blast_furnace[facing=south,lit=true]");
        b.put(x, y + 1, z, "minecraft:smooth_stone_slab[type=bottom]");
        b.put(x + 1, y + 1, z, "minecraft:lightning_rod");
        b.put(x + 2, y, z, BARREL);
        b.put(x + 2, y, z + 1, BARREL);
    }

    /** Колючая проволока: спираль из решёток и паутины. */
    static void wire(B b, int x1, int z1, int x2, int z2) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= n; i++) {
            int x = x1 + (n == 0 ? 0 : (x2 - x1) * i / n), z = z1 + (n == 0 ? 0 : (z2 - z1) * i / n);
            b.setG(x, 0, z, i % 3 == 0 ? BARS : WEB);
        }
    }

    /** Двускатная крыша, конёк вдоль x. skipX — диапазон провала (для руин), patch — затянут брезентом. */
    static void gable(B b, int x0, int x1, int z0, int z1, int y, String mat, String gableWall, int skipFrom, int skipTo, boolean patch) {
        int w = z1 - z0;
        for (int k = 0; k <= w / 2 + 1; k++) {
            int zn = z0 - 1 + k, zs = z1 + 1 - k;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                boolean hole = x >= skipFrom && x <= skipTo && k < w / 2 + 1 && (k > 0 || b.chance(0.5));
                if (hole) {
                    if (patch) { b.put(x, y + k, zn, TARP); if (zn != zs) b.put(x, y + k, zs, TARP); }
                    continue;
                }
                String id = mat.contains(":") ? mat : "minecraft:" + mat;
                if (zn < zs) {
                    b.set(x, y + k, zn, id + "_stairs[facing=south,half=bottom]");
                    b.set(x, y + k, zs, id + "_stairs[facing=north,half=bottom]");
                } else if (zn == zs) b.set(x, y + k, zn, id + "_slab[type=bottom]");
            }
            if (gableWall != null) for (int z = zn + 1; z <= zs - 1; z++) { b.set(x0, y + k, z, gableWall); b.set(x1, y + k, z, gableWall); }
        }
    }

    // ================================================================= деревенский дом

    /**
     * Сельский дом Кордона 15×11: кирпичный цоколь, бревенчатые стены, две комнаты с печью, чердак,
     * застеклённая веранда, крыльцо, двор с огородом, дровяником, туалетом и забором.
     */
    static void villageHouse(B b) {
        int L = b.lvl;
        int x0 = -7, x1 = 7, z0 = -5, z1 = 5;
        // фундамент и цоколь
        b.foundation(x0, z0, x1, z1, 4, COBBLE);
        b.fill(x0, 0, z0, x1, 0, z1, BRICKS);
        b.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, PLANKS);
        // стены: побелённая штукатурка по кирпичу (как сельские хаты Кордона), у брошенных она облезла до кирпича
        double peel = L == 0 ? 0.45 : L == 1 ? 0.25 : 0.08;
        for (int y = 1; y <= 4; y++) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                b.set(x, y, z, corner ? "minecraft:stone_bricks" : b.h(x, y * 3, z) < peel ? WASH_D : WASH);
            }
        }
        // цокольный пояс и карниз-полублоки по периметру
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z : new int[] {z0 - 1, z1 + 1}) b.put(x, 0, z, "minecraft:stone_brick_slab[type=bottom]");
        for (int z = z0; z <= z1; z++) for (int x : new int[] {x0 - 1, x1 + 1}) b.put(x, 0, z, "minecraft:stone_brick_slab[type=bottom]");
        b.clear(x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1);
        // перегородка: кухня (запад) и горница (восток)
        for (int y = 1; y <= 4; y++) for (int z = z0 + 1; z <= z1 - 1; z++) if (z != 1 || y > 2) b.set(-1, y, z, WASH);
        // потолок-чердак
        b.fill(x0 + 1, 5, z0 + 1, x1 - 1, 5, z1 - 1, "minecraft:spruce_slab[type=bottom]");
        // окна: рама, голубые ставни по бокам, подоконник-полублок, наличник сверху
        for (int x : new int[] {x0 + 2, x1 - 5, x1 - 2})
            for (int z : new int[] {z0, z1}) {
                if (z == z1 && x < 0) continue; // южная стена кухни выходит на веранду
                window(b, x, z, z == z0 ? "north" : "south", L);
            }
        for (int z : new int[] {-2, 2}) { window(b, x0, z, "west", L); window(b, x1, z, "east", L); }
        // дверь из сеней на веранду
        b.set(-4, 1, z1, AIR); b.set(-4, 2, z1, AIR);
        if (L >= 2) {
            b.put(-4, 1, z1, "minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false]");
            b.put(-4, 2, z1, "minecraft:spruce_door[facing=south,half=upper,hinge=left,open=false]");
        }
        // застеклённая веранда x -7..-1, z 6..9
        int vx0 = x0, vx1 = -1, vz0 = z1, vz1 = z1 + 4;
        b.foundation(vx0, vz0 + 1, vx1, vz1, 3, COBBLE);
        b.fill(vx0, 0, vz0 + 1, vx1, 0, vz1, PLANKS);
        for (int x = vx0; x <= vx1; x++) for (int z = vz0 + 1; z <= vz1; z++) {
            if (x != vx0 && x != vx1 && z != vz1) continue;
            b.set(x, 1, z, PLANKS);
            boolean post = x == vx0 || x == vx1 || (x - vx0) % 3 == 0;
            b.set(x, 2, z, post ? LOG : L >= 3 ? "minecraft:iron_trapdoor[facing=south,half=bottom,open=true]" : PANE);
            b.set(x, 3, z, post ? LOG : PANE);
        }
        b.set(-3, 1, vz1, AIR); b.set(-3, 2, vz1, AIR); b.set(-3, 3, vz1, AIR);
        for (int x = vx0 - 1; x <= vx1 + 1; x++) for (int z = vz0 + 1; z <= vz1 + 1; z++)
            b.set(x, 4, z, SLATE + "_stairs[facing=north,half=bottom]");
        // крыльцо
        b.put(-3, 0, vz1 + 1, "minecraft:spruce_stairs[facing=north,half=bottom]");
        b.put(-3, -1, vz1 + 1, COBBLE);
        // основная крыша; на 0-м уровне провал над горницей, на 1-м он затянут брезентом
        // шиферная кровля с дощатыми фронтонами
        gable(b, x0, x1, z0, z1, 5, SLATE, PLANKS, L <= 1 ? 1 : 99, L <= 1 ? 5 : -99, L == 1);
        // печная труба и (если живут) дым
        b.fill(-3, 1, -2, -2, 9, -2, BRICKS);
        if (L >= 1) b.put(-3, 10, -2, CAMPFIRE.replace("lit=true", "lit=true,signal_fire=false"));
        // ----------------------------------------------------------- интерьер
        if (L == 0) {
            rubble(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.25);
            weeds(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.1);
            b.put(-5, 1, -4, "minecraft:furnace[facing=south]");
        } else {
            // кухня: печь, стол, лавка, бочка с водой
            b.put(-4, 1, -4, "minecraft:furnace[facing=south,lit=" + (L >= 2) + "]");
            b.put(-5, 1, -4, "minecraft:smoker[facing=south]");
            b.put(-6, 1, 0, "minecraft:spruce_fence");
            b.put(-6, 2, 0, "minecraft:spruce_pressure_plate");
            b.put(-6, 1, 1, stairs("spruce", "west"));
            b.put(-6, 1, -1, stairs("spruce", "west"));
            b.put(-2, 1, 4, CRATE);
            // горница: лежаки / кровати, стол со «станцией»
            for (int z : new int[] {-4, -3}) b.put(6, 1, z, L >= 2 ? "minecraft:white_wool" : TARP);
            for (int z : new int[] {3, 4}) b.put(6, 1, z, L >= 2 ? "minecraft:white_wool" : TARP);
            if (L == 1) { b.put(2, 1, 0, CAMPFIRE.replace("lit=true", "lit=false")); rubble(b, 0, -4, 6, 4, 1, 0.05); }
            if (L >= 2) {
                b.put(3, 1, -4, "minecraft:crafting_table");
                b.put(4, 1, -4, "minecraft:cartography_table");
                b.put(2, 1, 4, "minecraft:chest[facing=north]");
                b.put(3, 4, 0, HANG);
                b.put(-4, 4, 0, HANG);
                b.put(0, 1, -4, "minecraft:note_block"); // радиоприёмник
            }
        }
        // ----------------------------------------------------------- двор
        int fx0 = x0 - 4, fx1 = x1 + 4, fz0 = z0 - 9, fz1 = vz1 + 4;
        double fenceKeep = L == 0 ? 0.45 : L == 1 ? 0.7 : 1.0;
        for (int x = fx0; x <= fx1; x++) for (int z : new int[] {fz0, fz1}) {
            if (z == fz1 && x >= -4 && x <= -2) continue; // калитка
            if (b.chance(fenceKeep)) { b.setG(x, 0, z, FENCE); if (L >= 3 && x % 2 == 0) b.setG(x, 1, z, BARS); }
        }
        for (int z = fz0; z <= fz1; z++) for (int x : new int[] {fx0, fx1})
            if (b.chance(fenceKeep)) { b.setG(x, 0, z, FENCE); if (L >= 3 && z % 2 == 0) b.setG(x, 1, z, BARS); }
        // огород за домом
        for (int x = x0; x <= x1; x++) for (int z = fz0 + 2; z <= z0 - 3; z++) {
            if (L >= 2) {
                if ((z & 1) == 0) { b.setG(x, -1, z, "minecraft:farmland[moisture=3]"); b.setG(x, 0, z, b.chance(0.5) ? "minecraft:potatoes[age=7]" : "minecraft:carrots[age=5]"); }
                else b.setG(x, -1, z, COARSE);
            } else if (b.chance(L == 0 ? 0.25 : 0.4)) { b.setG(x, -1, z, COARSE); if (L == 0 && b.chance(0.5)) b.setG(x, 0, z, GRASSY); }
        }
        // дровяник у забора и уличный туалет
        B shed = b.sub(fx1 - 3, fz0 + 3, 0);
        shed.walls(-1, 0, -1, 1, 2, 1, DPLANKS);
        shed.clear(0, 0, 0, 0, 1, 0);
        shed.set(0, 0, 1, AIR); shed.set(0, 1, 1, AIR);
        shed.fill(-2, 3, -2, 2, 3, 2, "minecraft:dark_oak_slab[type=bottom]");
        for (int z = -1; z <= 2; z++) { b.setG(fx1 - 1, 0, fz0 + 7 + z, logZ()); if (z < 2) b.setG(fx1 - 1, 1, fz0 + 7 + z, logZ()); }
        B wc = b.sub(fx0 + 2, fz0 + 2, 0);
        wc.walls(0, 0, 0, 1, 2, 1, PLANKS);
        wc.set(0, 0, 1, AIR); wc.set(0, 1, 1, AIR);
        wc.fill(0, 3, 0, 1, 3, 1, "minecraft:spruce_slab[type=bottom]");
        // хозяйство по уровням
        if (L == 0) {
            weeds(b, fx0 + 1, fz0 + 1, fx1 - 1, fz1 - 1, 0, 0.12);
            Structures.car(b.sub(fx1 - 4, fz1 - 3, 0), 0, 0, 0, 0);
        }
        if (L == 1) {
            b.put(4, 0, z1 + 3, BARREL);
            b.put(4, 1, z1 + 3, CAMPFIRE);
            b.put(2, 0, z1 + 3, logX());
            b.put(6, 0, z1 + 3, logX());
            sandbags(b, -5, vz1 + 2, -4, vz1 + 2, 0, 1);
            b.put(5, 0, z1 + 6, CRATE);
        }
        if (L >= 2) {
            genset(b, x1 + 1, 0, 1);
            // бельевая верёвка и лавочка у калитки
            b.put(0, 0, z1 + 4, FENCE); b.put(0, 1, z1 + 4, FENCE);
            b.put(5, 0, z1 + 4, FENCE); b.put(5, 1, z1 + 4, FENCE);
            for (int x = 1; x <= 4; x++) b.put(x, 2, z1 + 4, "minecraft:chain[axis=x]");
            b.put(0, 0, fz1 - 1, stairs("spruce", "north"));
            b.put(1, 0, fz1 - 1, stairs("spruce", "north"));
            b.put(-7, 6, 0, "minecraft:lightning_rod"); // антенна
            b.put(-7, 7, 0, "minecraft:lightning_rod");
            b.put(fx1 - 2, 0, fz1 - 2, "minecraft:composter[level=4]");
        }
        if (L >= 3) {
            mgNest(b, fx0 + 3, 0, fz1 - 3);
            mgNest(b, fx1 - 3, 0, fz1 - 3);
            // наблюдательный пост на чердаке у трубы
            // наблюдательный пост на коньке крыши
            b.fill(0, 12, -1, 3, 12, 1, PLANKS);
            sandbags(b, 0, -1, 3, -1, 13, 0);
            sandbags(b, 0, 1, 3, 1, 13, 0);
            searchlight(b, 3, 13, 0);
            wire(b, fx0 - 2, fz1 + 2, fx1 + 2, fz1 + 2);
        }
    }

    // ================================================================= бункер Сидоровича

    /** Бункер торговца 21×17 под земляным холмом: тамбур, торговый зал с прилавком, склад, генераторная. */
    static void traderBunker(B b) {
        int L = b.lvl;
        int x0 = -10, x1 = 10, z0 = -8, z1 = 8, fl = -6;
        double w = b.wear;
        b.wear = 0;
        // насыпь-холм
        for (int x = x0 - 9; x <= x1 + 9; x++) for (int z = z0 - 9; z <= z1 + 6; z++) {
            double ddx = Math.max(0, Math.abs(x) - 7), ddz = Math.max(0, Math.abs(z) - 5);
            int h = (int) Math.round(4.2 - Math.hypot(ddx, ddz) * 0.55 + (b.h(x, 7, z) - 0.5) * 0.8);
            if (h < 0) continue;
            for (int y = 0; y <= h; y++) b.put(x, y, z, y == h ? GRASS : DIRT);
            if (h >= 0 && L == 0 && b.chance(0.1)) b.put(x, h + 1, z, GRASSY);
        }
        b.wear = w;
        // коробка
        b.fill(x0, fl - 1, z0, x1, 0, z1, GCONCRETE);
        b.fill(x0 + 1, fl, z0 + 1, x1 - 1, -1, z1 - 1, AIR);
        b.fill(x0 + 1, fl - 1, z0 + 1, x1 - 1, fl - 1, z1 - 1, "minecraft:polished_andesite");
        // внутренние стены: тамбур в центре юга, склад запад, генераторная восток
        for (int y = fl; y <= -1; y++) {
            for (int x = x0 + 1; x <= x1 - 1; x++) if (Math.abs(x) > 1 || y > fl + 2) b.set(x, y, 3, STONEB);
            for (int z = 4; z <= z1 - 1; z++) { b.set(-3, y, z, STONEB); b.set(3, y, z, STONEB); }
        }
        // проходы в склад и генераторную из зала
        for (int y = fl; y <= fl + 1; y++) { b.set(-6, y, 3, AIR); b.set(6, y, 3, AIR); }
        // вход: лестница с юга и бетонный портал
        for (int k = 0; k <= 6; k++) {
            int zz = z1 + 6 - k;
            b.fill(-1, -k, zz, 1, 2, zz, AIR);
            b.fill(-1, -k - 1, zz, 1, -k - 1, zz, "minecraft:stone_brick_stairs[facing=north,half=bottom]");
            b.fill(-2, -k - 1, zz, -2, 1, zz, GCONCRETE);
            b.fill(2, -k - 1, zz, 2, 1, zz, GCONCRETE);
            if (k >= 2) b.fill(-1, 2 - (k >= 4 ? 0 : 0), zz, 1, 2, zz, GCONCRETE);
        }
        b.fill(-1, fl, z1, 1, fl + 2, z1, AIR);
        // козырёк
        b.fill(-3, 3, z1 + 1, 3, 3, z1 + 5, SLAB);
        b.fill(-3, 2, z1 + 1, -3, 2, z1 + 5, GCONCRETE);
        b.fill(3, 2, z1 + 1, 3, 2, z1 + 5, GCONCRETE);
        // дверь
        if (L >= 1) {
            b.put(0, fl, z1, "minecraft:iron_door[facing=north,half=lower,hinge=left,open=true]");
            b.put(0, fl + 1, z1, "minecraft:iron_door[facing=north,half=upper,hinge=left,open=true]");
        } else {
            b.put(-1, fl, z1, "minecraft:iron_door[facing=east,half=lower,hinge=left,open=true]"); // сорвана, прислонена
            b.put(-1, fl + 1, z1, "minecraft:iron_door[facing=east,half=upper,hinge=left,open=true]");
        }
        // вентиляция и антенна на холме
        b.fill(x1 - 2, 1, z0 + 2, x1 - 2, 5, z0 + 2, "minecraft:iron_bars");
        b.put(x1 - 2, 6, z0 + 2, "minecraft:iron_trapdoor[facing=north,half=bottom,open=false]");
        b.fill(x0 + 2, 1, z0 + 2, x0 + 2, 4, z0 + 2, "minecraft:iron_bars");
        if (L >= 1) b.fill(x0 + 3, 4, z0 + 3, x0 + 3, 10, z0 + 3, "minecraft:lightning_rod");
        // ----------------------------------------------------------- внутри
        if (L == 0) {
            for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z1 - 1; z++) if (b.chance(0.35)) b.put(x, fl, z, WATER);
            rubble(b, x0 + 1, z0 + 1, x1 - 1, z1 - 1, fl, 0.15);
            // ступени частично засыпаны
            for (int k = 0; k < 3; k++) b.put(b.r.nextInt(3) - 1, -k - 1 + 1, z1 + 6 - k - 1, GRAVEL);
            return;
        }
        // зал: прилавок из досок, за ним полки
        for (int x = -6; x <= 6; x++) b.set(x, fl, -2, x == 6 ? AIR : "minecraft:spruce_planks");
        for (int x = -6; x <= 5; x++) b.put(x, fl + 1, -2, "minecraft:spruce_slab[type=bottom]");
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            b.put(x, fl, z0 + 1, CRATE);
            if (L >= 2 || b.chance(0.4)) b.put(x, fl + 1, z0 + 1, (x & 1) == 0 ? BARREL : CRATE);
        }
        b.put(-3, fl, -5, "minecraft:lectern[facing=south]"); // стол Сидоровича с бумагами
        b.put(-2, fl, -5, stairs("spruce", "north"));
        b.put(0, -1, 0, HANG);
        if (L >= 2) {
            b.put(-6, -1, -4, SEA); b.put(6, -1, -4, SEA); b.put(0, -1, 5, SEA);
            b.put(4, fl, -5, "minecraft:anvil[facing=north]");
            b.put(5, fl, -5, "minecraft:smithing_table");
            b.put(-9, fl, 0, "minecraft:note_block");
            // склад
            for (int x = -9; x <= -4; x++) for (int z = 4; z <= 7; z++) if ((x + z) % 2 == 0) { b.put(x, fl, z, CRATE); if (b.chance(0.5)) b.put(x, fl + 1, z, CRATE); }
            // генераторная
            genset(b, 5, fl, 5);
            b.put(8, fl, 7, "minecraft:lever[face=wall,facing=north,powered=true]");
        } else {
            for (int x = -9; x <= -4; x++) if (b.chance(0.5)) b.put(x, fl, 7, CRATE);
            rubble(b, 4, 4, 9, 7, fl, 0.2);
        }
        // ----------------------------------------------------------- снаружи
        if (L >= 2) {
            b.put(0, 2, z1 + 5, HANG.replace("hanging=true", "hanging=true"));
            b.put(-2, 2, z1 + 6, "minecraft:lantern[hanging=false]");
            for (int x = 4; x <= 7; x++) b.put(x, 0, z1 + 5, x == 4 ? "minecraft:spruce_fence" : "minecraft:spruce_slab[type=bottom]");
        }
        if (L >= 3) {
            mgNest(b, 0, 4, -1);
            searchlight(b, 6, 4, -3);
            // сетка-рабица вокруг входа с воротами
            for (int x = -8; x <= 8; x++) if (Math.abs(x) > 2) { b.setG(x, 0, z1 + 12, BARS); b.setG(x, 1, z1 + 12, BARS); }
            for (int z = z1 + 4; z <= z1 + 12; z++) for (int x : new int[] {-8, 8}) { b.setG(x, 0, z, BARS); b.setG(x, 1, z, BARS); }
            sandbags(b, -2, z1 + 10, 2, z1 + 10, 0, 1);
            b.put(-2, 0, z1 + 13, "minecraft:polished_andesite"); b.put(2, 0, z1 + 13, "minecraft:polished_andesite");
        }
    }

    // ================================================================= КПП военных

    /** Блокпост на трассе 81×45: шлагбаум, будки, казарма, вышки, ДОТ, змейка из блоков, колючка. Дорога вдоль z. */
    static void checkpoint(B b) {
        int L = b.lvl;
        // бетонная площадка и асфальт дороги
        for (int x = -40; x <= 40; x++) for (int z = -20; z <= 22; z++) {
            boolean road = Math.abs(x) <= 4;
            if (road) b.put(x, -1, z, b.chance(0.08) ? GRAVEL : ASPH);
            else if (Math.abs(x) <= 26 && b.chance(L == 0 ? 0.6 : 0.92)) b.put(x, -1, z, b.chance(0.12) ? CRACKED : SMOOTH);
            if (road && x == 0 && (z & 3) < 2) b.put(x, -1, z, "minecraft:white_concrete");
        }
        // ворота периметра на южной кромке (линия забора Зоны): столбы и балка с колючкой
        int G = 21;
        for (int x : new int[] {-6, 6}) b.fill(x, 0, G, x, 6, G, GCONCRETE);
        for (int x = -6; x <= 6; x++) b.set(x, 6, G, x % 2 == 0 ? GCONCRETE : BARS);
        for (int x = -5; x <= 5; x++) b.put(x, 5, G, (x & 1) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete");
        // бетонный забор периметра от ворот в стороны
        for (int x = -40; x <= 40; x++) {
            if (Math.abs(x) <= 6) continue;
            boolean breach = L == 0 && Math.floorMod(x * 7, 11) == 0;
            if (breach) continue;
            for (int y = 0; y <= 3; y++) b.setG(x, y, G, y == 3 ? (x % 4 == 0 ? "minecraft:stone_brick_wall" : BARS) : x % 4 == 0 ? GCONCRETE : PO2);
        }
        // шлагбаум (на нулевом — сломан и лежит)
        b.fill(-6, 0, 4, -6, 1, 4, "minecraft:polished_andesite");
        b.fill(6, 0, 4, 6, 1, 4, "minecraft:polished_andesite");
        for (int x = -5; x <= 5; x++) {
            String c = (x & 1) == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete";
            if (L == 0) { if (x < 2) b.put(x, 0, 6 + (x + 5) / 4, c); }
            else b.set(x, 1, 4, c);
        }
        // будка у шлагбаума (запад)
        b.fill(-14, 0, -3, -8, 0, 3, CONCRETE);
        b.walls(-14, 1, -3, -8, 3, 3, CONCRETE);
        b.clear(-13, 1, -2, -9, 3, 2);
        b.fill(-15, 4, -4, -7, 4, 4, SLAB);
        for (int z : new int[] {-1, 1}) b.set(-8, 2, z, L >= 3 ? BARS : PANE);
        b.set(-14, 2, 0, PANE);
        b.set(-11, 1, 3, AIR); b.set(-11, 2, 3, AIR);
        if (L >= 1) { b.put(-12, 1, -2, "minecraft:cartography_table"); b.put(-10, 1, -2, stairs("spruce", "north")); }
        if (L >= 2) { b.put(-11, 3, 0, HANG); b.put(-14, 5, -3, SEA); }
        // змейка из бетонных блоков
        for (int[] c : new int[][] {{-5, -8}, {-3, -8}, {2, -4}, {4, -4}, {-5, 9}, {-3, 9}, {2, 12}, {4, 12}, {-5, 15}, {-3, 15}}) {
            if (L == 0 && b.chance(0.4)) { b.put(c[0] + b.r.nextInt(5) - 2, 0, c[1] + b.r.nextInt(3), SLAB); continue; }
            b.fill(c[0], 0, c[1], c[0] + 1, 0, c[1], SMOOTH);
            b.put(c[0], 1, c[1], SLAB); b.put(c[0] + 1, 1, c[1], SLAB);
            for (int x = c[0]; x <= c[0] + 1; x++) b.put(x, 0, c[1] + 1, (x & 1) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete");
        }
        // ----------------------------------------------------------- казарма: два этажа силикатного кирпича
        int bx0 = -34, bx1 = -18, bz0 = -10, bz1 = 14;
        b.foundation(bx0, bz0, bx1, bz1, 3, COBBLE);
        if (L == 0) {
            // остов: стены первого этажа местами
            for (int x = bx0; x <= bx1; x++) for (int z = bz0; z <= bz1; z++) {
                if (x != bx0 && x != bx1 && z != bz0 && z != bz1) continue;
                int h = 1 + (int) (b.h(x, 0, z) * 5);
                for (int y = 0; y < h; y++) b.put(x, y, z, b.chance(0.2) ? "minecraft:terracotta" : WHITE_BRICK);
            }
            rubble(b, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, 0, 0.3);
            weeds(b, bx0 + 1, bz0 + 1, bx1 - 1, bz1 - 1, 0, 0.15);
        } else {
            int top = L >= 2 ? 8 : 4;
            b.walls(bx0, 0, bz0, bx1, top - 1, bz1, WHITE_BRICK);
            b.fill(bx0 + 1, 0, bz0 + 1, bx1 - 1, top - 1, bz1 - 1, AIR);
            b.fill(bx0, 0, bz0, bx1, 0, bz1, WHITE_BRICK);
            b.fill(bx0 + 1, 0, bz0 + 1, bx1 - 1, 0, bz1 - 1, "minecraft:polished_andesite");
            // межэтажное перекрытие и пояс
            if (L >= 2) {
                b.fill(bx0 + 1, 4, bz0 + 1, bx1 - 1, 4, bz1 - 1, SMOOTH);
                for (int k = 0; k < 4; k++) { b.set(bx0 + 2 + k, 1 + k, bz0 + 2, "minecraft:stone_stairs[facing=east,half=bottom]"); b.set(bx0 + 2 + k, 4, bz0 + 2, AIR); }
                for (int x = bx0; x <= bx1; x++) { b.set(x, 4, bz0, "minecraft:white_concrete"); b.set(x, 4, bz1, "minecraft:white_concrete"); }
            }
            b.fill(bx0 - 1, top, bz0 - 1, bx1 + 1, top, bz1 + 1, SLAB);
            for (int x = bx0; x <= bx1; x++) for (int z : new int[] {bz0 - 1, bz1 + 1}) b.set(x, top, z, "minecraft:stone_brick_wall");
            // окна
            for (int z = bz0 + 2; z <= bz1 - 2; z += 3) for (int y : L >= 2 ? new int[] {2, 6} : new int[] {2}) {
                String g = L >= 3 ? BARS : L == 1 ? "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]" : PANE;
                b.set(bx1, y, z, g); b.set(bx0, y, z, g);
            }
            b.set(bx1, 1, 2, AIR); b.set(bx1, 2, 2, AIR);
            b.put(bx1 + 1, 0, 2, "minecraft:stone_stairs[facing=west,half=bottom]");
            // койки
            for (int z = bz0 + 2; z <= bz1 - 2; z += 2) { b.put(bx0 + 1, 1, z, "minecraft:green_wool"); b.put(bx0 + 2, 1, z, "minecraft:green_wool"); }
            if (L >= 2) {
                for (int z = bz0 + 3; z <= bz1 - 2; z += 2) b.put(bx0 + 1, 5, z, "minecraft:green_wool");
                b.put(bx1 - 2, 5, 0, "minecraft:cartography_table"); b.put(bx1 - 2, 5, 1, "minecraft:lectern[facing=west]");
                for (int z = bz0 + 3; z <= bz1 - 3; z += 6) { b.put(-26, 3, z, SEA); b.put(-26, 7, z, SEA); }
                b.fill(bx1 - 1, 1, bz1 - 1, bx1 - 1, 2, bz1 - 1, "minecraft:barrel[facing=up]"); // оружейка
                b.put(bx1 - 2, 1, bz1 - 1, "minecraft:smithing_table");
                b.fill(bx0 + 2, top + 1, bz1 - 2, bx0 + 2, top + 6, bz1 - 2, "minecraft:lightning_rod"); // антенна связи
            } else {
                // лагерь в коробке без второго этажа: брезент вместо крыши
                b.fill(bx0, top, bz0, bx1, top, bz1, TARP);
                b.put(-26, 1, 2, CAMPFIRE);
            }
        }
        // ----------------------------------------------------------- лагерь, техника, вышки
        if (L == 1) {
            sandbags(b, 8, -6, 8, 2, 0, 1);
            sandbags(b, -16, 5, -14, 5, 0, 1);
            Structures.armyTent(b, 16, 8);
            b.put(12, 0, -2, CAMPFIRE);
            for (int i = 0; i < 4; i++) b.put(14 + i, 0, -6, i % 2 == 0 ? CRATE : CLOTH);
        }
        if (L == 0) {
            // сгоревший БТР
            B wreck = b.sub(18, 6, 0);
            Structures.btr(wreck, 0, 0);
            for (int x = -4; x <= 4; x++) for (int z = -1; z <= 1; z++) if (b.chance(0.4)) wreck.put(x, 2, z, "minecraft:coal_block");
            rubble(b, 8, -10, 30, 18, 0, 0.04);
        }
        if (L >= 2) {
            mgNest(b, 11, 0, -4);
            Structures.watchtower(b, 20, 10);
            // УАЗ
            for (int i = -2; i <= 2; i++) for (int j = -1; j <= 1; j++) {
                boolean wheel = Math.abs(i) == 2 && Math.abs(j) == 1;
                b.put(-12 + j, 0, 14 + i, wheel ? ASPH : OLIVE);
                if (i >= -1) b.put(-12 + j, 1, 14 + i, i == 1 ? PANE : OLIVE);
                if (i >= -1 && i <= 0) b.put(-12 + j, 2, 14 + i, CLOTH);
            }
            // флагшток, фонари вдоль дороги, генератор
            b.fill(14, 0, -9, 14, 8, -9, BARS);
            b.put(14, 9, -9, "minecraft:green_banner[rotation=0]");
            for (int z = -8; z <= 16; z += 8) { b.fill(-6, 0, z, -6, 4, z, BARS); b.put(-6, 5, z, SEA); }
            genset(b, -16, 0, -8);
            // бетонные стенки вдоль дороги у ворот
            for (int z = 16; z <= 20; z++) for (int x : new int[] {-7, 7}) { b.setG(x, 0, z, CONCRETE); b.setG(x, 1, z, CONCRETE); }
        }
        if (L >= 3) {
            Structures.watchtower(b, -20, -18);
            Structures.btr(b, 18, -4);
            // ДОТ восточнее дороги: бетонный куб с амбразурами
            b.fill(10, 0, 14, 16, 3, 19, GCONCRETE);
            b.fill(11, 0, 15, 15, 2, 18, AIR);
            for (int x = 11; x <= 15; x += 2) b.set(x, 1, 14, AIR);
            b.put(13, 0, 15, "minecraft:dispenser[facing=north]");
            b.set(16, 0, 17, AIR); b.set(16, 1, 17, AIR);
            b.fill(9, 4, 13, 17, 4, 20, GRASS);
            // гнёзда на крыше казармы, прожекторы
            mgNest(b, -26, 9, -6);
            searchlight(b, -20, 9, 12);
            searchlight(b, 25, 0, -10);
            // ежи и колючка перед воротами (к северу)
            for (int x = -24; x <= 24; x += 6) if (Math.abs(x) > 6) {
                b.setG(x, 0, -16, BARS); b.setG(x + 1, 1, -16, BARS); b.setG(x - 1, 1, -16, BARS); b.setG(x, 1, -16, BARS);
            }
            wire(b, -38, -19, -8, -19);
            wire(b, 8, -19, 38, -19);
        }
    }

    // ================================================================= застава на дороге

    /** Застава 45×31: блоки змейкой, мешки, будка, шлагбаум; на высоком уровне вышка и ДОТ. */
    static void roadPost(B b) {
        int L = b.lvl;
        for (int x = -4; x <= 4; x++) for (int z = -15; z <= 15; z++) if (b.chance(0.4)) b.put(x, -1, z, b.chance(0.5) ? ASPH : GRAVEL);
        for (int[] c : new int[][] {{-6, -8}, {-4, -8}, {2, -2}, {4, -2}, {-6, 6}, {-4, 6}}) {
            if (L == 0 && b.chance(0.5)) { b.put(c[0] - 3 + b.r.nextInt(6), 0, c[1] + 1, SLAB); continue; }
            b.fill(c[0], 0, c[1], c[0] + 1, 0, c[1], SMOOTH);
            b.put(c[0], 1, c[1], SLAB); b.put(c[0] + 1, 1, c[1], SLAB);
        }
        // будка
        B booth = b.sub(-14, 0, 0);
        booth.walls(-3, 0, -3, 3, 3, 3, L >= 2 ? CONCRETE : PLANKS);
        booth.clear(-2, 0, -2, 2, 3, 2);
        booth.set(3, 0, 0, AIR); booth.set(3, 1, 0, AIR);
        booth.set(3, 2, -2, PANE); booth.set(-3, 2, 0, PANE); booth.set(0, 2, 3, PANE);
        booth.fill(-4, 4, -4, 4, 4, 4, L >= 2 ? SLAB : "minecraft:spruce_slab[type=bottom]");
        if (L == 0) rubble(booth, -2, -2, 2, 2, 0, 0.3);
        else { booth.put(-2, 0, -2, "minecraft:cartography_table"); booth.put(-2, 0, 2, CRATE); }
        if (L >= 1) {
            sandbags(b, 9, -5, 9, 4, 0, 1);
            for (int x = -5; x <= 5; x++) b.set(x, 1, 10, (x & 1) == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
            b.fill(-6, 0, 10, -6, 1, 10, "minecraft:polished_andesite");
            b.put(-14, 0, 8, CAMPFIRE);
            b.put(-16, 0, 8, logZ()); b.put(-12, 0, 8, logZ());
            b.put(-15, 0, 11, CRATE);
        }
        if (L == 1) Structures.tentCamp(b.sub(16, 8, 0));
        if (L >= 2) {
            b.fill(-10, 0, -6, -10, 4, -6, BARS); b.put(-10, 5, -6, SEA);
            b.put(-11, 3, 0, HANG);
            genset(b, -19, 0, 5);
            mgNest(b, 13, 0, 0);
        }
        if (L >= 3) {
            Structures.watchtower(b, 16, -10);
            b.fill(10, 0, 9, 15, 3, 13, GCONCRETE);
            b.fill(11, 0, 10, 14, 2, 12, AIR);
            b.set(12, 1, 9, AIR); b.set(13, 1, 9, AIR);
            b.set(15, 0, 11, AIR); b.set(15, 1, 11, AIR);
            b.fill(9, 4, 8, 16, 4, 14, GRASS);
            wire(b, -20, -14, -6, -14);
            wire(b, 6, -14, 20, -14);
            searchlight(b, -8, 0, -12);
        }
        if (L == 0) weeds(b, -20, -14, 20, 14, 0, 0.05);
    }

    // ================================================================= АТП

    /**
     * АТП 125×97: бетонный забор с воротами, девять гаражных боксов, двухэтажная контора, ангар-мастерская,
     * водонапорная башня, заправка. Уровни: руины → лагерь бандитов → рабочая база → крепость с вышками.
     */
    static void atp(B b) {
        int L = b.lvl;
        int X = 62, Z = 48;
        // забор
        for (int x = -X; x <= X; x++) for (int z : new int[] {-Z, Z}) atpFence(b, x, z, z == Z && Math.abs(x) <= 5);
        for (int z = -Z; z <= Z; z++) for (int x : new int[] {-X, X}) atpFence(b, x, z, false);
        // двор
        for (int x = -X + 1; x <= X - 1; x++) for (int z = -Z + 1; z <= Z - 1; z++) {
            double v = b.h(x >> 3, 0, z >> 3);
            if (v < (L >= 2 ? 0.8 : 0.55)) b.put(x, -1, z, b.chance(0.15) ? GRAVEL : b.chance(0.5) ? ASPH : GCONCRETE);
        }
        if (L == 0) weeds(b, -X + 1, -Z + 1, X - 1, Z - 1, 0, 0.06);
        // ворота
        if (L >= 2) for (int x = -5; x <= 5; x++) for (int y = 0; y <= 3; y++) b.set(x, y, Z, x == 0 ? GCONCRETE : (x + y) % 2 == 0 ? "minecraft:iron_block" : BARS);
        if (L >= 2) for (int y = 0; y <= 4; y++) { b.set(-6, y, Z, GCONCRETE); b.set(6, y, Z, GCONCRETE); }
        if (L == 1) { // баррикада из машин у ворот
            Structures.car(b.sub(-3, Z - 3, 0), 0, 0, 0, 0);
            Structures.car(b.sub(4, Z - 4, 90), 0, 0, 0, 2);
            sandbags(b, -9, Z - 6, -6, Z - 6, 0, 1);
        }
        // ----------------------------------------------------------- гаражные боксы (север)
        for (int i = 0; i < 9; i++) {
            int x0 = -56 + i * 10, z0 = -46, z1 = -33;
            boolean collapsed = L == 0 && b.chance(0.4);
            b.walls(x0, 0, z0, x0 + 10, 6, z1, BRICKS);
            b.fill(x0 + 1, 0, z0 + 1, x0 + 9, 5, z1 - 1, AIR);
            b.fill(x0 + 1, -1, z0 + 1, x0 + 9, -1, z1 - 1, GCONCRETE);
            if (!collapsed) b.fill(x0, 7, z0, x0 + 10, 7, z1, SLAB);
            else rubble(b, x0 + 1, z0 + 1, x0 + 9, z1 - 1, 0, 0.4);
            b.fill(x0 + 2, 0, z1, x0 + 8, 4, z1, AIR);
            b.set(x0 + 5, 5, z1, "minecraft:yellow_terracotta"); // номер бокса
            if (L >= 2 && i % 2 == 0) b.fill(x0 + 2, 4, z1, x0 + 8, 4, z1, "minecraft:iron_trapdoor[facing=south,half=top,open=false]");
            if (L == 1 && i % 3 == 1) { b.put(x0 + 3, 0, z0 + 3, TARP); b.put(x0 + 3, 0, z0 + 4, TARP); b.put(x0 + 6, 0, z0 + 6, BARREL); b.put(x0 + 6, 1, z0 + 6, CAMPFIRE); }
            if (i != 5 && b.chance(L >= 2 ? 0.5 : 0.3)) Structures.truck(b, x0 + 5, -40, true);
        }
        // ----------------------------------------------------------- контора: два этажа, лестница, крыльцо
        int ox0 = 26, ox1 = 54, oz0 = -22, oz1 = -6;
        b.foundation(ox0, oz0, ox1, oz1, 3, COBBLE);
        b.walls(ox0, 0, oz0, ox1, 9, oz1, WHITE_BRICK);
        b.fill(ox0 + 1, 0, oz0 + 1, ox1 - 1, 8, oz1 - 1, AIR);
        b.fill(ox0 + 1, -1, oz0 + 1, ox1 - 1, -1, oz1 - 1, "minecraft:polished_andesite");
        b.fill(ox0 + 1, 4, oz0 + 1, ox1 - 1, 4, oz1 - 1, SMOOTH);
        for (int x = ox0; x <= ox1; x++) { b.set(x, 4, oz0, "minecraft:white_concrete"); b.set(x, 4, oz1, "minecraft:white_concrete"); }
        b.fill(ox0 - 1, 10, oz0 - 1, ox1 + 1, 10, oz1 + 1, SLAB);
        for (int x = ox0 - 1; x <= ox1 + 1; x++) for (int z : new int[] {oz0 - 1, oz1 + 1}) b.set(x, 11, z, "minecraft:stone_brick_wall");
        for (int z = oz0 - 1; z <= oz1 + 1; z++) for (int x : new int[] {ox0 - 1, ox1 + 1}) b.set(x, 11, z, "minecraft:stone_brick_wall");
        // лестничная клетка и перегородки
        for (int k = 0; k < 4; k++) { b.set(ox0 + 2 + k, k, oz0 + 2, "minecraft:stone_stairs[facing=east,half=bottom]"); b.set(ox0 + 2 + k, 4, oz0 + 2, AIR); }
        for (int y = 0; y <= 8; y++) for (int z = oz0 + 1; z <= oz1 - 1; z++) {
            if (y == 4 || z == -14 && (y <= 1 || y == 5 || y == 6)) continue;
            b.set(36, y, z, WHITE_BRICK); b.set(45, y, z, WHITE_BRICK);
        }
        // окна лентой
        for (int x = ox0 + 2; x <= ox1 - 2; x += 3) for (int y : new int[] {1, 2, 6, 7}) {
            String g = L >= 3 ? BARS : L == 1 ? (y > 4 ? "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]" : AIR) : PANE;
            b.set(x, y, oz0, g); b.set(x, y, oz1, g);
        }
        b.set(40, 0, oz1, AIR); b.set(40, 1, oz1, AIR); b.set(41, 0, oz1, AIR); b.set(41, 1, oz1, AIR);
        b.fill(38, 3, oz1 + 1, 43, 3, oz1 + 3, SLAB); // козырёк
        b.fill(38, 0, oz1 + 3, 38, 2, oz1 + 3, BARS); b.fill(43, 0, oz1 + 3, 43, 2, oz1 + 3, BARS);
        b.put(37, 8, oz1 + 1, "minecraft:red_terracotta"); // табличка «АТП»
        for (int x = 38; x <= 43; x++) b.put(x, 8, oz1 + 1, (x & 1) == 0 ? "minecraft:white_concrete" : "minecraft:red_concrete");
        if (L == 0) rubble(b, ox0 + 1, oz0 + 1, ox1 - 1, oz1 - 1, 0, 0.18);
        if (L >= 1) { for (int x = 47; x <= 52; x += 2) b.put(x, 0, oz0 + 2, TARP); b.put(50, 0, -12, CAMPFIRE.replace("lit=true", "lit=" + (L == 1))); }
        if (L >= 2) {
            b.put(30, 5, -18, "minecraft:cartography_table"); b.put(31, 5, -18, "minecraft:lectern[facing=north]");
            for (int x = 30; x <= 34; x += 2) b.put(x, 0, -10, "minecraft:chest[facing=north]");
            for (int x = 31; x <= 51; x += 10) { b.put(x, 3, -14, SEA); b.put(x, 8, -14, SEA); }
            genset(b, ox1 + 2, 0, -12);
        }
        // ----------------------------------------------------------- ангар-мастерская
        int hx0 = -56, hx1 = -24, hz0 = 6, hz1 = 40;
        b.walls(hx0, 0, hz0, hx1, 7, hz1, "minecraft:terracotta");
        b.fill(hx0 + 1, 0, hz0 + 1, hx1 - 1, 7, hz1 - 1, AIR);
        b.fill(hx0 + 1, -1, hz0 + 1, hx1 - 1, -1, hz1 - 1, GCONCRETE);
        for (int y = 0; y <= 7; y++) for (int z = hz0; z <= hz1; z += 6) { b.set(hx0, y, z, BRICKS); b.set(hx1, y, z, BRICKS); }
        // полукруглая крыша из ржавого профлиста
        int cw = (hx1 - hx0) / 2, cx = (hx0 + hx1) / 2;
        for (int x = hx0 - 1; x <= hx1 + 1; x++) {
            double t = (x - cx) / (double) (cw + 1);
            int y = 8 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int z = hz0; z <= hz1; z++) {
                if (L == 0 && b.h(x >> 2, 9, z >> 2) < 0.45) continue;
                boolean patchHole = L == 1 && b.h(x >> 2, 9, z >> 2) < 0.25;
                b.put(x, y, z, patchHole ? TARP : (z % 6 == 0 ? IRON : RAWIRON));
            }
        }
        for (int x = hx0 + 1; x <= hx1 - 1; x++) {
            double t = (x - cx) / (double) (cw + 1);
            int top = 8 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int y = 8; y < top; y++) { b.set(x, y, hz0, "minecraft:terracotta"); b.set(x, y, hz1, "minecraft:terracotta"); }
        }
        b.fill(-44, 0, hz1, -36, 6, hz1, AIR); // ворота ангара
        if (L >= 2) { b.fill(-44, 6, hz1, -36, 6, hz1, "minecraft:iron_trapdoor[facing=south,half=top,open=false]"); }
        // смотровая яма и станки
        b.fill(-46, -2, 18, -44, -1, 30, AIR);
        b.fill(-46, -3, 18, -44, -3, 30, GCONCRETE);
        if (L >= 1) Structures.truck(b, -45, 24, true);
        if (L >= 2) {
            b.put(-53, 0, 9, "minecraft:anvil[facing=north]"); b.put(-51, 0, 9, "minecraft:blast_furnace[facing=south,lit=true]");
            b.put(-49, 0, 9, "minecraft:grindstone[face=floor,facing=north]"); b.put(-47, 0, 9, "minecraft:smithing_table");
            for (int z = 12; z <= 36; z += 8) b.put(-40, 12, z, SEA);
            b.fill(-55, 0, 14, -55, 2, 34, "minecraft:barrel[facing=east]");
        } else if (L == 1) {
            Structures.tentCamp(b.sub(-34, 20, 0));
            Structures.tentCamp(b.sub(-34, 32, 180));
        } else rubble(b, hx0 + 1, hz0 + 1, hx1 - 1, hz1 - 1, 0, 0.12);
        // ----------------------------------------------------------- водонапорная башня (Рожновского)
        int tx = 44, tz = 32;
        b.foundation(tx - 2, tz - 2, tx + 2, tz + 2, 2, GCONCRETE);
        for (int y = 0; y <= 18; y++) for (int a = 0; a < 360; a += 30) {
            int x = (int) Math.round(Math.cos(Math.toRadians(a)) * 1.5), z = (int) Math.round(Math.sin(Math.toRadians(a)) * 1.5);
            b.set(tx + x, y, tz + z, y % 6 == 0 ? IRON : RUST);
        }
        for (int y = 19; y <= 25; y++) {
            double rr = y == 19 || y == 25 ? 3.5 : 4.2;
            for (int a = 0; a < 360; a += 10) {
                int x = (int) Math.round(Math.cos(Math.toRadians(a)) * rr), z = (int) Math.round(Math.sin(Math.toRadians(a)) * rr);
                b.set(tx + x, y, tz + z, y == 25 ? IRON : y % 2 == 0 ? RUST : RAWIRON);
            }
        }
        b.fill(tx - 2, 26, tz - 2, tx + 2, 26, tz + 2, IRON);
        b.put(tx, 27, tz, "minecraft:lightning_rod");
        for (int y = 0; y <= 25; y++) b.put(tx, y, tz + 2, "minecraft:ladder[facing=south]");
        if (L >= 3) { mgNest(b, tx, 27, tz); searchlight(b, tx + 2, 27, tz - 2); }
        // ----------------------------------------------------------- заправка с цистернами
        for (int i = 0; i < 2; i++) {
            int cz = 18 + i * 6;
            for (int x = 6; x <= 16; x++) for (int a = 0; a < 360; a += 30) {
                int dy = (int) Math.round(Math.sin(Math.toRadians(a)) * 1.6), dz = (int) Math.round(Math.cos(Math.toRadians(a)) * 1.6);
                b.set(x, 2 + dy, cz + dz, x == 6 || x == 16 ? GCONCRETE : (L == 0 ? RUST : "minecraft:white_concrete"));
            }
            b.put(8, 0, cz, GCONCRETE); b.put(14, 0, cz, GCONCRETE);
        }
        b.fill(0, 0, 32, 1, 1, 32, "minecraft:red_concrete"); // колонка
        // ----------------------------------------------------------- автобусы и пожарная машина (как на АТП оригинала)
        Vehicles.vehicle(b, "laz", -14, 26, 0, null, L <= 1 ? 1 + b.r.nextInt(2) : 0);
        Vehicles.vehicle(b, "paz", -14, 31, 0, null, L <= 1 ? 1 : 0);
        Vehicles.vehicle(b, "laz", 22, 40, 180, null, 2);
        Vehicles.vehicleAt(b, "zil_fire", -1, 0, -40, 90, null, L == 0 ? 1 : 0);
        Vehicles.vehicle(b, "raf", 30, 10, 90, null, L <= 1 ? 2 : 1);
        // могильник: бочки с отходами у дальнего забора — фонят
        for (int i = 0; i < 7; i++) b.setG(54 - b.r.nextInt(6), 0, -44 + b.r.nextInt(6), b.chance(0.6) ? RADBARREL : BARREL);
        // ----------------------------------------------------------- двор: машины, бочки
        Structures.truck(b, 5, 2, false);
        Structures.truck(b, -8, 14, true);
        if (L >= 1) Structures.truck(b, 18, -10, false);
        for (int i = 0; i < 10; i++) { int x = -15 + b.r.nextInt(30), z = -20 + b.r.nextInt(16); b.setG(x, 0, z, b.chance(0.5) ? BARREL : TIRES); }
        Structures.barrels(b, 14, 40);
        if (L == 1) {
            for (int[] c : new int[][] {{-10, 0}, {12, -24}, {-2, 34}}) { b.put(c[0], 0, c[1], BARREL); b.put(c[0], 1, c[1], CAMPFIRE); }
            sandbags(b, 30, 2, 36, 2, 0, 1);
        }
        if (L >= 2) {
            for (int x = -40; x <= 40; x += 16) { b.fill(x, 0, 0, x, 5, 0, BARS); b.put(x, 6, 0, SEA); }
            mgNest(b, 0, 0, Z - 6);
        }
        if (L >= 3) {
            for (int[] c : new int[][] {{-X + 2, -Z + 2}, {X - 4, -Z + 2}, {-X + 2, Z - 4}, {X - 4, Z - 4}}) Structures.watchtower(b, c[0], c[1]);
            mgNest(b, 34, 11, -14);
            mgNest(b, 48, 11, -14);
            searchlight(b, 30, 11, -20);
            searchlight(b, -40, 15, 23);
            for (int x = -X; x <= X; x += 2) for (int z : new int[] {-Z, Z}) if (Math.abs(x) > 6) b.setG(x, 3, z, BARS);
            for (int z = -Z; z <= Z; z += 2) for (int x : new int[] {-X, X}) b.setG(x, 3, z, BARS);
            wire(b, -30, Z + 4, -8, Z + 4);
            wire(b, 8, Z + 4, 30, Z + 4);
        }
    }

    private static void atpFence(B b, int x, int z, boolean gate) {
        if (gate) return;
        double gapP = b.lvl == 0 ? 0.12 : b.lvl == 1 ? 0.05 : 0;
        if (b.h(x >> 2, 1, z >> 2) < gapP) return;
        // забор из плит ПО-2 с ромбами, столбы через 4 блока
        boolean post = Math.floorMod(x + z, 4) == 0;
        for (int y = 0; y <= 2; y++) b.setG(x, y, z, post ? GCONCRETE : PO2);
        b.setG(x, 3, z, post ? "minecraft:stone_brick_wall" : "minecraft:smooth_stone_slab[type=bottom]");
    }

    // ================================================================= элеватор

    /** Элеватор 117×93: рабочая башня 40 м, четыре силоса, галерея, приёмный навес, коровники, трактор. */
    static void elevator(B b) {
        int L = b.lvl;
        // рабочая башня
        int tx0 = -12, tx1 = -1, tz0 = -12, tz1 = -1, H = 40;
        b.foundation(tx0, tz0, tx1, tz1, 3, GCONCRETE);
        b.walls(tx0, 0, tz0, tx1, H, tz1, CONCRETE);
        b.fill(tx0 + 1, 0, tz0 + 1, tx1 - 1, H, tz1 - 1, AIR);
        for (int y = 0; y <= H; y += 8) b.fill(tx0 + 1, y, tz0 + 1, tx1 - 1, y, tz1 - 1, y == 0 ? GCONCRETE : SMOOTH);
        for (int y = 0; y <= H; y++) b.set(tx0 + 1, y, tz0 + 1, "minecraft:ladder[facing=south]");
        for (int y = 3; y <= H - 2; y += 4) {
            b.set(tx0, y, -6, PANE); b.set(tx0, y, -7, PANE);
            b.set(tx1, y, -6, AIR); b.set(-6, y, tz1, PANE);
        }
        for (int y = 0; y <= H; y += 8) for (int x = tx0; x <= tx1; x++) { b.set(x, y, tz0, GCONCRETE); b.set(x, y, tz1, GCONCRETE); }
        b.fill(-8, 0, tz1, -5, 3, tz1, AIR);
        // надстройка-голова
        b.fill(tx0 - 1, H + 1, tz0 - 1, tx1 + 1, H + 1, tz1 + 1, SLAB);
        b.walls(tx0 + 2, H + 2, tz0 + 2, tx0 + 7, H + 6, tz0 + 7, CONCRETE);
        b.fill(tx0 + 3, H + 2, tz0 + 3, tx0 + 6, H + 5, tz0 + 6, AIR);
        b.fill(tx0 + 1, H + 7, tz0 + 1, tx0 + 8, H + 7, tz0 + 8, SLAB);
        for (int x = tx0 + 3; x <= tx0 + 6; x++) b.set(x, H + 4, tz0 + 2, PANE);
        // надпись полосой
        for (int x = tx0; x <= tx1; x++) b.set(x, H - 3, tz1, (x & 1) == 0 ? "minecraft:red_terracotta" : CONCRETE);
        // силосы
        for (int i = 0; i < 4; i++) {
            int sx = 8 + i * 9, sz = -6;
            for (int y = -1; y <= 30; y++) for (int a = 0; a < 360; a += 9) {
                int x = (int) Math.round(Math.cos(Math.toRadians(a)) * 4), z = (int) Math.round(Math.sin(Math.toRadians(a)) * 4);
                b.set(sx + x, y, sz + z, y % 8 == 0 ? GCONCRETE : CONCRETE);
            }
            b.fill(sx - 3, 31, sz - 3, sx + 3, 31, sz + 3, SLAB);
            for (int y = 0; y <= 30; y++) b.put(sx, y, sz + 4 + 1, y % 3 == 0 ? BARS : AIR);
            b.fill(sx - 1, 0, sz + 5, sx + 1, 1, sz + 5, GCONCRETE); // выпускной лоток
        }
        // галерея-транспортёр на опорах
        for (int x = 0; x <= 40; x++) {
            boolean gone = L == 0 && x > 22 && x < 30;
            if (!gone) {
                b.fill(x, 32, -8, x, 34, -4, WHITE_BRICK);
                b.fill(x, 33, -7, x, 33, -5, AIR);
                if (x % 3 == 0) b.set(x, 33, -8, PANE);
            }
        }
        // приёмный навес у дороги и весовая
        int nz0 = 10, nz1 = 22;
        for (int x = -20; x <= 4; x += 6) for (int z : new int[] {nz0, nz1}) b.fill(x, 0, z, x, 6, z, GCONCRETE);
        for (int x = -21; x <= 5; x++) for (int z = nz0 - 1; z <= nz1 + 1; z++) b.set(x, 7, z, z % 4 == 0 ? IRON : RAWIRON);
        b.fill(-14, -1, 13, -6, -1, 19, ASPH);
        b.fill(-18, 0, 12, -18, 0, 20, "minecraft:iron_block"); // весы
        // коровники
        Structures.barn(b, -56, 18, 30);
        Structures.barn(b, -56, 32, 30);
        // ограда
        double keep = L == 0 ? 0.5 : L == 1 ? 0.7 : 1.0;
        for (int x = -58; x <= 58; x++) for (int z : new int[] {-46, 46}) if (b.chance(keep)) b.setG(x, 0, z, FENCE);
        for (int z = -46; z <= 46; z++) for (int x : new int[] {-58, 58}) if (b.chance(keep)) b.setG(x, 0, z, FENCE);
        // техника и хлам
        Structures.tractor(b, 18, 26);
        if (L >= 1) Structures.truck(b, -10, 16, false);
        for (int i = 0; i < 8; i++) b.setG(10 + b.r.nextInt(30), 0, 10 + b.r.nextInt(30), b.chance(0.5) ? "minecraft:hay_block[axis=y]" : BARREL);
        if (L == 0) { weeds(b, -56, -44, 56, 44, 0, 0.04); rubble(b, tx0 + 1, tz0 + 1, tx1 - 1, tz1 - 1, 1, 0.2); }
        if (L == 1) {
            Structures.tentCamp(b.sub(-8, 26, 0));
            Structures.tentCamp(b.sub(4, 30, 90));
            b.put(-12, 0, 16, CAMPFIRE);
            sandbags(b, -20, 24, -12, 24, 0, 1);
        }
        if (L >= 2) {
            genset(b, -16, 0, 4);
            for (int x = -18; x <= 2; x += 10) b.put(x, 6, 16, SEA);
            b.put(-7, 1, -7, SEA); b.put(-7, 17, -7, SEA); b.put(-7, 33, -7, SEA);
            Structures.armyTent(b, 30, 34);
        }
        if (L >= 3) {
            // снайперское гнездо на голове башни и прожекторы
            sandbags(b, tx0 - 1, tz0 - 1, tx1 + 1, tz0 - 1, H + 2, 0);
            sandbags(b, tx0 - 1, tz1 + 1, tx1 + 1, tz1 + 1, H + 2, 0);
            searchlight(b, tx1, H + 2, tz1 - 2);
            searchlight(b, tx0 + 9, H + 8, tz0 + 4);
            mgNest(b, -8, 8, 16);
            Structures.watchtower(b, 52, -40);
            Structures.watchtower(b, -52, -40);
            wire(b, -40, 50, 40, 50);
        }
    }

    // ================================================================= полевая база армии

    /**
     * Полевая база военных 49×41 (меньше КПП): бетонный забор, ворота со шлагбаумом, одноэтажная казарма,
     * штабной бункер с антенной связи, навес для техники, склад снабжения (сюда разгружаются конвои), вышка.
     */
    static void militaryBase(B b) {
        int L = b.lvl;
        int X = 24, Z = 20;
        // площадка
        for (int x = -X + 1; x <= X - 1; x++) for (int z = -Z + 1; z <= Z - 1; z++)
            if (b.h(x >> 2, 0, z >> 2) < (L >= 2 ? 0.85 : 0.6)) b.put(x, -1, z, b.chance(0.2) ? GRAVEL : b.chance(0.5) ? SMOOTH : GCONCRETE);
        for (int z = 10; z <= Z + 6; z++) for (int x = -3; x <= 3; x++) b.put(x, -1, z, b.chance(0.1) ? GRAVEL : ASPH);
        // забор из бетонных плит с колючкой; ворота на юге
        for (int x = -X; x <= X; x++) for (int z : new int[] {-Z, Z}) baseFence(b, x, z, z == Z && Math.abs(x) <= 3);
        for (int z = -Z; z <= Z; z++) for (int x : new int[] {-X, X}) baseFence(b, x, z, false);
        for (int x : new int[] {-4, 4}) b.fill(x, 0, Z, x, 4, Z, GCONCRETE);
        if (L >= 1) for (int x = -3; x <= 3; x++) b.set(x, 1, Z + 2, (x & 1) == 0 ? "minecraft:red_concrete" : "minecraft:white_concrete");
        if (L >= 1) { b.put(-4, 0, Z + 2, "minecraft:polished_andesite"); b.put(4, 0, Z + 2, "minecraft:polished_andesite"); }
        // ----------------------------------------------------------- казарма
        int kx0 = -21, kx1 = -4, kz0 = -17, kz1 = -8;
        b.foundation(kx0, kz0, kx1, kz1, 3, COBBLE);
        if (L == 0) {
            for (int x = kx0; x <= kx1; x++) for (int z = kz0; z <= kz1; z++) {
                if (x != kx0 && x != kx1 && z != kz0 && z != kz1) continue;
                int h = (int) (b.h(x, 1, z) * 4);
                for (int y = 0; y < h; y++) b.put(x, y, z, WHITE_BRICK);
            }
            rubble(b, kx0 + 1, kz0 + 1, kx1 - 1, kz1 - 1, 0, 0.3);
        } else {
            b.walls(kx0, 0, kz0, kx1, 4, kz1, WHITE_BRICK);
            b.fill(kx0 + 1, 0, kz0 + 1, kx1 - 1, 4, kz1 - 1, AIR);
            b.fill(kx0 + 1, -1, kz0 + 1, kx1 - 1, -1, kz1 - 1, "minecraft:polished_andesite");
            b.fill(kx0 - 1, 5, kz0 - 1, kx1 + 1, 5, kz1 + 1, L >= 2 ? SLAB : TARP);
            for (int x = kx0 + 2; x <= kx1 - 2; x += 3) {
                String g = L >= 3 ? BARS : L == 1 ? "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]" : PANE;
                b.set(x, 2, kz1, g); b.set(x, 2, kz0, g);
            }
            b.set(-12, 1, kz1, AIR); b.set(-12, 2, kz1, AIR);
            for (int x = kx0 + 2; x <= kx1 - 2; x += 2) { b.put(x, 1, kz0 + 1, "minecraft:green_wool"); if (L >= 2) b.put(x, 2, kz0 + 1, "minecraft:green_wool"); }
            if (L >= 2) { for (int x = -18; x <= -6; x += 6) b.put(x, 4, -12, SEA); b.put(-6, 1, kz1 - 1, "minecraft:barrel[facing=up]"); }
        }
        // ----------------------------------------------------------- штабной бункер связи
        int hx0 = 6, hx1 = 15, hz0 = -17, hz1 = -10;
        b.foundation(hx0, hz0, hx1, hz1, 2, GCONCRETE);
        b.walls(hx0, 0, hz0, hx1, 3, hz1, GCONCRETE);
        b.fill(hx0 + 1, 0, hz0 + 1, hx1 - 1, 3, hz1 - 1, AIR);
        b.fill(hx0 - 1, 4, hz0 - 1, hx1 + 1, 4, hz1 + 1, L == 0 ? CSLAB : GRASS);
        b.set(10, 1, hz1, AIR); b.set(10, 2, hz1, AIR);
        for (int x = hx0 + 2; x <= hx1 - 2; x += 3) b.set(x, 2, hz1, BARS);
        if (L >= 1) { b.put(8, 1, hz0 + 1, "minecraft:cartography_table"); b.put(9, 1, hz0 + 1, "minecraft:lectern[facing=south]"); }
        if (L >= 2) {
            b.put(13, 1, hz0 + 1, "minecraft:note_block"); // радиостанция
            b.put(11, 3, -13, HANG);
            b.fill(14, 5, -16, 14, 16, -16, BARS); // мачта связи
            b.fill(14, 17, -16, 14, 19, -16, "minecraft:lightning_rod");
            for (int k = 1; k <= 3; k++) { b.put(14 - k, 15 - k * 3, -16, "minecraft:chain[axis=x]"); b.put(14 + k, 15 - k * 3, -16, "minecraft:chain[axis=x]"); }
        } else if (L == 1) b.fill(14, 5, -16, 14, 9, -16, "minecraft:lightning_rod");
        // ----------------------------------------------------------- навес для техники
        int nx0 = 6, nx1 = 21, nz0 = -5, nz1 = 7;
        for (int x = nx0; x <= nx1; x += 5) for (int z : new int[] {nz0, nz1}) b.fill(x, 0, z, x, 4, z, L == 0 && b.chance(0.4) ? AIR : LOG);
        for (int x = nx0 - 1; x <= nx1 + 1; x++) for (int z = nz0 - 1; z <= nz1 + 1; z++)
            if (L > 0 || b.h(x >> 1, 5, z >> 1) > 0.5) b.set(x, 5, z, (x + z) % 5 == 0 ? IRON : RAWIRON);
        if (L >= 1) Structures.truck(b, 13, -1, false);
        if (L >= 2) {
            for (int i = -2; i <= 2; i++) for (int j = -1; j <= 1; j++) {   // УАЗ
                boolean wheel = Math.abs(i) == 2 && Math.abs(j) == 1;
                b.put(13 + i, 0, 4 + j, wheel ? ASPH : OLIVE);
                if (i >= -1) b.put(13 + i, 1, 4 + j, i == 1 ? PANE : OLIVE);
                if (i >= -1 && i <= 0) b.put(13 + i, 2, 4 + j, CLOTH);
            }
            genset(b, nx1 - 2, 0, nz1 - 2);
        }
        if (L == 0) Structures.btr(b.sub(14, 1, 0), 0, 0);
        if (L >= 3) Structures.btr(b, 13, 12);
        // ----------------------------------------------------------- склад снабжения: сюда разгружают конвои
        int sx0 = -21, sx1 = -8, sz0 = 0, sz1 = 12;
        if (L >= 1) {
            for (int x = sx0; x <= sx1; x++) for (int z = sz0; z <= sz1; z++) {
                if ((x - sx0) % 3 == 2 || (z - sz0) % 4 == 3) continue;
                double v = b.h(x, 2, z);
                int stack = L >= 2 ? 1 + (int) (v * 3) : (v < 0.4 ? 1 : 0);
                for (int y = 0; y < stack; y++) b.put(x, y, z, v < 0.3 ? BARREL : v < 0.55 ? "minecraft:green_wool" : CRATE);
            }
            if (L >= 2) { // брезентовый навес над складом
                for (int x = sx0 - 1; x <= sx1 + 1; x++) for (int z = sz0 - 1; z <= sz1 + 1; z++) b.set(x, 4, z, CLOTH);
                for (int[] c : new int[][] {{sx0 - 1, sz0 - 1}, {sx1 + 1, sz0 - 1}, {sx0 - 1, sz1 + 1}, {sx1 + 1, sz1 + 1}}) b.fill(c[0], 0, c[1], c[0], 3, c[1], FENCE);
            }
        } else rubble(b, sx0, sz0, sx1, sz1, 0, 0.1);
        // ----------------------------------------------------------- лагерь, вышки, укрепления
        if (L == 1) {
            Structures.armyTent(b, -2, 2);
            b.put(0, 0, 10, CAMPFIRE);
            sandbags(b, -8, Z - 3, -5, Z - 3, 0, 1);
        }
        if (L >= 2) {
            Structures.watchtower(b, X - 4, -Z + 3);
            mgNest(b, 6, 0, Z - 4);
            b.fill(-2, 0, -4, -2, 8, -4, BARS);
            b.put(-2, 9, -4, "minecraft:green_banner[rotation=0]");
            for (int z = -2; z <= 14; z += 8) { b.fill(4, 0, z, 4, 4, z, BARS); b.put(4, 5, z, SEA); }
            sandbags(b, -9, Z - 3, -5, Z - 3, 0, 1);
        }
        if (L >= 3) {
            Structures.watchtower(b, -X + 3, Z - 6);
            mgNest(b, -6, 0, Z - 4);
            searchlight(b, 20, 0, 14);
            searchlight(b, -2, 6, -13);
            for (int x = -X; x <= X; x += 2) for (int z : new int[] {-Z, Z}) if (Math.abs(x) > 4) b.setG(x, 4, z, BARS);
            for (int z = -Z; z <= Z; z += 2) for (int x : new int[] {-X, X}) b.setG(x, 4, z, BARS);
            wire(b, -X - 3, Z + 4, -6, Z + 4);
            wire(b, 6, Z + 4, X + 3, Z + 4);
        }
        if (L == 0) weeds(b, -X + 1, -Z + 1, X - 1, Z - 1, 0, 0.06);
    }

    private static void baseFence(B b, int x, int z, boolean gate) {
        if (gate) return;
        if (b.lvl == 0 && b.h(x >> 2, 3, z >> 2) < 0.15) return;
        boolean post = Math.floorMod(x + z, 4) == 0;
        for (int y = 0; y <= 2; y++) b.setG(x, y, z, post ? GCONCRETE : PO2);
        b.setG(x, 3, z, post ? "minecraft:stone_brick_wall" : BARS);
    }
}
