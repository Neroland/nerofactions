package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * Plain-JVM tests for the persistent reputation store — the state is constructed directly rather
 * than through {@code get(server)} (that lookup needs a live {@code MinecraftServer}'s data
 * storage), the same style as Core's {@code ErasureConformanceTest}. The provider bound to
 * {@code ReputationApi} before each test is captured and restored after it, so a test binding this
 * store cannot leak into another suite ({@code ReputationApi} has no reset seam; restoring the
 * captured provider is the clean equivalent).
 *
 * <p>Privacy note: every test uses a random UUID and never logs it.
 */
class FactionReputationStateTest {

    private static final Identifier ALLIANCE = Identifier.parse("nerofactions:test_alliance");
    private static final Identifier SYNDICATE = Identifier.parse("nerofactions:test_syndicate");

    private ReputationProvider originalProvider;

    @BeforeEach
    void captureProvider() {
        originalProvider = ReputationApi.provider();
    }

    @AfterEach
    void restoreProvider() {
        ReputationApi.setProvider(originalProvider);
    }

    @Test
    void setThenGetRoundTrips() {
        FactionReputationState state = new FactionReputationState();
        UUID player = UUID.randomUUID();

        state.setReputation(player, ALLIANCE, 40);
        state.setReputation(player, SYNDICATE, -15);

        assertEquals(40, state.getReputation(player, ALLIANCE));
        assertEquals(-15, state.getReputation(player, SYNDICATE), "negative standing must round-trip");
    }

    @Test
    void absentPlayerAndAbsentFactionReadZero() {
        FactionReputationState state = new FactionReputationState();
        UUID stranger = UUID.randomUUID();

        assertEquals(0, state.getReputation(stranger, ALLIANCE));
        assertFalse(state.hasRow(stranger), "a read must never create a row");
        assertEquals(0, state.rowCount());

        state.setReputation(stranger, ALLIANCE, 7);
        assertEquals(0, state.getReputation(stranger, SYNDICATE),
                "a faction with no recorded standing reads 0 even for a known player");
    }

    @Test
    void writingZeroRemovesTheEntryAndAnEmptiedRow() {
        FactionReputationState state = new FactionReputationState();
        UUID player = UUID.randomUUID();

        state.setReputation(player, ALLIANCE, 25);
        state.setReputation(player, SYNDICATE, 10);
        state.setReputation(player, SYNDICATE, 0);

        assertEquals(0, state.getReputation(player, SYNDICATE));
        assertTrue(state.hasRow(player), "the other faction's standing must survive");

        state.setReputation(player, ALLIANCE, 0);
        assertFalse(state.hasRow(player), "writing the last standing to 0 must drop the whole row");
        assertEquals(0, state.rowCount(), "absent==0 is canonical; explicit zeros are never stored");
    }

    @Test
    void forgetPlayerClearsAllFactionsButKeepsOtherPlayers() {
        FactionReputationState state = new FactionReputationState();
        UUID erased = UUID.randomUUID();
        UUID retained = UUID.randomUUID();

        state.setReputation(erased, ALLIANCE, 60);
        state.setReputation(erased, SYNDICATE, -30);
        state.setReputation(retained, ALLIANCE, 12);

        state.forgetPlayer(erased);

        assertFalse(state.hasRow(erased), "every standing of the erased player must be gone");
        assertEquals(0, state.getReputation(erased, ALLIANCE));
        assertEquals(0, state.getReputation(erased, SYNDICATE),
                "negative standing is cleared too — the documented no-tombstone consequence");
        assertTrue(state.hasRow(retained), "another player's rows must survive");
        assertEquals(12, state.getReputation(retained, ALLIANCE));
        assertEquals(1, state.rowCount());
    }

    @Test
    void forgetPlayerOnUnknownUuidIsANoOp() {
        FactionReputationState state = new FactionReputationState();
        UUID known = UUID.randomUUID();
        state.setReputation(known, ALLIANCE, 5);

        assertDoesNotThrow(() -> state.forgetPlayer(UUID.randomUUID()));
        assertDoesNotThrow(() -> state.forgetPlayer(null));

        assertEquals(1, state.rowCount(), "an unknown UUID must change nothing");
        assertEquals(5, state.getReputation(known, ALLIANCE));
    }

    /**
     * The load-bearing one: Core's {@code CoreData} eraser purges reputation by calling
     * {@code ReputationApi.provider().forgetPlayer(uuid)} — that route must actually reach and
     * clear this store once it is the bound provider.
     */
    @Test
    void forgetPlayerThroughReputationApiClearsStoredStanding() {
        FactionReputationState state = new FactionReputationState();
        ReputationApi.setProvider(state);
        assertTrue(ReputationApi.hasRealProvider(),
                "binding the persistent store must count as a real provider");

        UUID player = UUID.randomUUID();
        ReputationApi.setReputation(player, ALLIANCE, 40);
        ReputationApi.setReputation(player, SYNDICATE, -5);
        assertEquals(40, ReputationApi.getReputation(player, ALLIANCE));
        assertTrue(state.hasRow(player), "the Api facade must have written into the bound store");

        ReputationApi.provider().forgetPlayer(player);

        assertFalse(state.hasRow(player), "erasure through the Api-bound provider must clear the store");
        assertEquals(0, ReputationApi.getReputation(player, ALLIANCE));
        assertEquals(0, ReputationApi.getReputation(player, SYNDICATE));
    }
}
