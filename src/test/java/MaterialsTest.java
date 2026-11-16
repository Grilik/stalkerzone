import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.Features;
import ru.stalkerzone.zonemap.build.Materials;
import ru.stalkerzone.zonemap.build.Structures;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Э2-13: словарь «материал → блок» (решение а6) и сборщики укреплений на нём. */
public class MaterialsTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    static final File BS = new File("src/main/resources/assets/stalkerzone/blockstates");
    /** Ванильные id, которых нет в 300 самых частых блоках TheZone, но которые точно есть в 1.20.1. */
    static final Set<String> VANILLA_EXTRA = Set.of("smooth_stone", "quartz_pillar", "red_wool", "yellow_concrete", "red_concrete", "white_concrete");

    static Set<String> vanilla() throws Exception {
        // палитра TheZone после конверсии в Java 1.20.1 — значит, id существуют
        String j = Files.readString(new File("docs/thezone/materials-sample.json").toPath(), StandardCharsets.UTF_8);
        Set<String> ids = new HashSet<>(VANILLA_EXTRA);
        String[] q = j.split("\"");
        for (int i = 1; i < q.length; i += 2) if (q[i].matches("[a-z0-9_]+")) ids.add(q[i]);   // строки в кавычках: id блоков (и пара ключей)
        return ids;
    }

    static String base(String st) { return st.contains("[") ? st.substring(0, st.indexOf('[')) : st; }

    /** Блок существует: ванильный — в палитре, блок мода — есть blockstate и в нём есть каждое свойство состояния. */
    static String bad(String st, Set<String> van) throws Exception {
        String id = base(st);
        if (id.startsWith("minecraft:")) return van.contains(id.substring(10)) ? null : "нет ванильного " + id;
        if (!id.startsWith("stalkerzone:")) return "чужое пространство имён " + id;
        File f = new File(BS, id.substring(12) + ".json");
        if (!f.exists()) return "нет blockstate " + id;
        if (st.contains("[")) {
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            for (String kv : st.substring(st.indexOf('[') + 1, st.length() - 1).split(","))
                if (!json.contains(kv)) return "в " + id + " нет свойства " + kv;
        }
        return null;
    }

    static Map<String, Integer> build(String kind) {
        Map<String, Integer> n = new TreeMap<>();
        BlockSink s = new BlockSink() {
            public void set(int x, int y, int z, String st) { n.merge(st, 1, Integer::sum); }
            public int ground(int x, int z) { return 70; }
        };
        Structures.build(new Features.Placement(kind, 0, 0, 0, 0, 0, 12345), s, 3);
        return n;
    }

    static final Set<String> MOD_ONLY = new HashSet<>();
    static { for (Materials m : Materials.values()) if (m.modOnly) MOD_ONLY.add(m.thezone); }

    static List<String> zm(Map<String, Integer> blocks) {
        List<String> r = new ArrayList<>();
        for (String st : blocks.keySet()) if (st.startsWith("stalkerzone:zm_") && !MOD_ONLY.contains(st)) r.add(st);
        return r;
    }

    public static void main(String[] a) throws Exception {
        Set<String> van = vanilla();
        ck(van.size() > 150, "палитра TheZone прочитана (" + van.size() + ")");
        List<String> errs = new ArrayList<>();
        for (Materials m : Materials.values()) for (Materials.Mode md : Materials.Mode.values()) {
            String e = bad(m.id(md), van);
            if (e != null) errs.add(m + "/" + md + ": " + e);
        }
        ck(errs.isEmpty(), "каждый материал → существующий блок в обоих режимах " + errs);
        List<String> zmInTz = new ArrayList<>();
        for (Materials m : Materials.values()) if (!m.modOnly && !m.thezone.startsWith("minecraft:")) zmInTz.add(m.name());
        ck(zmInTz.isEmpty(), "режим thezone: только ванильные блоки, кроме помеченных mod " + zmInTz);
        int modOnly = 0;
        for (Materials m : Materials.values()) if (m.modOnly) { modOnly++; ck(m.thezone.equals(m.mod) && m.mod.startsWith("stalkerzone:"), m + ": mod — блок мода в обоих режимах"); }
        ck(modOnly <= 5, "помеченных mod мало (" + modOnly + ")");
        ck(Materials.ROOF_SHEET_STAIRS.with("facing=south,half=top").equals("minecraft:nether_brick_stairs[facing=south,half=top]"), "with() заменяет свойства");
        ck(Materials.mode() == Materials.Mode.THEZONE, "по умолчанию режим thezone");

        // сборщики укреплений: КПП (шлагбаум, змейка, мешки, гнёзда, вышки), застава, военный лагерь, вышка
        for (String kind : new String[] {"kpp", "road_post", "military_camp", "watchtower", "rail_bridge"}) {
            Map<String, Integer> tz = build(kind);
            ck(zm(tz).isEmpty(), kind + ": в режиме thezone нет блоков zm_* " + zm(tz));
            Materials.Mode old = Materials.mode(Materials.Mode.MOD);
            Map<String, Integer> md = build(kind);
            Materials.mode(old);
            ck(!md.equals(tz), kind + ": режим mod меняет блоки");
        }
        Map<String, Integer> kpp = build("kpp");
        ck(kpp.getOrDefault(Materials.SANDBAG.thezone, 0) > 20, "КПП: мешки — " + Materials.SANDBAG.thezone + " (" + kpp.getOrDefault(Materials.SANDBAG.thezone, 0) + ")");
        ck(kpp.containsKey(Materials.WARN_STRIPES.thezone) && kpp.containsKey(Materials.BARRIER_RED.thezone), "КПП: полосы и шлагбаум из словаря");
        ck(!kpp.containsKey("minecraft:smooth_sandstone"), "КПП: старых мешков (smooth_sandstone) нет");
        Map<String, Integer> tower = build("watchtower");
        ck(kpp.containsKey(Materials.BARBED_WIRE.thezone) && kpp.containsKey(Materials.BARS.thezone), "КПП: колючая проволока из словаря");
        ck(tower.containsKey("minecraft:ladder[facing=west]") && tower.containsKey(Materials.POST_WOOD.thezone), "вышка: лестница и столбы");
        if (f > 0) { System.out.println("FAILED " + f); System.exit(1); }
        System.out.println("ALL OK");
    }
}
