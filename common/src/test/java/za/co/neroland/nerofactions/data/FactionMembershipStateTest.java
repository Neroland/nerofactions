package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * Plain-JVM tests for the persistent membership store, constructed directly (the {@code get(server)}
 * lookup needs a live server), the same style as {@code FactionReputationStateTest}. The
 * erasure test is the load-bearing one: a single {@code forgetPlayer} must clear every section —
 * memberships, cooldown, decay bookkeeping AND accrual counters — because that is exactly what the
 * registered {@code eraseFor} eraser delegates to.
 */
class FactionMembershipStateTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");
    private static final long T0 = 1_700_000_000_000L;
    private static final long DAY = 20_000L;

    @Test
    void membershipRoundTripsAndUnknownPlayersReadEmpty() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        assertEquals(Set.of(), state.membershipsOf(UUID.randomUUID()));
        assertFalse(state.hasRow(player), "a read must never create a row");

        state.recordJoin(player, GUILD, T0);
        assertTrue(state.isMember(player, GUILD));
        assertEquals(T0, state.joinedAt(player, GUILD));
        assertEquals(Set.of(GUILD), state.membershipsOf(player));
        assertEquals(0L, state.joinedAt(player, UNION));
    }

    @Test
    void accrualRowsResetImplicitlyOnANewDayStamp() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        state.addAccrued(player, GUILD, "quest", DAY, 120);
        state.addAccrued(player, GUILD, "quest", DAY, 30);
        assertEquals(150, state.accruedToday(player, GUILD, "quest", DAY));
        assertEquals(0, state.accruedToday(player, GUILD, "combat", DAY), "per source");
        assertEquals(0, state.accruedToday(player, UNION, "quest", DAY), "per faction");

        assertEquals(0, state.accruedToday(player, GUILD, "quest", DAY + 1),
                "yesterday's counter reads 0 against today's stamp");
        state.addAccrued(player, GUILD, "quest", DAY + 1, 10);
        assertEquals(10, state.accruedToday(player, GUILD, "quest", DAY + 1),
                "the first write of a new day replaces the stale counter");
    }

    @Test
    void elapsedCooldownClearsAndPrunesAnOtherwiseEmptyRow() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        state.recordJoin(player, GUILD, T0);
        assertTrue(state.recordLeave(player, GUILD, T0, T0 + 1000));
        assertFalse(state.recordLeave(player, GUILD, T0, T0 + 1000), "not a member any more");
        assertEquals(T0 + 1000, state.cooldownUntil(player));

        // Decay finishes → left entry removed; then the cooldown elapses → row fully pruned.
        state.advanceDecay(player, GUILD, T0, true);
        assertTrue(state.hasRow(player), "a live cooldown keeps the row");
        state.clearCooldownIfElapsed(player, T0 + 999);
        assertTrue(state.hasRow(player), "a cooldown that has not elapsed is kept");
        state.clearCooldownIfElapsed(player, T0 + 1000);
        assertFalse(state.hasRow(player),
                "an emptied row is pruned — absent and never-seen are identical on disk");
    }

    @Test
    void oneForgetPlayerClearsMembershipCooldownDecayAndAccrual() {
        FactionMembershipState state = new FactionMembershipState();
        UUID erased = UUID.randomUUID();
        UUID retained = UUID.randomUUID();

        // Populate every section for the player being erased.
        state.recordJoin(erased, GUILD, T0);
        state.recordJoin(erased, UNION, T0);
        state.recordLeave(erased, UNION, T0 + 1, T0 + 100_000);
        state.addAccrued(erased, GUILD, "quest", DAY, 120);
        state.recordJoin(retained, GUILD, T0);
        state.addAccrued(retained, GUILD, "quest", DAY, 5);

        state.forgetPlayer(erased);

        assertFalse(state.hasRow(erased), "the whole row must be gone in one request");
        assertEquals(Set.of(), state.membershipsOf(erased));
        assertEquals(0L, state.cooldownUntil(erased));
        assertEquals(Map.of(), state.leftFactions(erased));
        assertEquals(0, state.accruedToday(erased, GUILD, "quest", DAY),
                "accrual counters are erased with everything else");

        assertTrue(state.hasRow(retained), "another player's row must survive");
        assertEquals(Set.of(GUILD), state.membershipsOf(retained));
        assertEquals(5, state.accruedToday(retained, GUILD, "quest", DAY));
        assertEquals(1, state.rowCount());
    }

    @Test
    void forgetPlayerOnUnknownOrNullUuidIsANoOp() {
        FactionMembershipState state = new FactionMembershipState();
        UUID known = UUID.randomUUID();
        state.recordJoin(known, GUILD, T0);

        assertDoesNotThrow(() -> state.forgetPlayer(UUID.randomUUID()));
        assertDoesNotThrow(() -> state.forgetPlayer(null));

        assertEquals(1, state.rowCount());
        assertTrue(state.isMember(known, GUILD));
    }
}
