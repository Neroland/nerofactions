package za.co.neroland.nerofactions.content.faction.spaceguild;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NavigationBeaconTest {
    @Test
    void protectionStopsOutsideTheTwentyFourBlockSphere() {
        assertTrue(NavigationBeaconBlock.withinRange(24 * 24));
        assertTrue(NavigationBeaconBlock.withinRange(23 * 23 + 4 * 4 + 4 * 4));
        assertFalse(NavigationBeaconBlock.withinRange(24 * 24 + 1));
    }
}
