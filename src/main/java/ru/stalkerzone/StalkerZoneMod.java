package ru.stalkerzone;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(StalkerZoneMod.MOD_ID)
public class StalkerZoneMod {
    public static final String MOD_ID = "stalkerzone";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Один день симуляции = 6000 тиков (5 минут реального времени). Позже вынесем в конфиг. */
    public static final int TICKS_PER_SIM_DAY = 6000;

    private boolean auditWarned;

    public StalkerZoneMod() {
        net.minecraftforge.eventbus.api.IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ru.stalkerzone.compat.CompatConfig.load();
        ru.stalkerzone.compat.GunPackInstaller.install();
        ru.stalkerzone.compat.GunQuality.register();
        ru.stalkerzone.mutant.MutantRegistry.init();
        ru.stalkerzone.block.ModBlocks.init();
        ru.stalkerzone.block.ModBlocks.BLOCKS.register(modBus);
        ru.stalkerzone.world.ZoneBiomeSource.SOURCES.register(modBus);
        ru.stalkerzone.world.xray.XrayTerrain.TYPES.register(modBus);
        System.setProperty("stalkerzone.levels", net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("stalkerzone").resolve("levels").toString());
        ru.stalkerzone.net.Net.register();
        ru.stalkerzone.npc.ModEntities.ENTITIES.register(modBus);
        ru.stalkerzone.item.ModItems.ITEMS.register(modBus);
        ru.stalkerzone.item.ModItems.TABS.register(modBus);
        modBus.addListener(ru.stalkerzone.client.ZonePacks::addPacks);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ru.stalkerzone.world.ZoneWorld.Events());
        MinecraftForge.EVENT_BUS.register(new ru.stalkerzone.survival.PlayerNeeds());
        MinecraftForge.EVENT_BUS.register(ru.stalkerzone.survival.Radiation.events());
        MinecraftForge.EVENT_BUS.register(new ru.stalkerzone.gear.Carry());
        MinecraftForge.EVENT_BUS.register(ru.stalkerzone.artifact.Belt.events());
        MinecraftForge.EVENT_BUS.register(new ru.stalkerzone.npc.ZombieManager.Events());
        MinecraftForge.EVENT_BUS.register(new ru.stalkerzone.mutant.MutantManager.Events());
        MinecraftForge.EVENT_BUS.register(new ru.stalkerzone.npc.PlayerFaction.Events());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        ZoneCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        if (server.getTickCount() % 20 == 0) {
            ru.stalkerzone.world.StructureManager.tick(server);
            ru.stalkerzone.world.ZoneMapPlacer.tick(server);
            ru.stalkerzone.npc.NpcManager.tick(server);
            ru.stalkerzone.mutant.MutantManager.tick(server);
            ru.stalkerzone.lab.LabManager.tick(server);
            ru.stalkerzone.world.LandmarkManager.tick(server);
            ru.stalkerzone.npc.ZombieManager.tick(server);
            ru.stalkerzone.npc.MeetingManager.tick(server);
            if (server.getTickCount() % 40 == 0) ru.stalkerzone.anomaly.PsiField.tick(server);
        }

        ru.stalkerzone.lab.LabManager.tickDownloads(server);
        ru.stalkerzone.anomaly.AnomalyManager.tick(server);
        ru.stalkerzone.anomaly.Artifacts.tick(server);
        ru.stalkerzone.pda.ZoneLifeService.tick(server);
        ru.stalkerzone.world.Perimeter.tick(server);
        ru.stalkerzone.npc.Reputation.tick(server);
        ru.stalkerzone.npc.Hermits.tick(server);
        ru.stalkerzone.anomaly.EmissionEvent.tick(server);

        ZoneData data = ZoneData.get(server);
        ++data.tickCounter;
        if (data.tickCounter < TICKS_PER_SIM_DAY) return;
        data.tickCounter = 0;
        data.sim.step();
        ru.stalkerzone.pda.ZoneLifeService.daily(server, data);
        data.setDirty();
        if (data.sim.lastEmissionDay == data.sim.day) ru.stalkerzone.anomaly.EmissionEvent.start(server);

        // закон сохранения должен выполняться всегда; если нет, это баг, о котором надо узнать
        String audit = data.sim.audit();
        if (!audit.isEmpty() && !auditWarned) {
            auditWarned = true;
            LOGGER.error("Аудит экономики Зоны не сошёлся на дне {}:\n{}", data.sim.day, audit);
        }
    }
}
