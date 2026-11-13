package za.co.neroland.nerofactions.content.faction.spaceguild;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

public final class NavigationBeaconBlock extends BaseEntityBlock {
    private static final MapCodec<NavigationBeaconBlock> CODEC = BlockCodecs.simple(NavigationBeaconBlock::new);

    public NavigationBeaconBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<NavigationBeaconBlock> codec() {
        return CODEC;
    }

    //? if <26.3 {
    /*@Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    *///?}

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeaconEntity(pos, state);
    }

    public static final class BeaconEntity extends BlockEntity {
        public BeaconEntity(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.NAVIGATION_BEACON_ENTITY.get(), pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer visitor) {
            visitor.sendSystemMessage(Component.translatable("message.nerofactions.navigation_beacon.use_compass"));
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.COMPASS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (player instanceof ServerPlayer visitor) bind(visitor, pos, hand, stack);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.COMPASS)) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player instanceof ServerPlayer visitor) bind(visitor, pos, hand, stack);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?}

    private static void bind(ServerPlayer player, BlockPos pos, InteractionHand hand, ItemStack stack) {
        if (!enabledFor(player)) {
            player.sendSystemMessage(Component.translatable("message.nerofactions.navigation_beacon.unavailable"));
            return;
        }
        ItemStack waypoint = stack.copyWithCount(1);
        waypoint.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(player.level().dimension(), pos)), false));
        if (stack.getCount() == 1) {
            player.setItemInHand(hand, waypoint);
        } else if (player.getInventory().add(waypoint)) {
            stack.shrink(1);
        } else {
            player.sendSystemMessage(Component.translatable("message.nerofactions.navigation_beacon.no_space"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.nerofactions.navigation_beacon.bound"));
    }

    private static boolean enabledFor(ServerPlayer player) {
        var server = player.level().getServer();
        return server != null && NeroFactionsConfig.NAVIGATION_BEACON_ENABLED.get()
                && FactionMembership.membershipOf(server, player.getUUID()).contains(OutpostFaction.SPACE_GUILD.factionId());
    }

    public static boolean protects(ServerPlayer player) {
        if (!enabledFor(player)) {
            return false;
        }
        var server = player.level().getServer();
        ServerLevel level = server.getLevel(player.level().dimension());
        if (level == null) {
            return false;
        }
        BlockPos center = player.blockPosition();
        for (int x = (center.getX() - 24) >> 4; x <= ((center.getX() + 24) >> 4); x++) {
            for (int z = (center.getZ() - 24) >> 4; z <= ((center.getZ() + 24) >> 4); z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof BeaconEntity && !entity.isRemoved()
                            && withinRange(entity.getBlockPos().distSqr(center))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean withinRange(double distanceSquared) {
        return distanceSquared <= 24 * 24;
    }
}
