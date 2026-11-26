package za.co.neroland.nerofactions.mixin;

//? if <1.20.5 {
/*import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import za.co.neroland.nerofactions.client.FactionsClientState;

@Mixin(GameRenderer.class)
abstract class LegacyVoidEntityReachMixin {
    @ModifyConstant(method = "pick", constant = @Constant(doubleValue = 9.0D))
    private double nerofactions$extendEntityReach(double distanceSquared) {
        double reach = Math.sqrt(distanceSquared) + FactionsClientState.legacyVoidReachBonus();
        return reach * reach;
    }
}
*///?}
