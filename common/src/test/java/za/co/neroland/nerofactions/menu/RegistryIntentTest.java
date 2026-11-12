package za.co.neroland.nerofactions.menu;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RegistryIntentTest {
    @Test
    void decodesJoinAndLeaveAndAllowsTheRateBoundary() {
        assertEquals(new RegistryIntent(0, false), RegistryIntent.decode(0, 7, 0, Long.MIN_VALUE));
        assertEquals(new RegistryIntent(6, true), RegistryIntent.decode(13, 7, 10, 5));
    }

    @Test
    void rejectsForgedIndicesSpamAndClockRollback() {
        assertNull(RegistryIntent.decode(-1, 7, 10, Long.MIN_VALUE));
        assertNull(RegistryIntent.decode(14, 7, 10, Long.MIN_VALUE));
        assertNull(RegistryIntent.decode(Integer.MAX_VALUE, 7, 10, Long.MIN_VALUE));
        assertNull(RegistryIntent.decode(0, 0, 10, Long.MIN_VALUE));
        assertNull(RegistryIntent.decode(0, 7, 9, 5));
        assertNull(RegistryIntent.decode(0, 7, 4, 5));
    }
}
