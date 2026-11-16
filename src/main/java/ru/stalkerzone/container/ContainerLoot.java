package ru.stalkerzone.container;

import ru.stalkerzone.zonemap.atlas.Json;

import java.util.ArrayList;
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
        for (Object io : Json.list(root.get("items"))) {
            Map<String, Object> i = Json.obj(io);
            List<Object> n = Json.list(i.get("n"));
            List<String> tags = new ArrayList<>();
            for (Object t : Json.list(i.get("tags"))) tags.add(Json.str(t));
            items.add(new Item(Json.str(i.get("id")), Json.str(i.get("gun")), Json.str(i.get("mode")), Json.str(i.get("ammo")),
                    Json.str(i.get("tier")), Json.num(i.get("w")), (int) Json.num(n.get(0)), (int) Json.num(n.get(1)), tags, Json.str(i.get("ru"))));
        }
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

    private double itemWeight(Group g, Item it) {
        double w = it.w;
        if (g != null) for (String t : it.tags) w *= g.tags.getOrDefault(t, 1.0);
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
    public List<Drop> roll(String kind, String place, int z, Random r) {
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
            for (Item it : items) if (it.tier.equals(tier) && allowed(g, it)) { cand.add(it); sum += itemWeight(g, it); }
            double x = r.nextDouble() * sum;
            Item got = cand.get(cand.size() - 1);
            for (Item it : cand) { x -= itemWeight(g, it); if (x < 0) { got = it; break; } }
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

    /** Все id предметов (без gun/ammo) — для проверки, что они есть в моде. */
    public List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (Item it : items) if (it.id != null) ids.add(it.id);
        return ids;
    }

    public boolean knowsKind(String kind) { return byKind.containsKey(kind); }

    public boolean knowsPlace(String place) { return places.containsKey(place); }
}
