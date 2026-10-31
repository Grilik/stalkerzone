package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Бункер Сидоровича по канону оригинала (S.T.A.L.K.E.R. Anomaly, l01_escape): бетонный портал со входом и лестницей
 * вниз за домами деревни новичков, под входом — подземная комната торговца (стол, ящики, свет). Размеры и место
 * совпадают с точкой узла «Бункер Сидоровича»: вход у (−35, −8), подземная комната — прямо под входом.
 *
 * <p>Уровень развития (как в MineColonies): 0 — руины (дверь выбита, часть стен осыпалась), 1 — обжитой,
 * 2 — торгующая; рабочее место торговца (duty) — за столом, отмечается тем же проходом сборки.
 */
public final class KordonBunker {
    private KordonBunker() {}

    /** Область, где воксели уровня заменяет этот сборщик: {x0, z0, x1, z1}. Вход и комната в границах с запасом. */
    public static final List<int[]> CLEAR = List.of(new int[] {-43, -15, -27, -2});

    static final String CONC = "stalkerzone:zm_concrete",
            CONC_OLD = "stalkerzone:zm_concrete_old", CONC_SLAB = "stalkerzone:zm_concrete_slab",
            CONC_FLOOR = "stalkerzone:zm_concrete_floor", CONC_STAIRS = "stalkerzone:zm_stair_steps_stairs",
            DOOR = "stalkerzone:zm_door_metal", PLANK = "minecraft:spruce_planks",
            SLAB = "minecraft:spruce_slab", CRATE = "stalkerzone:military_crate",
            WCRATE = "stalkerzone:wooden_crate", LIGHT = "minecraft:lantern",
            AIR = "minecraft:air";

    private static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x61c88647 ^ z * 0x165667b1;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return h & 0x7fffffff;
    }

    /** Сборщик с учётом уровня: на руинах (0) часть стен и ступеней выбита. */
    private static final class S {
        final BlockSink s;
        final int lvl;
        S(BlockSink s, int lvl) { this.s = s; this.lvl = Math.max(0, Math.min(3, lvl)); }

        int ground(int x, int z) { return s.ground(x, z); }

        void set(int x, int y, int z, String st) { s.set(x, y, z, st); }

        /** Рабочее место (карточка 031): отмечается тем же проходом сборки. */
        void mark(String kind, int x, int y, int z) { s.mark(kind, x, y, z); }

        /** Блок стены/крыши: на уровне 0 — часть выбита. */
        void wall(int x, int y, int z, String st) {
            int h = hash(x, y, z) % 100;
            if (lvl == 0 && h < 18) { s.set(x, y, z, AIR); return; }
            s.set(x, y, z, st);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String st) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.set(x, y, z, st);
        }

        /** Дверной проём 1×2 (на уровне 1+ — металлическая дверь). alongX: стена тянется по X. */
        void portalDoor(int x, int y, int z, boolean alongX) {
            String f = alongX ? "south" : "east";
            if (lvl >= 1) {
                set(x, y, z, DOOR + "[facing=" + f + ",half=lower,hinge=left]");
                set(x, y + 1, z, DOOR + "[facing=" + f + ",half=upper,hinge=left]");
            } else {
                set(x, y, z, AIR);
                set(x, y + 1, z, AIR);
            }
        }
    }

    /** Построить бункер. Блоки — в мировых координатах. */
    public static void build(BlockSink sink, int lvl) {
        S b = new S(sink, lvl);
        clear(b);
        room(b);
        entrance(b);
    }

    // ---------------------------------------------------------------- расчистка

    /** Снять всё, что выше земли в границах замены (на случай, если воксели уже стоят). */
    private static void clear(S b) {
        int[] c = CLEAR.get(0);
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int g = b.ground(x, z);
            for (int y = g + 1; y <= g + 8; y++) b.set(x, y, z, AIR);
        }
    }

    // ---------------------------------------------------------------- подземная комната

    /** Комната торговца под входом: пол на 6 ниже земли, стены 3 высотой, потолок - бетон. След 7x9x3. */
    private static void room(S b) {
        int g = b.ground(-35, -8);
        int fl = g - 6;
        int x0 = -38, x1 = -32, z0 = -13, z1 = -5;   // внутри 7x9
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            for (int y = fl; y <= g + 1; y++) b.set(x, y, z, AIR);
        b.fill(x0 - 1, fl - 1, z0 - 1, x1 + 1, fl - 1, z1 + 1, CONC_FLOOR);
        b.fill(x0 - 1, fl + 3, z0 - 1, x1 + 1, fl + 3, z1 + 1, CONC);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            b.wall(x, fl, z0 - 1, CONC); b.wall(x, fl + 1, z0 - 1, CONC); b.wall(x, fl + 2, z0 - 1, CONC);
            b.wall(x, fl, z1 + 1, CONC); b.wall(x, fl + 1, z1 + 1, CONC); b.wall(x, fl + 2, z1 + 1, CONC);
        }
        for (int z = z0; z <= z1; z++) {
            b.wall(x0 - 1, fl, z, CONC); b.wall(x0 - 1, fl + 1, z, CONC); b.wall(x0 - 1, fl + 2, z, CONC);
            b.wall(x1 + 1, fl, z, CONC); b.wall(x1 + 1, fl + 1, z, CONC); b.wall(x1 + 1, fl + 2, z, CONC);
        }
        for (int x = -37; x <= -33; x++) b.set(x, fl, -12, PLANK);   // прилавок
        b.set(-36, fl, -11, PLANK);                                  // стол Сидоровича
        b.set(-34, fl + 1, -11, SLAB + "[type=bottom]");
        b.set(-37, fl, -6, CRATE);
        b.set(-33, fl, -6, WCRATE);
        b.set(-37, fl, -11, WCRATE);
        b.set(-33, fl + 3, -5, LIGHT + "[hanging=true]");
        b.set(-37, fl + 3, -13, LIGHT + "[hanging=true]");
        if (b.lvl >= 2) b.set(-35, fl, -12, "minecraft:lectern[facing=north]");
        b.mark("duty", -35, fl, -11);
    }

    // ---------------------------------------------------------------- вход

    /** Бетонный портал на поверхности и лестница вниз в комнату: дверь в северном торце, спуск по Z. */
    private static void entrance(S b) {
        int g = b.ground(-35, -8);
        int fl = g - 6;
        int xa = -37, xb = -33, zd = -4;   // дверь на северном торце комнаты
        // лестница: от двери (низ, z=-4) к поверхности (верх, z=-9), спуск на юг
        for (int k = 0; k < 6; k++) {
            int zz = -9 + k;                                  // -9..-4
            int yy = fl + 5 - k;                              // 80..75
            for (int x = xa; x <= xb; x++) {
                b.set(x, yy, zz, CONC_STAIRS + "[facing=south,half=bottom]");
                for (int h = 1; h <= 3; h++) b.set(x, yy + h, zz, AIR);   // освободить шахту сверху
            }
            b.fill(xa - 1, yy, zz, xa - 1, yy + 2, zz, CONC);   // стенки шахты
            b.fill(xb + 1, yy, zz, xb + 1, yy + 2, zz, CONC);
        }
        // верхняя ступень на уровне земли: спуск начинается от поверхности без перепада
        for (int x = xa; x <= xb; x++) {
            b.set(x, g, -10, CONC_STAIRS + "[facing=south,half=bottom]");
            b.set(x, g + 1, -10, AIR); b.set(x, g + 2, -10, AIR);
        }
        // проём в северной стене комнаты и дверь
        for (int x = xa; x <= xb; x++) { b.set(x, fl, zd, AIR); b.set(x, fl + 1, zd, AIR); }
        b.portalDoor(-35, fl, zd, false);
        // бетонный портал на поверхности над спуском
        int top = g;
        for (int x = xa - 1; x <= xb + 1; x++) {
            if (x < xa || x > xb) b.wall(x, top + 1, -10, CONC);
            b.wall(x, top + 2, -10, CONC_OLD);
            b.wall(x, top + 1, zd, CONC); b.wall(x, top + 2, zd, CONC_OLD);
        }
        for (int z = -10; z <= zd; z++) {
            b.wall(xa - 1, top + 1, z, CONC); b.wall(xa - 1, top + 2, z, CONC_OLD);
            b.wall(xb + 1, top + 1, z, CONC); b.wall(xb + 1, top + 2, z, CONC_OLD);
        }
        b.fill(xa - 1, top + 3, -10, xb + 1, top + 3, zd, CONC_SLAB + "[type=bottom]");
    }
}