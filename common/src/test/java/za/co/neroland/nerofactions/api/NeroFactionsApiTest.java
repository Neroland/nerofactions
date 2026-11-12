package za.co.neroland.nerofactions.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;

class NeroFactionsApiTest {
    @Test
    void storeBackedReadsAreScopedAndDoNotRequireAnOnlinePlayer() {
        Identifier a = Identifier.parse("nerofactions:a");
        Identifier b = Identifier.parse("nerofactions:b");
        Identifier absent = Identifier.parse("nerofactions:absent");
        UUID offline = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var state = new za.co.neroland.nerofactions.data.FactionReputationState();
        state.setReputation(offline, a, 400);
        state.setReputation(other, a, -101);
        var ladder = Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500);
        var definitions = Map.of(a, new FactionDefinition(a, "A", "A theme", ladder, Map.of(), List.of(b), List.of(), Optional.empty()),
                b, new FactionDefinition(b, "B", "B theme", ladder, Map.of(), List.of(), List.of(), Optional.empty()));
        var mutableMemberships = new java.util.LinkedHashSet<>(List.of(a));
        int[] awards = {0};
        NeroFactionsApi.Access access = new NeroFactionsApi.Access() {
            public Map<Identifier, FactionDefinition> factions() { return definitions; }
            public java.util.Set<Identifier> membership(UUID player) {
                return player.equals(offline) ? mutableMemberships : java.util.Set.of();
            }
            public int standing(UUID player, Identifier faction) { return state.getReputation(player, faction); }
            public int awardEvent(UUID player, Identifier faction, int amount) {
                assertEquals(offline, player);
                assertEquals(a, faction);
                assertEquals(25, amount);
                awards[0]++;
                return 7;
            }
        };
        assertEquals(2, NeroFactionsApi.factionViews(access).size());
        assertThrows(UnsupportedOperationException.class, () -> NeroFactionsApi.factionViews(access).clear());
        var memberships = NeroFactionsApi.membershipView(access, offline);
        mutableMemberships.clear();
        assertEquals(java.util.Set.of(a), memberships);
        assertTrue(NeroFactionsApi.membershipView(access, other).isEmpty());
        assertEquals(400, NeroFactionsApi.standingValue(access, offline, a));
        assertEquals(-101, NeroFactionsApi.standingValue(access, other, a));
        assertEquals(0, NeroFactionsApi.standingValue(access, offline, absent));
        assertEquals("member", NeroFactionsApi.tierView(access, offline, a).orElseThrow().id());
        assertEquals("outsider", NeroFactionsApi.tierView(access, other, a).orElseThrow().id());
        assertTrue(NeroFactionsApi.tierView(access, offline, absent).isEmpty());
        assertTrue(NeroFactionsApi.enemies(access, a, b));
        assertFalse(NeroFactionsApi.enemies(access, b, a));
        assertFalse(NeroFactionsApi.enemies(access, a, absent));
        assertEquals(7, NeroFactionsApi.award(access, offline, a, 25, a));
        assertEquals(0, NeroFactionsApi.award(access, offline, absent, 25, a));
        assertEquals(0, NeroFactionsApi.award(access, offline, a, -1, a));
        assertEquals(0, NeroFactionsApi.award(access, offline, a, 1_000_001, a));
        assertEquals(0, NeroFactionsApi.award(access, offline, a, 25, null));
        assertEquals(1, awards[0]);
    }

    @Test
    void nullServerReturnsSafeDefaults() {
        UUID player = UUID.randomUUID();
        Identifier id = Identifier.fromNamespaceAndPath("nerofactions", "test");
        assertTrue(NeroFactionsApi.factions(null).isEmpty());
        assertTrue(NeroFactionsApi.membershipOf(null, player).isEmpty());
        assertEquals(0, NeroFactionsApi.standing(null, player, id));
        assertTrue(NeroFactionsApi.tierOf(null, player, id).isEmpty());
        assertFalse(NeroFactionsApi.isEnemy(null, id, id));
        assertEquals(0, NeroFactionsApi.awardEvent(null, player, id, 10, id));
    }

    @Test
    void viewsCopyFactionDataAndExposeThresholds() {
        Identifier id = Identifier.fromNamespaceAndPath("nerofactions", "test");
        var enemies = new ArrayList<Identifier>();
        enemies.add(id);
        var faction = new FactionDefinition(id, "Test", "Theme",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
                Map.of(), enemies, List.of(), Optional.empty());
        FactionView view = NeroFactionsApi.view(faction);
        enemies.clear();
        assertEquals(List.of(id), view.enemies());
        assertEquals(new FactionTierView("member", 400), view.tiers().get(2));
        assertThrows(UnsupportedOperationException.class, () -> view.enemies().clear());
    }
}
