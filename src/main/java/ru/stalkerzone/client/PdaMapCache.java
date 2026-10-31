package ru.stalkerzone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.stalkerzone.StalkerZoneMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Подложка карты КПК (карточка 015): цвет верхнего блока по сетке 4×4 блока.
 * Собирается на клиенте по мере исследования (туман войны — открываются только пройденные места)
 * и кешируется в {@code stalkerzone/pda_map_&lt;мир&gt;.png}. Начало сетки задаёт срез (строка B снимка),
 * поэтому у каждой локации своя подложка, а не только у Кордона.
 */
@Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, value = Dist.CLIENT)
public final class PdaMapCache {
    /** Сторона клетки подложки, блоков. */
    public static final int STEP = 4;
    /** Радиус сбора вокруг игрока, блоков. */
    private static final int RADIUS = 96;
    /** Сбор раз в столько тиков; сохранение файла раз в столько тиков. */
    private static final int SAMPLE_EVERY = 10, SAVE_EVERY = 1200;

    private static final Map<Long, Integer> TILES = new HashMap<>();
    private static String world = "";
    /** Левый верхний угол подложки в клетках; Integer.MIN_VALUE — срез ещё не наблюдался. */
    private static int originX = Integer.MIN_VALUE, originZ;
    private static int ticks;

    private PdaMapCache() {}

    /** Цвет открытой клетки (ARGB) или 0, если место ещё не исследовано. */
    public static int color(int gx, int gz) {
        Integer c = TILES.get(key(gx, gz));
        return c == null ? 0 : c;
    }

    /** Сколько клеток уже открыто — для легенды карты. */
    public static int known() { return TILES.size(); }

    /** Границы среза из снимка КПК задают начало сетки: x — влево, z — вверх. */
    public static void observe(int minX, int minZ) {
        int gx = Math.floorDiv(minX, STEP), gz = Math.floorDiv(minZ, STEP);
        if (gx == originX && gz == originZ) return;
        if (originX != Integer.MIN_VALUE) { save(); TILES.clear(); }
        originX = gx;
        originZ = gz;
        load();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (originX != Integer.MIN_VALUE && !TILES.isEmpty()) save();
            TILES.clear();
            world = "";
            originX = Integer.MIN_VALUE;
            return;
        }
        String now = worldName(mc);
        if (!now.equals(world)) {
            if (originX != Integer.MIN_VALUE && !TILES.isEmpty()) save();
            TILES.clear();
            originX = Integer.MIN_VALUE;
            world = now;
        }
        if (++ticks % SAMPLE_EVERY != 0) return;
        sample(level);
        if (ticks % SAVE_EVERY == 0) save();
    }

    /** Снять цвета верхнего блока вокруг игрока: только загруженные чанки, неоткрытые клетки пропускаем. */
    private static void sample(ClientLevel level) {
        int px = (int) Minecraft.getInstance().player.getX(), pz = (int) Minecraft.getInstance().player.getZ();
        int g0x = Math.floorDiv(px - RADIUS, STEP), g1x = Math.floorDiv(px + RADIUS, STEP);
        int g0z = Math.floorDiv(pz - RADIUS, STEP), g1z = Math.floorDiv(pz + RADIUS, STEP);
        for (int gx = g0x; gx <= g1x; gx++) {
            for (int gz = g0z; gz <= g1z; gz++) {
                long k = key(gx, gz);
                if (TILES.containsKey(k)) continue;
                int wx = gx * STEP + STEP / 2, wz = gz * STEP + STEP / 2;
                if (!level.hasChunk(wx >> 4, wz >> 4)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                if (y <= level.getMinBuildHeight()) continue;
                BlockPos pos = new BlockPos(wx, y - 1, wz);
                BlockState st = level.getBlockState(pos);
                MapColor col = st.getMapColor(level, pos);
                TILES.put(k, 0xFF000000 | (col == null ? 0x8A8A60 : col.col));
            }
        }
    }

    /** Имя мира для имени файла: у одиночной игры — имя мира, на сервере — имя сервера. */
    private static String worldName(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            String n = mc.getSingleplayerServer().getWorldData().getLevelName();
            if (n != null && !n.isEmpty()) return n;
        }
        if (mc.getCurrentServer() != null && mc.getCurrentServer().name != null) return mc.getCurrentServer().name;
        return "zone";
    }

    private static String safe() {
        return world.isEmpty() ? "zone" : world.replaceAll("[^\\p{L}\\p{N}_ -]", "_");
    }

    private static Path file() {
        return new File(Minecraft.getInstance().gameDirectory, "stalkerzone").toPath().resolve("pda_map_" + safe() + ".png");
    }

    /** Прочитать кеш: чужие (уже собранные в этой сессии) клетки не перетираем. */
    private static void load() {
        if (originX == Integer.MIN_VALUE) return;
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try {
            BufferedImage img = ImageIO.read(f.toFile());
            if (img == null) return;
            for (int x = 0; x < img.getWidth(); x++) {
                for (int z = 0; z < img.getHeight(); z++) {
                    int argb = img.getRGB(x, z);
                    if ((argb >>> 24) == 0) continue;
                    TILES.putIfAbsent(key(originX + x, originZ + z), argb);
                }
            }
        } catch (Exception e) {
            System.err.println("[stalkerzone] подложка КПК не прочитана: " + e);
        }
    }

    /** Записать кеш: файл тянется от начала сетки, поэтому клетки до начала отбрасываем. */
    private static void save() {
        if (originX == Integer.MIN_VALUE || world.isEmpty() || TILES.isEmpty()) return;
        int w = 0, h = 0;
        for (long k : TILES.keySet()) {
            int gx = (int) (k >> 32), gz = (int) k;
            w = Math.max(w, gx - originX + 1);
            h = Math.max(h, gz - originZ + 1);
        }
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return;
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (Map.Entry<Long, Integer> en : TILES.entrySet()) {
                int x = (int) (en.getKey() >> 32) - originX, z = (int) (long) en.getKey() - originZ;
                if (x < 0 || z < 0 || x >= w || z >= h) continue;
                img.setRGB(x, z, en.getValue());
            }
            Path dir = new File(Minecraft.getInstance().gameDirectory, "stalkerzone").toPath();
            Files.createDirectories(dir);
            ImageIO.write(img, "png", dir.resolve("pda_map_" + safe() + ".png").toFile());
        } catch (Exception e) {
            System.err.println("[stalkerzone] подложка КПК не сохранена: " + e);
        }
    }

    private static long key(int gx, int gz) { return ((long) gx << 32) ^ (gz & 0xFFFFFFFFL); }

}
