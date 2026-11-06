import ru.stalkerzone.zonemap.KillZone;
import ru.stalkerzone.zonemap.build.*;

import java.util.*;

/**
 * Карточка 055: огонь на поражение в запретной полосе перед южным блокпостом. Полосы — из меток «kill_line»
 * площадки south_post (по 4 угла на прямоугольник, П-20, PR #99): Prefabs.killZones(уровень, земля, ур.).
 * В полосе игрок или одиночка — враг военных (KillZone.militaryHostile), за полосой — нейтрален.
 */
public class KillZoneTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        // синтетические точки: два прямоугольника
        List<int[]> pts = List.of(new int[]{0, 0}, new int[]{10, 0}, new int[]{0, 5}, new int[]{10, 5},
                new int[]{40, 40}, new int[]{30, 30}, new int[]{40, 30}, new int[]{30, 40});
        List<int[]> z = KillZone.fromPoints(pts);
        ck(z.size() == 2, "8 точек — 2 полосы: " + z.size());
        if (z.size() == 2) {
            ck(Arrays.equals(z.get(0), new int[]{0, 0, 10, 5}) && Arrays.equals(z.get(1), new int[]{30, 30, 40, 40}),
                    "углы в любом порядке → {x0, z0, x1, z1}: " + Arrays.toString(z.get(0)) + " " + Arrays.toString(z.get(1)));
        }
        ck(KillZone.inside(z, 5, 2.5) && KillZone.inside(z, 10, 5) && KillZone.inside(z, 35.5, 31), "точки внутри и на краю — в полосе");
        ck(!KillZone.inside(z, 11, 2) && !KillZone.inside(z, 20, 20), "за полосой — нет");
        ck(KillZone.militaryHostile(z, 5, 2) && !KillZone.militaryHostile(z, 5, 8), "в полосе — враг, за полосой — нейтрален");
        ck(KillZone.fromPoints(List.of(new int[]{1, 1}, new int[]{2, 2}, new int[]{3, 3})).isEmpty(), "неполная четвёрка точек — не полоса");

        // полосы южного блокпоста из меток сборщика
        Prefabs.Ground flat = (x, zz) -> 90;
        for (int lvl = 0; lvl <= 3; lvl++) {
            List<int[]> zones = Prefabs.killZones("l01_escape", flat, lvl);
            ck(zones.size() == 2, "ур. " + lvl + ": у южного блокпоста 2 полосы: " + zones.size());
            ck(KillZone.militaryHostile(zones, 92, 200), "ур. " + lvl + ": перед северным забором (92, 200) — огонь");
            ck(KillZone.militaryHostile(zones, 56, 199), "ур. " + lvl + ": на дороге перед проездом (56, 199) — огонь");
            ck(!KillZone.militaryHostile(zones, 92, 185), "ур. " + lvl + ": дальше в Зоне (92, 185) — не трогают");
            ck(!KillZone.militaryHostile(zones, 92, 230), "ур. " + lvl + ": внутри блокпоста (92, 230) — не трогают");
        }
        ck(Prefabs.killZones("l99_none", flat, 1).isEmpty(), "у уровня без площадок полос нет");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
