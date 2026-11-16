package ru.stalkerzone.container;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import ru.stalkerzone.compat.Tacz;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Э1-17: добыча контейнера в предметы Minecraft по {@code data/stalkerzone/container_loot/loot.json} (ресурсы сервера, датапаки). */
public final class LootService {
    private static final Logger LOG = LogUtils.getLogger();
    public static final ResourceLocation FILE = new ResourceLocation("stalkerzone", "container_loot/loot.json");
    private static MinecraftServer cachedFor;
    private static ContainerLoot cached;

    private LootService() {}

    static synchronized ContainerLoot loot(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cachedFor = server;
        cached = null;
        try (InputStream in = server.getResourceManager().getResourceOrThrow(FILE).open()) {
            cached = new ContainerLoot(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.error("[stalkerzone] добыча контейнеров: не прочитан {}", FILE, e);
        }
        return cached;
    }

    public static List<ItemStack> roll(MinecraftServer server, String kind, String place, int z, String loc, Random r) {
        List<ItemStack> out = new ArrayList<>();
        ContainerLoot l = loot(server);
        if (l == null) return out;
        for (ContainerLoot.Drop d : l.roll(kind, place, z, loc, r)) {
            ItemStack st = ItemStack.EMPTY;
            if (d.gun() != null) st = Tacz.gun(d.gun(), d.mode() == null ? "SEMI" : d.mode(), 0);
            else if (d.ammo() != null) {
                st = Tacz.ammo(d.ammo(), d.count());
                if (st.isEmpty()) st = stack("stalkerzone:good_ammo", d.count()); // без TaCZ — кустарные патроны
            } else st = stack(d.id(), d.count());
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    private static ItemStack stack(String id, int n) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            LOG.warn("[stalkerzone] добыча контейнеров: нет предмета {}", id);
            return ItemStack.EMPTY;
        }
        return new ItemStack(it, Math.min(n, it.getMaxStackSize()));
    }
}
