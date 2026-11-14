package za.co.neroland.nerofactions.content.faction.salvagers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SalvagerLootTest {
    private static final String EMPTY = "";

    /** Mirrors the mixin: place the extras into the container's empty slots and return how many landed. */
    private static int fill(List<String> container, List<String> extras) {
        var placed = SalvagerLoot.placements(container.size(), slot -> container.get(slot).isEmpty(),
                String::isEmpty, extras);
        placed.forEach(container::set);
        return placed.size();
    }

    private static List<String> slots(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    void anActiveSalvagerOpeningFirstGetsOneExtraRollPlacedInEmptySlotsOnly() {
        assertEquals(1, SalvagerLoot.extraRolls(true, true));
        assertEquals(43L, SalvagerLoot.extraSeed(42L));
        var container = slots("bread", EMPTY, "iron", EMPTY, EMPTY);
        assertEquals(2, fill(container, List.of("gold", EMPTY, "string")));
        assertEquals(List.of("bread", "gold", "iron", "string", EMPTY), container);
    }

    @Test
    void noExtraRollWhenInactiveOrTheContainerHadNoLootTable() {
        assertEquals(0, SalvagerLoot.extraRolls(false, true));
        assertEquals(0, SalvagerLoot.extraRolls(true, false));
        assertEquals(0, SalvagerLoot.extraRolls(false, false));
    }

    @Test
    void aFullContainerIsNeverOverwrittenAndOverflowIsDiscarded() {
        var full = slots("bread", "iron");
        assertEquals(0, fill(full, List.of("gold", "diamond")));
        assertEquals(List.of("bread", "iron"), full);

        var oneFree = slots("bread", EMPTY);
        assertEquals(1, fill(oneFree, List.of("gold", "diamond")));
        assertEquals(List.of("bread", "gold"), oneFree);
    }
}
