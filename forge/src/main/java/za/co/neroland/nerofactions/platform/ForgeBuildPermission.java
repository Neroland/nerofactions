package za.co.neroland.nerofactions.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.6 {
import net.minecraftforge.common.util.Result;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
*///?}
import net.minecraftforge.event.level.BlockEvent;

/**
 * Forge implementation of {@link BuildPermission}: posts {@link BlockEvent.BreakEvent} (where Forge
 * claim mods hook) and reads the verdict back. From 1.21.6 (EventBus 7) the event has its own bus and
 * listeners refuse with {@code setResult(Result.DENY)}; before that it is posted on the global game
 * bus and refused by cancellation. Registered via
 * {@code META-INF/services/za.co.neroland.nerofactions.platform.BuildPermission}.
 */
public final class ForgeBuildPermission implements BuildPermission {

    @Override
    public boolean mayModify(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState current) {
        //? if >=1.21.6 {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, current, player, Result.DEFAULT);
        BlockEvent.BreakEvent.BUS.post(event);
        return event.getResult() != Result.DENY;
        //?} else {
        /*BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, current, player);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled();
        *///?}
    }
}
