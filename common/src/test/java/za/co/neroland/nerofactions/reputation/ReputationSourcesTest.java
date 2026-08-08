package za.co.neroland.nerofactions.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerofactions.data.FactionReputationState;
import za.co.neroland.nerofactions.reputation.ReputationSources.Source;
import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * Plain-JVM tests of the weighted/capped source-award core and enemy bleed, driven through the
 * package-private parameterised overload (no server, explicit day stamps and config inputs).
 *
 * <p>The enemy graph under test is deliberately asymmetric: A lists B, B lists C, C lists nobody —
 * so the tests can prove bleed follows exactly the target's list, respects asymmetry, and never
 * cascades into the enemy's own enemies.
 */
class ReputationSourcesTest {

    private static final Identifier A = Identifier.parse("nerofactions:test_a");
    private static final Identifier B = Identifier.parse("nerofactions:test_b");
    private static final Identifier C = Identifier.parse("nerofactions:test_c");

    private static final FactionDefinition FACTION_A = definition(A, List.of(B));
    private static final FactionDefinition FACTION_B = definition(B, List.of(C));

    private static final long DAY_1 = 20_000L;
    private static final long DAY_2 = 20_001L;
    private static final int UNCAPPED = -1;

    private ReputationProvider originalProvider;
    private FactionMembershipState state;
    private UUID player;

    private static FactionDefinition definition(Identifier id, List<Identifier> enemies) {
        return new FactionDefinition(id, "Test " + id.getPath(), "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000,
                        "inner_circle", 2500),
                Map.of(), enemies, List.of(), Optional.empty());
    }

    @BeforeEach
    void setUp() {
        originalProvider = ReputationApi.provider();
        ReputationApi.setProvider(new FactionReputationState());
        state = new FactionMembershipState();
        player = UUID.randomUUID();
    }

    @AfterEach
    void restoreProvider() {
        ReputationApi.setProvider(originalProvider);
    }

    @Test
    void weightsScaleTheBaseAmount() {
        int applied = ReputationSources.award(state, FACTION_A, player, Source.COMBAT, 100,
                DAY_1, 0.6D, 150, 0.0D);
        assertEquals(60, applied);
        assertEquals(60, ReputationApi.getReputation(player, A));

        applied = ReputationSources.award(state, FACTION_A, player, Source.TRADE, 10,
                DAY_1, 0.3D, 100, 0.0D);
        assertEquals(3, applied, "0.3 weight rounds 10 to 3");
    }

    @Test
    void dailyCapClampsAndThenExhausts() {
        assertEquals(100, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 100,
                DAY_1, 1.0D, 150, 0.0D));
        assertEquals(50, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 100,
                DAY_1, 1.0D, 150, 0.0D), "only the remaining headroom is granted");
        assertEquals(0, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 100,
                DAY_1, 1.0D, 150, 0.0D), "an exhausted cap grants nothing");
        assertEquals(150, ReputationApi.getReputation(player, A));
    }

    @Test
    void capsArePerSourceAndPerFaction() {
        assertEquals(150, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 150,
                DAY_1, 1.0D, 150, 0.0D));
        assertEquals(0, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 1,
                DAY_1, 1.0D, 150, 0.0D));

        // A different source against the same faction has its own counter…
        assertEquals(90, ReputationSources.award(state, FACTION_A, player, Source.COMBAT, 150,
                DAY_1, 0.6D, 150, 0.0D));
        // …and the same source against a different faction does too.
        assertEquals(150, ReputationSources.award(state, FACTION_B, player, Source.QUEST, 150,
                DAY_1, 1.0D, 150, 0.0D));
    }

    @Test
    void dayRollResetsTheCap() {
        assertEquals(150, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 200,
                DAY_1, 1.0D, 150, 0.0D));
        assertEquals(0, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 10,
                DAY_1, 1.0D, 150, 0.0D));

        assertEquals(150, ReputationSources.award(state, FACTION_A, player, Source.QUEST, 200,
                DAY_2, 1.0D, 150, 0.0D), "a new day stamp starts a fresh counter");
        assertEquals(300, ReputationApi.getReputation(player, A));
    }

    @Test
    void adminBypassesTheCapAtWeightOne() {
        assertEquals(10_000, ReputationSources.award(state, FACTION_A, player, Source.ADMIN, 10_000,
                DAY_1, 1.0D, UNCAPPED, 0.0D));
        assertEquals(10_000, ReputationSources.award(state, FACTION_A, player, Source.ADMIN, 10_000,
                DAY_1, 1.0D, UNCAPPED, 0.0D), "no daily cap ever applies to ADMIN");
        assertEquals(20_000, ReputationApi.getReputation(player, A));
    }

    @Test
    void adminNeverBleedsEvenWithANonZeroRatio() {
        // Regression: Stage 2 shipped ADMIN bleeding; operator actions must be exact.
        int applied = ReputationSources.award(state, FACTION_A, player, Source.ADMIN, 100,
                DAY_1, 1.0D, UNCAPPED, 0.5D);
        assertEquals(100, applied);
        assertEquals(100, ReputationApi.getReputation(player, A));
        assertEquals(0, ReputationApi.getReputation(player, B),
                "ADMIN awards never bleed A's listed enemy, whatever the ratio");
    }

    @Test
    void enemyBleedFollowsExactlyTheTargetsListAndNeverCascades() {
        int applied = ReputationSources.award(state, FACTION_A, player, Source.QUEST, 100,
                DAY_1, 1.0D, 300, 0.5D);
        assertEquals(100, applied);
        assertEquals(100, ReputationApi.getReputation(player, A));
        assertEquals(-50, ReputationApi.getReputation(player, B),
                "A's listed enemy bleeds round(delta * ratio)");
        assertEquals(0, ReputationApi.getReputation(player, C),
                "bleed never cascades into the enemy's own enemies");
    }

    @Test
    void bleedRespectsAsymmetry() {
        // B lists C (not A): awarding B bleeds C and leaves A untouched.
        ReputationSources.award(state, FACTION_B, player, Source.QUEST, 100,
                DAY_1, 1.0D, 300, 0.5D);
        assertEquals(100, ReputationApi.getReputation(player, B));
        assertEquals(-50, ReputationApi.getReputation(player, C));
        assertEquals(0, ReputationApi.getReputation(player, A),
                "C lists nobody and B does not list A — asymmetric graphs are respected");
    }

    @Test
    void zeroRatioDisablesBleedEntirely() {
        ReputationSources.award(state, FACTION_A, player, Source.QUEST, 100,
                DAY_1, 1.0D, 300, 0.0D);
        assertEquals(100, ReputationApi.getReputation(player, A));
        assertEquals(0, ReputationApi.getReputation(player, B), "ratio 0.0 is a true off switch");
    }

    @Test
    void aFullyClampedAwardDoesNotBleed() {
        ReputationSources.award(state, FACTION_A, player, Source.QUEST, 300,
                DAY_1, 1.0D, 300, 0.5D);
        int enemyBefore = ReputationApi.getReputation(player, B);

        int applied = ReputationSources.award(state, FACTION_A, player, Source.QUEST, 100,
                DAY_1, 1.0D, 300, 0.5D);
        assertEquals(0, applied);
        assertEquals(enemyBefore, ReputationApi.getReputation(player, B),
                "no primary delta, no bleed");
    }
}
