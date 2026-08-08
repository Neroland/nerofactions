package za.co.neroland.nerofactions.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.data.FactionMembershipState;

/**
 * The combat trigger's decision core: <b>member factions only</b>, and {@code combatAwardBase = 0}
 * is a true off switch. The weighting/cap/bleed of the award itself is {@code ReputationSources}'
 * already-tested territory — this trigger only chooses the targets and hands over the base.
 */
class InternalTriggersTest {

    private static final UUID PLAYER = UUID.fromString("2b3f8c1d-4a5e-4f60-9b71-8d2c3e4f5a6b");
    private static final Identifier GUILD = Identifier.parse("nerofactions:space_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:miner_union");

    @Test
    void configuredZeroDisablesTheTriggerEntirely() {
        Set<Identifier> memberships = new LinkedHashSet<>(List.of(GUILD, UNION));
        assertEquals(List.of(), InternalTriggers.combatAwardTargets(0, memberships));
        assertEquals(List.of(), InternalTriggers.combatAwardTargets(-1, memberships));
    }

    @Test
    void factionlessPlayersEarnNothing() {
        assertEquals(List.of(), InternalTriggers.combatAwardTargets(2, Set.of()));
        assertEquals(List.of(), InternalTriggers.combatAwardTargets(2, null));
    }

    @Test
    void memberFactionsOnlyStraightFromTheMembershipStore() {
        // Real store, no server: standing with a faction (or a faction merely existing) never
        // makes the list — only recorded memberships do, and leaving drops the faction again.
        FactionMembershipState state = new FactionMembershipState();
        state.recordJoin(PLAYER, GUILD, 1_000L);
        state.recordJoin(PLAYER, UNION, 2_000L);

        assertEquals(List.of(GUILD, UNION),
                InternalTriggers.combatAwardTargets(2, state.membershipsOf(PLAYER)),
                "every member faction is awarded, in membership order");

        state.recordLeave(PLAYER, GUILD, 3_000L, 0L);
        assertEquals(List.of(UNION),
                InternalTriggers.combatAwardTargets(2, state.membershipsOf(PLAYER)));

        // A different player kills the same monster: their (empty) membership row decides.
        assertEquals(List.of(), InternalTriggers.combatAwardTargets(2,
                state.membershipsOf(UUID.fromString("00000000-0000-4000-8000-000000000001"))));
    }
}
