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

    /** v1.14: брошенный болт. */
    public static final RegistryObject<EntityType<ru.stalkerzone.anomaly.BoltEntity>> BOLT = ENTITIES.register("bolt",
            () -> EntityType.Builder.<ru.stalkerzone.anomaly.BoltEntity>of(ru.stalkerzone.anomaly.BoltEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("bolt"));

    private ModEntities() {}
}
