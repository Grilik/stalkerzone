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
        ck(guard >= 4, "часовых (guard) " + guard);
        ck(bed >= 14, "спальных мест (bed) " + bed);
        ck(patrol >= 16, "точек патруля " + patrol);
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
        // карточка 037: элеватор в списке сборщиков — свои границы, блоки, рабочие места
        int elevatorSolid = 0, cook = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            if (e.getValue().equals("minecraft:air")) continue;
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey());
            boolean inElevator = (x >= 128 && x <= 141 && z >= -272 && z <= -255)
                    || (x >= 175 && x <= 246 && z >= -247 && z <= -158);
            if (inElevator) elevatorSolid++;
        }
        ck(elevatorSolid > 500, "элеватор: блоков " + elevatorSolid);
        for (Prefabs.Point p : marks) if (p.kind().equals("cook")) cook++;
        ck(cook >= 1, "рабочее место повара (cook) " + cook);

        // карточка 040: дальний блокпост — свои блоки и рабочие места в его границах
        int farSolid = 0, farGate = 0, farGuard = 0, farCook = 0, farBed = 0, farPatrol = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            if (e.getValue().equals("minecraft:air")) continue;
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey());
            if (x >= 214 && x <= 280 && z >= -830 && z <= -750) farSolid++;
        }
        for (Prefabs.Point p : marks) {
            if (p.x() < 214 || p.x() > 280 || p.z() < -830 || p.z() > -750) continue;
            switch (p.kind()) {
                case "gate" -> farGate++;
                case "guard" -> farGuard++;
                case "cook" -> farCook++;
                case "bed" -> farBed++;
                case "patrol" -> farPatrol++;
                default -> { }
            }
        }
        ck(farSolid > 500, "дальний блокпост: блоков " + farSolid);
        ck(farGate >= 1 && farGuard >= 1 && farCook >= 1 && farBed >= 1 && farPatrol >= 1,
                "дальний блокпост: gate/guard/cook/bed/patrol " + farGate + "/" + farGuard + "/" + farCook + "/" + farBed + "/" + farPatrol);
        // границы сборщиков не пересекаются
        java.util.List<int[]> cl = Prefabs.clear("l01_escape");
        boolean overlap = false;
        outer:
        for (int i = 0; i < cl.size(); i++) for (int j = i + 1; j < cl.size(); j++) {
            int[] ra = cl.get(i), rb = cl.get(j);
            if (ra[0] <= rb[2] && rb[0] <= ra[2] && ra[1] <= rb[3] && rb[1] <= ra[3]) { overlap = true; break outer; }
        }
        ck(!overlap && cl.size() >= 5, "сборщиков " + cl.size() + ", без пересечения границ");

        // карточка 036: АТП — гаражи, мастерские, вышки, техника, забор
        int atpSolid = 0, workMark = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            if (e.getValue().equals("minecraft:air")) continue;
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey());
            boolean inATP = x >= 288 && x <= 356 && z >= -160 && z <= -86;
            if (inATP) atpSolid++;
        }
        ck(atpSolid > 2000, "АТП: блоков " + atpSolid);
        // проверка, что у АТП есть рабочие места всех нужных видов
        int atpGuard = 0, atpBed = 0, atpPatrol = 0, atpWork = 0;
        for (Prefabs.Point p : marks) {
            boolean inATP = p.x() >= 288 && p.x() <= 356 && p.z() >= -160 && p.z() <= -86;
            if (!inATP) continue;
            switch (p.kind()) {
                case "guard" -> atpGuard++;
                case "bed" -> atpBed++;
                case "patrol" -> atpPatrol++;
                case "work" -> atpWork++;
                default -> { }
            }
        }
        ck(atpGuard >= 2, "АТП: часовых (guard) " + atpGuard);
        ck(atpBed >= 4, "АТП: спальных мест (bed) " + atpBed);
        ck(atpPatrol >= 4, "АТП: точек патруля " + atpPatrol);
        ck(atpWork >= 3, "АТП: рабочих мест у техники (work) " + atpWork);
        System.exit(f > 0 ? 1 : 0);
    }
}
