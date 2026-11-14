package za.co.neroland.nerofactions.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Asks the running loader whether a player may modify a block, by firing that loader's own
 * block-break event for the position and reading the verdict back.
 *
 * <p>Claim and protection mods guard terrain by listening to the loader's break/place events, so a
 * block that common code rewrites directly through {@code setBlockAndUpdate} would bypass them. Any
 * NeroFactions feature that changes a block on a player's behalf must vet the target through this
 * seam first. Each loader module ships exactly one implementation, registered via
 * {@code META-INF/services/za.co.neroland.nerofactions.platform.BuildPermission}; resolve it with
 * {@link Services#load(Class)} during initialisation, never lazily on a tick path.
 *
 * <p>Nothing here logs or stores the player, the position or the block (POPIA/GDPR).
 */
public interface BuildPermission {

    /**
     * True when no listener refuses {@code player} breaking {@code current} at {@code pos}.
     *
     * @param level   the server level the block is in
     * @param player  the player on whose behalf the block would change
     * @param pos     the block position that would change
     * @param current the block state currently at {@code pos}
     */
    boolean mayModify(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState current);
}
