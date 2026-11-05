import ru.stalkerzone.world.xray.LevelRegion;

import java.io.FileInputStream;
import java.io.PrintWriter;

/**
 * Превью площадки (П-02): высоты земли уровня после правки площадками → «x z y» для tools/prefab/site_preview.py.
 * java SitePreview <l01_escape.szl> <out.txt> x0 z0 x1 z1
 */
public class SitePreview {
    public static void main(String[] a) throws Exception {
        LevelRegion r;
        try (FileInputStream in = new FileInputStream(a[0])) { r = LevelRegion.load("l01_escape", in); }
        int x0 = Integer.parseInt(a[2]), z0 = Integer.parseInt(a[3]), x1 = Integer.parseInt(a[4]), z1 = Integer.parseInt(a[5]);
        try (PrintWriter w = new PrintWriter(a[1], "UTF-8")) {
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) w.println(x + " " + z + " " + r.groundBlock(x, z));
        }
    }
}
