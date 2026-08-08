package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * Plain-JVM tests for the DSAR export serialiser: everything both stores hold for the subject —
 * and nothing about anyone else — lands in a stable JSON envelope; a player the mod holds nothing
 * for gets the same envelope with empty sections (an honest "we store nothing about you").
 */
class PlayerDataExportTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");
    private static final long T0 = 1_700_000_000_000L;
    private static final long DAY_STAMP = 19_000L;

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

        String json = PlayerDataExport.toJson(reputation, membership, player, "1.2.3-test", T0);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("nerofactions", root.get("mod").getAsString());
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
        String json = PlayerDataExport.toJson(new FactionReputationState(),
                new FactionMembershipState(), UUID.randomUUID(), "1.2.3-test", T0);
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

        String first = PlayerDataExport.toJson(reputation, membership, player, "1.2.3-test", T0);
        String second = PlayerDataExport.toJson(reputation, membership, player, "1.2.3-test", T0);
        assertEquals(first, second, "same data + same stamp = identical bytes (sorted keys)");
        assertTrue(first.indexOf(GUILD.toString()) < first.indexOf(UNION.toString()),
                "keys are sorted regardless of insertion order");
    }
}
