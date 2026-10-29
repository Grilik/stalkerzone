package ru.stalkerzone.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.item.ModItems;

import java.util.EnumMap;
import java.util.Map;

/**
 * Блоки Зоны (v1.17): стены, кровля, полы, заборы, окна и двери с текстурами в духе оригинала. У каждого материала
 * полный блок, ступени и плита; у части — щит (люк), ограда, панель или дверь. Текстуры в моде свои; личный ресурспак
 * игрока (tools/xray/make_pack.py) подменяет их текстурами из его копии игры.
 */
public final class ZoneBlocks {
    public static final Map<ZoneMat, RegistryObject<Block>> FULL = new EnumMap<>(ZoneMat.class), STAIRS = new EnumMap<>(ZoneMat.class),
            SLAB = new EnumMap<>(ZoneMat.class), TRAPDOOR = new EnumMap<>(ZoneMat.class), FENCE = new EnumMap<>(ZoneMat.class),
            PANE = new EnumMap<>(ZoneMat.class), DOOR = new EnumMap<>(ZoneMat.class);

    private ZoneBlocks() {}

    private static BlockBehaviour.Properties props(ZoneMat m) {
        return switch (m.kind) {
            case WOOD -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f, 2.5f).sound(SoundType.WOOD);
            case METAL -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL);
            case GLASS -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false);
            default -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.8f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE);
        };
    }

    private static RegistryObject<Block> reg(String name, java.util.function.Supplier<Block> s) {
        RegistryObject<Block> b = ModBlocks.BLOCKS.register(name, s);
        ModItems.ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        ModBlocks.DECOR.put(name, b);
        return b;
    }

    /** Регистрация (вызывается до подключения реестров). */
    public static void init() {
        for (ZoneMat m : ZoneMat.values()) {
            String n = "zm_" + m.key;
            RegistryObject<Block> full = reg(n, () -> m.kind == ZoneMat.Kind.GLASS ? new GlassBlock(props(m)) : new Block(props(m)));
            FULL.put(m, full);
            STAIRS.put(m, reg(n + "_stairs", () -> new StairBlock(() -> full.get().defaultBlockState(), props(m))));
            SLAB.put(m, reg(n + "_slab", () -> new SlabBlock(props(m))));
            BlockSetType set = m.kind == ZoneMat.Kind.METAL ? BlockSetType.IRON : BlockSetType.SPRUCE;
            if (m.trapdoor) TRAPDOOR.put(m, reg(n + "_trapdoor", () -> new TrapDoorBlock(props(m).noOcclusion(), set)));
            if (m.fence) FENCE.put(m, reg(n + "_fence", () -> new FenceBlock(props(m).noOcclusion())));
            if (m.pane) PANE.put(m, reg(n + "_pane", () -> new IronBarsBlock(props(m).noOcclusion())));
            if (m.door) DOOR.put(m, reg(n + "_door", () -> new DoorBlock(props(m).noOcclusion(), set)));
        }
    }

    public static net.minecraft.world.level.block.state.BlockState full(ZoneMat m) { return FULL.get(m).get().defaultBlockState(); }
}
