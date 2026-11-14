package za.co.neroland.nerofactions.content.faction.freecolonists;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

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
        var user = context.getPlayer();
        // Membership is only known server-side; the client predicts the member path and the server corrects it.
        boolean member = user instanceof ServerPlayer settler ? settlerMember(settler) : user != null;
        var tool = item(Head.forUse(member, user != null && user.isShiftKeyDown(),
                state.is(BlockTags.MINEABLE_WITH_AXE), state.is(BlockTags.MINEABLE_WITH_HOE)));
        //? if >=26.3 {
        /*var transformer = tool.getDefaultInstance().get(net.minecraft.core.component.DataComponents.BLOCK_TRANSFORMER);
        return transformer == null ? InteractionResult.PASS : transformer.value().transformBlock(context);
        *///?} else {
        return tool.useOn(context);
        //?}
    }

    private static Item head(BlockState state) {
        return item(Head.forTarget(state.is(BlockTags.MINEABLE_WITH_AXE), state.is(BlockTags.MINEABLE_WITH_HOE)));
    }

    private static Item item(Head head) {
        return switch (head) {
            case AXE -> Items.IRON_AXE;
            case HOE -> Items.IRON_HOE;
            case SHOVEL -> Items.IRON_SHOVEL;
        };
    }

    private static boolean settlerMember(ServerPlayer player) {
        var server = player.level().getServer();
        var faction = OutpostFaction.FREE_COLONISTS.factionId();
        var playerId = player.getUUID();
        return server != null && !player.isSpectator()
                && FactionMembership.membershipOf(server, playerId).contains(faction)
                && GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(faction),
                        ReputationApi.getReputation(playerId, faction), FactionTier.MEMBER);
    }

    enum Head {
        AXE, HOE, SHOVEL;

        static Head forTarget(boolean wood, boolean crop) {
            return wood ? AXE : crop ? HOE : SHOVEL;
        }

        /** Right-click head: Free Colonists members get the auto-switch (sneak forces the hoe); everyone else a plain shovel. */
        static Head forUse(boolean member, boolean sneaking, boolean wood, boolean crop) {
            if (!member) return SHOVEL;
            return sneaking ? HOE : forTarget(wood, crop);
        }
    }
}
