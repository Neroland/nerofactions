package za.co.neroland.nerofactions.content.faction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.faction.FactionGuard.Targeting;

class FactionGuardTest {
    private static final Identifier HOST = OutpostFaction.MINER_UNION.factionId();
    private static final Identifier ENEMY = OutpostFaction.VOID_CULT.factionId();
    private static final FactionDefinition GUARD_FACTION = faction(HOST, List.of(ENEMY));
    private static final FactionDefinition ENEMY_FACTION = faction(ENEMY, List.of());
    private static final Map<Identifier, FactionDefinition> FACTIONS = Map.of(HOST, GUARD_FACTION, ENEMY, ENEMY_FACTION);

    @Test
    void aNeutralVisitorIsLeftAlone() {
        assertFalse(targets(List.of(), 0, 0, false));
        assertFalse(targets(List.of(HOST), 1200, 0, false), "own member");
        assertFalse(targets(List.of(), -100, 0, false), "exactly at the hostile boundary");
    }

    @Test
    void theHostileStandingBandIsTargeted() {
        assertTrue(targets(List.of(), -101, 0, false));
        assertTrue(targets(List.of(HOST), -500, 0, false));
    }

    @Test
    void enemyFactionMembersAreTargetedFromTrustedButNotAtMember() {
        assertTrue(targets(List.of(ENEMY), 0, 1000, false), "enemy trusted");
        assertTrue(targets(List.of(ENEMY), 0, 2500, false), "enemy inner circle");
        assertFalse(targets(List.of(ENEMY), 0, 400, false), "enemy member is surcharged, not attacked");
        assertFalse(targets(List.of(), 0, 2500, false), "high enemy standing without membership");
    }

    @Test
    void anyoneWhoRecentlyHurtTheGuardOrEnvoyIsTargeted() {
        assertTrue(targets(List.of(), 0, 0, true));
        assertTrue(targets(List.of(ENEMY), 0, 400, true));
    }

    @Test
    void voidVestmentsAreDetectedFromTwentyBlocksByCultOpponentsOnly() {
        assertTrue(Targeting.opposesCult(GUARD_FACTION));
        assertFalse(Targeting.opposesCult(ENEMY_FACTION));
        assertTrue(Targeting.detects(12 * 12, false));
        assertFalse(Targeting.detects(12 * 12 + 1, false));
        assertTrue(Targeting.detects(20 * 20, true));
        assertFalse(Targeting.detects(20 * 20 + 1, true));
        assertTrue(Targeting.detects(16 * 16, true));
        assertFalse(Targeting.detects(16 * 16, false));
    }

    private static boolean targets(List<Identifier> memberships, int hostStanding, int enemyStanding, boolean defending) {
        var access = FactionAccess.evaluate(GUARD_FACTION, memberships, FACTIONS,
                id -> id.equals(HOST) ? hostStanding : enemyStanding, -100);
        return Targeting.shouldTarget(access, defending);
    }

    private static FactionDefinition faction(Identifier id, List<Identifier> enemies) {
        return new FactionDefinition(id, id.getPath(), "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
                Map.of(), enemies, List.of(), Optional.empty());
    }
}
