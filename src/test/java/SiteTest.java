import ru.stalkerzone.sim.KordonScenario;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.zonemap.build.Prefabs;
import ru.stalkerzone.zonemap.build.Site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** П-02: площадки — плато ровное, откос плавный, вне границ рельеф не меняется, NPC узла получают только свои места. */
public class SiteTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    /** Холмистая «деревня»: 80–86 с перепадом соседних колонок меньше блока. */
    static double hill(int x, int z) { return 83 + 2.2 * Math.sin(x / 9.0) + 1.6 * Math.cos(z / 7.0) + 0.02 * x; }

    static final class G implements Site.Grid {
        final Map<Long, Double> m = new HashMap<>();
        static long k(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
        public boolean contains(int x, int z) { return x >= -200 && x <= 200 && z >= -200 && z <= 300; }
        public double get(int x, int z) { return m.getOrDefault(k(x, z), hill(x, z)); }
        public void set(int x, int z, double y) { m.put(k(x, z), y); }
        int block(int x, int z) { return (int) Math.floor(Math.round(get(x, z) * 8) / 8.0); }   // как LevelRegion (×8)
    }

    public static void main(String[] a) {
        G g = new G();
        String log = Prefabs.shape("l01_escape", g);
        System.out.println(log);
        Site village = Prefabs.site("l01_escape", Prefabs.NODE_VILLAGE);
        ck(village != null, "площадка деревни есть");
        Site.Plateau p = Prefabs.plateauAt(0, 0);
        ck(p != null && !p.fixed(), "плато деревни AUTO");
        int top = p.top();
        // медиана исходной земли (шаг 2, как в Plateau.prepare)
        List<Integer> hs = new ArrayList<>();
        for (int x = p.x0; x <= p.x1; x += 2) for (int z = p.z0; z <= p.z1; z += 2) hs.add((int) Math.floor(hill(x, z)));
        hs.sort(Integer::compare);
        ck(top == hs.get(hs.size() / 2), "высота плато = медиана исходной земли: " + top);

        boolean flat = true;
        for (int x = p.x0; x <= p.x1; x++) for (int z = p.z0; z <= p.z1; z++) if (g.block(x, z) != top) flat = false;
        ck(flat, "плато ровное внутри");
        int worst = 0;
        int[] b = p.bounds();
        for (int x = b[0] - 2; x <= b[2] + 2; x++) for (int z = b[1] - 2; z <= b[3] + 2; z++) {
            worst = Math.max(worst, Math.abs(g.block(x, z) - g.block(x + 1, z)));
            worst = Math.max(worst, Math.abs(g.block(x, z) - g.block(x, z + 1)));
        }
        ck(worst <= 1, "откос плавный: перепад соседних колонок " + worst);
        int changed = 0;
        for (Map.Entry<Long, Double> e : g.m.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            boolean in = false;
            for (Site s : Prefabs.sites("l01_escape")) for (Site.Shaper sh : s.shapers()) {
                int[] r = sh.bounds();
                if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) in = true;
            }
            if (!in && Math.abs(e.getValue() - hill(x, z)) > 1e-9) changed++;
        }
        ck(changed == 0, "вне границ площадок рельеф не меняется (изменено " + changed + ")");
        ck(g.block(60, 220) == (int) Math.floor(hill(60, 220)), "у южного блокпоста рельеф прежний");

        // выемка только опускает землю
        Site.Trench t = new Site.Trench(142, -94, 174, -70, 90, 4, 6);
        boolean down = true, bottom = true;
        int[] tb = t.bounds();
        for (int x = tb[0]; x <= tb[2]; x++) for (int z = tb[1]; z <= tb[3]; z++) {
            double o = 96, v = t.shape(x, z, o);
            if (v > o + 1e-9) down = false;
            if (t.dist(x + 0.5, z + 0.5) <= 3 && Math.floor(v) != 90) bottom = false;
            if (t.shape(x, z, 85) != 85) down = false;   // где земля уже ниже дна — без изменений
        }
        ck(down, "выемка только опускает землю");
        ck(bottom, "дно выемки на заданной высоте");

        // рабочие места: каждый узел — только свои
        Prefabs.Ground gr = g::block;
        List<Prefabs.Point> vp = Prefabs.points("l01_escape", gr, Prefabs.POINT_LEVEL, Prefabs.NODE_VILLAGE);
        List<Prefabs.Point> sp = Prefabs.points("l01_escape", gr, Prefabs.POINT_LEVEL, Prefabs.NODE_SOUTH_POST);
        List<Prefabs.Point> bp = Prefabs.points("l01_escape", gr, Prefabs.POINT_LEVEL, Prefabs.NODE_BUNKER);
        boolean vin = !vp.isEmpty(), vy = true;
        for (Prefabs.Point q : vp) {
            if (!village.covers(q.x(), q.z())) vin = false;
            if (!q.kind().equals("shelter") && Prefabs.plateauAt(q.x(), q.z()) != null && q.y() != top + 1) vy = false;   // укрытия — в подвалах (П-03)
        }
        ck(vin, "места деревни (" + vp.size() + ") — в площадке деревни");
        ck(vy, "места деревни на высоте плато + 1 (кроме укрытий в подвалах)");
        String[] kinds = {"guard", "bed", "patrol", "cook", "rest"};
        for (String k : kinds) ck(vp.stream().anyMatch(q -> q.kind().equals(k)), "в деревне есть место " + k);
        Site south = Prefabs.site("l01_escape", Prefabs.NODE_SOUTH_POST);
        ck(!sp.isEmpty() && sp.stream().allMatch(q -> south.covers(q.x(), q.z())), "места южного блокпоста — только его");
        ck(vp.stream().noneMatch(q -> south.covers(q.x(), q.z())), "жители деревни не получают нар южного блокпоста");
        ck(bp.stream().anyMatch(q -> q.kind().equals("duty")) && bp.stream().noneMatch(q -> q.kind().equals("guard")),
                "у бункера — место торговца, без чужих часовых");
        ck(vp.size() + sp.size() + bp.size() == Prefabs.points("l01_escape", gr).size(), "сумма мест узлов = все места уровня");
        ck(Prefabs.points("l01_escape", gr, Prefabs.POINT_LEVEL, 5).isEmpty(), "у узла без площадки (АТП) мест нет");

        // центры узлов — в своих площадках (номера узлов не разъехались с KordonScenario)
        Simulation sim = KordonScenario.create(1);
        for (Site s : Prefabs.sites("l01_escape")) {
            Node n = sim.nodes.get(s.node);
            ck(s.covers(n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE), "узел " + s.node + " «" + n.name + "» в площадке " + s.id);
        }
        // бункер (≈ −35, −8) стоит на плато деревни
        ck(Prefabs.plateauAt(-35, -8) != null, "место бункера — на плато деревни");
        System.exit(f > 0 ? 1 : 0);
    }
}
