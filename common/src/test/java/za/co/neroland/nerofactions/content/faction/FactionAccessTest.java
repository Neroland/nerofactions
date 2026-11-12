package za.co.neroland.nerofactions.content.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;

class FactionAccessTest {
    private static final Identifier HOST = OutpostFaction.MINER_UNION.factionId();
    private static final Identifier RIVAL = OutpostFaction.VOID_CULT.factionId();
    private static final FactionDefinition VENDOR = faction(HOST, List.of(RIVAL));
    private static final FactionDefinition ENEMY = faction(RIVAL, List.of());
    private static final Map<Identifier, FactionDefinition> FACTIONS = Map.of(HOST, VENDOR, RIVAL, ENEMY);

    @Test
    void onlyActualRivalMembershipTriggersTheRivalStandingRule() {
        assertEquals(FactionAccess.OPEN, access(List.of(), 0, 2500));
        assertEquals(FactionAccess.OPEN, access(List.of(RIVAL), 0, 399));
        assertEquals(FactionAccess.SURCHARGE, access(List.of(RIVAL), 0, 400));
        assertEquals(FactionAccess.REFUSE_RIVAL, access(List.of(HOST, RIVAL), 1200, 1000));
        assertFalse(access(List.of(RIVAL), 0, 999).refusesService());
        assertTrue(access(List.of(RIVAL), 0, 2500).refusesService());
    }

    @Test
    void standingBoundaryAppliesToMembersAndVisitors() {
        assertEquals(FactionAccess.OPEN, access(List.of(), -100, 0));
        assertEquals(FactionAccess.REFUSE_STANDING, access(List.of(HOST), -101, 0));
        assertEquals(FactionAccess.REFUSE_STANDING, access(List.of(), -101, 0));
    }

    @Test
    void hostilityUsesTheHostDirectionAndIgnoresRemovedRivals() {
        assertEquals(FactionAccess.OPEN,
                FactionAccess.evaluate(ENEMY, List.of(HOST), FACTIONS, id -> 1000, -100));
        assertEquals(FactionAccess.OPEN,
                FactionAccess.evaluate(VENDOR, List.of(RIVAL), Map.of(HOST, VENDOR), id -> 1000, -100));
    }

    private static FactionAccess access(List<Identifier> memberships, int hostStanding, int rivalStanding) {
        return FactionAccess.evaluate(VENDOR, memberships, FACTIONS,
                id -> id.equals(HOST) ? hostStanding : rivalStanding, -100);
    }

    private static FactionDefinition faction(Identifier id, List<Identifier> enemies) {
        return new FactionDefinition(id, id.getPath(), "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
                Map.of(), enemies, List.of(), Optional.empty());
    }
}
