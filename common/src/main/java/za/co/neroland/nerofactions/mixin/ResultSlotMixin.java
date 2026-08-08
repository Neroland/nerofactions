package za.co.neroland.nerofactions.mixin;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import za.co.neroland.nerofactions.crafting.CraftingPlayer;

/**
 * Pins the crafting player around vanilla's <b>take-time remainder</b> recipe lookup.
 * {@code ResultSlot.onTake} re-resolves the recipe (independently of the result-slot refresh) to
 * compute container remainders and consume the grid; if a {@code nerofactions:gated} recipe failed
 * to resolve here for lack of a player, vanilla's fallback returns copies of every input — i.e.
 * the grid would not be consumed, an infinite-craft dupe. {@code ResultSlot} owns its player as a
 * field, so this redirect attributes the lookup to exactly that player, scoped by
 * {@link CraftingPlayer#resolveWith}'s try/finally.
 */
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {

    @Shadow
    @Final
    private Player player;

    @Redirect(method = "getRemainingItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor("
                            + "Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                            + "Lnet/minecraft/world/level/Level;)"
                            + "Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> nerofactions$remainderWithCrafter(
            RecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, Level level) {
        ServerPlayer crafter = this.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        // The parameter is RecipeInput so the handler descriptor matches the erased target; the
        // call site only ever passes a CraftingInput.
        return CraftingPlayer.resolveWith(crafter,
                () -> manager.getRecipeFor(type, (CraftingInput) input, level));
    }
}
