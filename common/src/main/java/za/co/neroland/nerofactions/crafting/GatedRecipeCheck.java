package za.co.neroland.nerofactions.crafting;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;

/**
 * The pure decision core of {@link GatedRecipe}, extracted so it unit-tests on a plain JVM with no
 * server: given a faction's ladder, a reputation value and the recipe's requirements, may this
 * player craft?
 *
 * <p><b>Standing gates, membership does not.</b> A gated recipe asks only what tier the player's
 * reputation resolves to — deliberately <em>not</em> whether the player is currently a member of
 * the faction. Standing is the earned, decaying quantity the whole tier system is built on; a
 * lapsed member whose standing has decayed below the required tier loses the recipe (the caller
 * applies pending decay before reading), while a member in name whose standing collapsed never
 * keeps it. Tying recipes to membership would also make {@code singleAllegiance} silently strip
 * every other faction's recipes on join, which is a membership rule, not a crafting one.
 *
 * <p><b>Fail closed, always:</b> an unknown faction, a missing tier threshold, or an unmet Core
 * gate all answer {@code false}. A gated recipe that cannot prove it is unlocked is locked.
 */
public final class GatedRecipeCheck {

    private GatedRecipeCheck() {
    }

    /** Whether {@code reputation} resolves to at least {@code required} on this faction's ladder. */
    public static boolean standingMet(@Nullable FactionDefinition faction, int reputation,
            FactionTier required) {
        if (faction == null || required == null) {
            return false;
        }
        return FactionTiers.tierOf(faction, reputation).ordinal() >= required.ordinal();
    }

    /**
     * The full recipe decision: tier met <b>and</b> (when the recipe names a {@code core_gate})
     * that Core progression gate open for the player.
     *
     * @param faction          the resolved faction definition, or null if unknown (fails closed)
     * @param reputation       the player's standing, read <em>after</em> pending decay was applied
     * @param required         the tier the recipe demands
     * @param coreGateRequired whether the recipe declares a {@code core_gate}
     * @param coreGateOpen     the server-resolved answer for that gate (ignored when not required)
     */
    public static boolean allowed(@Nullable FactionDefinition faction, int reputation,
            FactionTier required, boolean coreGateRequired, boolean coreGateOpen) {
        return standingMet(faction, reputation, required) && (!coreGateRequired || coreGateOpen);
    }
}
