package za.co.neroland.nerofactions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

@Mixin(LivingEntity.class)
abstract class VoidVestmentsMeleeMixin {
    //? if >=1.21.2 {
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void nerofactions$lifeSteal(net.minecraft.server.level.ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> result) {
    //?} else {
    /*@Inject(method = "hurt", at = @At("RETURN"))
    private void nerofactions$lifeSteal(DamageSource source, float amount, CallbackInfoReturnable<Boolean> result) {
    *///?}
        if (result.getReturnValueZ() && amount > 0 && source.is(DamageTypes.PLAYER_ATTACK)
                && source.getDirectEntity() instanceof ServerPlayer attacker
                && GearSetBonuses.active(attacker, FactionArmor.VOID_VESTMENTS)) {
            attacker.heal(Math.min(1.5F, amount * 0.15F));
        }
    }
}
