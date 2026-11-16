import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import ru.stalkerzone.container.ContainerKind;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.Simulation;

/**
 * Э1-17: контейнеры мода — 16 видов (8 типов × A/B) с вместимостью и весом по превью-листу, ресурсы на месте,
 * учёт добычи в Ledger сохраняется (сама добыча — ContainerLootTest).
 */
public class ContainersTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static final String RES = "src/main/resources/";
    static boolean has(String p) { return Files.exists(Path.of(RES + p)); }
    static String read(String p) throws Exception { return Files.readString(Path.of(RES + p)).replace("\r\n", "\n"); }

    /** Вместимость и вес по типу (1…8) — превью-лист, решение пользователя 16.11. */
    static final int[][] SHEET = {{18, 15}, {18, 20}, {18, 15}, {27, 30}, {6, 3}, {27, 40}, {18, 35}, {9, 120}};

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
