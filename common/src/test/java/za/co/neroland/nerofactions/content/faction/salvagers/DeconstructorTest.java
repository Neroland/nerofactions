package za.co.neroland.nerofactions.content.faction.salvagers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeconstructorTest {
    @Test
    void recoveryRoundsDownAndNeverExceedsThreeQuarters() {
        assertEquals(4, DeconstructorBlock.Yield.units(8, 50));
        assertEquals(6, DeconstructorBlock.Yield.units(8, 100));
        assertEquals(0, DeconstructorBlock.Yield.units(1, 75));
        assertEquals(0, DeconstructorBlock.Yield.units(4, -10));
    }

    @Test
    void aClickIsAllowedFirstAndOnceTheCooldownHasPassed() {
        assertTrue(DeconstructorBlock.Cooldown.allowed(100, null, 10));
        assertTrue(DeconstructorBlock.Cooldown.allowed(110, 100L, 10));
        assertTrue(DeconstructorBlock.Cooldown.allowed(5, 100L, 10));
    }

    @Test
    void aClickWithinTheCooldownIsRefused() {
        assertFalse(DeconstructorBlock.Cooldown.allowed(100, 100L, 10));
        assertFalse(DeconstructorBlock.Cooldown.allowed(109, 100L, 10));
    }
}
