package za.co.neroland.nerofactions.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;

/**
 * The snapshot sections' shapes and — above all — their scoping: a request sees the requesting
 * UUID's rows and nothing else, even when a bystander's record is richer. Plain JVM, null server:
 * the {@link FactionsLinkSnapshots.Stores} seam is filled with direct stores, exactly the shape
 * the production path resolves from the bound server.
 */
class FactionsLinkSnapshotsTest {

    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID BYSTANDER = UUID.randomUUID();

    private static FactionDefinition faction(String path, String displayName) {
        return new FactionDefinition(
                Identifier.fromNamespaceAndPath("nerofactions", path),
                displayName, "A test faction.",
                Map.of("outsider", 0, "associate", 100, "member", 400,
                        "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(), List.of(), Optional.empty());
    }

    /** Direct stores behind the seam — the same reads production makes, no server required. */
    private static final class Fixture implements FactionsLinkSnapshots.View {

        final Map<Identifier, FactionDefinition> factions = new LinkedHashMap<>();
        final FactionReputationState reputation = new FactionReputationState();
        final FactionMembershipState membership = new FactionMembershipState();

        Fixture(FactionDefinition... definitions) {
            for (FactionDefinition definition : definitions) {
                factions.put(definition.id(), definition);
            }
        }

        FactionsLinkSnapshots snapshots() {
            return new FactionsLinkSnapshots(() -> this);
        }

        @Override
        public Map<Identifier, FactionDefinition> factions() {
            return factions;
        }

        @Override
        public int standing(UUID player, Identifier faction) {
            return reputation.getReputation(player, faction);
        }

        @Override
        public Set<Identifier> memberships(UUID player) {
            return membership.membershipsOf(player);
        }

        @Override
        public long joinedAt(UUID player, Identifier faction) {
            return membership.joinedAt(player, faction);
        }

        @Override
        public long cooldownUntil(UUID player) {
            return membership.cooldownUntil(player);
        }

        @Override
        public Map<Identifier, Long> leftFactions(UUID player) {
            return membership.leftFactions(player);
        }

        @Override
        public boolean online(UUID player) {
            return false;
        }
    }

    // --- standing: scoping ------------------------------------------------------

    @Test
    void standingReturnsExactlyTheRequestersRows() {
        FactionDefinition guild = faction("space_guild", "Space Guild");
        FactionDefinition salvagers = faction("salvagers", "Salvagers");
        Fixture fixture = new Fixture(guild, salvagers);
        fixture.reputation.setReputation(REQUESTER, guild.id(), 1200);
        // The bystander's record is deliberately RICHER: higher standing, and a faction the
        // requester has never touched. None of it may appear.
        fixture.reputation.setReputation(BYSTANDER, guild.id(), 2600);
        fixture.reputation.setReputation(BYSTANDER, salvagers.id(), 777);
        fixture.membership.recordJoin(BYSTANDER, salvagers.id(), 1_000L);

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_STANDING, Map.of());

        assertEquals(1, root.getAsJsonPrimitive("schema_version").getAsInt());
        assertFalse(root.getAsJsonPrimitive("player_online").getAsBoolean());
        assertEquals(1, root.getAsJsonArray("standings").size(),
                "one row: the requester's one faction — the bystander's salvagers row is absent");
        JsonObject row = root.getAsJsonArray("standings").get(0).getAsJsonObject();
        assertEquals("nerofactions:space_guild", row.getAsJsonPrimitive("faction").getAsString());
        assertEquals("Space Guild", row.getAsJsonPrimitive("display_name").getAsString());
        assertEquals(1200, row.getAsJsonPrimitive("value").getAsInt());
        assertEquals(FactionTier.TRUSTED.ordinal(), row.getAsJsonPrimitive("tier").getAsInt());
        assertEquals("trusted", row.getAsJsonPrimitive("tier_name").getAsString());

        String serialised = root.toString();
        assertFalse(serialised.contains(BYSTANDER.toString()),
                "no other player's UUID may appear anywhere in a snapshot");
        assertFalse(serialised.contains("2600"), "the bystander's standing value must not leak");
        assertFalse(serialised.contains("777"), "the bystander's standing value must not leak");
        assertFalse(serialised.contains("salvagers"),
                "a faction only the bystander has history with reveals that history");
    }

    @Test
    void membershipAtZeroStandingStillGetsAnOutsiderRow() {
        FactionDefinition guild = faction("space_guild", "Space Guild");
        Fixture fixture = new Fixture(guild);
        fixture.membership.recordJoin(REQUESTER, guild.id(), 5_000L);
        // No reputation stored at all: canonical zero — the pledge alone earns the row.

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_STANDING, Map.of());

        assertEquals(1, root.getAsJsonArray("standings").size());
        JsonObject row = root.getAsJsonArray("standings").get(0).getAsJsonObject();
        assertEquals(0, row.getAsJsonPrimitive("value").getAsInt());
        assertEquals(FactionTier.OUTSIDER.ordinal(), row.getAsJsonPrimitive("tier").getAsInt());
        assertEquals("outsider", row.getAsJsonPrimitive("tier_name").getAsString());
    }

    @Test
    void tierResolutionMatchesTheLadderAtTheBoundaries() {
        FactionDefinition guild = faction("space_guild", "Space Guild");
        Fixture fixture = new Fixture(guild);
        fixture.reputation.setReputation(REQUESTER, guild.id(), 2500); // exactly at-threshold

        JsonObject row = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_STANDING, Map.of())
                .getAsJsonArray("standings").get(0).getAsJsonObject();

        assertEquals(FactionTier.INNER_CIRCLE.ordinal(), row.getAsJsonPrimitive("tier").getAsInt(),
                "a value exactly at a threshold belongs to the higher tier");
        assertEquals("inner_circle", row.getAsJsonPrimitive("tier_name").getAsString());
    }

    @Test
    void negativeStandingIsReportedAsAnOutsiderRow() {
        FactionDefinition guild = faction("space_guild", "Space Guild");
        Fixture fixture = new Fixture(guild);
        fixture.reputation.setReputation(REQUESTER, guild.id(), -300);

        JsonObject row = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_STANDING, Map.of())
                .getAsJsonArray("standings").get(0).getAsJsonObject();

        assertEquals(-300, row.getAsJsonPrimitive("value").getAsInt());
        assertEquals(FactionTier.OUTSIDER.ordinal(), row.getAsJsonPrimitive("tier").getAsInt());
    }

    // --- membership -------------------------------------------------------------

    @Test
    void membershipSectionCarriesTheRequestersBookkeepingOnly() {
        FactionDefinition guild = faction("space_guild", "Space Guild");
        FactionDefinition salvagers = faction("salvagers", "Salvagers");
        Fixture fixture = new Fixture(guild, salvagers);
        fixture.membership.recordJoin(REQUESTER, guild.id(), 1_000L);
        fixture.membership.recordJoin(REQUESTER, salvagers.id(), 2_000L);
        fixture.membership.recordLeave(REQUESTER, salvagers.id(), 5_000L, 7_000L);
        fixture.membership.recordJoin(BYSTANDER, salvagers.id(), 3_000L);

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_MEMBERSHIP, Map.of());

        assertEquals(1, root.getAsJsonPrimitive("schema_version").getAsInt());
        assertFalse(root.getAsJsonPrimitive("player_online").getAsBoolean());
        JsonObject memberships = root.getAsJsonObject("memberships");
        assertEquals(1, memberships.size());
        assertEquals(1_000L, memberships.getAsJsonPrimitive("nerofactions:space_guild").getAsLong());
        assertEquals(7_000L, root.getAsJsonPrimitive("cooldown_until_ms").getAsLong());
        JsonObject left = root.getAsJsonObject("left");
        assertEquals(1, left.size());
        assertEquals(5_000L, left.getAsJsonPrimitive("nerofactions:salvagers").getAsLong());
        assertFalse(root.toString().contains(BYSTANDER.toString()));
    }

    @Test
    void membershipSectionForAnUnknownPlayerIsShapedButEmpty() {
        Fixture fixture = new Fixture(faction("space_guild", "Space Guild"));

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_MEMBERSHIP, Map.of());

        assertEquals(0, root.getAsJsonObject("memberships").size());
        assertEquals(0L, root.getAsJsonPrimitive("cooldown_until_ms").getAsLong());
        assertEquals(0, root.getAsJsonObject("left").size());
    }

    // --- the empty answers ------------------------------------------------------

    @Test
    void unknownSectionYieldsAnEmptyObject() {
        Fixture fixture = new Fixture(faction("space_guild", "Space Guild"));
        fixture.reputation.setReputation(REQUESTER, fixture.factions.keySet().iterator().next(), 500);

        JsonObject root = fixture.snapshots().snapshot(REQUESTER, "colonies", Map.of());

        assertTrue(root.isEmpty(), "an unknown section says nothing at all — not even an envelope");
    }

    @Test
    void nullPlayerYieldsAnEmptyObject() {
        Fixture fixture = new Fixture(faction("space_guild", "Space Guild"));
        assertTrue(fixture.snapshots()
                .snapshot(null, FactionsLinkModule.SECTION_STANDING, Map.of()).isEmpty());
    }

    @Test
    void noServerYieldsAnEmptyObject() {
        // A Stores seam with nothing to open is exactly the production "no world loaded" state.
        FactionsLinkSnapshots snapshots = new FactionsLinkSnapshots(() -> null);
        assertTrue(snapshots
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_STANDING, Map.of()).isEmpty());
        assertTrue(snapshots
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_MEMBERSHIP, Map.of()).isEmpty());
    }

    // --- discovery metadata -----------------------------------------------------

    @Test
    void providerAdvertisesTheModuleContract() {
        FactionsLinkSnapshots snapshots = new FactionsLinkSnapshots(() -> null);
        assertEquals("nerofactions", snapshots.moduleId());
        assertEquals(FactionsLinkModule.SCHEMA_VERSION, snapshots.schemaVersion());
        assertEquals(List.of("standing", "membership"), snapshots.sections());
    }
}
