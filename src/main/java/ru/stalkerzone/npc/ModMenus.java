package ru.stalkerzone.npc;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;

/** Окна мода (П-10: рюкзак на месте гибели; дальше — торговец, пульт базы). */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, StalkerZoneMod.MOD_ID);

    public static final RegistryObject<MenuType<DeathBackpackMenu>> DEATH_BACKPACK = MENUS.register("death_backpack",
            () -> new MenuType<>(DeathBackpackMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
