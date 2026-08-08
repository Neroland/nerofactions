package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * POPIA/GDPR <b>erasure conformance</b> for NeroFactions, run through Core's reusable
 * {@link ErasureConformance} harness — the load-bearing Stage 6 test. NeroFactions is
 * data-handling by nature (reputation and membership keyed by player UUID), and the exact bug
 * this mod's erasure wiring exists to prevent is an eraser that quietly does nothing while the
 * data stays on disk; the harness turns that into a mechanical check, including that the bound
 * {@link ReputationProvider}'s {@code forgetPlayer} is a real override <em>and</em> was actually
 * invoked during the request.
 *
 * <p>Plain JVM, modelled on Core's own {@code ErasureConformanceTest}: both stores are
 * constructed directly ({@code get(server)} needs a live server), the eraser registered here
 * mirrors the production registration in {@link NeroFactionsData} ({@code eraseLocal}: reputation
 * + membership + transient terminal session) minus the {@code SavedDataRecovery.backupNow}
 * refresh, which needs a live level; the {@code MinecraftServer} argument is pass-through and
 * unused. The reputation state itself is bound as the provider — it implements Core's
 * {@link ReputationProvider} with a real {@code forgetPlayer} override, exactly like production
 * (where {@code ServerReputationProvider} delegates to it).
 *
 * <p>Privacy note: every test uses a random UUID and never logs it. The harness's isolation pass
 * deliberately logs one "Data eraser ... failed" warning per run — that is the assertion working.
 */
class NeroFactionsErasureConformanceTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");
    private static final long T0 = 1_700_000_000_000L;
    private static final long DAY_STAMP = 19_000L;

    /** Erasers this test registered, removed again so one test cannot leak into the next. */
    private final List<PlayerDataEraser> registered = new ArrayList<>();

    private ReputationProvider providerBefore;

    @BeforeEach
    void captureProvider() {
        providerBefore = ReputationApi.provider();
    }

    @AfterEach
    void restoreRegistryAndProvider() {
        registered.forEach(PlayerDataErasure::unregister);
        registered.clear();
        ReputationApi.setProvider(providerBefore);
        TerminalSessions.clearAll();
    }

    private void register(PlayerDataEraser eraser) {
        registered.add(eraser);
        PlayerDataErasure.register(eraser);
    }

    /** Seeds every section both stores have, plus the transient terminal session. */
    private static void seed(FactionReputationState reputation, FactionMembershipState membership,
            UUID player) {
        reputation.setReputation(player, GUILD, 120);
        reputation.setReputation(player, UNION, -40);
        membership.recordJoin(player, GUILD, T0);
        membership.recordJoin(player, UNION, T0);
        // Leaving UNION populates the decay bookmark AND arms the join cooldown.
        membership.recordLeave(player, UNION, T0 + 1L, T0 + 3_600_000L);
        membership.addAccrued(player, GUILD, "quest", DAY_STAMP, 120);
        membership.addAccrued(player, GUILD, "combat", DAY_STAMP, 30);
        membership.raiseRewardWatermark(player, GUILD, 3);
        TerminalSessions.current(player, List.of(GUILD), GUILD);
    }

    @Test
    void erasurePurgesEverythingNeroFactionsStores() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();

        FactionReputationState reputation = new FactionReputationState();
        FactionMembershipState membership = new FactionMembershipState();

        // Bind the real store as the provider — a real forgetPlayer override, as in production.
        // The harness fails the run if the bound provider inherits Core's default no-op, or if
        // forgetPlayer is never invoked during the request (the silent-failure cases).
        ReputationApi.setProvider(reputation);

        seed(reputation, membership, player);
        seed(reputation, membership, bystander);

        // Mirrors what NeroFactionsData registers (eraseLocal), minus the SavedDataRecovery
        // backup refresh, which needs a live level.
        register((server, uuid) -> {
            reputation.forgetPlayer(uuid);
            membership.forgetPlayer(uuid);
            TerminalSessions.clear(uuid);
        });

        ErasureConformance.create()
                .probe("nerofactions:reputation", reputation::hasRow)
                .probe("nerofactions:membership", membership::hasRow)
                .verify(null, player);

        // A second player's data must survive the request untouched — erasure is per-subject.
        assertTrue(reputation.hasRow(bystander), "the bystander's standings must survive");
        assertTrue(membership.hasRow(bystander), "the bystander's membership row must survive");

        // The harness must have restored the provider it instrumented.
        assertSame(reputation, ReputationApi.provider(), "the recording wrapper must be removed");
    }

    @Test
    void conformanceReportIsCleanAndCarriesNoPlayerIdentity() {
        UUID player = UUID.randomUUID();

        FactionReputationState reputation = new FactionReputationState();
        FactionMembershipState membership = new FactionMembershipState();
        ReputationApi.setProvider(reputation);
        seed(reputation, membership, player);

        register((server, uuid) -> {
            reputation.forgetPlayer(uuid);
            membership.forgetPlayer(uuid);
            TerminalSessions.clear(uuid);
        });

        ErasureConformance.Report report = ErasureConformance.create()
                .probe("nerofactions:reputation", reputation::hasRow)
                .probe("nerofactions:membership", membership::hasRow)
                .run(null, player);

        // A passing report implies the provider-override and forgetPlayer-invoked checks passed —
        // the harness records those as failures otherwise.
        assertTrue(report.passed(), report.describe());
        assertTrue(report.retainedSubsystems().isEmpty());
        assertTrue(report.erasersRegistered() >= 1, "our eraser must be in the fan-out");
        assertFalse(report.describe().contains(player.toString()),
                "the report must never carry the player UUID (it is personal data)");

        // The transient terminal session went with the same request.
        assertFalse(TerminalSessions.current(player, List.of(GUILD), UNION).equals(GUILD),
                "the remembered terminal selection must be gone (falls back to preferred)");
    }
}
