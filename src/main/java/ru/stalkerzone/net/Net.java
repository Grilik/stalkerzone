package ru.stalkerzone.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.pda.PdaService;

import java.util.function.Supplier;

/** Сеть мода: снимок КПК с сервера на клиент и действия игрока в КПК с клиента на сервер. */
public final class Net {
    private static final String VERSION = "2";   // 2: окно торговца (П-07)
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StalkerZoneMod.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static final int MAX = 1 << 18;

    private Net() {}

    public static void register() {
        CHANNEL.messageBuilder(PdaSnapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PdaSnapshot::encode).decoder(PdaSnapshot::new).consumerMainThread(PdaSnapshot::handle).add();
        CHANNEL.messageBuilder(PdaAction.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PdaAction::encode).decoder(PdaAction::new).consumerMainThread(PdaAction::handle).add();
        CHANNEL.messageBuilder(EmissionPhase.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EmissionPhase::encode).decoder(EmissionPhase::new).consumerMainThread(EmissionPhase::handle).add();
        CHANNEL.messageBuilder(TraderView.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TraderView::encode).decoder(TraderView::new).consumerMainThread(TraderView::handle).add();
        CHANNEL.messageBuilder(TraderAction.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TraderAction::encode).decoder(TraderAction::new).consumerMainThread(TraderAction::handle).add();
    }

    public static void sendTo(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** Снимок состояния Зоны для экрана КПК (текст в простом построчном формате). */
    public static final class PdaSnapshot {
        public final String text;
        public final boolean open;

        public PdaSnapshot(String text, boolean open) {
            this.text = text;
            this.open = open;
        }

        public PdaSnapshot(FriendlyByteBuf buf) {
            this.open = buf.readBoolean();
            this.text = buf.readUtf(MAX);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeUtf(text, MAX);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.stalkerzone.client.ClientPda.receive(text, open));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Действие в КПК: взять, отказаться, сдать, получить награду, сдать данные. */
    public static final class PdaAction {
        public final int action, id;

        public PdaAction(int action, int id) {
            this.action = action;
            this.id = id;
        }

        public PdaAction(FriendlyByteBuf buf) {
            this.action = buf.readVarInt();
            this.id = buf.readVarInt();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeVarInt(id);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) PdaService.action(p, action, id);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Фаза выброса для клиента (карточка 030): «краснота» неба и тумана, нарастающая к пику и
     * спадающая после, и признак «игрок снаружи укрытий» (тогда экран темнеет сильнее).
     * {@code phase}: 0 — выброса нет, 1 — идёт выброс, 2 — затухание.
     */
    public static final class EmissionPhase {
        public final int phase;
        public final float intensity;
        public final boolean outside;

        public EmissionPhase(int phase, float intensity, boolean outside) {
            this.phase = phase;
            this.intensity = intensity;
            this.outside = outside;
        }

        public EmissionPhase(FriendlyByteBuf buf) {
            this.phase = buf.readVarInt();
            this.intensity = buf.readFloat();
            this.outside = buf.readBoolean();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(phase);
            buf.writeFloat(intensity);
            buf.writeBoolean(outside);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.stalkerzone.client.ClientEmission.receive(phase, intensity, outside));
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * П-07: состояние окна торговца для клиента. Всё посчитано на сервере (цены, итог сделки, что торговец берёт);
     * клиент только рисует и отправляет {@link TraderAction}.
     */
    public static final class TraderView {
        /** Строка списка: товар торговца, вещь игрока (index — слот инвентаря) или вещь на ремонт. */
        public record Row(int index, net.minecraft.world.item.ItemStack icon, String name, int count, long price, String note, boolean ok) {}
        /** Контракт: флаги — {@code PdaSnapshot.CAN_*}. */
        public record ContractRow(int id, String text, int flags) {}
        /** Итог выбранной сделки до подтверждения: side 0 — купить, 1 — продать; qty и money — что выйдет на самом деле. */
        public record Quote(int side, int index, int asked, int qty, long money, String reason) {}

        public final boolean open;
        public final int entity;
        public final String title, note, message;
        public final long traderCash, playerMoney;
        public final double weight, limit;
        public final java.util.List<Row> offers, items, repairs;
        public final java.util.List<ContractRow> contracts;
        public final Quote quote;

        public TraderView(boolean open, int entity, String title, String note, String message, long traderCash, long playerMoney,
                          double weight, double limit, java.util.List<Row> offers, java.util.List<Row> items, java.util.List<Row> repairs,
                          java.util.List<ContractRow> contracts, Quote quote) {
            this.open = open; this.entity = entity; this.title = title; this.note = note; this.message = message;
            this.traderCash = traderCash; this.playerMoney = playerMoney; this.weight = weight; this.limit = limit;
            this.offers = offers; this.items = items; this.repairs = repairs; this.contracts = contracts; this.quote = quote;
        }

        public TraderView(FriendlyByteBuf b) {
            open = b.readBoolean(); entity = b.readVarInt();
            title = b.readUtf(); note = b.readUtf(); message = b.readUtf(4096);
            traderCash = b.readVarLong(); playerMoney = b.readVarLong(); weight = b.readDouble(); limit = b.readDouble();
            offers = rows(b); items = rows(b); repairs = rows(b);
            int n = b.readVarInt();
            java.util.List<ContractRow> cs = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) cs.add(new ContractRow(b.readVarInt(), b.readUtf(4096), b.readVarInt()));
            contracts = cs;
            quote = b.readBoolean() ? new Quote(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readUtf()) : null;
        }

        private static java.util.List<Row> rows(FriendlyByteBuf b) {
            int n = b.readVarInt();
            java.util.List<Row> out = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++)
                out.add(new Row(b.readVarInt(), b.readItem(), b.readUtf(), b.readVarInt(), b.readVarLong(), b.readUtf(), b.readBoolean()));
            return out;
        }

        private static void rows(FriendlyByteBuf b, java.util.List<Row> rows) {
            b.writeVarInt(rows.size());
            for (Row r : rows) {
                b.writeVarInt(r.index()); b.writeItem(r.icon()); b.writeUtf(r.name()); b.writeVarInt(r.count());
                b.writeVarLong(r.price()); b.writeUtf(r.note()); b.writeBoolean(r.ok());
            }
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBoolean(open); b.writeVarInt(entity);
            b.writeUtf(title); b.writeUtf(note); b.writeUtf(message, 4096);
            b.writeVarLong(traderCash); b.writeVarLong(playerMoney); b.writeDouble(weight); b.writeDouble(limit);
            rows(b, offers); rows(b, items); rows(b, repairs);
            b.writeVarInt(contracts.size());
            for (ContractRow c : contracts) { b.writeVarInt(c.id()); b.writeUtf(c.text(), 4096); b.writeVarInt(c.flags()); }
            b.writeBoolean(quote != null);
            if (quote != null) {
                b.writeVarInt(quote.side()); b.writeVarInt(quote.index()); b.writeVarInt(quote.asked()); b.writeVarInt(quote.qty());
                b.writeVarLong(quote.money()); b.writeUtf(quote.reason());
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.stalkerzone.client.TraderScreen.receive(this));
            ctx.get().setPacketHandled(true);
        }
    }

    /** П-07: просьба окна торговца — сервер проверяет и проводит сделку сам ({@code npc.TraderWindow}). */
    public static final class TraderAction {
        public final int action, entity, side, index, qty;

        public TraderAction(int action, int entity, int side, int index, int qty) {
            this.action = action; this.entity = entity; this.side = side; this.index = index; this.qty = qty;
        }

        public TraderAction(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(action); b.writeVarInt(entity); b.writeVarInt(side); b.writeVarInt(index); b.writeVarInt(qty);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ru.stalkerzone.npc.TraderWindow.action(p, this);
            ctx.get().setPacketHandled(true);
        }
    }
}
