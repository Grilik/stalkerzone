package ru.stalkerzone.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Мост к TaCZ без жёсткой зависимости: если TaCZ не установлен, все методы безопасно ничего не делают,
 * и NPC воюют как раньше. Оружие создаётся по NBT (GunId, режим огня, патроны), стрельба NPC идёт через
 * интерфейс IGunOperator, который TaCZ добавляет каждому LivingEntity.
 */
public final class Tacz {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String MODID = "tacz";
    private static Boolean loaded;
    private static Method drawM, shootM, aimM, initM;
    private static boolean reflectFailed;

    private Tacz() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded(MODID);
        return loaded;
    }

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, path));
    }

    /** Оружие TaCZ с полным магазином. mode: AUTO, SEMI или BURST. */
    public static ItemStack gun(String gunId, String mode, int ammo) {
        if (!loaded()) return ItemStack.EMPTY;
        Item it = item("modern_kinetic_gun");
        if (it == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it);
        CompoundTag t = s.getOrCreateTag();
        t.putString("GunId", gunId);
        t.putString("GunFireMode", mode);
        t.putInt("GunCurrentAmmoCount", Math.max(0, ammo));
        t.putBoolean("HasBulletInBarrel", ammo > 0);
        return s;
    }

    /** Патроны TaCZ указанного калибра. */
    public static ItemStack ammo(String ammoId, int count) {
        if (!loaded() || count <= 0) return ItemStack.EMPTY;
        Item it = item("ammo");
        if (it == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it, count);
        s.getOrCreateTag().putString("AmmoId", ammoId);
        return s;
    }

    public static boolean isGun(ItemStack s) {
        if (s.isEmpty() || !loaded()) return false;
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(s.getItem());
        return k != null && MODID.equals(k.getNamespace()) && s.hasTag() && s.getTag().contains("GunId");
    }

    public static boolean isAmmo(ItemStack s) {
        if (s.isEmpty() || !loaded()) return false;
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(s.getItem());
        return k != null && MODID.equals(k.getNamespace()) && k.getPath().equals("ammo") && s.hasTag() && s.getTag().contains("AmmoId");
    }

    public static String gunId(ItemStack s) { return isGun(s) ? s.getTag().getString("GunId") : ""; }

    public static String ammoId(ItemStack s) { return isAmmo(s) ? s.getTag().getString("AmmoId") : ""; }

    public static int ammoIn(ItemStack s) { return isGun(s) ? s.getTag().getInt("GunCurrentAmmoCount") : 0; }

    public static void setAmmoIn(ItemStack s, int n) {
        if (!isGun(s)) return;
        s.getTag().putInt("GunCurrentAmmoCount", Math.max(0, n));
        s.getTag().putBoolean("HasBulletInBarrel", n > 0);
    }

    // ---------------------------------------------------------------- стрельба NPC

    private static boolean bind(LivingEntity e) {
        if (reflectFailed) return false;
        if (shootM != null) return true;
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            if (!op.isInstance(e)) { reflectFailed = true; return false; }
            drawM = op.getMethod("draw", Supplier.class);
            shootM = op.getMethod("shoot", Supplier.class, Supplier.class);
            aimM = op.getMethod("aim", boolean.class);
            initM = op.getMethod("initialData");
            return true;
        } catch (Throwable t) {
            LOG.warn("TaCZ найден, но его API изменилось: NPC будут драться врукопашную ({})", t.toString());
            reflectFailed = true;
            return false;
        }
    }

    /** Взять оружие в руки (после смены предмета в руке). */
    public static void draw(LivingEntity e) {
        if (!loaded() || !bind(e)) return;
        try {
            Supplier<ItemStack> sup = e::getMainHandItem;
            drawM.invoke(e, sup);
        } catch (Throwable t) { fail(t); }
    }

    public static void aim(LivingEntity e, boolean on) {
        if (!loaded() || !bind(e)) return;
        try { aimM.invoke(e, on); } catch (Throwable t) { fail(t); }
    }

    /** Выстрел; возвращает имя результата TaCZ (SUCCESS, COOL_DOWN, NO_AMMO, IS_DRAWING...) или пустую строку. */
    public static String shoot(LivingEntity e, float pitch, float yaw) {
        if (!loaded() || !bind(e)) return "";
        try {
            Supplier<Float> p = () -> pitch, y = () -> yaw;
            Object r = shootM.invoke(e, p, y);
            return r == null ? "" : r.toString();
        } catch (Throwable t) { fail(t); return ""; }
    }

    private static void fail(Throwable t) {
        if (!reflectFailed) LOG.warn("Ошибка вызова TaCZ: {}", t.toString());
        reflectFailed = true;
    }
}
