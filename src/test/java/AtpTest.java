import ru.stalkerzone.sim.KordonScenario;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonATP;
import ru.stalkerzone.zonemap.build.Prefabs;
import ru.stalkerzone.zonemap.build.Site;

import java.io.File;
import java.util.*;

/**
 * П-21: АТП (узел 5, смарт-террейн 7_11) — площадка atp, брошенная база по вокселям оригинала.
 * Рельеф: наклонное поле ≈ 92 (перепад ±4 по площадке), правится площадками уровня (плато AUTO).
 * Проверяется: площадка у узла 5 и плато, ровно 4 shelter + 2 work и никаких жилых мест на уровнях 0–3,
 * места стоячие и под крышей, ко всем можно дойти от ворот (обход в 3D), у мест хабара — ящики, разруха
 * (нет дверей, света, кроватей; окна выбиты), постройки по обмеру на месте, блоки мода существуют, площадки не пересекаются.
 */
public class AtpTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static final String L = "l01_escape";

    static double field(int x, int z) { return 92.4 + 0.06 * (x - 320) - 0.05 * (z + 125); }

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

    static boolean thin(String s) {
        return s.equals("minecraft:air") || s.contains("ladder") || s.contains("cobweb") || s.contains("chain");
    }

    static boolean solid(int x, int y, int z) {
        String s = at(x, y, z);
        if (s != null) return !thin(s);
        return y <= g.block(x, z);
    }

    static boolean free(int x, int y, int z) { return !solid(x, y, z); }

    static boolean stand(int x, int y, int z) { return free(x, y, z) && free(x, y + 1, z) && solid(x, y - 1, z); }

    public static void main(String[] a) {
        Site s = Prefabs.site(L, Prefabs.NODE_ATP);
        ck(s != null && s.id.equals("atp") && Prefabs.NODE_ATP == 5, "у узла 5 площадка atp");
        if (s == null) { System.out.println("ПРОВАЛОВ: " + f); System.exit(1); }
        Simulation sim = KordonScenario.create(1);
        Node n = sim.nodes.get(5);
        ck(n.name.equals("АТП"), "узел 5 — «АТП» (" + n.name + ")");
        ck(s.covers(n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE), "центр узла 5 в площадке atp");
        ck(s.covers(327, -124), "точка узла (327, −124) из kordon_points.json — в площадке");
        Site.Plateau pl = s.plateauAt(327, -124);
        ck(pl != null && !pl.fixed(), "плато AUTO под двором");

        // рельеф: правка площадками; плато ровное
        g = new G();
        Site.apply(Prefabs.sites(L), g);
        int top = g.block(327, -124);
        boolean flat = true;
        for (int x = KordonATP.PLATEAU[0]; x <= KordonATP.PLATEAU[2]; x += 3)
            for (int z = KordonATP.PLATEAU[1]; z <= KordonATP.PLATEAU[3]; z += 3) if (g.block(x, z) != top) flat = false;
        ck(flat && Math.abs(top - 92) <= 1, "плато ровное, y " + top + " (≈ 92)");

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
            long shelter = pts.stream().filter(p -> p.kind().equals("shelter")).count();
            long work = pts.stream().filter(p -> p.kind().equals("work")).count();
            ck(shelter == 4 && work == 2 && pts.size() == 6, L0 + "места: shelter " + shelter + ", work " + work + ", всего " + pts.size() + " (нужно 4 + 2)");
            List<Prefabs.Point> sp = Prefabs.points(L, (x, z) -> g.block(x, z), lvl, 5);
            ck(sp.size() == pts.size(), L0 + "Prefabs.points узла 5 отдаёт те же места (" + sp.size() + ")");
            for (Prefabs.Point p : pts) {
                String d = p.kind() + " (" + p.x() + "," + p.y() + "," + p.z() + ")";
                ck(s.covers(p.x(), p.z()), L0 + d + " в площадке");
                ck(stand(p.x(), p.y(), p.z()), L0 + d + " — можно стоять (2 блока воздуха, пол под ногами)");
                boolean roof = false;
                for (int y = p.y() + 2; y <= p.y() + 9; y++) if (solid(p.x(), y, p.z())) roof = true;
                if (p.kind().equals("shelter")) ck(roof, L0 + d + " под крышей");
                if (p.kind().equals("work")) {
                    int crates = 0;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++) {
                        String b = at(p.x() + dx, p.y() + dy, p.z() + dz);
                        if (b != null && (b.contains("crate"))) crates++;
                    }
                    ck(crates >= 2, L0 + d + " — у места хабара ящики (" + crates + ")");
                }
            }
            // проходимость: обход от ворот в заборе (301, −98)
            int sx = 301, sz = -98, sy = g.block(sx, sz) + 1;
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> q = new ArrayDeque<>();
            if (stand(sx, sy, sz)) { q.add(new int[] {sx, sy, sz}); seen.add(Prefabs.key(sx, sy, sz)); }
            int[] box = {KordonATP.PLATEAU[0] - 2, KordonATP.PLATEAU[1] - 2, KordonATP.PLATEAU[2] + 2, KordonATP.PLATEAU[3] + 2};
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + dd[0], y = c[1] + dy, z = c[2] + dd[1];
                    if (x < box[0] || x > box[2] || z < box[1] || z > box[3]) continue;
                    if (dy == 1 && !free(c[0], c[1] + 2, c[2])) continue;   // голова при шаге вверх
                    if (!stand(x, y, z)) continue;
                    if (seen.add(Prefabs.key(x, y, z))) q.add(new int[] {x, y, z});
                }
                // лестница: подняться по ladder
                String here = at(c[0], c[1], c[2]);
                if (here != null && here.contains("ladder")) for (int up = 1; up <= 6; up++) {
                    int y = c[1] + up;
                    String u = at(c[0], y, c[2]);
                    if (u == null || !u.contains("ladder")) break;
                    for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int x = c[0] + dd[0], z = c[2] + dd[1];
                        if (stand(x, y + 1, z) && seen.add(Prefabs.key(x, y + 1, z))) q.add(new int[] {x, y + 1, z});
                    }
                }
            }
            for (Prefabs.Point p : pts) ck(seen.contains(Prefabs.key(p.x(), p.y(), p.z())),
                    L0 + "до " + p.kind() + " (" + p.x() + "," + p.z() + ") можно дойти от ворот");
            // второй этаж конторы достижим по стремянке
            int y2 = g.block(318, -146) + 5;
            ck(seen.contains(Prefabs.key(320, y2, -144)) || seen.contains(Prefabs.key(320, y2, -142)), L0 + "второй этаж конторы достижим по стремянке");

            // разруха: ни дверей, ни света, ни кроватей; окна выбиты
            int doors = 0, light = 0, beds = 0, glass = 0;
            for (String b : blocks.values()) {
                if (b.contains("_door[") || b.endsWith("_door")) doors++;
                if (b.contains("lantern") || b.contains("torch") || b.contains("campfire") || b.contains("lamp")) light++;
                if (b.contains("_bed") || b.contains("mattress")) beds++;
                if (b.contains("glass")) glass++;
            }
            ck(doors == 0 && light == 0 && beds == 0, L0 + "брошено: дверей " + doors + ", света " + light + ", лежанок " + beds);
            ck(glass <= 12, L0 + "окна выбиты (осколков стекла " + glass + ")");
            // постройки по обмеру
            int y = g.block(318, -146);
            int[] wl = new int[2], wt = new int[2];
            for (int z = KordonATP.KZ0; z <= KordonATP.KZ1; z++) for (int fl = 0; fl < 2; fl++) for (int dy = 0; dy < 3; dy++) {
                int yy = y + 1 + fl * 4 + dy;
                for (int x : new int[] {KordonATP.KX0, KordonATP.KX1}) { wt[fl]++; if (solid(x, yy, z)) wl[fl]++; }
            }
            ck(wl[0] > wt[0] * 0.6 && wl[1] > wt[1] * 0.5, L0 + "контора: стены двух этажей (" + wl[0] + "/" + wt[0] + ", " + wl[1] + "/" + wt[1] + ")");
            int ridge = 0;
            for (int yy = y + 8; yy <= y + 13; yy++) if (at(318, yy, -145) != null && at(318, yy, -145).contains("boards_old_slab")) ridge = yy - y;
            ck(ridge >= 9 && ridge <= 11, L0 + "конёк конторы ≈ +11 (обмер 11): +" + ridge);
            ck(at(KordonATP.BX0, y + 1, -137) != null && at(KordonATP.BX0, y + 1, -137).equals("minecraft:air")
                    && at(KordonATP.BX0, y + 1, -131).equals("minecraft:air") && at(KordonATP.BX0, y + 1, -127).equals("minecraft:air"),
                    L0 + "боксы: трое ворот на запад открыты");
            int truck = 0;
            for (int x = 333; x <= 341; x++) for (int z = -133; z <= -130; z++) for (int yy = y + 1; yy <= y + 4; yy++)
                if (at(x, yy, z) != null && !at(x, yy, z).equals("minecraft:air")) truck++;
            ck(truck > 10, L0 + "в среднем боксе грузовик (" + truck + " блоков)");
            boolean tank = false;
            for (int yy = y + 9; yy <= y + 12; yy++) if (at(292, yy, -108) != null && at(292, yy, -108).contains("rust")) tank = true;
            ck(tank, L0 + "водокачка: бак на высоте");
            int hall = 0;
            for (int yy = y + 7; yy <= y + 12; yy++) if (at(316, yy, -116) != null && at(316, yy, -116).contains("shifer")) hall++;
            ck(hall >= 1, L0 + "мастерская: высокий цех под шифером");
            int fence = 0;
            for (int x = 292; x <= 353; x++) if ("stalkerzone:concrete_fence".equals(at(x, g.block(x, -94) + 1, -94))) fence++;
            ck(fence > 30 && fence < 57, L0 + "забор по югу с проломами (" + fence + " из 57)");
            for (int x = 299; x <= 303; x++) ck(at(x, g.block(x, -94) + 1, -94) == null, L0 + "проём ворот x " + x);
            // блоки мода существуют (иначе в мире молча воздух)
            Set<String> bad = new TreeSet<>();
            File bs = new File("src/main/resources/assets/stalkerzone/blockstates");
            for (String b : blocks.values()) {
                if (!b.startsWith("stalkerzone:")) continue;
                String id = b.substring(12, b.contains("[") ? b.indexOf('[') : b.length());
                if (!new File(bs, id + ".json").exists()) bad.add(id);
            }
            ck(bad.isEmpty(), L0 + "все блоки мода существуют " + bad);
            // всё в границах замены
            int out = 0;
            for (long k : blocks.keySet()) if (!s.covers(Prefabs.kx(k), Prefabs.kz(k))) out++;
            ck(out == 0, L0 + "все блоки в границах CLEAR (вне: " + out + ")");
        }
        // площадки не пересекаются
        for (Site o : Prefabs.sites(L)) {
            if (o == s) continue;
            boolean cross = false;
            for (int[] c : s.clearRects()) for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) if (o.covers(x, z)) cross = true;
            ck(!cross, "atp не пересекается с площадкой " + o.id);
        }
        // чужие узлы мест АТП не получают
        for (int other : new int[] {Prefabs.NODE_VILLAGE, Prefabs.NODE_BUNKER, Prefabs.NODE_SOUTH_POST, Prefabs.NODE_TUNNEL, Prefabs.NODE_WAGON})
            ck(Prefabs.points(L, (x, z) -> 92, Prefabs.POINT_LEVEL, other).stream().noneMatch(p -> s.covers(p.x(), p.z())), "узел " + other + " не получает мест АТП");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
