package za.co.neroland.nerofactions.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerofactions.client.FactionsClientState;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.FactionArmor;

@Mixin(Entity.class)
abstract class VoidVestmentsSightMixin {
    @Inject(method = "isInvisibleTo", at = @At("RETURN"), cancellable = true)
    private void nerofactions$revealInvisibility(Player viewer, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ() && canSee(viewer)) {
            result.setReturnValue(false);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean canSee(Player viewer) {
        return viewer != null && viewer.level().isClientSide()
                && FactionArmor.VOID_VESTMENTS.equipped(viewer) == 4
                && FactionsClientState.standing().factions().stream().anyMatch(row ->
                        row.faction().equals(FactionArmor.VOID_VESTMENTS.factionId())
                        && row.member() && row.tier() >= FactionTier.TRUSTED.ordinal());
    }
}
