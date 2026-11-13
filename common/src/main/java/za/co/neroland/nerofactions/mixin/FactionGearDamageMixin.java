package za.co.neroland.nerofactions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

@Mixin(ServerPlayer.class)
abstract class FactionGearDamageMixin {
    //? if >=1.21.2 {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float nerofactions$adjustDamage(float amount, net.minecraft.server.level.ServerLevel level, DamageSource source) {
    //?} else {
    /*@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float nerofactions$adjustDamage(float amount, DamageSource source) {
    *///?}
        return GearSetBonuses.incomingDamage((ServerPlayer) (Object) this, source, amount);
    }
}
