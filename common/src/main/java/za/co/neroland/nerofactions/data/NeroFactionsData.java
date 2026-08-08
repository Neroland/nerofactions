package za.co.neroland.nerofactions.data;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * Registers NeroFactions' player-data stores with the shared privacy hooks and binds the real Core
 * reputation provider — the same one-shot registrar shape as Core's {@code CoreData}. Called once
 * from {@link NeroFactionsCommon#init()} (step 4), before any faction data can exist, because
 * registering late is how an erasure request silently misses a store (POPIA/GDPR).
 *
 * <p>{@link #eraseLocal(MinecraftServer, UUID)} is the <b>one</b> local erasure path — the
 * registered eraser and {@link RetentionSweep NeroFactions' own retention sweep} both call it, so
 * "what an erasure request clears" and "what the sweep clears" can never drift apart. It erases the
 * <em>stores</em> directly ({@link FactionReputationState#eraseFor} and
 * {@link FactionMembershipState#eraseFor}: row removal + immediate {@code SavedDataRecovery} backup
 * refresh each) plus the trade terminal's transient in-memory selection. Core's {@code CoreData}
 * separately drives {@code ReputationApi.provider().forgetPlayer(uuid)} in the same fan-out; that
 * route reaches the reputation store through {@code ServerReputationProvider} and finds the row
 * already gone (or removes it first, in which case {@code eraseFor}'s removal is the no-op and its
 * backup refresh still runs). Either ordering, one erase request clears everything exactly once.
 *
 * <p>This mod's erasure conformance is mechanically verified by
 * {@code NeroFactionsErasureConformanceTest}, which runs Core's {@code ErasureConformance} harness
 * against mirrored registrations of exactly this path.
 */
public final class NeroFactionsData {

    private NeroFactionsData() {
    }

    public static void init() {
        // ONE eraser in the shared fan-out, so a single erasure request clears reputation AND
        // membership (current factions, join/leave timestamps, cooldown, decay bookkeeping,
        // per-source daily accrual counters, reward watermarks) AND the terminal's transient
        // session row — each store eraser also refreshes its recovery backup.
        PlayerDataErasure.register(NeroFactionsData::eraseLocal);
        // Replaces Core's in-memory default so ReputationApi.hasRealProvider() is true from
        // startup; the provider itself degrades safely until a server binds (see its javadoc).
        ReputationApi.setProvider(NeroFactionsCommon.REPUTATION_PROVIDER);
    }

    /**
     * Purges everything NeroFactions holds for {@code player}: both persistent stores (with their
     * {@code SavedDataRecovery} backups refreshed in the same request, so erased rows do not
     * survive in the backup files) and the trade terminal's in-memory session selection. Called by
     * the registered {@code PlayerDataErasure} eraser (a Core erasure request or Core's own
     * retention sweep) and by {@link RetentionSweep} (this mod's own inactivity sweep). Never logs
     * the player's identity.
     */
    public static void eraseLocal(MinecraftServer server, UUID player) {
        FactionReputationState.eraseFor(server, player);
        FactionMembershipState.eraseFor(server, player);
        TerminalSessions.clear(player);
    }
}
