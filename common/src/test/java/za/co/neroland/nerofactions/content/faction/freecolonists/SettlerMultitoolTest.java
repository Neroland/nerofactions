package za.co.neroland.nerofactions.content.faction.freecolonists;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.faction.freecolonists.SettlerMultitool.Head;

class SettlerMultitoolTest {
    @Test
    void selectsOneHeadPerTargetWithWoodTakingPriorityOverCrop() {
        assertEquals(Head.AXE, Head.forTarget(true, false));
        assertEquals(Head.HOE, Head.forTarget(false, true));
        assertEquals(Head.AXE, Head.forTarget(true, true));
        assertEquals(Head.SHOVEL, Head.forTarget(false, false));
    }

    @Test
    void membersGetTheHeadChosenByTheTargetAndSneakForcesTheHoe() {
        assertEquals(Head.AXE, Head.forUse(true, false, true, false));
        assertEquals(Head.HOE, Head.forUse(true, false, false, true));
        assertEquals(Head.SHOVEL, Head.forUse(true, false, false, false));
        assertEquals(Head.HOE, Head.forUse(true, true, true, false));
    }

    @Test
    void nonMembersGetAPlainShovelWithNoAxeOrHoeTransform() {
        assertEquals(Head.SHOVEL, Head.forUse(false, false, true, false));
        assertEquals(Head.SHOVEL, Head.forUse(false, false, false, true));
        assertEquals(Head.SHOVEL, Head.forUse(false, true, true, true));
    }
}
