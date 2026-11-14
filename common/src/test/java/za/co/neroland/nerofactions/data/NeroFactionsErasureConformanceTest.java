package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import za.co.neroland.nerofactions.content.faction.minerunion.QuotaState;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
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

    @Test
    void outpostVisitsRoundTripAndErasureLeavesOnlyTheOtherPlayer() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        Identifier overworld = Identifier.parse("minecraft:overworld");
        var original = new OutpostVisits();
        assertTrue(original.record(player, OutpostFaction.SPACE_GUILD, overworld));
        assertFalse(original.record(player, OutpostFaction.SPACE_GUILD, overworld));
        assertFalse(original.record(player, OutpostFaction.NONE, overworld));
        assertTrue(original.record(bystander, OutpostFaction.SALVAGERS, overworld));
        var encoded = OutpostVisits.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var visits = OutpostVisits.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        register((server, uuid) -> {
            visits.forget(uuid);
            reputation.forgetPlayer(uuid);
        });
        ErasureConformance.create().probe("nerofactions:outpost_visits", uuid -> visits.knownPlayers().contains(uuid))
                .verify(null, player);
        assertTrue(visits.export(player).isEmpty());
        assertFalse(visits.export(bystander).isEmpty());
    }

    @Test
    void contractAssignmentsProgressTokensAndCountAreErasedForOnlyTheSubject() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        var contracts = new ContractState();
        var pool = za.co.neroland.nerofactions.contracts.ContractFixtures.pool();
        var faction = za.co.neroland.nerofactions.contracts.ContractFixtures.FACTION;
        contracts.ensure(player, faction, 0, 123, 5, pool);
        contracts.ensure(bystander, faction, 0, 123, 5, pool);
        contracts.advance(player, faction, 0, 16);
        contracts.reserveReward(player, faction, 0);
        var reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        register((server, uuid) -> { contracts.forget(uuid); reputation.forgetPlayer(uuid); });
        ErasureConformance.create().probe("nerofactions:contracts", uuid -> contracts.knownPlayers().contains(uuid))
                .verify(null, player);
        assertTrue(contracts.entries(player).isEmpty());
        assertTrue(contracts.completed(player) == 0);
        assertFalse(contracts.rerollAvailable(player));
        assertFalse(contracts.entries(bystander).isEmpty());
    }

    @Test
    void weeklyQuotaErasureClearsOnlyTheSubject() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        JsonArray deposits = new JsonArray();
        for (UUID id : List.of(player, bystander)) {
            JsonObject deposit = new JsonObject();
            deposit.add("player", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, id).getOrThrow());
            deposit.addProperty("week", 20);
            deposit.addProperty("tonnes", 12);
            deposits.add(deposit);
        }
        JsonObject saved = new JsonObject();
        saved.add("deposits", deposits);
        var quota = QuotaState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        var reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        register((server, uuid) -> {
            quota.forget(uuid);
            reputation.forgetPlayer(uuid);
        });
        ErasureConformance.create().probe("nerofactions:miner_quota", uuid -> quota.knownPlayers().contains(uuid))
                .verify(null, player);
        assertTrue(quota.export(player).isEmpty());
        assertFalse(quota.export(bystander).isEmpty());
    }

    @Test
    void workbenchErasureClearsTheOwnerAndGuestRolesWithoutAffectingOtherBenches() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var original = new WorkbenchAccess();
        assertTrue(original.claim(first, player));
        assertTrue(original.claim(second, bystander));
        assertFalse(original.claim(first, bystander));
        assertTrue(original.setGuest(first, player, visitor, true));
        assertTrue(original.setGuest(second, bystander, player, true));
        assertTrue(original.setGuest(second, bystander, visitor, true));
        assertFalse(original.setGuest(second, player, visitor, false));
        var encoded = WorkbenchAccess.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var access = WorkbenchAccess.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(access.permits(first, player));
        assertTrue(access.permits(second, player));
        assertFalse(access.export(player).toString().contains(bystander.toString()));
        var reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        register((server, uuid) -> {
            access.forget(uuid);
            reputation.forgetPlayer(uuid);
        });
        ErasureConformance.create().probe("nerofactions:workbench_access", uuid -> access.knownPlayers().contains(uuid))
                .verify(null, player);
        assertFalse(access.permits(first, visitor));
        assertFalse(access.permits(second, player));
        assertTrue(access.permits(second, visitor));
        assertTrue(access.claim(first, bystander));
    }

    @Test
    void corporateDailyCapsSurviveReloadAndEraseOnlyTheSubject() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        var initial = new CorporateLedger();
        assertTrue(initial.reserveDelivery(player, 15));
        assertFalse(initial.reserveDelivery(player, 14));
        for (int stamp = 0; stamp < 3; stamp++) assertTrue(initial.reserveStamp(player, 15));
        assertFalse(initial.reserveStamp(player, 15));
        assertTrue(initial.reserveDelivery(bystander, 15));
        var encoded = CorporateLedger.CODEC.encodeStart(JsonOps.INSTANCE, initial).getOrThrow();
        var ledger = CorporateLedger.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertFalse(ledger.reserveStamp(player, 14));
        var reputation = new FactionReputationState();
        ReputationApi.setProvider(reputation);
        register((server, uuid) -> {
            ledger.forget(uuid);
            reputation.forgetPlayer(uuid);
        });
        ErasureConformance.create().probe("nerofactions:corporate_ledger", uuid -> ledger.knownPlayers().contains(uuid))
                .verify(null, player);
        assertTrue(ledger.export(player).isEmpty());
        assertTrue(ledger.delivered(bystander, 15));
        ledger.expire(16);
        assertTrue(ledger.reserveDelivery(bystander, 15));
        assertTrue(ledger.delivered(bystander, 16));
    }

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
