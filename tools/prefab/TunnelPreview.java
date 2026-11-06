import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonTunnel;
import ru.stalkerzone.zonemap.build.Prefabs;
import ru.stalkerzone.zonemap.build.Site;
import java.io.PrintWriter;
import java.util.*;

/** java TunnelPreview <out.txt> <lvl> [z<=N|x<=N|y<=N] — туннель на синтетической насыпи после правки рельефа площадкой. */
public class TunnelPreview {
    static Map<Long, Double> ov = new HashMap<>();
    static long k(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    static double base(int x, int z) {
        double d = Math.abs(z + 262) - 4;
        return 100 + Math.max(0, Math.min(10, 10 - 1.2 * Math.max(0, d)));
    }
    static int gb(int x, int z) { Double v = ov.get(k(x, z)); double y = v != null ? v : base(x, z); return (int) Math.floor(Math.round(y * 8) / 8.0); }
    static long kk(int x, int y, int z) { return ((long) x << 40) | ((long) (y + 2048) << 20) | (z + 2048); }
    public static void main(String[] a) throws Exception {
        Site.Grid g = new Site.Grid() {
            public boolean contains(int x, int z) { return true; }
            public double get(int x, int z) { Double v = ov.get(k(x, z)); return v != null ? v : base(x, z); }
            public void set(int x, int z, double y) { ov.put(k(x, z), y); }
        };
        System.out.println(Prefabs.shape("l01_escape", g));
        int lvl = Integer.parseInt(a[1]);
        String cut = a.length > 2 ? a[2] : null;
        int[] c = KordonTunnel.CLEAR.get(0);
        int x0 = c[0] - 3, z0 = c[1] - 3, x1 = c[2] + 3, z1 = c[3] + 3, bs = 90;
        Map<Long, String> m = new LinkedHashMap<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int gg = gb(x, z);
            for (int y = bs; y <= gg; y++) m.put(kk(x, y, z), y == gg ? "minecraft:grass_block" : y > gg - 3 ? "minecraft:dirt" : "minecraft:stone");
        }
        List<String> marks = new ArrayList<>();
        BlockSink s = new BlockSink() {
            public void set(int x, int y, int z, String st) { m.put(kk(x, y, z), st); }
            public int ground(int x, int z) { return gb(x, z); }
            public void mark(String kind, int x, int y, int z) { marks.add(kind + " " + x + " " + y + " " + z); }
        };
        KordonTunnel.build(s, lvl);
        int ox = x0, oy = bs, oz = z0;
        try (PrintWriter w = new PrintWriter(a[0], "UTF-8")) {
            for (Map.Entry<Long, String> e : m.entrySet()) {
                if (e.getValue().equals("minecraft:air")) continue;
                long q = e.getKey();
                int x = (int) (q >> 40), y = (int) ((q >> 20) & 0xfffff) - 2048, z = (int) (q & 0xfffff) - 2048;
                if (cut != null && cut.startsWith("z<=") && z > Integer.parseInt(cut.substring(3))) continue;
                if (cut != null && cut.startsWith("x<=") && x > Integer.parseInt(cut.substring(3))) continue;
                if (cut != null && cut.startsWith("y<=") && y > Integer.parseInt(cut.substring(3))) continue;
                w.println((x - ox) + " " + (y - oy) + " " + (z - oz) + " " + e.getValue());
            }
        }
        try (PrintWriter w = new PrintWriter(a[0] + ".marks", "UTF-8")) {
            w.println("# origin " + ox + " " + oy + " " + oz);
            for (String mk : marks) w.println(mk);
        }
    }
}
