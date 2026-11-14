package za.co.neroland.nerofactions.content.faction.salvagers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SalvageScannerTest {
    @Test
    void bearingUsesMinecraftAxesAndRoundsTheDistance() {
        assertEquals("northwest", SalvageScanner.Bearing.direction(-20, -20));
        assertEquals("south", SalvageScanner.Bearing.direction(0, 36));
        assertEquals(32, SalvageScanner.Bearing.roughDistance(20, 20));
    }

    @Test
    void aSiteUnderfootDoesNotReceiveAnArbitraryDirection() {
        assertEquals("here", SalvageScanner.Bearing.direction(0, 0));
        assertEquals(0, SalvageScanner.Bearing.roughDistance(0, 0));
    }
}
