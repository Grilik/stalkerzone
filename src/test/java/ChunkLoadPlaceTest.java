import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Зависание 07.11 (плейтест, сторож): рельсы туннеля ставились при ChunkEvent.Load через LevelChunk.setBlockState →
 * BaseRailBlock.onPlace опрашивал соседний, ещё не готовый чанк → серверный поток ждал вечно.
 * ObjPropBlock.safeSet обязан ставить блоки без BlockEntity прямо в секцию (rawSet), без onPlace.
 * Проверка по исходникам: класс зависит от Minecraft, в тестах симуляции его не скомпилировать.
 */
public class ChunkLoadPlaceTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    static String body(String src, String sig) {
        int i = src.indexOf(sig);
        if (i < 0) return "";
        int b = src.indexOf('{', i), d = 0;
        for (int j = b; j < src.length(); j++) {
            if (src.charAt(j) == '{') d++;
            if (src.charAt(j) == '}' && --d == 0) return src.substring(b, j + 1);
        }
        return "";
    }

    public static void main(String[] a) throws Exception {
        String src = Files.readString(Path.of("src/main/java/ru/stalkerzone/block/ObjPropBlock.java"));
        String safe = body(src, "public static boolean safeSet(");
        String raw = body(src, "private static boolean rawSet(");
        ck(!safe.isEmpty() && !raw.isEmpty(), "есть safeSet и rawSet");
        int toRaw = safe.indexOf("rawSet("), vanilla = safe.indexOf("c.setBlockState(");
        ck(toRaw >= 0 && safe.contains("!s.hasBlockEntity()") && (vanilla < 0 || toRaw < vanilla),
                "safeSet: блок без BlockEntity в LevelChunk — через rawSet, до ванильного setBlockState");
        ck(!raw.contains(".setBlockState(pos") && !raw.contains("onPlace(") && raw.contains("sec.setBlockState("),
                "rawSet пишет в секцию и не зовёт onPlace (ни setBlockState чанка)");
        ck(raw.contains("getHeightmaps()") && raw.contains("checkBlock(") && raw.contains("updateSectionStatus("),
                "rawSet обновляет карты высот и свет, как ваниль");
        ck(raw.contains("hasDifferentLightProperties(c,"), "свойства света читаются из чанка, не из Level (Level потянул бы getChunk)");
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛОВ: " + f);
        if (f > 0) System.exit(1);
    }
}
