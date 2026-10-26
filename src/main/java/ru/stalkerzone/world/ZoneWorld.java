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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.sim.ZoneScenario;

/** Мир Зоны: границы, точка появления, возврат игроков с края. Шкала и границы берутся из карты симуляции. */
public final class ZoneWorld {
    private static final String ENTERED_TAG = "stalkerzone_entered";

    private static ZoneBounds bounds;
    private static Node kordon;

    private ZoneWorld() {}

    private static void init() {
        if (bounds != null) return;
        // координаты локаций статичны и не зависят от seed
        Simulation s = ZoneScenario.create(0);
        bounds = ZoneBounds.fromNodes(s.nodes);
        kordon = s.nodes.get(0);
    }

    public static ZoneBounds bounds() { init(); return bounds; }

    /** Точка появления в блоках: Кордон. */
    public static int[] spawnXZ() { init(); return new int[]{kordon.x * ZoneBounds.SCALE, kordon.z * ZoneBounds.SCALE}; }

    public static final class Events {
        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load e) {
            if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
            ChunkAccess chunk = e.getChunk();
            ZoneBounds b = bounds();
            ru.stalkerzone.block.ObjPropBlock.worldgen(() -> {
                WorldDecorator.decorate(chunk, b, level);
                if (b.touchesChunk(chunk.getPos().x, chunk.getPos().z)) WallBuilder.build(chunk, b);
            });
        }

        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent e) {
            MinecraftServer server = e.getServer();
            ServerLevel level = server.overworld();
            ZoneBounds b = bounds();
            // запасная граница: квадрат вокруг прямоугольника, на случай если стена в каком-то чанке не построилась
            WorldBorder wb = level.getWorldBorder();
            wb.setCenter(b.centerX(), b.centerZ());
            wb.setSize(b.enclosingSquare());
            wb.setWarningBlocks(4);
            int[] sp = spawnXZ();
            level.setDefaultSpawnPos(SpawnFinder.safeNear(level, b, sp[0], sp[1]), 0f);
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
            int[] s = spawnXZ();
            BlockPos pos = SpawnFinder.safeNear(level, bounds(), s[0], s[1]);
            sp.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
            sp.setRespawnPosition(Level.OVERWORLD, pos, 0f, true, false);
            // стартовый набор новичка: КПК и флешка
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(ru.stalkerzone.item.ModItems.PDA.get()));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(ru.stalkerzone.item.ModItems.FLASH_DRIVE.get()));
            // v1.14: как у новичка у Сидоровича — горсть болтов и простенький «Отклик»
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(ru.stalkerzone.item.ModItems.BOLT.get(), 24));
            sp.getInventory().add(new net.minecraft.world.item.ItemStack(ru.stalkerzone.item.ModItems.DETECTORS[0].get()));
        }

        /** Возврат игрока, оказавшегося за забором (старые чанки, команды, баги). Раз в секунду. */
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 20 != 0 || sp.level().dimension() != Level.OVERWORLD) return;
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
