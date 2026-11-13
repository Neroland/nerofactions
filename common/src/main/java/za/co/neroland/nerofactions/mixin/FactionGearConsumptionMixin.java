package za.co.neroland.nerofactions.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

@Mixin(ItemStack.class)
abstract class FactionGearConsumptionMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void nerofactions$colonistMeal(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> result) {
        if (user instanceof ServerPlayer player && ((ItemStack) (Object) this).has(DataComponents.FOOD)
                && GearSetBonuses.active(player, FactionArmor.COLONIST_FIELD_GEAR)) {
            var food = player.getFoodData();
            food.setFoodLevel(Math.min(20, food.getFoodLevel() + 2));
        }
    }
}
