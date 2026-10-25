package ru.stalkerzone.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;

import java.util.List;
import java.util.Random;

/**
 * Зомбированные сталкеры (v1.5): у точек, где симуляция насчитала зомби, бродят зомби в куртках и касках
 * (каска не даёт им гореть днём). Убийство уменьшает число зомби в симуляции.
 */
public final class ZombieManager {
    private static final String TAG = "stalkerzone_zombie";
    private static final Random RND = new Random();

    private ZombieManager() {}

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        Simulation sim = ZoneData.get(server).sim;
        for (Node n : sim.nodes) {
            double count = sim.people.zombies[n.id];
            int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
            boolean near = false;
            for (ServerPlayer p : players) if (Math.hypot(p.getX() - cx, p.getZ() - cz) < 110) near = true;
            AABB box = new AABB(cx - 70, level.getMinBuildHeight(), cz - 70, cx + 70, level.getMaxBuildHeight(), cz + 70);
            List<Zombie> here = level.getEntitiesOfClass(Zombie.class, box, z -> z.getTags().contains(TAG + n.id));
            int wanted = near ? Math.min(4, (int) Math.floor(count)) : 0;
            if (here.size() > wanted) {
                here.get(0).discard();
                continue;
            }
            if (here.size() >= wanted || RND.nextInt(4) != 0) continue;
            int x = cx + RND.nextInt(81) - 40, z = cz + RND.nextInt(81) - 40;
            if (Math.hypot(x - cx, z - cz) < 30 || !level.hasChunk(x >> 4, z >> 4)) continue;
            boolean seen = false;
            for (ServerPlayer p : players) if (p.distanceToSqr(x, p.getY(), z) < 24 * 24) seen = true;
            if (seen) continue;
            BlockPos pos = SpawnFinder.groundAt(level, x, z);
            Zombie zb = EntityType.ZOMBIE.create(level);
            if (zb == null) continue;
            zb.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RND.nextFloat() * 360f, 0f);
            zb.setCustomName(Component.literal("Зомбированный сталкер"));
            zb.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zb.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            zb.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RND.nextBoolean() ? Items.STONE_SWORD : Items.AIR));
            zb.setDropChance(EquipmentSlot.HEAD, 0f);
            zb.setDropChance(EquipmentSlot.CHEST, 0f);
            zb.setDropChance(EquipmentSlot.MAINHAND, 0f);
            zb.addTag(TAG);
            zb.addTag(TAG + n.id);
            zb.setPersistenceRequired();
            level.addFreshEntity(zb);
        }
    }

    public static final class Events {
        @SubscribeEvent
        public void onDeath(LivingDeathEvent e) {
            if (!(e.getEntity() instanceof Zombie z) || z.level().isClientSide || !z.getTags().contains(TAG)) return;
            MinecraftServer server = z.getServer();
            if (server == null) return;
            ZoneData data = ZoneData.get(server);
            for (String t : z.getTags()) {
                if (!t.startsWith(TAG) || t.equals(TAG)) continue;
                try {
                    data.sim.people.zombieKilled(Integer.parseInt(t.substring(TAG.length())));
                    data.setDirty();
                } catch (NumberFormatException ignored) {
                    // чужая метка
                }
            }
        }
    }
}
