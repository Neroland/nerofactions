package za.co.neroland.nerofactions.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
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
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.registry.NeroFactionsItems;

@Mixin(ItemStack.class)
abstract class FactionGearTooltipMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void nerofactions$appendSetProgress(Item.TooltipContext context, Player player, TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> result) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!stack.is(NeroFactionsItems.UNION_HELMET.get()) && !stack.is(NeroFactionsItems.UNION_CHESTPLATE.get())
                && !stack.is(NeroFactionsItems.UNION_LEGGINGS.get()) && !stack.is(NeroFactionsItems.UNION_BOOTS.get())) {
            return;
        }
        List<Component> lines = new ArrayList<>(result.getReturnValue());
        if (player == null) {
            lines.add(Component.translatable("tooltip.nerofactions.union_exosuit.requires").withStyle(ChatFormatting.GRAY));
        } else {
            int equipped = 0;
            if (player.getItemBySlot(EquipmentSlot.HEAD).is(NeroFactionsItems.UNION_HELMET.get())) equipped++;
            if (player.getItemBySlot(EquipmentSlot.CHEST).is(NeroFactionsItems.UNION_CHESTPLATE.get())) equipped++;
            if (player.getItemBySlot(EquipmentSlot.LEGS).is(NeroFactionsItems.UNION_LEGGINGS.get())) equipped++;
            if (player.getItemBySlot(EquipmentSlot.FEET).is(NeroFactionsItems.UNION_BOOTS.get())) equipped++;
            lines.add(Component.translatable("tooltip.nerofactions.union_exosuit.progress", equipped)
                    .withStyle(ChatFormatting.GRAY));
            var standing = FactionsClientState.standing().factions().stream()
                    .filter(row -> row.faction().equals(OutpostFaction.MINER_UNION.factionId())).findFirst().orElse(null);
            String state = standing == null || !standing.member() ? "membership" : standing.tier() < FactionTier.TRUSTED.ordinal() ? "trusted"
                    : equipped < 4 ? "pieces" : "eligible";
            lines.add(Component.translatable("tooltip.nerofactions.union_exosuit." + state)
                    .withStyle("eligible".equals(state) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        result.setReturnValue(lines);
    }
}
