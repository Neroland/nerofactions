package za.co.neroland.nerofactions.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * Tier-boundary arithmetic on the shipped default ladder (0/100/400/1000/2500): a value exactly at
 * a threshold belongs to the <em>higher</em> tier, negative standing is always Outsider.
 */
class FactionTiersTest {

    private static final FactionDefinition LADDER = new FactionDefinition(
            Identifier.fromNamespaceAndPath("nerofactions", "test_ladder"),
            "Test Ladder", "test",
            Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000,
                    "inner_circle", 2500),
            Map.of(), List.of(), List.of(), Optional.empty());

    @Test
    void exactlyAtAThresholdBelongsToTheHigherTier() {
        assertEquals(FactionTier.ASSOCIATE, FactionTiers.tierOf(LADDER, 100));
        assertEquals(FactionTier.MEMBER, FactionTiers.tierOf(LADDER, 400));
        assertEquals(FactionTier.TRUSTED, FactionTiers.tierOf(LADDER, 1000));
        assertEquals(FactionTier.INNER_CIRCLE, FactionTiers.tierOf(LADDER, 2500));
    }

    @Test
    void justBelowAThresholdBelongsToTheLowerTier() {
        assertEquals(FactionTier.OUTSIDER, FactionTiers.tierOf(LADDER, 99));
        assertEquals(FactionTier.ASSOCIATE, FactionTiers.tierOf(LADDER, 399));
        assertEquals(FactionTier.MEMBER, FactionTiers.tierOf(LADDER, 999));
        assertEquals(FactionTier.TRUSTED, FactionTiers.tierOf(LADDER, 2499));
    }

    @Test
    void zeroAndNegativeStandingAreOutsider() {
        assertEquals(FactionTier.OUTSIDER, FactionTiers.tierOf(LADDER, 0));
        assertEquals(FactionTier.OUTSIDER, FactionTiers.tierOf(LADDER, -1));
        assertEquals(FactionTier.OUTSIDER, FactionTiers.tierOf(LADDER, Integer.MIN_VALUE));
    }

    @Test
    void standingAboveTheTopThresholdStaysInnerCircle() {
        assertEquals(FactionTier.INNER_CIRCLE, FactionTiers.tierOf(LADDER, Integer.MAX_VALUE));
    }
}
