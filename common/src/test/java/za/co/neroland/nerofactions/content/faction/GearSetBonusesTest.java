package za.co.neroland.nerofactions.content.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses.UnionEnvironment;

class GearSetBonusesTest {
    private static final FactionDefinition UNION = new FactionDefinition(
            OutpostFaction.MINER_UNION.factionId(), "Miner Union", "test",
            Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
            Map.of(), List.of(), List.of(), Optional.empty());

    @Test
    void theExosuitRequiresFourPiecesCurrentMembershipAndTrustedStanding() {
        assertTrue(GearSetBonuses.eligible(4, true, UNION, 1000));
        assertFalse(GearSetBonuses.eligible(3, true, UNION, 2500));
        assertFalse(GearSetBonuses.eligible(4, false, UNION, 2500));
        assertFalse(GearSetBonuses.eligible(4, true, UNION, 999));
        assertFalse(GearSetBonuses.eligible(4, true, null, 2500));
    }

    @Test
    void theBonusAndDaylightPenaltyAreMutuallyExclusive() {
        assertEquals(UnionEnvironment.UNDERGROUND, GearSetBonuses.unionEnvironment(39, true, true, true));
        assertEquals(UnionEnvironment.UNDERGROUND, GearSetBonuses.unionEnvironment(120, false, true, true));
        assertEquals(UnionEnvironment.DAYLIGHT, GearSetBonuses.unionEnvironment(40, true, true, true));
        assertEquals(UnionEnvironment.NEUTRAL, GearSetBonuses.unionEnvironment(60, true, false, true));
        assertEquals(UnionEnvironment.NEUTRAL, GearSetBonuses.unionEnvironment(60, true, true, false));
    }

    @Test
    void salvagerToolsMaySaveWearWhileArmorWearsFaster() {
        assertEquals(0, GearSetBonuses.SalvageWear.adjust(1, false, 0));
        assertEquals(1, GearSetBonuses.SalvageWear.adjust(1, false, 1));
        assertEquals(2, GearSetBonuses.SalvageWear.adjust(1, true, 0));
        assertEquals(1, GearSetBonuses.SalvageWear.adjust(1, true, 1));
        assertEquals(5, GearSetBonuses.SalvageWear.adjust(4, true, 3));
    }
}
