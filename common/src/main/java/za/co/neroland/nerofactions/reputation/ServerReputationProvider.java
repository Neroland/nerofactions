package za.co.neroland.nerofactions.reputation;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * The global Core {@link ReputationProvider} NeroFactions binds at init, explicitly rebound/cleared
 * for each server lifecycle (the same shape as NeroEconomy's {@code ServerCurrencyProvider}).
 * Binding happens once at mod construction so {@code ReputationApi.hasRealProvider()} is true from
 * startup; the loaders' server-started/stopped hooks only swap which server the provider reads.
 *
 * <p>Deliberately <em>thin</em>: every call resolves the bound server's
 * {@link FactionReputationState} through {@code FactionReputationState.get} rather than caching the
 * instance. That keeps this provider identical to whatever instance {@code SavedDataRecovery} has
 * installed (a recovery mid-session swaps the cached store) and lets the periodic backup pass
 * piggyback on the lookups it already throttles.
 *
 * <p>Core hands this provider to every sibling mod, which may call it from a client, a menu, or
 * between worlds. Those calls degrade to "no standing, nothing changed" with a debug log rather
 * than throwing through the Core API and taking an unrelated mod down with them.
 *
 * <p>{@link #forgetPlayer(UUID)} delegates the row removal only — the {@code SavedDataRecovery}
 * backup refresh belongs to {@link FactionReputationState#eraseFor}, NeroFactions' registered
 * eraser. Core's erasure fan-out runs both routes (its own {@code CoreData} eraser drives
 * {@code ReputationApi.provider().forgetPlayer}); whichever runs second is an idempotent no-op, so
 * one erase request pays for exactly one removal and one backup encode.
 */
public final class ServerReputationProvider implements ReputationProvider {

    /**
     * The running server, or {@code null} between worlds. Written on the server thread from the
     * loader hooks; {@code volatile} so an integrated-server restart in the same JVM is seen at
     * once.
     */
    private volatile MinecraftServer server;

    /** Records the server that has just finished starting. Called once per world load. */
    public void bind(MinecraftServer nextServer) {
        if (nextServer == null) {
            throw new IllegalArgumentException("server");
        }
        server = nextServer;
        // Warm the store now: a corrupt reputation file surfaces (and recovers) at world load
        // rather than on the first standing check mid-gameplay.
        FactionReputationState.get(nextServer);
    }

    /** Forgets the stopped server so a later world in this JVM cannot read a stale reference. */
    public void unbind(MinecraftServer stoppedServer) {
        if (server == stoppedServer) {
            server = null;
        }
    }

    /**
     * The currently bound server, or {@code null} between worlds. The read-only seam Stage 3's
     * tier-crossing publisher uses to resolve faction definitions and online players from a
     * context (Core's {@code ReputationEvents}) that carries no server of its own.
     */
    public MinecraftServer boundServer() {
        return server;
    }

    @Override
    public int getReputation(UUID player, Identifier faction) {
        FactionReputationState state = boundState("read");
        return state == null ? 0 : state.getReputation(player, faction);
    }

    @Override
    public void setReputation(UUID player, Identifier faction, int value) {
        FactionReputationState state = boundState("write");
        if (state != null) {
            state.setReputation(player, faction, value);
        }
    }

    @Override
    public void forgetPlayer(UUID player) {
        FactionReputationState state = boundState("erasure");
        if (state != null) {
            state.forgetPlayer(player);
        }
    }

    private FactionReputationState boundState(String operation) {
        MinecraftServer current = server;
        if (current == null) {
            NeroFactionsCommon.LOGGER.debug(
                    "[NeroFactions] Reputation {} ignored; no server is bound.", operation);
            return null;
        }
        return FactionReputationState.get(current);
    }
}
