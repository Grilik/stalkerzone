import ru.stalkerzone.zonemap.build.KordonTunnel;
import ru.stalkerzone.zonemap.build.Prefabs;
import ru.stalkerzone.zonemap.build.Site;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * П-23: туннель с «электрами» под ж/д насыпью (узел 7, смарт-террейн 4_9) — площадка tunnel.
 * Рельеф: синтетическая насыпь (гребень 10 блоков вдоль x по z −262, откосы 1:1,2) на поле высотой 100.
 * Проверяется: пол и выемка у порталов, проход сквозь туннель (обход в 3D, уровни 0–3), свод и фасады, рельсы над ним, мусор
 * и копоть, 5 мест anomaly внутри, две заставы по разные стороны насыпи с нужными рабочими местами.
 */
public class TunnelTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    static final int FIELD = 100;

    /** Поле 100 и насыпь вдоль x: гребень |z+262| ≤ 4 высотой 10, откосы 1,2 блока на блок. */
    static double embankment(int x, int z) {
        double d = Math.abs(z + 262) - 4;
        return FIELD + Math.max(0, Math.min(10, 10 - 1.2 * Math.max(0, d)));
    }

    static final class G implements Site.Grid {
        final Map<Long, Double> m = new HashMap<>();
        final java.util.function.BiFunction<Integer, Integer, Double> base;
        G(java.util.function.BiFunction<Integer, Integer, Double> base) { this.base = base; }
        static long k(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
        public boolean contains(int x, int z) { return x >= -200 && x <= 700 && z >= -900 && z <= 300; }
        public double get(int x, int z) { Double v = m.get(k(x, z)); return v != null ? v : base.apply(x, z); }
        public void set(int x, int z, double y) { m.put(k(x, z), y); }
        int block(int x, int z) { return (int) Math.floor(Math.round(get(x, z) * 8) / 8.0); }
    }

    /** Мир для обхода: блоки построек поверх земли карты высот. */
    static final class W {
        final Map<Long, String> blocks;
        final G g;
        W(Map<Long, String> blocks, G g) { this.blocks = blocks; this.g = g; }

        String at(int x, int y, int z) { return blocks.get(Prefabs.key(x, y, z)); }

        static boolean thin(String s) {   // не мешают телу: воздух, огонь, фонари, рельсы, коврики
            return s.equals("minecraft:air") || s.contains("campfire") || s.contains("lantern") || s.endsWith("rail")
                    || s.contains("rail[") || s.contains("carpet");
        }

        static boolean slab(String s) { return s.contains("_slab[type=bottom]"); }

        boolean solid(int x, int y, int z) {
            String s = at(x, y, z);
            if (s != null) return !thin(s) && !slab(s);
            return y <= g.block(x, z);
        }

        boolean support(int x, int y, int z) {
            String s = at(x, y, z);
            if (s != null) return !thin(s) || s.contains("rail");
            return y <= g.block(x, z);
        }

        /** Клетка для стояния: ноги и голова свободны, под ногами опора. */
        boolean stand(int x, int y, int z) { return !solid(x, y, z) && !solid(x, y + 1, z) && support(x, y - 1, z); }
    }

    /** Обход 4-соседний с прыжком на 1 и спуском до 3; через насыпь — только внутри проёма (x 129..135). */
    static boolean walk(W w, int sx, int sy, int sz, int gz, boolean south) {
        int lo = KordonTunnel.AX - 3, hi = KordonTunnel.AX + 3;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(new int[] {sx, sy, sz});
        seen.add(Prefabs.key(sx, sy, sz));
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (south ? c[2] >= gz : c[2] <= gz) return true;
            for (int[] s : d) {
                int nx = c[0] + s[0], nz = c[2] + s[1];
                if (nx < 106 || nx > 158 || nz < -296 || nz > -228) continue;
                if (nz >= KordonTunnel.ZN0 && nz <= KordonTunnel.ZS1 && (nx < lo || nx > hi)) continue;
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = c[1] + dy;
                    if (!w.stand(nx, ny, nz)) continue;
                    if (dy == 1 && w.solid(c[0], c[1] + 2, c[2])) continue;   // прыжок под потолком
                    if (seen.add(Prefabs.key(nx, ny, nz))) q.add(new int[] {nx, ny, nz});
                    break;
                }
            }
        }
        return false;
    }

    static int count(Map<Long, String> m, java.util.function.Predicate<String> p) {
        int n = 0;
        for (Map.Entry<Long, String> e : m.entrySet()) {
            int x = Prefabs.kx(e.getKey()), z = Prefabs.kz(e.getKey());
            if (x >= 106 && x <= 158 && z >= -296 && z <= -228 && p.test(e.getValue())) n++;
        }
        return n;
    }

    public static void main(String[] a) {
        String level = "l01_escape";
        Site site = Prefabs.site(level, Prefabs.NODE_TUNNEL);
        ck(site != null, "площадка tunnel (узел 7) есть");
        ck(site.covers(130, -260), "центр узла «Туннель с электрами» (130, −260) в площадке");
        ck(site.covers(131, -254) && site.covers(KordonTunnel.AX, -285) && site.covers(KordonTunnel.AX, -239), "костёр узла (131, −254) и оба двора в площадке");

        // ---- рельеф: до правки
        G g = new G(TunnelTest::embankment);
        Prefabs.Ground gr0 = g::block;
        int floor0 = KordonTunnel.floorOf(gr0);
        ck(floor0 == FIELD, "пол туннеля по земле дворов до правки = поле: " + floor0);

        String log = Prefabs.shape(level, g);
        System.out.println(log);
        ck(log.contains("выемка tunnel пол y=" + FIELD), "лог рельефа: выемка tunnel, пол y=" + FIELD);
        boolean flat = true;
        for (int[] r : new int[][] {KordonTunnel.NORTH_YARD, KordonTunnel.SOUTH_YARD})
            for (int x = r[0]; x <= r[2]; x++) for (int z = r[1]; z <= r[3]; z++) if (g.block(x, z) != FIELD) flat = false;
        ck(flat, "дворы заставы ровные на отметке пола");
        ck(KordonTunnel.floorOf(g::block) == FIELD, "пол после правки тот же (функция без состояния)");
        int worst = 0, changedOutside = 0;
        int[] nb = {KordonTunnel.NORTH_YARD[0] - KordonTunnel.MARGIN, KordonTunnel.NORTH_YARD[1] - KordonTunnel.MARGIN,
                KordonTunnel.SOUTH_YARD[2] + KordonTunnel.MARGIN, KordonTunnel.SOUTH_YARD[3] + KordonTunnel.MARGIN};
        for (int x = nb[0] - 2; x <= nb[2] + 2; x++) for (int z = nb[1] - 2; z <= nb[3] + 2; z++) {
            boolean in = x >= nb[0] && x <= nb[2] && z >= nb[1] && z <= nb[3];
            if (!in && Math.abs(g.get(x, z) - embankment(x, z)) > 1e-9) changedOutside++;
            worst = Math.max(worst, Math.max(Math.abs(g.block(x, z) - g.block(x + 1, z)), Math.abs(g.block(x, z) - g.block(x, z + 1))));
        }
        ck(changedOutside == 0, "вне границ выемки рельеф прежний (изменено " + changedOutside + ")");
        ck(worst <= 3, "откосы выемки без стенок выше 3 блоков: перепад соседних колонок " + worst);
        ck(Prefabs.plateauAt(KordonTunnel.AX, -285) == null, "дворы — не плато деревни (отдельная правка)");

        final int F = FIELD;
        Prefabs.Ground gr = g::block;
        List<Prefabs.Point> pts = Prefabs.points(level, gr, Prefabs.POINT_LEVEL, Prefabs.NODE_TUNNEL);
        ck(!pts.isEmpty(), "у узла 7 есть рабочие места: " + pts.size());
        ck(pts.stream().allMatch(p -> site.covers(p.x(), p.z())), "все места — в границах площадки");

        // ---- уровни 0–3: проход, свод, фасады, рельсы
        for (int lvl = 0; lvl <= 3; lvl++) {
            Map<Long, String> m = Prefabs.build(level, gr, lvl);
            W w = new W(m, g);
            String L = "ур." + lvl + ": ";
            int outside = 0;
            for (Map.Entry<Long, String> e : m.entrySet())
                if (!Prefabs.cleared(level, Prefabs.kx(e.getKey()), Prefabs.kz(e.getKey()))) outside++;
            ck(outside == 0, L + "все блоки в границах замены (вне: " + outside + ")");

            // проход сквозь туннель: с севера двор → на юг двор, и обратно
            ck(w.stand(KordonTunnel.AX, F + 1, -280) && w.stand(KordonTunnel.AX, F + 1, -245), L + "оба двора проходимы");
            ck(walk(w, KordonTunnel.AX, F + 1, -280, -245, true), L + "проход с севера на юг сквозь туннель");
            ck(walk(w, KordonTunnel.AX, F + 1, -245, -280, false), L + "проход с юга на север сквозь туннель");

            // свод: по оси 5 свободных блоков и перекрытие; у стен 3
            boolean axis = true, ceil = true, walls = true;
            for (int z = KordonTunnel.ZN0; z <= KordonTunnel.ZS1; z++) {
                for (int y = F + 1; y <= F + 5; y++) if (w.solid(KordonTunnel.AX, y, z)) axis = false;
                if (!w.solid(KordonTunnel.AX, F + 6, z)) ceil = false;
                for (int y = F + 1; y <= F + 3; y++)
                    if (!(w.solid(KordonTunnel.AX - 4, y, z) || w.at(KordonTunnel.AX - 4, y, z) != null)
                            || !(w.solid(KordonTunnel.AX + 4, y, z) || w.at(KordonTunnel.AX + 4, y, z) != null)) walls = false;
            }
            ck(axis, L + "по оси туннеля 5 свободных блоков по всей длине (13)");
            ck(ceil, L + "свод закрыт над осью");
            ck(walls, L + "бетонные стены по обе стороны прохода");
            boolean roof = true;
            for (int x = KordonTunnel.AX - 5; x <= KordonTunnel.AX + 5; x++) for (int z = KordonTunnel.ZI0; z <= KordonTunnel.ZI1; z++) {
                boolean open = false;   // над перекрытием нет прохода «в небо»: сверху колонка закрыта хотя бы одним твёрдым блоком выше свода
                for (int y = F + 7; y <= F + 12; y++) if (w.solid(x, y, z)) open = true;
                if (!open) roof = false;
            }
            ck(roof, L + "над сводом насыпь: колонки x 127..137 закрыты");
            for (int fz : new int[] {KordonTunnel.ZN0, KordonTunnel.ZS1}) {
                int best = 0;
                for (int x = KordonTunnel.AX - 4; x <= KordonTunnel.AX + 4; x++)
                    for (int y = F; y <= F + 14; y++) if (w.solid(x, y, fz)) best = Math.max(best, y - F);
                ck(best >= 9 && best <= 11, L + "фасад портала z " + fz + " высотой до " + best + " над полом (обмер 9–11)");
            }

            // рельсы над туннелем и по всей площадке
            int rails = count(m, s -> s.startsWith("minecraft:rail"));
            ck(rails >= (lvl == 0 ? 70 : 80), L + "рельсов: " + rails);
            boolean railsOk = true, high = true;
            for (Map.Entry<Long, String> e : m.entrySet()) {
                if (!e.getValue().startsWith("minecraft:rail")) continue;
                int x = Prefabs.kx(e.getKey()), y = Prefabs.ky(e.getKey()), z = Prefabs.kz(e.getKey());
                if (!w.solid(x, y - 1, z)) railsOk = false;
                if (x >= 124 && x <= 140 && y < F + 9) high = false;
                if (z != -263 && z != -261) railsOk = false;
            }
            ck(railsOk, L + "каждый рельс на опоре, две нитки z −263 и −261");
            ck(high, L + "над туннелем рельсы не ниже пола + 9 (x 124..140)");

            // мусор, копоть, остов
            int soot = count(m, s -> s.equals("minecraft:black_concrete") || s.equals("minecraft:coal_block"));
            int junk = count(m, s -> s.contains("tires") || s.contains("rusty_barrel") || s.contains("zm_rubble") || s.contains("wooden_crate"));
            int car = 0;
            for (Map.Entry<Long, String> e : m.entrySet()) {
                int x = Prefabs.kx(e.getKey()), y = Prefabs.ky(e.getKey()), z = Prefabs.kz(e.getKey());
                if (x >= 129 && x <= 131 && z >= -264 && z <= -259 && y >= F + 1 && !e.getValue().equals("minecraft:air")) car++;
            }
            ck(soot >= 30, L + "обгоревшие следы (чёрный бетон, уголь): " + soot);
            ck(junk >= 6, L + "мусор и обломки: " + junk);
            ck(car >= 15, L + "остов машины вдоль западной стены: " + car);
            ck(Prefabs.build(level, gr, lvl).equals(m), L + "сборка детерминирована");
        }

        // ---- рабочие места: 5 аномалий внутри, две заставы по разные стороны насыпи
        Map<Long, String> m1 = Prefabs.build(level, gr, Prefabs.POINT_LEVEL);
        W w1 = new W(m1, g);
        List<Prefabs.Point> an = new ArrayList<>(), north = new ArrayList<>(), south = new ArrayList<>(), other = new ArrayList<>();
        for (Prefabs.Point p : pts) {
            if (p.kind().equals("anomaly")) an.add(p);
            else if (p.z() <= KordonTunnel.ZN0 - 1) north.add(p);
            else if (p.z() >= KordonTunnel.ZS1 + 1) south.add(p);
            else other.add(p);
        }
        ck(an.size() >= 4 && an.size() <= 6, "точек anomaly внутри туннеля: " + an.size() + " (нужно 4–6)");
        boolean inside = true, free = true;
        for (Prefabs.Point p : an) {
            if (p.x() < KordonTunnel.AX - 3 || p.x() > KordonTunnel.AX + 3 || p.z() < KordonTunnel.ZI0 || p.z() > KordonTunnel.ZI1 || p.y() != F + 1) inside = false;
            if (!w1.stand(p.x(), p.y(), p.z())) free = false;
        }
        ck(inside, "anomaly — в проходе (x 129..135, z −266..−258), на полу");
        ck(free, "в клетках anomaly свободно и есть пол (электры можно ставить)");
        ck(other.isEmpty(), "прочих мест нет: все — anomaly или одной из двух застав (прочих " + other.size() + ")");
        ck(!north.isEmpty() && !south.isEmpty(), "две группы мест: северная " + north.size() + ", южная " + south.size());
        for (String side : new String[] {"north", "south"}) {
            List<Prefabs.Point> grp = side.equals("north") ? north : south;
            Map<String, Integer> c = new HashMap<>();
            for (Prefabs.Point p : grp) c.merge(p.kind(), 1, Integer::sum);
            String N = side.equals("north") ? "северная застава: " : "южная застава: ";
            int guard = c.getOrDefault("guard", 0);
            ck(guard >= 1 && guard <= 2, N + "guard 1–2: " + guard);
            ck(c.getOrDefault("rest", 0) == 2, N + "rest ×2: " + c.getOrDefault("rest", 0));
            ck(c.getOrDefault("bed", 0) == 3, N + "bed ×3: " + c.getOrDefault("bed", 0));
            ck(c.getOrDefault("shelter", 0) == 2, N + "shelter ×2: " + c.getOrDefault("shelter", 0));
            ck(c.getOrDefault("cook", 0) == 1, N + "cook ×1 (у костра): " + c.getOrDefault("cook", 0));
            boolean ok = true;
            for (Prefabs.Point p : grp) if (!w1.stand(p.x(), p.y(), p.z())) { ok = false; System.out.println("  занято: " + p); }
            ck(ok, N + "каждое место свободно (ноги и голова) и стоит на опоре");
        }
        int nmaxz = north.stream().mapToInt(Prefabs.Point::z).max().orElse(0), smin = south.stream().mapToInt(Prefabs.Point::z).min().orElse(0);
        ck(nmaxz < KordonTunnel.ZN0 && smin > KordonTunnel.ZS1, "заставы по разные стороны насыпи: север z ≤ " + nmaxz + ", юг z ≥ " + smin);
        ck(smin - nmaxz >= 14, "между группами не меньше длины туннеля: " + (smin - nmaxz));
        // насыпь между ними: гребень выше, а дальняя застава не видна поверху — между группами земля выше пола на 8+
        ck(g.block(KordonTunnel.AX, -262) >= F + 8, "между группами — насыпь (гребень +" + (g.block(KordonTunnel.AX, -262) - F) + ")");
        // костёр южной заставы — ровно в точке campfire узла
        ck(m1.getOrDefault(Prefabs.key(131, F + 1, -254), "").startsWith("minecraft:campfire"), "костёр южной заставы в точке campfire узла (131, −254)");
        ck(count(m1, s -> s.startsWith("minecraft:campfire")) == 2, "костров ровно два — по одному у входа");
        ck(count(m1, s -> s.contains("zm_sandbags")) >= 20 && count(m1, s -> s.endsWith("_wool")) >= 30, "мешки и палатки на месте");

        // ---- без правки рельефа и на ровной земле (превью, юнит-тесты): сборка не падает, проход есть
        for (int lvl = 0; lvl <= 3; lvl++) {
            Prefabs.Ground flatG = (x, z) -> 75;
            Map<Long, String> fm = Prefabs.build(level, flatG, lvl);
            G fg = new G((x, z) -> 75.0);
            ck(KordonTunnel.floorOf(flatG) == 75 && walk(new W(fm, fg), KordonTunnel.AX, 76, -280, -245, true), "ровная земля 75, ур." + lvl + ": пол 75, проход есть");
        }
        System.exit(f > 0 ? 1 : 0);
    }
}
