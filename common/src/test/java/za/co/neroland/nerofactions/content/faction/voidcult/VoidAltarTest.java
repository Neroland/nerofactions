package za.co.neroland.nerofactions.content.faction.voidcult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoidAltarTest {
    @Test
    void bothSacrificesAreRequired() {
        assertTrue(VoidAltarBlock.Rite.hasCost(3, true, 4));
        assertFalse(VoidAltarBlock.Rite.hasCost(2, true, 4));
        assertFalse(VoidAltarBlock.Rite.hasCost(3, true, 3));
        assertFalse(VoidAltarBlock.Rite.hasCost(3, false, 64));
    }

    @Test
    void backlashUsesAnExclusiveBoundary() {
        assertTrue(VoidAltarBlock.Rite.backlash(14, 15));
        assertFalse(VoidAltarBlock.Rite.backlash(15, 15));
        assertFalse(VoidAltarBlock.Rite.backlash(0, 0));
        assertTrue(VoidAltarBlock.Rite.backlash(99, 100));
    }
}
