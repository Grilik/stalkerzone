import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.sim.Territories;
import ru.stalkerzone.sim.Territory;
import ru.stalkerzone.sim.TerritoryKind;
import ru.stalkerzone.sim.TerritoryPotential;
import ru.stalkerzone.sim.TerritoryPotential.Features;
import ru.stalkerzone.sim.TerritoryTask;
import ru.stalkerzone.zonemap.atlas.Atlas;
import ru.stalkerzone.zonemap.atlas.AtlasValidator;
import ru.stalkerzone.zonemap.atlas.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Э1-16: потенциал территорий вместо мини-баз. Проверяет: потенциал по типам и особенностям,
 * запрет еды и воды, занятие и освобождение, локальную задачу по потенциалу, конечный запас с
 * восстановлением, сохранение состояния, отсутствие ресурсов и населения из воздуха и регрессий.
 */
public class TerritoryPotentialTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static final Path DIR = Path.of("src/main/resources/data/stalkerzone/atlas");
    static final String LOC = "locations/cordon.json";

    static String read(String rel) {
        try { return Files.readString(DIR.resolve(rel), StandardCharsets.UTF_8).replace("\r\n", "\n"); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static List<String> errorsWith(String from, String to) {
        String src = read(LOC);
        if (!src.contains(from)) throw new IllegalStateException("в " + LOC + " нет фрагмента: " + from);
        return AtlasValidator.validate(Atlas.load(rel -> rel.equals(LOC) ? src.replace(from, to) : read(rel)));
    }

    static boolean has(List<String> errs, String part) {
        for (String e : errs) if (e.contains(part)) return true;
        return false;
    }

    static Map<String, Double> pot(TerritoryKind k, Features ft) { return TerritoryPotential.derive(k, ft); }

    /** Снимок экономики и населения: территории не должны его менять. */
    static String snapshot(Simulation s) {
        StringBuilder sb = new StringBuilder("day=").append(s.day).append(';');
        for (Faction fa : s.factions) {
            sb.append('f').append(fa.id).append(':').append(fa.members).append(':').append(fa.stock.money);
            for (Good g : Good.ALL) sb.append(':').append(fa.stock.get(g));
            sb.append(';');
        }
        for (Node n : s.nodes) {
            sb.append('n').append(n.id).append(':').append(n.owner).append(':').append(n.supply);
            for (Good g : Good.ALL) sb.append(':').append(n.depot.get(g)).append('/').append(n.market.get(g));
            sb.append(';');
        }
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            sb.append(g.name()).append(':').append(s.ledger.created[i]).append(':').append(s.ledger.destroyed[i])
              .append(':').append(s.ledger.imported[i]).append(':').append(s.ledger.exported[i])
              .append(':').append(s.ledger.worldLoot[i]).append(';');
        }
        sb.append("money:").append(s.ledger.moneyMinted).append(':').append(s.ledger.moneyBurned)
          .append(':').append(s.ledger.worldRubles)
          .append(";mutants:").append(s.ledger.mutantsBorn).append(':').append(s.ledger.mutantsDied)
          .append(";people:").append(s.people.roster.size());
        return sb.toString();
    }

    public static void main(String[] args) {
        // --- 1. потенциал определяется типом и особенностями территории ---
        Map<String, Double> forest = pot(TerritoryKind.FOREST, new Features(0.8, 0, 0, 0, 0, 0));
        ck(forest.keySet().equals(Set.of("wood")) && forest.get("wood") > 0.8, "лес: дерево и стройматериалы (" + forest + ")");
        Map<String, Double> scrap = pot(TerritoryKind.SCRAP, new Features(0, 500, 0, 0, 0, 0));
        ck(scrap.get("parts") == 1.0, "свалка и техника: детали (" + scrap + ")");
        Map<String, Double> field = pot(TerritoryKind.ANOMALY_FIELD, new Features(0, 0, 0, 0, 0, 1));
        ck(field.get("artifacts") == 1.0, "аномальное поле: артефакты (" + field + ")");
        Map<String, Double> lair = pot(TerritoryKind.LAIR, new Features(0, 0, 0, 0, 1, 0));
        ck(lair.get("mutant") == 1.0, "логово: части и мясо мутантов (" + lair + ")");
        Map<String, Double> industrial = pot(TerritoryKind.INDUSTRIAL, new Features(0, 1000, 0, 0, 0, 0));
        ck(industrial.get("workshop") == 1.0 && industrial.get("storage") > 0, "заводской корпус: мастерская и склад (" + industrial + ")");
        Map<String, Double> cross = pot(TerritoryKind.CROSSROADS, new Features(0, 0, 0, 3, 0, 0));
        ck(cross.get("trade") == 1.0, "развилка дорог: торговля и пошлина (" + cross + ")");
        Map<String, Double> under = pot(TerritoryKind.UNDERGROUND, new Features(0, 0, 500, 0, 0, 0));
        ck(under.get("shelter") == 1.0, "подвал и бункер: укрытие (" + under + ")");
        Map<String, Double> ruins = pot(TerritoryKind.RUINS, new Features(0, 300, 0, 0, 0, 0));
        ck(ruins.get("parts") > 0.5, "руины: детали разборкой (" + ruins + ")");

        ck(pot(TerritoryKind.INDUSTRIAL, new Features(0, 200, 0, 0, 0, 0)).get("workshop") < industrial.get("workshop"),
                "больше существующих построек — больше мастерская");
        ck(pot(TerritoryKind.CROSSROADS, new Features(0, 0, 0, 1, 0, 0)).get("trade") < cross.get("trade"),
                "больше дорог — больше торговля");
        ck(pot(TerritoryKind.FOREST, new Features(0.2, 0, 0, 0, 0, 0)).get("wood") < forest.get("wood"),
                "больше леса — больше дерева");
        ck(pot(TerritoryKind.UNDERGROUND, new Features(0, 0, 100, 0, 0, 0)).get("shelter") < under.get("shelter"),
                "больше подземных объёмов — больше укрытие");
        ck(pot(TerritoryKind.FOREST, Features.EMPTY).get("wood") == 0.3, "лес без измеренного леса — только промысловая база");
        ck(TerritoryPotential.strategicValue(cross) > TerritoryPotential.strategicValue(forest),
                "стратегическая ценность развилки выше лесной");

        // --- 2. еды и воды территория не даёт ---
        boolean clean = true;
        for (TerritoryKind k : TerritoryKind.values())
            for (double fo : new double[]{0, 0.5, 1})
                for (int b : new int[]{0, 1000})
                    for (int u : new int[]{0, 500})
                        for (int r : new int[]{0, 3})
                            for (int l : new int[]{0, 1})
                                for (int p : new int[]{0, 1}) {
                                    Map<String, Double> x = pot(k, new Features(fo, b, u, r, l, p));
                                    for (Map.Entry<String, Double> e : x.entrySet()) {
                                        if (TerritoryPotential.FORBIDDEN.contains(e.getKey())) clean = false;
                                        if (!TerritoryPotential.KEYS.contains(e.getKey())) clean = false;
                                        if (e.getValue() <= 0 || e.getValue() > 1) clean = false;
                                    }
                                }
        ck(clean, "ни один тип и набор особенностей не даёт еду и воду, силы в 0..1");
        ck(TerritoryTask.byKey("food") == null && TerritoryTask.byKey("water") == null, "локальных задач «еда» и «вода» нет");
        ck(new HashSet<>(TerritoryPotential.KEYS).equals(AtlasValidator.POTENTIAL_KEYS), "ключи потенциала: ядро = валидатор атласа");
        ck(TerritoryPotential.KEYS.equals(List.of("wood", "parts", "artifacts", "mutant", "workshop", "storage", "trade", "shelter")),
                "порядок ключей потенциала зафиксирован: он входит в формат сохранения");
        HashSet<String> kinds = new HashSet<>();
        for (TerritoryKind k : TerritoryKind.values()) kinds.add(k.id);
        ck(kinds.equals(AtlasValidator.ZONE_TYPES), "типы территорий: ядро = валидатор атласа");

        // --- 3. зоны атласа: валидатор, потенциал = derive(type, features), набор узлов не изменился ---
        Atlas a = Atlas.loadDir(DIR);
        List<String> errs = AtlasValidator.validate(a);
        for (String e : errs) System.out.println("  ошибка: " + e);
        ck(errs.isEmpty(), "атлас с территориями проходит валидатор");
        ck(a.nodes().size() == 24, "зоны не узлы: узлов по-прежнему 24");
        List<Territory> defs = Territories.fromAtlas(a);
        ck(defs.size() == 8, "территорий Кордона: 8 (" + defs.size() + ")");
        int checked = 0;
        for (Map<String, Object> o : a.objects()) {
            if (!"zone".equals(Json.str(o.get("kind")))) continue;
            String id = Atlas.id(o);
            ck(!o.containsKey("node") && !o.containsKey("site"), id + ": без узла и без площадки (стройка отложена)");
            Map<String, Double> want = pot(TerritoryKind.byId(Json.str(o.get("type"))), Territories.features(o));
            Map<String, Double> got = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(o.get("potential")).entrySet()) got.put(e.getKey(), Json.num(e.getValue()));
            ck(want.equals(got), id + ": потенциал выведен из типа и особенностей, не произвольный (" + got + ")");
            checked++;
        }
        ck(checked == 8, "сверены все 8 территорий");

        // --- 4. валидатор ловит запрещённое ---
        ck(has(errorsWith("\"potential\": {\"parts\": 0.5}", "\"potential\": {\"food\": 0.5}"), "не даёт food"), "ловит potential.food");
        ck(has(errorsWith("\"potential\": {\"parts\": 0.5}", "\"potential\": {\"water\": 0.5}"), "не даёт water"), "ловит potential.water");
        ck(has(errorsWith("\"potential\": {\"parts\": 0.5}", "\"potential\": {\"fuel\": 0.5}"), "неизвестный ключ"), "ловит неизвестный ключ");
        ck(has(errorsWith("\"potential\": {\"parts\": 0.5}", "\"potential\": {\"parts\": 1.5}"), "вне 0..1"), "ловит силу вне 0..1");
        ck(has(errorsWith("\"potential\": {\"parts\": 0.5},", ""), "нет potential"), "ловит зону без потенциала");
        ck(has(errorsWith("\"area\": [[-4464, 3619], [-4429, 3619], [-4429, 3648], [-4464, 3648]],", ""), "нет area"), "ловит зону без area");
        ck(has(errorsWith("\"type\": \"scrap\"", "\"type\": \"castle\""), "неизвестный type"), "ловит неизвестный тип");
        ck(has(errorsWith("\"kind\": \"zone\",\n      \"type\": \"scrap\"", "\"kind\": \"zone\",\n      \"node\": 24,\n      \"type\": \"scrap\""), "не узел"), "ловит зону с узлом");
        ck(has(errorsWith("\"buildings\": [{\"ref\": \"cordon.field.dump\"}]", "\"buildings\": [{\"ref\": \"cordon.base.village\"}]"), "вне area зоны"), "ловит постройку вне зоны");
        ck(has(errorsWith("\"buildings\": [{\"ref\": \"cordon.field.dump\"}]", "\"buildings\": [{\"ref\": \"cordon.zone.nowhere\"}]"), "не найден"), "ловит несуществующую постройку");
        ck(has(errorsWith("\"parent\": \"cordon.field.dump\"", "\"parent\": \"cordon.landmark.ruins\""), "не узел"), "ловит родителя не узла");
        ck(has(errorsWith("\"forest\": 0.1675", "\"forest\": 1.5"), "доля 0..1"), "ловит долю леса вне 0..1");
        ck(has(errorsWith("\"fields\": 1}", "\"fields\": 1.5}"), "целое"), "ловит нецелое число особенностей");

        // --- 5. занятие и освобождение территории ---
        Territories ts = new Territories();
        ts.define(Territories.fromAtlas(a));
        Territory elev = ts.byId("cordon.zone.elevator");
        ck(elev != null && !elev.occupied() && elev.owner == -1 && elev.task == null, "изначально территория ничья, задачи нет");
        ck(ts.occupy(elev.id, 0, 1), "группировка занимает подходящую территорию");
        ck(elev.owner == 0 && elev.occupiedDay == 1, "принадлежность и день занятия зафиксированы");
        ck(!ts.occupy(elev.id, 3, 2), "чужую занятую территорию занять нельзя (захват — Э7-04)");
        ck(!ts.occupy(elev.id, -1, 2), "без группировки занять нельзя");
        ck(!ts.occupy("cordon.zone.nowhere", 0, 2), "несуществующую территорию занять нельзя");
        ck(ts.release(elev.id) && elev.owner == -1 && elev.task == null && elev.occupiedDay == -1, "освобождение снимает владельца и задачу");
        ck(!ts.release(elev.id), "повторное освобождение — отказ");
        ck(ts.occupy(elev.id, 3, 3) && elev.owner == 3, "после освобождения территорию занимает другая группировка");
        Territory empty = new Territory("test.zone.empty", TerritoryKind.FOREST, "Пустая", Map.of());
        Territories t2 = new Territories();
        t2.define(List.of(empty));
        ck(!t2.occupy(empty.id, 0, 1), "территорию без потенциала занимать незачем");
        ck(elev.reserve("workshop") == elev.capacity("workshop"), "занятие и освобождение не меняют запас зоны");

        // --- 6. локальная задача по потенциалу ---
        ck(elev.suitable(TerritoryTask.WORKSHOP) && !elev.suitable(TerritoryTask.ARTIFACTS), "пригодность — по потенциалу территории");
        ck(ts.assign(elev.id, TerritoryTask.WORKSHOP) && elev.task == TerritoryTask.WORKSHOP, "мастерская на элеваторе (workshop 0.63)");
        ck(!ts.assign(elev.id, TerritoryTask.ARTIFACTS), "задача не по потенциалу отклоняется");
        ck(!ts.assign(elev.id, TerritoryTask.WOOD), "заготовка дерева на элеваторе не по потенциалу");
        ck(ts.assign(elev.id, null) && elev.task == null, "задачу можно снять");
        ck(!ts.assign("cordon.zone.dump", TerritoryTask.PARTS), "без владельца задачу не назначить");
        ck(ts.occupy("cordon.zone.dump", 0, 4) && ts.assign("cordon.zone.dump", TerritoryTask.PARTS), "свалка: разборка техники");
        ck(!ts.assign("cordon.zone.dump", TerritoryTask.TRADE), "на свалке нет торговли и пошлины");

        // --- 7. добыча: конечный запас, нужны люди и задача, восстановление с задержкой ---
        ts.assign(elev.id, TerritoryTask.WORKSHOP);
        double cap = elev.capacity("workshop");
        ck(cap == 63, "запас зоны = сила потенциала × 100 (" + cap + ")");
        ck(ts.take(elev.id, TerritoryTask.WORKSHOP, 0, 10, 5) == 0, "без людей добычи нет");
        ck(ts.take(elev.id, TerritoryTask.PARTS, 5, 10, 5) == 0, "не по локальной задаче добычи нет");
        ck(ts.take(elev.id, TerritoryTask.WORKSHOP, 5, 0, 5) == 0, "нулевой заказ ничего не даёт");
        double got = ts.take(elev.id, TerritoryTask.WORKSHOP, 5, 10, 5);
        ck(got == 10 && elev.reserve("workshop") == cap - 10, "добыча уменьшает запас ровно на взятое");
        ck(ts.take(elev.id, TerritoryTask.WORKSHOP, 100, 1000, 6) == cap - 10, "больше, чем есть в зоне, добыть нельзя");
        ck(elev.reserve("workshop") == 0 && ts.take(elev.id, TerritoryTask.WORKSHOP, 5, 10, 7) == 0, "исчерпанная зона ничего не даёт");
        ck(ts.take(elev.id, TerritoryTask.WORKSHOP, 1, 100, 7) == 0, "один человек не добудет из пустой зоны");
        ts.daily(8);
        ck(elev.reserve("workshop") == 0, "раньше 7 дней после добычи запас не восстанавливается");
        ts.daily(13);
        ck(elev.reserve("workshop") > 0 && elev.reserve("workshop") < cap, "после задержки запас восстанавливается частями");
        for (int d = 14; d < 300; d++) ts.daily(d);
        ck(elev.reserve("workshop") == cap, "восстановление не превышает полный запас зоны");
        ck(ts.take("cordon.zone.bald_hill", TerritoryTask.MUTANT, 5, 10, 300) == 0, "без владельца добычи нет даже с запасом");

        // --- 8. сохранение состояния после перезапуска ---
        Simulation s = Scenarios.create("kordon", 7);
        s.territories.define(Territories.fromAtlas(a));
        for (int d = 0; d < 5; d++) s.step();
        ck(s.territories.occupy("cordon.zone.atp_crossroads", 3, s.day), "банда занимает развилку у АТП");
        ck(s.territories.assign("cordon.zone.atp_crossroads", TerritoryTask.TRADE), "локальная задача — торговля и пошлина");
        double took = s.territories.take("cordon.zone.atp_crossroads", TerritoryTask.TRADE, 4, 6, s.day);
        ck(took == 6, "добыча на развилке: 6 единиц");
        Territory before = s.territories.byId("cordon.zone.atp_crossroads");
        String data = s.save();
        Simulation l = Simulation.load(data);
        Territory after = l.territories.byId("cordon.zone.atp_crossroads");
        ck(after != null && after.owner == 3 && after.task == TerritoryTask.TRADE && after.occupiedDay == before.occupiedDay,
                "после загрузки владелец, задача и день занятия сохранены");
        ck(after.kind == before.kind && after.potential.equals(before.potential), "тип и потенциал сохранены");
        boolean reserveOk = true;
        for (String k : TerritoryPotential.KEYS)
            if (after.reserve(k) != before.reserve(k) || after.capacity(k) != before.capacity(k)) reserveOk = false;
        ck(reserveOk, "запас и полный запас территории сохранены");
        ck(l.territories.list.size() == s.territories.list.size(), "число территорий сохранено");
        ck(l.day == s.day && l.audit().isEmpty(), "день сохранён, аудит пуст после загрузки: " + l.audit());
        ck(l.territories.byId("cordon.zone.dump").owner == -1, "незанятая территория осталась ничьей");
        Simulation l2 = Simulation.load(l.save());
        ck(l2.territories.byId("cordon.zone.atp_crossroads").reserve("trade") == after.reserve("trade"),
                "повторный цикл сохранения не меняет запас");

        // --- 9. владение не создаёт ресурсов, денег и людей; регрессий нет ---
        Simulation base = Scenarios.create("kordon", 11);
        Simulation with = Scenarios.create("kordon", 11);
        with.territories.define(Territories.fromAtlas(a));
        for (Territory t : with.territories.list) {
            ck(with.territories.occupy(t.id, 0, 0), "занята " + t.id);
            TerritoryTask task = TerritoryTask.byKey(t.potential.keySet().iterator().next());
            ck(with.territories.assign(t.id, task), "задача по потенциалу: " + t.id + " — " + task.ru);
        }
        for (int d = 0; d < 30; d++) { base.step(); with.step(); }
        ck(base.audit().isEmpty() && with.audit().isEmpty(), "аудит пуст в обоих прогонах: " + with.audit());
        ck(snapshot(base).equals(snapshot(with)), "30 дней с занятыми территориями: экономика, склады и население не изменились");
        ck(with.ledger.created[Good.FOOD.ordinal()] == base.ledger.created[Good.FOOD.ordinal()],
                "территории не производят еду (еда только привозная, Э8-08)");
        boolean full = true;
        for (Territory t : with.territories.list)
            for (String k : TerritoryPotential.KEYS) if (t.reserve(k) != t.capacity(k)) full = false;
        ck(full, "без добычи запас зон остался полным: владение ничего не изымает и не создаёт");
        ck(with.nodes.size() == base.nodes.size() && with.factions.size() == base.factions.size(),
                "набор узлов и группировок не изменился");
        boolean owners = true;
        for (int i = 0; i < base.nodes.size(); i++) if (base.nodes.get(i).owner != with.nodes.get(i).owner) owners = false;
        ck(owners, "владельцы узлов не изменились: принадлежность территории не смешана с узлами");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
