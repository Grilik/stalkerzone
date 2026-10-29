package ru.stalkerzone.world.xray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Уровни оригинала, встроенные в мир 1:1. Загружаются лениво из папки игры (stalkerzone/levels/*.szl). */
public final class LevelRegions {
    /** Уровни по умолчанию (если нет layout.json). */
    public static final String[] IDS = {"l01_escape"};
    /** Переход между уровнями: от края одного до края другого (точки уже в блоках мира). */
    public record Link(String a, String b, int ax, int az, int bx, int bz) {}
    private static volatile List<Link> links = List.of();

    public static List<Link> links() { all(); return links; }

    static java.io.File dir() { return new java.io.File(System.getProperty("stalkerzone.levels", "stalkerzone/levels")); }

    /** layout.json: {"levels":[{"id":..}], "links":[{"a","b","ax","az","bx","bz"}]} — простой разбор без библиотек. */
    private static String[] readLayout(List<Link> outLinks) {
        java.io.File f = new java.io.File(dir(), "layout.json");
        if (!f.isFile()) return IDS;
        try {
            String s = java.nio.file.Files.readString(f.toPath());
            List<String> ids = new ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"(\\w+)\"").matcher(s);
            while (m.find()) ids.add(m.group(1));
            java.util.regex.Matcher l = java.util.regex.Pattern.compile("\"a\"\\s*:\\s*\"(\\w+)\",\\s*\"b\"\\s*:\\s*\"(\\w+)\",\\s*\"ax\"\\s*:\\s*(-?\\d+),\\s*\"az\"\\s*:\\s*(-?\\d+),\\s*\"bx\"\\s*:\\s*(-?\\d+),\\s*\"bz\"\\s*:\\s*(-?\\d+)").matcher(s);
            while (l.find()) outLinks.add(new Link(l.group(1), l.group(2), Integer.parseInt(l.group(3)), Integer.parseInt(l.group(4)),
                    Integer.parseInt(l.group(5)), Integer.parseInt(l.group(6))));
            return ids.isEmpty() ? IDS : ids.toArray(new String[0]);
        } catch (Exception e) {
            System.err.println("[stalkerzone] layout.json не прочитан: " + e);
            return IDS;
        }
    }

    public static LevelRegion byId(String id) {
        for (LevelRegion r : all()) if (r.id.equals(id)) return r;
        return null;
    }
    /** Ширина полосы, на которой рельеф уровня плавно переходит в обычный рельеф Зоны. */
    public static final int BLEND = 96;
    private static volatile List<LevelRegion> regions;

    private LevelRegions() {}

    public static List<LevelRegion> all() {
        List<LevelRegion> r = regions;
        if (r != null) return r;
        synchronized (LevelRegions.class) {
            if (regions != null) return regions;
            List<LevelRegion> out = new ArrayList<>();
            List<Link> ls = new ArrayList<>();
            for (String id : readLayout(ls)) {
                // данные уровня делаются из файлов игры у пользователя (tools/xray) и лежат рядом с игрой, не в моде:
                // <папка игры>/stalkerzone/levels/<id>.szl; запасной путь — ресурсы мода
                java.io.File ext = new java.io.File(dir(), id + ".szl");
                try (InputStream in = ext.isFile() ? new java.io.FileInputStream(ext)
                        : LevelRegions.class.getResourceAsStream("/data/stalkerzone/levels/" + id + ".szl")) {
                    if (in != null) out.add(LevelRegion.load(id, in));
                } catch (Exception e) {
                    System.err.println("[stalkerzone] не загрузился уровень " + id + ": " + e);
                }
            }
            links = List.copyOf(ls);
            regions = List.copyOf(out);
            return regions;
        }
    }

    public static LevelRegion at(int x, int z) {
        for (LevelRegion r : all()) if (r.contains(x, z)) return r;
        return null;
    }

    /** Ближайший уровень в пределах полосы перехода. */
    public static LevelRegion near(double x, double z, double within) {
        LevelRegion best = null;
        double bd = within;
        for (LevelRegion r : all()) {
            double d = r.distance(x, z);
            if (d <= bd) { bd = d; best = r; }
        }
        return best;
    }

    /** Уровень покрывает этот чанк (хоть одним углом)? Там свои постройки — наш декор и лагеря не ставим. */
    public static boolean coversChunk(int cx, int cz) {
        for (LevelRegion r : all())
            if (r.contains(cx * 16, cz * 16) || r.contains(cx * 16 + 15, cz * 16 + 15) || r.contains(cx * 16 + 15, cz * 16) || r.contains(cx * 16, cz * 16 + 15)) return true;
        return false;
    }

    public static boolean covers(int x, int z) { return at(x, z) != null; }

    /** Уровень, задевающий чанк (хоть одной колонкой), или null. */
    public static LevelRegion atChunk(int cx, int cz) {
        for (LevelRegion r : all())
            if (cx * 16 + 15 >= r.x0 && cz * 16 + 15 >= r.z0 && cx * 16 < r.x0 + r.w && cz * 16 < r.z0 + r.d) return r;
        return null;
    }
}
