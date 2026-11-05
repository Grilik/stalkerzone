import ru.stalkerzone.world.xray.LevelRegion;
import ru.stalkerzone.zonemap.BlockSink;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Превью любого сборщика zonemap/build: рельеф (из .szl или ровная площадка) + постройка → дамп «x y z состояние»
 * для tools/iso_tex.py и tools/prefab/ortho.py.
 * java PrefabPreview <l01_escape.szl | flat:Y> <out.txt> <уровень 0..3> <Класс, напр. KordonBunker> [разрез: z<=N | x<=N | y<=N]
 * Ровная площадка (flat:Y) — то, что даст площадка Site; .szl не нужен. Разрез срезает всё южнее z=N (восточнее x=N, выше y=N)
 * и показывает грунт до глубины 8 — видно подземные помещения.
 */
public class PrefabPreview {
    public static void main(String[] a) throws Exception {
        LevelRegion r = null;
        int flat = 0;
        if (a[0].startsWith("flat:")) flat = Integer.parseInt(a[0].substring(5));
        else try (FileInputStream in = new FileInputStream(a[0])) { r = LevelRegion.load("l01_escape", in); }
        final LevelRegion rr = r;
        final int fy = flat;
        java.util.function.IntBinaryOperator ground = (x, z) -> rr != null ? rr.groundBlock(x, z) : fy;
        int lvl = Integer.parseInt(a[2]);
        Class<?> cls = Class.forName("ru.stalkerzone.zonemap.build." + a[3]);
        @SuppressWarnings("unchecked") List<int[]> clear = (List<int[]>) cls.getField("CLEAR").get(null);
        String cut = a.length > 4 ? a[4] : null;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] c : clear) { x0 = Math.min(x0, c[0]); z0 = Math.min(z0, c[1]); x1 = Math.max(x1, c[2]); z1 = Math.max(z1, c[3]); }
        x0 -= 3; z0 -= 3; x1 += 3; z1 += 3;
        int base = Integer.MAX_VALUE;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) base = Math.min(base, ground.applyAsInt(x, z));
        int depth = 9;   // грунт до глубины 9 — видно подземные помещения в разрезах
        Map<Long, String> m = new LinkedHashMap<>();
        boolean floorCut = cut != null && cut.startsWith("y<=");   // срез по высоте: без грунта, только постройка
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (floorCut) break;
            int g = ground.applyAsInt(x, z);
            for (int y = Math.min(base, g) - depth; y <= g; y++)
                m.put(k(x, y, z), y == g ? (rr != null && rr.surface(x, z) == 6 ? "minecraft:gray_concrete" : "minecraft:grass_block")
                        : y > g - 3 ? "minecraft:dirt" : "minecraft:stone");
        }
        java.util.List<String> marks = new java.util.ArrayList<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { m.put(k(x, y, z), st); }
            @Override public int ground(int x, int z) { return ground.applyAsInt(x, z); }
            @Override public void mark(String kind, int x, int y, int z) { marks.add(kind + " " + x + " " + y + " " + z); }
        };
        cls.getMethod("build", BlockSink.class, int.class).invoke(null, s, lvl);
        int ox = x0, oz = z0, oy = base - depth;
        try (PrintWriter w = new PrintWriter(a[1], "UTF-8")) {
            for (Map.Entry<Long, String> e : m.entrySet()) {
                if (e.getValue().equals("minecraft:air")) continue;
                long kk = e.getKey();
                int x = (int) (kk >> 40), y = (int) ((kk >> 20) & 0xfffff) - 2048, z = (int) (kk & 0xfffff) - 2048;
                if (cut != null && cut.startsWith("z<=") && z > Integer.parseInt(cut.substring(3))) continue;
                if (cut != null && cut.startsWith("x<=") && x > Integer.parseInt(cut.substring(3))) continue;
                if (cut != null && cut.startsWith("y<=") && y > Integer.parseInt(cut.substring(3))) continue;
                w.println((x - ox) + " " + (y - oy) + " " + (z - oz) + " " + e.getValue());
            }
        }
        try (PrintWriter w = new PrintWriter(a[1] + ".marks", "UTF-8")) {
            w.println("# origin " + ox + " " + oy + " " + oz);
            for (String mk : marks) w.println(mk);
        }
    }

    static long k(int x, int y, int z) { return ((long) x << 40) | ((long) (y + 2048) << 20) | (z + 2048); }
}
