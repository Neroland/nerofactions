package za.co.neroland.nerofactions.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.RewardEntry;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.reputation.TierCrossings;

/**
 * Plain-JVM tests of the reward-granting core: the watermark makes grants <b>idempotent for
 * life</b> — each tier's rewards pay exactly once ever per player+faction, decay + re-crossing
 * never re-grants, downward crossings never grant, and erasure (the whole-row
 * {@code forgetPlayer}) resets eligibility. Crossings are produced by the real
 * {@link TierCrossings#crossings} enumeration so the two engines can never drift apart.
 */
class RewardGrantsTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");

    private static final RewardEntry ASSOCIATE_ITEM =
            new RewardEntry("item", Optional.of(Identifier.parse("minecraft:iron_ingot")), 8,
                    Optional.empty());
    private static final RewardEntry ASSOCIATE_COSMETIC =
            new RewardEntry("cosmetic", Optional.empty(), 1, Optional.of("banner_pattern"));
    private static final RewardEntry MEMBER_ITEM =
            new RewardEntry("item", Optional.of(Identifier.parse("minecraft:gold_ingot")), 4,
                    Optional.empty());

    private static FactionDefinition faction() {
        return new FactionDefinition(GUILD, "Test Guild", "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000,
                        "inner_circle", 2500),
                Map.of("associate", List.of(ASSOCIATE_ITEM, ASSOCIATE_COSMETIC),
                        "member", List.of(MEMBER_ITEM)),
                List.of(), List.of(), Optional.empty());
    }

    /** One recorded delivery: which tier paid which entry. */
    private record Delivery(FactionTier tier, RewardEntry entry) {
    }

    @Test
    void risingCrossingGrantsTheTierTableOnceAndRaisesTheWatermark() {
        FactionMembershipState state = new FactionMembershipState();
        FactionDefinition guild = faction();
        UUID player = UUID.randomUUID();
        List<Delivery> delivered = new ArrayList<>();

        List<FactionTier> granted = RewardGrants.grant(state, guild, player,
                TierCrossings.crossings(guild, 0, 150),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));

        assertEquals(List.of(FactionTier.ASSOCIATE), granted);
        assertEquals(List.of(new Delivery(FactionTier.ASSOCIATE, ASSOCIATE_ITEM),
                new Delivery(FactionTier.ASSOCIATE, ASSOCIATE_COSMETIC)), delivered,
                "both entry kinds (item and cosmetic) deliver, in table order");
        assertEquals(FactionTier.ASSOCIATE.ordinal(), state.rewardWatermark(player, GUILD));
    }

    @Test
    void multiTierJumpGrantsEachNewlyReachedTierInAscendingOrder() {
        FactionMembershipState state = new FactionMembershipState();
        FactionDefinition guild = faction();
        UUID player = UUID.randomUUID();
        List<Delivery> delivered = new ArrayList<>();

        List<FactionTier> granted = RewardGrants.grant(state, guild, player,
                TierCrossings.crossings(guild, 0, 1200),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));

        // Trusted ships no table in this fixture, but the tier still counts as granted (and the
        // watermark still rises past it) — an empty table is a valid table.
        assertEquals(List.of(FactionTier.ASSOCIATE, FactionTier.MEMBER, FactionTier.TRUSTED),
                granted);
        assertEquals(3, delivered.size(), "associate's two entries + member's one");
        assertEquals(FactionTier.TRUSTED.ordinal(), state.rewardWatermark(player, GUILD));
    }

    @Test
    void decayAndRecrossNeverRegrants() {
        FactionMembershipState state = new FactionMembershipState();
        FactionDefinition guild = faction();
        UUID player = UUID.randomUUID();
        List<Delivery> delivered = new ArrayList<>();

        RewardGrants.grant(state, guild, player, TierCrossings.crossings(guild, 0, 500),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));
        assertEquals(3, delivered.size());

        // Standing decays below Associate, then is re-earned past Member again.
        List<FactionTier> downward = RewardGrants.grant(state, guild, player,
                TierCrossings.crossings(guild, 500, 50),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));
        assertTrue(downward.isEmpty(), "downward never grants");
        assertEquals(3, delivered.size(), "downward delivered nothing");

        List<FactionTier> recross = RewardGrants.grant(state, guild, player,
                TierCrossings.crossings(guild, 50, 500),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));
        assertTrue(recross.isEmpty(), "the watermark is a high-water mark: no re-grant, ever");
        assertEquals(3, delivered.size());
        assertEquals(FactionTier.MEMBER.ordinal(), state.rewardWatermark(player, GUILD),
                "the watermark never lowered through decay and re-crossing");
    }

    @Test
    void downwardOnlyChangeTouchesNothing() {
        FactionMembershipState state = new FactionMembershipState();
        FactionDefinition guild = faction();
        UUID player = UUID.randomUUID();
        List<Delivery> delivered = new ArrayList<>();

        List<FactionTier> granted = RewardGrants.grant(state, guild, player,
                TierCrossings.crossings(guild, 1200, 0),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));

        assertTrue(granted.isEmpty());
        assertTrue(delivered.isEmpty());
        assertEquals(0, state.rewardWatermark(player, GUILD), "no watermark from falling");
    }

    @Test
    void erasureClearsTheWatermarkAndRestoresEligibility() {
        FactionMembershipState state = new FactionMembershipState();
        FactionDefinition guild = faction();
        UUID player = UUID.randomUUID();
        List<Delivery> delivered = new ArrayList<>();

        RewardGrants.grant(state, guild, player, TierCrossings.crossings(guild, 0, 150),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));
        assertEquals(FactionTier.ASSOCIATE.ordinal(), state.rewardWatermark(player, GUILD));

        // The registered eraser delegates to forgetPlayer: the whole row — watermark included.
        state.forgetPlayer(player);
        assertEquals(0, state.rewardWatermark(player, GUILD), "erasure clears the watermark");

        List<FactionTier> regrant = RewardGrants.grant(state, guild, player,
                TierCrossings.crossings(guild, 0, 150),
                (tier, entry) -> delivered.add(new Delivery(tier, entry)));
        assertEquals(List.of(FactionTier.ASSOCIATE), regrant,
                "an erased player is reward-eligible again (no tombstone, by design)");
    }

    @Test
    void watermarkIsHighWaterOnly() {
        FactionMembershipState state = new FactionMembershipState();
        UUID player = UUID.randomUUID();

        assertTrue(state.raiseRewardWatermark(player, GUILD, 3));
        assertTrue(!state.raiseRewardWatermark(player, GUILD, 2), "never lowered");
        assertTrue(!state.raiseRewardWatermark(player, GUILD, 3), "never re-raised in place");
        assertEquals(3, state.rewardWatermark(player, GUILD));
        assertTrue(state.raiseRewardWatermark(player, GUILD, 4));
        assertEquals(4, state.rewardWatermark(player, GUILD));
    }
}
