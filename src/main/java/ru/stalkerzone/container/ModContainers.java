package ru.stalkerzone.container;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.block.ModBlocks;
import ru.stalkerzone.item.ModItems;

import java.util.EnumMap;
import java.util.Map;

/** Э1-17: блоки, предметы и блок-сущность контейнеров мода ({@code stalkerzone:<kind.id>}). */
public final class ModContainers {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StalkerZoneMod.MOD_ID);
    public static final Map<ContainerKind, RegistryObject<Block>> BLOCKS = new EnumMap<>(ContainerKind.class);
    public static final Map<ContainerKind, RegistryObject<Item>> ITEMS = new EnumMap<>(ContainerKind.class);

    static {
        for (ContainerKind k : ContainerKind.values()) {
            RegistryObject<Block> b = ModBlocks.BLOCKS.register(k.id, () -> k.tall()
                    ? new TallZoneContainerBlock(props(k), k) : new ZoneContainerBlock(props(k), k));
            BLOCKS.put(k, b);
            ITEMS.put(k, ModItems.ITEMS.register(k.id, () -> new BlockItem(b.get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<BlockEntityType<ZoneContainerBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("zone_container",
            () -> BlockEntityType.Builder.of(ZoneContainerBlockEntity::new,
                    BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private ModContainers() {}

    private static BlockBehaviour.Properties props(ContainerKind k) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().noOcclusion();
        return switch (k.family) {
            case WOOD -> p.mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(SoundType.WOOD);
            case SAFE -> p.mapColor(MapColor.METAL).strength(8.0f, 1200.0f).sound(SoundType.METAL);
            default -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL);
        };
    }
}
