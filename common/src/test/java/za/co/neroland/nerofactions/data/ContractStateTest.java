package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.contracts.ContractFixtures;

class ContractStateTest {
    private static final UUID PLAYER = new UUID(1, 2);

    @Test
    void rewardReservationIsOnceOnlyAndSurvivesPersistence() {
        var state = new ContractState();
        var entries = state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        assertNull(state.reserveReward(PLAYER, ContractFixtures.FACTION, 0));
        assertEquals(16, state.advance(PLAYER, ContractFixtures.FACTION, 0, Integer.MAX_VALUE));
        assertEquals(entries.get(0).contract(), state.reserveReward(PLAYER, ContractFixtures.FACTION, 0));
        assertNull(state.reserveReward(PLAYER, ContractFixtures.FACTION, 0));
        assertEquals(0, state.advance(PLAYER, ContractFixtures.FACTION, 0, 10));
        var json = ContractState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var restored = ContractState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(restored.reserveReward(PLAYER, ContractFixtures.FACTION, 0));
        assertEquals(1, restored.completed(PLAYER));
    }

    @Test
    void rollbackDoesNotRefillTokenOrProgressAndNewDayPreservesCount() {
        var state = new ContractState();
        var entries = state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        var replacement = ContractFixtures.pool().stream().filter(c -> entries.stream()
                .noneMatch(e -> e.contract().id().equals(c.id()))).findFirst().orElseThrow();
        assertTrue(state.reroll(PLAYER, ContractFixtures.FACTION, 0, replacement));
        assertFalse(state.reroll(PLAYER, ContractFixtures.FACTION, 0, entries.get(0).contract()));
        state.advance(PLAYER, ContractFixtures.FACTION, 1, 16);
        state.reserveReward(PLAYER, ContractFixtures.FACTION, 1);
        state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 4, ContractFixtures.pool());
        assertEquals(5, state.day(PLAYER));
        assertFalse(state.rerollAvailable(PLAYER));
        assertTrue(state.entry(PLAYER, ContractFixtures.FACTION, 1).claimed());
        state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 6, ContractFixtures.pool());
        assertTrue(state.rerollAvailable(PLAYER));
        assertEquals(0, state.entry(PLAYER, ContractFixtures.FACTION, 1).progress());
        assertEquals(1, state.completed(PLAYER));
    }

    @Test
    void exportAndErasureAreScopedAndRejectInvalidProgress() {
        var state = new ContractState();
        UUID other = new UUID(3, 4);
        state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        state.ensure(other, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        assertEquals(0, state.advance(PLAYER, ContractFixtures.FACTION, 0, -1));
        assertEquals(0, state.advance(PLAYER, ContractFixtures.FACTION, 99, 1));
        String export = state.export(PLAYER).toString();
        assertFalse(export.contains(PLAYER.toString()));
        assertFalse(export.contains(other.toString()));
        assertEquals(3, state.export(PLAYER).getAsJsonArray("entries").size());
        state.forget(PLAYER);
        assertTrue(state.entries(PLAYER).isEmpty());
        assertFalse(state.entries(other).isEmpty());
        assertEquals(0, state.completed(PLAYER));
    }

    @Test
    void offlineAssignmentsExpireAtTheDayBoundaryWithoutResettingOnRollback() {
        var state = new ContractState();
        state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        state.advance(PLAYER, ContractFixtures.FACTION, 0, 16);
        state.reserveReward(PLAYER, ContractFixtures.FACTION, 0);
        state.expireAll(6);
        assertTrue(state.entries(PLAYER).isEmpty());
        assertEquals(1, state.completed(PLAYER));
        state.ensure(PLAYER, ContractFixtures.FACTION, 0, 123, 4, ContractFixtures.pool());
        assertEquals(6, state.day(PLAYER));
        state.advance(PLAYER, ContractFixtures.FACTION, 0, 1);
        state.expireAll(5);
        assertEquals(1, state.entry(PLAYER, ContractFixtures.FACTION, 0).progress());
    }
}
