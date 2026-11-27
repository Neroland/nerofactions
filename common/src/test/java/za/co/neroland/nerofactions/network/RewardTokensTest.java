package za.co.neroland.nerofactions.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewardTokensTest {
    @Test
    void roundTripsItemsAndCosmetics() {
        String raw = String.join(RewardTokens.SEPARATOR,
                RewardTokens.item("minecraft:coal", 16), RewardTokens.cosmetic("banner_pattern"));
        List<RewardTokens.Token> tokens = RewardTokens.parse(raw);
        assertEquals(2, tokens.size());
        assertTrue(tokens.get(0).isItem());
        assertEquals("minecraft:coal", tokens.get(0).itemId());
        assertEquals(16, tokens.get(0).count());
        assertFalse(tokens.get(1).isItem());
        assertEquals("banner_pattern", tokens.get(1).cosmetic());
    }

    @Test
    void olderServerProseIsNotTokenised() {
        assertFalse(RewardTokens.isTokenised("coal ×16, banner pattern"));
        assertTrue(RewardTokens.parse("coal ×16, banner pattern").isEmpty());
    }

    @Test
    void entryTruncatedByTheSnapshotBoundIsDropped() {
        assertEquals(1, RewardTokens.parse("i:minecraft:coal:16;i:minecraft:iro").size());
        assertEquals(1, RewardTokens.parse("i:minecraft:coal:16;i:minecraft:iron_ingot:").size());
    }
}
