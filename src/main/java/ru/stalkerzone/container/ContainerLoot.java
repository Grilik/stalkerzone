package ru.stalkerzone.container;

import ru.stalkerzone.zonemap.atlas.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Э1-17: генератор добычи контейнеров мода по data-файлу {@code data/stalkerzone/container_loot/loot.json}.
 * Без Minecraft: его вызывают блок-сущность (один раз при первом открытии), тест {@code ContainerLootTest}
 * и сводка по миру {@code tools/containers/LootSummary.java}.
 */
public final class ContainerLoot {
    public static final String[] TIERS = {"junk", "common", "useful", "rare", "very_rare"};
    public static final String[] PLACES = {"storage", "base", "home", "field", "attic"};

    /** Предмет добычи: id предмета (или gun/ammo TaCZ), количество, уровень. */
    public record Drop(String id, String gun, String mode, String ammo, int count, String tier, String ru) {
        public String key() { return gun != null ? "gun:" + gun : ammo != null ? "ammo:" + ammo : id; }
    }

    private record Item(String id, String gun, String mode, String ammo, String tier, double w, int lo, int hi, List<String> tags, String ru) {}
    private record Group(Map<String, Double> tier, Map<String, Double> tags, List<String> only, double empty) {}
    private record Place(double empty, int count, Map<String, Double> tier) {}

    private final Map<String, Double> tiers = new LinkedHashMap<>();
    private final Map<String, Double> depthTier = new HashMap<>();
    private final double southZ, northZ;
    private final int countLo, countHi;
    private final Map<String, Place> places = new HashMap<>();
    private final Map<String, Group> byKind = new HashMap<>();
    private final List<Item> items = new ArrayList<>();
    /** Поправки тегов по локации (ближайшее место places.json). */
    private final Map<String, Map<String, Double>> locTags = new HashMap<>();
    /** Бюджет локации: оружие, артефакты, рубли — раскладка при правке мира ({@link #plan}). */
    private record Budget(int gunLo, int gunHi, int artLo, int artHi, int rubLo, int rubHi, int gearLo, int gearHi,
                          List<String> guns, List<String> arts, List<String> gear) {}
    /** Слияние мест places.json в одну локацию бюджета (Припять основная). */
    private final Map<String, String> aliases = new HashMap<>();
    /** Полный бюджет — у места с не меньше чем fullAt контейнерами; меньше — пропорционально. */
    private int fullAt = 1;
    private final Map<String, Budget> budgets = new HashMap<>();
    private final Map<String, String> gunModes = new HashMap<>();
    private int maxPerContainer = 2;

    public ContainerLoot(String json) {
        Map<String, Object> root = Json.obj(Json.parse(json));
        nums(Json.obj(root.get("tiers")), tiers);
        List<Object> c = Json.list(root.get("count"));
        countLo = (int) Json.num(c.get(0)); countHi = (int) Json.num(c.get(1));
        Map<String, Object> d = Json.obj(root.get("depth"));
        southZ = Json.num(d.get("south_z")); northZ = Json.num(d.get("north_z"));
        nums(Json.obj(d.get("tier")), depthTier);
        for (Map.Entry<String, Object> e : Json.obj(root.get("places")).entrySet()) {
            Map<String, Object> p = Json.obj(e.getValue());
            Map<String, Double> t = new HashMap<>();
            nums(Json.obj(p.get("tier")), t);
            places.put(e.getKey(), new Place(Json.num(p.get("empty")), (int) Json.num(p.get("count")), t));
        }
        for (Object go : Json.obj(root.get("groups")).values()) {
            Map<String, Object> g = Json.obj(go);
            Map<String, Double> t = new HashMap<>(), tg = new HashMap<>();
            nums(Json.obj(g.get("tier")), t);
            nums(Json.obj(g.get("tags")), tg);
            List<String> only = new ArrayList<>();
            if (g.get("only") != null) for (Object o : Json.list(g.get("only"))) only.add(Json.str(o));
            double em = g.get("empty") != null ? Json.num(g.get("empty")) : 1.0;
            Group gr = new Group(t, tg, only, em);
            for (Object k : Json.list(g.get("kinds"))) byKind.put(Json.str(k), gr);
        }
        Map<String, Object> locs = Json.obj(root.get("locations"));
        if (locs != null) for (Map.Entry<String, Object> e : locs.entrySet()) {
            Map<String, Object> lo = Json.obj(e.getValue());
            if (lo == null) continue;
            Map<String, Double> t = new HashMap<>();
            nums(Json.obj(lo.get("tags")), t);
            locTags.put(e.getKey(), t);
        }
        Map<String, Object> b = Json.obj(root.get("budget"));
        if (b != null) {
            Map<String, Object> artPools = Json.obj(b.get("artifacts"));
            for (Map.Entry<String, Object> e : Json.obj(b.get("gun_modes")).entrySet()) gunModes.put(e.getKey(), Json.str(e.getValue()));
            maxPerContainer = (int) Json.num(b.get("max_per_container"));
            if (b.get("full_at") != null) fullAt = (int) Json.num(b.get("full_at"));
            Map<String, Object> al = Json.obj(b.get("aliases"));
            if (al != null) for (Map.Entry<String, Object> e : al.entrySet()) aliases.put(e.getKey(), Json.str(e.getValue()));
            for (Object go : Json.obj(b.get("groups")).values()) {
                Map<String, Object> g = Json.obj(go);
                List<String> guns = new ArrayList<>(), arts = new ArrayList<>(), gear = new ArrayList<>();
                for (Object o : Json.list(g.get("gun_pool"))) guns.add(Json.str(o));
                if (g.get("gear_pool") != null) for (Object o : Json.list(g.get("gear_pool"))) gear.add(Json.str(o));
                for (Object t : Json.list(g.get("art_tiers")))
                    for (Object o : Json.list(artPools.get(String.valueOf((int) Json.num(t))))) arts.add(Json.str(o));
                int[] gr = range(g.get("guns")), ar = range(g.get("artifacts")), rr = range(g.get("rubles"));
                int[] ge = g.get("gear") != null ? range(g.get("gear")) : new int[]{0, 0};
                Budget bu = new Budget(gr[0], gr[1], ar[0], ar[1], rr[0], rr[1], ge[0], ge[1], guns, arts, gear);
                for (Object l : Json.list(g.get("locs"))) budgets.put(Json.str(l), bu);
            }
        }
        for (Object io : Json.list(root.get("items"))) {
            Map<String, Object> i = Json.obj(io);
            List<Object> n = Json.list(i.get("n"));
            List<String> tags = new ArrayList<>();
            for (Object t : Json.list(i.get("tags"))) tags.add(Json.str(t));
            items.add(new Item(Json.str(i.get("id")), Json.str(i.get("gun")), Json.str(i.get("mode")), Json.str(i.get("ammo")),
                    Json.str(i.get("tier")), Json.num(i.get("w")), (int) Json.num(n.get(0)), (int) Json.num(n.get(1)), tags, Json.str(i.get("ru"))));
        }
    }

    private static int[] range(Object o) {
        List<Object> l = Json.list(o);
        return new int[]{(int) Json.num(l.get(0)), (int) Json.num(l.get(1))};
    }

    private static void nums(Map<String, Object> m, Map<String, Double> out) {
        if (m == null) return;
        for (Map.Entry<String, Object> e : m.entrySet()) if (!Double.isNaN(Json.num(e.getValue()))) out.put(e.getKey(), Json.num(e.getValue()));
    }

    /** Глубина Зоны: 0 — юг (Кордон), 1 — север. */
    public double depth(int z) { return Math.max(0, Math.min(1, (southZ - z) / (southZ - northZ))); }

    /** Вероятность пустого контейнера. */
    public double emptyChance(String kind, String place) {
        Group g = byKind.get(kind);
        return Math.min(1, place(place).empty * (g == null ? 1 : g.empty));
    }

    private Place place(String p) { return places.getOrDefault(p, places.get("field")); }

    private boolean allowed(Group g, Item it) {
        if (g == null || g.only.isEmpty()) return true;
        for (String t : it.tags) if (g.only.contains(t)) return true;
        return false;
    }

    private double itemWeight(Group g, Item it, String loc) {
        double w = it.w;
        Map<String, Double> lt = loc == null ? null : locTags.get(loc);
        for (String t : it.tags) {
            if (g != null) w *= g.tags.getOrDefault(t, 1.0);
            if (lt != null) w *= lt.getOrDefault(t, 1.0);
        }
        return w;
    }

    /** Веса уровней с поправками глубины, места и типа (уровень без доступных предметов — 0). */
    public Map<String, Double> tierWeights(String kind, String place, int z) {
        Group g = byKind.get(kind);
        Place p = place(place);
        double dep = depth(z);
        Map<String, Double> out = new LinkedHashMap<>();
        for (String t : TIERS) {
            boolean any = false;
            for (Item it : items) if (it.tier.equals(t) && allowed(g, it)) { any = true; break; }
            double w = !any ? 0 : tiers.getOrDefault(t, 0.0) * (1 + (depthTier.getOrDefault(t, 1.0) - 1) * dep)
                    * p.tier.getOrDefault(t, 1.0) * (g == null ? 1 : g.tier.getOrDefault(t, 1.0));
            out.put(t, w);
        }
        return out;
    }

    /** Добыча одного контейнера: пусто с вероятностью emptyChance, иначе 1–4 предмета. */
    public List<Drop> roll(String kind, String place, int z, Random r) { return roll(kind, place, z, null, r); }

    /** То же с поправками локации loc (ближайшее место places.json; null — без поправок). */
    public List<Drop> roll(String kind, String place, int z, String loc, Random r) {
        List<Drop> out = new ArrayList<>();
        if (r.nextDouble() < emptyChance(kind, place)) return out;
        Group g = byKind.get(kind);
        int n = Math.max(countLo, Math.min(countHi, countLo + r.nextInt(countHi - countLo + 1) + place(place).count));
        Map<String, Double> tw = tierWeights(kind, place, z);
        for (int i = 0; i < n; i++) {
            String tier = pick(tw, r);
            if (tier == null) break;
            List<Item> cand = new ArrayList<>();
            double sum = 0;
            for (Item it : items) if (it.tier.equals(tier) && allowed(g, it)) { cand.add(it); sum += itemWeight(g, it, loc); }
            double x = r.nextDouble() * sum;
            Item got = cand.get(cand.size() - 1);
            for (Item it : cand) { x -= itemWeight(g, it, loc); if (x < 0) { got = it; break; } }
            int cnt = got.lo + (got.hi > got.lo ? r.nextInt(got.hi - got.lo + 1) : 0);
            out.add(new Drop(got.id, got.gun, got.mode, got.ammo, cnt, tier, got.ru));
        }
        return out;
    }

    private static String pick(Map<String, Double> w, Random r) {
        double sum = 0;
        for (double v : w.values()) sum += v;
        if (sum <= 0) return null;
        double x = r.nextDouble() * sum;
        String last = null;
        for (Map.Entry<String, Double> e : w.entrySet()) {
            if (e.getValue() <= 0) continue;
            last = e.getKey();
            x -= e.getValue();
            if (x < 0) return e.getKey();
        }
        return last;
    }

    /** Контейнер для раскладки бюджета: вид, место, локация. */
    public record Box(String kind, String place, String loc) {}

    /**
     * Раскладка бюджета локаций по контейнерам (при правке мира): оружие — в любые, кроме цинков (чердаки — в последнюю
     * очередь), артефакты — только в сейфы, рубли — 2–6 пачек (сначала сейфы и шкафы). Не больше maxPerContainer
     * оружия и артефактов в одном контейнере. Возвращает индекс контейнера -> предметы.
     */
    public Map<Integer, List<Drop>> plan(List<Box> boxes, Random r) {
        Map<Integer, List<Drop>> out = new HashMap<>();
        Map<String, List<Integer>> byLoc = new LinkedHashMap<>();
        for (int i = 0; i < boxes.size(); i++) byLoc.computeIfAbsent(locOf(boxes.get(i).loc()), k -> new ArrayList<>()).add(i);
        for (Map.Entry<String, List<Integer>> e : byLoc.entrySet()) {
            Budget b = budgets.get(e.getKey());
            if (b == null) continue;
            List<Integer> all = e.getValue();
            double sc = Math.min(1.0, all.size() / (double) fullAt);  // мало контейнеров — бюджет меньше
            List<Integer> gunBox = new ArrayList<>(), gunLate = new ArrayList<>(), safes = new ArrayList<>(), money = new ArrayList<>(), best = new ArrayList<>();
            for (int i : all) {
                Box x = boxes.get(i);
                if (x.kind().startsWith("safe")) { safes.add(i); money.add(i); }
                else if (x.kind().contains("cabinet") || x.kind().startsWith("locker")) money.add(i);
                if (!x.kind().startsWith("ammo_zinc")) ("attic".equals(x.place()) ? gunLate : gunBox).add(i);
                // артефакт без сейфа — в лучший контейнер базы или склада, не в полевые ящики и не на чердаки
                if (("base".equals(x.place()) || "storage".equals(x.place()))
                        && (x.kind().startsWith("army_crate") || x.kind().startsWith("metal_box") || x.kind().startsWith("locker") || x.kind().contains("cabinet")))
                    best.add(i);
            }
            Collections.shuffle(best, r);
            List<Integer> artBox = safes.isEmpty() ? best : safes;
            Collections.shuffle(gunBox, r);
            Collections.shuffle(gunLate, r);
            gunBox.addAll(gunLate);
            int guns = scaled(b.gunLo, b.gunHi, sc, r);
            for (int k = 0; k < guns && !b.guns.isEmpty(); k++) {
                String g = b.guns.get(r.nextInt(b.guns.size()));
                put(out, gunBox, new Drop(null, g, gunModes.getOrDefault(g, "SEMI"), null, 1, "budget", "оружие"));
            }
            int arts = scaled(b.artLo, b.artHi, sc, r);
            Collections.shuffle(safes, r);
            for (int k = 0; k < arts && !b.arts.isEmpty(); k++)
                put(out, artBox, new Drop("stalkerzone:artifact_" + b.arts.get(r.nextInt(b.arts.size())), null, null, null, 1, "budget", "артефакт"));
            int gear = scaled(b.gearLo, b.gearHi, sc, r);
            for (int k = 0; k < gear && !b.gear.isEmpty(); k++)
                put(out, gunBox, new Drop(b.gear.get(r.nextInt(b.gear.size())), null, null, null, 1, "budget", "снаряжение"));
            int rub = (int) (sc * (b.rubLo + r.nextInt(b.rubHi - b.rubLo + 1))) / 50 * 50;
            List<Integer> rbox = new ArrayList<>(money);
            Collections.shuffle(rbox, r);
            List<Integer> rest = new ArrayList<>(all);
            rest.removeAll(rbox);
            Collections.shuffle(rest, r);
            rbox.addAll(rest);
            int parts = Math.max(1, Math.min(rbox.size(), 2 + r.nextInt(5)));
            for (int k = 0; k < parts && rub > 0; k++) {
                int part = k == parts - 1 ? rub : Math.max(50, rub / (parts - k) / 50 * 50);
                rub -= part;
                List<Drop> l = out.computeIfAbsent(rbox.get(k), q -> new ArrayList<>());
                for (int h = part / 100; h > 0; h -= 64) l.add(new Drop("stalkerzone:ruble_100", null, null, null, Math.min(64, h), "budget", "рубли"));
                if (part % 100 > 0) l.add(new Drop("stalkerzone:ruble", null, null, null, part % 100, "budget", "рубли"));
            }
        }
        return out;
    }

    /** Положить оружие или артефакт в первый контейнер списка, где таких меньше maxPerContainer; занятый — в конец. */
    private void put(Map<Integer, List<Drop>> out, List<Integer> cand, Drop d) {
        for (int i : new ArrayList<>(cand)) {
            List<Drop> l = out.computeIfAbsent(i, k -> new ArrayList<>());
            long n = l.stream().filter(x -> !"рубли".equals(x.ru())).count();
            if (n < maxPerContainer) {
                l.add(d);
                cand.remove((Integer) i);
                cand.add(i);
                return;
            }
        }
    }

    public boolean hasBudget(String loc) { return budgets.containsKey(locOf(loc)); }

    /** Локация бюджета для места places.json (слияние частей Припяти). */
    public String locOf(String loc) { return loc == null ? null : aliases.getOrDefault(loc, loc); }

    /** Случайное число из [lo, hi], умноженное на долю sc (округление вероятностное). */
    private static int scaled(int lo, int hi, double sc, Random r) {
        double v = (lo + r.nextInt(hi - lo + 1)) * sc;
        int n = (int) v;
        return n + (r.nextDouble() < v - n ? 1 : 0);
    }

    /** Все id предметов (без gun/ammo) — для проверки, что они есть в моде. */
    public List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (Item it : items) if (it.id != null) ids.add(it.id);
        return ids;
    }

    public boolean knowsKind(String kind) { return byKind.containsKey(kind); }

    public boolean knowsPlace(String place) { return places.containsKey(place); }
}
