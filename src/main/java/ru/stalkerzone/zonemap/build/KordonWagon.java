package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Лагерь у вагончика — застава одиночек О7 (западный берег оврага у дорожного моста), по вокселям оригинала
 * l01_escape (Anomaly), см. {@code docs/kordon_objects/12-wagon-o7.md}.
 *
 * <p>Как в игре: строительный вагончик-бытовка с красной кровлей (x 125..129, z −74..−63), зелёная железная будка
 * (x 107..110, z −73..−68), штабеля бетонных плит и листов шифера. Вагончик стоит на бетонных блоках, вход
 * с запада по ступеням, внутри нары, стол, печка. Костёр и ночлег лагеря — под дорожным мостом ({@link KordonRoadBridge}).
 *
 * <p>Уровень развития (решение 01.11, «стоянка → пост → укрепление»): 0 — брошенная стоянка (выбитые окна, без двери
 * и света), 1 — стоянка (вагончик обжит), 2 — пост (мешки у вагончика, навес у будки), 3 — укрепление (вышка
 * у дороги и ограждение из мешков).
 */
public final class KordonWagon {
    private KordonWagon() {}

    /** Область сборщика (воксели вагончика, будки и мусора оригинала заменяются). */
    public static final List<int[]> CLEAR = List.of(new int[] {104, -90, 134, -58});

    static final String PLASTER = "stalkerzone:zm_plaster", ROOF = "stalkerzone:zm_roof_red", GREEN = "stalkerzone:zm_metal_green",
            GREEN_R = "stalkerzone:zm_metal_green_rust", RUST = "stalkerzone:zm_rust_sheet", CONC = "stalkerzone:zm_concrete",
            CONC_O = "stalkerzone:zm_concrete_old", SHIFER = "stalkerzone:zm_shifer", FLOOR = "stalkerzone:zm_planks_floor",
            POST = "stalkerzone:zm_wood_post_fence", PLANKS = "stalkerzone:zm_planks_gray", GLASS = "stalkerzone:zm_glass_dirty_pane",
            DOOR = "stalkerzone:zm_door_green_door", BAGS = "stalkerzone:zm_sandbags", LIGHT = "minecraft:lantern[hanging=true]",
            CRATE = "stalkerzone:military_crate", WCRATE = "stalkerzone:wooden_crate", BARREL = "minecraft:barrel[facing=up]",
            STOVE = "minecraft:furnace[facing=east]", TABLE = "minecraft:spruce_slab[type=top]", MATTRESS = "stalkerzone:zm_mattress_slab[type=bottom]",
            LADDER = "minecraft:ladder", AIR = "minecraft:air";

    // вагончик (обмер: x 125..129, z −74..−63)
    public static final int TX0 = 125, TX1 = 129, TZ0 = -74, TZ1 = -63, DOOR_Z = -68;

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Построить лагерь. Блоки — в мировых координатах. */
    public static void build(BlockSink b, int lvl) {
        lvl = Math.max(0, Math.min(3, lvl));
        trailer(b, lvl);
        booth(b, lvl);
        debris(b);
        if (lvl >= 2) for (int z = DOOR_Z - 3; z <= DOOR_Z + 3; z++) if (Math.abs(z - DOOR_Z) > 1) b.set(TX0 - 3, g(b, TX0 - 3, z) + 1, z, BAGS);
        if (lvl >= 3) tower(b);
        // часовой у вагончика, лицом к дороге на север; обход вдоль берега
        b.mark("guard", TX1 + 2, g(b, TX1 + 2, TZ0 - 2) + 1, TZ0 - 2);
        for (int[] p : new int[][] {{112, -80}, {131, -78}, {131, -60}, {108, -62}})
            b.mark("patrol", p[0], g(b, p[0], p[1]) + 1, p[1]);
    }

    static int g(BlockSink b, int x, int z) { return b.ground(x, z); }

    /** Пол площадки под вагончиком: медиана земли по следу. */
    static int base(BlockSink b, int x0, int z0, int x1, int z1) {
        int n = (x1 - x0 + 1) * (z1 - z0 + 1), i = 0;
        int[] gs = new int[n];
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) gs[i++] = b.ground(x, z);
        java.util.Arrays.sort(gs);
        return gs[n / 2];
    }

    /**
     * Вагончик-бытовка: на бетонных блоках (пол на 1 выше земли), стены штукатурка 3 блока, низкая двускатная кровля
     * из красных плит, дверь с запада по ступеням, окна по длинным сторонам; внутри нары, стол, печь, ящики.
     */
    private static void trailer(BlockSink b, int lvl) {
        int g = base(b, TX0, TZ0, TX1, TZ1), fl = g + 1, y = fl + 1, top = y + 3;
        for (int x = TX0; x <= TX1; x++) for (int z = TZ0; z <= TZ1; z++) {
            boolean edge = x == TX0 || x == TX1 || z == TZ0 || z == TZ1;
            boolean foot = (x == TX0 || x == TX1) && (z == TZ0 || z == TZ1 || z == (TZ0 + TZ1) / 2);
            for (int yy = g + 1; yy < fl; yy++) b.set(x, yy, z, foot ? CONC_O : AIR);
            b.set(x, fl, z, FLOOR);
            for (int yy = y; yy < top; yy++) {
                if (!edge) { b.set(x, yy, z, AIR); continue; }
                boolean win = yy == y + 1 && (x == TX0 || x == TX1) && Math.floorMod(z - TZ0, 3) == 1 && z != DOOR_Z;
                if (win) { b.set(x, yy, z, lvl == 0 ? AIR : GLASS); continue; }
                b.set(x, yy, z, lvl == 0 && hash(x, yy, z) % 9 == 0 ? AIR : yy == top - 1 ? "stalkerzone:zm_plaster_old" : PLASTER);
            }
            // кровля: плиты, по краям чуть ниже — низкий скат к длинным сторонам
            boolean ridge = x == (TX0 + TX1) / 2;
            b.set(x, top, z, ROOF + (ridge ? "" : "_slab[type=top]"));
        }
        for (int z = TZ0 - 1; z <= TZ1 + 1; z++) { b.set(TX0 - 1, top, z, ROOF + "_slab[type=bottom]"); b.set(TX1 + 1, top, z, ROOF + "_slab[type=bottom]"); }
        // дверь и ступени с запада
        if (lvl >= 1) {
            b.set(TX0, y, DOOR_Z, DOOR + "[facing=west,hinge=left,open=false,half=lower]");
            b.set(TX0, y + 1, DOOR_Z, DOOR + "[facing=west,hinge=left,open=false,half=upper]");
        } else { b.set(TX0, y, DOOR_Z, AIR); b.set(TX0, y + 1, DOOR_Z, AIR); }
        b.set(TX0 - 1, fl, DOOR_Z, CONC + "_slab[type=top]");                  // крыльцо вровень с полом
        b.set(TX0 - 2, g + 1, DOOR_Z, CONC + "_slab[type=bottom]");             // ступень с земли
        // обстановка: нары вдоль восточной стены, стол с печкой у северного торца, ящики у южного
        for (int z : new int[] {TZ0 + 2, TZ0 + 4, TZ1 - 3, TZ1 - 2}) {   // нары между печкой и ящиками, не у двери
            b.set(TX1 - 1, y, z, lvl >= 1 ? MATTRESS : AIR);
            b.mark("bed", TX1 - 2, y, z);
        }
        b.set(TX0 + 1, y, TZ0 + 1, lvl >= 1 ? STOVE : AIR);
        b.set(TX0 + 2, y, TZ0 + 1, lvl >= 1 ? TABLE : WCRATE);
        b.set(TX0 + 1, y, TZ1 - 1, CRATE);
        b.set(TX0 + 2, y, TZ1 - 1, WCRATE);
        if (lvl >= 1) { b.set((TX0 + TX1) / 2, top - 1, TZ0 + 3, LIGHT); b.set((TX0 + TX1) / 2, top - 1, TZ1 - 3, LIGHT); }
        b.mark("rest", TX0 + 2, y, TZ0 + 2);
    }

    /** Зелёная железная будка (обмер x 107..110, z −73..−68): склад лагеря, дверь на восток к вагончику. */
    private static void booth(BlockSink b, int lvl) {
        int x0 = 107, x1 = 110, z0 = -73, z1 = -68, g = base(b, x0, z0, x1, z1), y = g + 1;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            b.set(x, g, z, CONC_O);
            for (int yy = y; yy <= y + 2; yy++) b.set(x, yy, z, edge ? (hash(x, yy, z) % 4 == 0 ? GREEN_R : GREEN) : AIR);
            b.set(x, y + 3, z, RUST + "_slab[type=bottom]");
        }
        b.set(x1, y, -70, AIR); b.set(x1, y + 1, -70, AIR);                     // проём к вагончику
        b.set(x0 + 1, y, z0 + 1, CRATE); b.set(x0 + 2, y, z0 + 1, BARREL); b.set(x0 + 1, y, z1 - 1, WCRATE);
        if (lvl >= 1) b.set(x0 + 2, y + 2, -70, LIGHT);
        b.mark("work", x0 + 2, y, -70);
        if (lvl >= 2) for (int z = z0 - 1; z <= z1 + 1; z++) {                   // навес у будки
            b.set(x1 + 3, y, z, z == z0 - 1 || z == z1 + 1 ? POST : AIR);
            b.set(x1 + 3, y + 1, z, z == z0 - 1 || z == z1 + 1 ? POST : AIR);
            for (int x = x1 + 1; x <= x1 + 3; x++) b.set(x, y + 2, z, RUST + "_slab[type=bottom]");
        }
    }

    /** Штабеля бетонных плит и шифера по обмеру (x 111..120, z −68..−62). */
    private static void debris(BlockSink b) {
        int[][] stacks = {{116, -68, 2}, {117, -67, 2}, {118, -66, 1}, {113, -65, 1}, {112, -64, 2}, {114, -63, 1}};
        for (int[] st : stacks) {
            int g = b.ground(st[0], st[1]);
            for (int k = 1; k <= st[2]; k++) b.set(st[0], g + k, st[1], CONC + "_slab[type=" + (k == st[2] ? "bottom" : "double") + "]");
        }
        for (int[] s : new int[][] {{122, -78}, {123, -77}, {121, -70}, {122, -69}, {117, -64}})
            b.set(s[0], b.ground(s[0], s[1]) + 1, s[1], SHIFER + "_slab[type=bottom]");
    }

    /** Укрепление (уровень 3): деревянная вышка у дороги с лестницей и площадкой в мешках. */
    private static void tower(BlockSink b) {
        int tx = 132, tz = -82, g = b.ground(tx, tz);
        for (int yy = g + 1; yy <= g + 5; yy++) {
            b.set(tx, yy, tz, POST); b.set(tx + 2, yy, tz, POST); b.set(tx, yy, tz + 2, POST); b.set(tx + 2, yy, tz + 2, POST);
            b.set(tx + 1, yy, tz + 2, LADDER + "[facing=north]");
        }
        for (int x = tx; x <= tx + 2; x++) for (int z = tz; z <= tz + 2; z++) {
            b.set(x, g + 6, z, PLANKS);
            if (x == tx || x == tx + 2 || z == tz) b.set(x, g + 7, z, BAGS);
        }
        b.set(tx + 1, g + 6, tz + 2, AIR);
        b.mark("guard", tx + 1, g + 7, tz + 1);
    }
}
