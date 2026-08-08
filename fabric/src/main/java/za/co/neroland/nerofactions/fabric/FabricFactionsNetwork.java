package za.co.neroland.nerofactions.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerofactions.network.FactionsNetwork;
import za.co.neroland.nerofactions.platform.NetworkPlatform;

/**
 * Fabric side of the networking seam. {@link #registerCommon()} (mod init, both sides) registers the
 * payload types; {@link #registerClient()} (client init) registers the receivers, keeping
 * {@code ClientPlayNetworking} off the dedicated server. Registered as the {@link NetworkPlatform}
 * implementation via {@code META-INF/services}.
 *
 * <p>Receivers hop to the client thread via {@code context.client().execute}, which is what makes
 * plain-data client mirror caches safe without any locking. The registration passes iterate
 * whatever {@link FactionsNetwork} declares — an empty list (the current foundation state) simply
 * registers nothing.
 */
public final class FabricFactionsNetwork implements NetworkPlatform {

    /**
     * Mod-init (both sides): the clientbound payload <em>types</em>, plus the serverbound types and
     * their receivers. Serverbound receivers belong here rather than in {@link #registerClient()},
     * because they are what a dedicated server needs — and keeping them out of the client method is
     * what stops {@code ClientPlayNetworking} ever loading on one.
     */
    public static void registerCommon() {
        for (FactionsNetwork.Clientbound<?> cb : FactionsNetwork.clientbound()) {
            registerClientboundType(cb);
        }
        for (FactionsNetwork.Serverbound<?> sb : FactionsNetwork.serverbound()) {
            registerServerbound(sb);
        }
    }

    /** Client-init: clientbound receivers (client-only API). */
    public static void registerClient() {
        for (FactionsNetwork.Clientbound<?> cb : FactionsNetwork.clientbound()) {
            registerClientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            FactionsNetwork.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            FactionsNetwork.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            FactionsNetwork.Serverbound<T> sb) {
        PayloadTypeRegistry.serverboundPlay().register(sb.type(), sb.codec());
        ServerPlayNetworking.registerGlobalReceiver(sb.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            // Hop to the server thread: every intent handler touches saved data.
            player.level().getServer().execute(() -> sb.handler().accept(payload, player));
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
