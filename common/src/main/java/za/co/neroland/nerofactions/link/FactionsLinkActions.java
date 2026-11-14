package za.co.neroland.nerofactions.link;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.contracts.ContractService;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkActionResult.Error;

/**
 * The write half of the link module: one action, {@code reroll_contract}, which spends the
 * requester's own daily contract reroll on one of their own slots.
 *
 * <p>Gate order, each step refusing before the next runs: the three kill switches
 * ({@code linkModuleEnabled}, {@code linkRerollEnabled}, {@code contractsEnabled}); a bound server
 * on its own thread; the action id; strict parameter parsing; the per-player rate limit
 * (<b>before any state is read</b>, so a refused call never marks a store dirty; a replay of the
 * remembered request (same faction, slot and request ID) is exempt so an idempotent retry is never throttled); the
 * player being online; and only then the contract state itself.
 *
 * <p><b>Replays are memory-only.</b> The replay cache is not persisted, so after a restart a
 * replay of a request that already spent the day's reroll is answered "No daily contract reroll
 * remains" rather than with its original result. The reroll itself was durable, so a client
 * recovers by reading the {@code contracts} section. Nothing durable records which slot was
 * rerolled or by which request, so answering {@code ok} there could not be told apart from a new
 * request trying a second reroll, and refusing is the safe side.
 *
 * <p>Server thread only. Nothing here logs a player identifier.
 */
public final class FactionsLinkActions implements LinkActionHandler {
    private static final String REROLL = "reroll_contract";
    private static final ReplayCache CACHE = new ReplayCache();
    private static final int LIMIT = 4096;

    private final Host host;

    /** Production: config switches and the bound server, resolved per call. */
    public FactionsLinkActions() {
        this(PRODUCTION);
    }

    /** Test seam: the switches and the game state are injectable so plain-JVM tests can drive execute. */
    FactionsLinkActions(Host host) {
        this.host = host;
    }

    @Override
    public String moduleId() {
        return FactionsLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return List.of(REROLL);
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (!host.linkModuleEnabled() || !host.linkRerollEnabled() || !host.contractsEnabled()) {
            return LinkActionResult.error(Error.ACTION_DISABLED, "Remote contract rerolls are disabled.");
        }
        Game game = host.open();
        if (game == null) {
            return LinkActionResult.error(Error.INTERNAL, "Faction server is unavailable.");
        }
        if (!REROLL.equals(actionId) || playerId == null) {
            return LinkActionResult.error(Error.VALIDATION, "Unknown faction action.");
        }
        Request request = Request.parse(params);
        if (request == null) {
            return LinkActionResult.error(Error.VALIDATION, "Expected a faction ID, slot 0–2 and request UUID.");
        }
        if (!CACHE.isRemembered(playerId, request) && !CACHE.tryRequest(playerId, game.tick())) {
            return LinkActionResult.error(Error.VALIDATION, "Wait before requesting another reroll.");
        }
        if (!game.online(playerId)) {
            return LinkActionResult.error(Error.PLAYER_OFFLINE_REQUIRED, "Join the server to reroll a contract.");
        }
        game.refresh(playerId);
        long day = game.contractDay(playerId);
        if (day == Long.MIN_VALUE) {
            return LinkActionResult.error(Error.VALIDATION, "No active faction contracts.");
        }

        Replay previous = CACHE.get(playerId, day, request.id());
        if (previous != null) {
            return previous.request().equals(request)
                    ? LinkActionResult.ok(previous.snapshot().deepCopy())
                    : LinkActionResult.error(Error.VALIDATION, "That request ID was already used.");
        }
        if (!game.rerollAvailable(playerId)) {
            return LinkActionResult.error(Error.VALIDATION, "No daily contract reroll remains.");
        }
        if (!game.reroll(playerId, request.faction(), request.slot())) {
            return LinkActionResult.error(Error.VALIDATION, "This contract cannot be rerolled.");
        }
        JsonObject result = game.contracts(playerId);
        CACHE.remember(playerId, day, request, result);
        return LinkActionResult.ok(result);
    }

    public static void forget(UUID player) {
        CACHE.replays.remove(player);
        CACHE.lastRequests.remove(player);
    }

    public static void clear() {
        CACHE.replays.clear();
        CACHE.lastRequests.clear();
    }

    record Request(Identifier faction, int slot, UUID id) {
        static Request parse(JsonObject params) {
            if (params == null || params.size() != 3 || !(params.get("factionId") instanceof JsonPrimitive faction)
                    || !(params.get("slot") instanceof JsonPrimitive slot)
                    || !(params.get("requestId") instanceof JsonPrimitive id)
                    || !faction.isString() || !slot.isNumber() || !id.isString()
                    || faction.getAsString().length() > 192 || id.getAsString().length() != 36) {
                return null;
            }
            Identifier identifier = Identifier.tryParse(faction.getAsString());
            if (identifier == null || !identifier.toString().equals(faction.getAsString())) {
                return null;
            }
            try {
                int index = slot.getAsBigDecimal().intValueExact();
                UUID uuid = UUID.fromString(id.getAsString());
                return index >= 0 && index < 3 && uuid.toString().equalsIgnoreCase(id.getAsString())
                        ? new Request(identifier, index, uuid) : null;
            } catch (ArithmeticException | IllegalArgumentException malformed) {
                return null;
            }
        }
    }

    record Replay(long day, Request request, JsonObject snapshot) { }

    static final class ReplayCache {
        private final Map<UUID, Replay> replays = new LinkedHashMap<>();
        private final Map<UUID, Integer> lastRequests = new LinkedHashMap<>();

        /** Whether this exact request is the player's remembered one (any day): an idempotent retry. */
        boolean isRemembered(UUID player, Request request) {
            Replay replay = replays.get(player);
            return replay != null && replay.request().equals(request);
        }

        Replay get(UUID player, long day, UUID request) {
            Replay replay = replays.get(player);
            return replay != null && replay.day() == day && replay.request().id().equals(request) ? replay : null;
        }

        void remember(UUID player, long day, Request request, JsonObject snapshot) {
            replays.put(player, new Replay(day, request, snapshot.deepCopy()));
            if (replays.size() > LIMIT) replays.remove(replays.keySet().iterator().next());
        }

        boolean tryRequest(UUID player, int tick) {
            Integer previous = lastRequests.get(player);
            if (previous != null && Integer.toUnsignedLong(tick - previous) < 20L) {
                return false;
            }
            lastRequests.put(player, tick);
            if (lastRequests.size() > LIMIT) lastRequests.remove(lastRequests.keySet().iterator().next());
            return true;
        }
    }

    // --- the server seam --------------------------------------------------------

    /**
     * Where one action call's switches and state come from. Package-private so tests can drive
     * {@link #execute} without a live server; {@link #PRODUCTION} is the only other implementation.
     */
    interface Host {
        boolean linkModuleEnabled();

        boolean linkRerollEnabled();

        boolean contractsEnabled();

        /** The game state for this call, or {@code null} when no server is bound or off-thread. */
        @Nullable
        Game open();
    }

    /** Everything one action call may read or change, keyed by the requesting player. */
    interface Game {
        /** The server tick counter, for the rate limit. Not player state. */
        int tick();

        boolean online(UUID player);

        /** Bring the player's contracts up to today (production: {@code ContractService.current}). */
        void refresh(UUID player);

        long contractDay(UUID player);

        boolean rerollAvailable(UUID player);

        /** Spend the daily reroll on one of the player's own slots; {@code false} when refused. */
        boolean reroll(UUID player, Identifier faction, int slot);

        /** The player's {@code contracts} snapshot after a successful reroll. */
        JsonObject contracts(UUID player);
    }

    private static final Host PRODUCTION = new Host() {
        @Override public boolean linkModuleEnabled() { return NeroFactionsConfig.LINK_MODULE_ENABLED.get(); }
        @Override public boolean linkRerollEnabled() { return NeroFactionsConfig.LINK_REROLL_ENABLED.get(); }
        @Override public boolean contractsEnabled() { return NeroFactionsConfig.CONTRACTS_ENABLED.get(); }

        @Override
        public Game open() {
            MinecraftServer server = FactionsLinkAccess.server();
            if (server == null || !server.isSameThread()) {
                return null;
            }
            return new Game() {
                @Override public int tick() { return server.getTickCount(); }
                @Override public boolean online(UUID player) { return FactionsLinkAccess.isOnline(server, player); }

                @Override
                public void refresh(UUID player) {
                    ServerPlayer online = server.getPlayerList().getPlayer(player);
                    if (online != null) {
                        ContractService.current(online);
                    }
                }

                @Override public long contractDay(UUID player) { return ContractState.get(server).day(player); }
                @Override public boolean rerollAvailable(UUID player) { return ContractState.get(server).rerollAvailable(player); }

                @Override
                public boolean reroll(UUID player, Identifier faction, int slot) {
                    ServerPlayer online = server.getPlayerList().getPlayer(player);
                    return online != null && ContractService.reroll(online, faction, slot);
                }

                @Override
                public JsonObject contracts(UUID player) {
                    return new FactionsLinkSnapshots().snapshot(player, FactionsLinkModule.SECTION_CONTRACTS, Map.of());
                }
            };
        }
    };
}
