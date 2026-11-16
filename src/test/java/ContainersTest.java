import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.stalkerzone.container.ContainerKind;
import ru.stalkerzone.container.ContainerKind.Family;
import ru.stalkerzone.container.ContainerKind.Place;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.Simulation;

/**
 * Э1-17: контейнеры мода — 16 видов (8 типов × A/B) с вместимостью и весом по превью-листу, ресурсы на месте,
 * таблицы добычи по семейству и месту (еда только привозная — товар «Еда»; рубли редко), учёт добычи в Ledger сохраняется.
 */
public class ContainersTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static final String RES = "src/main/resources/";
    static boolean has(String p) { return Files.exists(Path.of(RES + p)); }
    static String read(String p) throws Exception { return Files.readString(Path.of(RES + p)).replace("\r\n", "\n"); }

    /** Вместимость и вес по типу (1…8) — превью-лист, решение пользователя 16.11. */
    static final int[][] SHEET = {{18, 15}, {18, 20}, {18, 15}, {27, 30}, {6, 3}, {27, 40}, {18, 35}, {9, 120}};
    static final Set<String> JUNK = Set.of("iron_nugget", "string", "paper", "glass_bottle", "bone", "stick", "leather", "coal", "flint",
            "bowl", "rotten_flesh", "chain", "copper_ingot", "iron_ingot", "redstone", "book");

    public static void main(String[] a) throws Exception {
        ContainerKind[] ks = ContainerKind.values();
        ck(ks.length == 16, "видов контейнеров 16 (" + ks.length + ")");
        Set<String> ids = new HashSet<>(), codes = new HashSet<>();
        for (ContainerKind k : ks) { ids.add(k.id); codes.add(k.code); }
        ck(ids.size() == 16 && codes.size() == 16, "id и коды уникальны");
        for (int t = 1; t <= 8; t++)
            for (String v : new String[]{"A", "B"}) {
                ContainerKind k = null;
                for (ContainerKind x : ks) if (x.code.equals(t + v)) k = x;
                ck(k != null && k.slots == SHEET[t - 1][0] && k.weight == SHEET[t - 1][1],
                        t + v + ": " + SHEET[t - 1][0] + " ячеек, " + SHEET[t - 1][1] + " кг");
            }
        for (ContainerKind k : ks) {
            ck(k.slots <= k.rows() * 9 && k.rows() <= 3 && k.w <= 16 && k.d <= 16 && k.h <= 32, k.id + ": окно и размер в пределах");
            String a1 = "assets/stalkerzone/";
            boolean files = has(a1 + "blockstates/" + k.id + ".json") && has(a1 + "models/block/container/" + k.id + ".json")
                    && has(a1 + "models/item/" + k.id + ".json") && has("data/stalkerzone/loot_tables/blocks/" + k.id + ".json");
            for (String fc : new String[]{"front", "side", "top"}) files &= has(a1 + "textures/block/container/" + k.id + "_" + fc + ".png");
            ck(files, k.id + ": состояния, модели, текстуры, выпадение блока");
            ck(read(a1 + "lang/ru_ru.json").contains("\"block.stalkerzone." + k.id + "\": \"" + k.ru + "\"")
                    && read(a1 + "lang/en_us.json").contains("\"block.stalkerzone." + k.id + "\""), k.id + ": название ru/en");
            ck(!k.tall() || read(a1 + "blockstates/" + k.id + ".json").contains("half=upper"), k.id + ": у двухблочного есть верхняя половина");
        }
        ck(ContainerKind.LOCKER.tall() && !ContainerKind.WOOD_CRATE.tall(), "шкафчик раздевалки — 2 блока, ящик — 1");

        // таблицы добычи по семейству и месту
        Pattern item = Pattern.compile("\"name\": \"([^\"]+)\",\\s*\"weight\": (\\d+)");
        Pattern empty = Pattern.compile("\"minecraft:empty\",\\s*\"weight\": (\\d+)");
        Set<String> tables = new HashSet<>();
        for (Family fam : Family.values()) for (Place p : Place.values()) tables.add(ContainerKind.lootTable(fam, p));
        ck(tables.size() == 14, "таблиц добычи 14: 3 семейства × 4 места + цинк + сейф (" + tables.size() + ")");
        ck(ContainerKind.lootTable(Family.WOOD, Place.BASE).equals("stalkerzone:containers/wood_base")
                && ContainerKind.lootTable(Family.ZINC, Place.HOME).equals("stalkerzone:containers/zinc"), "имя таблицы: containers/<семейство>[_<место>]");
        for (String t : tables) {
            String p = "data/stalkerzone/loot_tables/containers/" + t.substring(t.indexOf('/') + 1) + ".json";
            if (!has(p)) { ck(false, t + ": файл " + p); continue; }
            String s = read(p);
            long all = 0, rub = 0, food = 0, ammo = 0;
            boolean junkOk = true;
            Matcher m = item.matcher(s);
            while (m.find()) {
                String n = m.group(1); long w = Long.parseLong(m.group(2)); all += w;
                if (n.startsWith("minecraft:") && !JUNK.contains(n.substring(10))) { junkOk = false; System.out.println("  чужой предмет " + n); }
                if (n.startsWith("stalkerzone:ruble")) rub += w;
                if (n.equals("stalkerzone:good_food")) food += w;
                if (n.startsWith("stalkerzone:good_ammo")) ammo += w;
            }
            Matcher e = empty.matcher(s);
            while (e.find()) all += Long.parseLong(e.group(1));
            ck(all > 0 && junkOk, t + ": только хлам Minecraft и товары мода (ванильной еды нет — еда привозная)");
            if (!t.endsWith("safe")) ck(rub * 100 <= all * 6, t + ": рубли редко (" + rub + " из " + all + ")");
            if (t.endsWith("zinc")) ck(ammo * 10 >= all * 9 && food == 0, t + ": в цинке — патроны");
        }

        // учёт добычи в Ledger переживает сохранение
        Simulation s = Scenarios.create("kordon", 5);
        s.ledger.worldLoot[Good.AMMO.ordinal()] = 37;
        s.ledger.worldRubles = 1234;
        Simulation l = Simulation.load(s.save());
        ck(l.ledger.worldLoot[Good.AMMO.ordinal()] == 37 && l.ledger.worldRubles == 1234, "Ledger.worldLoot/worldRubles сохраняются (v23)");
        ck(s.audit().isEmpty(), "приход из мира не ломает закон сохранения симуляции");
        System.exit(f > 0 ? 1 : 0);
    }
}
