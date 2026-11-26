package za.co.neroland.nerofactions.content.faction.corporation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class ExecutiveStamp extends Item {
    public ExecutiveStamp(Properties properties) { super(properties.durability(64)); }

    //? if >=1.21.2 {
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*@Override public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
    *///?}
        if (player instanceof ServerPlayer customer && level instanceof ServerLevel world) {
            CorporateCounter.stamp(customer, world, hand);
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        *///?}
    }

    //? if <1.20.5 {
    /*@Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.nerofactions.executive_stamp"));
    }
    *///?}
}
