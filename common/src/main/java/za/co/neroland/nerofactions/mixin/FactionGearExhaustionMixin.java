package za.co.neroland.nerofactions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

@Mixin(Player.class)
abstract class FactionGearExhaustionMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float nerofactions$corporateExhaustion(float amount) {
        return (Object) this instanceof ServerPlayer player ? GearSetBonuses.exhaustion(player, amount) : amount;
    }
}
