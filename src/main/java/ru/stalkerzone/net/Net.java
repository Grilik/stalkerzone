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
    private static final String VERSION = "1";
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
}
