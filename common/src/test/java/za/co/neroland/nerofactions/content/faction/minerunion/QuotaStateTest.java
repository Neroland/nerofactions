package za.co.neroland.nerofactions.content.faction.minerunion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionTier;

class QuotaStateTest {

    @Test
    void capsDepositsAcrossBoardsAndDoesNotPartiallyConsumeAMetalBlock() {
        var state = new QuotaState();
        UUID player = UUID.randomUUID();
        assertEquals(64, QuotaState.limit(64, FactionTier.OUTSIDER));
        assertEquals(320, QuotaState.limit(64, FactionTier.INNER_CIRCLE));
        assertEquals(7, state.accept(player, 5, 64, 9, 8));
        assertEquals(0, state.accept(player, 5, 64, 9, 1));
        assertEquals(1, state.accept(player, 5, 64, 1, 20));
        assertEquals(64, state.contributed(player, 5));
        assertEquals(0, state.accept(player, 4, 64, 1, 1));
        assertEquals(1, state.accept(player, 6, 64, 9, 1));
        assertEquals(9, state.contributed(player, 6));
    }

    @Test
    void roundTripsAndErasesOnlyTheRequestedPlayer() {
        var state = new QuotaState();
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        state.accept(player, 5, 64, 9, 2);
        state.accept(bystander, 5, 64, 1, 5);
        var encoded = QuotaState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        var decoded = QuotaState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(18, decoded.export(player).get("tonnes").getAsInt());
        assertFalse(decoded.export(player).has("player"));
        decoded.forget(player);
        assertTrue(decoded.export(player).isEmpty());
        assertEquals(5, decoded.contributed(bystander, 5));
    }
}
