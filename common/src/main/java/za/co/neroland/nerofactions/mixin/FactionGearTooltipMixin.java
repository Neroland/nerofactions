package za.co.neroland.nerofactions.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import za.co.neroland.nerofactions.client.FactionsClientState;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.FactionArmor;

@Mixin(ItemStack.class)
abstract class FactionGearTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void nerofactions$appendSetProgress(Item.TooltipContext context, Player player, TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> result) {
        ItemStack stack = (ItemStack) (Object) this;
        FactionArmor set = null;
        for (FactionArmor candidate : FactionArmor.values()) {
            if (candidate.includes(stack)) {
                set = candidate;
                break;
            }
        }
        if (set == null) {
            return;
        }
        var faction = set.factionId();
        List<Component> lines = new ArrayList<>(result.getReturnValue());
        String prefix = "tooltip.nerofactions." + set.id() + ".";
        if (player == null) {
            lines.add(Component.translatable(prefix + "requires").withStyle(ChatFormatting.GRAY));
        } else {
            int equipped = set.equipped(player);
            lines.add(Component.translatable(prefix + "progress", equipped)
                    .withStyle(ChatFormatting.GRAY));
            var standing = FactionsClientState.standing().factions().stream()
                    .filter(row -> row.faction().equals(faction)).findFirst().orElse(null);
            String state = standing == null || !standing.member() ? "membership" : standing.tier() < FactionTier.TRUSTED.ordinal() ? "trusted"
                    : equipped < 4 ? "pieces" : "eligible";
            lines.add(Component.translatable(prefix + state)
                    .withStyle("eligible".equals(state) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        result.setReturnValue(lines);
    }
}
