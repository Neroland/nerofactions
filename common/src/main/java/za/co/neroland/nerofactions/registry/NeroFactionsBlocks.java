package za.co.neroland.nerofactions.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.block.TradeTerminalBlock;
import za.co.neroland.nerofactions.content.faction.minerunion.QuotaBoardBlock;
import za.co.neroland.nerofactions.content.faction.spaceguild.NavigationBeaconBlock;
import za.co.neroland.nerofactions.content.faction.voidcult.VoidAltarBlock;
import za.co.neroland.nerofactions.content.faction.salvagers.DeconstructorBlock;
import za.co.neroland.nerofactions.content.faction.freecolonists.CommunityWorkbenchBlock;
import za.co.neroland.nerofactions.content.faction.corporation.CorporateTerminalBlock;
import za.co.neroland.nerofactions.content.faction.terraformingauthority.AtmosphereProcessorBlock;
import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;

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

    public static final RegistryEntry<Block> FACTION_REGISTRY = BLOCKS.register("faction_registry",
            key -> new za.co.neroland.nerofactions.block.FactionRegistryBlock(BlockBehaviour.Properties.of()
                    .setId(key).mapColor(MapColor.COLOR_CYAN).strength(3.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryEntry<Block> CONTRACT_BOARD = BLOCKS.register("contract_board",
            key -> new za.co.neroland.nerofactions.block.ContractBoardBlock(BlockBehaviour.Properties.of()
                    .setId(key).mapColor(MapColor.COLOR_CYAN).strength(3.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryEntry<Block> QUOTA_BOARD = BLOCKS.register("quota_board",
            key -> new QuotaBoardBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryEntry<Block> NAVIGATION_BEACON = BLOCKS.register("navigation_beacon",
            key -> new NavigationBeaconBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BLUE)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).lightLevel(state -> 12)));

    public static final RegistryEntry<Block> VOID_ALTAR = BLOCKS.register("void_altar",
            key -> new VoidAltarBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F, 8.0F).sound(SoundType.AMETHYST).lightLevel(state -> 6)));

    public static final RegistryEntry<Block> DECONSTRUCTOR = BLOCKS.register("deconstructor",
            key -> new DeconstructorBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_GRAY)
                    .strength(4.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryEntry<Block> COMMUNITY_WORKBENCH = BLOCKS.register("community_workbench",
            key -> new CommunityWorkbenchBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.WOOD)
                    .strength(2.5F, 6.0F).sound(SoundType.WOOD)));

    public static final RegistryEntry<Block> ATMOSPHERE_PROCESSOR = BLOCKS.register("atmosphere_processor",
            key -> new AtmosphereProcessorBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL)));

    public static final RegistryEntry<Block> CORPORATE_TERMINAL = BLOCKS.register("corporate_terminal",
            key -> new CorporateTerminalBlock(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 6.0F).sound(SoundType.METAL)));

    private static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroFactionsCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<QuotaBoardBlock.BoardEntity>> QUOTA_BOARD_ENTITY =
            BLOCK_ENTITIES.register("quota_board",
                    key -> BlockEntityTypeCompat.create(QuotaBoardBlock.BoardEntity::new, QUOTA_BOARD.get()));

    public static final RegistryEntry<BlockEntityType<NavigationBeaconBlock.BeaconEntity>> NAVIGATION_BEACON_ENTITY =
            BLOCK_ENTITIES.register("navigation_beacon",
                    key -> BlockEntityTypeCompat.create(NavigationBeaconBlock.BeaconEntity::new, NAVIGATION_BEACON.get()));

    public static final RegistryEntry<BlockEntityType<VoidAltarBlock.AltarEntity>> VOID_ALTAR_ENTITY =
            BLOCK_ENTITIES.register("void_altar",
                    key -> BlockEntityTypeCompat.create(VoidAltarBlock.AltarEntity::new, VOID_ALTAR.get()));

    public static final RegistryEntry<BlockEntityType<DeconstructorBlock.MachineEntity>> DECONSTRUCTOR_ENTITY =
            BLOCK_ENTITIES.register("deconstructor",
                    key -> BlockEntityTypeCompat.create(DeconstructorBlock.MachineEntity::new, DECONSTRUCTOR.get()));

    public static final RegistryEntry<BlockEntityType<CommunityWorkbenchBlock.Bench>> COMMUNITY_WORKBENCH_ENTITY =
            BLOCK_ENTITIES.register("community_workbench",
                    key -> BlockEntityTypeCompat.create(CommunityWorkbenchBlock.Bench::new, COMMUNITY_WORKBENCH.get()));

    public static final RegistryEntry<BlockEntityType<AtmosphereProcessorBlock.Processor>> ATMOSPHERE_PROCESSOR_ENTITY =
            BLOCK_ENTITIES.register("atmosphere_processor",
                    key -> BlockEntityTypeCompat.create(AtmosphereProcessorBlock.Processor::new, ATMOSPHERE_PROCESSOR.get()));

    public static final RegistryEntry<BlockEntityType<CorporateTerminalBlock.Terminal>> CORPORATE_TERMINAL_ENTITY =
            BLOCK_ENTITIES.register("corporate_terminal",
                    key -> BlockEntityTypeCompat.create(CorporateTerminalBlock.Terminal::new, CORPORATE_TERMINAL.get()));

    private NeroFactionsBlocks() {
    }

    /** Classload-forcing no-op: touching this class runs the static registrations above. */
    public static void init() {
    }
}
