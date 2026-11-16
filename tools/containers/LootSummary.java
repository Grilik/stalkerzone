import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import ru.stalkerzone.container.ContainerLoot;
import ru.stalkerzone.zonemap.atlas.Json;

/**
 * Э1-17: прогон генератора добычи по всем контейнерам мира (сводка.json из tools/thezone/containers_loot_edit.py).
 * Среднее по RUNS прогонам: оружие, патроны (штук), снаряжение, артефакты, рубли, медицина, консервы — по локациям.
 *   javac -d out <исходники тестов> tools/containers/LootSummary.java && java -cp out LootSummary <сводка.json> [RUNS]
 */
public class LootSummary {
    static final String[] COLS = {"контейнеров", "пустых", "оружие", "патроны", "снаряжение", "артефакты", "рубли", "медицина", "консервы"};

    public static void main(String[] a) throws Exception {
        ContainerLoot l = new ContainerLoot(Files.readString(Path.of("src/main/resources/data/stalkerzone/container_loot/loot.json")));
        List<Object> rows = Json.list(Json.parse(Files.readString(Path.of(a[0]))));
        int runs = a.length > 1 ? Integer.parseInt(a[1]) : 20;
        Map<String, double[]> by = new TreeMap<>();
        Random r = new Random(17);
        for (Object o : rows) {
            Map<String, Object> m = Json.obj(o);
            String loc = Json.str(m.get("loc")) + ("attic".equals(Json.str(m.get("place"))) ? " (чердаки)" : "");
            double[] s = by.computeIfAbsent(loc, k -> new double[COLS.length]);
            double[] tot = by.computeIfAbsent("ВСЕГО", k -> new double[COLS.length]);
            int z = (int) Json.num(m.get("z"));
            for (double[] t : new double[][]{s, tot}) t[0] += 1;
            for (int run = 0; run < runs; run++) {
                List<ContainerLoot.Drop> d = l.roll(Json.str(m.get("kind")), Json.str(m.get("place")), z, r);
                double[] add = new double[COLS.length];
                if (d.isEmpty()) add[1] = 1;
                for (ContainerLoot.Drop x : d) {
                    String id = x.id() == null ? "" : x.id();
                    if (x.gun() != null) add[2]++;
                    else if (x.ammo() != null || id.contains("good_ammo")) add[3] += x.count();
                    else if (id.contains("suit_") || id.contains("backpack") || id.contains("detector") || id.contains("good_gear")) add[4]++;
                    else if (id.contains("artifact_")) add[5]++;
                    else if (id.equals("stalkerzone:ruble")) add[6] += x.count();
                    else if (id.equals("stalkerzone:ruble_100")) add[6] += 100.0 * x.count();
                    else if (id.equals("stalkerzone:ruble_1000")) add[6] += 1000.0 * x.count();
                    else if (id.contains("good_meds")) add[7] += x.count();
                    else if (id.contains("good_food")) add[8] += x.count();
                }
                for (double[] t : new double[][]{s, tot}) for (int i = 1; i < COLS.length; i++) t[i] += add[i] / runs;
            }
        }
        System.out.println("| Локация | " + String.join(" | ", COLS) + " |");
        System.out.println("|---" + "|---:".repeat(COLS.length) + "|");
        for (Map.Entry<String, double[]> e : by.entrySet()) {
            double[] s = e.getValue();
            StringBuilder b = new StringBuilder("| " + e.getKey() + " | " + (int) s[0] + " | " + Math.round(100 * s[1] / s[0]) + " %");
            for (int i = 2; i < COLS.length; i++) b.append(" | ").append(i == 6 || i == 3 ? String.valueOf(Math.round(s[i])) : String.format("%.1f", s[i]));
            System.out.println(b.append(" |"));
        }
    }
}
