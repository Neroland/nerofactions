package za.co.neroland.nerofactions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.menu.MenuOpener;
import za.co.neroland.nerofactions.menu.RegistryMenu;
import za.co.neroland.nerofactions.network.StandingSync;

public final class FactionRegistryBlock extends Block {
    public FactionRegistryBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
    *///?}
        if (player instanceof ServerPlayer online) {
            if (!za.co.neroland.nerofactions.config.NeroFactionsConfig.REGISTRY_ENABLED.get()) {
                za.co.neroland.nerofactions.platform.VersionCompat.systemMessage(player,
                        net.minecraft.network.chat.Component.translatable("gui.nerofactions.registry_disabled"));
                return InteractionResult.SUCCESS;
            }
            if (MenuOpener.open(online, RegistryMenu.provider(level, pos)).isPresent()) {
                StandingSync.send(online);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
