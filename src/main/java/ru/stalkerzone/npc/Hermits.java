package ru.stalkerzone.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.sim.Anomaly;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Отшельники Зоны (v1.14, этап 9). Лесник в Рыжем лесу — знает лес: подсказывает, где созрел артефакт, даёт болты.
 * Болотный Доктор на хуторе — лечит всех даром: раны, радиацию, хвори, даже «паутину»; звери его не трогают.
 */
public final class Hermits {
    private static final Random RND = new Random();
    private static final String[][] WHO = {{"Кордон Лесника", "Лесник"}, {"Хутор Болотного Доктора", "Болотный Доктор"}};

    private Hermits() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        ZoneData d = ZoneData.get(server);
        for (String[] w : WHO) {
            Node n = null;
            for (Node x : d.sim.nodes) if (x.name.equals(w[0])) n = x;
            if (n == null) continue;
            int cx = n.x * ZoneBounds.SCALE, cz = n.z * ZoneBounds.SCALE;
            boolean near = false;
            for (ServerPlayer p : level.players()) if (Math.hypot(p.getX() - cx, p.getZ() - cz) < 56) near = true;
            if (!near || !level.hasChunk(cx >> 4, cz >> 4) || !level.hasChunk((cx + 8) >> 4, cz >> 4)) continue;
            List<StalkerEntity> here = level.getEntitiesOfClass(StalkerEntity.class, new AABB(cx - 30, level.getMinBuildHeight(), cz - 30, cx + 30, level.getMaxBuildHeight(), cz + 30),
                    StalkerEntity::isHermit);
            if (!here.isEmpty()) continue;
            int y = SpawnFinder.groundAt(level, cx + 8, cz).getY();
            StalkerEntity e = ModEntities.STALKER.get().create(level);
            if (e == null) continue;
            BlockPos pos = new BlockPos(cx, y + 1, cz - 1);
            e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180f, 0f);
            e.configure(0, StalkerEntity.ROLE_HERMIT, -1, pos, Component.literal(w[1]));
            level.addFreshEntity(e);
        }
    }

    public static void talk(ServerPlayer p, StalkerEntity e) {
        String name = e.getCustomName() == null ? "" : e.getCustomName().getString();
        if (name.contains("Доктор")) doctor(p);
        else forester(p);
    }

    private static void doctor(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        ru.stalkerzone.survival.Radiation.setDose(p, 0);
        List<MobEffect> bad = new ArrayList<>();
        for (MobEffectInstance i : p.getActiveEffects()) if (i.getEffect().getCategory() == MobEffectCategory.HARMFUL) bad.add(i.getEffect());
        for (MobEffect m : bad) p.removeEffect(m);
        boolean web = p.getPersistentData().contains("sz_web_doom");
        p.getPersistentData().remove("sz_web_doom");
        p.getFoodData().setFoodLevel(Math.max(p.getFoodData().getFoodLevel(), 16));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.8f, 1.2f);
        String[] lines = {"«Ложись, сынок. Ничего не спрашивай — сам не знаю, как это делаю».",
                "«Зона всех лечит, кто к ней с уважением. А я так, помогаю».",
                "«Ступай. И псов у Собачьей деревни не трогай — они меня знают»."};
        p.sendSystemMessage(Component.literal("Болотный Доктор: " + lines[RND.nextInt(lines.length)]).withStyle(ChatFormatting.AQUA));
        p.sendSystemMessage(Component.literal("Раны затянулись, радиация ушла" + (web ? ", и сердце бьётся ровно — паутина отпустила." : ".")).withStyle(ChatFormatting.GREEN));
    }

    private static void forester(ServerPlayer p) {
        ZoneData d = ZoneData.get(p.getServer());
        // где ближе всего созрел артефакт
        Anomaly best = null;
        double bd = Double.MAX_VALUE;
        for (Anomaly a : d.sim.anomalies) {
            if (a.pending <= 0) continue;
            Node n = d.sim.nodes.get(a.node);
            double dist = Math.hypot(n.x * ZoneBounds.SCALE + a.dx - p.getX(), n.z * ZoneBounds.SCALE + a.dz - p.getZ());
            if (dist < bd) { bd = dist; best = a; }
        }
        String hint;
        if (best != null) {
            Node n = d.sim.nodes.get(best.node);
            hint = "«Лес говорит: у " + n.name + " в поле «" + best.type.ru + "» созрел артефакт. Около " + (n.x * ZoneBounds.SCALE + best.dx) + ", "
                    + (n.z * ZoneBounds.SCALE + best.dz) + ". Детектор не забудь».";
        } else {
            hint = "«Тихо нынче в лесу. После выброса приходи — будет что искать».";
        }
        p.sendSystemMessage(Component.literal("Лесник: " + hint).withStyle(ChatFormatting.DARK_GREEN));
        long last = p.getPersistentData().getLong("sz_forester");
        long now = p.level().getGameTime();
        if (now - last > 24000) {
            p.getPersistentData().putLong("sz_forester", now);
            ItemStack bolts = new ItemStack(ModItems.BOLT.get(), 16);
            if (!p.getInventory().add(bolts)) p.drop(bolts, false);
            p.sendSystemMessage(Component.literal("Лесник: «Держи болтов. По Рыжему лесу без них — верная смерть».").withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
