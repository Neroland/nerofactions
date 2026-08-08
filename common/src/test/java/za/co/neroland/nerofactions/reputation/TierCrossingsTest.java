package za.co.neroland.nerofactions.reputation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.event.ThresholdEvents.ThresholdCrossing;

/**
 * The tier-boundary enumeration and the published payload shape — the contract quest authors (and
 * NeroQuests' {@code custom_event}) write against, tested plain-JVM against directly-constructed
 * definitions (the stock 0/100/400/1000/2500 ladder).
 *
 * <p>Core's {@code ThresholdEvents} has no unsubscribe seam, so listener-path tests register a
 * collector that filters on a per-test unique faction id: residual listeners from other tests can
 * never pollute it, and it can never observe another test's crossings.
 */
class TierCrossingsTest {

    private static FactionDefinition faction(String path) {
        return new FactionDefinition(
                Identifier.fromNamespaceAndPath("nerofactions", path),
                "Test Faction", "A test faction.",
                Map.of("outsider", 0, "associate", 100, "member", 400,
                        "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(), List.of(), Optional.empty());
    }

    @Test
    void singleStepRisingFiresExactlyOneBoundary() {
        FactionDefinition ladder = faction("ladder");
        List<ThresholdCrossing> crossings = TierCrossings.crossings(ladder, 0, 150);

        assertEquals(1, crossings.size());
        ThresholdCrossing crossing = crossings.get(0);
        assertEquals(TierCrossings.CHANNEL, crossing.channel());
        assertEquals("nerofactions:ladder", crossing.scope());
        assertEquals(FactionTier.ASSOCIATE.ordinal(), crossing.value());
        assertEquals(100, crossing.threshold());
        assertTrue(crossing.rising());
    }

    @Test
    void valueExactlyAtThresholdCrossesIntoTheHigherTier() {
        // FactionTiers: at-threshold belongs to the higher tier, so 99 -> 100 is a crossing.
        List<ThresholdCrossing> crossings = TierCrossings.crossings(faction("edge"), 99, 100);
        assertEquals(1, crossings.size());
        assertEquals(FactionTier.ASSOCIATE.ordinal(), crossings.get(0).value());
        assertTrue(crossings.get(0).rising());
    }

    @Test
    void multiTierJumpFiresOneCrossingPerBoundaryInAscendingOrder() {
        // 0 -> 1200 crosses associate, member, trusted: three crossings, in the order crossed.
        List<ThresholdCrossing> crossings = TierCrossings.crossings(faction("jump"), 0, 1200);

        assertEquals(3, crossings.size());
        assertEquals(List.of(1L, 2L, 3L),
                crossings.stream().map(ThresholdCrossing::value).toList(),
                "value = ordinal of the tier on the upper side of each boundary, ascending");
        assertEquals(List.of(100L, 400L, 1000L),
                crossings.stream().map(ThresholdCrossing::threshold).toList(),
                "threshold = that tier's reputation threshold on the faction ladder");
        assertTrue(crossings.stream().allMatch(ThresholdCrossing::rising));
    }

    @Test
    void fallingEnumeratesTheSameBoundariesDownward() {
        // Decay-shaped: 1200 (trusted) erodes to 50 (outsider) — loses trusted, member, associate.
        List<ThresholdCrossing> crossings = TierCrossings.crossings(faction("decayed"), 1200, 50);

        assertEquals(3, crossings.size());
        assertEquals(List.of(3L, 2L, 1L),
                crossings.stream().map(ThresholdCrossing::value).toList(),
                "falling enumerates upper-side ordinals in the order lost: descending");
        assertEquals(List.of(1000L, 400L, 100L),
                crossings.stream().map(ThresholdCrossing::threshold).toList());
        assertTrue(crossings.stream().noneMatch(ThresholdCrossing::rising));
    }

    @Test
    void fallingOutOfInnerCircleIsASingleValueFourCrossing() {
        List<ThresholdCrossing> crossings = TierCrossings.crossings(faction("lapsed"), 2600, 2499);
        assertEquals(1, crossings.size());
        assertEquals(FactionTier.INNER_CIRCLE.ordinal(), crossings.get(0).value());
        assertEquals(2500, crossings.get(0).threshold());
        assertFalse(crossings.get(0).rising());
    }

    @Test
    void noCrossingWhenTheTierDidNotChange() {
        FactionDefinition ladder = faction("flat");
        assertEquals(List.of(), TierCrossings.crossings(ladder, 100, 399),
                "moving inside associate is not a crossing");
        assertEquals(List.of(), TierCrossings.crossings(ladder, 5, 80),
                "moving inside outsider is not a crossing");
        assertEquals(List.of(), TierCrossings.crossings(ladder, -500, 0),
                "negative standing recovering to neutral stays outsider — no boundary exists below associate");
        assertEquals(List.of(), TierCrossings.crossings(ladder, 250, 250));
    }

    @Test
    void publishedCrossingsCarryTheFactionAndNeverAPlayer() {
        // Unique faction id per run: the collector filters on it, so residual ThresholdEvents
        // listeners (the bus has no unsubscribe) can never pollute this test or be polluted by it.
        String path = "witness_" + UUID.randomUUID().toString().toLowerCase().replace("-", "");
        FactionDefinition witnessed = faction(path);
        String expectedScope = witnessed.id().toString();
        UUID player = UUID.randomUUID(); // exists only to assert its absence from the payload

        List<ThresholdCrossing> seen = new CopyOnWriteArrayList<>();
        ThresholdEvents.onCrossing(crossing -> {
            if (expectedScope.equals(crossing.scope())) {
                seen.add(crossing);
            }
        });

        // publish takes no player at all — the structural guarantee that no identifier can leak.
        boolean reachedInnerCircle = TierCrossings.publish(witnessed, 0, 2600);

        assertTrue(reachedInnerCircle, "0 -> 2600 arrives at the inner circle");
        assertEquals(4, seen.size(), "one crossing per boundary reached the shared bus");
        assertEquals(List.of(1L, 2L, 3L, 4L),
                seen.stream().map(ThresholdCrossing::value).toList());
        for (ThresholdCrossing crossing : seen) {
            assertEquals(TierCrossings.CHANNEL, crossing.channel());
            assertEquals(expectedScope, crossing.scope(),
                    "scope is exactly the faction id — a place/system, never a person");
            assertFalse(crossing.scope().contains(player.toString()),
                    "no player identifier may ever appear in a crossing scope");
        }
    }

    @Test
    void linkConsumerReceivesEveryCrossingWithThePlayerResolved() {
        FactionDefinition ladder = faction("linked");
        UUID player = UUID.randomUUID();
        List<ThresholdCrossing> crossings = TierCrossings.crossings(ladder, 0, 1200);
        List<String> seen = new CopyOnWriteArrayList<>();
        try {
            TierCrossings.setPlayerCrossingConsumer((forPlayer, forFaction, crossing) ->
                    seen.add(forPlayer + "/" + forFaction.id() + "/" + crossing.value()
                            + "/" + crossing.rising()));

            TierCrossings.notifyPlayerCrossingConsumer(player, ladder, crossings);
        } finally {
            TierCrossings.setPlayerCrossingConsumer(null); // single slot — never leak across tests
        }

        assertEquals(List.of(
                player + "/nerofactions:linked/1/true",
                player + "/nerofactions:linked/2/true",
                player + "/nerofactions:linked/3/true"), seen,
                "every boundary reaches the link seam, in crossing order, player resolved");
    }

    @Test
    void linkConsumerFailureIsSwallowedAndNeverReachesTheReputationFlow() {
        FactionDefinition ladder = faction("fragile");
        List<ThresholdCrossing> crossings = TierCrossings.crossings(ladder, 0, 150);
        try {
            TierCrossings.setPlayerCrossingConsumer((forPlayer, forFaction, crossing) -> {
                throw new IllegalStateException("a broken link module");
            });

            assertDoesNotThrow(() -> TierCrossings.notifyPlayerCrossingConsumer(
                    UUID.randomUUID(), ladder, crossings));
        } finally {
            TierCrossings.setPlayerCrossingConsumer(null);
        }
    }

    @Test
    void noConsumerAndNoPlayerAreBothQuietNoOps() {
        FactionDefinition ladder = faction("quiet");
        List<ThresholdCrossing> crossings = TierCrossings.crossings(ladder, 0, 150);
        // No consumer registered at all:
        assertDoesNotThrow(() -> TierCrossings.notifyPlayerCrossingConsumer(
                UUID.randomUUID(), ladder, crossings));
        // Consumer registered, but no player to route to (must not be invoked):
        List<String> seen = new CopyOnWriteArrayList<>();
        try {
            TierCrossings.setPlayerCrossingConsumer(
                    (forPlayer, forFaction, crossing) -> seen.add("invoked"));
            TierCrossings.notifyPlayerCrossingConsumer(null, ladder, crossings);
            TierCrossings.notifyPlayerCrossingConsumer(UUID.randomUUID(), ladder, List.of());
        } finally {
            TierCrossings.setPlayerCrossingConsumer(null);
        }
        assertEquals(List.of(), seen);
    }

    @Test
    void publishReportsInnerCircleArrivalOnlyForRisingArrivals() {
        FactionDefinition ladder = faction("arrivals_" + UUID.randomUUID().toString()
                .toLowerCase().replace("-", ""));
        assertFalse(TierCrossings.publish(ladder, 0, 500), "member is not the inner circle");
        assertFalse(TierCrossings.publish(ladder, 2600, 0), "falling out is not an arrival");
        assertFalse(TierCrossings.publish(ladder, 300, 350), "no crossing, no arrival");
        assertTrue(TierCrossings.publish(ladder, 2499, 2500), "at-threshold arrival counts");
    }
}
