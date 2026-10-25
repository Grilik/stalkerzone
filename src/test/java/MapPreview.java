import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.Features;
import ru.stalkerzone.zonemap.Region;
import ru.stalkerzone.zonemap.ZoneMap;
import ru.stalkerzone.zonemap.build.Structures;
import ru.stalkerzone.zonemap.design.WorldDesign;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/** Превью готовой карты: мир целиком, Кордон сверху в масштабе 1:1, дамп объектов для объёмных рендеров. */
public class MapPreview {
    static ZoneMap map = WorldDesign.get();

    public static void main(String[] a) throws Exception {
        String out = a.length > 0 ? a[0] : ".";
        if (a.length > 1 && a[1].equals("supply")) {
            // военная дорога снабжения: НИИ Агропром → полевые базы → бункер учёных
            area(out + "/supply_road.png", -2500, -2550, -1250, -1200, true);
            return;
        }
        if (a.length > 1 && a[1].equals("vehicles")) {
            // каждая машина отдельно: целая и сгоревшая
            String[] all = {"zhiguli", "moskvich", "zaporozhets", "niva", "uaz", "raf", "laz", "paz", "zil_fire", "zil", "kamaz", "btr", "tractor"};
            for (int i = 0; i < all.length; i++) for (int d : new int[] {0, 1, 2}) dumpSeed(out + "/veh_" + all[i] + "_" + d + ".txt", "vehicle", i + ((long) d << 8), 7);
            return;
        }
        if (a.length > 1 && a[1].equals("levels")) {
            // дампы построек по уровням развития с именами блоков (для рендера с текстурами)
            for (int i = 2; i < a.length; i++) for (int lv = 0; lv <= 3; lv++) dumpStates(out + "/lvl_" + a[i] + "_" + lv + ".txt", a[i], lv);
            return;
        }
        world(out + "/world.png");
        Region c = map.region("cordon");
        area(out + "/cordon.png", c.x0, c.z0, c.x1, c.z1, true);
        // крупные фрагменты 1 блок = 3 пикселя
        zoom(out + "/cordon_village.png", -480, 170, -130, 380);
        zoom(out + "/cordon_bridge.png", -40, -120, 220, 80);
        zoom(out + "/cordon_atp.png", -450, -360, -300, -250);
        zoom(out + "/cordon_elevator.png", 270, -360, 400, -240);
        zoom(out + "/cordon_kpp.png", -40, 440, 230, 560);
        // дампы объектов
        String[] kinds = {"rookie_house", "sidorovich_bunker", "kpp", "atp", "farm_elevator", "military_camp", "rail_bridge"};
        for (String k : kinds) dump(out + "/obj_" + k + ".txt", k);
    }

    // ------------------------------------------------------------ цвета блоков

    static int color(String s) {
        String id = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
        id = id.replace("minecraft:", "").replace("stalkerzone:", "");
        Map<String, Integer> c = COLORS;
        if (c.containsKey(id)) return c.get(id);
        if (id.contains("spruce")) return 0x6b4f2e;
        if (id.contains("dark_oak")) return 0x4a3220;
        if (id.contains("oak")) return 0x9c7f4e;
        if (id.contains("birch")) return 0xc8b77a;
        if (id.contains("cobblestone")) return 0x7a7a7a;
        if (id.contains("stone_brick")) return 0x7d7d7d;
        if (id.contains("concrete")) return 0x9a9a9a;
        if (id.contains("terracotta")) return 0x9a6a4a;
        if (id.contains("wool")) return 0x6a7a40;
        if (id.contains("leaves")) return 0x3f6a2a;
        if (id.contains("glass")) return 0xa8c8d0;
        return 0x888888;
    }

    static final Map<String, Integer> COLORS = new HashMap<>();
    static {
        Object[][] t = {
                {"grass_block", 0x6f8a46}, {"dirt", 0x7a5a3a}, {"coarse_dirt", 0x6e5038}, {"dirt_path", 0x9a8456}, {"gravel", 0x8a8480},
                {"mud", 0x4a3e38}, {"moss_block", 0x5a7030}, {"podzol", 0x6a4a28}, {"sand", 0xc8b888}, {"clay", 0x9aa0aa},
                {"gray_concrete", 0x55585c}, {"black_concrete", 0x2a2c30}, {"light_gray_concrete", 0x9a9a94}, {"white_concrete", 0xd8dadc},
                {"red_concrete", 0x8e2020}, {"yellow_concrete", 0xe0b020}, {"green_concrete", 0x4a5a22}, {"smooth_stone", 0x9e9e9e},
                {"smooth_stone_slab", 0xa8a8a8}, {"bricks", 0x93503c}, {"white_terracotta", 0xd0b8a8}, {"terracotta", 0x985a3e},
                {"brown_terracotta", 0x4d3323}, {"green_terracotta", 0x4c5a2a}, {"red_terracotta", 0x8e3c2e}, {"cyan_terracotta", 0x565b5b},
                {"blue_terracotta", 0x4a3c5a}, {"lime_terracotta", 0x67753a}, {"iron_block", 0xd8d8d8}, {"raw_iron_block", 0xa6876a},
                {"iron_bars", 0x8a8a8a}, {"chain", 0x404050}, {"rail", 0x6a6058}, {"water", 0x3a5a70}, {"rusty_barrel", 0x8a4a22},
                {"tires", 0x202020}, {"smooth_sandstone", 0xd8ca9c}, {"polished_andesite", 0x8a8e8a}, {"andesite", 0x8a8a86},
                {"cobblestone_slab", 0x7a7a7a}, {"hay_block", 0xb89a2a}, {"campfire", 0xd06020}, {"bone_block", 0xe0dcc8},
                {"cracked_stone_bricks", 0x767676}, {"mossy_cobblestone", 0x6a7a5a}, {"lightning_rod", 0xb07050}, {"lantern", 0xe0a040},
                {"sea_lantern", 0xc8e0d8}, {"deepslate", 0x505058}, {"polished_deepslate", 0x484850}, {"red_leaves", 0xa04a2a},
                {"dead_bush", 0x7a5a2a}, {"cobweb", 0xdddddd}, {"air", -1}};
        for (Object[] o : t) COLORS.put((String) o[0], (Integer) o[1]);
    }

    // ------------------------------------------------------------ вид сверху

    /** Собирает верхний блок каждой колонки: рельеф, затем объекты и деревья поверх. */
    static final class Top implements BlockSink {
        final int x0, z0, w, h;
        final int[] y, col;
        Top(int x0, int z0, int w, int h) {
            this.x0 = x0; this.z0 = z0; this.w = w; this.h = h;
            y = new int[w * h]; col = new int[w * h];
            for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
                ZoneMap.Column c = map.column(x0 + i, z0 + j);
                int k = j * w + i;
                y[k] = c.height;
                col[k] = color(c.top);
                if (c.deco != null) { y[k] = c.height + 1; col[k] = color(c.deco); }
                if (c.water > c.height) { y[k] = c.water; col[k] = 0x3a5a70; }
            }
        }
        boolean in(int x, int z) { return x >= x0 && x < x0 + w && z >= z0 && z < z0 + h; }
        @Override public void set(int x, int yy, int z, String s) {
            if (!in(x, z)) return;
            int k = (z - z0) * w + (x - x0);
            int c = color(s);
            if (c < 0) { if (yy == y[k]) { y[k]--; } return; }
            if (yy >= y[k]) { y[k] = yy; col[k] = c; }
        }
        @Override public int ground(int x, int z) { return map.column(x, z).height + 1; }
    }

    static void trees(Top t) {
        for (int cx = Math.floorDiv(t.x0, 5) - 1; cx <= Math.floorDiv(t.x0 + t.w, 5) + 1; cx++)
            for (int cz = Math.floorDiv(t.z0, 5) - 1; cz <= Math.floorDiv(t.z0 + t.h, 5) + 1; cz++) {
                ZoneMap.Tree tr = map.treeInCell(cx, cz);
                if (tr == null) continue;
                int base = map.column(tr.x(), tr.z()).height + 1;
                int r = tr.kind() == Features.ForestKind.PINE ? 2 : tr.kind() == Features.ForestKind.DEAD ? 0 : 3;
                int c = switch (tr.kind()) {
                    case PINE -> 0x2e4a2a; case BIRCH -> 0x6a8a3a; case DEAD -> 0x5a4a38; case RED -> 0xa04a2a; case SWAMP_WILLOW -> 0x4a6a34; default -> 0x3f6a2a;
                };
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r + 1) continue;
                    t.set(tr.x() + dx, base + tr.height(), tr.z() + dz, "x");
                    if (t.in(tr.x() + dx, tr.z() + dz)) t.col[(tr.z() + dz - t.z0) * t.w + tr.x() + dx - t.x0] = c;
                }
            }
    }

    static BufferedImage shade(Top t, int scale) {
        BufferedImage img = new BufferedImage(t.w * scale, t.h * scale, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < t.w; i++) for (int j = 0; j < t.h; j++) {
            int k = j * t.w + i;
            int hl = t.y[Math.max(0, i - 1) + j * t.w], hu = t.y[i + Math.max(0, j - 1) * t.w];
            double light = 1 + 0.09 * ((t.y[k] - hl) + (t.y[k] - hu));
            light = Math.max(0.55, Math.min(1.35, light));
            int c = t.col[k];
            int r = (int) Math.min(255, ((c >> 16) & 255) * light), g = (int) Math.min(255, ((c >> 8) & 255) * light), bl = (int) Math.min(255, (c & 255) * light);
            int rgb = (r << 16) | (g << 8) | bl;
            for (int a = 0; a < scale; a++) for (int b = 0; b < scale; b++) img.setRGB(i * scale + a, j * scale + b, rgb);
        }
        return img;
    }

    static void area(String file, int x0, int z0, int x1, int z1, boolean labels) throws Exception {
        Top t = new Top(x0, z0, x1 - x0, z1 - z0);
        for (Features.Placement p : map.placementsIn(x0, z0, x1, z1, 140)) Structures.build(p, t);
        trees(t);
        BufferedImage img = shade(t, 1);
        if (labels) label(img, x0, z0, 1, 13);
        ImageIO.write(img, "png", new File(file));
        System.out.println(file + " " + img.getWidth() + "x" + img.getHeight());
    }

    static void zoom(String file, int x0, int z0, int x1, int z1) throws Exception {
        Top t = new Top(x0, z0, x1 - x0, z1 - z0);
        for (Features.Placement p : map.placementsIn(x0, z0, x1, z1, 140)) Structures.build(p, t);
        trees(t);
        BufferedImage img = shade(t, 3);
        label(img, x0, z0, 3, 22);
        ImageIO.write(img, "png", new File(file));
        System.out.println(file + " " + img.getWidth() + "x" + img.getHeight());
    }

    static Color stColor(Features.StType t) {
        return switch (t) {
            case BASE -> new Color(255, 220, 60); case OUTPOST -> new Color(255, 150, 40); case CHECKPOINT -> new Color(255, 110, 110);
            case RESOURCE -> new Color(90, 220, 255); case LAIR -> new Color(220, 60, 60); case LAB -> new Color(190, 120, 255);
            default -> new Color(220, 220, 220);
        };
    }

    static void label(BufferedImage img, int x0, int z0, int scale, int size) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("DejaVu Sans", Font.BOLD, size));
        for (Region r : map.regions) for (Features.Smart s : r.smarts) {
            int x = (s.x() - x0) * scale, y = (s.z() - z0) * scale;
            if (x < -50 || y < -50 || x > img.getWidth() + 50 || y > img.getHeight() + 50) continue;
            g.setColor(stColor(s.type()));
            g.setStroke(new BasicStroke(2));
            int rr = s.r() * scale;
            g.drawOval(x - rr, y - rr, rr * 2, rr * 2);
            String txt = s.name() + " (" + s.type().ru + (s.owner() != null ? ", " + s.owner() : "") + ")";
            g.setColor(Color.BLACK);
            g.drawString(txt, x - g.getFontMetrics().stringWidth(txt) / 2 + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(txt, x - g.getFontMetrics().stringWidth(txt) / 2, y);
        }
        g.dispose();
    }

    // ------------------------------------------------------------ вся карта

    static void world(String file) throws Exception {
        int s = 6;
        int w = (map.maxX - map.minX) / s, h = (map.maxZ - map.minZ) / s;
        Top t = new Top(map.minX, map.minZ, 1, 1);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] hh = new int[w * h];
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
            ZoneMap.Column c = map.column(map.minX + i * s, map.minZ + j * s);
            hh[j * w + i] = c.height;
            int col = color(c.top);
            if (c.deco != null) col = 0x6a6058;
            if (c.water > c.height) col = 0x3a5a70;
            ZoneMap.Tree tr = map.treeInCell(Math.floorDiv(map.minX + i * s, 5), Math.floorDiv(map.minZ + j * s, 5));
            if (tr != null) col = switch (tr.kind()) { case RED -> 0xa04a2a; case PINE -> 0x2e4a2a; case DEAD -> 0x5a4a38; default -> 0x3f6a2a; };
            Region r = map.regionAt(map.minX + i * s, map.minZ + j * s);
            if (r == null) col = 0x303830;
            img.setRGB(i, j, col);
        }
        for (int i = 1; i < w; i++) for (int j = 1; j < h; j++) {
            double light = 1 + 0.05 * ((hh[j * w + i] - hh[j * w + i - 1]) + (hh[j * w + i] - hh[(j - 1) * w + i]));
            int c = img.getRGB(i, j);
            light = Math.max(0.6, Math.min(1.3, light));
            int r = (int) Math.min(255, ((c >> 16) & 255) * light), g = (int) Math.min(255, ((c >> 8) & 255) * light), b = (int) Math.min(255, (c & 255) * light);
            img.setRGB(i, j, (r << 16) | (g << 8) | b);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2));
        for (Features.Path p : map.allPaths()) {
            g.setColor(switch (p.type()) { case ASPHALT -> new Color(40, 40, 44); case RAIL -> new Color(120, 80, 60); case TRAIL -> new Color(160, 140, 90); default -> new Color(150, 120, 70); });
            double[] pt = p.pts();
            for (int i = 0; i + 3 < pt.length; i += 2)
                g.drawLine((int) ((pt[i] - map.minX) / s), (int) ((pt[i + 1] - map.minZ) / s), (int) ((pt[i + 2] - map.minX) / s), (int) ((pt[i + 3] - map.minZ) / s));
        }
        for (Region r : map.regions) {
            g.setColor(new Color(255, 255, 255, 90));
            g.drawRect((r.x0 - map.minX) / s, (r.z0 - map.minZ) / s, (r.x1 - r.x0) / s, (r.z1 - r.z0) / s);
            g.setFont(new Font("DejaVu Sans", Font.BOLD, 20));
            g.setColor(Color.BLACK);
            g.drawString(r.name, (r.x0 - map.minX) / s + 7, (r.z0 - map.minZ) / s + 25);
            g.setColor(r.detailed ? new Color(255, 230, 120) : Color.WHITE);
            g.drawString(r.name, (r.x0 - map.minX) / s + 6, (r.z0 - map.minZ) / s + 24);
            g.setFont(new Font("DejaVu Sans", Font.PLAIN, 11));
            for (Features.Smart sm : r.smarts) {
                int x = (sm.x() - map.minX) / s, y = (sm.z() - map.minZ) / s;
                g.setColor(stColor(sm.type()));
                g.fillOval(x - 4, y - 4, 8, 8);
                g.setColor(Color.BLACK);
                g.drawString(sm.name(), x + 6, y + 5);
                g.setColor(Color.WHITE);
                g.drawString(sm.name(), x + 5, y + 4);
            }
        }
        // легенда
        g.setFont(new Font("DejaVu Sans", Font.BOLD, 14));
        Object[][] leg = {{"база", Features.StType.BASE}, {"аванпост", Features.StType.OUTPOST}, {"застава", Features.StType.CHECKPOINT},
                {"ресурс (аномалии)", Features.StType.RESOURCE}, {"логово", Features.StType.LAIR}, {"лаборатория", Features.StType.LAB}, {"территория", Features.StType.TERRITORY}};
        int ly = h - 20 - leg.length * 20;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(8, ly - 20, 230, leg.length * 20 + 26);
        for (Object[] o : leg) {
            g.setColor(stColor((Features.StType) o[1]));
            g.fillOval(16, ly - 11, 11, 11);
            g.setColor(Color.WHITE);
            g.drawString((String) o[0], 34, ly);
            ly += 20;
        }
        g.dispose();
        ImageIO.write(img, "png", new File(file));
        System.out.println(file + " " + w + "x" + h);
    }

    // ------------------------------------------------------------ дамп объекта на ровной земле

    static void dump(String file, String kind) throws Exception {
        Map<Long, String> vox = new HashMap<>();
        BlockSink flat = new BlockSink() {
            @Override public void set(int x, int y, int z, String s) {
                long k = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
                if (s.equals("minecraft:air")) vox.remove(k); else vox.put(k, s);
            }
            @Override public int ground(int x, int z) { return 0; }
        };
        // ровная площадка земли
        Features.Placement p = new Features.Placement(kind, 0, 0, 0, 0, 0, 12345);
        int r = Math.min(60, Structures.reach(p) - 4);
        if (kind.equals("rail_bridge")) {
            // насыпь вдоль x и дорога вдоль z для наглядности
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                int h = Math.max(0, 6 - Math.max(0, Math.abs(z) - 3));
                for (int y = -1; y < h; y++) flat.set(x, y, z, y == h - 1 && h > 0 ? "minecraft:gravel" : "minecraft:grass_block");
            }
            BlockSink emb = new BlockSink() {
                @Override public void set(int x, int y, int z, String s) { flat.set(x, y, z, s); }
                @Override public int ground(int x, int z) { return Math.max(0, 6 - Math.max(0, Math.abs(z) - 3)); }
            };
            Structures.build(p, emb);
        } else {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) flat.set(x, -1, z, "minecraft:grass_block");
            Structures.build(p, flat);
        }
        try (PrintWriter w = new PrintWriter(file)) {
            for (Map.Entry<Long, String> e : vox.entrySet()) {
                long k = e.getKey();
                int x = (int) ((k >> 40) & 0xfffff) - 512, y = (int) ((k >> 20) & 0xfffff) - 512, z = (int) (k & 0xfffff) - 512;
                int c = color(e.getValue());
                if (c < 0) continue;
                w.println(x + " " + y + " " + z + " " + c);
            }
        }
        System.out.println(file + " " + vox.size());
    }

    /** Дамп объекта на ровной траве: строки «x y z состояние_блока». */
    static void dumpStates(String file, String kind, int level) throws Exception {
        Map<Long, String> vox = new HashMap<>();
        boolean emb = kind.equals("rail_bridge") || kind.equals("rail_tunnel");
        java.util.function.IntBinaryOperator h = (x, z) -> emb ? Math.max(0, 8 - Math.max(0, Math.abs(z) - 4)) : 0;
        BlockSink flat = new BlockSink() {
            @Override public void set(int x, int y, int z, String s) {
                long k = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
                if (s.equals("minecraft:air")) vox.remove(k); else vox.put(k, s);
            }
            @Override public int ground(int x, int z) { return h.applyAsInt(x, z); }
        };
        Features.Placement p = new Features.Placement(kind, 0, 0, 0, 0, 0, 12345);
        int r = Structures.reach(p) - 6;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            int top = h.applyAsInt(x, z);
            for (int y = -2; y < top; y++) flat.set(x, y, z, y == top - 1 ? (emb && top > 1 ? "minecraft:gravel" : "minecraft:grass_block") : "minecraft:dirt");
            if (top == 0) flat.set(x, -1, z, "minecraft:grass_block");
        }
        Structures.build(p, flat, level);
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            for (Map.Entry<Long, String> e : vox.entrySet()) {
                long k = e.getKey();
                int x = (int) ((k >> 40) & 0xfffff) - 512, y = (int) ((k >> 20) & 0xfffff) - 512, z = (int) (k & 0xfffff) - 512;
                w.println(x + " " + y + " " + z + " " + e.getValue());
            }
        }
        System.out.println(file + " " + vox.size());
    }

    static void dumpSeed(String file, String kind, long seed, int r) throws Exception {
        Map<Long, String> vox = new HashMap<>();
        BlockSink flat = new BlockSink() {
            @Override public void set(int x, int y, int z, String s) {
                long k = ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
                if (s.equals("minecraft:air")) vox.remove(k); else vox.put(k, s);
            }
            @Override public int ground(int x, int z) { return 0; }
        };
        for (int x = -r; x <= r; x++) for (int z = -3; z <= 3; z++) { flat.set(x, -1, z, "minecraft:gravel"); flat.set(x, -2, z, "minecraft:dirt"); }
        Structures.build(new Features.Placement(kind, 0, 0, 0, 0, 0, seed), flat, 2);
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            for (Map.Entry<Long, String> e : vox.entrySet()) {
                long k = e.getKey();
                w.println((((k >> 40) & 0xfffff) - 512) + " " + (((k >> 20) & 0xfffff) - 512) + " " + ((k & 0xfffff) - 512) + " " + e.getValue());
            }
        }
    }
}
