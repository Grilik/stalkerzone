import java.nio.file.Files;
import java.nio.file.Path;

import ru.stalkerzone.sim.TheZoneRules;

/** Э2-20: в режиме THEZONE NPC симуляции не ставятся (узлы ещё в координатах старого Кордона), уровни .szl не грузятся. */
public class TheZoneNpcTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static String read(String p) throws Exception { return Files.readString(Path.of(p)).replace("\r\n", "\n"); }

    /** Первая строка тела метода tick(MinecraftServer) — проверка правила. */
    static boolean guarded(String src, String rule) {
        int i = src.indexOf("public static void tick(MinecraftServer server) {");
        if (i < 0) return false;
        String next = src.substring(i).split("\n", 3)[1].trim();
        return next.startsWith("if (!ru.stalkerzone.sim.TheZoneRules." + rule + "()) return;");
    }

    public static void main(String[] a) throws Exception {
        TheZoneRules.set(false);
        ck(TheZoneRules.simNpcSpawn() && TheZoneRules.originalLevels(), "LEGACY: NPC симуляции и уровни оригинала есть");
        TheZoneRules.set(true);
        ck(!TheZoneRules.simNpcSpawn() && !TheZoneRules.originalLevels(), "THEZONE: NPC симуляции нет, уровни оригинала не грузятся");
        TheZoneRules.set(false);

        for (String m : new String[]{"NpcManager", "CaravanManager", "MeetingManager", "ZombieManager", "Hermits"})
            ck(guarded(read("src/main/java/ru/stalkerzone/npc/" + m + ".java"), "simNpcSpawn"),
                    m + ".tick в THEZONE сразу выходит — ничего не ставит");
        String lr = read("src/main/java/ru/stalkerzone/world/xray/LevelRegions.java");
        int all = lr.indexOf("public static List<LevelRegion> all() {");
        ck(all >= 0 && lr.indexOf("TheZoneRules.originalLevels()) return List.of();", all) > all
                && lr.indexOf("TheZoneRules.originalLevels()) return List.of();", all) < lr.indexOf("synchronized", all),
                "LevelRegions.all() в THEZONE пуст до загрузки .szl (и площадок l01_escape)");
        String wm = read("src/main/java/ru/stalkerzone/world/WorldMode.java");
        ck(wm.contains("finally { ru.stalkerzone.sim.TheZoneRules.set(current == THEZONE); }")
                && wm.contains("ru.stalkerzone.sim.TheZoneRules.set(m == THEZONE)"), "WorldMode.detect/force передают режим правилам");
        System.exit(f > 0 ? 1 : 0);
    }
}
