package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Постройки уровней, собранные по геометрии оригинала (этап 1 плана после плейтеста): вместо вокселей .szl
 * в своих границах. Чистая Java (без Minecraft): мир получает готовый набор «позиция → состояние блока».
 *
 * <p>Постройки собраны в площадки ({@link Site}, П-02): площадка узла правит рельеф, расчищает оригинал и держит
 * сборщики. Как добавить сборщик: класс в {@code zonemap/build/} с {@code CLEAR} и {@code build(BlockSink, lvl)},
 * затем {@code .build(of(X.CLEAR, X::build))} в нужной площадке списка {@code SITES}.
 */
public final class Prefabs {
    private Prefabs() {}

    public interface Ground { int at(int x, int z); }

    /** Рабочее место постройки (карточка 031): вид, координаты (y — пол над землёй/площадкой). */
    public record Point(String kind, int x, int y, int z) {}

    /** Уровень развития, на котором снимаются рабочие места (как генерация мира). */
    public static final int POINT_LEVEL = 1;

    /** Один сборщик построек уровня: границы замены, сами блоки и рабочие места (карточка 031). */
    public interface Builder {
        /** Границы {x0, z0, x1, z1}, где воксели уровня заменяются постройками. */
        java.util.List<int[]> clear();
        /** Построить блоки постройки в мировых координатах на уровне развития lvl (0–3). */
        void build(BlockSink sink, int lvl);
    }

    private static Builder of(java.util.List<int[]> clear, java.util.function.ObjIntConsumer<BlockSink> build) {
        return new Builder() {
            @Override public java.util.List<int[]> clear() { return clear; }
            @Override public void build(BlockSink sink, int lvl) { build.accept(sink, lvl); }
        };
    }

    /** Узлы сценария «Кордон» ({@code KordonScenario}), у которых есть площадки. */
    public static final int NODE_VILLAGE = 0, NODE_BUNKER = 1, NODE_SOUTH_POST = 2, NODE_ELEVATOR = 4, NODE_ATP = 5, NODE_TUNNEL = 7, NODE_WAGON = 19;

    /** Плато деревни новичков (П-02): ровная часть и ширина откоса; высота — медиана уровня (AUTO). */
    public static final int[] VILLAGE_PLATEAU = {-40, -24, 56, 34};
    public static final int VILLAGE_MARGIN = 8;
    /** Костёр деревни — центр жизни узла (точка campfire из kordon_points.json). */
    public static final int[] VILLAGE_FIRE = {2, -1};

    /**
     * Логово Л3 «Собачий лес» у узла 21 (карточки 053, 060): без плато, Structures.lairDen у (45, −175).
     */
    public static final int NODE_LAIR_L3 = 21;
    public static final int[] LAIR_L3 = {45, -175};
    private static final List<int[]> LAIR_L3_CLEAR = List.of(new int[]{LAIR_L3[0] - 14, LAIR_L3[1] - 14, LAIR_L3[0] + 14, LAIR_L3[1] + 14});

    /**
     * Площадки уровня Kordon (l01_escape), «Малый Кордон». Отключены (классы оставлены, 01.11):
     * свиноферма, дальний блокпост, мост — там снова воксели оригинала; туннель с электрами вернулся площадкой tunnel (П-23),
     * АТП — площадкой atp (П-21), элеватор — площадкой elevator (П-22, малая застава одиночек).
     */
    private static final List<Site> SITES = java.util.List.of(
            new Site("village", NODE_VILLAGE)
                    .plateau(VILLAGE_PLATEAU[0], VILLAGE_PLATEAU[1], VILLAGE_PLATEAU[2], VILLAGE_PLATEAU[3], Site.AUTO, VILLAGE_MARGIN)
                    .build(of(KordonVillage.CLEAR, KordonVillage::build)),   // П-03: дома, подвалы, забор, места NPC
            // бункер Сидоровича — свой узел (торговец), стоит на плато деревни; облик переделывает «Строитель» (П-02б)
            new Site("bunker", NODE_BUNKER).build(of(KordonBunker.CLEAR, KordonBunker::build)),
            new Site("south_post", NODE_SOUTH_POST).build(of(KordonSouthPost.CLEAR, KordonSouthPost::build)),
            // О7 — лагерь у вагончика и дорожный мост через овраг (узел 19, П-21/П-22)
            new Site("wagon", NODE_WAGON).plateau(104, -88, 130, -60, Site.AUTO, 6).shaper(KordonRoadBridge.RAVINE)
                    .build(of(KordonWagon.CLEAR, KordonWagon::build)).build(of(KordonRoadBridge.CLEAR, KordonRoadBridge::build)),
            // А6 — свалка техники и застава О12 на дне чаши (узел 20, П-23)
            new Site("dump", 20).plateau(-54, -134, -22, -108, Site.AUTO, 6).build(of(KordonDump.CLEAR, KordonDump::build)),
            // П-23: туннель с «электрами» под насыпью (смарт-террейн 4_9) — выемка у порталов, две заставы одиночек
            new Site("tunnel", NODE_TUNNEL).shaper(KordonTunnel.cut()).build(of(KordonTunnel.CLEAR, KordonTunnel::build)),
            // П-21: АТП (смарт-террейн 7_11) — брошенная база по вокселям оригинала, плато AUTO
            new Site("atp", NODE_ATP).plateau(KordonATP.PLATEAU[0], KordonATP.PLATEAU[1], KordonATP.PLATEAU[2], KordonATP.PLATEAU[3],
                    Site.AUTO, KordonATP.MARGIN).build(of(KordonATP.CLEAR, KordonATP::build)),
            // П-22: элеватор (смарт-террейн 5_9) — башня, силосы, галерея, коровники; малая застава одиночек
            new Site("elevator", NODE_ELEVATOR).plateau(KordonElevator.PLATEAU[0], KordonElevator.PLATEAU[1], KordonElevator.PLATEAU[2],
                    KordonElevator.PLATEAU[3], Site.AUTO, KordonElevator.MARGIN).build(of(KordonElevator.CLEAR, KordonElevator::build)),
            // Логово Л3 «Собачий лес» у узла 21 (карточки 053, 060): без плато, Structures.lairDen у (45, −175)
            new Site("lair", NODE_LAIR_L3).build(of(LAIR_L3_CLEAR, Prefabs::lairL3)));

    /** Логово Л3: кости, череп, паутина у (45, −175); место «den» для мутантов. */
    static void lairL3(BlockSink s, int lvl) {
        Structures.lairDen(new B(s, LAIR_L3[0], LAIR_L3[1], 0, 2101L, lvl));
        s.mark("den", LAIR_L3[0], s.ground(LAIR_L3[0], LAIR_L3[1]) + 1, LAIR_L3[1]);
    }

    /** Площадки уровня (пусто — уровень без площадок). */
    public static List<Site> sites(String levelId) { return "l01_escape".equals(levelId) ? SITES : List.of(); }

    /** Площадка узла или null. */
    public static Site site(String levelId, int node) {
        for (Site s : sites(levelId)) if (s.node == node) return s;
        return null;
    }

    private static final java.util.Map<String, List<int[]>> CLEAR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Границы {x0, z0, x1, z1}, где воксели уровня заменяются постройками; пусто — уровень без замен. */
    public static List<int[]> clear(String levelId) {
        return CLEAR_CACHE.computeIfAbsent(levelId, id -> {
            List<int[]> out = new java.util.ArrayList<>();
            for (Site s : sites(id)) out.addAll(s.clearRects());
            return List.copyOf(out);
        });
    }

    public static boolean cleared(String levelId, int x, int z) {
        for (int[] c : clear(levelId)) if (x >= c[0] && x <= c[2] && z >= c[1] && z <= c[3]) return true;
        return false;
    }

    /** Колонка в границах правки рельефа какой-либо площадки уровня (плато с откосом, выемка). */
    public static boolean shaped(String levelId, int x, int z) {
        for (Site s : sites(levelId)) for (Site.Shaper sh : s.shapers()) {
            int[] b = sh.bounds();
            if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) return true;
        }
        return false;
    }

    /** Плато, на ровной части которого лежит (x, z), на любом уровне; null — не на плато. */
    public static Site.Plateau plateauAt(int x, int z) {
        for (Site s : SITES) { Site.Plateau p = s.plateauAt(x, z); if (p != null) return p; }
        return null;
    }

    /**
     * Править карту высот уровня площадками (до генерации чанков, из LevelRegion.load). Возвращает строку для лога:
     * высоты плато и исходная земля в контрольных точках (открытая проблема 5 — расхождение высот).
     */
    public static String shape(String levelId, Site.Grid g) {
        List<Site> ss = sites(levelId);
        if (ss.isEmpty()) return "";
        StringBuilder log = new StringBuilder("[stalkerzone] площадки " + levelId + ": исходная земля");
        for (int[] p : new int[][] {{VILLAGE_FIRE[0], VILLAGE_FIRE[1]}, {154, -75}, {-35, -8}})
            if (g.contains(p[0], p[1])) log.append(String.format(" (%d,%d)=%.1f", p[0], p[1], g.get(p[0], p[1])));
        Site.apply(ss, g);
        for (Site s : ss) for (Site.Shaper sh : s.shapers()) {
            if (sh instanceof Site.Plateau p) log.append("; плато ").append(s.id).append(" y=").append(p.top())
                    .append(p.fixed() ? "" : " (AUTO)");
            else if (sh instanceof KordonTunnel.Cut c) log.append("; выемка ").append(s.id).append(" пол y=").append(c.floor());
        }
        return log.toString();
    }

    private static void run(String levelId, BlockSink s, int lvl, int node) {
        for (Site site : sites(levelId)) if (node < 0 || site.node == node) for (Builder b : site.builders()) b.build(s, lvl);
    }

    /** Рабочие места построек уровня (карточка 031): каждый сборщик выдаёт свои точки тем же проходом. */
    public static List<Point> points(String levelId, Ground g) { return points(levelId, g, POINT_LEVEL); }

    /** Рабочие места построек уровня на уровне развития lvl. */
    public static List<Point> points(String levelId, Ground g, int lvl) { return points(levelId, g, lvl, -1); }

    /** Рабочие места площадки узла node (П-02: NPC узла берут только свои места); node &lt; 0 — все. */
    public static List<Point> points(String levelId, Ground g, int lvl, int node) {
        java.util.ArrayList<Point> out = new java.util.ArrayList<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) {}
            @Override public int ground(int x, int z) { return g.at(x, z); }
            @Override public void mark(String kind, int x, int y, int z) { out.add(new Point(kind, x, y, z)); }
        };
        run(levelId, s, lvl, node);
        return out;
    }

    /**
     * Запретные полосы перед периметром (карточка 055): метки «kill_line» всех площадок уровня по порядку,
     * каждые 4 точки — углы одного прямоугольника {@link ru.stalkerzone.zonemap.KillZone}.
     */
    public static List<int[]> killZones(String levelId, Ground g, int lvl) {
        java.util.ArrayList<int[]> pts = new java.util.ArrayList<>();
        for (Site site : sites(levelId))
            for (Point p : points(levelId, g, lvl, site.node))
                if ("kill_line".equals(p.kind())) pts.add(new int[]{p.x(), p.z()});
        return ru.stalkerzone.zonemap.KillZone.fromPoints(pts);
    }

    // упаковка позиции: x и z по 21 бит (±1 млн), y — 21 бит; раньше z+2^21 вылезал в поле y и ломал координаты
    public static long key(int x, int y, int z) { return ((long) (x + (1 << 20)) << 42) | ((long) (y + 2048) << 21) | (z + (1 << 20)); }
    public static int kx(long k) { return (int) (k >>> 42) - (1 << 20); }
    public static int ky(long k) { return (int) ((k >>> 21) & 0x1fffff) - 2048; }
    public static int kz(long k) { return (int) (k & 0x1fffff) - (1 << 20); }

    /** Все блоки построек уровня (уровень развития lvl), с соединёнными решётками и стёклами. */
    public static Map<Long, String> build(String levelId, Ground g, int lvl) {
        Map<Long, String> m = new HashMap<>();
        BlockSink s = new BlockSink() {
            @Override public void set(int x, int y, int z, String st) { m.put(key(x, y, z), st); }
            @Override public int ground(int x, int z) { return g.at(x, z); }
        };
        run(levelId, s, lvl, -1);
        connect(m);
        return m;
    }

    /** Решётки и стёкла-панели в генерации мира сами не соединяются: проставить стороны по соседям. */
    static void connect(Map<Long, String> m) {
        Map<Long, String> upd = new HashMap<>();
        for (Map.Entry<Long, String> e : m.entrySet()) {
            String st = e.getValue();
            if (st.contains("[") || !(st.endsWith("iron_bars") || st.endsWith("_pane"))) continue;
            long k = e.getKey();
            int x = kx(k), y = ky(k), z = kz(k);
            String n = side(m, x, y, z - 1), so = side(m, x, y, z + 1), w = side(m, x - 1, y, z), ea = side(m, x + 1, y, z);
            upd.put(k, st + "[north=" + n + ",south=" + so + ",west=" + w + ",east=" + ea + "]");
        }
        m.putAll(upd);
    }

    private static String side(Map<Long, String> m, int x, int y, int z) {
        String s = m.get(key(x, y, z));
        return s != null && !s.equals("minecraft:air") && !s.contains("ladder") && !s.contains("door") ? "true" : "false";
    }
}
