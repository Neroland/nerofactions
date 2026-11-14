package za.co.neroland.nerofactions.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.contracts.ContractFixtures;

/**
 * The snapshot sections' shapes and — above all — their scoping: a request sees the requesting
 * UUID's rows and nothing else, even when a bystander's record is richer. Plain JVM, null server:
 * the {@link FactionsLinkSnapshots.Stores} seam is filled with direct stores ({@link LinkFixture}), exactly the shape
 * the production path resolves from the bound server.
 */
class FactionsLinkSnapshotsTest {

    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID BYSTANDER = UUID.randomUUID();

    private static FactionDefinition faction(String path, String displayName) {
        return LinkFixture.faction(path, displayName);
    }

    @Test
    void outpostVisitsExposeOnlyTheRequestersFactionAndDimension() {
        LinkFixture fixture = new LinkFixture();
        Identifier overworld = Identifier.parse("minecraft:overworld");
        fixture.visits.record(REQUESTER, OutpostFaction.MINER_UNION, overworld);
        fixture.visits.record(BYSTANDER, OutpostFaction.VOID_CULT, overworld);

        var rows = fixture.snapshots().snapshot(REQUESTER, FactionsLinkModule.SECTION_OUTPOSTS_VISITED,
                Map.of("player", BYSTANDER.toString())).getAsJsonArray("outposts_visited");
        assertEquals(1, rows.size());
        var row = rows.get(0).getAsJsonObject();
        assertEquals(Set.of("faction", "dimension"), row.keySet());
        assertEquals("nerofactions:miner_union", row.get("faction").getAsString());
        assertEquals("minecraft:overworld", row.get("dimension").getAsString());
    }

    @Test
    void contractsAreRequesterScopedAndDoNotRevealStaleOrOtherPlayersProgress() {
        Identifier faction = ContractFixtures.FACTION;
        LinkFixture fixture = new LinkFixture(faction(faction.getPath(), "Contract faction"));
        UUID requester = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        fixture.membership.recordJoin(requester, faction, 1);
        fixture.membership.recordJoin(bystander, faction, 1);
        fixture.contractState.ensure(requester, faction, 0, 4, 5, ContractFixtures.pool());
        fixture.contractState.ensure(bystander, faction, 0, 4, 5, ContractFixtures.pool());
        fixture.contractState.advance(bystander, faction, 0, 9);
        var snapshot = fixture.snapshots().snapshot(requester, "contracts", Map.of("player", bystander.toString()));
        var rows = snapshot.getAsJsonArray("contracts");
        assertEquals(3, rows.size());
        assertEquals(0, rows.get(0).getAsJsonObject().get("progress").getAsInt());
        assertFalse(snapshot.toString().contains(bystander.toString()));
        assertFalse(snapshot.toString().contains("coordinates"));
        assertFalse(snapshot.toString().contains("position"));
        fixture.membership.recordLeave(requester, faction, 2, 0);
        assertEquals(0, fixture.snapshots().snapshot(requester, "contracts", Map.of())
                .getAsJsonArray("contracts").size());
    }

    // --- standing: scoping ------------------------------------------------------

    @Test
    void standingReturnsExactlyTheRequestersRows() {
        FactionDefinition guild = faction("space_guild", "Space Guild");
        FactionDefinition salvagers = faction("salvagers", "Salvagers");
        LinkFixture fixture = new LinkFixture(guild, salvagers);
        fixture.reputation.setReputation(REQUESTER, guild.id(), 1200);
        // The bystander's record is deliberately RICHER: higher standing, and a faction the
        // requester has never touched. None of it may appear.
        fixture.reputation.setReputation(BYSTANDER, guild.id(), 2600);
        fixture.reputation.setReputation(BYSTANDER, salvagers.id(), 777);
        fixture.membership.recordJoin(BYSTANDER, salvagers.id(), 1_000L);

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_STANDING, Map.of());

        assertEquals(2, root.getAsJsonPrimitive("schema_version").getAsInt());
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
        LinkFixture fixture = new LinkFixture(guild);
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
        LinkFixture fixture = new LinkFixture(guild);
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
        LinkFixture fixture = new LinkFixture(guild);
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
        LinkFixture fixture = new LinkFixture(guild, salvagers);
        fixture.membership.recordJoin(REQUESTER, guild.id(), 1_000L);
        fixture.membership.recordJoin(REQUESTER, salvagers.id(), 2_000L);
        fixture.membership.recordLeave(REQUESTER, salvagers.id(), 5_000L, 7_000L);
        fixture.membership.recordJoin(BYSTANDER, salvagers.id(), 3_000L);

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_MEMBERSHIP, Map.of());

        assertEquals(2, root.getAsJsonPrimitive("schema_version").getAsInt());
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
        LinkFixture fixture = new LinkFixture(faction("space_guild", "Space Guild"));

        JsonObject root = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_MEMBERSHIP, Map.of());

        assertEquals(0, root.getAsJsonObject("memberships").size());
        assertEquals(0L, root.getAsJsonPrimitive("cooldown_until_ms").getAsLong());
        assertEquals(0, root.getAsJsonObject("left").size());
    }

    // --- the empty answers ------------------------------------------------------

    @Test
    void unknownSectionYieldsAnEmptyObject() {
        LinkFixture fixture = new LinkFixture(faction("space_guild", "Space Guild"));
        fixture.reputation.setReputation(REQUESTER, fixture.factions.keySet().iterator().next(), 500);

        JsonObject root = fixture.snapshots().snapshot(REQUESTER, "colonies", Map.of());

        assertTrue(root.isEmpty(), "an unknown section says nothing at all — not even an envelope");
    }

    @Test
    void nullPlayerYieldsAnEmptyObject() {
        LinkFixture fixture = new LinkFixture(faction("space_guild", "Space Guild"));
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

    // --- gear --------------------------------------------------------------------

    @Test
    void gearUnlocksFollowTheRequestersOwnTierNotABystanders() {
        Identifier union = Identifier.parse("nerofactions:miner_union");
        LinkFixture fixture = new LinkFixture(faction("miner_union", "Miner Union"));
        fixture.membership.recordJoin(REQUESTER, union, 1L);
        fixture.membership.recordJoin(BYSTANDER, union, 1L);
        fixture.reputation.setReputation(REQUESTER, union, 150);   // ASSOCIATE
        fixture.reputation.setReputation(BYSTANDER, union, 1500);  // TRUSTED

        JsonObject associate = unionSet(fixture);
        assertFalse(associate.get("eligible").getAsBoolean());
        associate.getAsJsonArray("pieces").forEach(piece ->
                assertFalse(piece.getAsJsonObject().get("unlocked").getAsBoolean(),
                        "a bystander's TRUSTED standing must not unlock the requester's gear"));

        fixture.reputation.setReputation(REQUESTER, union, 1200);  // TRUSTED
        JsonObject trusted = unionSet(fixture);
        assertTrue(trusted.get("eligible").getAsBoolean());
        assertEquals(4, trusted.getAsJsonArray("pieces").size());
        trusted.getAsJsonArray("pieces").forEach(piece ->
                assertTrue(piece.getAsJsonObject().get("unlocked").getAsBoolean()));
    }

    private static JsonObject unionSet(LinkFixture fixture) {
        JsonArray sets = fixture.snapshots()
                .snapshot(REQUESTER, FactionsLinkModule.SECTION_GEAR, Map.of()).getAsJsonArray("sets");
        assertEquals(1, sets.size(), "only sets whose faction is loaded are listed");
        JsonObject set = sets.get(0).getAsJsonObject();
        assertEquals("nerofactions:miner_union", set.get("faction").getAsString());
        return set;
    }

    // --- every section: no coordinates, no bystander -----------------------------

    private static final Pattern POSITION_KEY =
            Pattern.compile("x|y|z|pos|position|block_pos|coords", Pattern.CASE_INSENSITIVE);

    @Test
    void noSectionCarriesAPositionKeyOrAnyBystanderData() {
        Identifier union = ContractFixtures.FACTION;
        String hiddenPath = "hidden_" + UUID.randomUUID().toString().replace("-", "");
        Identifier hidden = Identifier.fromNamespaceAndPath("nerofactions", hiddenPath);
        LinkFixture fixture = new LinkFixture(faction(union.getPath(), "Miner Union"),
                faction(hiddenPath, "Bystander Only"));
        UUID requester = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        Identifier overworld = Identifier.parse("minecraft:overworld");

        fixture.online.add(requester);
        fixture.online.add(bystander);
        fixture.membership.recordJoin(requester, union, 1_000L);
        fixture.reputation.setReputation(requester, union, 150);
        fixture.contractState.ensure(requester, union, 0, 4, 5, ContractFixtures.pool());
        fixture.visits.record(requester, OutpostFaction.MINER_UNION, overworld);

        // A richer bystander record, every value unique to them.
        fixture.membership.recordJoin(bystander, union, 1_111_111L);
        fixture.membership.recordJoin(bystander, hidden, 1_234_567L);
        fixture.membership.recordLeave(bystander, union, 7_654_321L, 9_876_543L);
        fixture.reputation.setReputation(bystander, union, 2777);
        fixture.reputation.setReputation(bystander, hidden, 2888);
        fixture.contractState.ensure(bystander, union, 0, 4, 5, ContractFixtures.pool());
        fixture.contractState.advance(bystander, union, 0, 9);
        fixture.visits.record(bystander, OutpostFaction.VOID_CULT, Identifier.parse("minecraft:the_nether"));

        FactionsLinkSnapshots snapshots = fixture.snapshots();
        for (String section : snapshots.sections()) {
            JsonObject root = snapshots.snapshot(requester, section, Map.of("player", bystander.toString()));
            assertTrue(root.has("schema_version"), section + " must answer, not fail closed");
            assertNoPositionKey(section, root);
            String serialised = root.toString();
            for (String leak : List.of(bystander.toString(), "1111111", "1234567", "7654321", "9876543",
                    "2777", "2888", "the_nether", "void_cult", hiddenPath)) {
                assertFalse(serialised.contains(leak), section + " leaked a bystander value");
            }
        }
    }

    private static void assertNoPositionKey(String section, JsonElement element) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                assertFalse(POSITION_KEY.matcher(entry.getKey()).matches(),
                        section + " carries a position-shaped key: " + entry.getKey());
                assertNoPositionKey(section, entry.getValue());
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> assertNoPositionKey(section, child));
        }
    }

    // --- discovery metadata -----------------------------------------------------

    @Test
    void providerAdvertisesTheModuleContract() {
        FactionsLinkSnapshots snapshots = new FactionsLinkSnapshots(() -> null);
        assertEquals("nerofactions", snapshots.moduleId());
        assertEquals(FactionsLinkModule.SCHEMA_VERSION, snapshots.schemaVersion());
        assertEquals(List.of("standing", "membership", "contracts", "gear", "outposts_visited"), snapshots.sections());
    }
}
