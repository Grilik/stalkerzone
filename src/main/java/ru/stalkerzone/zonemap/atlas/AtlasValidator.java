package ru.stalkerzone.zonemap.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Проверки атласа (решение б4). Возвращает список ошибок; пустой — атлас годен. */
public final class AtlasValidator {
    public static final double RMS_MAX = 8, LOO_MAX = 16;
    public static final Set<String> KINDS = Set.of("base", "outpost", "checkpoint", "field", "lair", "cache",
            "trader", "exit", "landmark", "road_node", "zone");
    /** Э1-16: типы территорий (kind=zone, поле type); совпадают с TerritoryKind в симуляции. */
    public static final Set<String> ZONE_TYPES = Set.of("forest", "scrap", "anomaly_field", "lair", "industrial",
            "ruins", "crossroads", "underground");
    /** Э1-16: ключи потенциала территории; совпадают с TerritoryPotential.KEYS. */
    public static final Set<String> POTENTIAL_KEYS = Set.of("wood", "parts", "artifacts", "mutant", "workshop",
            "storage", "trade", "shelter");
    /** Э1-16: территория не даёт еды и воды (пользователь, 15.11 19:14) — валидатор запрещает ключи. */
    public static final Set<String> POTENTIAL_FORBIDDEN = Set.of("food", "water");
    /** Э1-16: измеряемые особенности зоны (съёмка Э1-04 и атлас). */
    public static final Set<String> ZONE_FEATURES = Set.of("forest", "buildingArea", "undergroundArea", "roads",
            "lairs", "fields");
    public static final Set<String> STATUSES = Set.of("observed", "accepted", "redesign", "new", "built",
            "verified", "integrated", "removed");
    public static final Set<String> MODES = Set.of("keep", "adapt", "redesign");

    private AtlasValidator() {}

    public static List<String> validate(Atlas a) {
        List<String> err = new ArrayList<>();
        double[][] bounds = poly(a.world.get("bounds"));
        if (bounds == null) err.add("world.bounds: нужен многоугольник из >= 3 вершин [x, z]");
        if (Json.nums(a.world.get("spawn")) == null) err.add("world.spawn: нужна точка");
        if (Double.isNaN(Json.num(a.world.get("version")))) err.add("world.version: нужно число");

        Set<String> locIds = new HashSet<>();
        for (Map<String, Object> loc : a.locations) {
            String id = Atlas.id(loc);
            if (id == null) { err.add("локация без id"); continue; }
            if (!locIds.add(id)) err.add("повтор id локации " + id);
        }
        // links ведут к существующим локациям
        List<Object> links = Json.list(a.world.get("links"));
        if (links != null) for (Object l : links) {
            Map<String, Object> m = Json.obj(l);
            if (m == null) { err.add("world.links: не объект"); continue; }
            for (String k : new String[]{"from", "to"})
                if (!locIds.contains(Json.str(m.get(k)))) err.add("link " + m.get("from") + "->" + m.get("to") + ": нет локации " + m.get(k));
        }
        // файлы локаций соответствуют id
        List<Object> refs = Json.list(a.world.get("locations"));
        for (int i = 0; refs != null && i < refs.size() && i < a.locations.size(); i++) {
            Map<String, Object> ref = Json.obj(refs.get(i));
            if (ref != null && !String.valueOf(ref.get("id")).equals(Atlas.id(a.locations.get(i))))
                err.add("world.locations[" + i + "].id не совпадает с id в файле");
        }

        Set<String> ids = new HashSet<>();
        Map<Integer, String> nodes = new HashMap<>();
        List<Object[]> sites = new ArrayList<>(); // {id, mode, bbox, clear}
        double[][][] areas = new double[a.locations.size()][][];

        for (int li = 0; li < a.locations.size(); li++) {
            Map<String, Object> loc = a.locations.get(li);
            String lid = String.valueOf(Atlas.id(loc));
            double[] bbox = Json.nums(loc.get("bbox"));
            if (bbox == null || bbox.length != 4) { err.add(lid + ": bbox должен быть [minX, minZ, maxX, maxZ]"); bbox = null; }
            areas[li] = poly(loc.get("area"));
            if (areas[li] == null) err.add(lid + ": area - многоугольник из >= 3 вершин");
            else if (selfIntersects(areas[li])) err.add(lid + ": area самопересекается");
            double[] tp = Json.nums(loc.get("teleport"));
            if (loc.containsKey("teleport") && bbox != null) {
                if (tp == null || tp.length < 2) err.add(lid + ": teleport - плохая точка");
                else if (!in(bbox, tp[0], tp[tp.length - 1])) err.add(lid + ": teleport вне bbox локации");
            }
            boolean residualOk = residualOk(loc, lid, err);
            Map<String, Map<String, Object>> locObjects = new HashMap<>();
            for (Map<String, Object> o : Atlas.objects(loc)) locObjects.put(Atlas.id(o), o);

            for (Map<String, Object> o : Atlas.objects(loc)) {
                String oid = Atlas.id(o);
                if (oid == null) { err.add(lid + ": объект без id"); continue; }
                if (!ids.add(oid)) err.add("повтор id объекта " + oid);
                if (!oid.startsWith(lid + ".")) err.add(oid + ": id должен начинаться с '" + lid + ".'");
                String kind = Json.str(o.get("kind"));
                if (kind == null || !KINDS.contains(kind)) err.add(oid + ": неизвестный kind " + kind);
                else if (!oid.startsWith(lid + "." + kind + ".")) err.add(oid + ": id должен быть <loc>.<kind>.<имя>");
                String status = Json.str(o.get("status"));
                if (status == null || !STATUSES.contains(status)) err.add(oid + ": неизвестный status " + status);
                if (Json.str(o.get("source")) == null) err.add(oid + ": нет source (откуда взято)");
                double[] pos = Json.nums(o.get("pos"));
                if (pos == null || pos.length < 2 || pos.length > 3) { err.add(oid + ": pos - [x, z] или [x, y, z]"); continue; }
                double px = pos[0], pz = pos[pos.length - 1];
                if (!"exit".equals(kind)) {
                    if (bbox != null && !in(bbox, px, pz)) err.add(oid + ": вне bbox локации");
                    if (bounds != null && !inPoly(bounds, px, pz)) err.add(oid + ": вне world.bounds");
                }
                if (!residualOk && !"observed".equals(status) && !"removed".equals(status))
                    err.add(oid + ": невязка локации вне порога, статус должен быть observed");
                if (o.containsKey("node")) {
                    double n = Json.num(o.get("node"));
                    if (Double.isNaN(n) || n < 0 || n != Math.floor(n)) err.add(oid + ": node - целое >= 0");
                    else {
                        String prev = nodes.put((int) n, oid);
                        if (prev != null) err.add("node " + (int) n + " повторён: " + prev + " и " + oid);
                    }
                }
                if ("base".equals(kind)) {
                    Map<String, Object> pts = Json.obj(o.get("points"));
                    if (pts == null || Json.nums(pts.get("campfire")) == null) err.add(oid + ": у базы нет points.campfire");
                    List<Object> beds = pts == null ? null : Json.list(pts.get("beds"));
                    if (beds == null || beds.isEmpty()) err.add(oid + ": у базы нет points.beds");
                }
                if ("zone".equals(kind)) zone(o, oid, bbox, areas[li], locObjects, err);
                Map<String, Object> site = Json.obj(o.get("site"));
                if (site != null) {
                    String mode = Json.str(site.get("mode"));
                    if (mode == null || !MODES.contains(mode)) err.add(oid + ": site.mode - keep/adapt/redesign");
                    double[] sb = Json.nums(site.get("bbox"));
                    if (sb == null || sb.length != 4) err.add(oid + ": site.bbox - [minX, minZ, maxX, maxZ]");
                    List<double[]> clear = new ArrayList<>();
                    List<Object> cl = Json.list(site.get("clear"));
                    if (cl != null) for (Object c : cl) {
                        double[] r = Json.nums(c);
                        if (r == null || r.length != 4) err.add(oid + ": site.clear - список rect"); else clear.add(r);
                    }
                    if (!clear.isEmpty() && !"redesign".equals(mode)) err.add(oid + ": site.clear допустим только при mode=redesign");
                    if (sb != null && sb.length == 4) sites.add(new Object[]{oid, mode, sb, clear});
                }
            }
        }
        // области локаций не пересекаются
        for (int i = 0; i < areas.length; i++)
            for (int j = i + 1; j < areas.length; j++)
                if (areas[i] != null && areas[j] != null && polysOverlap(areas[i], areas[j]))
                    err.add("area локаций " + Atlas.id(a.locations.get(i)) + " и " + Atlas.id(a.locations.get(j)) + " пересекаются");
        // bbox площадок любого режима не пересекаются; clear чужой площадки не лезет в bbox соседей
        for (int i = 0; i < sites.size(); i++)
            for (int j = i + 1; j < sites.size(); j++) {
                if (rectsOverlap((double[]) sites.get(i)[2], (double[]) sites.get(j)[2]))
                    err.add("bbox площадок " + sites.get(i)[0] + " и " + sites.get(j)[0] + " пересекаются");
            }
        for (Object[] s : sites) {
            @SuppressWarnings("unchecked") List<double[]> clear = (List<double[]>) s[3];
            for (double[] c : clear) for (Object[] t : sites)
                if (t != s && rectsOverlap(c, (double[]) t[2])) err.add("clear площадки " + s[0] + " задевает площадку " + t[0]);
        }
        return err;
    }

    /**
     * Э1-16: территория потенциала (kind=zone). Многоугольник внутри локации, потенциал без еды и воды,
     * особенности измерены, постройки — существующие объекты атласа внутри зоны. Зона не узел (б3)
     * и не площадка: стройка отложена (Э5-13), занятие идёт в существующих постройках TheZone.
     */
    private static void zone(Map<String, Object> o, String oid, double[] bbox, double[][] locArea,
                             Map<String, Map<String, Object>> locObjects, List<String> err) {
        if (o.containsKey("node")) err.add(oid + ": зона потенциала не узел симуляции (решение б3)");
        if (o.containsKey("site")) err.add(oid + ": у зоны нет площадки — стройка отложена (Э5-13)");
        String type = Json.str(o.get("type"));
        if (type == null || !ZONE_TYPES.contains(type)) err.add(oid + ": неизвестный type территории " + type);

        double[][] area = poly(o.get("area"));
        if (area == null) err.add(oid + ": у зоны нет area (многоугольник из >= 3 вершин)");
        else {
            if (selfIntersects(area)) err.add(oid + ": area зоны самопересекается");
            for (double[] v : area) {
                if (bbox != null && !in(bbox, v[0], v[1])) err.add(oid + ": вершина area вне bbox локации");
                if (locArea != null && !inPoly(locArea, v[0], v[1])) err.add(oid + ": area зоны вне area локации");
            }
        }

        Map<String, Object> pot = Json.obj(o.get("potential"));
        if (pot == null || pot.isEmpty()) err.add(oid + ": у зоны нет potential{}");
        else for (Map.Entry<String, Object> e : pot.entrySet()) {
            String key = e.getKey();
            if (POTENTIAL_FORBIDDEN.contains(key)) {
                err.add(oid + ": территория не даёт " + key + " (еды и воды зона не даёт)");
                continue;
            }
            if (!POTENTIAL_KEYS.contains(key)) { err.add(oid + ": неизвестный ключ потенциала " + key); continue; }
            double v = Json.num(e.getValue());
            if (Double.isNaN(v) || v < 0 || v > 1) err.add(oid + ": сила потенциала " + key + " вне 0..1");
        }

        Map<String, Object> ft = Json.obj(o.get("features"));
        if (ft == null) err.add(oid + ": у зоны нет features (особенности территории)");
        else for (String key : ZONE_FEATURES) {
            double v = Json.num(ft.get(key));
            if (Double.isNaN(v) || v < 0) { err.add(oid + ": features." + key + " — число >= 0"); continue; }
            if ("forest".equals(key) && v > 1) err.add(oid + ": features.forest — доля 0..1");
            if (!"forest".equals(key) && v != Math.floor(v)) err.add(oid + ": features." + key + " — целое");
        }

        String parent = Json.str(o.get("parent"));
        if (parent != null) {
            Map<String, Object> p = locObjects.get(parent);
            if (p == null) err.add(oid + ": parent " + parent + " не найден в локации");
            else if (!p.containsKey("node")) err.add(oid + ": parent " + parent + " не узел симуляции");
        }

        List<Object> buildings = Json.list(o.get("buildings"));
        if (buildings != null) for (Object b : buildings) {
            Map<String, Object> bm = Json.obj(b);
            String ref = bm == null ? null : Json.str(bm.get("ref"));
            Map<String, Object> refObj = ref == null ? null : locObjects.get(ref);
            if (refObj == null) { err.add(oid + ": buildings ref " + ref + " не найден в локации"); continue; }
            double[] rp = Json.nums(refObj.get("pos"));
            if (area != null && (rp == null || !inPoly(area, rp[0], rp[rp.length - 1])))
                err.add(oid + ": постройка " + ref + " вне area зоны");
        }
    }

    private static boolean residualOk(Map<String, Object> loc, String lid, List<String> err) {
        Map<String, Object> r = Json.obj(loc.get("residual"));
        if (r == null) { err.add(lid + ": нет residual {rms, max, loo.max}"); return false; }
        Map<String, Object> loo = Json.obj(r.get("loo"));
        double rms = Json.num(r.get("rms")), max = Json.num(r.get("max"));
        double lm = loo == null ? Double.NaN : Json.num(loo.get("max"));
        if (Double.isNaN(rms) || Double.isNaN(max) || Double.isNaN(lm)) { err.add(lid + ": residual неполон (rms, max, loo.max)"); return false; }
        return rms <= RMS_MAX && lm <= LOO_MAX;
    }

    // ---- геометрия ----

    static double[][] poly(Object o) {
        List<Object> l = Json.list(o);
        if (l == null || l.size() < 3) return null;
        double[][] p = new double[l.size()][];
        for (int i = 0; i < p.length; i++) {
            p[i] = Json.nums(l.get(i));
            if (p[i] == null || p[i].length != 2) return null;
        }
        return p;
    }

    static boolean in(double[] b, double x, double z) { return x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]; }

    /** Прямоугольники [minX, minZ, maxX, maxZ] с общими блоками (границы включительно). */
    static boolean rectsOverlap(double[] a, double[] b) {
        return a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3];
    }

    static boolean inPoly(double[][] p, double x, double z) {
        boolean c = false;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
            if (onSeg(p[j], p[i], x, z)) return true;
            if ((p[i][1] > z) != (p[j][1] > z) && x < (p[j][0] - p[i][0]) * (z - p[i][1]) / (p[j][1] - p[i][1]) + p[i][0]) c = !c;
        }
        return c;
    }

    private static boolean onSeg(double[] a, double[] b, double x, double z) {
        double cr = (b[0] - a[0]) * (z - a[1]) - (b[1] - a[1]) * (x - a[0]);
        return cr == 0 && x >= Math.min(a[0], b[0]) && x <= Math.max(a[0], b[0]) && z >= Math.min(a[1], b[1]) && z <= Math.max(a[1], b[1]);
    }

    private static double orient(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    /** Отрезки ab и cd имеют общую точку (в том числе касание). */
    static boolean segsTouch(double[] a, double[] b, double[] c, double[] d) {
        double d1 = orient(a, b, c), d2 = orient(a, b, d), d3 = orient(c, d, a), d4 = orient(c, d, b);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;
        return (d1 == 0 && onSeg(a, b, c[0], c[1])) || (d2 == 0 && onSeg(a, b, d[0], d[1]))
                || (d3 == 0 && onSeg(c, d, a[0], a[1])) || (d4 == 0 && onSeg(c, d, b[0], b[1]));
    }

    static boolean selfIntersects(double[][] p) {
        int n = p.length;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1 || (i == 0 && j == n - 1)) continue; // соседние рёбра
                if (segsTouch(p[i], p[(i + 1) % n], p[j], p[(j + 1) % n])) return true;
            }
        return false;
    }

    /** Два многоугольника имеют общую точку: пересечение рёбер либо один внутри другого. */
    static boolean polysOverlap(double[][] a, double[][] b) {
        for (int i = 0; i < a.length; i++)
            for (int j = 0; j < b.length; j++)
                if (segsTouch(a[i], a[(i + 1) % a.length], b[j], b[(j + 1) % b.length])) return true;
        return inPoly(b, a[0][0], a[0][1]) || inPoly(a, b[0][0], b[0][1]);
    }
}
