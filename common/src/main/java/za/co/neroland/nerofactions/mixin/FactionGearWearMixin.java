package za.co.neroland.nerofactions.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

//? if >=1.21.4 {
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.injection.ModifyArg;
//?} else {
/*import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?}

@Mixin(ItemStack.class)
abstract class FactionGearWearMixin {
    private static final String PLAYER_WEAR =
            "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V";
    private static final String LIVING_WEAR =
            "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V";

    // Forwarding overloads don't call the damage calculation. Exactly one hook should match.
    //? if >=1.21.4 {
    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyArg(method = LIVING_WEAR, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)I"),
            index = 0, require = 0)
    private int nerofactions$salvageWearLiving(int amount, ServerLevel level, LivingEntity user) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }

    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyArg(method = PLAYER_WEAR, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)I"),
            index = 0, require = 0)
    private int nerofactions$salvageWearPlayer(int amount, ServerLevel level, ServerPlayer user) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }

    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyArg(method = PLAYER_WEAR, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;processDurabilityChange(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;ZLjava/util/function/Consumer;)I"),
            index = 0, require = 0)
    private int nerofactions$salvageWearForge(int amount, ServerLevel level, ServerPlayer user,
            boolean damageItem, Consumer<?> onBreak) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }
    //?} else if >=1.21 {
    /*private static final String DAMAGEABLE = "Lnet/minecraft/world/item/ItemStack;isDamageableItem()Z";

    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyVariable(method = LIVING_WEAR, at = @At(value = "INVOKE", target = DAMAGEABLE),
            argsOnly = true, ordinal = 0, require = 0)
    private int nerofactions$salvageWearLiving(int amount, int originalAmount, ServerLevel level, LivingEntity user) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }

    @Group(name = "nerofactions$salvageWear", min = 1, max = 1)
    @ModifyVariable(method = PLAYER_WEAR, at = @At(value = "INVOKE", target = DAMAGEABLE),
            argsOnly = true, ordinal = 0, require = 0)
    private int nerofactions$salvageWearPlayer(int amount, int originalAmount, ServerLevel level, ServerPlayer user) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }
    *///?} else {
    /*@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/util/RandomSource;"
            + "Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/Runnable;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nerofactions$salvageWearLegacy(int amount, int originalAmount,
            net.minecraft.util.RandomSource random, ServerPlayer user) {
        return GearSetBonuses.salvageWear(user, (ItemStack) (Object) this, amount);
    }
    *///?}
}
