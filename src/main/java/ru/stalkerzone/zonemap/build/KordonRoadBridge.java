package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.List;

/**
 * Дорожный мост над оврагом у заставы О7, по вокселям оригинала l01_escape (Anomaly), см.
 * {@code docs/kordon_objects/12-wagon-o7.md}: бетонное полотно шириной 7 по линии дороги x ≈ −0,9155·z + 84,6, бетонные
 * бордюры с перилами, две пары опор, устои на берегах. Овраг идёт поперёк (142, −94) → (174, −70), дно ≈ 90.
 *
 * <p>Под мостом — лагерь О7: костёр, подстилки, ящики; от вагончика туда ведёт тропа по дну оврага. Под полотном
 * 3 блока до балок — можно пройти. Если площадка ещё не вырыла овраг (выемка Site.Trench у «Программиста»),
 * сборщик сам выкапывает проход шириной полотна под мостом.
 */
public final class KordonRoadBridge {
    private KordonRoadBridge() {}

    /** Область сборщика: полотно, бордюры, устои и лагерь под мостом (воксели старого моста оригинала заменяются). */
    public static final List<int[]> CLEAR = List.of(new int[] {136, -104, 182, -56});

    static final String CONC = "stalkerzone:zm_concrete", CONC_O = "stalkerzone:zm_concrete_old", PANEL = "stalkerzone:zm_concrete_panel",
            ROAD = "minecraft:gray_concrete", RAIL = "minecraft:iron_bars", COARSE = "minecraft:coarse_dirt",
            FIRE = "minecraft:campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]",
            BEDROLL = "minecraft:brown_carpet", CRATE = "stalkerzone:wooden_crate", LOG = "minecraft:spruce_log[axis=x]",
            AIR = "minecraft:air";

    /** Перекрёсток дороги и оврага (середина моста) и направления: u — вдоль дороги, n — поперёк. */
    public static final double CX = 158.8, CZ = -81.0;
    static final double UX = -0.6752, UZ = 0.7376, NX = 0.7376, NZ = 0.6752;
    /** Полуширина полотна, полудлина пролёта, положение опор вдоль дороги. */
    public static final double HALF_W = 3.5, HALF_L = 12.5, PIER = 5.5;

    static double t(double x, double z) { return (x - CX) * UX + (z - CZ) * UZ; }
    static double v(double x, double z) { return (x - CX) * NX + (z - CZ) * NZ; }

    /** Высота полотна — медиана земли дороги за концами моста (там рельеф не трогают). */
    public static int deck(BlockSink b) {
        int[] gs = new int[6];
        int i = 0;
        for (int s = -1; s <= 1; s += 2) for (int k = 0; k < 3; k++) {
            double tt = s * (HALF_L + 2 + k);
            gs[i++] = b.ground((int) Math.floor(CX + tt * UX), (int) Math.floor(CZ + tt * UZ));
        }
        java.util.Arrays.sort(gs);
        return gs[3];
    }

    /** Овраг под мостом — та же выемка, что у площадки (Site.Trench): дно 90, полуширина 4, откосы 6. */
    public static final Site.Trench RAVINE = new Site.Trench(142, -94, 174, -70, 90, 4, 6);

    /** Земля с учётом оврага: если площадка ещё не вырыла выемку, сборщик выкапывает её сам. */
    static int floorAt(BlockSink b, int x, int z) {
        int gl = b.ground(x, z);
        return Math.min(gl, (int) Math.floor(RAVINE.shape(x, z, gl)));
    }

    /** Пол лагеря под мостом: дно оврага в середине, но не выше чем полотно − 5 (под балками 3 блока). */
    public static int under(BlockSink b, int d) {
        return Math.min(floorAt(b, (int) Math.floor(CX), (int) Math.floor(CZ)), d - 5);
    }

    public static void build(BlockSink b, int lvl) {
        lvl = Math.max(0, Math.min(3, lvl));
        int d = deck(b), f = under(b, d);
        int[] c = CLEAR.get(0);
        // овраг: снять грунт над профилем выемки (когда выемку уже сделала площадка — ничего не меняется)
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int gl = b.ground(x, z), nh = floorAt(b, x, z);
            if (nh >= gl) continue;
            for (int yy = nh + 1; yy <= gl; yy++) b.set(x, yy, z, AIR);
            b.set(x, nh, z, nh <= 91 ? COARSE : "minecraft:grass_block");
        }
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            double tt = t(x + 0.5, z + 0.5), vv = v(x + 0.5, z + 0.5), at = Math.abs(tt), av = Math.abs(vv);
            if (at > HALF_L + 1 || av > HALF_W + 1.5) continue;
            boolean span = at <= HALF_L;
            if (av <= HALF_W) {
                // полотно: асфальт сверху, бетонная плита под ним; над — воздух (старые воксели и бугры)
                for (int yy = d + 1; yy <= d + 4; yy++) b.set(x, yy, z, AIR);
                b.set(x, d, z, ROAD);
                b.set(x, d - 1, z, span ? CONC : CONC_O);
                if (span && at < HALF_L - 1) {                     // проход под мостом: от пола лагеря до балок
                    for (int yy = f + 1; yy <= d - 2; yy++) b.set(x, yy, z, AIR);
                    if (floorAt(b, x, z) > f) b.set(x, f, z, COARSE);
                    else for (int yy = floorAt(b, x, z) + 1; yy < f; yy++) b.set(x, yy, z, "minecraft:dirt");
                    if (yyIsPier(at, vv)) for (int yy = Math.min(f, floorAt(b, x, z)); yy <= d - 2; yy++) b.set(x, yy, z, PANEL);
                } else if (!span || at >= HALF_L - 1) {            // устои: бетон от земли до полотна
                    for (int yy = Math.min(floorAt(b, x, z), d - 2); yy <= d - 2; yy++) b.set(x, yy, z, CONC_O);
                }
            } else if (av <= HALF_W + 1.5) {
                // бордюр и перила по краям полотна (на пролёте — над оврагом, на подходах — по насыпи)
                for (int yy = d + 2; yy <= d + 4; yy++) b.set(x, yy, z, AIR);
                b.set(x, d, z, CONC);
                b.set(x, d - 1, z, CONC);
                b.set(x, d + 1, z, lvl == 0 && (x * 7 + z * 3) % 5 == 0 ? AIR : (Math.floorMod(Math.round(tt), 4) == 0 ? CONC : RAIL));
            }
        }
        camp(b, d, f, lvl);
    }

    static boolean yyIsPier(double at, double vv) { return Math.abs(at - PIER) < 0.75 && Math.abs(Math.abs(vv) - 2.5) < 0.75; }

    /** Лагерь О7 под мостом: костёр посередине, подстилки и ящики вокруг. */
    private static void camp(BlockSink b, int d, int f, int lvl) {
        int fx = (int) Math.floor(CX), fz = (int) Math.floor(CZ);
        b.set(fx, f + 1, fz, lvl >= 1 ? FIRE : "minecraft:campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]");
        b.mark("cook", fx + 1, f + 1, fz);
        int[][] sleep = {{-2, -2}, {-3, 0}, {2, 2}, {3, 0}};
        for (int[] s : sleep) {
            int x = (int) Math.floor(CX + s[0] * UX * 1.0 + s[1] * NX), z = (int) Math.floor(CZ + s[0] * UZ + s[1] * NZ);
            if (lvl >= 1) b.set(x, f + 1, z, BEDROLL);             // скатка-подстилка, место сна на ней
            b.mark("bed", x, f + 1, z);
        }
        int lx = (int) Math.floor(CX - 3 * UX - 2 * NX), lz = (int) Math.floor(CZ - 3 * UZ - 2 * NZ);
        b.set(lx, f + 1, lz, LOG);
        b.mark("rest", lx + 1, f + 1, lz);
        int cx = (int) Math.floor(CX + 3 * UX - 2 * NX), cz = (int) Math.floor(CZ + 3 * UZ - 2 * NZ);
        b.set(cx, f + 1, cz, CRATE);
        b.mark("shelter", fx - 1, f + 1, fz);
        // часовые на концах моста
        for (int s = -1; s <= 1; s += 2) {
            double tt = s * (HALF_L - 1);
            b.mark("guard", (int) Math.floor(CX + tt * UX + 2 * NX), d + 1, (int) Math.floor(CZ + tt * UZ + 2 * NZ));
        }
    }
}
