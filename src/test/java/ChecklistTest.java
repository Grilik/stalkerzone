import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** П-06: чек-лист по файлам — CHECKLIST.md не дописывают, в docs/checklist/ у каждого файла заголовок и пункты. */
public class ChecklistTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    /** Э0-22: отметка «Проверено» — «✅|❌ ГГГГ-ММ-ДД, хеш сборки (7+), кто» и, по желанию, « — примечание». */
    static final java.util.regex.Pattern MARK =
            java.util.regex.Pattern.compile("^(✅|❌) \\d{4}-\\d{2}-\\d{2}, [0-9a-f]{7,40}, [^,—]+?( — .+)?$");

    /** В таблицах с колонкой «Проверено» каждая заполненная ячейка — в формате {@link #MARK}. */
    static void checked(String name, List<String> ls) {
        int col = -1, bad = 0, marks = 0;
        for (String l : ls) {
            if (!l.startsWith("|")) { col = -1; continue; }
            String[] c = l.split("\\|", -1);
            if (l.startsWith("| №")) {
                col = -1;
                for (int i = 0; i < c.length; i++) if (c[i].trim().equals("Проверено")) col = i;
                continue;
            }
            if (col < 0 || l.startsWith("|---") || col >= c.length) continue;
            String v = c[col].trim();
            if (v.isEmpty()) continue;
            marks++;
            if (!MARK.matcher(v).matches()) { bad++; System.out.println("  " + name + ": плохая отметка «" + v + "»"); }
        }
        if (marks > 0) ck(bad == 0, name + ": отметки «Проверено» в формате (" + marks + ")");
    }

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
            checked(p.getFileName().toString(), ls);
        }
        // проба формата отметки
        ck(MARK.matcher("✅ 2026-11-14, a581fc2, пользователь — 15 мин").matches()
                && !MARK.matcher("✅ 14.11, a581fc2").matches() && !MARK.matcher("ок").matches(), "формат отметки «Проверено» распознаётся");
        System.exit(f > 0 ? 1 : 0);
    }
}
