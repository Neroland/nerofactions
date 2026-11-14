package za.co.neroland.nerofactions.content.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.faction.FactionArmor.Stats;

class FactionArmorBalanceTest {
    private static boolean dominates(Stats a, Stats b) {
        boolean atLeast = a.armorTotal() >= b.armorTotal() && a.toughnessTotal() >= b.toughnessTotal()
                && a.durability() >= b.durability();
        boolean better = a.armorTotal() > b.armorTotal() || a.toughnessTotal() > b.toughnessTotal()
                || a.durability() > b.durability();
        return atLeast && better;
    }

    @Test
    void noSetDominatesEveryOtherSetOnArmourToughnessAndDurability() {
        // One entry per faction set; FactionArmor itself needs a bootstrapped registry to load.
        assertEquals(7, Stats.ALL.size());
        for (Map.Entry<String, Stats> candidate : Stats.ALL.entrySet()) {
            boolean dominatesAll = Stats.ALL.entrySet().stream()
                    .filter(other -> !other.getKey().equals(candidate.getKey()))
                    .allMatch(other -> dominates(candidate.getValue(), other.getValue()));
            assertFalse(dominatesAll, candidate.getKey() + " is strictly the best set");
        }
    }

    @Test
    void theOldCorporateNumbersWouldHaveDominatedAndTheRebalancedOnesDoNot() {
        Stats oldCorporate = new Stats(22, 3, 7, 5, 3, 1.0F);
        assertTrue(Stats.ALL.values().stream().filter(stats -> stats != Stats.CORPORATE_POWER_SUIT)
                .allMatch(other -> dominates(oldCorporate, other)));
        assertEquals(15, Stats.CORPORATE_POWER_SUIT.armorTotal());
        assertEquals(2.0F, Stats.CORPORATE_POWER_SUIT.toughnessTotal());
        assertFalse(dominates(Stats.CORPORATE_POWER_SUIT, Stats.UNION_EXOSUIT));
    }

    @Test
    void freeColonistsFieldGearHasTheLowestArmour() {
        int colonist = Stats.COLONIST_FIELD_GEAR.armorTotal();
        Stats.ALL.forEach((name, stats) -> {
            if (stats != Stats.COLONIST_FIELD_GEAR) {
                assertTrue(colonist < stats.armorTotal(), name + " is not above the Colonist gear");
            }
        });
    }

    @Test
    void guildFlightSuitToughnessIsCancelledByItsSetPenalty() {
        // GearSetBonuses removes two points of armour toughness while the full Guild set is active.
        float effective = Math.max(0, Stats.GUILD_FLIGHT_SUIT.toughnessTotal() - 2);
        assertEquals(0.0F, effective);
        assertTrue(effective < Stats.UNION_EXOSUIT.toughnessTotal());
    }
}
