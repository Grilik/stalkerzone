import ru.stalkerzone.sim.KordonScenario;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.zonemap.atlas.Atlas;
import ru.stalkerzone.zonemap.atlas.AtlasValidator;
import ru.stalkerzone.zonemap.atlas.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Э1-13, Э1-06: атлас Кордона загружается, проходит валидатор б4; узлы на TheZone соответствуют KordonScenario.COORDS
 *  (координаты симуляции — пока «Кордон 1:1») через грубое подобие локации с допуском на ручную расстановку. */
public class AtlasTest {
  static int f = 0;
  static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

  static final Path DIR = Path.of("src/main/resources/data/stalkerzone/atlas");

  static String read(String rel) {
    try { return Files.readString(DIR.resolve(rel), StandardCharsets.UTF_8).replace("\r\n", "\n"); }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  /** Атлас с подменой текста в одном из файлов. */
  static List<String> errorsWith(String file, String from, String to) {
    String src = read(file);
    if (!src.contains(from)) throw new IllegalStateException("в " + file + " нет фрагмента: " + from);
    Atlas a = Atlas.load(rel -> rel.equals(file) ? src.replace(from, to) : read(rel));
    return AtlasValidator.validate(a);
  }

  /** То же с двумя подменами (вторая — первое вхождение). */
  static List<String> errorsWith2(String file, String f1, String t1, String f2, String t2) {
    String src = read(file);
    if (!src.contains(f1) || !src.contains(f2)) throw new IllegalStateException("в " + file + " нет фрагмента");
    String s2 = src.replace(f1, t1).replaceFirst(java.util.regex.Pattern.quote(f2), java.util.regex.Matcher.quoteReplacement(t2));
    Atlas a = Atlas.load(rel -> rel.equals(file) ? s2 : read(rel));
    return AtlasValidator.validate(a);
  }

  static boolean has(List<String> errs, String part) {
    for (String e : errs) if (e.contains(part)) return true;
    return false;
  }

  public static void main(String[] args) {
    // --- парсер ---
    Object j = Json.parse("{\"a\":[1,-2.5e1,\"x\\u0041\\n\"],\"b\":{\"c\":null,\"d\":true}}");
    Map<String, Object> jm = Json.obj(j);
    ck(Json.nums(Json.list(jm.get("a")).subList(0, 2)) != null && Json.num(Json.list(jm.get("a")).get(1)) == -25.0, "JSON: числа");
    ck("xA\n".equals(Json.list(jm.get("a")).get(2)), "JSON: строки и escape");
    boolean bad = false;
    try { Json.parse("{\"a\":1,}"); } catch (IllegalArgumentException e) { bad = true; }
    ck(bad, "JSON: ошибка на запятой перед }");

    // --- реальный атлас ---
    Atlas a = Atlas.loadDir(DIR);
    List<String> errs = AtlasValidator.validate(a);
    for (String e : errs) System.out.println("  ошибка: " + e);
    ck(errs.isEmpty(), "атлас проходит валидатор (ID, node, links, bbox, area, площадки, базы, residual, teleport)");
    ck(a.locations.size() == 1 && "cordon".equals(Atlas.id(a.locations.get(0))), "в атласе одна локация cordon");

    Map<Integer, Map<String, Object>> nodes = a.nodes();
    ck(nodes.size() == 24, "узлов 24");
    boolean seq = true;
    int k = 0;
    for (int n : nodes.keySet()) seq &= n == k++;
    ck(seq, "номера узлов 0..23 подряд");

    // 19-23: источник указан
    for (int n = 19; n <= 23; n++) {
      String src = Json.str(nodes.get(n).get("source"));
      ck(src != null && src.startsWith("ручная правка"), "узел " + n + ": источник указан (" + (src == null ? "нет" : src.substring(0, Math.min(40, src.length()))) + "...)");
    }
    // 0-18 из l01_escape.json, кроме перенесённого холма
    for (int n = 0; n <= 18; n++) {
      String src = Json.str(nodes.get(n).get("source"));
      ck(src != null && (src.startsWith("l01_escape.json") || n == 13), "узел " + n + ": источник указан");
    }

    // Кордон на TheZone (Э1-06, решение 15.11): места по F3 — вручную, остальное — подобием оригинал -> TheZone.
    // COORDS — в координатах «Кордон 1:1» (= оригинал + (209, -142)); пересчёт COORDS*10 подобием локации ложится
    // не дальше 75 блоков от узла атласа (ручной сдвиг мест по F3 — до ~60, округление COORDS — до 5 по оси).
    Map<String, Object> tr = Json.obj(a.locations.get(0).get("transform"));
    double sc = Json.num(tr.get("scale")), an = Math.toRadians(Json.num(tr.get("angle")));
    double ta = sc * Math.cos(an), tb = sc * Math.sin(an), tdx = Json.num(tr.get("dx")), tdz = Json.num(tr.get("dz"));
    int[][] C = KordonScenario.COORDS;
    ck(C.length == 24, "COORDS: 24 узла");
    Simulation sim = KordonScenario.create(0);
    for (int i = 0; i < 24; i++) {
      Map<String, Object> o = nodes.get(i);
      double ox = C[i][0] * 10.0 - 209, oz = C[i][1] * 10.0 + 142;
      double d = Math.hypot(Atlas.x(o) - (ta * ox - tb * oz + tdx), Atlas.z(o) - (tb * ox + ta * oz + tdz));
      ck(d <= 75, "узел " + i + " " + o.get("name") + ": на TheZone в " + Math.round(d) + " бл. от пересчёта COORDS");
      Node sn = sim.nodes.get(i);
      ck(sn.name.equals(o.get("name")), "узел " + i + ": имя атласа совпадает с сценарием");
    }

    // --- отрицательные случаи: валидатор действительно ловит ошибки ---
    String loc = "locations/cordon.json";
    ck(has(errorsWith(loc, "\"id\": \"cordon.trader.sidorovich\"", "\"id\": \"cordon.base.village\""), "повтор id объекта"), "ловит повтор id");
    ck(has(errorsWith(loc, "\"node\": 23,", "\"node\": 22,"), "node 22 повторён"), "ловит повтор node");
    ck(has(errorsWith(loc, "\"bbox\": [-4449, 3756, -4343, 3822]", "\"bbox\": [-4297, 3676, -4268, 3707]"), "площадок"), "ловит пересечение bbox площадок");
    ck(has(errorsWith(loc, "\"builder\": \"wagon\",", "\"builder\": \"wagon\", \"clear\": [[0, 0, 1, 1]],"), "clear"), "ловит CLEAR вне redesign");
    ck(has(errorsWith(loc, "\"builder\": \"wagon\",\n        \"mode\": \"keep\"", "\"builder\": \"wagon\",\n        \"mode\": \"adapt\""), "site.clear") == false, "adapt без clear допустим");
    ck(has(errorsWith(loc, "\"campfire\": [-4402, 70, 3782],", ""), "campfire"), "ловит базу без костра");
    ck(has(errorsWith(loc, "\"beds\": [[-4378, 70, 3777], [-4406, 71, 3762], [-4405, 71, 3766]],", ""), "points.beds"), "ловит базу без кроватей");
    // невязка Кордона вне порога (Э1-06) — все объекты observed; объект с другим статусом — ошибка
    String obs = "\"status\": \"observed\"", acc = "\"status\": \"accepted\"";
    String res = "\"rms\": 31.7,\n    \"max\": 57.67,\n    \"loo\": {\n      \"max\": 67.09";
    ck(has(errorsWith(loc, obs, acc), "невязка"), "ловит невязку вне порога без observed");
    ck(!has(errorsWith2(loc, res, "\"rms\": 1,\n    \"max\": 2,\n    \"loo\": {\n      \"max\": 3", obs, acc), "невязка"),
        "невязка в пороге: статус не обязан быть observed");
    ck(has(errorsWith2(loc, res, "\"rms\": 1,\n    \"max\": 2,\n    \"loo\": {\n      \"max\": 99", obs, acc), "невязка"),
        "ловит loo.max вне порога");
    ck(has(errorsWith(loc, "\"teleport\": [-4118, 78, 3486]", "\"teleport\": [5000, 0, 5000]"), "teleport"), "ловит teleport вне bbox");
    ck(has(errorsWith(loc, "\"pos\": [-4400, 70, 3788]", "\"pos\": [9000, 70, 3788]"), "вне bbox"), "ловит объект вне bbox локации");
    ck(has(errorsWith("world.json", "\"links\": []", "\"links\": [{\"from\": \"cordon\", \"to\": \"nowhere\", \"type\": \"road\", \"line\": [[0, 0], [1, 1]]}]"), "nowhere"), "ловит link на несуществующую локацию");
    ck(has(errorsWith(loc, "\"kind\": \"base\",\n      \"node\": 0", "\"kind\": \"castle\",\n      \"node\": 0"), "kind"), "ловит неизвестный kind");

    // area двух локаций пересекаются: та же локация дважды под другим id в world.json
    final String w2f = read("world.json").replace("\"locations\": [",
        "\"locations\": [{\"id\": \"twin\", \"file\": \"locations/twin.json\"}, ");
    String twin = "{\"id\": \"twin\", \"name\": \"T\", \"source\": \"test\", \"bbox\": [-4500, 3500, -4490, 3510],"
        + " \"area\": [[-4500, 3500], [-4490, 3500], [-4490, 3510], [-4500, 3510]], \"residual\": {\"rms\": 0, \"max\": 0, \"loo\": {\"max\": 0}}, \"objects\": []}";
    Atlas two = Atlas.load(rel -> rel.equals("world.json") ? w2f : rel.equals("locations/twin.json") ? twin : read(rel));
    ck(has(AtlasValidator.validate(two), "пересекаются"), "ловит пересекающиеся area локаций");

    System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
    System.exit(f);
  }
}
