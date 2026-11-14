package za.co.neroland.nerofactions.content.faction.spaceguild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GuildLaunchFuelTest {
    @Test
    void preservesValidQuotesUntilThereIsALaunchHook() {
        assertEquals(0, GuildLaunchFuel.quotedUnits(0));
        assertEquals(650, GuildLaunchFuel.quotedUnits(650));
        assertEquals(Long.MAX_VALUE, GuildLaunchFuel.quotedUnits(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> GuildLaunchFuel.quotedUnits(-1));
    }
}
