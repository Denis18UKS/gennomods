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

/**
 * All receptions are decided by the server. No client can conjure up the
 * story signal or an extremely rare cosmic broadcast.
 */
public final class ContactNetwork {
    private ContactNetwork() {}

    public record Action(BlockPos pos, String operation, int frequency)
            implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "radio_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC =
            StreamCodec.of((buf,p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.operation(), 16);
                buf.writeVarInt(p.frequency());
            },buf -> new Action(buf.readBlockPos(),buf.readUtf(16),buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** stationId 0 means no reception; 99 means recorded first-contact story. */
    public record Snapshot(BlockPos pos, int frequency,
                           int stationId, int signalStrength)
            implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "radio_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC =
            StreamCodec.of((buf,p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeVarInt(p.frequency());
                buf.writeVarInt(p.stationId());
                buf.writeVarInt(p.signalStrength());
            },buf -> new Snapshot(buf.readBlockPos(),buf.readVarInt(),
                buf.readVarInt(),buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar=event.registrar("2");
        registrar.playToServer(Action.TYPE,Action.CODEC,(packet,context) ->
            context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                if (player.blockPosition().distSqr(packet.pos()) > 64) return;
                if (!player.serverLevel().getBlockState(packet.pos())
                        .is(FirstContact.RADIO.get())) return;

                FirstContact.Story story=FirstContact.Story.of(player.serverLevel());
                switch (packet.operation()) {
                    case "tune" -> {
                        // Numeric protocol uses 0.1 MHz units and validates range.
                        if (packet.frequency() < RadioSignals.MIN
                                || packet.frequency() > RadioSignals.MAX) return;
                        story.setFrequency(player.serverLevel(),packet.pos(),packet.frequency());
                    }
                    case "scan" -> {
                        int current=story.getFrequency(player.serverLevel(),packet.pos());
                        int found=RadioSignals.scan(player.serverLevel(),current,story.messageReady);
                        story.setFrequency(player.serverLevel(),packet.pos(),found);
                    }
                    case "poll" -> { /* read-only refresh while GUI is open */ }
                    default -> { return; }
                }
                FirstContact.sendRadio(player,packet.pos());
            }));
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context) ->
            context.enqueueWork(() ->
                dev.zunserz.firstcontact.client.RadioScreen.receive(packet)));
    }
}
