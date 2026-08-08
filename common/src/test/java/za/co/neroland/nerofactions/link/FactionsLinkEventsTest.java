package za.co.neroland.nerofactions.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.reputation.TierCrossings;
import za.co.neroland.nerolandcore.event.ThresholdEvents.ThresholdCrossing;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

/**
 * The tier-change event: its payload names a faction and a boundary and nothing player-shaped, and
 * its publication is routed to the one player it concerns via the {@link LinkEvent}'s own
 * {@code playerId} — the payload itself never carries a UUID. Crossings come from
 * {@link TierCrossings#crossings} so these tests can never drift from the real enumeration.
 */
class FactionsLinkEventsTest {

    private static FactionDefinition faction(String path) {
        return new FactionDefinition(
                Identifier.fromNamespaceAndPath("nerofactions", path),
                "Test Faction", "A test faction.",
                Map.of("outsider", 0, "associate", 100, "member", 400,
                        "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(), List.of(), Optional.empty());
    }

    private static String uniquePath(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().toLowerCase().replace("-", "");
    }

    // --- the payload, pure ------------------------------------------------------

    @Test
    void risingPayloadCarriesTheFactionAndTheBoundaryReached() {
        FactionDefinition ladder = faction("ladder");
        ThresholdCrossing crossing = TierCrossings.crossings(ladder, 0, 150).get(0);

        JsonObject payload = FactionsLinkEvents.payloadFor(ladder, crossing);

        assertEquals(5, payload.size(), "exactly: schema_version, faction, tier, tier_name, rising");
        assertEquals(FactionsLinkModule.SCHEMA_VERSION,
                payload.getAsJsonPrimitive("schema_version").getAsInt());
        assertEquals("nerofactions:ladder", payload.getAsJsonPrimitive("faction").getAsString());
        assertEquals(FactionTier.ASSOCIATE.ordinal(), payload.getAsJsonPrimitive("tier").getAsInt());
        assertEquals("associate", payload.getAsJsonPrimitive("tier_name").getAsString());
        assertTrue(payload.getAsJsonPrimitive("rising").getAsBoolean());
    }

    @Test
    void fallingPayloadNamesTheTierJustLost() {
        FactionDefinition ladder = faction("lapsed");
        ThresholdCrossing crossing = TierCrossings.crossings(ladder, 2600, 2499).get(0);

        JsonObject payload = FactionsLinkEvents.payloadFor(ladder, crossing);

        assertEquals(FactionTier.INNER_CIRCLE.ordinal(),
                payload.getAsJsonPrimitive("tier").getAsInt());
        assertEquals("inner_circle", payload.getAsJsonPrimitive("tier_name").getAsString());
        assertFalse(payload.getAsJsonPrimitive("rising").getAsBoolean());
    }

    // --- publication ------------------------------------------------------------

    @Test
    void publicationRoutesByPlayerIdAndKeepsThePayloadAnonymous() {
        FactionDefinition witnessed = faction(uniquePath("witness"));
        String expectedFaction = witnessed.id().toString();
        UUID player = UUID.randomUUID();
        ThresholdCrossing crossing = TierCrossings.crossings(witnessed, 0, 2600).get(3);

        List<LinkEvent> seen = new CopyOnWriteArrayList<>();
        Consumer<LinkEvent> collector = event -> {
            if (event.payload().has("faction") && expectedFaction
                    .equals(event.payload().getAsJsonPrimitive("faction").getAsString())) {
                seen.add(event);
            }
        };
        NeroLinkRegistry.eventBus().subscribe(collector);
        try {
            FactionsLinkEvents.onTierCrossing(player, witnessed, crossing);
        } finally {
            NeroLinkRegistry.eventBus().unsubscribe(collector);
        }

        assertEquals(1, seen.size(), "one crossing, one event");
        LinkEvent event = seen.get(0);
        assertEquals("nerofactions", event.moduleId());
        assertEquals(FactionsLinkModule.TOPIC_STANDING, event.topic());
        assertEquals(player, event.playerId(),
                "the LinkEvent's own routing field is the only place the player appears");
        assertFalse(event.isBroadcast(), "a tier change is never broadcast");
        assertFalse(event.payload().toString().contains(player.toString()),
                "the payload itself must carry no player identifier");
        assertEquals(FactionTier.INNER_CIRCLE.ordinal(),
                event.payload().getAsJsonPrimitive("tier").getAsInt());
        assertTrue(event.timestamp() > 0);
    }

    @Test
    void nullPlayerOrFactionPublishesNothing() {
        FactionDefinition witnessed = faction(uniquePath("silent"));
        ThresholdCrossing crossing = TierCrossings.crossings(witnessed, 0, 150).get(0);

        List<LinkEvent> seen = new CopyOnWriteArrayList<>();
        Consumer<LinkEvent> collector = event -> {
            if ("nerofactions".equals(event.moduleId()) && event.payload().has("faction")
                    && event.payload().getAsJsonPrimitive("faction").getAsString()
                            .equals(witnessed.id().toString())) {
                seen.add(event);
            }
        };
        NeroLinkRegistry.eventBus().subscribe(collector);
        try {
            FactionsLinkEvents.onTierCrossing(null, witnessed, crossing);
            FactionsLinkEvents.onTierCrossing(UUID.randomUUID(), null, crossing);
            FactionsLinkEvents.onTierCrossing(UUID.randomUUID(), witnessed, null);
        } finally {
            NeroLinkRegistry.eventBus().unsubscribe(collector);
        }

        assertTrue(seen.isEmpty());
    }
}
