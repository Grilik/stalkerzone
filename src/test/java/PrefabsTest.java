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

        // карточка 035: бункер Сидоровича в списке сборщиков — своя граница, блоки, рабочее место торговца
        int bunkerSolid = 0, duty = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            if (e.getValue().equals("minecraft:air")) continue;
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey());
            boolean inBunker = x >= -43 && x <= -27 && z >= -15 && z <= -2;
            if (inBunker) bunkerSolid++;
        }
        ck(bunkerSolid > 100, "бункер: блоков " + bunkerSolid);
        for (Prefabs.Point p : marks) if (p.kind().equals("duty")) duty++;
        ck(duty >= 1, "рабочее место торговца (duty) " + duty);
        // карточка 039: мост и туннель с электрами — блоки в своих границах, туннель это настоящий проход
        int bridgeSolid = 0, tunnelSolid = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            if (e.getValue().equals("minecraft:air")) continue;
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey());
            if (x >= 215 && x <= 326 && z >= -325 && z <= -238) bridgeSolid++;
            if (x >= 95 && x <= 142 && z >= -273 && z <= -211) tunnelSolid++;
        }
        ck(bridgeSolid > 1500, "мост: блоков " + bridgeSolid);
        ck(tunnelSolid > 400, "туннель: блоков " + tunnelSolid);
        // туннель: сквозной проход по оси Z — в середине стены и арка, в створе воздух
        int fy = Integer.MIN_VALUE;   // пол туннеля: гравий в створе (floor-1)
        for (int y = 40; y <= 140; y++) if ("minecraft:gravel".equals(m.get(Prefabs.key(132, y, -266)))) fy = y + 1;
        boolean passage = fy != Integer.MIN_VALUE;
        for (int z = -268; passage && z <= -264; z++) {
            for (int x = 132; x <= 132; x++) for (int y = fy; y <= fy + 2; y++) {
                String c = m.get(Prefabs.key(x, y, z));
                if (c != null && !c.equals("minecraft:air")) passage = false;
            }
            for (int y = fy; y <= fy + 2; y++) {
                String w1 = m.get(Prefabs.key(130, y, z)), w2 = m.get(Prefabs.key(134, y, z));
                if (w1 == null || w1.equals("minecraft:air") || w2 == null || w2.equals("minecraft:air")) passage = false;
            }
        }
        ck(passage, "туннель: сквозной проход в створе" + (passage ? "" : " ЗАНЯТ"));
        // границы сборщиков не пересекаются
        java.util.List<int[]> cl = Prefabs.clear("l01_escape");
        boolean overlap = false;
        outer:
        for (int i = 0; i < cl.size(); i++) for (int j = i + 1; j < cl.size(); j++) {
            int[] ra = cl.get(i), rb = cl.get(j);
            if (ra[0] <= rb[2] && rb[0] <= ra[2] && ra[1] <= rb[3] && rb[1] <= ra[3]) { overlap = true; break outer; }
        }
        ck(!overlap && cl.size() >= 2, "сборщиков " + cl.size() + ", без пересечения границ");
        System.exit(f > 0 ? 1 : 0);
    }
}
