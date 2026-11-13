package za.co.neroland.nerofactions.mixin;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

@Mixin(ItemStack.class)
abstract class FactionGearWearMixin {
    //? if >=26.1 {
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nerofactions$salvageWear(int amount, int originalAmount, ServerLevel level, LivingEntity user,
            Consumer<ItemStack> onBreak) {
    //?} else {
    /*@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nerofactions$salvageWear(int amount, int originalAmount, ServerLevel level, net.minecraft.server.level.ServerPlayer user,
            Consumer<net.minecraft.world.item.Item> onBreak) {
    *///?}
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }
}
