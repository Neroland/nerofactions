package za.co.neroland.nerofactions.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * Plain-JVM tests of the deterministic decay core: N whole elapsed days erode N × rate toward 0,
 * partial days do nothing, the erosion floors at 0 (never overshoots into the other sign), and a
 * faction whose standing reaches 0 stops being tracked entirely.
 */
class FactionDecayTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final long T0 = 1_700_000_000_000L;
    private static final int RATE = 25;

    private ReputationProvider originalProvider;
    private FactionMembershipState state;
    private FactionReputationState reputation;
    private UUID player;

    @BeforeEach
    void setUp() {
        originalProvider = ReputationApi.provider();
        state = new FactionMembershipState();
        reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        player = UUID.randomUUID();
    }

    @AfterEach
    void restoreProvider() {
        ReputationApi.setProvider(originalProvider);
    }

    private void leaveWithStanding(int standing) {
        ReputationApi.setReputation(player, GUILD, standing);
        state.recordJoin(player, GUILD, T0);
        state.recordLeave(player, GUILD, T0, 0L);
    }

    @Test
    void wholeElapsedDaysErodeAtTheConfiguredRate() {
        leaveWithStanding(1000);

        FactionDecay.applyDecay(state, player, T0 + 3 * FactionDecay.DAY_MS, RATE);
        assertEquals(1000 - 3 * RATE, ReputationApi.getReputation(player, GUILD));

        // The bookmark advanced by exactly the days applied: an immediate second pass is a no-op.
        FactionDecay.applyDecay(state, player, T0 + 3 * FactionDecay.DAY_MS, RATE);
        assertEquals(1000 - 3 * RATE, ReputationApi.getReputation(player, GUILD),
                "the same elapsed days must never be applied twice");

        FactionDecay.applyDecay(state, player, T0 + 5 * FactionDecay.DAY_MS, RATE);
        assertEquals(1000 - 5 * RATE, ReputationApi.getReputation(player, GUILD));
    }

    @Test
    void partialDaysDoNothing() {
        leaveWithStanding(100);

        FactionDecay.applyDecay(state, player, T0 + FactionDecay.DAY_MS - 1, RATE);
        assertEquals(100, ReputationApi.getReputation(player, GUILD));
        assertTrue(state.leftFactions(player).containsKey(GUILD), "still tracked");
    }

    @Test
    void decayFloorsAtZeroAndStopsTracking() {
        leaveWithStanding(30);

        // 2 whole days at 25/day would be 50 — but erosion floors at 0, never overshoots.
        FactionDecay.applyDecay(state, player, T0 + 2 * FactionDecay.DAY_MS, RATE);
        assertEquals(0, ReputationApi.getReputation(player, GUILD));
        assertEquals(0, state.leftFactions(player).size(),
                "a faction decayed to 0 must stop being tracked");
    }

    @Test
    void negativeStandingDecaysUpTowardZero() {
        leaveWithStanding(-60);

        FactionDecay.applyDecay(state, player, T0 + FactionDecay.DAY_MS, RATE);
        assertEquals(-35, ReputationApi.getReputation(player, GUILD),
                "decay erodes toward 0 from either sign — the grudge fades too");

        FactionDecay.applyDecay(state, player, T0 + 3 * FactionDecay.DAY_MS, RATE);
        assertEquals(0, ReputationApi.getReputation(player, GUILD));
        assertEquals(0, state.leftFactions(player).size());
    }

    @Test
    void zeroStandingIsReleasedWithoutArithmetic() {
        leaveWithStanding(0); // never stored: absent == 0

        FactionDecay.applyDecay(state, player, T0 + 1, RATE);
        assertEquals(0, state.leftFactions(player).size(),
                "tracking something that can never change is pointless — released immediately");
    }
}
