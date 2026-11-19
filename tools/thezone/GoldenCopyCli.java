import ru.stalkerzone.world.golden.GoldenCopy;

import java.nio.file.Path;
import java.util.List;

/**
 * Э1-11: CLI золотой копии TheZone для инструмента и smoke-проверок (ядро — GoldenCopy, без stdout).
 * Компиляция: javac -cp out -d out tools/thezone/GoldenCopyCli.java
 * java -cp out GoldenCopyCli create <goldenRoot> <savesDir> <имя> | verify <goldenRoot> [N] | list <goldenRoot>
 */
public class GoldenCopyCli {
    public static void main(String[] args) throws Exception {
        if (args.length >= 4 && args[0].equals("create")) {
            GoldenCopy.Created c = GoldenCopy.createWorld(Path.of(args[1]), Path.of(args[2]), args[3]);
            System.out.println("создан: " + c.worldDir() + " (золотая копия v" + c.goldenVersion() + ")");
        } else if (args.length >= 2 && args[0].equals("verify")) {
            Path root = Path.of(args[1]);
            List<Integer> vs = args.length >= 3 ? List.of(Integer.parseInt(args[2])) : GoldenCopy.versions(root);
            boolean ok = !vs.isEmpty();
            for (int v : vs) {
                boolean good = GoldenCopy.verify(GoldenCopy.versionDir(root, v));
                ok &= good;
                System.out.println("v" + v + ": " + (good ? "целая" : "ПОВРЕЖДЕНА"));
            }
            if (!ok) System.exit(1);
        } else if (args.length >= 2 && args[0].equals("list")) {
            Path root = Path.of(args[1]);
            for (int v : GoldenCopy.versions(root)) {
                System.out.println("v" + v + ": " + (GoldenCopy.verify(GoldenCopy.versionDir(root, v)) ? "целая" : "ПОВРЕЖДЕНА"));
            }
        } else {
            System.out.println("Использование: create <goldenRoot> <savesDir> <имя> | verify <goldenRoot> [N] | list <goldenRoot>");
            System.exit(2);
        }
    }
}
