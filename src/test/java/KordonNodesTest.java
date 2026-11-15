import ru.stalkerzone.sim.*;
import ru.stalkerzone.zonemap.build.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Э0-11: номера узлов Кордона продублированы в коде и JSON — сверяем, пока не заменит атлас (Э1-13/Э2-05). */
public class KordonNodesTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static boolean inRange(int[] ids, int n) { for (int i : ids) if (i < 0 || i >= n) return false; return true; }

    public static void main(String[] a) throws Exception {
        int n = KordonScenario.create(1).nodes.size();
        ck(KordonScenario.COORDS.length == n, "COORDS.length (" + KordonScenario.COORDS.length + ") == узлов сценария (" + n + ")");
        ck(KordonBalance.RAID_TARGET_WEIGHT.length == n, "RAID_TARGET_WEIGHT.length (" + KordonBalance.RAID_TARGET_WEIGHT.length + ") == узлов сценария (" + n + ")");

        ck(Prefabs.NODE_WAGON == KordonScenario.WAGON, "Prefabs.NODE_WAGON == KordonScenario.WAGON");
        ck(Prefabs.NODE_DUMP == KordonScenario.DUMP, "Prefabs.NODE_DUMP == KordonScenario.DUMP");
        ck(Prefabs.NODE_LAIR_L3 == KordonScenario.DUMP_FOREST, "Prefabs.NODE_LAIR_L3 == KordonScenario.DUMP_FOREST");

        for (Site s : Prefabs.sites("l01_escape"))
            ck(s.node >= 0 && s.node < n, "площадка " + s.id + ": узел " + s.node + " существует");

        ck(inRange(KordonScenario.SMALL_OUTPOSTS, n), "SMALL_OUTPOSTS в диапазоне");
        ck(inRange(KordonScenario.LAIR_SITES, n), "LAIR_SITES в диапазоне");
        ck(inRange(KordonScenario.EXITS, n), "EXITS в диапазоне");
        boolean zo = true;
        for (int[] row : KordonScenario.ZONE_OUTPOSTS) zo &= inRange(row, n);
        ck(zo, "ZONE_OUTPOSTS в диапазоне");

        String json = Files.readString(Path.of("src/main/resources/data/stalkerzone/points/l01_escape.json"));
        Matcher m = Pattern.compile("(?m)^ {3}\"id\": (\\d+)").matcher(json);
        Set<Integer> ids = new TreeSet<>();
        while (m.find()) ids.add(Integer.parseInt(m.group(1)));
        ck(!ids.isEmpty(), "в l01_escape.json найдены узлы: " + ids.size());
        boolean sub = true;
        for (int id : ids) if (id >= n) { sub = false; System.out.println("     узел JSON " + id + " вне сценария"); }
        ck(sub, "id из l01_escape.json входят в узлы сценария");
        for (int i = 0; i < n; i++)
            if (!ids.contains(i)) System.out.println("WARN узел " + i + " без точек в l01_escape.json");

        System.out.println(f == 0 ? "KordonNodesTest: OK" : "KordonNodesTest: FAIL " + f);
        if (f != 0) System.exit(1);
    }
}
