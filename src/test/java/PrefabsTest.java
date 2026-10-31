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

        // карточка 031: рабочие места построек
        java.util.List<Prefabs.Point> marks = Prefabs.points("l01_escape", (x, z) -> 75);
        int guard = 0, bed = 0, patrol = 0, inBounds = 0, bedsFree = 0, bedsTotal = 0;
        for (Prefabs.Point p : marks) {
            if (Prefabs.cleared("l01_escape", p.x(), p.z())) inBounds++;
            switch (p.kind()) {
                case "guard" -> guard++;
                case "bed" -> bed++;
                case "patrol" -> patrol++;
                default -> { }
            }
            if (p.kind().equals("bed")) {
                bedsTotal++;
                String st = m.get(Prefabs.key(p.x(), p.y(), p.z()));
                if (st == null || st.equals("minecraft:air")) bedsFree++;
            }
        }
        ck(guard >= 3, "часовых (guard) " + guard);
        ck(bed >= 10, "спальных мест (bed) " + bed);
        ck(patrol >= 4, "точек патруля " + patrol);
        ck(inBounds == marks.size(), "все точки в границах замены (" + inBounds + "/" + marks.size() + ")");
        ck(bedsFree == bedsTotal, "точки bed не в твёрдых блоках (" + bedsFree + "/" + bedsTotal + ")");
        ck(Prefabs.points("l02_garbage", (x, z) -> 75).isEmpty(), "другие уровни без рабочих мест");
        System.exit(f > 0 ? 1 : 0);
    }
}
