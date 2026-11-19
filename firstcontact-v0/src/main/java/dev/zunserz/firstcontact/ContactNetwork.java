package dev.zunserz.firstcontact;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server owns all story state; the client only requests tuning and listening. */
public final class ContactNetwork {
    private ContactNetwork() {}

    public record Action(BlockPos pos, String operation, int frequency) implements CustomPacketPayload {
        public static final Type<Action> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "radio_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.operation(), 16);
                buf.writeVarInt(p.frequency());
            },
            buf -> new Action(buf.readBlockPos(), buf.readUtf(16), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(BlockPos pos, boolean messageReady, boolean messageRead,
                           int frequency) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "radio_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeBoolean(p.messageReady());
                buf.writeBoolean(p.messageRead());
                buf.writeVarInt(p.frequency());
            },
            buf -> new Snapshot(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (packet, context) ->
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (player.blockPosition().distSqr(packet.pos()) > 64) return;
                if (!player.serverLevel().getBlockState(packet.pos()).is(FirstContact.RADIO.get())) return;
                FirstContact.Story story = FirstContact.Story.of(player.serverLevel());
                switch (packet.operation()) {
                    case "tune" -> story.setFrequency(player.serverLevel(), packet.pos(), packet.frequency());
                    case "read" -> {
                        if (story.messageReady && story.getFrequency(player.serverLevel(), packet.pos()) == 987) {
                            story.messageRead = true;
                            story.setDirty();
                        }
                    }
                    default -> { return; }
                }
                FirstContact.sendRadio(player, packet.pos());
            }));
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC,
            (packet, context) -> context.enqueueWork(() ->
                dev.zunserz.firstcontact.client.RadioScreen.receive(packet)));
    }
}
