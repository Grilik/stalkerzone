import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import ru.stalkerzone.container.ContainerLoot;
import ru.stalkerzone.zonemap.atlas.Json;

/**
 * Э1-17: раскладка бюджета локаций (оружие, артефакты, рубли — один раз, сид 17) и прогон случайной добычи (среднее
 * по RUNS прогонам) по всем контейнерам мира (сводка.json из tools/thezone/containers_loot_edit.py).
 *   java -cp out LootSummary <сводка.json> <RUNS> <prices.json> <раскладка.json>
 * раскладка.json — [{x,y,z, items:[{id|gun|ammo, mode, count}]}] для правки мира.
 */
public class LootSummary {
    static final String[] COLS = {"контейнеров", "пустых*", "оружие", "артефакты", "рубли", "патроны", "снаряжение", "медицина", "консервы", "стоимость, ₽"};

    public static void main(String[] a) throws Exception {
        ContainerLoot l = new ContainerLoot(Files.readString(Path.of("src/main/resources/data/stalkerzone/container_loot/loot.json")));
        List<Object> rows = Json.list(Json.parse(Files.readString(Path.of(a[0]))));
        int runs = Integer.parseInt(a[1]);
        Map<String, Object> pr = Json.obj(Json.obj(Json.parse(Files.readString(Path.of(a[2])))).get("prices"));
        List<ContainerLoot.Box> boxes = new ArrayList<>();
        for (Object o : rows) {
            Map<String, Object> m = Json.obj(o);
            boxes.add(new ContainerLoot.Box(Json.str(m.get("kind")), Json.str(m.get("place")), Json.str(m.get("loc"))));
        }
        Map<Integer, List<ContainerLoot.Drop>> plan = l.plan(boxes, new Random(17));
        StringBuilder js = new StringBuilder("[");
        Map<String, double[]> by = new TreeMap<>();
        Random r = new Random(17);
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> m = Json.obj(rows.get(i));
            String loc = l.locOf(Json.str(m.get("loc")));
            double[] s = by.computeIfAbsent(loc, k -> new double[COLS.length]);
            double[] tot = by.computeIfAbsent("ВСЕГО", k -> new double[COLS.length]);
            int z = (int) Json.num(m.get("z"));
            double[] add = new double[COLS.length];
            add[0] = 1;
            List<ContainerLoot.Drop> st = plan.getOrDefault(i, List.of());
            if (!st.isEmpty()) {
                js.append(js.length() > 1 ? ",\n" : "").append(String.format("{\"x\":%d,\"y\":%d,\"z\":%d,\"items\":[", (int) Json.num(m.get("x")), (int) Json.num(m.get("y")), z));
                for (int k = 0; k < st.size(); k++) {
                    ContainerLoot.Drop d = st.get(k);
                    js.append(k > 0 ? "," : "").append(d.gun() != null ? String.format("{\"gun\":\"%s\",\"mode\":\"%s\",\"count\":1}", d.gun(), d.mode())
                            : String.format("{\"id\":\"%s\",\"count\":%d}", d.id(), d.count()));
                    count(d, add, pr, 1);
                }
                js.append("]}");
            }
            for (int run = 0; run < runs; run++) {
                List<ContainerLoot.Drop> d = l.roll(Json.str(m.get("kind")), Json.str(m.get("place")), z, Json.str(m.get("loc")), r);
                if (d.isEmpty() && st.isEmpty()) add[1] += 1.0 / runs;
                for (ContainerLoot.Drop x : d) count(x, add, pr, 1.0 / runs);
            }
            for (double[] t : new double[][]{s, tot}) for (int k = 0; k < COLS.length; k++) t[k] += add[k];
        }
        Files.writeString(Path.of(a[3]), js.append("]").toString());
        StringBuilder out = new StringBuilder("| Локация | " + String.join(" | ", COLS) + " |\n|---" + "|---:".repeat(COLS.length) + "|\n");
        for (Map.Entry<String, double[]> e : by.entrySet()) {
            double[] s = e.getValue();
            out.append("| ").append(e.getKey()).append(" | ").append((int) s[0]).append(" | ").append(Math.round(100 * s[1] / s[0])).append(" %");
            for (int k = 2; k < COLS.length; k++)
                out.append(" | ").append(k <= 5 || k == 9 ? String.valueOf(Math.round(s[k])) : String.format("%.0f", s[k]));
            out.append(" |\n");
        }
        Files.writeString(Path.of(a[3] + ".md"), out.toString());
    }

    static void count(ContainerLoot.Drop x, double[] add, Map<String, Object> pr, double w) {
        String id = x.id() == null ? "" : x.id();
        String key = x.gun() != null ? "gun:" + x.gun() : x.ammo() != null ? "ammo:" + x.ammo() : id;
        Map<String, Object> p = Json.obj(pr.get(key));
        double v = p == null ? 0 : Json.num(p.get("price"));
        add[9] += w * v * x.count();
        if (x.gun() != null) add[2] += w;
        else if (id.contains("artifact_")) add[3] += w;
        else if (id.startsWith("stalkerzone:ruble")) add[4] += w * x.count() * (id.endsWith("_100") ? 100 : id.endsWith("_1000") ? 1000 : 1);
        else if (x.ammo() != null || id.contains("good_ammo")) add[5] += w * x.count();
        else if (id.contains("suit_") || id.contains("backpack") || id.contains("detector") || id.contains("good_gear")) add[6] += w;
        else if (id.contains("good_meds")) add[7] += w * x.count();
        else if (id.contains("good_food")) add[8] += w * x.count();
    }
}
