package za.co.neroland.nerofactions.neoforge;

//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
//? if >=1.21.6 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerofactions.network.FactionsNetwork;
import za.co.neroland.nerofactions.platform.NetworkPlatform;

/**
 * NeoForge side of the networking seam: registers every {@link FactionsNetwork} payload during
 * {@code RegisterPayloadHandlersEvent} and implements the send methods. Registered as the
 * {@link NetworkPlatform} implementation via {@code META-INF/services}.
 *
 * <p>The registrar is {@code optional()}, so a vanilla (or NeroFactions-less) client can still
 * connect — it simply never receives a NeroFactions payload. An empty payload list (the current
 * foundation state) registers nothing and is harmless.
 *
 * <p>Handlers run through {@code context.enqueueWork}, i.e. on the client thread, which is what
 * makes plain-data client mirror caches safe without any locking.
 */
public final class NeoForgeFactionsNetwork implements NetworkPlatform {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeFactionsNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (FactionsNetwork.Clientbound<?> cb : FactionsNetwork.clientbound()) {
            registerClientbound(registrar, cb);
        }
        for (FactionsNetwork.Serverbound<?> sb : FactionsNetwork.serverbound()) {
            registerServerbound(registrar, sb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadRegistrar registrar, FactionsNetwork.Clientbound<T> cb) {
        registrar.playToClient(cb.type(), cb.codec(),
                (payload, context) -> context.enqueueWork(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadRegistrar registrar, FactionsNetwork.Serverbound<T> sb) {
        registrar.playToServer(sb.type(), sb.codec(),
                (payload, context) -> context.enqueueWork(() -> {
                    // enqueueWork puts us on the server thread, which every intent handler needs.
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        sb.handler().accept(payload, serverPlayer);
                    }
                }));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.6 {
        ClientPacketDistributor.sendToServer(payload);
        //?} else {
        /*PacketDistributor.sendToServer(payload);
        *///?}
    }
}
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.network.FactionsNetwork;
import za.co.neroland.nerolandcore.network.legacy.LegacyPayload;
import za.co.neroland.nerolandcore.network.legacy.LegacyStreamCodec;
import za.co.neroland.nerofactions.platform.NetworkPlatform;

public final class NeoForgeFactionsNetwork implements NetworkPlatform {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeFactionsNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar(NeroFactionsCommon.MOD_ID).versioned("1").optional();
        FactionsNetwork.clientbound().forEach(packet -> registerClientbound(registrar, packet));
        FactionsNetwork.serverbound().forEach(packet -> registerServerbound(registrar, packet));
    }

    private static <T extends LegacyPayload> void registerClientbound(IPayloadRegistrar registrar,
            FactionsNetwork.Clientbound<T> packet) {
        registrar.play(packet.type().id(),
                buffer -> new WirePayload<>(packet.type(), packet.codec(), packet.codec().decode(buffer)),
                builder -> {
                    builder.client((payload, context) ->
                            context.workHandler().execute(() -> packet.handler().accept(payload.message())));
                });
    }

    private static <T extends LegacyPayload> void registerServerbound(IPayloadRegistrar registrar,
            FactionsNetwork.Serverbound<T> packet) {
        registrar.play(packet.type().id(),
                buffer -> new WirePayload<>(packet.type(), packet.codec(), packet.codec().decode(buffer)),
                builder -> {
                    builder.server((payload, context) -> context.workHandler().execute(() ->
                            context.player().ifPresent(player -> {
                                if (player instanceof ServerPlayer serverPlayer) {
                                    packet.handler().accept(payload.message(), serverPlayer);
                                }
                            })));
                });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, LegacyPayload payload) {
        if (!NetworkRegistry.getInstance().isConnected(player.connection, payload.type().id())) {
            return;
        }
        for (FactionsNetwork.Clientbound<?> packet : FactionsNetwork.clientbound()) {
            if (packet.type().equals(payload.type())) {
                PacketDistributor.PLAYER.with(player).send(wrapClientbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered clientbound payload " + payload.type().id());
    }

    @Override
    public void sendToServer(LegacyPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.getInstance().isConnected(connection, payload.type().id())) {
            return;
        }
        for (FactionsNetwork.Serverbound<?> packet : FactionsNetwork.serverbound()) {
            if (packet.type().equals(payload.type())) {
                PacketDistributor.SERVER.noArg().send(wrapServerbound(packet, payload));
                return;
            }
        }
        throw new IllegalArgumentException("Unregistered serverbound payload " + payload.type().id());
    }

    private static <T extends LegacyPayload> WirePayload<T> wrapClientbound(FactionsNetwork.Clientbound<T> packet,
            LegacyPayload payload) {
        return new WirePayload<>(packet.type(), packet.codec(), packet.type().payloadClass().cast(payload));
    }

    private static <T extends LegacyPayload> WirePayload<T> wrapServerbound(FactionsNetwork.Serverbound<T> packet,
            LegacyPayload payload) {
        return new WirePayload<>(packet.type(), packet.codec(), packet.type().payloadClass().cast(payload));
    }

    private record WirePayload<T extends LegacyPayload>(LegacyPayload.Type<T> type,
            LegacyStreamCodec<? super FriendlyByteBuf, T> codec, T message)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        @Override
        public ResourceLocation id() {
            return type.id();
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            codec.encode(buffer, message);
        }
    }
}
*///?}
