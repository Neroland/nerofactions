package za.co.neroland.nerofactions.link;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.contracts.ContractService;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkActionResult.Error;

public final class FactionsLinkActions implements LinkActionHandler {
    private static final String REROLL = "reroll_contract";
    private static final ReplayCache CACHE = new ReplayCache();
    private static final int LIMIT = 4096;

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
        if (!NeroFactionsConfig.LINK_MODULE_ENABLED.get() || !NeroFactionsConfig.LINK_REROLL_ENABLED.get()
                || !NeroFactionsConfig.CONTRACTS_ENABLED.get()) {
            return LinkActionResult.error(Error.ACTION_DISABLED, "Remote contract rerolls are disabled.");
        }
        var server = FactionsLinkAccess.server();
        if (server == null || !server.isSameThread()) {
            return LinkActionResult.error(Error.INTERNAL, "Faction server is unavailable.");
        }
        if (!REROLL.equals(actionId) || playerId == null) {
            return LinkActionResult.error(Error.VALIDATION, "Unknown faction action.");
        }
        Request request = Request.parse(params);
        if (request == null) {
            return LinkActionResult.error(Error.VALIDATION, "Expected a faction ID, slot 0–2 and request UUID.");
        }
        var player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return LinkActionResult.error(Error.PLAYER_OFFLINE_REQUIRED, "Join the server to reroll a contract.");
        }
        ContractService.current(player);
        var state = ContractState.get(server);
        long day = state.day(playerId);
        if (day == Long.MIN_VALUE) {
            return LinkActionResult.error(Error.VALIDATION, "No active faction contracts.");
        }

        Replay previous = CACHE.get(playerId, day, request.id());
        if (previous != null) {
            return previous.request().equals(request)
                    ? LinkActionResult.ok(previous.snapshot().deepCopy())
                    : LinkActionResult.error(Error.VALIDATION, "That request ID was already used.");
        }
        if (!state.rerollAvailable(playerId)) {
            return LinkActionResult.error(Error.VALIDATION, "No daily contract reroll remains.");
        }
        if (!CACHE.tryRequest(playerId, server.getTickCount())) {
            return LinkActionResult.error(Error.VALIDATION, "Wait before requesting another reroll.");
        }
        if (!ContractService.reroll(player, request.faction(), request.slot())) {
            return LinkActionResult.error(Error.VALIDATION, "This contract cannot be rerolled.");
        }
        JsonObject result = new FactionsLinkSnapshots().snapshot(playerId, FactionsLinkModule.SECTION_CONTRACTS, Map.of());
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
}
