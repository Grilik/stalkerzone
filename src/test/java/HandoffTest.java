import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** П-12: свежий клон без доступа к Project находит точку входа, очередь задач, журналы и навык. */
public class HandoffTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    static final String[] DOCS = {
        "00-start-here", "01-malyi-kordon", "02-prompts", "04-interfaces", "05-handoff", "06-conveyor-v2",
        "10-log-programmer", "11-log-builder",
        "stalker-mod-kordon-1.0", "stalker-mod-kordon-factions-logic", "stalker-mod-look-and-bases",
        "stalker-mod-lore-bible", "stalker-mod-design-details", "stalker-mod-kordon-1to1",
        "stalker-mod-whole-map", "stalker-mod-v116-combat-player", "stalker-mod-appearance", "stalker-mod-logistics",
    };

    public static void main(String[] a) throws Exception {
        Path claude = Path.of("CLAUDE.md");
        ck(Files.exists(claude), "CLAUDE.md в корне");
        String root = Files.exists(claude) ? Files.readString(claude).replace("\r\n", "\n") : "";
        ck(root.contains("docs/claude/00-start-here.md"), "CLAUDE.md указывает на docs/claude/00-start-here.md");
        // все пути docs/... и .claude/... из CLAUDE.md существуют
        Matcher m = Pattern.compile("`((?:docs|\\.claude)/[^`]+)`").matcher(root);
        while (m.find()) ck(Files.exists(Path.of(m.group(1))), "CLAUDE.md → " + m.group(1));

        Path skill = Path.of(".claude/skills/stalkerzone-dev/SKILL.md");
        ck(Files.exists(skill), "навык " + skill);
        if (Files.exists(skill)) {
            String s = Files.readString(skill).replace("\r\n", "\n");
            ck(s.startsWith("---") && s.matches("(?s)---\\s*\\nname: \"?stalkerzone-dev\"?\\s*\\n.*"), "у навыка заголовок с name: stalkerzone-dev");
        }

        for (String d : DOCS) {
            Path p = Path.of("docs/claude", d + ".md");
            boolean ok = Files.exists(p) && Files.size(p) > 500;
            ck(ok, "docs/claude/" + d + ".md есть и не пустой");
        }
        if (Files.exists(Path.of("docs/claude/02-prompts.md")))
            ck(Files.readString(Path.of("docs/claude/02-prompts.md")).replace("\r\n", "\n").contains("### Промпт «Программист»"),
               "в 02-prompts.md есть ролевой промпт «Программист»");

        // данных игры GSC в репозитории нет
        try (var s = Files.walk(Path.of("."))) {
            long szl = s.filter(p -> !p.startsWith("./.git") && !p.startsWith("./build") && !p.startsWith("./run")
                    && p.toString().endsWith(".szl")).count();
            ck(szl == 0, "в репозитории нет *.szl");
        }
        System.exit(f > 0 ? 1 : 0);
    }
}
