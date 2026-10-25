package ru.stalkerzone.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;
import ru.stalkerzone.sim.Good;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Кустарное и заводское оружие и патроны (v1.12) поверх TaCZ, без жёсткой зависимости.
 *
 * <p>Ствол несёт метку сборки {@value #GUN_TAG}: кустарная (собрана и перебрана в мастерских Зоны) или заводская
 * (с Большой земли). Пачка патронов несёт метку {@value #AMMO_TAG}: кустарные (перезаряженные гильзы, самодельный
 * порох) или заводские. При перезарядке в ствол записывается, какие патроны в него ушли ({@value #LOADED_TAG}).
 * <ul>
 *   <li>урон: кустарный ствол ×0,92, заводской ×1,05; кустарные патроны ×0,88, заводские ×1,08;</li>
 *   <li>осечки: кустарные патроны 2,5%, кустарный ствол ещё 1,5%, заводские патроны 0,3%.</li>
 * </ul>
 * Оружие и патроны без метки (из чужих паков, выданные командой) считаются заводскими.
 */
public final class GunQuality {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String GUN_TAG = "szBuild", AMMO_TAG = "szAmmo", LOADED_TAG = "szLoaded";
    public static final String HAND = "hand", FACTORY = "factory";
    private static final Random RNG = new Random();
    private static boolean registered;

    private GunQuality() {}

    // ---------------------------------------------------------------- метки на предметах

    public static boolean handGun(ItemStack gun) {
        return gun.hasTag() && HAND.equals(gun.getTag().getString(GUN_TAG));
    }

    public static boolean handAmmo(ItemStack ammo) {
        return ammo.hasTag() && HAND.equals(ammo.getTag().getString(AMMO_TAG));
    }

    public static boolean handLoaded(ItemStack gun) {
        return gun.hasTag() && HAND.equals(gun.getTag().getString(LOADED_TAG));
    }

    /** Пометить ствол: кустарный или заводской. */
    public static ItemStack markGun(ItemStack gun, boolean hand) {
        if (!gun.isEmpty()) {
            CompoundTag t = gun.getOrCreateTag();
            t.putString(GUN_TAG, hand ? HAND : FACTORY);
            if (!t.contains(LOADED_TAG)) t.putString(LOADED_TAG, hand ? HAND : FACTORY);
        }
        return gun;
    }

    /** Какие патроны сейчас в магазине. */
    public static void markLoaded(ItemStack gun, boolean hand) {
        if (!gun.isEmpty()) gun.getOrCreateTag().putString(LOADED_TAG, hand ? HAND : FACTORY);
    }

    public static ItemStack markAmmo(ItemStack ammo, boolean hand) {
        if (!ammo.isEmpty()) ammo.getOrCreateTag().putString(AMMO_TAG, hand ? HAND : FACTORY);
        return ammo;
    }

    /** Товар симуляции, которым торгуется пачка патронов. */
    public static Good ammoGood(ItemStack ammo) { return handAmmo(ammo) ? Good.AMMO : Good.AMMO_PRO; }

    /** Товар симуляции, которым торгуется ствол. */
    public static Good gunGood(ItemStack gun) { return handGun(gun) ? Good.GEAR : Good.GEAR_PRO; }

    /** Единиц товара на ствол: заводской стоит меньше единиц, но каждая дороже. */
    public static int units(CompatConfig.GunSpec g, boolean hand) {
        return hand ? Math.max(1, g.gear()) : Math.max(1, (int) Math.round(g.gear() * 0.6));
    }

    public static double damageFactor(ItemStack gun) {
        if (!Tacz.isGun(gun)) return 1;
        return (handGun(gun) ? 0.92 : 1.05) * (handLoaded(gun) ? 0.88 : 1.08);
    }

    public static double misfireChance(ItemStack gun) {
        return (handLoaded(gun) ? 0.025 : 0.003) + (handGun(gun) ? 0.015 : 0);
    }

    /** Строки для подсказки на стволе или патронах. */
    public static void tooltip(ItemStack s, List<Component> out) {
        if (Tacz.isGun(s)) {
            boolean hand = handGun(s);
            out.add(Component.literal(hand ? "Сборка: кустарная (мастерские Зоны)" : "Сборка: заводская (Большая земля)")
                    .withStyle(hand ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            out.add(Component.literal("Заряжено: " + (handLoaded(s) ? "кустарные патроны" : "заводские патроны")
                    + String.format(", урон %+.0f%%, осечки %.1f%%", (damageFactor(s) - 1) * 100, misfireChance(s) * 100))
                    .withStyle(ChatFormatting.GRAY));
        } else if (Tacz.isAmmo(s)) {
            boolean hand = handAmmo(s);
            out.add(Component.literal(hand ? "Кустарные: перезаряженные гильзы, осечки и урон ниже" : "Заводские: ровный заряд, редкие осечки")
                    .withStyle(hand ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        }
    }

    // ---------------------------------------------------------------- события TaCZ

    /** Подписаться на события TaCZ через отражение (TaCZ может не быть). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized void register() {
        if (registered || !Tacz.loaded()) return;
        registered = true;
        try {
            Class hurt = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            Class shoot = Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
            Class reload = Class.forName("com.tacz.guns.api.event.common.GunReloadEvent");
            Method hAttacker = hurt.getMethod("getAttacker"), hBase = hurt.getMethod("getBaseAmount"),
                    hSet = hurt.getMethod("setBaseAmount", float.class), hSide = hurt.getMethod("getLogicalSide");
            Method sShooter = shoot.getMethod("getShooter"), sGun = shoot.getMethod("getGunItemStack"), sSide = shoot.getMethod("getLogicalSide");
            Method rEntity = reload.getMethod("getEntity"), rGun = reload.getMethod("getGunItemStack"), rSide = reload.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, hurt, (Consumer) (Object e) -> {
                try {
                    if (!server(hSide.invoke(e))) return;
                    LivingEntity a = (LivingEntity) hAttacker.invoke(e);
                    if (a == null) return;
                    double k = damageFactor(a.getMainHandItem());
                    if (k != 1) hSet.invoke(e, (float) ((float) hBase.invoke(e) * k));
                } catch (Throwable t) { warn(t); }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, shoot, (Consumer) (Object e) -> {
                try {
                    if (!server(sSide.invoke(e))) return;
                    ItemStack gun = (ItemStack) sGun.invoke(e);
                    LivingEntity who = (LivingEntity) sShooter.invoke(e);
                    if (gun == null || who == null || RNG.nextDouble() >= misfireChance(gun)) return;
                    ((Event) e).setCanceled(true);
                    who.level().playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
                    if (who instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Осечка!").withStyle(ChatFormatting.RED), true);
                } catch (Throwable t) { warn(t); }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, reload, (Consumer) (Object e) -> {
                try {
                    if (!server(rSide.invoke(e))) return;
                    LivingEntity who = (LivingEntity) rEntity.invoke(e);
                    ItemStack gun = (ItemStack) rGun.invoke(e);
                    if (!(who instanceof Player p) || gun == null) return;
                    // TaCZ берёт патроны из инвентаря по порядку ячеек: смотрим, какая пачка подходящего калибра первая
                    String need = gunAmmo(gun);
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack s = p.getInventory().getItem(i);
                        if (Tacz.isAmmo(s) && (need.isEmpty() || need.equals(Tacz.ammoId(s)))) {
                            markLoaded(gun, handAmmo(s));
                            return;
                        }
                    }
                } catch (Throwable t) { warn(t); }
            });
            LOG.info("Кустарное и заводское оружие: события TaCZ подключены");
        } catch (Throwable t) {
            LOG.warn("Не удалось подключить события TaCZ (качество оружия и патронов отключено): {}", t.toString());
        }
    }

    private static String gunAmmo(ItemStack gun) {
        CompatConfig.GunSpec g = CompatConfig.GUNS.get(Tacz.gunId(gun));
        return g == null ? "" : g.ammo();
    }

    private static boolean server(Object side) {
        return side != null && "SERVER".equals(side.toString());
    }

    private static boolean warned;

    private static void warn(Throwable t) {
        if (!warned) LOG.warn("Ошибка в обработчике качества оружия: {}", t.toString());
        warned = true;
    }
}
