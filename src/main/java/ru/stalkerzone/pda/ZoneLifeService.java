package ru.stalkerzone.pda;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.NodeType;
import ru.stalkerzone.sim.ZoneLife;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;

import java.util.List;
import java.util.Random;

/**
 * Живая Зона в мире (v1.14, этап 6): лента КПК в чат владельцам КПК, КПК погибших на земле, тайники-бочки,
 * слухи у костров, проводники. Данные и логика — в {@link ZoneLife}.
 */
public final class ZoneLifeService {
    private static final Random RND = new Random();
    private static final String HEARD = "sz_rumors";

    private ZoneLifeService() {}

    /** После шага симуляции: новый день Зоны и рассылка ленты. */
    public static void daily(MinecraftServer server, ZoneData data) {
        List<ZoneLife.Msg> msgs = data.life.daily(data.sim, RND);
        data.setDirty();
        int shown = 0;
        for (ZoneLife.Msg m : msgs) {
            if (shown++ >= 4) break;   // не заваливать чат
            for (ServerPlayer p : server.overworld().players()) {
                if (!hasPda(p)) continue;
                p.sendSystemMessage(Component.literal("[КПК] ").withStyle(ChatFormatting.DARK_GREEN)
                        .append(Component.literal(m.text).withStyle(ChatFormatting.GRAY)));
                p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.get(), SoundSource.PLAYERS, 0.4f, 1.6f);
            }
        }
        // карточка 026: личные сообщения о судьбе контрактов игрока (сорван / выполнен)
        for (ru.stalkerzone.sim.ContractBoard.Note note : data.sim.contracts.drainNotes()) {
            for (ServerPlayer p : server.overworld().players()) {
                if (!p.getName().getString().equals(note.player())) continue;
                p.sendSystemMessage(Component.literal("[КПК] ").withStyle(ChatFormatting.DARK_GREEN)
                        .append(Component.literal(note.text()).withStyle(ChatFormatting.GOLD)));
                p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }
    }

    static boolean hasPda(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(ModItems.PDA.get())) return true;
        return false;
    }

    /** Раз в 2 секунды: КПК погибших и тайники рядом с игроками, слухи у костров. */
    public static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (t % 40 != 0) return;
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        ZoneData data = ZoneData.get(server);
        ZoneLife life = data.life;
        for (ServerPlayer p : level.players()) {
            for (ZoneLife.Stash st : life.stashes) {
                if (!st.pdaPlaced && near(p, st.pdaX, st.pdaZ, 48) && level.hasChunk(st.pdaX >> 4, st.pdaZ >> 4)) {
                    placePda(level, st);
                    data.setDirty();
                }
                if (st.known && !st.placed && near(p, st.x, st.z, 40) && level.hasChunk(st.x >> 4, st.z >> 4)) {
                    placeStash(level, st);
                    data.setDirty();
                }
            }
            if (t % 1200 == 0 && RND.nextDouble() < 0.5) campfire(p, data);
        }
    }

    private static boolean near(ServerPlayer p, int x, int z, int r) {
        return Math.abs(p.getX() - x) < r && Math.abs(p.getZ() - z) < r;
    }

    /** КПК погибшего лежит в траве, рядом кости. */
    private static void placePda(ServerLevel level, ZoneLife.Stash st) {
        st.pdaPlaced = true;
        BlockPos g = SpawnFinder.groundAt(level, st.pdaX, st.pdaZ);
        ItemStack pda = new ItemStack(ModItems.DEAD_PDA.get());
        CompoundTag tag = pda.getOrCreateTag();
        tag.putInt("stash", st.id);
        tag.putString("owner", st.owner);
        pda.setHoverName(Component.literal("КПК: " + st.owner));
        ItemEntity it = new ItemEntity(level, g.getX() + 0.5, g.getY() + 0.2, g.getZ() + 0.5, pda);
        it.setDeltaMovement(0, 0, 0);
        it.setUnlimitedLifetime();
        level.addFreshEntity(it);
        if (level.getBlockState(g).isAir()) level.setBlock(g, Blocks.SKELETON_SKULL.defaultBlockState(), 2);
    }

    /** Тайник — ржавая бочка (обычная) с хабаром погибшего. */
    private static void placeStash(ServerLevel level, ZoneLife.Stash st) {
        st.placed = true;
        BlockPos g = SpawnFinder.groundAt(level, st.x, st.z);
        if (!level.getFluidState(g).isEmpty()) g = g.above();
        level.setBlock(g, Blocks.BARREL.defaultBlockState(), 2);
        if (!(level.getBlockEntity(g) instanceof BarrelBlockEntity be)) return;
        be.setCustomName(Component.literal("Тайник: " + st.owner));
        int slot = 0;
        for (int i = 0; i < st.loot.length - 1 && slot < 26; i++) {
            int q = st.loot[i];
            while (q > 0 && slot < 26) {
                int c = Math.min(64, q);
                be.setItem(slot++, new ItemStack(ModItems.of(Good.ALL[i]), c));
                q -= c;
            }
        }
        long money = st.loot[st.loot.length - 1];
        for (ItemStack s : ModItems.rubleStacks(money)) if (slot < 27) be.setItem(slot++, s);
    }

    /** Слух у костра: если игрок в лагере, кто-нибудь да расскажет. */
    private static void campfire(ServerPlayer p, ZoneData data) {
        Node camp = null;
        for (Node n : data.sim.nodes) {
            if (n.type != NodeType.BASE && n.type != NodeType.SETTLEMENT) continue;
            if (Math.hypot(n.x * ZoneBounds.SCALE - p.getX(), n.z * ZoneBounds.SCALE - p.getZ()) < 45) camp = n;
        }
        if (camp == null || data.life.rumors.isEmpty()) return;
        int[] heard = p.getPersistentData().getIntArray(HEARD);
        ZoneLife.Rumor pick = null;
        for (ZoneLife.Rumor r : data.life.rumors) {
            boolean seen = false;
            for (int h : heard) if (h == r.id) seen = true;
            if (!seen) { pick = r; break; }
        }
        if (pick == null) return;
        int[] nh = java.util.Arrays.copyOf(heard, Math.min(heard.length + 1, 64));
        nh[nh.length - 1] = pick.id;
        p.getPersistentData().put(HEARD, new IntArrayTag(nh));
        p.sendSystemMessage(Component.literal("У костра рассказывают: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(pick.text).withStyle(ChatFormatting.WHITE)));
        ZoneLife.Stash st = data.life.hear(pick);
        if (st != null) {
            data.setDirty();
            p.sendSystemMessage(Component.literal("[КПК] Отметка: возможный тайник около " + pick.x + ", " + pick.z + ". Проверить? (/zone rumors)")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    /** Список услышанных слухов. */
    public static String rumors(ServerPlayer p, ZoneData data) {
        int[] heard = p.getPersistentData().getIntArray(HEARD);
        StringBuilder sb = new StringBuilder("Слухи, что вы слышали у костров:");
        int n = 0;
        for (int h : heard) {
            ZoneLife.Rumor r = data.life.rumor(h);
            if (r == null) continue;
            sb.append("\n• ").append(r.text);
            n++;
        }
        return n == 0 ? "Вы ещё не слышали слухов. Посидите у костра в лагере." : sb.toString();
    }

    /** Последние сообщения ленты. */
    public static String feed(ZoneData data, int count) {
        List<ZoneLife.Msg> f = data.life.feed;
        StringBuilder sb = new StringBuilder("Лента КПК:");
        for (int i = Math.max(0, f.size() - count); i < f.size(); i++) sb.append("\n[день ").append(f.get(i).day).append("] ").append(f.get(i).text);
        return f.isEmpty() ? "Лента пока пуста." : sb.toString();
    }

    /** Проводник: за деньги ведёт в другую локацию по безопасной тропе. Деньги идут торговцу лагеря. */
    public static String guide(ServerPlayer p, ZoneData data, String dest) {
        Node here = null, to = null;
        for (Node n : data.sim.nodes) {
            if ((n.type == NodeType.BASE || n.type == NodeType.SETTLEMENT)
                    && Math.hypot(n.x * ZoneBounds.SCALE - p.getX(), n.z * ZoneBounds.SCALE - p.getZ()) < 45) here = n;
            if (n.name.equalsIgnoreCase(dest) || n.name.toLowerCase().startsWith(dest.toLowerCase())) to = to == null ? n : to;
        }
        if (here == null) return "Проводники сидят в лагерях: подойдите к лагерю (база или поселение).";
        if (to == null) {
            StringBuilder names = new StringBuilder();
            for (Node n : data.sim.nodes) names.append(names.length() == 0 ? "" : ", ").append(n.name);
            return "Не знаю такого места: «" + dest + "». Куда водят: " + names + ".";
        }
        if (to == here) return "Вы уже здесь.";
        if (!data.sim.graph.reachable(here.id, to.id)) return "Туда проводники не ходят: путь закрыт (пси-поле или война).";
        int days = data.sim.graph.dist(here.id, to.id);
        long price = 250 + 180L * days;
        long cash = ru.stalkerzone.npc.TradeService.countRubles(p);
        if (cash < price) return "Проводник просит " + price + " руб. до «" + to.name + "» (у вас " + cash + ").";
        ru.stalkerzone.npc.TradeService.takeRubles(p, price);
        data.sim.playerPay(here, price);
        data.setDirty();
        ServerLevel level = p.serverLevel();
        BlockPos pos = SpawnFinder.safeNear(level, ru.stalkerzone.world.ZoneWorld.bounds(), to.x * ZoneBounds.SCALE + 12, to.z * ZoneBounds.SCALE + 12);
        p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        return "Проводник довёл вас до «" + to.name + "» тропами между аномалиями. Плата " + price + " руб. ушла в кассу «" + here.name + "».";
    }
}
