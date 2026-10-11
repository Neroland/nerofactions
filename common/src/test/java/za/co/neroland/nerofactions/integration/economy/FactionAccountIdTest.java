package za.co.neroland.nerofactions.integration.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/** The faction-to-treasury-account mapping (NeroEconomy-free, so it runs without NeroEconomy). */
class FactionAccountIdTest {

    @Test
    void theKindIsTheModIdAndTheValueIsTheFactionId() {
        assertEquals("nerofactions", FactionAccountId.KIND);
        assertEquals(Optional.of("nerofactions:space_guild"),
                FactionAccountId.valueFor(Identifier.parse("nerofactions:space_guild")));
    }

    @Test
    void anIdThatCannotBeAnAccountValueIsRefused() {
        assertTrue(FactionAccountId.valueFor(null).isEmpty());
        assertTrue(FactionAccountId.valueFor(
                Identifier.fromNamespaceAndPath("nerofactions", "x".repeat(70))).isEmpty(),
                "account values are at most 64 characters");
    }
}
