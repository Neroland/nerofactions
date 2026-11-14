package za.co.neroland.nerofactions.data;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * Registers NeroFactions' player-data stores with the shared privacy hooks and binds the real Core
 * reputation provider — the same one-shot registrar shape as Core's {@code CoreData}. Called once
 * from {@link NeroFactionsCommon#init()} (step 4), before any faction data can exist, because
 * registering late is how an erasure request silently misses a store (POPIA/GDPR).
 *
 * <p>{@link #eraseLocal(MinecraftServer, UUID)} is the <b>one</b> local erasure path — the
 * registered eraser and {@link RetentionSweep NeroFactions' own retention sweep} both call it, and
 * it walks {@link FactionsStores}, the same list the sweep and {@link PlayerDataExport} use, so
 * "what an erasure request clears", "what the sweep clears" and "what an access request returns"
 * cannot drift apart. For every persistent store it forgets the player's rows and refreshes the
 * {@code SavedDataRecovery} backup; then it clears every transient in-memory map. Core's
 * {@code CoreData} separately drives {@code ReputationApi.provider().forgetPlayer(uuid)} in the
 * same fan-out; either ordering, one erase request clears everything.
 *
 * <p>Every step is guarded: a failing store (a backup that cannot be written, say) does not stop
 * the stores after it. The first failure is rethrown once every step has run, with later ones
 * attached as suppressed, so Core's fan-out still reports the erasure as incomplete.
 *
 * <p>Verified by {@code NeroFactionsErasureConformanceTest}, which runs Core's
 * {@code ErasureConformance} harness against {@link #eraseLocal(FactionsStores.Snapshot,
 * FactionsStores.BackupHook, MinecraftServer, UUID)} — the same code the live path runs.
 */
public final class NeroFactionsData {

    private NeroFactionsData() {
    }

    public static void init() {
        // ONE eraser in the shared fan-out, so a single erasure request clears every store in
        // FactionsStores (each with its recovery backup refreshed) and every transient map.
        PlayerDataErasure.register(NeroFactionsData::eraseLocal);
        // Replaces Core's in-memory default so ReputationApi.hasRealProvider() is true from
        // startup; the provider itself degrades safely until a server binds (see its javadoc).
        ReputationApi.setProvider(NeroFactionsCommon.REPUTATION_PROVIDER);
    }

    /**
     * Purges everything NeroFactions holds for {@code player} on the live server, then re-syncs
     * the (now empty) standings and contracts to the player if they are online. Called by the
     * registered {@code PlayerDataErasure} eraser and by {@link RetentionSweep}. Never logs the
     * player's identity.
     */
    public static void eraseLocal(MinecraftServer server, UUID player) {
        Throwable failure = null;
        try {
            eraseLocal(FactionsStores.live(server), (store, uuid) -> store.refreshBackup(server, uuid),
                    server, player);
        } catch (Throwable t) {
            failure = t;
        }
        try {
            za.co.neroland.nerofactions.network.StandingSync.send(server, player);
            var online = server.getPlayerList().getPlayer(player);
            if (online != null) za.co.neroland.nerofactions.platform.Services.NETWORK.sendToPlayer(online,
                    new za.co.neroland.nerofactions.network.ContractSnapshot(java.util.List.of(), Long.MIN_VALUE, false, ""));
        } catch (Throwable t) {
            failure = FactionsStores.collect(failure, t);
        }
        if (failure != null) {
            throw FactionsStores.rethrow(failure);
        }
    }

    /**
     * The erasure itself, over {@code stores} with an injectable {@code backup} hook (the seam the
     * conformance test drives). Each persistent store: forget the player's rows, then refresh its
     * backup; then each transient map. Every step runs even if an earlier one failed; the first
     * failure is rethrown at the end with the rest suppressed. {@code server} is only handed to the
     * transient forgets (null in plain-JVM tests).
     */
    static void eraseLocal(FactionsStores.Snapshot stores, FactionsStores.BackupHook backup,
            MinecraftServer server, UUID player) {
        Throwable failure = null;
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            try {
                store.forgetIn(stores, player);
                backup.backup(store, player);
            } catch (Throwable t) {
                failure = FactionsStores.collect(failure, t);
            }
        }
        for (FactionsStores.Transient state : FactionsStores.TRANSIENT) {
            try {
                state.forget().accept(server, player);
            } catch (Throwable t) {
                failure = FactionsStores.collect(failure, t);
            }
        }
        if (failure != null) {
            throw FactionsStores.rethrow(failure);
        }
    }
}
