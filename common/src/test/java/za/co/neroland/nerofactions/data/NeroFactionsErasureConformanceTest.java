package za.co.neroland.nerofactions.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.content.faction.minerunion.QuotaState;
import za.co.neroland.nerofactions.content.faction.salvagers.DeconstructorBlock;
import za.co.neroland.nerofactions.content.faction.voidcult.VoidAltarBlock;
import za.co.neroland.nerofactions.contracts.ContractFixtures;
import za.co.neroland.nerofactions.link.FactionsLinkActions;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.reputation.ReputationProvider;

/**
 * POPIA/GDPR <b>erasure conformance</b> for NeroFactions, run through Core's reusable
 * {@link ErasureConformance} harness against the <b>real</b> erasure path:
 * {@link NeroFactionsData#eraseLocal(FactionsStores.Snapshot, FactionsStores.BackupHook,
 * net.minecraft.server.MinecraftServer, UUID)} — the exact code the live eraser runs, with fresh
 * store instances (no live server) and a recording backup hook in place of
 * {@code SavedDataRecovery.backupNow}. Probes are built from {@link FactionsStores} itself, so a
 * store added to the list must be seeded here or the harness's "no data before erasure" check
 * fails; {@link #everySavedDataRecoveryStoreIsInTheStoreList} fails the build if a new store is
 * <em>not</em> added to the list.
 *
 * <p>Privacy note: every test uses a random UUID and never logs it. The harness's isolation pass
 * deliberately logs one "Data eraser ... failed" warning per run — that is the assertion working.
 */
class NeroFactionsErasureConformanceTest {

    private static final Identifier GUILD = Identifier.parse("nerofactions:test_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:test_union");
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final long T0 = 1_700_000_000_000L;
    private static final long DAY_STAMP = 19_000L;

    /** Erasers this test registered, removed again so one test cannot leak into the next. */
    private final List<PlayerDataEraser> registered = new ArrayList<>();
    /** (store id, player) of every backup refresh the real path asked for. */
    private final List<Map.Entry<String, UUID>> backups = new ArrayList<>();
    /** Anything the real path threw while the harness ran it (the fan-out swallows it). */
    private final List<Throwable> thrown = new ArrayList<>();

    private ReputationProvider providerBefore;

    @BeforeEach
    void captureProvider() {
        providerBefore = ReputationApi.provider();
    }

    @AfterEach
    void restoreRegistryAndProvider() throws ReflectiveOperationException {
        registered.forEach(PlayerDataErasure::unregister);
        registered.clear();
        ReputationApi.setProvider(providerBefore);
        TerminalSessions.clearAll();
        FactionsLinkActions.clear();
        VoidAltarBlock.clear();
        deconstructorUses().clear();
    }

    // --- The real path, end to end ------------------------------------------------------------

    @Test
    void realEraseLocalClearsEveryStoreAndTransientForTheSubjectOnly() throws Exception {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        FactionsStores.Snapshot stores = seededStores(player, bystander);
        seedTransients(player);
        seedTransients(bystander);
        ReputationApi.setProvider(stores.get(FactionsStores.REPUTATION));
        registerReal(stores);

        ErasureConformance harness = ErasureConformance.create();
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            harness.probe(store.id(), uuid -> store.knownIn(stores).contains(uuid));
        }
        harness.probe("nerofactions:terminal_sessions", NeroFactionsErasureConformanceTest::hasTerminalSession)
                .probe("nerofactions:link_replay_cache", NeroFactionsErasureConformanceTest::hasReplay)
                .probe("nerofactions:deconstructor_cooldowns", NeroFactionsErasureConformanceTest::hasDeconstructorUse)
                .probe("nerofactions:void_altar_boons", NeroFactionsErasureConformanceTest::hasBoon);
        assertEquals(4, FactionsStores.TRANSIENT.size(),
                "a new transient state needs a probe above");

        ErasureConformance.Report report = harness.run(null, player);
        assertTrue(report.passed(), report.describe());
        assertFalse(report.describe().contains(player.toString()),
                "the report must never carry the player UUID (it is personal data)");
        assertTrue(thrown.isEmpty(), "the real erasure path threw: " + thrown);

        // One backup refresh per persistent store for the subject, no more, no less.
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            assertEquals(1, backups.stream().filter(b -> b.getKey().equals(store.id())
                    && b.getValue().equals(player)).count(), "backup refresh count for " + store.id());
        }

        // A second player's data must survive the request untouched — erasure is per-subject.
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            assertTrue(store.knownIn(stores).contains(bystander), "the bystander's row must survive in " + store.id());
        }
        assertTrue(hasTerminalSession(bystander));
        assertTrue(hasReplay(bystander));
        assertTrue(hasDeconstructorUse(bystander));
        assertSame(stores.get(FactionsStores.REPUTATION), ReputationApi.provider(),
                "the recording wrapper must be removed");
    }

    @Test
    void aFailingBackupDoesNotStopLaterStoresAndStillPropagates() throws Exception {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        FactionsStores.Snapshot stores = seededStores(player, bystander);
        seedTransients(player);
        FactionsStores.Persistent<?> second = FactionsStores.PERSISTENT.get(1);
        FactionsStores.Persistent<?> fourth = FactionsStores.PERSISTENT.get(3);
        IllegalStateException firstFailure = new IllegalStateException("backup #2 failed");
        IllegalStateException laterFailure = new IllegalStateException("backup #4 failed");
        List<String> refreshed = new ArrayList<>();

        IllegalStateException propagated = assertThrows(IllegalStateException.class, () ->
                NeroFactionsData.eraseLocal(stores, (store, uuid) -> {
                    refreshed.add(store.id());
                    if (store == second) throw firstFailure;
                    if (store == fourth) throw laterFailure;
                }, null, player));

        assertSame(firstFailure, propagated, "the first failure is the one rethrown");
        assertEquals(List.of(laterFailure), List.of(propagated.getSuppressed()),
                "later failures ride along as suppressed");
        assertEquals(FactionsStores.PERSISTENT.size(), refreshed.size(),
                "every store after the failing one was still processed");
        for (FactionsStores.Persistent<?> store : FactionsStores.PERSISTENT) {
            assertFalse(store.knownIn(stores).contains(player), store.id() + " must still be erased");
            assertTrue(store.knownIn(stores).contains(bystander));
        }
        assertFalse(hasTerminalSession(player), "transient state after a failure must be cleared too");
        assertFalse(hasReplay(player));
        assertFalse(hasDeconstructorUse(player));
    }

    @Test
    void aCleanErasureDoesNotThrowAndRefreshesEveryBackupOnce() throws Exception {
        UUID player = UUID.randomUUID();
        FactionsStores.Snapshot stores = seededStores(player, UUID.randomUUID());
        List<String> refreshed = new ArrayList<>();

        assertDoesNotThrow(() -> NeroFactionsData.eraseLocal(stores,
                (store, uuid) -> refreshed.add(store.id()), null, player));

        List<String> expected = new ArrayList<>();
        FactionsStores.PERSISTENT.forEach(store -> expected.add(store.id()));
        assertEquals(expected, refreshed);
    }

    // --- Completeness: a store outside the list fails the build ----------------------------------

    @Test
    void everySavedDataRecoveryStoreIsInTheStoreList() throws IOException {
        Path sources = repoRoot().resolve("common/src/main/java");
        Set<String> users = savedDataRecoveryUsers(sources);
        assertTrue(users.size() >= FactionsStores.PERSISTENT.size(),
                "the source scan found too few stores to be meaningful: " + users);
        Set<String> missing = new TreeSet<>(users);
        FactionsStores.persistentTypes().forEach(type -> missing.remove(type.getName()));
        assertTrue(missing.isEmpty(), "SavedData stores not registered in FactionsStores "
                + "(erasure, export and retention sweep would all miss them): " + missing);
    }

    @Test
    void theSourceScanFlagsAnUnregisteredStore() throws IOException {
        Path root = Files.createTempDirectory("nf-stores");
        Path pkg = Files.createDirectories(root.resolve("za/co/neroland/nerofactions/rogue"));
        Files.writeString(pkg.resolve("RogueState.java"), "package za.co.neroland.nerofactions.rogue;\n"
                + "class RogueState { Object get() { return SavedDataRecovery.get(null, null, null, \"x\"); } }\n");
        Files.writeString(pkg.resolve("Bystander.java"), "package za.co.neroland.nerofactions.rogue;\nclass Bystander {}\n");

        assertEquals(Set.of("za.co.neroland.nerofactions.rogue.RogueState"), savedDataRecoveryUsers(root));
    }

    /** Fully-qualified names of every class under {@code root} that loads through the recovery guard. */
    private static Set<String> savedDataRecoveryUsers(Path root) throws IOException {
        Set<String> users = new HashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("SavedDataRecovery.get(")) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    users.add(relative.substring(0, relative.length() - ".java".length()).replace('/', '.'));
                }
            }
        }
        return users;
    }

    private static Path repoRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("stonecutter.gradle"))) {
            root = root.getParent();
        }
        assertNotNull(root);
        return root;
    }

    // --- Store-specific semantics, erased through the real path ---------------------------------

    @Test
    void outpostVisitsRoundTripAndErasureLeavesOnlyTheOtherPlayer() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        var original = new OutpostVisits();
        assertTrue(original.record(player, OutpostFaction.SPACE_GUILD, OVERWORLD));
        assertFalse(original.record(player, OutpostFaction.SPACE_GUILD, OVERWORLD));
        assertFalse(original.record(player, OutpostFaction.NONE, OVERWORLD));
        assertTrue(original.record(bystander, OutpostFaction.SALVAGERS, OVERWORLD));
        var encoded = OutpostVisits.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var visits = OutpostVisits.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        verifySingleStore(FactionsStores.fresh().with(FactionsStores.OUTPOST_VISITS, visits),
                FactionsStores.OUTPOST_VISITS, uuid -> visits.knownPlayers().contains(uuid), player);
        assertTrue(visits.export(player).isEmpty());
        assertFalse(visits.export(bystander).isEmpty());
    }

    @Test
    void contractAssignmentsProgressTokensAndCountAreErasedForOnlyTheSubject() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        var contracts = new ContractState();
        contracts.ensure(player, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        contracts.ensure(bystander, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
        contracts.advance(player, ContractFixtures.FACTION, 0, 16);
        contracts.reserveReward(player, ContractFixtures.FACTION, 0);
        verifySingleStore(FactionsStores.fresh().with(FactionsStores.CONTRACTS, contracts),
                FactionsStores.CONTRACTS, uuid -> contracts.knownPlayers().contains(uuid), player);
        assertTrue(contracts.entries(player).isEmpty());
        assertEquals(0, contracts.completed(player));
        assertFalse(contracts.rerollAvailable(player));
        assertFalse(contracts.entries(bystander).isEmpty());
    }

    @Test
    void weeklyQuotaErasureClearsOnlyTheSubject() {
        UUID player = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        var quota = quotaFor(player, bystander);
        verifySingleStore(FactionsStores.fresh().with(FactionsStores.MINER_QUOTA, quota),
                FactionsStores.MINER_QUOTA, uuid -> quota.knownPlayers().contains(uuid), player);
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
        verifySingleStore(FactionsStores.fresh().with(FactionsStores.WORKBENCH_ACCESS, access),
                FactionsStores.WORKBENCH_ACCESS, uuid -> access.knownPlayers().contains(uuid), player);
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
        verifySingleStore(FactionsStores.fresh().with(FactionsStores.CORPORATE_LEDGER, ledger),
                FactionsStores.CORPORATE_LEDGER, uuid -> ledger.knownPlayers().contains(uuid), player);
        assertTrue(ledger.export(player).isEmpty());
        assertTrue(ledger.delivered(bystander, 15));
        ledger.expire(16);
        assertTrue(ledger.reserveDelivery(bystander, 15));
        assertTrue(ledger.delivered(bystander, 16));
    }

    // --- Helpers ----------------------------------------------------------------------------------

    /** Registers the REAL erasure path over {@code stores}, recording backups and anything thrown. */
    private void registerReal(FactionsStores.Snapshot stores) {
        PlayerDataEraser eraser = (server, uuid) -> {
            try {
                NeroFactionsData.eraseLocal(stores,
                        (store, subject) -> backups.add(Map.entry(store.id(), subject)), server, uuid);
            } catch (RuntimeException | Error t) {
                thrown.add(t);
                throw t;
            }
        };
        registered.add(eraser);
        PlayerDataErasure.register(eraser);
    }

    /** Runs the harness on one seeded store, erased through the real path. */
    private void verifySingleStore(FactionsStores.Snapshot stores, FactionsStores.Persistent<?> store,
            Predicate<UUID> retains, UUID player) {
        ReputationApi.setProvider(stores.get(FactionsStores.REPUTATION));
        registerReal(stores);
        ErasureConformance.create().probe(store.id(), retains).verify(null, player);
        assertTrue(thrown.isEmpty(), "the real erasure path threw: " + thrown);
    }

    /** Every persistent store seeded for both players. */
    private static FactionsStores.Snapshot seededStores(UUID player, UUID bystander) {
        FactionsStores.Snapshot stores = FactionsStores.fresh()
                .with(FactionsStores.MINER_QUOTA, quotaFor(player, bystander));
        UUID benchOfPlayer = UUID.randomUUID();
        UUID benchOfBystander = UUID.randomUUID();
        for (UUID id : List.of(player, bystander)) {
            FactionReputationState reputation = stores.get(FactionsStores.REPUTATION);
            reputation.setReputation(id, GUILD, 120);
            reputation.setReputation(id, UNION, -40);
            FactionMembershipState membership = stores.get(FactionsStores.MEMBERSHIP);
            membership.recordJoin(id, GUILD, T0);
            membership.recordJoin(id, UNION, T0);
            // Leaving UNION populates the decay bookmark AND arms the join cooldown.
            membership.recordLeave(id, UNION, T0 + 1L, T0 + 3_600_000L);
            membership.addAccrued(id, GUILD, "quest", DAY_STAMP, 120);
            membership.raiseRewardWatermark(id, GUILD, 3);
            stores.get(FactionsStores.CONTRACTS).ensure(id, ContractFixtures.FACTION, 0, 123, 5, ContractFixtures.pool());
            stores.get(FactionsStores.OUTPOST_VISITS).record(id, OutpostFaction.SPACE_GUILD, OVERWORLD);
            stores.get(FactionsStores.CORPORATE_LEDGER).reserveDelivery(id, 15);
            stores.get(FactionsStores.WORKBENCH_ACCESS).claim(id.equals(player) ? benchOfPlayer : benchOfBystander, id);
        }
        return stores;
    }

    private static QuotaState quotaFor(UUID... players) {
        JsonArray deposits = new JsonArray();
        for (UUID id : players) {
            JsonObject deposit = new JsonObject();
            deposit.add("player", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, id).getOrThrow());
            deposit.addProperty("week", 20);
            deposit.addProperty("tonnes", 12);
            deposits.add(deposit);
        }
        JsonObject saved = new JsonObject();
        saved.add("deposits", deposits);
        return QuotaState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
    }

    /** Seeds every transient state that can be seeded without a live server. */
    private static void seedTransients(UUID player) throws Exception {
        TerminalSessions.current(player, List.of(GUILD), GUILD);
        TerminalSessions.rememberTerminal(player, OVERWORLD, 7L, OutpostFaction.SPACE_GUILD, GUILD);
        Object cache = replayCache();
        Method tryRequest = cache.getClass().getDeclaredMethod("tryRequest", UUID.class, int.class);
        tryRequest.setAccessible(true);
        tryRequest.invoke(cache, player, 0);
        Class<?> requestType = Class.forName(FactionsLinkActions.class.getName() + "$Request");
        Constructor<?> request = requestType.getDeclaredConstructor(Identifier.class, int.class, UUID.class);
        request.setAccessible(true);
        Method remember = cache.getClass().getDeclaredMethod("remember", UUID.class, long.class, requestType,
                JsonObject.class);
        remember.setAccessible(true);
        remember.invoke(cache, player, 1L, request.newInstance(GUILD, 0, UUID.randomUUID()), new JsonObject());
        deconstructorUses().put(player, 100L);
        seedBoon(player);
    }

    @SuppressWarnings("unchecked")
    private static void seedBoon(UUID player) throws ReflectiveOperationException {
        Class<?> boonType = Class.forName(VoidAltarBlock.class.getName() + "$Boon");
        Constructor<?> boon = boonType.getDeclaredConstructors()[0];
        boon.setAccessible(true);
        Field boons = VoidAltarBlock.class.getDeclaredField("BOONS");
        boons.setAccessible(true);
        ((Map<UUID, Object>) boons.get(null)).put(player, boon.newInstance(1_000, null));
    }

    private static boolean hasTerminalSession(UUID player) {
        return TerminalSessions.selected(player) != null || TerminalSessions.lastTerminal(player) != null;
    }

    private static boolean hasReplay(UUID player) {
        try {
            Object cache = replayCache();
            return privateMap(cache, "replays").containsKey(player)
                    || privateMap(cache, "lastRequests").containsKey(player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean hasDeconstructorUse(UUID player) {
        try {
            return deconstructorUses().containsKey(player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean hasBoon(UUID player) {
        try {
            Field boons = VoidAltarBlock.class.getDeclaredField("BOONS");
            boons.setAccessible(true);
            return ((Map<?, ?>) boons.get(null)).containsKey(player);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object replayCache() throws ReflectiveOperationException {
        Field field = FactionsLinkActions.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        return field.get(null);
    }

    private static Map<?, ?> privateMap(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(owner);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Long> deconstructorUses() throws ReflectiveOperationException {
        Field field = DeconstructorBlock.class.getDeclaredField("LAST_USE");
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(null);
    }
}
