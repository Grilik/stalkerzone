package ru.stalkerzone.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.item.ModItems;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Блоки мода: терминал лаборатории, пропсы Зоны, листва Рыжего леса. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StalkerZoneMod.MOD_ID);

    public static final RegistryObject<Block> LAB_TERMINAL = BLOCKS.register("lab_terminal", () -> new LabTerminalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).lightLevel(s -> 7).sound(SoundType.METAL).noLootTable()));
    public static final RegistryObject<Block> RUSTY_BARREL = BLOCKS.register("rusty_barrel", () -> new PropBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion(), 2, 0, 2, 14, 15, 14));
    public static final RegistryObject<Block> TIRES = BLOCKS.register("tires", () -> new PropBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0f, 2.0f).sound(SoundType.WOOL).noOcclusion(), 1, 0, 1, 15, 8, 15));
    public static final RegistryObject<Block> RED_LEAVES = BLOCKS.register("red_leaves", () -> new LeavesBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2f).randomTicks().sound(SoundType.GRASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    public static final RegistryObject<Item> LAB_TERMINAL_ITEM = ModItems.ITEMS.register("lab_terminal", () -> new BlockItem(LAB_TERMINAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> RUSTY_BARREL_ITEM = ModItems.ITEMS.register("rusty_barrel", () -> new BlockItem(RUSTY_BARREL.get(), new Item.Properties()));
    public static final RegistryObject<Item> TIRES_ITEM = ModItems.ITEMS.register("tires", () -> new BlockItem(TIRES.get(), new Item.Properties()));
    public static final RegistryObject<Item> RED_LEAVES_ITEM = ModItems.ITEMS.register("red_leaves", () -> new BlockItem(RED_LEAVES.get(), new Item.Properties()));

    // ------------------------------------------------------------------ декор и техника (v1.11)

    /** Все декоративные блоки по имени (их ставят строители готовой карты строкой «stalkerzone:имя»). */
    public static final Map<String, RegistryObject<Block>> DECOR = new LinkedHashMap<>();
    public static final String[] PAINTS = {"red", "white", "blue", "cream", "olive", "orange", "green", "beige"};

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.8f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties metal(MapColor c) {
        return BlockBehaviour.Properties.of().mapColor(c).strength(2.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f, 2.0f).sound(SoundType.WOOD);
    }

    private static RegistryObject<Block> decor(String name, Supplier<Block> s) {
        RegistryObject<Block> b = BLOCKS.register(name, s);
        DECOR.put(name, b);
        ModItems.ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    /** Блок с полублоком и ступенью из того же материала. */
    private static void family(String name, Supplier<BlockBehaviour.Properties> p) {
        RegistryObject<Block> full = decor(name, () -> new Block(p.get()));
        Supplier<BlockState> st = () -> full.get().defaultBlockState();
        decor(name + "_stairs", () -> new StairBlock(st, p.get()));
        decor(name + "_slab", () -> new SlabBlock(p.get()));
    }

    static {
        decor("whitewash", () -> new Block(stone()));
        decor("whitewash_damaged", () -> new Block(stone()));
        decor("concrete_fence", () -> new Block(stone()));
        decor("slate_roof", () -> new Block(stone()));
        Supplier<BlockState> slate = () -> DECOR.get("slate_roof").get().defaultBlockState();
        decor("slate_roof_stairs", () -> new StairBlock(slate, stone()));
        decor("slate_roof_slab", () -> new SlabBlock(stone()));
        for (String c : PAINTS) {
            MapColor mc = switch (c) {
                case "red" -> MapColor.COLOR_RED; case "white" -> MapColor.SNOW; case "blue" -> MapColor.COLOR_BLUE;
                case "cream", "beige" -> MapColor.SAND; case "olive", "green" -> MapColor.COLOR_GREEN; default -> MapColor.COLOR_ORANGE;
            };
            family("car_paint_" + c, () -> metal(mc));
        }
        decor("car_glass", () -> new GlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS)
                .noOcclusion().isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
        decor("car_grille", () -> new Block(metal(MapColor.METAL)));
        decor("car_headlight", () -> new Block(metal(MapColor.METAL)));
        decor("car_wheel", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0f, 2.0f).sound(SoundType.WOOL)));
        decor("wooden_crate", () -> new Block(wood()));
        decor("military_crate", () -> new Block(wood()));
        decor("medical_crate", () -> new Block(wood()));
        decor("radioactive_barrel", () -> new PropBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f, 6.0f)
                .sound(SoundType.METAL).noOcclusion().lightLevel(s -> 2), 2, 0, 2, 14, 15, 14));
    }

    // ------------------------------------------------------------------ 3D-пропсы и техника на OBJ-моделях (v1.12)

    public static final RegistryObject<Block> HITBOX = BLOCKS.register("prop_hitbox", () -> new HitboxBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(2.0f, 6.0f).noOcclusion().noLootTable()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, t) -> false)));
    public static final Map<String, RegistryObject<Block>> PROPS3D = new LinkedHashMap<>();

    static {
        for (GeneratedProps.Prop pr : GeneratedProps.PROPS) {
            boolean soft = pr.name().contains("sandbag");
            boolean wood = pr.name().contains("spool") || pr.name().contains("bench");
            boolean stoneLike = pr.name().contains("concrete") || pr.name().contains("jersey") || pr.name().contains("fbs") || pr.name().contains("rubble");
            PROPS3D.put(pr.name(), decor(pr.name(), () -> new ObjPropBlock(BlockBehaviour.Properties.of()
                    .mapColor(stoneLike ? MapColor.STONE : wood ? MapColor.WOOD : MapColor.METAL)
                    .strength(soft ? 1.0f : 2.5f, 6.0f).sound(soft ? SoundType.WOOL : wood ? SoundType.WOOD : stoneLike ? SoundType.STONE : SoundType.METAL)
                    .noOcclusion().isViewBlocking((s, l, p) -> false).lightLevel(s -> pr.light()), pr.box(), pr.cells())));
        }
        for (GeneratedProps.Vehicle v : GeneratedProps.VEHICLES) {
            PROPS3D.put(v.name(), decor(v.name(), () -> new VehicleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 12.0f).sound(SoundType.METAL).noOcclusion().isViewBlocking((s, l, p) -> false), v)));
        }
    }

    private ModBlocks() {}

    /** Вызвать из конструктора мода до регистрации шины предметов. */
    public static void init() {}
}
