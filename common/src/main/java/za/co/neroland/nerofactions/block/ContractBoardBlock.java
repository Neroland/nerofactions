package za.co.neroland.nerofactions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.menu.ContractBoardMenu;
import za.co.neroland.nerofactions.menu.MenuOpener;

public final class ContractBoardBlock extends Block {
    public ContractBoardBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer online && MenuOpener.open(online, ContractBoardMenu.provider(level, pos)).isPresent()
                && online.containerMenu instanceof ContractBoardMenu menu) menu.refresh();
        return InteractionResult.SUCCESS;
    }
}
