import ru.stalkerzone.world.xray.LevelRegion;
import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.build.KordonSouthPost;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Превью южного блокпоста (этап 1): рельеф из .szl + постройки KordonSouthPost → дамп «x y z состояние» для tools/iso_tex.py.
 * java SouthPostPreview <l01_escape.szl> <out.txt> [уровень 0..3]
 */
public class SouthPostPreview {
    public static void main(String[] a) throws Exception {
        LevelRegion r;
        try (FileInputStream in = new FileInputStream(a[0])) { r = LevelRegion.load("l01_escape", in); }
        int lvl = a.length > 2 ? Integer.parseInt(a[2]) : 2;
        int[] c = KordonSouthPost.CLEAR.get(0);
        int bx = c[0], bz = c[1];
        int base = Integer.MAX_VALUE;
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) base = Math.min(base, r.groundBlock(x, z));
        Map<Long, String> m = new LinkedHashMap<>();
        for (int x = c[0]; x <= c[2]; x++) for (int z = c[1]; z <= c[3]; z++) {
            int g = r.groundBlock(x, z);
            for (int y = Math.max(base, g - 2); y <= g; y++) m.put(k(x, y, z), y == g ? (r.surface(x, z) == 6 ? "minecraft:gray_concrete" : "minecraft:grass_block") : "minecraft:dirt");
        }
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { m.put(k(x, y, z), st); }
            @Override public int ground(int x, int z) { return r.groundBlock(x, z); }
        };
        KordonSouthPost.build(s, lvl);
        try (PrintWriter w = new PrintWriter(a[1], "UTF-8")) {
            for (Map.Entry<Long, String> e : m.entrySet()) {
                if (e.getValue().equals("minecraft:air")) continue;
                long kk = e.getKey();
                int x = (int) (kk >> 40), y = (int) ((kk >> 20) & 0xfffff) - 2048, z = (int) (kk & 0xfffff) - 2048;
                w.println((x - bx) + " " + (y - base) + " " + (z - bz) + " " + e.getValue());
            }
        }
    }

    static long k(int x, int y, int z) { return ((long) x << 40) | ((long) (y + 2048) << 20) | (z + 2048); }
}
