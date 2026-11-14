package za.co.neroland.nerofactions.content.faction.freecolonists;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlerMultitool extends Item {
    public SettlerMultitool(Properties properties) {
        super(properties.durability(300));
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Item tool = head(state);
        return tool.getDestroySpeed(tool.getDefaultInstance(), state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        Item tool = head(state);
        return tool.isCorrectToolForDrops(tool.getDefaultInstance(), state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) > 0) {
            stack.hurtAndBreak(1, miner, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        var tool = context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? Items.IRON_HOE : head(state);
        //? if >=26.3 {
        /*var transformer = tool.getDefaultInstance().get(net.minecraft.core.component.DataComponents.BLOCK_TRANSFORMER);
        return transformer == null ? InteractionResult.PASS : transformer.value().transformBlock(context);
        *///?} else {
        return tool.useOn(context);
        //?}
    }

    private static Item head(BlockState state) {
        return switch (Head.forTarget(state.is(BlockTags.MINEABLE_WITH_AXE), state.is(BlockTags.MINEABLE_WITH_HOE))) {
            case AXE -> Items.IRON_AXE;
            case HOE -> Items.IRON_HOE;
            case SHOVEL -> Items.IRON_SHOVEL;
        };
    }

    enum Head {
        AXE, HOE, SHOVEL;

        static Head forTarget(boolean wood, boolean crop) {
            return wood ? AXE : crop ? HOE : SHOVEL;
        }
    }
}
