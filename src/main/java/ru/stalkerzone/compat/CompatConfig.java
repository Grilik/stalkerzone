package ru.stalkerzone.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * config/stalkerzone/compat.json — связь с другими модами.
 * guns: оружие TaCZ (своё szguns и любые паки), loadouts: что носят фракции,
 * decor: подмена блоков-декора мода блоками других модов (если такие блоки есть в игре),
 * mutants: какими существами других модов заменять мутантов логов.
 * Файл создаётся с настройками по умолчанию; его можно править, игра перечитывает его при старте.
 */
public final class CompatConfig {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Оружие: калибр, магазин, режим огня, цена в единицах «Снаряжения», дальность боя NPC, названия для /zone buy. */
    public record GunSpec(String id, String ammo, int mag, String mode, int gear, int range, int burst, List<String> names) {}

    public static final Map<String, GunSpec> GUNS = new LinkedHashMap<>();
    /** Фракция (имя как в симуляции) -> список (оружие, вес). Ключ "*leader" — для лидеров и заместителей. */
    public static final Map<String, List<Map.Entry<String, Integer>>> LOADOUTS = new LinkedHashMap<>();
    /** Патронов TaCZ в одной единице товара «Патроны». */
    public static final Map<String, Integer> ROUNDS_PER_UNIT = new HashMap<>();
    /** Блок мода -> блоки других модов, которыми его можно заменить. */
    public static final Map<String, List<String>> DECOR = new HashMap<>();
    /** Вид мутанта (bloodsucker, snork...) -> существа других модов. */
    public static final Map<String, List<String>> MUTANTS = new HashMap<>();
    public static int decorChance = 50;

    private static boolean loaded;
    /** Версия раскладок по умолчанию: при росте старый compat.json обновляется (замены декора и мутантов сохраняются). */
    static final int VERSION = 2;

    private CompatConfig() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path p = FMLPaths.CONFIGDIR.get().resolve("stalkerzone").resolve("compat.json");
        JsonObject root;
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) { GSON.toJson(defaults(), w); }
            }
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) { root = JsonParser.parseReader(r).getAsJsonObject(); }
            int ver = root.has("_version") ? root.get("_version").getAsInt() : 1;
            if (ver < VERSION) {
                // новая версия мода принесла оружие и раскладки: старый файл сохраняем рядом, свои замены декора и мутантов переносим
                Files.copy(p, p.resolveSibling("compat.json.v" + ver + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                JsonObject fresh = defaults();
                for (String k : new String[] {"decor", "mutants", "decor_chance"}) if (root.has(k)) fresh.add(k, root.get(k));
                try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) { GSON.toJson(fresh, w); }
                LOG.info("compat.json обновлён до версии {} (старый сохранён как compat.json.v{}.bak)", VERSION, ver);
                root = fresh;
            }
        } catch (Exception e) {
            LOG.warn("compat.json не прочитан ({}), беру настройки по умолчанию", e.toString());
            root = defaults();
        }
        parse(root);
    }

    private static void parse(JsonObject root) {
        GUNS.clear(); LOADOUTS.clear(); ROUNDS_PER_UNIT.clear(); DECOR.clear(); MUTANTS.clear();
        JsonObject guns = root.getAsJsonObject("guns");
        if (guns != null) for (Map.Entry<String, JsonElement> e : guns.entrySet()) {
            JsonObject g = e.getValue().getAsJsonObject();
            List<String> names = new ArrayList<>();
            if (g.has("names")) for (JsonElement n : g.getAsJsonArray("names")) names.add(n.getAsString());
            GUNS.put(e.getKey(), new GunSpec(e.getKey(), str(g, "ammo", "tacz:9mm"), num(g, "mag", 30), str(g, "mode", "SEMI"),
                    num(g, "gear", 2), num(g, "range", 24), num(g, "burst", 1), names));
        }
        JsonObject lo = root.getAsJsonObject("loadouts");
        if (lo != null) for (Map.Entry<String, JsonElement> e : lo.entrySet()) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>();
            for (JsonElement el : e.getValue().getAsJsonArray()) {
                JsonArray a = el.getAsJsonArray();
                list.add(Map.entry(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsInt() : 1));
            }
            LOADOUTS.put(e.getKey(), list);
        }
        JsonObject rpu = root.getAsJsonObject("rounds_per_unit");
        if (rpu != null) for (Map.Entry<String, JsonElement> e : rpu.entrySet()) ROUNDS_PER_UNIT.put(e.getKey(), e.getValue().getAsInt());
        readLists(root.getAsJsonObject("decor"), DECOR);
        readLists(root.getAsJsonObject("mutants"), MUTANTS);
        if (root.has("decor_chance")) decorChance = root.get("decor_chance").getAsInt();
    }

    private static void readLists(JsonObject o, Map<String, List<String>> into) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            List<String> l = new ArrayList<>();
            for (JsonElement el : e.getValue().getAsJsonArray()) l.add(el.getAsString());
            if (!l.isEmpty()) into.put(e.getKey(), l);
        }
    }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }

    private static int num(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }

    public static int roundsPerUnit(String ammo) { return ROUNDS_PER_UNIT.getOrDefault(ammo, 20); }

    /** Случайное оружие для фракции по весам; null — оставить старое снаряжение. */
    public static GunSpec pickGun(String faction, boolean leader, Random r) {
        load();
        List<Map.Entry<String, Integer>> list = leader && LOADOUTS.containsKey(faction + "*leader") ? LOADOUTS.get(faction + "*leader") : LOADOUTS.get(faction);
        if (list == null || list.isEmpty()) return null;
        int sum = 0;
        for (Map.Entry<String, Integer> e : list) sum += Math.max(0, e.getValue());
        if (sum <= 0) return null;
        int k = r.nextInt(sum);
        for (Map.Entry<String, Integer> e : list) {
            k -= Math.max(0, e.getValue());
            if (k < 0) return GUNS.get(e.getKey());
        }
        return null;
    }

    public static GunSpec byName(String name) {
        load();
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (GunSpec g : GUNS.values()) {
            if (g.id().equalsIgnoreCase(n)) return g;
            for (String a : g.names()) if (a.equalsIgnoreCase(n)) return g;
        }
        return null;
    }

    /** Подмена декоративного блока блоком другого мода, если тот есть в игре. */
    public static BlockState decor(String id, BlockState fallback, Random r) {
        load();
        List<String> alt = DECOR.get(id);
        if (alt == null || r.nextInt(100) >= decorChance) return fallback;
        String pick = alt.get(r.nextInt(alt.size()));
        ResourceLocation rl = ResourceLocation.tryParse(pick);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) return fallback;
        Block b = ForgeRegistries.BLOCKS.getValue(rl);
        return b == null ? fallback : b.defaultBlockState();
    }

    // ---------------------------------------------------------------- значения по умолчанию

    private static JsonObject gun(String ammo, int mag, String mode, int gear, int range, int burst, String... names) {
        JsonObject o = new JsonObject();
        o.addProperty("ammo", ammo); o.addProperty("mag", mag); o.addProperty("mode", mode);
        o.addProperty("gear", gear); o.addProperty("range", range); o.addProperty("burst", burst);
        JsonArray a = new JsonArray();
        for (String n : names) a.add(n);
        o.add("names", a);
        return o;
    }

    private static JsonArray pairs(Object... pairs) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < pairs.length; i += 2) {
            JsonArray e = new JsonArray();
            e.add((String) pairs[i]); e.add((Integer) pairs[i + 1]);
            a.add(e);
        }
        return a;
    }

    private static JsonArray list(String... s) {
        JsonArray a = new JsonArray();
        for (String x : s) a.add(x);
        return a;
    }

    static JsonObject defaults() {
        JsonObject root = new JsonObject();
        root.addProperty("_version", VERSION);
        root.addProperty("_about", "Совместимость Stalker Zone с другими модами. guns и loadouts работают при установленном TaCZ. "
                + "decor и mutants: впишите id блоков и существ из своих модов (например, сталкерских декораций и мутантов), "
                + "мод подставит их, если они есть в игре.");
        JsonObject g = new JsonObject();
        // своё оружие Зоны (ганпак szguns): советский и постсоветский арсенал, как в оригинальной трилогии
        g.add("szguns:pm", gun("szguns:9x18", 8, "SEMI", 1, 16, 1, "пм", "pm", "макаров"));
        g.add("szguns:pb", gun("szguns:9x18", 8, "SEMI", 1, 16, 1, "пб", "pb"));
        g.add("szguns:fort12", gun("szguns:9x18", 12, "SEMI", 1, 18, 1, "форт", "форт-12", "fort12"));
        g.add("szguns:aps", gun("szguns:9x18", 20, "AUTO", 2, 16, 3, "апс", "стечкин", "aps"));
        g.add("szguns:toz_obrez", gun("tacz:12g", 2, "SEMI", 1, 10, 1, "обрез", "obrez"));
        g.add("szguns:toz34", gun("tacz:12g", 2, "SEMI", 1, 14, 1, "тоз-34", "тоз", "двустволка", "toz34"));
        g.add("szguns:aks74u", gun("tacz:545x39", 30, "AUTO", 2, 24, 4, "аксу", "aks74u", "ксюха"));
        g.add("szguns:ak74", gun("tacz:545x39", 30, "AUTO", 3, 32, 4, "ак-74", "ак74", "ak74"));
        g.add("szguns:aks74", gun("tacz:545x39", 30, "AUTO", 3, 30, 4, "акс-74", "акс74", "aks74"));
        g.add("szguns:akm", gun("tacz:762x39", 30, "AUTO", 3, 30, 4, "акм", "akm"));
        g.add("szguns:an94", gun("tacz:545x39", 30, "BURST", 5, 36, 2, "абакан", "ан-94", "an94"));
        g.add("szguns:groza", gun("szguns:9x39", 20, "AUTO", 5, 28, 4, "гроза", "оц-14", "groza"));
        g.add("szguns:vss", gun("szguns:9x39", 20, "SEMI", 5, 48, 1, "винторез", "всс", "vss"));
        g.add("szguns:svd", gun("tacz:762x54", 10, "SEMI", 5, 56, 1, "свд", "svd"));
        g.add("szguns:pkm", gun("tacz:762x54", 100, "AUTO", 6, 40, 8, "пкм", "рп-74", "pkm"));
        g.add("szguns:gauss", gun("szguns:gauss", 10, "SEMI", 9, 64, 1, "гаусс", "гаусс-винтовка", "gauss"));
        // стандартный пак TaCZ под сталкерскими названиями (как в оригинальной трилогии)
        g.add("tacz:m4a1", gun("tacz:556x45", 30, "AUTO", 4, 32, 4, "трс-301", "m4", "м4"));
        g.add("tacz:hk_mp5a5", gun("tacz:9mm", 30, "AUTO", 3, 20, 4, "гадюка", "гадюка-5", "mp5"));
        g.add("tacz:sks_tactical", gun("tacz:762x39", 10, "SEMI", 3, 40, 1, "скс", "sks"));
        g.add("tacz:rpk", gun("tacz:762x39", 40, "AUTO", 5, 36, 6, "рпк", "rpk"));
        g.add("tacz:glock_17", gun("tacz:9mm", 17, "SEMI", 2, 18, 1, "фора", "фора-12", "glock"));
        g.add("tacz:m1911", gun("tacz:45acp", 7, "SEMI", 2, 16, 1, "кора", "кора-919", "colt"));
        g.add("tacz:deagle", gun("tacz:50ae", 7, "SEMI", 4, 20, 1, "чёрный-ястреб", "ястреб", "deagle"));
        g.add("tacz:m870", gun("tacz:12g", 6, "SEMI", 3, 12, 1, "чейзер", "чейзер-13", "m870"));
        g.add("tacz:spas_12", gun("tacz:12g", 8, "SEMI", 4, 12, 1, "спас", "спса-14", "spas"));
        g.add("tacz:g36k", gun("tacz:556x45", 30, "AUTO", 4, 32, 4, "гп-37", "g36"));
        g.add("tacz:aug", gun("tacz:556x45", 30, "AUTO", 5, 36, 4, "ил-86", "aug"));
        g.add("tacz:rpg7", gun("tacz:rpg_rocket", 1, "SEMI", 8, 48, 1, "рпг", "рпг-7у", "rpg"));
        root.add("guns", g);
        // раскладки как в оригинале: у новичков обрезы и «ксюхи», у Долга армейское, у Свободы западное,
        // у Монолита лучшее, у армии штатное; лидеры и заместители — по строке *leader
        JsonObject lo = new JsonObject();
        lo.add("Одиночки", pairs("szguns:pm", 2, "szguns:fort12", 1, "szguns:toz34", 3, "szguns:toz_obrez", 2, "szguns:aks74u", 4,
                "szguns:ak74", 2, "szguns:akm", 1, "tacz:hk_mp5a5", 2, "tacz:m870", 1));
        lo.add("Одиночки*leader", pairs("szguns:ak74", 2, "szguns:svd", 1, "szguns:vss", 1, "szguns:groza", 1));
        lo.add("Долг", pairs("szguns:ak74", 6, "szguns:aks74", 2, "szguns:pkm", 2, "szguns:svd", 1, "tacz:spas_12", 1, "szguns:groza", 1));
        lo.add("Долг*leader", pairs("szguns:groza", 2, "szguns:pkm", 1, "szguns:an94", 1));
        lo.add("Свобода", pairs("tacz:m4a1", 3, "tacz:g36k", 3, "tacz:aug", 2, "tacz:hk_mp5a5", 2, "szguns:ak74", 2, "szguns:vss", 1, "szguns:svd", 1));
        lo.add("Свобода*leader", pairs("tacz:g36k", 1, "szguns:vss", 1, "tacz:aug", 1));
        lo.add("Бандиты", pairs("szguns:toz_obrez", 4, "szguns:pm", 3, "szguns:aks74u", 3, "szguns:toz34", 2, "szguns:akm", 2, "tacz:m1911", 1));
        lo.add("Бандиты*leader", pairs("szguns:akm", 2, "szguns:ak74", 1, "tacz:deagle", 1));
        lo.add("Военные", pairs("szguns:ak74", 6, "szguns:aks74u", 2, "szguns:aks74", 2, "szguns:pkm", 1, "szguns:svd", 1, "szguns:fort12", 1));
        lo.add("Военные*leader", pairs("szguns:an94", 2, "szguns:pkm", 1, "szguns:groza", 1));
        lo.add("Монолит", pairs("szguns:ak74", 4, "szguns:akm", 2, "szguns:pkm", 2, "szguns:svd", 2, "szguns:groza", 2, "tacz:rpk", 1, "szguns:gauss", 1));
        lo.add("Монолит*leader", pairs("szguns:gauss", 2, "szguns:groza", 1, "szguns:vss", 1));
        lo.add("Учёные", pairs("szguns:pm", 2, "szguns:fort12", 2, "szguns:aps", 1, "tacz:glock_17", 1));
        root.add("loadouts", lo);
        JsonObject rpu = new JsonObject();
        rpu.addProperty("tacz:9mm", 24); rpu.addProperty("tacz:12g", 10); rpu.addProperty("tacz:545x39", 30);
        rpu.addProperty("tacz:762x39", 30); rpu.addProperty("tacz:556x45", 30); rpu.addProperty("tacz:762x54", 20);
        rpu.addProperty("tacz:45acp", 21); rpu.addProperty("tacz:50ae", 14); rpu.addProperty("tacz:rpg_rocket", 1);
        rpu.addProperty("szguns:9x18", 24); rpu.addProperty("szguns:9x39", 20); rpu.addProperty("szguns:gauss", 1);
        root.add("rounds_per_unit", rpu);
        JsonObject decor = new JsonObject();
        decor.addProperty("_example", "\"stalkerzone:rusty_barrel\": [\"other_mod:barrel\", \"other_mod:barrel_rusty\"]");
        for (String k : new String[] {"stalkerzone:rusty_barrel", "stalkerzone:radioactive_barrel", "stalkerzone:wooden_crate",
                "stalkerzone:military_crate", "stalkerzone:medical_crate", "stalkerzone:tires", "stalkerzone:prop_gas_cylinder",
                "stalkerzone:prop_sandbags"}) decor.add(k, new JsonArray());
        root.add("decor", decor);
        root.addProperty("decor_chance", 50);
        JsonObject mut = new JsonObject();
        mut.addProperty("_example", "\"bloodsucker\": [\"other_mod:bloodsucker\"]");
        for (ru.stalkerzone.mutant.MutantType t : ru.stalkerzone.mutant.MutantType.ALL) { String k = t.id;
            mut.add(k, new JsonArray()); }
        root.add("mutants", mut);
        return root;
    }

    /** Для тестов и команды /zone compat. */
    public static String summary() {
        load();
        return "TaCZ " + (Tacz.loaded() ? "есть" : "нет") + ", оружия " + GUNS.size() + ", раскладок " + LOADOUTS.size()
                + ", замен декора " + DECOR.size() + ", замен мутантов " + MUTANTS.size();
    }

    static void reset() { loaded = false; }

    static void write(Path p) throws IOException {
        try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) { GSON.toJson(defaults(), w); }
    }
}
