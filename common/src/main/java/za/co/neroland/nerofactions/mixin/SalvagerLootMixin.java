package za.co.neroland.nerofactions.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.GearSetBonuses;

@Mixin(RandomizableContainer.class)
interface SalvagerLootMixin {
    @Redirect(method = "unpackLootTable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getLuck()F"))
    private float nerofactions$salvageLuck(Player viewer) {
        return viewer.getLuck() + (viewer instanceof ServerPlayer player
                && GearSetBonuses.active(player, FactionArmor.SALVAGER_PATCHWORK) ? 2 : 0);
    }
}
