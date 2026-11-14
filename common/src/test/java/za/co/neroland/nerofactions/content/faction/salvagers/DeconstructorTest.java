package za.co.neroland.nerofactions.content.faction.salvagers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeconstructorTest {
    @Test
    void recoveryRoundsDownAndNeverExceedsThreeQuarters() {
        assertEquals(4, DeconstructorBlock.Yield.units(8, 50));
        assertEquals(6, DeconstructorBlock.Yield.units(8, 100));
        assertEquals(0, DeconstructorBlock.Yield.units(1, 75));
        assertEquals(0, DeconstructorBlock.Yield.units(4, -10));
    }
}
