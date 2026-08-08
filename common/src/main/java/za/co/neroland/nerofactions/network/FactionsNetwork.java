package za.co.neroland.nerofactions.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerofactions.platform.Services;

/**
 * The cross-loader payload registry: NeroFactions declares its payloads here once (type + stream
 * codec + handler), and each loader module iterates the lists and wires them to its own networking
 * API — NeoForge's {@code PayloadRegistrar}, Forge's {@code ChannelBuilder}, Fabric's
 * {@code PayloadTypeRegistry} + {@code Client/ServerPlayNetworking}. Sending goes through the
 * {@link Services#NETWORK} seam. The channel is {@code nerofactions:main}.
 *
 * <p>This is Neroland Core's {@code CoreNetwork} architecture reproduced on NeroFactions' own
 * channel. It cannot reuse Core's instance: Core drains its payload lists during Core's own
 * bootstrap (on Forge the channel is {@code build()}-sealed inside Core's constructor), so a
 * downstream registration would be silently dropped — see
 * {@link za.co.neroland.nerofactions.platform.NetworkPlatform} for the full reasoning.
 *
 * <p><b>No payloads are declared yet.</b> The channel and the declare-once/register-per-loader
 * split are laid down in the foundation stage so later stages (reputation sync, faction
 * membership screens) only have to add {@code clientbound(...)}/{@code serverbound(...)} lines to
 * {@link #init()} — the loader registration passes already iterate whatever is declared, and each
 * handles an empty list cleanly. NeroFactions remains server-authoritative end to end: reputation
 * and membership are decided server-side and the client renders what it is told.
 */
public final class FactionsNetwork {

    /** A server &rarr; client payload plus the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /** A client &rarr; server payload plus the server-side handler (with the sending player). */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private static boolean declared;

    private FactionsNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    /** Every declared server &rarr; client payload, for each loader's registration pass. */
    public static List<Clientbound<?>> clientbound() {
        return CLIENTBOUND;
    }

    /** Every declared client &rarr; server payload, for each loader's registration pass. */
    public static List<Serverbound<?>> serverbound() {
        return SERVERBOUND;
    }

    /**
     * Declares the payloads. Called once from common init, before any loader registers them (each
     * loader entry point runs common init first, then its own network registration). On Forge in
     * particular the channel is sealed at {@code build()}, so a payload declared later would never
     * exist. Currently declares nothing — later stages add their payload lines here.
     */
    public static void init() {
        if (declared) {
            return; // defensive: a second call must not duplicate registrations
        }
        declared = true;
        // Later stages declare payloads here (clientbound(...) / serverbound(...)).
    }

    /**
     * Drops every client-side mirror. Each loader calls this when the client leaves a world or
     * server, so one session's faction state can never bleed into the next — or appear at all on a
     * server that does not run NeroFactions. No client mirrors exist yet; later stages that add a
     * synced cache must clear it here.
     */
    public static void clearClientCaches() {
        // Later stages clear their client mirror caches here.
    }
}
