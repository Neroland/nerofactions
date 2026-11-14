package za.co.neroland.nerofactions.content.faction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.faction.OutpostResident.Leash;

class OutpostResidentTest {
    @Test
    void aBoundResidentOutsideItsOutpostOnFootIsReturnedWithoutDismounting() {
        assertTrue(Leash.returnHome(true, false));
        assertFalse(Leash.dismount(true, false));
        assertFalse(Leash.returnHome(true, true));
    }

    @Test
    void aBoundResidentCarriedOutInAVehicleIsDismountedThenReturned() {
        assertTrue(Leash.dismount(true, true));
        assertTrue(Leash.returnHome(true, false));
        // Even inside its outpost a bound resident refuses to ride.
        assertTrue(Leash.dismount(true, true));
    }

    @Test
    void anUnboundResidentIsLeftAlone() {
        assertFalse(Leash.dismount(false, true));
        assertFalse(Leash.returnHome(false, false));
    }
}
