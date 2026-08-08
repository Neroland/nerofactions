package za.co.neroland.nerofactions.membership;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerofactions.membership.FactionMembership.JoinResult;
import za.co.neroland.nerofactions.membership.FactionMembership.LeaveResult;
import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * Plain-JVM tests of the membership rule engine, driven through the package-private parameterised
 * overloads (no server, explicit clock values, explicit config inputs). The provider bound to
 * {@code ReputationApi} is captured and restored around each test, the Stage 1 convention.
 */
class FactionMembershipTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");
    private static final Identifier GHOST = Identifier.parse("nerofactions:test_ghost");

    private static final Predicate<Identifier> KNOWN = id -> GUILD.equals(id) || UNION.equals(id);
    private static final long T0 = 1_700_000_000_000L;
    private static final long COOLDOWN_MS = 30L * FactionMembership.MINUTE_MS;

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
    void joinLeaveRoundTripAndTypedRefusals() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        assertEquals(JoinResult.UNKNOWN_FACTION,
                FactionMembership.join(state, KNOWN, player, GHOST, T0, false));
        assertEquals(JoinResult.JOINED,
                FactionMembership.join(state, KNOWN, player, GUILD, T0, false));
        assertEquals(Set.of(GUILD), state.membershipsOf(player));
        assertEquals(T0, state.joinedAt(player, GUILD));
        assertEquals(JoinResult.ALREADY_MEMBER,
                FactionMembership.join(state, KNOWN, player, GUILD, T0 + 1, false));

        assertEquals(LeaveResult.NOT_A_MEMBER,
                FactionMembership.leave(state, player, UNION, T0 + 2, COOLDOWN_MS, 0));
        assertEquals(LeaveResult.LEFT,
                FactionMembership.leave(state, player, GUILD, T0 + 2, COOLDOWN_MS, 0));
        assertEquals(Set.of(), state.membershipsOf(player));
    }

    @Test
    void singleAllegianceRefusesASecondFactionUntilTheToggleAllowsIt() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        assertEquals(JoinResult.JOINED,
                FactionMembership.join(state, KNOWN, player, GUILD, T0, false));
        assertEquals(JoinResult.OTHER_ALLEGIANCE,
                FactionMembership.join(state, KNOWN, player, UNION, T0 + 1, false),
                "the default single-allegiance rule refuses a second faction");

        assertEquals(JoinResult.JOINED,
                FactionMembership.join(state, KNOWN, player, UNION, T0 + 1, true),
                "allowMultipleFactions=true permits a second faction");
        assertEquals(Set.of(GUILD, UNION), state.membershipsOf(player));
    }

    @Test
    void leavingArmsTheJoinCooldownUntilItElapses() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        FactionMembership.join(state, KNOWN, player, GUILD, T0, false);
        FactionMembership.leave(state, player, GUILD, T0, COOLDOWN_MS, 0);
        assertEquals(T0 + COOLDOWN_MS, state.cooldownUntil(player));

        assertEquals(JoinResult.ON_COOLDOWN,
                FactionMembership.join(state, KNOWN, player, UNION, T0 + COOLDOWN_MS - 1, false));
        assertEquals(JoinResult.JOINED,
                FactionMembership.join(state, KNOWN, player, UNION, T0 + COOLDOWN_MS, false),
                "the instant the cooldown elapses, joining works again");
    }

    @Test
    void leavingAppliesTheSwitchPenaltyThroughTheReputationApi() {
        FactionMembershipState state = new FactionMembershipState();
        FactionReputationState reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        UUID player = UUID.randomUUID();

        ReputationApi.setReputation(player, GUILD, 120);
        FactionMembership.join(state, KNOWN, player, GUILD, T0, false);
        assertEquals(LeaveResult.LEFT,
                FactionMembership.leave(state, player, GUILD, T0 + 1, COOLDOWN_MS, 50));

        assertEquals(70, ReputationApi.getReputation(player, GUILD),
                "switchPenaltyPoints must be applied as a negative adjustment, not a wipe");
        assertEquals(Set.of(GUILD), state.leftFactions(player).keySet(),
                "leaving must start decay bookkeeping for the left faction");
    }

    @Test
    void rejoiningALeftFactionStopsItsDecayTracking() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        FactionMembership.join(state, KNOWN, player, GUILD, T0, false);
        FactionMembership.leave(state, player, GUILD, T0, 0L, 0);
        assertTrue(state.leftFactions(player).containsKey(GUILD));

        assertEquals(JoinResult.JOINED,
                FactionMembership.join(state, KNOWN, player, GUILD, T0 + 1, false));
        assertEquals(Set.of(), state.leftFactions(player).keySet(),
                "a member's standing must not decay — rejoining clears the left entry");
    }
}
