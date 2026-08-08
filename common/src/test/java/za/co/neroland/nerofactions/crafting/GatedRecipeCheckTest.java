package za.co.neroland.nerofactions.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;

/**
 * The pure decision core of the {@code nerofactions:gated} recipe condition — the part that must
 * be provably right without a server. The stock 0/100/400/1000/2500 ladder throughout.
 *
 * <p>Deliberately absent from every signature here: membership. Standing gates recipes; being (or
 * not being) a current member of the faction cannot appear in the decision because it is not an
 * input — that is the design, not an omission.
 */
class GatedRecipeCheckTest {

    private static final FactionDefinition FACTION = new FactionDefinition(
            Identifier.fromNamespaceAndPath("nerofactions", "check"),
            "Check Faction", "A test faction.",
            Map.of("outsider", 0, "associate", 100, "member", 400,
                    "trusted", 1000, "inner_circle", 2500),
            Map.of(), List.of(), List.of(), Optional.empty());

    @Test
    void standingMetResolvesTheLadderInclusively() {
        assertTrue(GatedRecipeCheck.standingMet(FACTION, 1000, FactionTier.TRUSTED),
                "exactly at threshold is in the tier");
        assertFalse(GatedRecipeCheck.standingMet(FACTION, 999, FactionTier.TRUSTED));
        assertTrue(GatedRecipeCheck.standingMet(FACTION, 1000, FactionTier.MEMBER),
                "a higher tier satisfies a lower requirement");
        assertTrue(GatedRecipeCheck.standingMet(FACTION, 0, FactionTier.OUTSIDER),
                "an outsider requirement is met by everyone at neutral standing");
        assertFalse(GatedRecipeCheck.standingMet(FACTION, -50, FactionTier.ASSOCIATE),
                "negative standing is outsider");
    }

    @Test
    void unknownFactionFailsClosed() {
        assertFalse(GatedRecipeCheck.standingMet(null, 5000, FactionTier.ASSOCIATE));
        assertFalse(GatedRecipeCheck.allowed(null, 5000, FactionTier.ASSOCIATE, false, true),
                "a recipe naming a faction the server does not know is locked, not open");
    }

    @Test
    void coreGateComposesAsAConjunction() {
        assertTrue(GatedRecipeCheck.allowed(FACTION, 2600, FactionTier.INNER_CIRCLE, true, true));
        assertFalse(GatedRecipeCheck.allowed(FACTION, 2600, FactionTier.INNER_CIRCLE, true, false),
                "standing alone cannot bypass a declared core_gate");
        assertFalse(GatedRecipeCheck.allowed(FACTION, 400, FactionTier.TRUSTED, true, true),
                "an open gate cannot bypass missing standing");
        assertTrue(GatedRecipeCheck.allowed(FACTION, 1000, FactionTier.TRUSTED, false, false),
                "with no core_gate declared, the gate answer is ignored entirely");
    }
}
