import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ru.stalkerzone.container.ContainerKind;
import ru.stalkerzone.container.ContainerLoot;

/**
 * Э1-17 (решение пользователя 16.11): добыча контейнеров по data-файлу loot.json — уровни хлам ~50, обычное ~30,
 * полезное ~15, редкое ~4, очень редкое ~1 %; 1–4 предмета; пустых 30–60 % по месту; север богаче; цинк — патроны,
 * сейф — деньги и ценное; еда — только консервы; у каждого вида и места есть правила.
 */
public class ContainerLootTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) throws Exception {
        String json = Files.readString(Path.of("src/main/resources/data/stalkerzone/container_loot/loot.json"));
        ContainerLoot l = new ContainerLoot(json);
        for (ContainerKind k : ContainerKind.values()) ck(l.knowsKind(k.id), k.id + ": тип описан в loot.json");
        for (String p : ContainerLoot.PLACES) ck(l.knowsPlace(p), "место " + p + " описано");

        // случайная добыча — хлам, обычное, полезное в отношении 50 : 30 : 15 (редкое и очень редкое — бюджетом локации)
        Map<String, Double> tw = l.tierWeights("wood_crate_old", "home", 4400);
        double sum = tw.values().stream().mapToDouble(Double::doubleValue).sum();
        double[] want = {50 / 0.95, 30 / 0.95, 15 / 0.95};
        for (int i = 0; i < 3; i++) {
            double pc = 100 * tw.get(ContainerLoot.TIERS[i]) / sum;
            ck(Math.abs(pc - want[i]) < 0.6, "уровень " + ContainerLoot.TIERS[i] + " на юге ≈ " + String.format("%.1f", want[i]) + " % (" + String.format("%.1f", pc) + ")");
        }
        ck(tw.get("rare") == 0 && tw.get("very_rare") == 0, "редкое и очень редкое — не случайно, а бюджетом локации");
        Map<String, Double> north = l.tierWeights("wood_crate_old", "home", -4400);
        double ns = north.values().stream().mapToDouble(Double::doubleValue).sum();
        ck(north.get("junk") / ns < tw.get("junk") / sum && north.get("useful") / ns > tw.get("useful") / sum,
                "север: хлама меньше, полезного больше");

        // пустые 30–60 % по месту; 1–4 предмета
        for (String p : ContainerLoot.PLACES) {
            double e = l.emptyChance("wood_crate", p);
            ck(e >= 0.3 && e <= 0.6, p + ": пустых " + Math.round(e * 100) + " %");
        }
        ck(l.emptyChance("wood_crate", "storage") < l.emptyChance("wood_crate", "field")
                && l.emptyChance("wood_crate", "base") < l.emptyChance("wood_crate", "attic"), "склад и база полнее поля и чердака");

        Random r = new Random(1);
        boolean counts = true, food = true, zinc = true, safe = true;
        Map<String, Integer> army = new HashMap<>(), crate = new HashMap<>();
        int full = 0;
        for (int i = 0; i < 20000; i++) {
            List<ContainerLoot.Drop> d = l.roll("wood_crate", "storage", 3500, r);
            if (!d.isEmpty()) { full++; counts &= d.size() >= 1 && d.size() <= 4; }
            for (ContainerLoot.Drop x : d) {
                crate.merge(x.tier(), 1, Integer::sum);
                if (x.id() != null && (x.id().startsWith("minecraft:") && x.ru().contains("еда"))) food = false;
            }
            for (ContainerLoot.Drop x : l.roll("ammo_zinc", "base", 3500, r)) zinc &= x.ammo() != null || x.id().contains("ammo");
            for (ContainerLoot.Drop x : l.roll("safe", "home", 3500, r)) safe &= x.ammo() == null && (x.id() == null || !x.id().contains("ammo") && !x.id().contains("suit_") && !x.id().contains("food"));
            for (ContainerLoot.Drop x : l.roll("army_crate", "base", 3500, r)) army.merge(x.tier(), 1, Integer::sum);
        }
        ck(counts && full > 0, "в непустом ящике 1–4 предмета");
        ck(zinc, "цинк — только патроны");
        ck(safe, "сейф — без патронов, одежды и еды (документы, медицина, ценное; деньги и артефакты — бюджетом локации)");
        double armyUseful = army.getOrDefault("useful", 0) / (double) army.values().stream().mapToInt(Integer::intValue).sum();
        double crateUseful = crate.getOrDefault("useful", 0) / (double) crate.values().stream().mapToInt(Integer::intValue).sum();
        ck(armyUseful > crateUseful, "армейский ящик на базе — больше патронов и снаряжения, чем простой ящик");
        ck(food, "ванильной еды нет");
        for (String id : l.itemIds())
            ck(id.startsWith("stalkerzone:") || id.startsWith("minecraft:"), "предмет " + id + " — мод или Minecraft");
        ck(l.itemIds().stream().filter(i -> json.contains("\"id\": \"" + i + "\"") && i.contains("food")).allMatch(i -> i.equals("stalkerzone:good_food")),
                "еда — только консервы (good_food)");
        // бюджет локации: раскладка при правке мира
        java.util.List<ContainerLoot.Box> boxes = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) boxes.add(new ContainerLoot.Box(i % 2 == 0 ? "wood_crate" : "file_cabinet", "home", "Кордон"));
        for (int i = 0; i < 200; i++) boxes.add(new ContainerLoot.Box(i < 4 ? "safe" : i < 10 ? "ammo_zinc" : "metal_box", i % 3 == 0 ? "attic" : "storage", "Припять, запад"));
        Map<Integer, List<ContainerLoot.Drop>> plan = l.plan(boxes, new Random(5));
        int[] guns = new int[2], arts = new int[2], rub = new int[2];
        boolean artInSafe = true, noZincGun = true, perBox = true;
        for (Map.Entry<Integer, List<ContainerLoot.Drop>> e : plan.entrySet()) {
            ContainerLoot.Box b = boxes.get(e.getKey());
            int k = b.loc().equals("Кордон") ? 0 : 1, valuables = 0;
            for (ContainerLoot.Drop d : e.getValue()) {
                if (d.gun() != null) { guns[k]++; valuables++; noZincGun &= !b.kind().startsWith("ammo_zinc"); }
                else if (d.id().contains("artifact_")) { arts[k]++; valuables++; artInSafe &= b.kind().startsWith("safe"); }
                else rub[k] += d.count() * (d.id().endsWith("_100") ? 100 : 1);
            }
            perBox &= valuables <= 2;
        }
        ck(guns[0] >= 2 && guns[0] <= 3 && arts[0] == 0 && rub[0] >= 3200 && rub[0] <= 4800,
                "Кордон: 2–3 ствола, 0 артефактов, 3 200–4 800 ₽ (" + guns[0] + ", " + arts[0] + ", " + rub[0] + ")");
        ck(guns[1] >= 6 && guns[1] <= 8 && arts[1] >= 2 && arts[1] <= 3 && rub[1] >= 28000 && rub[1] <= 36000,
                "Припять (основная): 6–8 стволов, 2–3 артефакта, 28–36 тыс. ₽ (" + guns[1] + ", " + arts[1] + ", " + rub[1] + ")");
        ck(artInSafe && noZincGun && perBox, "артефакты только в сейфах, оружия нет в цинках, ценного ≤ 2 в ящике");
        System.exit(f > 0 ? 1 : 0);
    }
}
