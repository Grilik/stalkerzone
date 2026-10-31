import ru.stalkerzone.zonemap.build.Prefabs;

import java.util.Map;

/** Постройки по оригиналу: упаковка координат обратима, блокпост попадает в свои границы. */
public class PrefabsTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) {
        int[][] pts = {{63, 75, 223}, {-148, -64, -955}, {655, 300, 492}, {-100000, 0, 100000}, {0, 0, 0}};
        boolean ok = true;
        for (int[] p : pts) {
            long k = Prefabs.key(p[0], p[1], p[2]);
            ok &= Prefabs.kx(k) == p[0] && Prefabs.ky(k) == p[1] && Prefabs.kz(k) == p[2];
        }
        ck(ok, "key/kx/ky/kz обратимы");
        Map<Long, String> m = Prefabs.build("l01_escape", (x, z) -> 75, 1);
        int solid = 0, outside = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            if (e.getValue().equals("minecraft:air")) continue;
            solid++;
            if (!Prefabs.cleared("l01_escape", Prefabs.kx(e.getKey()), Prefabs.kz(e.getKey()))) outside++;
        }
        ck(solid > 3000, "блокпост: блоков " + solid);
        ck(outside == 0, "все блоки в границах замены (вне: " + outside + ")");
        ck(Prefabs.build("l02_garbage", (x, z) -> 75, 1).isEmpty(), "другие уровни без построек");
        System.exit(f > 0 ? 1 : 0);
    }
}
