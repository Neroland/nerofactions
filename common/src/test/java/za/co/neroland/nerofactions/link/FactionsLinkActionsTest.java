package za.co.neroland.nerofactions.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.link.FactionsLinkActions.ReplayCache;
import za.co.neroland.nerofactions.link.FactionsLinkActions.Request;

class FactionsLinkActionsTest {
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
        assertTrue(cache.tryRequest(player, 15));
        assertFalse(cache.tryRequest(player, 34));
        assertTrue(cache.tryRequest(player, 35));
    }

    private static JsonObject parameters(UUID id) {
        JsonObject result = new JsonObject();
        result.addProperty("factionId", "nerofactions:miner_union");
        result.addProperty("slot", 0);
        result.addProperty("requestId", id.toString());
        return result;
    }
}
