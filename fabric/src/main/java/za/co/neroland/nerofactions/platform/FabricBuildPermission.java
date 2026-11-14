package za.co.neroland.nerofactions.platform;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric implementation of {@link BuildPermission}: asks every {@link PlayerBlockBreakEvents#BEFORE}
 * listener (where Fabric claim mods hook) whether the break may go ahead. Registered via
 * {@code META-INF/services/za.co.neroland.nerofactions.platform.BuildPermission}.
 */
public final class FabricBuildPermission implements BuildPermission {

    @Override
    public boolean mayModify(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState current) {
        return PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, pos, current, level.getBlockEntity(pos));
    }
}
