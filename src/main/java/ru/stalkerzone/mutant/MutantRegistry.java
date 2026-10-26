package ru.stalkerzone.mutant;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.npc.ModEntities;

/** Регистрация мутантов, их частей и яиц призыва (для проверки в творческом режиме). */
public final class MutantRegistry {
    @SuppressWarnings("unchecked")
    public static final RegistryObject<EntityType<MutantEntity>>[] TYPES = new RegistryObject[MutantType.ALL.length];
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] PARTS = new RegistryObject[MutantType.ALL.length];
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] EGGS = new RegistryObject[MutantType.ALL.length];

    private static final int[][] EGG_COLORS = {
            {0x6b5a48, 0x2a221c}, {0xc9a08a, 0x7a3b33}, {0x5a4636, 0x2b211a}, {0x6f6a58, 0x3a3a30},
            {0x7a3e36, 0x2b1714}, {0x4a3a33, 0x8a2e22}, {0xb7a08f, 0x6a4f86},
            {0x8a6a48, 0x3a2a1c}, {0x7a7088, 0x3a304a}, {0x5a5550, 0x2a2622}, {0x6a5038, 0x2a1a10}, {0xb8c8e0, 0x6080b0}, {0xa08878, 0x503830}};

    static {
        for (MutantType t : MutantType.ALL) {
            int i = t.ordinal();
            TYPES[i] = ModEntities.ENTITIES.register(t.id, () -> EntityType.Builder
                    .<MutantEntity>of((type, level) -> new MutantEntity(type, level, t), MobCategory.MONSTER)
                    .sized(t.width, t.height)
                    .clientTrackingRange(10)
                    .build(t.id));
            PARTS[i] = ModItems.ITEMS.register(t.partId, () -> new MutantPartItem(t));
            EGGS[i] = ModItems.ITEMS.register(t.id + "_spawn_egg",
                    () -> new ForgeSpawnEggItem(TYPES[i], EGG_COLORS[i][0], EGG_COLORS[i][1], new Item.Properties()));
        }
    }

    private MutantRegistry() {}

    /** Вызвать из конструктора мода до регистрации шин, чтобы записи попали в реестры. */
    public static void init() {}

    public static MutantType typeOf(EntityType<?> et) {
        for (MutantType t : MutantType.ALL) if (TYPES[t.ordinal()].get() == et) return t;
        return null;
    }
}
