package ru.stalkerzone.world.golden;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Э1-11А: версионируемая золотая копия TheZone (vN) и создание независимых миров из неё.
 *
 * <p>Формат хранения — конвенции Э1-03 ({@code tools/thezone/backup_world.ps1}): каталог {@code vN/}
 * с файлами мира ({@code region/}, {@code entities/}, {@code level.dat}, …), {@code checksums.md5}
 * («HASH  относительный/путь», MD5 каждого файла) и {@code golden.json} (метаданные версии).
 * Золотую копию создаёт инструмент {@code tools/thezone/golden_copy.py} из рабочей копии TheZone
 * только чтением; исходная TheZone и рабочая копия не изменяются. Этот класс копию не создаёт и не правит.
 *
 * <p>Мир из золотой копии — полная независимая копия: последующие обновления копии на него не влияют,
 * правки в мире не затрагивают эталон. История симуляции не наследуется: {@code data/stalkerzone_sim.dat}
 * в новый мир не попадает (свой simSeed каждого мира — Э1-11В, {@code ZoneData}). Режим THEZONE world.json
 * задаётся манифестом формата Э1-09 ({@code WorldMode}). Создание атомарно: копия собирается во временном
 * каталоге, сверяется по MD5 и переименовывается; при любой ошибке временный каталог удаляется,
 * существующие миры и эталон не трогаются. Целостность: старшая целая версия; повреждённая старшая
 * откатывается к предыдущей целой, противоречащих эталонов нет (номера версий строго возрастают,
 * существующая версия не перезаписывается).
 *
 * <p>Класс без Minecraft и без вывода в stdout (правило LogLinesTest): тест {@code GoldenCopyTest},
 * CLI для инструмента и smoke — {@code tools/thezone/GoldenCopyCli.java}.
 */
public final class GoldenCopy {
    /** Контрольные суммы Э1-03: «HASH  относительный/путь» (MD5, регистр не важен). */
    public static final String CHECKSUMS = "checksums.md5";
    /** Метаданные версии золотой копии (version, created, source, files, bytes, spawn). */
    public static final String META = "golden.json";
    /** Манифест мира (Э1-09): по нему WorldMode определяет режим THEZONE. */
    public static final String WORLD_MANIFEST = "stalkerzone/world.json";
    /** Сохранение симуляции мода: в новый мир не попадает — история у каждого мира своя. */
    private static final String SIM_SAVE = "data/stalkerzone_sim.dat";
    private static final int BUF = 1 << 20;

    private GoldenCopy() {}

    /** Созданный мир: каталог и версия золотой копии, из которой он сделан. */
    public record Created(Path worldDir, int goldenVersion) {}

    /** Номера версий vN в каталоге золотых копий, по возрастанию. */
    public static List<Integer> versions(Path root) {
        List<Integer> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.list(root)) {
            s.filter(Files::isDirectory).forEach(p -> {
                String n = p.getFileName().toString();
                if (n.length() > 1 && n.charAt(0) == 'v' && n.substring(1).chars().allMatch(Character::isDigit)) {
                    try {
                        int v = Integer.parseInt(n.substring(1));
                        if (("v" + v).equals(n)) out.add(v); // только канонические имена: v01 ≠ v1 — не эталон
                    } catch (NumberFormatException ignored) { }
                }
            });
        } catch (IOException ignored) { }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    public static Path versionDir(Path root, int version) { return root.resolve("v" + version); }

    /** Целостность версии: точный состав файлов (кроме checksums.md5/golden.json) и MD5 каждого. */
    public static boolean verify(Path versionDir) {
        return verifyAgainst(versionDir, versionDir.resolve(CHECKSUMS));
    }

    /** Сверка каталога с контрольными суммами: состав файлов точный, MD5 каждого совпадает. */
    public static boolean verifyAgainst(Path dir, Path checksumsFile) {
        try {
            Map<String, String> expected = readChecksums(checksumsFile);
            if (expected.isEmpty()) return false;
            Map<String, Path> actual = worldFiles(dir);
            if (!actual.keySet().equals(expected.keySet())) return false;
            for (Map.Entry<String, String> e : expected.entrySet()) {
                if (!md5(actual.get(e.getKey())).equalsIgnoreCase(e.getValue())) return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Старшая целая версия; при повреждении старшей — предыдущая целая. Если целых нет — IOException. */
    public static int latestVerified(Path root) throws IOException {
        List<Integer> vs = versions(root);
        for (int i = vs.size() - 1; i >= 0; i--) {
            if (verify(versionDir(root, vs.get(i)))) return vs.get(i);
        }
        throw new IOException("нет целой золотой копии в " + root
                + " — создайте vN инструментом: python tools/thezone/golden_copy.py make <рабочая копия> <каталог копий>");
    }

    /**
     * Создать мир из старшей целой золотой копии. Каталог {@code savesDir/folderName} не должен существовать
     * (существующие миры не перезаписываются). Копия собирается во временном каталоге, сверяется по MD5,
     * получает манифест THEZONE и атомарно переименовывается; при ошибке временный каталог удаляется.
     */
    public static Created createWorld(Path goldenRoot, Path savesDir, String folderName) throws IOException {
        String name = checkFolderName(folderName);
        int version = latestVerified(goldenRoot);
        Path src = versionDir(goldenRoot, version);
        Files.createDirectories(savesDir);
        Path target = savesDir.resolve(name);
        if (Files.exists(target)) throw new IOException("мир уже существует: " + target + " (не перезаписываю)");
        Path tmp = savesDir.resolve("." + name + ".tmp-" + System.nanoTime());
        try {
            copyWorldFiles(src, tmp);
            if (!verifyAgainst(tmp, src.resolve(CHECKSUMS))) {
                throw new IOException("копия не прошла сверку MD5: " + name + " (мир не создан, эталон не тронут)");
            }
            Files.deleteIfExists(tmp.resolve(SIM_SAVE));
            writeManifest(tmp, version);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target);
            }
            return new Created(target, version);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tmp);
            if (e instanceof IOException io) throw io;
            throw new IOException("создание мира не удалось: " + e.getMessage(), e);
        }
    }

    /** Имя каталога мира: без запрещённых символов, не пустое, не «.»/«..», не скрытое. */
    public static String checkFolderName(String name) {
        if (name == null) throw new IllegalArgumentException("имя мира не задано");
        String n = name.trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..") || n.startsWith(".")) {
            throw new IllegalArgumentException("недопустимое имя мира: «" + name + "»");
        }
        if (n.chars().anyMatch(c -> "\\/:*?\"<>|".indexOf(c) >= 0)) {
            throw new IllegalArgumentException("недопустимые символы в имени мира: «" + name + "»");
        }
        return n;
    }

    /** Имя для нового мира: запрещённые символы заменяются на «_», при занятости добавляется « (2)», « (3)»… */
    public static String uniqueName(Path savesDir, String base) throws IOException {
        String n = sanitizeName(base);
        if (!Files.exists(savesDir.resolve(n))) return n;
        for (int i = 2; i < 1000; i++) {
            String cand = n + " (" + i + ")";
            if (!Files.exists(savesDir.resolve(cand))) return cand;
        }
        throw new IOException("слишком много миров с именем «" + n + "»");
    }

    /** Замена запрещённых символов и обрезка пробелов; пустое имя → «Зона». */
    public static String sanitizeName(String base) {
        String n = base == null ? "" : base.trim().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        while (n.endsWith(".")) n = n.substring(0, n.length() - 1).trim();
        return n.isEmpty() || n.equals(".") || n.equals("..") ? "Зона" : n;
    }

    /** Контрольные суммы Э1-03 для каталога (кроме самих checksums.md5/golden.json). Для инструмента и тестов. */
    public static void writeChecksums(Path dir) throws IOException {
        Map<String, Path> files = worldFiles(dir);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Path> e : files.entrySet()) sb.append(md5(e.getValue())).append("  ").append(e.getKey()).append('\n');
        Files.writeString(dir.resolve(CHECKSUMS), sb.toString(), StandardCharsets.UTF_8);
    }

    /** MD5 файла, шестнадцатеричный верхний регистр (как Get-FileHash в Э1-03). */
    public static String md5(Path file) throws IOException {
        MessageDigest d;
        try { d = MessageDigest.getInstance("MD5"); } catch (Exception e) { throw new IOException("MD5 недоступен", e); }
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[BUF];
            int n;
            while ((n = in.read(buf)) > 0) d.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : d.digest()) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /** Файлы мира каталога: относительные пути с «/», без checksums.md5 и golden.json. */
    private static Map<String, Path> worldFiles(Path dir) throws IOException {
        Map<String, Path> out = new TreeMap<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (!rel.equals(CHECKSUMS) && !rel.equals(META)) out.put(rel, p);
            });
        }
        return out;
    }

    private static Map<String, String> readChecksums(Path file) throws IOException {
        Map<String, String> out = new TreeMap<>();
        if (!Files.isRegularFile(file)) return out;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IOException("битая строка контрольных сумм: «" + line + "»");
            }
            out.put(parts[1].trim().replace('\\', '/'), parts[0].trim());
        }
        return out;
    }

    private static void copyWorldFiles(Path src, Path dst) throws IOException {
        for (Map.Entry<String, Path> e : worldFiles(src).entrySet()) {
            Path to = dst.resolve(e.getKey());
            Files.createDirectories(to.getParent());
            Files.copy(e.getValue(), to);
        }
    }

    /** Манифест формата Э1-08/Э1-09: режим thezone, атлас, происхождение из золотой копии vN. */
    private static void writeManifest(Path worldDir, int version) throws IOException {
        Path manifest = worldDir.resolve(WORLD_MANIFEST);
        Files.createDirectories(manifest.getParent());
        String json = "{\n \"mode\": \"thezone\",\n \"atlas\": 1,\n \"source\": \"golden v" + version
                + "\",\n \"goldenVersion\": " + version + "\n}\n";
        Files.writeString(manifest, json, StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
