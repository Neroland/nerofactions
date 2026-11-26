package za.co.neroland.nerofactions.mixin;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
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
        ItemStack stack = (ItemStack) (Object) this;
        //? if >=1.20.5 {
        boolean isFood = stack.has(DataComponents.FOOD);
        //?} else {
        /*boolean isFood = stack.getItem().isEdible();
        *///?}
        if (user instanceof ServerPlayer player && isFood
                && GearSetBonuses.active(player, FactionArmor.COLONIST_FIELD_GEAR)) {
            var food = player.getFoodData();
            food.setFoodLevel(Math.min(20, food.getFoodLevel() + 2));
        }
    }
}
