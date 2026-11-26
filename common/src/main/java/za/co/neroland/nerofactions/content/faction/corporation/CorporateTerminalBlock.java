package za.co.neroland.nerofactions.content.faction.corporation;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

public final class CorporateTerminalBlock extends BaseEntityBlock {
    private static final MapCodec<CorporateTerminalBlock> CODEC = BlockCodecs.simple(CorporateTerminalBlock::new);

    public CorporateTerminalBlock(Properties properties) { super(properties); }
    protected MapCodec<CorporateTerminalBlock> codec() { return CODEC; }

    //? if <26.3 {
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    //?}

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Terminal(pos, state); }

    //? if >=1.20.5 {
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
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
        if (player instanceof ServerPlayer customer && level instanceof ServerLevel world) {
            CorporateCounter.deliver(customer, world, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer customer && level instanceof ServerLevel world) {
            CorporateCounter.deliver(customer, world, stack);
        }
        return InteractionResult.SUCCESS;
    }
    //?} else if >=1.20.5 {
    /*@Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer customer && level instanceof ServerLevel world) {
            CorporateCounter.deliver(customer, world, stack);
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?} else {
    /*private InteractionResult useHeldItem(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer customer && level instanceof ServerLevel world) {
            CorporateCounter.deliver(customer, world, stack);
        }
        return InteractionResult.SUCCESS;
    }
    *///?}

    public static final class Terminal extends BlockEntity {
        public Terminal(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.CORPORATE_TERMINAL_ENTITY.get(), pos, state);
        }
    }
}
