package za.co.neroland.nerofactions.content.faction.terraformingauthority;

import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.platform.BuildPermission;
import za.co.neroland.nerofactions.platform.Services;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerolandcore.reputation.ReputationApi;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideMode;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

public final class AtmosphereProcessorBlock extends BaseEntityBlock {
    private static final MapCodec<AtmosphereProcessorBlock> CODEC = BlockCodecs.simple(AtmosphereProcessorBlock::new);
    private static final int ENERGY_PER_PULSE = 120;

    /**
     * The loader's break-event seam, so claim and protection mods can refuse each target. Resolved
     * when the block is constructed (registration, i.e. mod init), never lazily on a tick path; while
     * unresolved every target is refused.
     */
    private static volatile BuildPermission permission;

    public AtmosphereProcessorBlock(Properties properties) {
        super(properties);
        if (permission == null) permission = Services.load(BuildPermission.class);
    }
    protected MapCodec<AtmosphereProcessorBlock> codec() { return CODEC; }

    //? if <26.3 {
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    //?}

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Processor(pos, state); }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            InteractionResult handled = useHeldItem(held, state, level, pos, player, hand, hit);
            if (handled != InteractionResult.PASS) {
                return handled;
            }
        }
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
    *///?}
        if (player instanceof ServerPlayer visitor && level.getBlockEntity(pos) instanceof Processor machine) {
            message(visitor, "status", machine.getEnergy().getAmount(), radius(machine.pulses,
                    NeroFactionsConfig.ATMOSPHERE_RADIUS.get()));
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer visitor && level.getBlockEntity(pos) instanceof Processor machine) {
            machine.pulse(visitor, stack);
        }
        return InteractionResult.SUCCESS;
    }
    //?} else if >=1.20.5 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer visitor && level.getBlockEntity(pos) instanceof Processor machine) {
            machine.pulse(visitor, stack);
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?} else {
    /*private InteractionResult useHeldItem(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer visitor && level.getBlockEntity(pos) instanceof Processor machine) {
            machine.pulse(visitor, stack);
        }
        return InteractionResult.SUCCESS;
    }
    *///?}

    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.nerofactions.atmosphere_processor." + key, args));
    }

    static int radius(int pulses, int limit) { return Math.min(Math.clamp(limit, 1, 8), 1 + Math.max(0, pulses) / 4); }

    /**
     * The conversion table, keyed by block registry id so it stays testable without a bootstrapped
     * registry. Only natural loose ground converts: stone, cobblestone, planks, bricks and every other
     * material players build with are deliberately absent, so a pulse cannot eat a neighbour's floor,
     * wall or roof. Returns null when the substrate does not convert.
     */
    static Output conversion(String substrateId, boolean seeds) {
        return switch (substrateId) {
            case "minecraft:dirt" -> seeds ? Output.GRASS_BLOCK : Output.PODZOL;
            case "minecraft:sand", "minecraft:gravel" -> Output.MOSS_BLOCK;
            case "minecraft:grass_block", "minecraft:podzol" -> seeds ? Output.OAK_SAPLING : null;
            default -> null;
        };
    }

    /**
     * Plans one target: the conversion for the substrate, provided the space above is clear, the
     * world-side checks pass ({@code placeable}) and the loader's break event lets the player modify
     * it ({@code permitted}). The permission seam is only asked for a real candidate, after the cheap
     * checks, and a refusal returns null, so nothing is converted and no input or energy is spent.
     */
    static Output plan(String substrateId, boolean seeds, boolean airAbove,
            Predicate<Output> placeable, Predicate<Output> permitted) {
        Output output = conversion(substrateId, seeds);
        if (output == null || !airAbove || !placeable.test(output) || !permitted.test(output)) return null;
        return output;
    }

    /** True only when the loader seam is resolved and lets {@code player} modify {@code pos}. */
    private static boolean permitted(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState current) {
        BuildPermission seam = permission;
        return seam != null && seam.mayModify(level, player, pos, current);
    }

    /** What a pulse can turn a substrate into. Saplings are placed on top; the rest replace the substrate. */
    enum Output {
        GRASS_BLOCK, PODZOL, MOSS_BLOCK, OAK_SAPLING;

        boolean onTop() { return this == OAK_SAPLING; }

        Block block() {
            return switch (this) {
                case GRASS_BLOCK -> Blocks.GRASS_BLOCK;
                case PODZOL -> Blocks.PODZOL;
                case MOSS_BLOCK -> Blocks.MOSS_BLOCK;
                case OAK_SAPLING -> Blocks.OAK_SAPLING;
            };
        }
    }

    public static final class Processor extends AbstractMachineBlockEntity {
        private int pulses;
        private long nextPulse;

        public Processor(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.ATMOSPHERE_PROCESSOR_ENTITY.get(), pos, state, 12_000, 120, 0, stack -> null);
            installSideConfig(SideConfig.builder().channel(Channel.ENERGY)
                    .allow(Channel.ENERGY, SideMode.OUTPUT, false).allow(Channel.ENERGY, SideMode.IO, false)
                    .defaultPreset(SidePreset.ALL_INPUT).build());
        }

        private void pulse(ServerPlayer player, ItemStack input) {
            if (!(level instanceof ServerLevel world)) return;
            var server = world.getServer();
            var faction = OutpostFaction.TERRAFORMING_AUTHORITY.factionId();
            if (!NeroFactionsConfig.ATMOSPHERE_PROCESSOR_ENABLED.get() || player.getAbilities().instabuild
                    || !player.getAbilities().mayBuild || player.isSpectator()
                    || !FactionMembership.membershipOf(server, player.getUUID()).contains(faction)
                    || !GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(faction),
                            ReputationApi.getReputation(player.getUUID(), faction), FactionTier.MEMBER)) {
                message(player, "denied");
                return;
            }
            boolean seeds = input.is(Items.WHEAT_SEEDS);
            if (!seeds && !input.is(Items.BONE_MEAL)) { message(player, "reagent"); return; }
            if (world.getGameTime() < nextPulse) { message(player, "cooldown"); return; }
            nextPulse = world.getGameTime() + 20;
            if (!energyBuffer().has(ENERGY_PER_PULSE)) { message(player, "power"); return; }
            int radius = radius(pulses, NeroFactionsConfig.ATMOSPHERE_RADIUS.get());
            for (BlockPos column : BlockPos.spiralAround(worldPosition, radius, Direction.EAST, Direction.SOUTH)) {
                if (column.getX() == worldPosition.getX() && column.getZ() == worldPosition.getZ()) continue;
                if (!world.hasChunkAt(column)) continue;
                int surface = world.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) - 1;
                if (Math.abs(surface - worldPosition.getY()) > 2) continue;
                BlockPos substrate = new BlockPos(column.getX(), surface, column.getZ());
                BlockState substrateState = world.getBlockState(substrate);
                Output output = plan(BuiltInRegistries.BLOCK.getKey(substrateState.getBlock()).toString(), seeds,
                        world.getBlockState(substrate.above()).isAir(),
                        out -> {
                            BlockPos target = out.onTop() ? substrate.above() : substrate;
                            return world.getWorldBorder().isWithinBounds(target) && world.mayInteract(player, target)
                                    && player.mayUseItemAt(target, Direction.UP, input)
                                    && world.getBlockEntity(target) == null
                                    && out.block().defaultBlockState().canSurvive(world, target);
                        },
                        out -> {
                            // Vet the column's ground through the loader's break event (claim mods
                            // listen there), and the air above too when a sapling goes on top.
                            if (!permitted(world, player, substrate, substrateState)) return false;
                            BlockPos above = substrate.above();
                            return !out.onTop() || permitted(world, player, above, world.getBlockState(above));
                        });
                if (output == null) continue;
                BlockPos target = output.onTop() ? substrate.above() : substrate;
                if (!world.setBlockAndUpdate(target, output.block().defaultBlockState())) continue;
                input.shrink(1);
                player.getInventory().setChanged();
                energyBuffer().consume(ENERGY_PER_PULSE);
                pulses = Math.min(28, pulses + 1);
                setChanged();
                message(player, "converted", output.block().getName(), radius);
                return;
            }
            message(player, "no_target");
        }

        //? if >=1.21.6 {
        @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
            super.saveAdditional(output);
            output.putInt("Pulses", pulses);
        }
        @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
            super.loadAdditional(input);
            pulses = Math.clamp(input.getIntOr("Pulses", 0), 0, 28);
        }
        //?} else {
        /*@Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("Pulses", pulses);
        }
        @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            pulses = Math.clamp(za.co.neroland.nerolandcore.data.compat.ValueInput.of(tag, registries).getIntOr("Pulses", 0), 0, 28);
        }
        *///?}
    }
}
