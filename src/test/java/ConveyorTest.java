import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** П-13: конвейер v2 — одна линия, одна попытка с потолком, без старшего Claude и автослияния; паспорта карточек верны. */
public class ConveyorTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static String read(String p) throws Exception { return Files.exists(Path.of(p)) ? Files.readString(Path.of(p)) : ""; }

    static String field(String text, String name) {
        Matcher m = Pattern.compile("^\\*\\*" + name + ":\\*\\*\\s*(.+)$", Pattern.MULTILINE).matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    static List<String> paths(String v) {
        return Pattern.compile("`([^`]+)`").matcher(v).results().map(r -> r.group(1)).toList();
    }

    public static void main(String[] a) throws Exception {
        String sh = read("tools/conveyor/conveyor.sh"), yml = read(".github/workflows/conveyor.yml");
        ck(!sh.isEmpty() && !yml.isEmpty(), "есть conveyor.sh и conveyor.yml");
        ck(!sh.contains("claude -p") && !sh.contains("STRONG_MODEL") && !yml.contains("CLAUDE_CODE_OAUTH_TOKEN"),
                "старшего Claude в конвейере нет");
        ck(!sh.contains("gh pr merge"), "конвейер не сливает PR сам (автослияния нет)");
        ck(sh.contains("CARD_MINUTES:-15") && sh.contains("CARD_BUDGET:-0.30") && sh.contains("CARD_RETRIES:-2"),
                "потолок на карточку: 15 мин, $0,30, --retries 2");
        ck(sh.contains("не вышло"), "неудача — PR «не вышло»");
        ck(sh.contains("conveyor-metrics:") && Files.exists(Path.of("tools/conveyor/metrics.py")), "метрики в PR и сводка metrics.py");
        ck(!yml.contains("matrix") && yml.contains("group: conveyor"), "одна линия (без matrix, одна очередь)");
        // таблицу карточек правит только конвейер после слияния (иначе соседние строки конфликтуют: PR #109, #115)
        ck(yml.contains("mark_done.py") && Files.exists(Path.of("tools/conveyor/mark_done.py")),
                "статус «готово» ставит конвейер после слияния (mark_done.py в conveyor.yml)");
        ck(!read("tools/conveyor/card.py").contains("\"docs/tasks/README.md\"}") && !sh.contains("только статус своей карточки")
                && read("tools/conveyor/rules.txt").contains("docs/tasks/README.md не меняй"),
                "исполнитель не правит docs/tasks/README.md");

        ck(read("tools/conveyor/check.sh").contains("tools/ci/test_sources.sh")
                && read(".github/workflows/build.yml").contains("tools/ci/test_sources.sh"),
                "CI и конвейер компилируют тесты по одному списку tools/ci/test_sources.sh");
        ck(read(".github/workflows/build.yml").contains("tools/ci/server_smoke.sh"), "CI запускает сервер на синтетическом уровне");
        String ign = read(".clineignore");
        ck(ign.contains("docs/claude/") && ign.contains("tools/xray/"), ".clineignore закрывает docs/claude/ и tools/xray/");

        int cards = 0;
        try (Stream<Path> s = Files.list(Path.of("docs/tasks"))) {
            for (Path p : s.filter(x -> x.getFileName().toString().matches("\\d{3}-.*\\.md")).sorted().toList()) {
                String t = Files.readString(p);
                if (!field(t, "Конвейер").startsWith("да")) continue;
                cards++;
                String n = p.getFileName().toString();
                List<String> test = paths(field(t, "Тест"));
                boolean testOk = test.size() == 1 && test.get(0).matches("docs/tasks/tests/[A-Z]\\w*Test\\.java")
                        && Files.exists(Path.of(test.get(0)));
                ck(testOk, n + ": тест карточки в docs/tasks/tests/ есть");
                if (testOk) {
                    String cls = Path.of(test.get(0)).getFileName().toString().replace(".java", "");
                    ck(Files.readString(Path.of(test.get(0))).contains("class " + cls), n + ": имя класса теста = имя файла");
                }
                List<String> files = paths(field(t, "Файлы"));
                ck(!files.isEmpty() && files.stream().allMatch(x -> Files.exists(Path.of(x))
                        && !x.startsWith(".github/") && !x.startsWith("docs/claude/") && !x.startsWith("tools/xray/")),
                        n + ": разрешённые файлы есть и вне закрытых зон");
                String check = field(t, "Чек-лист");
                ck(check.startsWith("нет") || paths(check).stream().allMatch(x -> x.matches("docs/checklist/\\d{3}-[\\w-]+\\.md")),
                        n + ": чек-лист — «нет» или docs/checklist/NNN-имя.md");
                ck(read("docs/tasks/README.md").contains("| " + n.substring(0, 3) + " |"), n + ": строка в таблице docs/tasks/README.md");
            }
        }
        ck(cards >= 2, "карточек конвейера v2: " + cards);
        System.exit(f > 0 ? 1 : 0);
    }
}
