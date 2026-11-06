import ru.stalkerzone.sim.*;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.zonemap.build.*;

import java.util.*;

/**
 * Карточка 053: логово Л3 в мире — площадка «lair» узла 21 ставит Structures.lairDen у (−92, −100)
 * (без плато), постройка в границах площадки, место логова «den» для мутантов.
 */
public class LairDenSiteTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }
    static final String L = "l01_escape";

    public static void main(String[] a) {
        Site s = Prefabs.site(L, 21);
        ck(s != null && s.id.equals("lair"), "у узла 21 площадка lair");
        if (s == null) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }
        Node n = KordonScenario.create(1).nodes.get(21);
        ck(s.covers(-92, -100) && s.covers(n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE), "площадка покрывает логово (−92, −100) и центр узла 21");
        ck(s.plateauAt(-92, -100) == null, "без плато: рельеф леса не выравнивается");
        int area = 0;
        for (int[] c : s.clearRects()) area += (c[2] - c[0] + 1) * (c[3] - c[1] + 1);
        ck(area > 0 && area <= 40 * 40, "площадка логова небольшая (≤ 40×40): " + area);

        Prefabs.Ground g = (x, z) -> 94;
        Map<Long, String> m = Prefabs.build(L, g, Prefabs.POINT_LEVEL);
        int inLair = 0, outside = 0, bones = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey()), y = Prefabs.ky(e.getKey());
            if (Math.hypot(x + 92, z + 100) > 16) continue;
            if (!s.covers(x, z)) { outside++; continue; }
            inLair++;
            if (y < 90 || y > 96) outside++;
            if (e.getValue().contains("bone_block") || e.getValue().contains("skeleton_skull")) bones++;
        }
        ck(inLair > 0 && bones > 0, "у (−92, −100) стоит логово: кости, череп (" + inLair + " блоков)");
        ck(outside == 0, "все блоки логова в площадке и у земли (вне: " + outside + ")");

        List<Prefabs.Point> pts = Prefabs.points(L, g, Prefabs.POINT_LEVEL, 21);
        ck(pts.stream().anyMatch(p -> p.kind().equals("den") && Math.hypot(p.x() + 92, p.z() + 100) <= 2 && p.y() == 95),
                "место логова den у (−92, −100) на земле + 1");
        ck(pts.stream().noneMatch(p -> p.kind().equals("bed") || p.kind().equals("guard")), "у логова нет мест людей");
        for (Site o : Prefabs.sites(L)) {
            if (o == s) continue;
            ck(!o.covers(-92, -100), "логово не внутри площадки " + o.id);
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
