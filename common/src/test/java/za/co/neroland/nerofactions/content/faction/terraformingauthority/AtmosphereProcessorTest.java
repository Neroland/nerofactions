package za.co.neroland.nerofactions.content.faction.terraformingauthority;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AtmosphereProcessorTest {
    @Test
    void radiusGrowsOnlyAfterFourSuccessfulPulsesAndRespectsTheLimit() {
        assertEquals(1, AtmosphereProcessorBlock.radius(0, 6));
        assertEquals(1, AtmosphereProcessorBlock.radius(3, 6));
        assertEquals(2, AtmosphereProcessorBlock.radius(4, 6));
        assertEquals(6, AtmosphereProcessorBlock.radius(100, 6));
        assertEquals(1, AtmosphereProcessorBlock.radius(-4, 0));
        assertEquals(8, AtmosphereProcessorBlock.radius(100, 99));
    }
}
