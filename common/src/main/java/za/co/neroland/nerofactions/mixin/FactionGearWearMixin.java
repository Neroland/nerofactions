package za.co.neroland.nerofactions.mixin;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

/**
 * Salvager durability rules on every durability loss. Vanilla (Fabric) passes the {@link ServerPlayer} who
 * caused the wear; NeoForge 26.x patches the same method to take any {@link LivingEntity}. Both shapes are
 * declared in one group that must match exactly once, so a loader that has neither still fails at load
 * instead of silently dropping the set bonus.
 */
@Mixin(ItemStack.class)
abstract class FactionGearWearMixin {
    //? if >=26.1 {
    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int nerofactions$salvageWearLiving(int amount, int originalAmount, ServerLevel level, LivingEntity user,
            Consumer<ItemStack> onBreak) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }
    //?}

    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int nerofactions$salvageWear(int amount, int originalAmount, ServerLevel level, ServerPlayer user,
            Consumer<net.minecraft.world.item.Item> onBreak) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }
}
