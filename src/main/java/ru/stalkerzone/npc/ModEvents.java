package ru.stalkerzone.npc;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.stalkerzone.StalkerZoneMod;

/** События шины мода: атрибуты сущностей и клиентский рендер. */
public final class ModEvents {
    private ModEvents() {}

    @Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Common {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent e) {
            e.put(ModEntities.STALKER.get(), StalkerEntity.createAttributes().build());
            for (ru.stalkerzone.mutant.MutantType t : ru.stalkerzone.mutant.MutantType.ALL) {
                e.put(ru.stalkerzone.mutant.MutantRegistry.TYPES[t.ordinal()].get(), ru.stalkerzone.mutant.MutantEntity.attributes(t).build());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                EntityRenderers.register(ModEntities.STALKER.get(), StalkerRenderer::new);
                net.minecraft.client.gui.screens.MenuScreens.<net.minecraft.world.inventory.ChestMenu, ru.stalkerzone.client.DeathBackpackScreen>register(ModMenus.DEATH_BACKPACK.get(),
                        ru.stalkerzone.client.DeathBackpackScreen::new);   // П-10
            });
        }
    }
}
