package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.function.BiFunction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.faction.minerunion.QuotaState;

/**
 * Plain-JVM tests for the DSAR export serialiser: everything every listed store holds for the subject —
 * and nothing about anyone else — lands in a stable JSON envelope; a player the mod holds nothing
 * for gets the same envelope with empty sections (an honest "we store nothing about you").
 */
class PlayerDataExportTest {

    @Test
    void contractExportContainsOnlyTheRequestersCurrentStateAndErasesCleanly() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var contracts = new ContractState();
        var faction = za.co.neroland.nerofactions.contracts.ContractFixtures.FACTION;
        var pool = za.co.neroland.nerofactions.contracts.ContractFixtures.pool();
        contracts.ensure(player, faction, 0, 123, 5, pool);
        contracts.ensure(other, faction, 0, 123, 6, pool);
        contracts.advance(player, faction, 0, 3);
        String json = export(stores(new FactionReputationState(), new FactionMembershipState(), contracts, new QuotaState(), new OutpostVisits()), player, "test");
        var section = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("contracts");
        assertEquals(5, section.get("day").getAsLong());
        assertEquals(3, section.getAsJsonArray("entries").size());
        assertFalse(json.contains(player.toString()));
        assertFalse(json.contains(other.toString()));
        contracts.forget(player);
        assertEquals(0, contracts.export(player).getAsJsonArray("entries").size());
        assertEquals(3, contracts.export(other).getAsJsonArray("entries").size());
    }

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");
    private static final long T0 = 1_700_000_000_000L;
    private static final long DAY_STAMP = 19_000L;

    private static final int BOON_TICKS = 640;

    /** The five stores the older tests seed, installed into an otherwise fresh store set. */
    private static FactionsStores.Snapshot stores(FactionReputationState reputation,
            FactionMembershipState membership, ContractState contracts, QuotaState quota, OutpostVisits visits) {
        return FactionsStores.fresh().with(FactionsStores.REPUTATION, reputation)
                .with(FactionsStores.MEMBERSHIP, membership).with(FactionsStores.CONTRACTS, contracts)
                .with(FactionsStores.MINER_QUOTA, quota).with(FactionsStores.OUTPOST_VISITS, visits);
    }

    /** Exports with no transient state held for anyone. */
    private static String export(FactionsStores.Snapshot stores, UUID player, String version) {
        return PlayerDataExport.toJson(stores, (state, uuid) -> new JsonPrimitive(0), player, version, T0);
    }

    @Test
    void everyStoreInTheListHasASectionInTheExport() {
        JsonObject root = JsonParser.parseString(
                export(FactionsStores.fresh(), UUID.randomUUID(), "test")).getAsJsonObject();
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            assertTrue(root.has(store.exportKey()), "missing export section for " + store.id());
        }
        for (FactionsStores.Transient state : FactionsStores.TRANSIENT) {
            if (state.exportKey() != null) {
                assertTrue(root.has(state.exportKey()), "missing export section for " + state.id());
            }
        }
    }

    @Test
    void corporateWorkbenchAndVoidBoonAreExportedForTheSubjectOnly() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID bench = UUID.randomUUID();
        FactionsStores.Snapshot stores = FactionsStores.fresh();
        assertTrue(stores.get(FactionsStores.CORPORATE_LEDGER).reserveDelivery(player, 15));
        assertTrue(stores.get(FactionsStores.WORKBENCH_ACCESS).claim(bench, player));
        BiFunction<FactionsStores.Transient, UUID, JsonElement> boons = (state, uuid) ->
                new JsonPrimitive(player.equals(uuid) ? BOON_TICKS : 0);

        JsonObject subject = JsonParser.parseString(
                PlayerDataExport.toJson(stores, boons, player, "test", T0)).getAsJsonObject();
        assertEquals(15, subject.getAsJsonObject("corporate_daily").get("day").getAsLong());
        assertTrue(subject.getAsJsonObject("corporate_daily").get("delivered").getAsBoolean());
        assertEquals(1, subject.getAsJsonObject("workbench_access").getAsJsonArray("owned").size());
        assertEquals(BOON_TICKS, subject.get("void_boon_ticks_remaining").getAsInt());

        JsonObject other = JsonParser.parseString(
                PlayerDataExport.toJson(stores, boons, bystander, "test", T0)).getAsJsonObject();
        assertEquals(0, other.getAsJsonObject("corporate_daily").size(),
                "the subject's ledger row must not appear in another player's export");
        assertEquals(0, other.getAsJsonObject("workbench_access").getAsJsonArray("owned").size());
        assertEquals(0, other.getAsJsonObject("workbench_access").getAsJsonArray("invited").size());
        assertEquals(0, other.get("void_boon_ticks_remaining").getAsInt());
    }

    @Test
    void seededStoresSerialiseEverySection() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();

        FactionReputationState reputation = new FactionReputationState();
        FactionMembershipState membership = new FactionMembershipState();
        reputation.setReputation(player, GUILD, 120);
        reputation.setReputation(player, UNION, -40);
        reputation.setReputation(bystander, GUILD, 999);
        membership.recordJoin(player, GUILD, T0);
        membership.recordJoin(player, UNION, T0 + 5L);
        membership.recordLeave(player, UNION, T0 + 10L, T0 + 3_600_000L);
        membership.addAccrued(player, GUILD, "quest", DAY_STAMP, 120);
        membership.raiseRewardWatermark(player, GUILD, 3);
        membership.recordJoin(bystander, GUILD, T0);

        String json = export(stores(reputation, membership, new ContractState(), new QuotaState(), new OutpostVisits()), player, "1.2.3-test");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("nerofactions", root.get("mod").getAsString());
        assertEquals(0, root.getAsJsonObject("miner_quota").size());
        assertEquals("1.2.3-test", root.get("mod_version").getAsString());
        assertEquals("2023-11-14T22:13:20Z", root.get("generated_at").getAsString());

        JsonObject standings = root.getAsJsonObject("reputation").getAsJsonObject("standings");
        assertEquals(2, standings.size());
        assertEquals(120, standings.get(GUILD.toString()).getAsInt());
        assertEquals(-40, standings.get(UNION.toString()).getAsInt());

        JsonObject membershipSection = root.getAsJsonObject("membership");
        JsonObject memberships = membershipSection.getAsJsonObject("memberships");
        assertEquals(1, memberships.size(), "UNION was left again");
        assertEquals(T0, memberships.get(GUILD.toString()).getAsLong());
        assertEquals(T0 + 3_600_000L, membershipSection.get("cooldown_until_ms").getAsLong());
        JsonObject left = membershipSection.getAsJsonObject("left");
        assertEquals(T0 + 10L, left.get(UNION.toString()).getAsLong());
        JsonObject accrual = membershipSection.getAsJsonObject("accrual")
                .getAsJsonObject(GUILD.toString()).getAsJsonObject("quest");
        assertEquals(DAY_STAMP, accrual.get("day").getAsLong());
        assertEquals(120, accrual.get("accrued").getAsInt());
        assertEquals(3, membershipSection.getAsJsonObject("reward_watermarks")
                .get(GUILD.toString()).getAsInt());

        // Data minimisation: nobody else's rows, and no UUID at all, travel in the export.
        assertFalse(json.contains("999"), "the bystander's standing must not leak into the export");
        assertFalse(json.contains(player.toString()), "the export carries no UUID");
        assertFalse(json.contains(bystander.toString()));
    }

    @Test
    void aPlayerTheModHoldsNothingForGetsAnEmptyEnvelope() {
        String json = export(stores(new FactionReputationState(), new FactionMembershipState(), new ContractState(), new QuotaState(), new OutpostVisits()), UUID.randomUUID(), "1.2.3-test");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("nerofactions", root.get("mod").getAsString());
        assertEquals(0, root.getAsJsonObject("reputation").getAsJsonObject("standings").size());
        JsonObject membershipSection = root.getAsJsonObject("membership");
        assertEquals(0, membershipSection.getAsJsonObject("memberships").size());
        assertEquals(0L, membershipSection.get("cooldown_until_ms").getAsLong());
        assertEquals(0, membershipSection.getAsJsonObject("left").size());
        assertEquals(0, membershipSection.getAsJsonObject("accrual").size());
        assertEquals(0, membershipSection.getAsJsonObject("reward_watermarks").size());
    }

    @Test
    void twoExportsOfTheSameDataAreByteIdentical() {
        UUID player = UUID.randomUUID();
        FactionReputationState reputation = new FactionReputationState();
        FactionMembershipState membership = new FactionMembershipState();
        reputation.setReputation(player, UNION, 10);
        reputation.setReputation(player, GUILD, 20);
        membership.recordJoin(player, UNION, T0);
        membership.recordJoin(player, GUILD, T0);

        String first = export(stores(reputation, membership, new ContractState(), new QuotaState(), new OutpostVisits()), player, "1.2.3-test");
        String second = export(stores(reputation, membership, new ContractState(), new QuotaState(), new OutpostVisits()), player, "1.2.3-test");
        assertEquals(first, second, "same data + same stamp = identical bytes (sorted keys)");
        assertTrue(first.indexOf(GUILD.toString()) < first.indexOf(UNION.toString()),
                "keys are sorted regardless of insertion order");
    }

    @Test
    void coreSectionIsNullForAPlayerTheModHoldsNothingFor() {
        String json = export(stores(new FactionReputationState(), new FactionMembershipState(), new ContractState(), new QuotaState(), new OutpostVisits()), UUID.randomUUID(), "1.2.3-test");
        assertEquals(null, PlayerDataExport.coreSection(JsonParser.parseString(json).getAsJsonObject()));
    }

    @Test
    void coreSectionIsTheSameDocumentWithoutTheEnvelope() {
        UUID player = UUID.randomUUID();
        FactionReputationState reputation = new FactionReputationState();
        reputation.setReputation(player, UNION, 10);
        String json = export(stores(reputation, new FactionMembershipState(), new ContractState(), new QuotaState(), new OutpostVisits()), player, "1.2.3-test");
        JsonElement section = PlayerDataExport.coreSection(JsonParser.parseString(json).getAsJsonObject());

        assertTrue(section != null && section.isJsonObject());
        JsonObject root = section.getAsJsonObject();
        assertFalse(root.has("mod"));
        assertFalse(root.has("generated_at"));
        assertEquals(10, root.getAsJsonObject("reputation").getAsJsonObject("standings").get(UNION.toString()).getAsInt());
        assertFalse(section.toString().contains(player.toString()), "the export carries no UUID");
    }
}
