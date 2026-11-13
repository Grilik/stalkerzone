import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Зависание 07.11 (плейтест, сторож): рельсы туннеля ставились при ChunkEvent.Load через LevelChunk.setBlockState →
 * BaseRailBlock.onPlace опрашивал соседний, ещё не готовый чанк → серверный поток ждал вечно.
 * ObjPropBlock.safeSet обязан ставить блоки без BlockEntity прямо в секцию (rawSet), без onPlace.
 * Проверка по исходникам: класс зависит от Minecraft, в тестах симуляции его не скомпилировать.
 *
 * Э0-07 (P-03): кроме safeSet/rawSet — обход всех исходников world/** и block/**. Прямая запись блока
 * (setBlockState( или .setBlock() допустима только в файлах пути тика — игрок рядом, чанки загружены
 * (ZoneMapPlacer, RadioactiveSpots, Painter) — и внутри самого ObjPropBlock, единственной точки записи при
 * ChunkEvent.Load. Блок с BlockEntity при загрузке чанка заменяется через ObjPropBlock.replaceBlockEntity
 * (снятие BlockEntity средствами чанка + rawSet), а не через LevelChunk.setBlockState с onRemove.
 * Это текстовая проверка; поведение на сервере проверяют полоса периметра и «zone wallbe» в tools/ci/server_smoke.sh.
 */
public class ChunkLoadPlaceTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    /** Файлы, где прямой setBlock допустим: путь тика (игрок рядом, чанки загружены) и сам ObjPropBlock. */
    static final List<String> DIRECT_ALLOWED = List.of(
            "world/ZoneMapPlacer.java", "world/RadioactiveSpots.java", "world/Painter.java", "block/ObjPropBlock.java");

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

    /** Код строки без комментариев (// и /* … *&#47;), чтобы упоминания setBlockState в пояснениях не считались вызовами. */
    static String[] codeLines(String src) {
        String[] lines = src.split("\n", -1);
        boolean inBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i]; StringBuilder sb = new StringBuilder(); int pos = 0;
            while (pos < l.length()) {
                if (inBlock) {
                    int e = l.indexOf("*/", pos);
                    if (e < 0) pos = l.length(); else { inBlock = false; pos = e + 2; }
                } else {
                    int bl = l.indexOf("/*", pos), ln = l.indexOf("//", pos);
                    if (ln >= 0 && (bl < 0 || ln < bl)) { sb.append(l, pos, ln); pos = l.length(); }
                    else if (bl >= 0) { sb.append(l, pos, bl); inBlock = true; pos = bl + 2; }
                    else { sb.append(l.substring(pos)); pos = l.length(); }
                }
            }
            lines[i] = sb.toString();
        }
        return lines;
    }

    public static void main(String[] a) throws Exception {
        String src = Files.readString(Path.of("src/main/java/ru/stalkerzone/block/ObjPropBlock.java")).replace("\r\n", "\n");
        String safe = body(src, "public static boolean safeSet(");
        String raw = body(src, "private static boolean rawSet(");
        ck(!safe.isEmpty() && !raw.isEmpty(), "есть safeSet и rawSet");
        int toRaw = safe.indexOf("rawSet("), vanilla = safe.indexOf("c.setBlockState(");
        ck(toRaw >= 0 && safe.contains("!s.hasBlockEntity()") && (vanilla < 0 || toRaw < vanilla),
                "safeSet: блок без BlockEntity в LevelChunk — через rawSet, до ванильного setBlockState");
        ck(!safe.contains("getSections()[idx].setBlockState("), "safeSet: жидкость тоже через rawSet (карты высот и свет), не прямо в секцию");
        ck(!raw.contains(".setBlockState(pos") && !raw.contains("onPlace(") && raw.contains("sec.setBlockState("),
                "rawSet пишет в секцию и не зовёт onPlace (ни setBlockState чанка)");
        ck(raw.contains("getHeightmaps()") && raw.contains("checkBlock(") && raw.contains("updateSectionStatus("),
                "rawSet обновляет карты высот и свет, как ваниль");
        ck(raw.contains("hasDifferentLightProperties(c,"), "свойства света читаются из чанка, не из Level (Level потянул бы getChunk)");

        // Э0-07: замена блока с BlockEntity при загрузке чанка — без выхода в Level
        String rep = body(src, "public static boolean replaceBlockEntity(");
        ck(!rep.isEmpty() && rep.contains("removeBlockEntity(") && rep.contains("rawSet(") && !rep.contains("lc.setBlockState("),
                "replaceBlockEntity: снимает BlockEntity средствами чанка (removeBlockEntity) и пишет через rawSet");
        String wall = Files.readString(Path.of("src/main/java/ru/stalkerzone/world/WallBuilder.java")).replace("\r\n", "\n");
        ck(wall.contains("ObjPropBlock.replaceBlockEntity("), "WallBuilder: блок с BlockEntity в стене — через ObjPropBlock.replaceBlockEntity");

        // Э0-07: во всех исходниках world/** и block/** прямая запись блока разрешена только в DIRECT_ALLOWED
        Path root = Path.of("src/main/java/ru/stalkerzone");
        int files = 0, direct = 0;
        for (String dir : List.of("world", "block")) {
            try (Stream<Path> st = Files.walk(root.resolve(dir))) {
                for (Path p : (Iterable<Path>) st.filter(x -> x.toString().endsWith(".java")).sorted()::iterator) {
                    files++;
                    String rel = root.relativize(p).toString().replace('\\', '/');
                    if (DIRECT_ALLOWED.contains(rel)) continue;
                    String[] lines = codeLines(Files.readString(p).replace("\r\n", "\n"));
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains("setBlockState(") || lines[i].contains(".setBlock(")) {
                            direct++;
                            ck(false, rel + ":" + (i + 1) + " — прямая запись блока вне ObjPropBlock: " + lines[i].trim());
                        }
                    }
                }
            }
        }
        ck(files >= 10, "просмотрено исходников world/** и block/**: " + files);
        ck(direct == 0, "прямых записей блока вне " + DIRECT_ALLOWED + ": " + direct);
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛОВ: " + f);
        if (f > 0) System.exit(1);
    }
}
