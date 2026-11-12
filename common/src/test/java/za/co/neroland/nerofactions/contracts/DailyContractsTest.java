package za.co.neroland.nerofactions.contracts;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyContractsTest {
    @Test
    void dailyDrawIsStableIndependentOfPoolOrderAndHasNoDuplicates() {
        UUID player = new UUID(1, 2);
        var pool = ContractFixtures.pool();
        var reversed = new java.util.ArrayList<>(pool);
        java.util.Collections.reverse(reversed);
        var first = DailyContracts.roll(pool, 123, 5, player, ContractFixtures.FACTION, 0);
        assertEquals(3, first.size());
        assertEquals(3, first.stream().map(ContractDefinition::id).distinct().count());
        assertEquals(first, DailyContracts.roll(reversed, 123, 5, player, ContractFixtures.FACTION, 0));
        assertTrue(java.util.stream.IntStream.range(3, 40).anyMatch(i -> !first.equals(
                DailyContracts.roll(pool, 123, 5, new UUID(1, i), ContractFixtures.FACTION, 0))));
        assertTrue(java.util.stream.IntStream.range(6, 40).anyMatch(day -> !first.equals(
                DailyContracts.roll(pool, 123, day, player, ContractFixtures.FACTION, 0))));
    }

    @Test
    void emptyOrIneligiblePoolsProduceNoAssignment() {
        var player = new UUID(1, 2);
        assertTrue(DailyContracts.roll(List.of(), 0, 0, player, ContractFixtures.FACTION, 0).isEmpty());
        assertTrue(DailyContracts.roll(ContractFixtures.pool(), 0, 0, player,
                net.minecraft.resources.Identifier.parse("nerofactions:absent"), 0).isEmpty());
    }
}
