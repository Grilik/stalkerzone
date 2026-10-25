package ru.stalkerzone.compat;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Кладёт ганпак «Оружие Зоны» (из ресурсов мода, папка szguns_pack) в .minecraft/tacz/stalkerzone_guns,
 * откуда его читает TaCZ. Вызывается из конструктора мода, до того как TaCZ сканирует паки.
 * Пак перезаписывается, только если в моде лежит другая версия (по files.txt).
 */
public final class GunPackInstaller {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String DIR = "stalkerzone_guns";

    private GunPackInstaller() {}

    public static void install() {
        if (!Tacz.loaded()) return;
        try {
            List<String> files = readList();
            if (files.isEmpty()) return;
            Path target = FMLPaths.GAMEDIR.get().resolve("tacz").resolve(DIR);
            String stamp = String.valueOf(String.join("\n", files).hashCode()) + ":" + files.size() + ":" + jarStamp();
            Path stampFile = target.resolve(".installed");
            if (Files.exists(stampFile) && Files.readString(stampFile).trim().equals(stamp)) return;
            int n = 0;
            for (String f : files) {
                try (InputStream in = GunPackInstaller.class.getResourceAsStream("/szguns_pack/" + f)) {
                    if (in == null) continue;
                    Path out = target.resolve(f);
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    n++;
                }
            }
            Files.writeString(stampFile, stamp);
            LOG.info("Ганпак «Оружие Зоны» установлен в {} ({} файлов)", target, n);
        } catch (Exception e) {
            LOG.warn("Не удалось установить ганпак «Оружие Зоны»: {}", e.toString());
        }
    }

    private static String jarStamp() {
        try (InputStream in = GunPackInstaller.class.getResourceAsStream("/szguns_pack/assets/szguns/textures/gun/uv/zone_guns.png")) {
            return in == null ? "0" : String.valueOf(in.readAllBytes().length);
        } catch (Exception e) {
            return "0";
        }
    }

    private static List<String> readList() throws Exception {
        List<String> out = new ArrayList<>();
        try (InputStream in = GunPackInstaller.class.getResourceAsStream("/szguns_pack/files.txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.contains("..")) out.add(line);
            }
        }
        return out;
    }
}
