package za.co.neroland.nerofactions.mixin;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import za.co.neroland.nerofactions.crafting.CraftingPlayer;

/**
 * Pins the crafting player around vanilla's <b>result-slot refresh</b> recipe lookup so
 * {@code nerofactions:gated} recipes can answer {@code matches()} for the right player.
 * {@code CraftingMenu.slotChangedCraftingGrid} is the single server-side implementation behind
 * both the 3x3 crafting table ({@code CraftingMenu.slotsChanged} / {@code finishPlacingRecipe})
 * and the 2x2 inventory grid ({@code InventoryMenu.slotsChanged} calls the same static), and it
 * receives the player as a parameter — so one redirect covers every grid on every loader, with
 * the pin scoped by {@link CraftingPlayer#resolveWith}'s try/finally (never a stale player).
 *
 * <p>Server-side only by construction: the target method takes a {@link ServerLevel}. Defensive
 * on the player: anything that is somehow not a {@link ServerPlayer} runs the lookup
 * unattributed, and gated recipes then fail closed.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {

    @Redirect(method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor("
                            + "Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                            + "Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/item/crafting/RecipeHolder;)"
                            + "Ljava/util/Optional;"))
    private static Optional<RecipeHolder<CraftingRecipe>> nerofactions$resolveWithCrafter(
            RecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, Level level,
            RecipeHolder<CraftingRecipe> hint,
            AbstractContainerMenu menu, ServerLevel serverLevel, Player player,
            CraftingContainer container, ResultContainer resultSlots,
            RecipeHolder<CraftingRecipe> recipeHint) {
        ServerPlayer crafter = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        // The parameter is RecipeInput so the handler descriptor matches the erased target; the
        // call site only ever passes a CraftingInput.
        return CraftingPlayer.resolveWith(crafter,
                () -> manager.getRecipeFor(type, (CraftingInput) input, level, hint));
    }
}
