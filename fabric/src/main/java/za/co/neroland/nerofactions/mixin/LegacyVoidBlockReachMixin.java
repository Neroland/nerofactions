package za.co.neroland.nerofactions.mixin;

//? if <1.20.5 {
/*import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerofactions.client.FactionsClientState;

@Mixin(MultiPlayerGameMode.class)
abstract class LegacyVoidBlockReachMixin {
    @Inject(method = "getPickRange", at = @At("RETURN"), cancellable = true)
    private void nerofactions$extendBlockReach(CallbackInfoReturnable<Float> result) {
        result.setReturnValue(result.getReturnValueF() + (float) FactionsClientState.legacyVoidReachBonus());
    }
}
*///?}
