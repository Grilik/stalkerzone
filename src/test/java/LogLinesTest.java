import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Код мира и клиента пишет в лог мода, а не в System.out/err: у игрока Forge не переносит stdout в logs/latest.log
 * (плейтест 05.11: строки «площадки l01_escape» не было). Загрузка уровня пишет строку с файлом и размером.
 */
public class LogLinesTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) throws Exception {
        for (String dir : new String[] {"src/main/java/ru/stalkerzone/world", "src/main/java/ru/stalkerzone/client"}) {
            try (Stream<Path> ps = Files.walk(Path.of(dir))) {
                for (Path p : (Iterable<Path>) ps.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    String s = Files.readString(p);
                    // LevelRegion — чистая Java без логгера: строку о площадках отдаёт полем sitesLog
                    ck(!s.contains("System.out.print") && !s.contains("System.err.print"), "без System.out/err: " + p);
                }
            }
        }
        String lr = Files.readString(Path.of("src/main/java/ru/stalkerzone/world/xray/LevelRegions.java"));
        ck(lr.contains("уровень {} загружен: файл {}, {} байт, x0/z0/w/d {}/{}/{}/{}, вокселей {}"), "строка о загрузке уровня");
        ck(lr.contains("LOG.info(lr.sitesLog)"), "строка о площадках идёт в лог мода");
        ck(lr.contains("уровень {} не найден"), "нет файла уровня — предупреждение в логе");
        System.exit(f > 0 ? 1 : 0);
    }
}
