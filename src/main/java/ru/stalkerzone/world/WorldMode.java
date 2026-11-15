package ru.stalkerzone.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Э1-09 (решение а1): режим мира. {@link #THEZONE} — готовый мир TheZone: мод не пишет блоки ни при загрузке
 * чанков, ни старыми тиковыми писателями (лагеря, площадки, ориентиры, мост) — всё стоит в чанках мира, новые
 * постановки идут только инструментом а4 (Э2-01, Э2-14). {@link #LEGACY} — прежний генерируемый мир.
 *
 * Режим — по манифесту {@code <мир>/stalkerzone/world.json} с {@code "mode": "thezone"}. Защита: мир без
 * манифеста, но с сидом TheZone и регионами на x −6656 (r.-13.*) — тоже THEZONE, с предупреждением в лог.
 */
public enum WorldMode {
    LEGACY, THEZONE;

    /** Сид мира TheZone (Java-конверсия, Э1-01). */
    public static final long THEZONE_SEED = -840995054016536013L;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static volatile WorldMode current = LEGACY;
    private static volatile String reason = "по умолчанию";

    public static WorldMode current() { return current; }

    public static boolean theZone() { return current == THEZONE; }

    public static String reason() { return reason; }

    /** Определить режим до загрузки уровней ({@code ServerAboutToStartEvent}). */
    public static void detect(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        WorldMode m = LEGACY;
        String why = "нет манифеста stalkerzone/world.json";
        Path manifest = root.resolve("stalkerzone").resolve("world.json");
        if (Files.isRegularFile(manifest)) {
            try {
                String mode = modeOf(Files.readString(manifest));
                if ("thezone".equals(mode)) { m = THEZONE; why = "манифест: mode=thezone"; }
                else why = "манифест: mode=" + mode;
            } catch (IOException | RuntimeException e) {
                // манифест есть, но не читается: безопаснее не писать в мир
                m = THEZONE;
                why = "манифест не читается (" + e.getMessage() + ") — включён THEZONE";
                LOGGER.error("[stalkerzone] {}", why);
            }
        } else if (seed(server) == THEZONE_SEED && hasTheZoneRegions(root.resolve("region"))) {
            m = THEZONE;
            why = "без манифеста: сид TheZone и регионы r.-13.* — включён THEZONE без постановок";
            LOGGER.warn("[stalkerzone] Мир похож на TheZone, но манифеста stalkerzone/world.json нет. Режим THEZONE: мод не пишет блоки в мир.");
        }
        current = m;
        reason = why;
        LOGGER.info("[stalkerzone] режим мира {} ({})", m, why);
    }

    /** Значение "mode" из JSON манифеста (без зависимости от формата остальных полей). */
    static String modeOf(String json) {
        com.google.gson.JsonElement e = com.google.gson.JsonParser.parseString(json);
        if (!e.isJsonObject() || !e.getAsJsonObject().has("mode")) return "";
        return e.getAsJsonObject().get("mode").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static long seed(MinecraftServer server) {
        try {
            return server.getWorldData().worldGenOptions().seed();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean hasTheZoneRegions(Path region) {
        if (!Files.isDirectory(region)) return false;
        try (Stream<Path> s = Files.list(region)) {
            return s.anyMatch(p -> p.getFileName().toString().startsWith("r.-13."));
        } catch (IOException e) {
            return false;
        }
    }

    /** Для тестов и команд: принудительно задать режим. */
    public static void force(WorldMode m, String why) { current = m; reason = why; }
}
