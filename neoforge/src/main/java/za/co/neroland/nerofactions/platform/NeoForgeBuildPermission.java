package za.co.neroland.nerofactions.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26.1 {
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//?} else {
/*import net.neoforged.neoforge.event.level.BlockEvent;
*///?}

/**
 * NeoForge implementation of {@link BuildPermission}: posts the loader's break event (where NeoForge
 * claim mods hook) and treats cancellation as a refusal. 26.x moved the event to
 * {@code event.level.block.BreakBlockEvent}; before 26.1 it is {@code BlockEvent.BreakEvent}.
 * Registered via {@code META-INF/services/za.co.neroland.nerofactions.platform.BuildPermission}.
 */
public final class NeoForgeBuildPermission implements BuildPermission {

    @Override
    public boolean mayModify(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState current) {
        //? if >=26.1 {
        BreakBlockEvent event = new BreakBlockEvent(level, pos, current, player);
        //?} else {
        /*BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, current, player);
        *///?}
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }
}
