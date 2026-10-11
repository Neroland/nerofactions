package za.co.neroland.nerofactions.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.TradeModifier;

/** Plain-JVM tests of the sale-reputation speciality match (the game-side award is runtime). */
class SaleReputationTest {

    private static final Identifier ORES = Identifier.parse("c:ores");
    private static final Identifier IRON_ORE = Identifier.parse("minecraft:iron_ore");
    private static final Identifier BREAD = Identifier.parse("minecraft:bread");
    private static final Identifier MINERS = Identifier.parse("nerofactions:miners");
    private static final Identifier BAKERS = Identifier.parse("nerofactions:bakers");

    private static FactionDefinition faction(Identifier id, Identifier speciality) {
        return new FactionDefinition(id, id.getPath(), "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(), List.of(new TradeModifier(speciality, Map.of("member", 0.9D))),
                Optional.empty());
    }

    private static Map<Identifier, FactionDefinition> factions() {
        Map<Identifier, FactionDefinition> map = new LinkedHashMap<>();
        map.put(MINERS, faction(MINERS, ORES));
        map.put(BAKERS, faction(BAKERS, BREAD));
        return map;
    }

    @Test
    void aTaggedSpecialityMatchesOnlyForMembers() {
        assertEquals(List.of(MINERS),
                SaleReputation.matchingFactions(IRON_ORE, Set.of(ORES), Set.of(MINERS, BAKERS), factions()));
        assertTrue(SaleReputation.matchingFactions(IRON_ORE, Set.of(ORES), Set.of(BAKERS), factions()).isEmpty(),
                "selling another faction's speciality earns nothing with your own");
    }

    @Test
    void anItemIdSpecialityMatchesWithoutATag() {
        assertEquals(List.of(BAKERS),
                SaleReputation.matchingFactions(BREAD, Set.of(), Set.of(BAKERS), factions()));
    }

    @Test
    void nothingToMatchIsEmptyNeverAThrow() {
        assertTrue(SaleReputation.matchingFactions(BREAD, Set.of(), Set.of(MINERS), factions()).isEmpty());
        assertTrue(SaleReputation.matchingFactions(null, Set.of(), Set.of(MINERS), factions()).isEmpty());
        assertTrue(SaleReputation.matchingFactions(BREAD, null, null, factions()).isEmpty());
    }
}
