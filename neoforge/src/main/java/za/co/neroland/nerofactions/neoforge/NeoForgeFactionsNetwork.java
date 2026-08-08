package za.co.neroland.nerofactions.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
