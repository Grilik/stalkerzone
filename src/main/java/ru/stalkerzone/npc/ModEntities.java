package ru.stalkerzone.npc;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, StalkerZoneMod.MOD_ID);

    public static final RegistryObject<EntityType<StalkerEntity>> STALKER = ENTITIES.register("stalker",
            () -> EntityType.Builder.of(StalkerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("stalker"));

    private ModEntities() {}
}
