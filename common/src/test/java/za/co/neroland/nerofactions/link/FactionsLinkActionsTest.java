package za.co.neroland.nerofactions.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.contracts.ContractDefinition;
import za.co.neroland.nerofactions.contracts.ContractFixtures;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerofactions.link.FactionsLinkActions.ReplayCache;
import za.co.neroland.nerofactions.link.FactionsLinkActions.Request;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkActionResult.Error;

/**
 * The reroll action's gates, driven through {@link FactionsLinkActions#execute} with the
 * {@link FactionsLinkActions.Host} seam filled by direct stores ({@link LinkFixture}). The fake
 * {@code reroll} mirrors {@code ContractService.reroll}'s authorisation (member of the faction,
 * own slot, unclaimed, no progress, reroll unspent) over the real {@link ContractState}.
 */
class FactionsLinkActionsTest {

    private static final Identifier UNION = ContractFixtures.FACTION;
    private static final Identifier GUILD = Identifier.parse("nerofactions:space_guild");

    private LinkFixture fixture;
    private FakeHost host;
    private FactionsLinkActions actions;
    private UUID requester;
    private UUID bystander;

    @BeforeEach
    void setUp() {
        FactionsLinkActions.clear();
        fixture = new LinkFixture(LinkFixture.faction(UNION.getPath(), "Miner Union"),
                LinkFixture.faction(GUILD.getPath(), "Space Guild"));
        requester = UUID.randomUUID();
        bystander = UUID.randomUUID();
        fixture.membership.recordJoin(requester, UNION, 1L);
        fixture.membership.recordJoin(bystander, UNION, 1L);
        fixture.membership.recordJoin(bystander, GUILD, 1L);
        fixture.contractState.ensure(requester, UNION, 0, 4, 5, ContractFixtures.pool());
        fixture.contractState.ensure(bystander, UNION, 0, 4, 5, ContractFixtures.pool());
        fixture.online.add(requester);
        fixture.online.add(bystander);
        host = new FakeHost(new FakeGame(fixture));
        actions = new FactionsLinkActions(host);
    }

    // --- golden -------------------------------------------------------------------

    @Test
    void requesterRerollsSlotZero() {
        Identifier before = slot(requester, 0);

        LinkActionResult result = actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID()));

        assertTrue(result.ok(), result.message());
        assertNotEquals(before, slot(requester, 0), "slot 0 now holds a different contract");
        assertFalse(fixture.contractState.rerollAvailable(requester));
        assertFalse(result.state().get("reroll_available").getAsBoolean());
        String rerolled = slot(requester, 0).toString();
        assertTrue(result.state().getAsJsonArray("contracts").asList().stream()
                .map(row -> row.getAsJsonObject())
                .anyMatch(row -> row.get("slot").getAsInt() == 0 && row.get("contract").getAsString().equals(rerolled)),
                "the returned snapshot shows the rerolled contract");
    }

    @Test
    void replayWithTheSameRequestIdReturnsTheSameResult() {
        JsonObject body = parameters(UUID.randomUUID());
        LinkActionResult first = actions.execute(requester, "reroll_contract", body);
        Identifier afterFirst = slot(requester, 0);

        LinkActionResult replay = actions.execute(requester, "reroll_contract", body.deepCopy());

        assertTrue(first.ok());
        assertTrue(replay.ok(), "an idempotent retry is answered, not throttled: " + replay.message());
        assertEquals(first.state(), replay.state());
        assertEquals(afterFirst, slot(requester, 0), "a replay never rerolls again");
    }

    // --- failures -----------------------------------------------------------------

    @Test
    void aNewRequestIdTheSameDayIsRefused() {
        assertTrue(actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID())).ok());
        Identifier afterFirst = slot(requester, 0);
        host.game.tick += 40; // past the rate-limit window

        LinkActionResult second = actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID()));

        assertEquals(Error.VALIDATION, second.error());
        assertEquals("No daily contract reroll remains.", second.message());
        assertEquals(afterFirst, slot(requester, 0));
    }

    @Test
    void eachKillSwitchDisablesTheAction() {
        List<Consumer<FakeHost>> switches = List.of(
                h -> h.linkModule = false, h -> h.linkReroll = false, h -> h.contracts = false);
        for (Consumer<FakeHost> off : switches) {
            FakeHost disabled = new FakeHost(new FakeGame(fixture));
            off.accept(disabled);
            LinkActionResult result = new FactionsLinkActions(disabled)
                    .execute(requester, "reroll_contract", parameters(UUID.randomUUID()));
            assertEquals(Error.ACTION_DISABLED, result.error());
            assertEquals(0, disabled.game.stateReads, "a disabled action reads nothing");
        }
        assertTrue(fixture.contractState.rerollAvailable(requester));
    }

    @Test
    void offlineRequesterIsRefused() {
        fixture.online.remove(requester);

        LinkActionResult result = actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID()));

        assertEquals(Error.PLAYER_OFFLINE_REQUIRED, result.error());
        assertTrue(fixture.contractState.rerollAvailable(requester));
    }

    @Test
    void aFactionTheRequesterDoesNotBelongToIsRefused() {
        JsonObject body = parameters(UUID.randomUUID());
        body.addProperty("factionId", GUILD.toString()); // the bystander's faction, not theirs

        LinkActionResult result = actions.execute(requester, "reroll_contract", body);

        assertEquals(Error.VALIDATION, result.error());
        assertTrue(fixture.contractState.rerollAvailable(requester));
    }

    @Test
    void aSlotWithProgressIsRefused() {
        fixture.contractState.advance(requester, UNION, 0, 1);
        Identifier before = slot(requester, 0);

        LinkActionResult result = actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID()));

        assertEquals(Error.VALIDATION, result.error());
        assertEquals(before, slot(requester, 0));
        assertTrue(fixture.contractState.rerollAvailable(requester));
    }

    @Test
    void theBystandersStateNeverChanges() {
        List<ContractState.Entry> before = List.copyOf(fixture.contractState.entries(bystander));

        assertTrue(actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID())).ok());

        assertEquals(before, fixture.contractState.entries(bystander));
        assertTrue(fixture.contractState.rerollAvailable(bystander));
    }

    @Test
    void rateLimitRefusesBeforeAnyStateRead() {
        LinkActionResult first = actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID()));
        assertTrue(first.ok(), "golden: the first call is allowed");
        int readsAfterFirst = host.game.stateReads;
        host.game.tick += 5; // inside the 20-tick window

        LinkActionResult second = actions.execute(requester, "reroll_contract", parameters(UUID.randomUUID()));

        assertEquals(Error.VALIDATION, second.error());
        assertEquals("Wait before requesting another reroll.", second.message());
        assertEquals(readsAfterFirst, host.game.stateReads, "refused before any state read");
    }

    // --- parsing and the cache (pure) ---------------------------------------------

    @Test
    void onlyCanonicalFactionSlotAndRequestParametersAreAccepted() {
        JsonObject body = parameters(UUID.randomUUID());
        assertNotNull(Request.parse(body));
        body.addProperty("slot", 1.5);
        assertNull(Request.parse(body));
        body.addProperty("slot", 3);
        assertNull(Request.parse(body));
        body.addProperty("slot", 2);
        body.addProperty("player", UUID.randomUUID().toString());
        assertNull(Request.parse(body));
        body.remove("player");
        body.addProperty("factionId", "miner_union");
        assertNull(Request.parse(body));
    }

    @Test
    void rerollReplayIsPlayerAndDayScopedAndKeepsAnIndependentSnapshot() {
        UUID player = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Request request = Request.parse(parameters(requestId));
        ReplayCache cache = new ReplayCache();
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("reroll_available", false);
        cache.remember(player, 5, request, snapshot);
        snapshot.addProperty("reroll_available", true);
        assertFalse(cache.get(player, 5, requestId).snapshot().get("reroll_available").getAsBoolean());
        assertEquals(request, cache.get(player, 5, requestId).request());
        assertNull(cache.get(player, 6, requestId));
        assertNull(cache.get(UUID.randomUUID(), 5, requestId));
        assertTrue(cache.isRemembered(player, request));
        assertFalse(cache.isRemembered(player, Request.parse(parameters(UUID.randomUUID()))));
        assertTrue(cache.tryRequest(player, 15));
        assertFalse(cache.tryRequest(player, 34));
        assertTrue(cache.tryRequest(player, 35));
    }

    // --- helpers ------------------------------------------------------------------

    private Identifier slot(UUID player, int slot) {
        return fixture.contractState.entry(player, UNION, slot).contract().id();
    }

    private static JsonObject parameters(UUID id) {
        JsonObject result = new JsonObject();
        result.addProperty("factionId", UNION.toString());
        result.addProperty("slot", 0);
        result.addProperty("requestId", id.toString());
        return result;
    }

    private static final class FakeHost implements FactionsLinkActions.Host {
        final FakeGame game;
        boolean linkModule = true;
        boolean linkReroll = true;
        boolean contracts = true;

        FakeHost(FakeGame game) {
            this.game = game;
        }

        @Override public boolean linkModuleEnabled() { return linkModule; }
        @Override public boolean linkRerollEnabled() { return linkReroll; }
        @Override public boolean contractsEnabled() { return contracts; }
        @Override public FactionsLinkActions.Game open() { return game; }
    }

    /** Counts every player-state read or write; {@link #tick()} is not player state. */
    private static final class FakeGame implements FactionsLinkActions.Game {
        final LinkFixture fixture;
        int tick;
        int stateReads;

        FakeGame(LinkFixture fixture) {
            this.fixture = fixture;
        }

        @Override public int tick() { return tick; }

        @Override
        public boolean online(UUID player) {
            stateReads++;
            return fixture.online.contains(player);
        }

        @Override
        public void refresh(UUID player) {
            stateReads++;
        }

        @Override
        public long contractDay(UUID player) {
            stateReads++;
            return fixture.contractState.day(player);
        }

        @Override
        public boolean rerollAvailable(UUID player) {
            stateReads++;
            return fixture.contractState.rerollAvailable(player);
        }

        @Override
        public boolean reroll(UUID player, Identifier faction, int slot) {
            stateReads++;
            if (!fixture.membership.membershipsOf(player).contains(faction)) {
                return false;
            }
            Set<Identifier> used = fixture.contractState.entries(player).stream()
                    .map(entry -> entry.contract().id()).collect(Collectors.toSet());
            ContractDefinition replacement = ContractFixtures.pool().stream()
                    .filter(contract -> contract.faction().equals(faction) && !used.contains(contract.id()))
                    .findFirst().orElse(null);
            return fixture.contractState.reroll(player, faction, slot, replacement);
        }

        @Override
        public JsonObject contracts(UUID player) {
            stateReads++;
            return fixture.snapshots().snapshot(player, FactionsLinkModule.SECTION_CONTRACTS, Map.of());
        }
    }
}
