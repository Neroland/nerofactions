package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.block.TradeTerminalBlock;

/**
 * Block registrations, through Neroland Core's {@link RegistrationProvider} — the same seam the
 * recipe serializer uses ({@code NeroFactionsRecipes}) and the NeroColonies {@code
 * NeroColoniesBlocks} pattern. On Fabric these apply the moment this class is loaded, which is why
 * {@link #init()} exists and is called from common init; on NeoForge/Forge the entry point attaches
 * the deferred registers to the mod bus via {@code RegistrationProvider.attach}.
 *
 * <p><b>One block, deliberately.</b> The trade terminal is the whole block roster of 0.1.0 —
 * a single shared terminal serving every faction (which faction is resolved per interaction from
 * the <em>player's</em> memberships), because per-faction machines are pure content multiplication
 * with no new behaviour. It is a plain {@link Block} with <b>no block entity</b>: everything it
 * needs (the player's factions, standing, the faction's trade table) lives in the SavedData stores
 * and the datapack definitions, so the block itself is stateless.
 *
 * <p><b>No custom art exists in 0.1.0</b> — the terminal's model reuses vanilla lodestone textures
 * via texture references (see {@code assets/nerofactions/models/block/trade_terminal.json}); this
 * is a deliberate scope decision, recorded for the changelog.
 */
public final class NeroFactionsBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroFactionsCommon.MOD_ID);

    /** The faction trade terminal: one shared block, every faction's counter. */
    public static final RegistryEntry<Block> TRADE_TERMINAL = BLOCKS.register("trade_terminal",
            key -> new TradeTerminalBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    private NeroFactionsBlocks() {
    }

    /** Classload-forcing no-op: touching this class runs the static registrations above. */
    public static void init() {
    }
}
