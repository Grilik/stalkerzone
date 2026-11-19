import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import ru.stalkerzone.world.golden.GoldenCopy;
import ru.stalkerzone.zonemap.atlas.Json;

/**
 * Э1-11А: золотая копия TheZone vN и независимые миры из неё.
 * Целостность (MD5, точный состав), откат к целой версии, атомарное создание,
 * очистка при ошибке, независимость миров от эталона, манифест THEZONE (Э1-09),
 * своя история симуляции (data/stalkerzone_sim.dat в мир не попадает).
 */
public class GoldenCopyTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }

    static Path tmpRoot;

    /** Синтетический «мир»: region/entities/level.dat/data — достаточно для механики копирования. */
    static void buildWorld(Path dir, byte mark) throws Exception {
        Files.createDirectories(dir.resolve("region"));
        Files.createDirectories(dir.resolve("entities"));
        Files.createDirectories(dir.resolve("data"));
        byte[] region = new byte[8192];
        for (int i = 0; i < region.length; i++) region[i] = (byte) (i * 31 + mark);
        Files.write(dir.resolve("region/r.0.0.mca"), region);
        Files.write(dir.resolve("region/r.-13.0.mca"), region);
        Files.write(dir.resolve("entities/r.0.0.mca"), new byte[] {mark, 1, 2, 3});
        Files.write(dir.resolve("level.dat"), new byte[] {31, (byte) 139, 8, 0, mark});
        Files.write(dir.resolve("data/idcounts.dat"), new byte[] {0, 0, 0, mark});
    }

    static void copyTree(Path src, Path dst) throws Exception {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path to = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(to);
                else { Files.createDirectories(to.getParent()); Files.copy(p, to); }
            }
        }
    }

    /** Версия золотой копии из синтетического мира: файлы + checksums.md5 (Э1-03) + golden.json. */
    static Path makeGolden(Path root, int n, byte mark) throws Exception {
        Path src = tmpRoot.resolve("src-v" + n);
        buildWorld(src, mark);
        Path v = GoldenCopy.versionDir(root, n);
        copyTree(src, v);
        GoldenCopy.writeChecksums(v);
        Files.writeString(v.resolve("golden.json"), "{\"version\": " + n + "}\n");
        return v;
    }

    static void deleteTree(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
        }
    }

    static boolean noTmp(Path saves) throws Exception {
        try (Stream<Path> s = Files.list(saves)) {
            return s.noneMatch(p -> p.getFileName().toString().contains(".tmp-"));
        }
    }

    public static void main(String[] a) throws Exception {
        tmpRoot = Files.createTempDirectory("sz-golden-test-");
        try {
            Path golden = tmpRoot.resolve("golden");
            Path saves = tmpRoot.resolve("saves");
            Files.createDirectories(saves);

            // версии и целостность
            ck(GoldenCopy.versions(golden).isEmpty(), "пустой каталог: версий нет");
            Path v1 = makeGolden(golden, 1, (byte) 1);
            Path v2 = makeGolden(golden, 2, (byte) 2);
            Files.createDirectories(golden.resolve("не-версия"));
            ck(GoldenCopy.versions(golden).equals(List.of(1, 2)), "versions: [1, 2], посторонние каталоги игнорируются");
            ck(GoldenCopy.verify(v1) && GoldenCopy.verify(v2), "обе версии целые (MD5 + состав)");
            ck(GoldenCopy.latestVerified(golden) == 2, "старшая целая версия — v2");

            byte[] before = Files.readAllBytes(v2.resolve("region/r.0.0.mca"));
            Files.write(v2.resolve("region/r.0.0.mca"), new byte[] {9, 9, 9});
            ck(!GoldenCopy.verify(v2), "повреждённый файл: версия не целая");
            ck(GoldenCopy.latestVerified(golden) == 1, "повреждённая старшая: откат к предыдущей целой v1");
            Files.write(v2.resolve("region/r.0.0.mca"), before);
            ck(GoldenCopy.verify(v2), "восстановленный файл: версия снова целая");

            Path extra = v2.resolve("region/лишний.mca");
            Files.write(extra, new byte[] {1});
            ck(!GoldenCopy.verify(v2), "лишний файл вне checksums: версия не целая (противоречащих эталонов нет)");
            Files.delete(extra);
            Files.delete(v2.resolve("entities/r.0.0.mca"));
            ck(!GoldenCopy.verify(v2), "неполная копия (нет файла): версия не целая");
            Files.write(v2.resolve("entities/r.0.0.mca"), new byte[] {2, 1, 2, 3});
            ck(GoldenCopy.verify(v2), "состав восстановлен: версия целая");

            // создание мира из старшей целой
            GoldenCopy.Created c = GoldenCopy.createWorld(golden, saves, "Зона");
            ck(c.goldenVersion() == 2 && Files.isDirectory(c.worldDir()), "мир создан из v2");
            ck(GoldenCopy.md5(c.worldDir().resolve("region/r.0.0.mca")).equals(GoldenCopy.md5(v2.resolve("region/r.0.0.mca"))),
                    "файлы мира байт-в-байт из эталона");
            ck(!Files.exists(c.worldDir().resolve(GoldenCopy.CHECKSUMS)) && !Files.exists(c.worldDir().resolve(GoldenCopy.META)),
                    "метаданные эталона (checksums.md5/golden.json) в мир не копируются");
            String manifest = Files.readString(c.worldDir().resolve(GoldenCopy.WORLD_MANIFEST));
            Map<String, Object> mj = Json.obj(Json.parse(manifest));
            ck("thezone".equals(Json.str(mj.get("mode"))), "манифест: mode=thezone (режим Э1-09)");
            ck(Json.num(mj.get("goldenVersion")) == 2, "манифест: происхождение golden v2 записано");
            ck(noTmp(saves), "временных каталогов не осталось");

            // независимость: правки мира не касаются эталона и других миров
            GoldenCopy.Created c2 = GoldenCopy.createWorld(golden, saves, "Зона (2)");
            Files.write(c.worldDir().resolve("region/r.0.0.mca"), new byte[] {7, 7, 7});
            ck(GoldenCopy.verify(v2), "правка мира не изменила эталон");
            ck(GoldenCopy.md5(c2.worldDir().resolve("region/r.0.0.mca")).equals(GoldenCopy.md5(v2.resolve("region/r.0.0.mca"))),
                    "второй мир не зависит от правок первого");
            Files.write(c2.worldDir().resolve("level.dat"), new byte[] {0});
            ck(GoldenCopy.verify(v1) && GoldenCopy.verify(v2), "повторные создания и правки миров: эталоны целые");

            // существующий мир не перезаписывается
            byte[] keep = Files.readAllBytes(c.worldDir().resolve("level.dat"));
            boolean refused = false;
            try { GoldenCopy.createWorld(golden, saves, "Зона"); } catch (java.io.IOException e) { refused = true; }
            ck(refused, "существующий мир: создание отказано");
            ck(java.util.Arrays.equals(keep, Files.readAllBytes(c.worldDir().resolve("level.dat"))), "существующий мир не тронут");
            ck(noTmp(saves), "после отказа временных каталогов нет");

            // ошибка копирования: повреждённый эталон — мир не создан, безопасно, повтор возможен
            Path bad = tmpRoot.resolve("bad-golden");
            Path bv = makeGolden(bad, 1, (byte) 3);
            Files.write(bv.resolve("level.dat"), new byte[] {0, 0});
            boolean failed = false;
            try { GoldenCopy.createWorld(bad, saves, "ИзПовреждённой"); } catch (java.io.IOException e) { failed = true; }
            ck(failed && !Files.exists(saves.resolve("ИзПовреждённой")), "повреждённый эталон: мир не создан");
            ck(noTmp(saves), "после ошибки временный каталог удалён (безопасное восстановление)");
            Files.write(bv.resolve("level.dat"), new byte[] {31, (byte) 139, 8, 0, 3});
            GoldenCopy.Created retry = GoldenCopy.createWorld(bad, saves, "ИзПовреждённой");
            ck(Files.isDirectory(retry.worldDir()), "после починки эталона создание проходит");

            // своя история симуляции: сохранение симуляции в новый мир не попадает
            Path withSim = tmpRoot.resolve("sim-golden");
            Path sv = makeGolden(withSim, 1, (byte) 4);
            Files.write(sv.resolve("data/stalkerzone_sim.dat"), new byte[] {5, 5, 5});
            GoldenCopy.writeChecksums(sv);
            GoldenCopy.Created cs = GoldenCopy.createWorld(withSim, saves, "БезЧужойИстории");
            ck(!Files.exists(cs.worldDir().resolve("data/stalkerzone_sim.dat")),
                    "data/stalkerzone_sim.dat эталона в мир не попадает — история у мира своя");
            ck(Files.exists(cs.worldDir().resolve("data/idcounts.dat")), "прочие данные мира на месте");

            // имена миров
            ck(GoldenCopy.sanitizeName("a/b:c*d?\"e<f>g|h\\i").equals("a_b_c_d__e_f_g_h_i"), "запрещённые символы заменяются (каждый — на «_»)");
            ck(GoldenCopy.sanitizeName("  ").equals("Зона") && GoldenCopy.sanitizeName("имя.").equals("имя"), "пустое/точка в конце нормализуются");
            Path empty = tmpRoot.resolve("empty-saves");
            Files.createDirectories(empty);
            ck(GoldenCopy.uniqueName(empty, "Зона").equals("Зона"), "свободное имя берётся как есть");
            Files.createDirectories(empty.resolve("Зона"));
            ck(GoldenCopy.uniqueName(empty, "Зона").equals("Зона (2)"), "занятое имя: суффикс (2)");
            Files.createDirectories(empty.resolve("Зона (2)"));
            ck(GoldenCopy.uniqueName(empty, "Зона").equals("Зона (3)"), "заняты оба: суффикс (3)");
            boolean badName = false;
            try { GoldenCopy.checkFolderName("../побег"); } catch (IllegalArgumentException e) { badName = true; }
            ck(badName, "имя с путевыми символами отклоняется");
            badName = false;
            try { GoldenCopy.checkFolderName("  "); } catch (IllegalArgumentException e) { badName = true; }
            ck(badName, "пустое имя отклоняется");
            ck(GoldenCopy.checkFolderName(" Зона ").equals("Зона"), "имя обрезается от пробелов");

            // полностью повреждённый каталог копий
            Files.write(v1.resolve("level.dat"), new byte[] {0});
            Files.write(v2.resolve("level.dat"), new byte[] {0});
            boolean none = false;
            try { GoldenCopy.latestVerified(golden); } catch (java.io.IOException e) { none = true; }
            ck(none, "целых версий нет: отказ с понятной ошибкой");
        } finally {
            deleteTree(tmpRoot);
        }
        System.out.println(f == 0 ? "GoldenCopyTest: всё зелёное" : "GoldenCopyTest: ПРОВАЛ " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
