package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.Simulation;

/** Мир Зоны: границы, точка появления, возврат игроков с края. Шкала и границы берутся из карты симуляции. */
public final class ZoneWorld {
    private static final String ENTERED_TAG = "stalkerzone_entered";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static ZoneBounds bounds;
    private static Node kordon;

    private ZoneWorld() {}

    /** Э1-09: идёт обработка ChunkEvent.Load мода. Любая запись мода в этот момент в режиме THEZONE — ошибка. */
    public static final ThreadLocal<Boolean> IN_CHUNK_LOAD = ThreadLocal.withInitial(() -> false);
    /** Э1-09: сколько записей мода отклонено в режиме THEZONE (CI и опытная копия ждут 0). */
    public static final java.util.concurrent.atomic.AtomicLong CHUNK_WRITES = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Можно ли старым писателям мода (декор при загрузке, лагеря, площадки, ориентиры, мост) ставить блоки.
     * В THEZONE — нет: всё уже стоит в чанках мира. Запись при загрузке чанка в THEZONE считается и пишется в лог.
     */
    public static boolean legacyWritesAllowed(String who) {
        if (!WorldMode.theZone()) return true;
        if (IN_CHUNK_LOAD.get()) {
            long n = CHUNK_WRITES.incrementAndGet();
            if (n <= 20 || n % 1000 == 0)
                LOGGER.error("[stalkerzone] запись блоков при загрузке чанка в мире TheZone отклонена: {} (всего {})", who, n);
        }
        return false;
    }

    private static void init() {
        if (bounds != null) return;
        // координаты локаций статичны и не зависят от seed
        Simulation s = Scenarios.create(0);
        ZoneBounds nb = ZoneBounds.fromNodes(s.nodes);
        kordon = s.nodes.get(0);
        // v1.18 (карточка 004): в сценарии «Кордон» граница — реальный край уровня l01_escape (points/l01_escape.json)
        if (Scenarios.isKordon(s.scenario)) {
            String levelId = LevelPoints.levelForScenario(Scenarios.KORDON);
            if (levelId != null && ru.stalkerzone.world.xray.LevelRegions.byId(levelId) != null) {
                ZoneBounds kb = LevelPoints.bounds(levelId);
                if (kb != null) {
                    bounds = kb;
                    return;
                }
            }
        }
        // v1.14: уровни оригинала 1:1 шире своих узлов — граница Зоны обходит их с запасом
        int x0 = nb.minX, x1 = nb.maxX, z0 = nb.minZ, z1 = nb.maxZ;
        for (ru.stalkerzone.world.xray.LevelRegion r : ru.stalkerzone.world.xray.LevelRegions.all()) {
            x0 = Math.min(x0, r.x0 - 160); x1 = Math.max(x1, r.x0 + r.w + 160);
            z0 = Math.min(z0, r.z0 - 160); z1 = Math.max(z1, r.z0 + r.d + 160);
        }
        bounds = new ZoneBounds(x0, x1, z0, z1);
    }

    public static ZoneBounds bounds() { init(); return bounds; }

    /** Точка появления в блоках: Кордон. */
    public static int[] spawnXZ() {
        init();
        // v1.18: в Кордоне — у костра деревни новичков (points/l01_escape.json)
        if (Scenarios.isKordon(Scenarios.current())) {
            String levelId = LevelPoints.levelForScenario(Scenarios.KORDON);
            if (levelId != null && ru.stalkerzone.world.xray.LevelRegions.byId(levelId) != null) {
                int[] p = LevelPoints.spawnXZ(levelId);
                if (p != null && bounds.inside(p[0], p[1])) return p;
            }
        }
        return new int[]{kordon.x * ZoneBounds.SCALE, kordon.z * ZoneBounds.SCALE};
    }

    public static final class Events {
        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load e) {
            if (WorldMode.theZone()) return; // Э1-09: в мире TheZone при загрузке чанка мод ничего не делает
            if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
            ChunkAccess chunk = e.getChunk();
            ZoneBounds b = bounds();
            IN_CHUNK_LOAD.set(true);
            try {
                ru.stalkerzone.block.ObjPropBlock.worldgen(() -> {
                    WorldDecorator.decorate(chunk, b, level);
                    if (b.touchesChunk(chunk.getPos().x, chunk.getPos().z)) WallBuilder.build(chunk, b);
                });
            } finally {
                IN_CHUNK_LOAD.set(false);
            }
        }

        /** Э1-09: режим мира — до загрузки уровней и первого ChunkEvent.Load. */
        @SubscribeEvent
        public void onAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent e) {
            CHUNK_WRITES.set(0);
            WorldMode.detect(e.getServer());
        }

        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent e) {
            MinecraftServer server = e.getServer();
            ServerLevel level = server.overworld();
            // Э1-09: в TheZone граница — многоугольник атласа (Э2-10), точка появления — из level.dat
            // (до Э1-11; затем world.spawn атласа). Старый прямоугольник Кордона и SpawnFinder не применяются.
            if (WorldMode.theZone()) return;
            ZoneBounds b = bounds();
            // запасная граница: квадрат вокруг прямоугольника, на случай если стена в каком-то чанке не построилась
            WorldBorder wb = level.getWorldBorder();
            wb.setCenter(b.centerX(), b.centerZ());
            wb.setSize(b.enclosingSquare());
            wb.setWarningBlocks(4);
            int[] sp = spawnXZ();
            level.setDefaultSpawnPos(SpawnFinder.safeNear(level, b, sp[0], sp[1]), 0f);
        }

        /** Э0-08: сводка подмен блоков воздухом (неизвестные состояния в сборщиках) — в лог при остановке; CI ждёт substitutions=0. */
        @SubscribeEvent
        public void onServerStopping(ServerStoppingEvent e) {
            LOGGER.info("[stalkerzone] {}", ZoneMapPlacer.substitutionReport());
            LOGGER.info("[stalkerzone] mode={} chunkWrites={}", WorldMode.current(), CHUNK_WRITES.get());
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            CompoundTag root = sp.getPersistentData();
            CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
            if (persisted.getBoolean(ENTERED_TAG)) return;
            persisted.putBoolean(ENTERED_TAG, true);
            root.put(Player.PERSISTED_NBT_TAG, persisted);

            ServerLevel level = sp.getServer().overworld();
            BlockPos pos;
            if (WorldMode.theZone()) {
                pos = level.getSharedSpawnPos(); // Э1-09: точка появления мира (атлас — Э1-11)
            } else {
                int[] s = spawnXZ();
                pos = SpawnFinder.safeNear(level, bounds(), s[0], s[1]);
            }
            sp.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
            sp.setRespawnPosition(Level.OVERWORLD, pos, 0f, true, false);
            // стартовый набор новичка (карточка 010) — список в ru.stalkerzone.sim.PlayerStart
            ru.stalkerzone.item.StartKit.give(sp);
            // вступление и подсказка про КПК при первом входе
            for (String line : ru.stalkerzone.sim.PlayerStart.INTRO)
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(ru.stalkerzone.sim.PlayerStart.PDA_HINT));
        }

        /** Возврат игрока, оказавшегося за забором (старые чанки, команды, баги). Раз в секунду. */
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 20 != 0 || sp.level().dimension() != Level.OVERWORLD) return;
            if (WorldMode.theZone()) return; // Э1-09: возврат с края в TheZone — по многоугольнику атласа (Э2-10)
            ZoneBounds b = bounds();
            int x = sp.getBlockX(), z = sp.getBlockZ();
            if (b.inside(x, z)) return;
            ServerLevel level = sp.serverLevel();
            BlockPos pos = SpawnFinder.groundAt(level, b.clampX(x), b.clampZ(z));
            sp.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal("За забором Зоны ничего нет."), true);
        }
    }
}
