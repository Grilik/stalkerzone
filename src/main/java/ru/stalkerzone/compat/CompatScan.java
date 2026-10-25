package ru.stalkerzone.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /zone compat scan и /zone compat apply (v1.13): поиск в установленных модах декора и мутантов, похожих на наши
 * (сталкерские декорации, мутанты Зоны и т. п.). Мод не копирует чужие модели: он только ставит чужие блоки
 * и существ в мир, если игрок сам установил эти моды. scan показывает найденное, apply записывает в пустые
 * списки config/stalkerzone/compat.json и перечитывает его.
 */
public final class CompatScan {
    private CompatScan() {}

    private static final Map<String, String[]> DECOR_KEYS = new LinkedHashMap<>();
    private static final Map<String, String[]> MUTANT_KEYS = new LinkedHashMap<>();

    static {
        DECOR_KEYS.put("stalkerzone:rusty_barrel", new String[] {"rusty_barrel", "barrel_rust", "oil_barrel", "drum", "bochka"});
        DECOR_KEYS.put("stalkerzone:radioactive_barrel", new String[] {"radioactive_barrel", "rad_barrel", "toxic_barrel", "waste_barrel", "nuclear_barrel"});
        DECOR_KEYS.put("stalkerzone:wooden_crate", new String[] {"wooden_crate", "wood_crate", "crate_wood", "yashik", "wooden_box"});
        DECOR_KEYS.put("stalkerzone:military_crate", new String[] {"military_crate", "army_crate", "ammo_box", "ammo_crate", "military_box", "supply_box"});
        DECOR_KEYS.put("stalkerzone:medical_crate", new String[] {"medical", "medkit_box", "first_aid", "med_box"});
        DECOR_KEYS.put("stalkerzone:tires", new String[] {"tire", "tyre", "shina"});
        DECOR_KEYS.put("stalkerzone:prop_gas_cylinder", new String[] {"gas_cylinder", "gas_balloon", "ballon", "propane", "oxygen_tank"});
        DECOR_KEYS.put("stalkerzone:prop_sandbags", new String[] {"sandbag", "sand_bag", "meshki"});
        MUTANT_KEYS.put("blind_dog", new String[] {"blind_dog", "blinddog", "slepoy", "mutant_dog"});
        MUTANT_KEYS.put("flesh", new String[] {"flesh", "plot"});
        MUTANT_KEYS.put("boar", new String[] {"boar", "kaban"});
        MUTANT_KEYS.put("snork", new String[] {"snork"});
        MUTANT_KEYS.put("bloodsucker", new String[] {"bloodsucker", "krovosos", "blood_sucker"});
        MUTANT_KEYS.put("chimera", new String[] {"chimera", "himera"});
        MUTANT_KEYS.put("controller", new String[] {"controller", "kontroler"});
    }

    private static boolean foreign(ResourceLocation k) {
        String ns = k.getNamespace();
        return !ns.equals("minecraft") && !ns.equals("stalkerzone") && !ns.equals("tacz") && !ns.equals("forge");
    }

    private static boolean matches(String path, String[] keys) {
        String p = path.toLowerCase(Locale.ROOT);
        for (String k : keys) if (p.contains(k)) return true;
        return false;
    }

    /** Найденное: наш ключ -> подходящие id из других модов. */
    public static Map<String, List<String>> decor() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (ResourceLocation k : ForgeRegistries.BLOCKS.getKeys()) {
            if (!foreign(k)) continue;
            for (Map.Entry<String, String[]> e : DECOR_KEYS.entrySet())
                if (matches(k.getPath(), e.getValue())) out.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(k.toString());
        }
        return out;
    }

    public static Map<String, List<String>> mutants() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (ResourceLocation k : ForgeRegistries.ENTITY_TYPES.getKeys()) {
            if (!foreign(k)) continue;
            for (Map.Entry<String, String[]> e : MUTANT_KEYS.entrySet())
                if (matches(k.getPath(), e.getValue())) out.computeIfAbsent(e.getKey(), x -> new ArrayList<>()).add(k.toString());
        }
        return out;
    }

    public static String report() {
        Map<String, List<String>> d = decor(), m = mutants();
        StringBuilder sb = new StringBuilder("Совместимость: найдено в других модах\n");
        if (d.isEmpty() && m.isEmpty()) return sb.append("Ничего похожего: сталкерские моды декора и мутантов не установлены.").toString();
        for (Map.Entry<String, List<String>> e : d.entrySet()) sb.append("декор ").append(e.getKey()).append(" ← ").append(e.getValue()).append('\n');
        for (Map.Entry<String, List<String>> e : m.entrySet()) sb.append("мутант ").append(e.getKey()).append(" ← ").append(e.getValue()).append('\n');
        return sb.append("Записать в compat.json: /zone compat apply").toString();
    }

    /** Дописать найденное в пустые списки compat.json и перечитать его. Возвращает, сколько списков заполнено. */
    public static int apply() {
        Path p = FMLPaths.CONFIGDIR.get().resolve("stalkerzone").resolve("compat.json");
        try {
            CompatConfig.load();
            JsonObject root;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) { root = JsonParser.parseReader(r).getAsJsonObject(); }
            int n = fill(root, "decor", decor()) + fill(root, "mutants", mutants());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, w);
            }
            CompatConfig.reset();
            CompatConfig.load();
            return n;
        } catch (Exception e) {
            return -1;
        }
    }

    private static int fill(JsonObject root, String key, Map<String, List<String>> found) {
        JsonObject o = root.has(key) ? root.getAsJsonObject(key) : new JsonObject();
        int n = 0;
        for (Map.Entry<String, List<String>> e : found.entrySet()) {
            JsonElement cur = o.get(e.getKey());
            if (cur != null && cur.isJsonArray() && cur.getAsJsonArray().size() > 0) continue; // свои настройки не трогаем
            JsonArray a = new JsonArray();
            for (String s : e.getValue()) a.add(s);
            o.add(e.getKey(), a);
            n++;
        }
        root.add(key, o);
        return n;
    }
}
