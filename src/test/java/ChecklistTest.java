import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** П-06: чек-лист по файлам — CHECKLIST.md не дописывают, в docs/checklist/ у каждого файла заголовок и пункты. */
public class ChecklistTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    public static void main(String[] a) throws Exception {
        String root = Files.readString(Path.of("CHECKLIST.md")).replace("\r\n", "\n");
        ck(root.lines().noneMatch(l -> l.startsWith("|")), "в CHECKLIST.md нет пунктов (они в docs/checklist/<задача>.md)");
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of("docs/checklist"))) {
            files = s.filter(p -> p.toString().endsWith(".md") && !p.getFileName().toString().equals("README.md")).sorted().toList();
        }
        ck(files.size() >= 30, "файлов чек-листа: " + files.size());
        for (Path p : files) {
            List<String> ls = Files.readAllLines(p);
            boolean head = !ls.isEmpty() && ls.get(0).startsWith("# ");
            boolean item = ls.stream().anyMatch(l -> l.startsWith("|") && !l.startsWith("|---") && !l.startsWith("| №"))
                    || ls.stream().anyMatch(l -> l.matches("^\\s*([-*]|\\d+\\.) .*"));
            ck(head && item, p.getFileName() + ": заголовок «# …» и хотя бы один пункт");
        }
        System.exit(f > 0 ? 1 : 0);
    }
}
