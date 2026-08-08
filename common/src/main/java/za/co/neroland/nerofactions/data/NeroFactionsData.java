package za.co.neroland.nerofactions.data;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * Registers NeroFactions' player-data stores with the shared privacy hooks and binds the real Core
 * reputation provider — the same one-shot registrar shape as Core's {@code CoreData}. Called once
 * from {@link NeroFactionsCommon#init()} (step 4), before any faction data can exist, because
 * registering late is how an erasure request silently misses a store (POPIA/GDPR).
 *
 * <p>The eraser erases the <em>store</em> directly ({@link FactionReputationState#eraseFor}: row
 * removal + immediate {@code SavedDataRecovery} backup refresh). Core's {@code CoreData} separately
 * drives {@code ReputationApi.provider().forgetPlayer(uuid)} in the same fan-out; that route
 * reaches the same store through {@code ServerReputationProvider} and finds the row already gone
 * (or removes it first, in which case {@code eraseFor}'s removal is the no-op and its backup
 * refresh still runs). Either ordering, one erase request clears everything exactly once.
 */
public final class NeroFactionsData {

    private NeroFactionsData() {
    }

    public static void init() {
        PlayerDataErasure.register(FactionReputationState::eraseFor);
        // Replaces Core's in-memory default so ReputationApi.hasRealProvider() is true from
        // startup; the provider itself degrades safely until a server binds (see its javadoc).
        ReputationApi.setProvider(NeroFactionsCommon.REPUTATION_PROVIDER);
    }
}
