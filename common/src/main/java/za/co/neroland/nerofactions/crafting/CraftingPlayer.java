package za.co.neroland.nerofactions.crafting;

import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.CraftingRecipe;
//? if >=1.20.2 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}

import org.jetbrains.annotations.Nullable;

/**
 * The server-side "who is crafting right now" seam. Vanilla's recipe lookup
 * ({@code RecipeManager.getRecipeFor}) is player-blind, but a {@link GatedRecipe} can only answer
 * {@code matches()} for a specific player — so the two mixins ({@code CraftingMenuMixin} around the
 * result-slot refresh, {@code ResultSlotMixin} around the take-time remainder lookup) wrap exactly
 * those vanilla lookups in {@link #resolveWith}, which pins the crafting player to the current
 * thread for the duration of the lookup and <b>always</b> clears it in {@code finally} — an
 * exception mid-lookup can never leave a stale player behind to leak an unlock into a later,
 * unattributed lookup (an auto-crafter's, for instance).
 *
 * <p>Everything here is server-side; in MC 26.x the client has no recipe manager at all, so
 * {@code matches()} is never called client-side. Outside a {@link #resolveWith} window —
 * auto-crafters, the crafter block, any modded player-blind lookup — {@link #current()} is null
 * and {@link GatedRecipe} fails closed: no player, no match.
 */
public final class CraftingPlayer {

    private static final ThreadLocal<ServerPlayer> CURRENT = new ThreadLocal<>();

    private CraftingPlayer() {
    }

    //? if >=1.20.2 {
    // Recipe-book placement can hand the crafting grid a non-crafting recipe through an erased holder.
    @Nullable
    public static RecipeHolder<CraftingRecipe> craftingHint(@Nullable RecipeHolder<?> hint) {
        if (hint == null || !(hint.value() instanceof CraftingRecipe)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        RecipeHolder<CraftingRecipe> crafting = (RecipeHolder<CraftingRecipe>) hint;
        return crafting;
    }
    //?}

    /** The player the current thread's recipe lookup is for, or null when unattributed. */
    @Nullable
    public static ServerPlayer current() {
        return CURRENT.get();
    }

    /**
     * Runs {@code lookup} with {@code player} pinned as the crafting player, restoring the
     * previous state (normally: cleared) in {@code finally}. A null player runs the lookup
     * unattributed — gated recipes then simply do not resolve.
     */
    public static <T> T resolveWith(@Nullable ServerPlayer player, Supplier<T> lookup) {
        if (player == null) {
            return lookup.get();
        }
        CURRENT.set(player);
        try {
            return lookup.get();
        } finally {
            CURRENT.remove();
        }
    }
}
