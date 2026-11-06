import ru.stalkerzone.sim.KordonScenario;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonElevator;
import ru.stalkerzone.zonemap.build.Prefabs;
import ru.stalkerzone.zonemap.build.Site;

import java.io.File;
import java.util.*;

/**
 * П-22: элеватор (узел 4, смарт-террейн 5_9) — площадка elevator, малая застава одиночек по вокселям оригинала.
 * Рельеф: наклонное поле ≈ 102,5, правится площадками уровня (плато AUTO).
 * Проверяется: площадка и плато; места на ур. 0–3 — bed 4, guard 2 (один на крыше башни), rest ≥ 1, shelter 3, костёр
 * в точке узла; места стоячие, укрытия под крышей; от костра дойти до всех мест, на крышу башни (по лестнице) и в галерею;
 * нары у bed; башня, силосы, галерея, коровник на месте; блоки мода существуют; всё в CLEAR; площадки не пересекаются.
 */
public class ElevatorTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static final String L = "l01_escape";

    static double field(int x, int z) { return 102.5 + 0.05 * (x - 205) - 0.04 * (z + 220); }

    static final class G implements Site.Grid {
        final Map<Long, Double> m = new HashMap<>();
        static long k(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
        public boolean contains(int x, int z) { return x >= -200 && x <= 700 && z >= -900 && z <= 300; }
        public double get(int x, int z) { Double v = m.get(k(x, z)); return v != null ? v : field(x, z); }
        public void set(int x, int z, double y) { m.put(k(x, z), y); }
        int block(int x, int z) { return (int) Math.floor(Math.round(get(x, z) * 8) / 8.0); }
    }

    static Map<Long, String> blocks;
    static G g;

    static String at(int x, int y, int z) { return blocks.get(Prefabs.key(x, y, z)); }
    static boolean thin(String s) { return s.equals("minecraft:air") || s.contains("ladder") || s.contains("cobweb"); }
    static boolean solid(int x, int y, int z) { String s = at(x, y, z); return s != null ? !thin(s) : y <= g.block(x, z); }
    static boolean free(int x, int y, int z) { return !solid(x, y, z); }
    static boolean stand(int x, int y, int z) { return free(x, y, z) && free(x, y + 1, z) && solid(x, y - 1, z); }

    static boolean near(Prefabs.Point p, String what, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -1; dy <= 2; dy++) {
            String b = at(p.x() + dx, p.y() + dy, p.z() + dz);
            if (b != null && b.contains(what)) return true;
        }
        return false;
    }

    public static void main(String[] a) {
        Site s = Prefabs.site(L, Prefabs.NODE_ELEVATOR);
        ck(s != null && s.id.equals("elevator") && Prefabs.NODE_ELEVATOR == 4, "у узла 4 площадка elevator");
        if (s == null) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }
        Node n = KordonScenario.create(1).nodes.get(4);
        ck(n.name.equals("Элеватор"), "узел 4 — «Элеватор» (" + n.name + ")");
        ck(s.covers(n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE), "центр узла 4 в площадке elevator");
        ck(s.covers(201, -222) && s.covers(KordonElevator.FIRE[0], KordonElevator.FIRE[1]), "точка узла (201, −222) и костёр (195, −227) в площадке");
        Site.Plateau pl = s.plateauAt(201, -222);
        ck(pl != null && !pl.fixed(), "плато AUTO");

        g = new G();
        Site.apply(Prefabs.sites(L), g);
        int top = g.block(201, -222);
        boolean flat = true;
        for (int x = KordonElevator.PLATEAU[0]; x <= KordonElevator.PLATEAU[2]; x += 3)
            for (int z = KordonElevator.PLATEAU[1]; z <= KordonElevator.PLATEAU[3]; z += 3) if (g.block(x, z) != top) flat = false;
        ck(flat && Math.abs(top - 102) <= 1, "плато ровное, y " + top + " (≈ 102)");

        for (int lvl = 0; lvl <= 3; lvl++) {
            blocks = new HashMap<>();
            List<Prefabs.Point> pts = new ArrayList<>();
            BlockSink sink = new BlockSink() {
                @Override public void set(int x, int y, int z, String st) { blocks.put(Prefabs.key(x, y, z), st); }
                @Override public int ground(int x, int z) { return g.block(x, z); }
                @Override public void mark(String kind, int x, int y, int z) { pts.add(new Prefabs.Point(kind, x, y, z)); }
            };
            for (Prefabs.Builder bb : s.builders()) bb.build(sink, lvl);
            String L0 = "ур. " + lvl + ": ";
            Map<String, Integer> kinds = new TreeMap<>();
            for (Prefabs.Point p : pts) kinds.merge(p.kind(), 1, Integer::sum);
            ck(kinds.getOrDefault("bed", 0) == 4 && kinds.getOrDefault("guard", 0) == 2 && kinds.getOrDefault("rest", 0) >= 1
                    && kinds.getOrDefault("shelter", 0) == 3, L0 + "места " + kinds + " (bed 4, guard 2, rest ≥ 1, shelter 3)");
            ck(Prefabs.points(L, (x, z) -> g.block(x, z), lvl, 4).size() == pts.size(), L0 + "Prefabs.points узла 4 — те же места");
            int gy = g.block(KordonElevator.TX0, KordonElevator.TZ0);
            ck(pts.stream().anyMatch(p -> p.kind().equals("guard") && p.x() > KordonElevator.TX0 && p.x() < KordonElevator.TX1
                    && p.z() > KordonElevator.TZ0 && p.z() < KordonElevator.TZ1 && p.y() >= gy + 13), L0 + "часовой наверху башни");
            String fire = at(KordonElevator.FIRE[0], g.block(KordonElevator.FIRE[0], KordonElevator.FIRE[1]) + 1, KordonElevator.FIRE[1]);
            ck(fire != null && fire.contains("campfire[lit=" + (lvl >= 1) + ""), L0 + "костёр в точке узла (" + fire + ")");
            for (Prefabs.Point p : pts) {
                String d = p.kind() + " (" + p.x() + "," + p.y() + "," + p.z() + ")";
                ck(s.covers(p.x(), p.z()), L0 + d + " в площадке");
                ck(stand(p.x(), p.y(), p.z()), L0 + d + " — можно стоять");
                if (p.kind().equals("shelter")) {
                    boolean roof = false;
                    for (int y = p.y() + 2; y <= p.y() + 9; y++) if (solid(p.x(), y, p.z())) roof = true;
                    ck(roof, L0 + d + " под крышей");
                }
                if (p.kind().equals("bed") && lvl >= 1) ck(near(p, "mattress", 1), L0 + d + " — рядом нары");
            }
            // обход от костра (3D, со ступенькой 1 и лестницами)
            int sx = KordonElevator.FIRE[0] + 3, sz = KordonElevator.FIRE[1], sy = g.block(sx, sz) + 1;
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> q = new ArrayDeque<>();
            ck(stand(sx, sy, sz), L0 + "у костра можно стоять");
            q.add(new int[] {sx, sy, sz}); seen.add(Prefabs.key(sx, sy, sz));
            int[] box = {KordonElevator.PLATEAU[0] - 2, KordonElevator.PLATEAU[1] - 2, KordonElevator.PLATEAU[2] + 2, KordonElevator.PLATEAU[3] + 2};
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + dd[0], y = c[1] + dy, z = c[2] + dd[1];
                    if (x < box[0] || x > box[2] || z < box[1] || z > box[3]) continue;
                    if (dy == 1 && !free(c[0], c[1] + 2, c[2])) continue;
                    if (stand(x, y, z) && seen.add(Prefabs.key(x, y, z))) q.add(new int[] {x, y, z});
                }
                String here = at(c[0], c[1], c[2]);
                if (here != null && here.contains("ladder")) for (int up = 1; up <= 16; up++) {
                    int y = c[1] + up;
                    String u = at(c[0], y, c[2]);
                    if (u == null || !u.contains("ladder")) break;
                    for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}}) {
                        int x = c[0] + dd[0], z = c[2] + dd[1];
                        for (int yy : new int[] {y, y + 1}) if (stand(x, yy, z) && seen.add(Prefabs.key(x, yy, z))) q.add(new int[] {x, yy, z});
                    }
                }
            }
            for (Prefabs.Point p : pts) ck(seen.contains(Prefabs.key(p.x(), p.y(), p.z())), L0 + "до " + p.kind() + " (" + p.x() + "," + p.y() + "," + p.z() + ") дойти от костра");
            boolean gal = false;
            for (int x = KordonElevator.TX0 - 15; x < KordonElevator.TX0 - 3; x++) if (seen.contains(Prefabs.key(x, gy + 9, -236)) || seen.contains(Prefabs.key(x, gy + 9, -235))) gal = true;
            ck(gal, L0 + "в галерею можно пройти с этажа башни");
            // облик по обмеру
            int silo = 0;
            for (int x = 205; x <= 222; x++) for (int y = gy + 2; y <= gy + 6; y++) { String b = at(x, y, -237); if (b != null && b.contains("rust")) silo++; }
            ck(silo > 20, L0 + "силосы (" + silo + ")");
            int barn = 0;
            for (int z = KordonElevator.WZ0; z <= KordonElevator.WZ1; z++) { String b = at(KordonElevator.WX0, gy + 1, z); if (b != null && b.contains("brick")) barn++; }
            ck(barn > 12, L0 + "коровник: кирпичная стена (" + barn + ")");
            int ridge = -1;
            for (int y = gy + 8; y <= gy + 14; y++) { String b = at(185, y, -223); if (b != null && b.contains("shifer")) ridge = y - gy; }
            ck(ridge >= 10 && ridge <= 12, L0 + "конёк коровника ≈ +11 (обмер 10): +" + ridge);
            Set<String> bad = new TreeSet<>();
            File bs = new File("src/main/resources/assets/stalkerzone/blockstates");
            for (String b : blocks.values()) {
                if (!b.startsWith("stalkerzone:")) continue;
                String id = b.substring(12, b.contains("[") ? b.indexOf('[') : b.length());
                if (!new File(bs, id + ".json").exists()) bad.add(id);
            }
            ck(bad.isEmpty(), L0 + "все блоки мода существуют " + bad);
            int out = 0;
            for (long k : blocks.keySet()) if (!s.covers(Prefabs.kx(k), Prefabs.kz(k))) out++;
            ck(out == 0, L0 + "все блоки в CLEAR (вне: " + out + ")");
        }
        for (Site o : Prefabs.sites(L)) {
            if (o == s) continue;
            boolean cross = false;
            for (int[] c : s.clearRects()) for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) if (o.covers(x, z)) cross = true;
            ck(!cross, "elevator не пересекается с площадкой " + o.id);
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
