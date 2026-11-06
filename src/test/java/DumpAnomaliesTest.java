import ru.stalkerzone.sim.*;

/**
 * Карточка 048: А6 «Свалка техники» (узел 20) — поле аномалий в чаше x −60..−10, z −145..−95
 * («комариные плеши» и трамплины), радиация; после выброса аномалии переезжают, но остаются в чаше, артефакты созревают.
 */
public class DumpAnomaliesTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = Scenarios.create("kordon", 5);
        AnomalyDistrict d = null;
        for (AnomalyDistrict x : s.anomalyDistricts) if (x.node == 20) d = x;
        ck(d != null, "у узла 20 есть район аномалий");
        if (d == null) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }
        boolean mosquito = false, spring = false;
        for (AnomalyType t : d.pool) { if (t == AnomalyType.MOSQUITO) mosquito = true; if (t == AnomalyType.SPRINGBOARD) spring = true; }
        ck(mosquito && spring, "в районе — «комариные плеши» и трамплины");
        ck(d.min >= 6, "полей в А6 много: не меньше 6 (min " + d.min + ")");
        boolean rad = false;
        for (int r : s.radiationNodes) if (r == 20) rad = true;
        ck(rad, "А6 — радиоактивное пятно (radiationNodes)");

        Node n = s.nodes.get(20);
        boolean inside = true, ripe = true, moved = false;
        int count = Integer.MAX_VALUE;
        String before = null;
        for (int e = 0; e < 10; e++) {
            s.triggerEmission();
            StringBuilder sb = new StringBuilder();
            int c = 0;
            for (Anomaly an : s.anomalies) {
                if (an.node != 20) continue;
                c++;
                int x = n.x * 10 + an.dx, z = n.z * 10 + an.dz;
                if (x < -60 || x > -10 || z < -145 || z > -95) { inside = false; System.out.println("  вне чаши: " + x + ", " + z + " " + an.type); }
                if (an.pending < 1) ripe = false;
                sb.append(an.dx).append(',').append(an.dz).append(' ');
            }
            count = Math.min(count, c);
            if (before != null && !before.equals(sb.toString())) moved = true;
            before = sb.toString();
        }
        ck(count >= 6, "после каждого выброса в А6 не меньше 6 аномалий (мин " + count + ")");
        ck(inside, "10 выбросов: все аномалии А6 внутри чаши x −60..−10, z −145..−95");
        ck(moved, "после выброса аномалии А6 переезжают");
        ck(ripe, "после выброса в каждой аномалии А6 созрел артефакт");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
