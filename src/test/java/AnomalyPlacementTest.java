import ru.stalkerzone.sim.*;
import ru.stalkerzone.world.ZoneBounds;

import java.util.*;

/**
 * Карточка 029: поля аномалий стоят на месте, аномалии не лезут в обжитые здания,
 * а артефакты появляются у края поля.
 */
public class AnomalyPlacementTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        fieldsStay();
        notInBases();
        artifactRing();
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** 20 выбросов: центры полей не меняются, аномалии не выходят за радиус своего поля. */
    static void fieldsStay() {
        boolean centers = true, inside = true, districts = true;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            int m = s.anomalyDistricts.size();
            if (m == 0) districts = false;
            int[] cx = new int[m], cz = new int[m];
            for (int i = 0; i < m; i++) {
                AnomalyDistrict d = s.anomalyDistricts.get(i);
                if (!d.isCentered()) districts = false;
                cx[i] = d.cdx; cz[i] = d.cdz;
            }
            for (int e = 0; e < 20; e++) s.triggerEmission();
            for (int i = 0; i < m; i++) {
                AnomalyDistrict d = s.anomalyDistricts.get(i);
                if (d.cdx != cx[i] || d.cdz != cz[i]) centers = false;
                for (Anomaly an : s.anomaliesAt(d.node)) {
                    if (Math.hypot(an.dx - d.cdx, an.dz - d.cdz) > d.radius + 0.5) inside = false;
                }
            }
        }
        ck(districts && centers, "20 выбросов: центр каждого поля остался на месте");
        ck(inside, "после 20 выбросов аномалии не выходят за радиус своего поля");
    }

    /** Ни одна аномалия не стоит в радиусе баз и посёлков — включая после выбросов. */
    static void notInBases() {
        boolean ok = true;
        int checked = 0;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int e = 0; e <= 5; e++) {
                for (Anomaly an : s.anomalies) {
                    Node n = s.nodes.get(an.node);
                    int bx = n.x * ZoneBounds.SCALE + an.dx, bz = n.z * ZoneBounds.SCALE + an.dz;
                    for (Node b : s.nodes) {
                        int c = s.anomalyClearance(b);
                        if (c <= 0) continue;
                        checked++;
                        if (Math.hypot(bx - b.x * ZoneBounds.SCALE, bz - b.z * ZoneBounds.SCALE) < c) ok = false;
                    }
                }
                s.triggerEmission();
            }
        }
        ck(ok, "ни одна аномалия не стоит в радиусе баз и посёлков (проверок: " + checked + ")");
    }

    /** Артефакт всегда на кольце 1–2 блока от границы радиуса поля. */
    static void artifactRing() {
        Random r = new Random(7);
        boolean ring = true;
        for (AnomalyType t : AnomalyType.ALL) {
            for (int i = 0; i < 500; i++) {
                double[] o = Anomaly.artifactOffset(t, r);
                double len = Math.hypot(o[0], o[1]);
                if (len <= t.radius - 2.0 || len > t.radius - 1.0 + 1e-9) ring = false;
            }
        }
        ck(ring, "артефакт всегда на кольце 1–2 блока от границы радиуса поля");
    }
}
