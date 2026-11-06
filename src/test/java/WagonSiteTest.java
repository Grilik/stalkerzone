import ru.stalkerzone.sim.*;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.zonemap.build.*;

import java.util.*;

/**
 * Карточка 045: площадки «wagon» (лагерь у вагончика О7 и дорожный мост, узел 19) и «dump» (свалка техники А6 и застава О12,
 * узел 20, сборщик KordonDump из PR #86) подключены к своим узлам —
 * NPC узла берут только её места (NpcManager.prefabPoints → Prefabs.points(level, g, lvl, node)), чужие узлы их не берут.
 */
public class WagonSiteTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }
    static final String L = "l01_escape";

    public static void main(String[] a) {
        Prefabs.Ground flat = (x, z) -> 95;
        Site w = Prefabs.site(L, 19);
        ck(w != null && w.id.equals("wagon"), "у узла 19 площадка wagon");
        if (w == null) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }

        Simulation sim = KordonScenario.create(1);
        Node n = sim.nodes.get(19);
        ck(w.covers(n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE), "центр узла 19 в площадке wagon");
        ck(w.plateauAt(120, -75) != null, "плато лагеря x 104..130, z −88..−60");
        ck(w.covers(158, -81), "дорожный мост (158, −81) — в площадке wagon");

        for (int lvl = 0; lvl <= 3; lvl++) {
            List<Prefabs.Point> pts = Prefabs.points(L, flat, lvl, 19);
            final int lv = lvl;
            ck(!pts.isEmpty() && pts.stream().allMatch(q -> w.covers(q.x(), q.z())), "ур. " + lv + ": места узла 19 — только в площадке wagon (" + pts.size() + ")");
            for (String k : new String[]{"guard", "bed", "cook"})
                ck(pts.stream().anyMatch(q -> q.kind().equals(k)), "ур. " + lv + ": у О7 есть место " + k);
            ck(pts.stream().anyMatch(q -> q.kind().equals("shelter")), "ур. " + lv + ": у О7 есть укрытие shelter (под мостом)");
        }
        for (int other : new int[]{Prefabs.NODE_VILLAGE, Prefabs.NODE_BUNKER, Prefabs.NODE_SOUTH_POST}) {
            List<Prefabs.Point> pts = Prefabs.points(L, flat, Prefabs.POINT_LEVEL, other);
            ck(pts.stream().noneMatch(q -> w.covers(q.x(), q.z())), "узел " + other + " не получает мест О7");
        }
        // dump: свалка техники и застава О12 (узел 20)
        Site d = Prefabs.site(L, 20);
        ck(d != null && d.id.equals("dump"), "у узла 20 площадка dump");
        if (d != null) {
            Node dn = sim.nodes.get(20);
            ck(d.covers(dn.x * ZoneBounds.SCALE, dn.z * ZoneBounds.SCALE), "центр узла 20 в площадке dump");
            ck(d.plateauAt(-38, -121) != null, "плато дна чаши x −54..−22, z −134..−108");
            List<Prefabs.Point> dp = Prefabs.points(L, flat, Prefabs.POINT_LEVEL, 20);
            ck(!dp.isEmpty() && dp.stream().allMatch(q -> d.covers(q.x(), q.z())), "места узла 20 — только в площадке dump (" + dp.size() + ")");
            ck(dp.stream().anyMatch(q -> q.kind().equals("guard")) && dp.stream().anyMatch(q -> q.kind().equals("bed")), "у О12 есть guard и bed");
            List<Prefabs.Point> vp = Prefabs.points(L, flat, Prefabs.POINT_LEVEL, Prefabs.NODE_VILLAGE);
            ck(vp.stream().noneMatch(q -> d.covers(q.x(), q.z())), "деревня не получает мест О12");
        }
        // площадки не перекрываются
        for (Site s : Prefabs.sites(L)) {
            if (s == w) continue;
            boolean cross = false;
            for (int[] c : w.clearRects()) for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) if (s.covers(x, z)) cross = true;
            ck(!cross, "wagon не пересекается с площадкой " + s.id);
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
