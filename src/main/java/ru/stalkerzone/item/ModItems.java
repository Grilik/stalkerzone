package ru.stalkerzone.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.sim.Good;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StalkerZoneMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StalkerZoneMod.MOD_ID);

    public static final RegistryObject<Item> RUBLE = ITEMS.register("ruble", () -> new RubleItem(1));
    public static final RegistryObject<Item> RUBLE_100 = ITEMS.register("ruble_100", () -> new RubleItem(100));
    public static final RegistryObject<Item> RUBLE_1000 = ITEMS.register("ruble_1000", () -> new RubleItem(1000));

    public static final RegistryObject<Item> FOOD = ITEMS.register("good_food", () -> new GoodItem(Good.FOOD));
    public static final RegistryObject<Item> PARTS = ITEMS.register("good_parts", () -> new GoodItem(Good.PARTS));
    public static final RegistryObject<Item> AMMO = ITEMS.register("good_ammo", () -> new GoodItem(Good.AMMO));
    public static final RegistryObject<Item> MEDS = ITEMS.register("good_meds", () -> new GoodItem(Good.MEDS));
    public static final RegistryObject<Item> GEAR = ITEMS.register("good_gear", () -> new GoodItem(Good.GEAR));
    public static final RegistryObject<Item> ARTIFACT = ITEMS.register("good_artifact", () -> new GoodItem(Good.ARTIFACT));
    public static final RegistryObject<Item> MATERIALS = ITEMS.register("good_materials", () -> new GoodItem(Good.MATERIALS));
    /** v1.13: прототипы снаряжения, заводские патроны и снаряжение с Большой земли. */
    public static final RegistryObject<Item> PROTO = ITEMS.register("good_proto", () -> new GoodItem(Good.PROTO));
    public static final RegistryObject<Item> AMMO_PRO = ITEMS.register("good_ammo_pro", () -> new GoodItem(Good.AMMO_PRO));
    public static final RegistryObject<Item> GEAR_PRO = ITEMS.register("good_gear_pro", () -> new GoodItem(Good.GEAR_PRO));

    /** Артефакты по каталогу {@link ru.stalkerzone.artifact.ArtifactDef}; все продаются как товар «Артефакты». */
    public static final String[] ARTIFACT_IDS = java.util.Arrays.stream(ru.stalkerzone.artifact.ArtifactDef.ALL).map(d -> d.id).toArray(String[]::new);
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] ARTIFACTS = new RegistryObject[ARTIFACT_IDS.length];

    static {
        for (int i = 0; i < ARTIFACT_IDS.length; i++) {
            final int idx = i;
            ARTIFACTS[i] = ITEMS.register("artifact_" + ARTIFACT_IDS[i], () -> new ru.stalkerzone.artifact.ArtifactItem(idx));
        }
    }

    /** v1.14: контейнеры для артефактов. */
    public static final RegistryObject<Item> CONTAINER_LEAD = ITEMS.register("container_lead", () -> new ru.stalkerzone.artifact.ContainerItem(false));
    public static final RegistryObject<Item> CONTAINER_PORCELAIN = ITEMS.register("container_porcelain", () -> new ru.stalkerzone.artifact.ContainerItem(true));

    /** v1.14: КПК погибшего сталкера (с отметкой тайника). */
    public static final RegistryObject<Item> DEAD_PDA = ITEMS.register("dead_pda", ru.stalkerzone.pda.DeadPdaItem::new);

    /** Карточка 030: рюкзак на месте гибели игрока — внутри весь его инвентарь. */
    public static final RegistryObject<Item> DEATH_BACKPACK = ITEMS.register("death_backpack", ru.stalkerzone.item.DeathBackpackItem::new);

    /** v1.14: болты и детекторы артефактов. */
    public static final RegistryObject<Item> BOLT = ITEMS.register("bolt", BoltItem::new);
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] DETECTORS = new RegistryObject[DetectorItem.Tier.values().length];

    static {
        for (DetectorItem.Tier t : DetectorItem.Tier.values())
            DETECTORS[t.ordinal()] = ITEMS.register("detector_" + t.name().toLowerCase(), () -> new DetectorItem(t));
    }

    /** Заглушка закрытой ячейки инвентаря. */
    public static final RegistryObject<Item> LOCKED_SLOT = ITEMS.register("locked_slot", ru.stalkerzone.gear.LockedSlotItem::new);

    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] SUITS = new RegistryObject[ru.stalkerzone.gear.Suit.ALL.length];
    @SuppressWarnings("unchecked")
    public static final RegistryObject<Item>[] BACKPACKS = new RegistryObject[ru.stalkerzone.gear.Backpack.ALL.length];

    static {
        for (ru.stalkerzone.gear.Suit suit : ru.stalkerzone.gear.Suit.ALL) {
            SUITS[suit.ordinal()] = ITEMS.register("suit_" + suit.id, () -> new ru.stalkerzone.gear.SuitItem(suit));
        }
        for (ru.stalkerzone.gear.Backpack b : ru.stalkerzone.gear.Backpack.ALL) {
            BACKPACKS[b.ordinal()] = ITEMS.register(b.id, () -> new ru.stalkerzone.gear.BackpackItem(b));
        }
    }

    /** КПК, накопители данных, пси-шлем (v1.3, лаборатории). */
    public static final RegistryObject<Item> PDA = ITEMS.register("pda", ru.stalkerzone.pda.PdaItem::new);
    public static final RegistryObject<Item> FLASH_DRIVE = ITEMS.register("flash_drive", () -> new DataDriveItem(false));
    public static final RegistryObject<Item> DATA_DISK = ITEMS.register("data_disk", () -> new DataDriveItem(true));
    public static final RegistryObject<Item> PSI_HELMET = ITEMS.register("psi_helmet", () -> new ru.stalkerzone.gear.PsiHelmetItem(false));
    public static final RegistryObject<Item> PSI_HELMET_PROTO = ITEMS.register("psi_helmet_proto", () -> new ru.stalkerzone.gear.PsiHelmetItem(true));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.stalkerzone"))
            .icon(() -> new ItemStack(ARTIFACT.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> {
                if (e != LOCKED_SLOT) out.accept(e.get());
            }))
            .build());

    private ModItems() {}

    public static Item of(Good g) {
        return switch (g) {
            case FOOD -> FOOD.get();
            case PARTS -> PARTS.get();
            case AMMO -> AMMO.get();
            case MEDS -> MEDS.get();
            case GEAR -> GEAR.get();
            case ARTIFACT -> ARTIFACT.get();
            case MATERIALS -> MATERIALS.get();
            case PROTO -> PROTO.get();
            case AMMO_PRO -> AMMO_PRO.get();
            case GEAR_PRO -> GEAR_PRO.get();
        };
    }

    /** Разложить сумму по купюрам, стопки до 64. */
    public static List<ItemStack> rubleStacks(long amount) {
        List<ItemStack> out = new ArrayList<>();
        long[] values = {1000, 100, 1};
        Item[] items = {RUBLE_1000.get(), RUBLE_100.get(), RUBLE.get()};
        for (int i = 0; i < values.length; i++) {
            long n = amount / values[i];
            amount -= n * values[i];
            while (n > 0) {
                int c = (int) Math.min(64, n);
                out.add(new ItemStack(items[i], c));
                n -= c;
            }
        }
        return out;
    }
}
