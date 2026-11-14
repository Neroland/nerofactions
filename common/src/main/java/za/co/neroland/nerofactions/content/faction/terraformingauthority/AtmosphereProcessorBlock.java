package za.co.neroland.nerofactions.content.faction.terraformingauthority;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    public AtmosphereProcessorBlock(Properties properties) { super(properties); }
    protected MapCodec<AtmosphereProcessorBlock> codec() { return CODEC; }

    //? if <26.3 {
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    //?}

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Processor(pos, state); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer visitor && level.getBlockEntity(pos) instanceof Processor machine) {
            machine.pulse(visitor, stack);
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?}

    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.nerofactions.atmosphere_processor." + key, args));
    }

    static int radius(int pulses, int limit) { return Math.min(Math.clamp(limit, 1, 8), 1 + Math.max(0, pulses) / 4); }

    static Block conversion(Block substrate, boolean seeds) {
        if (substrate == Blocks.DIRT) return seeds ? Blocks.GRASS_BLOCK : Blocks.PODZOL;
        if (substrate == Blocks.SAND || substrate == Blocks.GRAVEL || substrate == Blocks.STONE) return Blocks.MOSS_BLOCK;
        if (seeds && (substrate == Blocks.GRASS_BLOCK || substrate == Blocks.PODZOL)) return Blocks.OAK_SAPLING;
        return null;
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
                Block output = conversion(world.getBlockState(substrate).getBlock(), seeds);
                if (output == null || !world.getBlockState(substrate.above()).isAir()) continue;
                BlockPos target = output == Blocks.OAK_SAPLING ? substrate.above() : substrate;
                if (!world.getWorldBorder().isWithinBounds(target) || !world.mayInteract(player, target)
                        || !player.mayUseItemAt(target, Direction.UP, input) || world.getBlockEntity(target) != null
                        || !output.defaultBlockState().canSurvive(world, target)) continue;
                if (!world.setBlockAndUpdate(target, output.defaultBlockState())) continue;
                input.shrink(1);
                player.getInventory().setChanged();
                energyBuffer().consume(ENERGY_PER_PULSE);
                pulses = Math.min(28, pulses + 1);
                setChanged();
                message(player, "converted", output.getName(), radius);
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
