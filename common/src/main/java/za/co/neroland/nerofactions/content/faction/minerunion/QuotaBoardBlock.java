package za.co.neroland.nerofactions.content.faction.minerunion;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerofactions.reputation.ReputationSources;
import za.co.neroland.nerolandcore.economy.CoreCurrencies;
import za.co.neroland.nerolandcore.economy.CurrencyApi;
import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class QuotaBoardBlock extends BaseEntityBlock {
    private static final MapCodec<QuotaBoardBlock> CODEC = BlockCodecs.simple(QuotaBoardBlock::new);
    private static final Identifier FACTION = OutpostFaction.MINER_UNION.factionId();
    private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, Identifier.parse("nerofactions:quota_ores"));
    private static final TagKey<Item> INGOTS = TagKey.create(Registries.ITEM, Identifier.parse("nerofactions:quota_ingots"));
    private static final TagKey<Item> RAW_BLOCKS = TagKey.create(Registries.ITEM, Identifier.parse("nerofactions:quota_raw_blocks"));
    private static final int MAX_ITEMS_PER_USE = 16;

    public QuotaBoardBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<QuotaBoardBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoardEntity(pos, state);
    }

    public static final class BoardEntity extends BlockEntity {
        public BoardEntity(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.QUOTA_BOARD_ENTITY.get(), pos, state);
        }
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
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
        if (player instanceof ServerPlayer online) {
            transact(online, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer online) {
            transact(online, stack);
        }
        return InteractionResult.SUCCESS;
    }
    //?} else if >=1.20.5 {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer online) {
            transact(online, stack);
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?} else {
    /*private InteractionResult useHeldItem(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer online) {
            transact(online, stack);
        }
        return InteractionResult.SUCCESS;
    }
    *///?}

    private static void transact(ServerPlayer player, ItemStack stack) {
        if (!NeroFactionsConfig.QUOTA_BOARD_ENABLED.get()) {
            message(player, "disabled");
            return;
        }
        var server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        var id = player.getUUID();
        var faction = FactionDefinitions.factionsForServer(server).get(FACTION);
        if (faction == null || !FactionMembership.membershipOf(server, id).contains(FACTION)) {
            message(player, "not_member");
            return;
        }

        long week = QuotaState.week(System.currentTimeMillis());
        int limit = QuotaState.limit(NeroFactionsConfig.QUOTA_BASE_TONNES.get(),
                FactionTiers.tierOf(faction, ReputationApi.getReputation(id, FACTION)));
        var quota = QuotaState.get(server);
        if (stack.isEmpty()) {
            message(player, "status", quota.contributed(id, week), limit);
            return;
        }
        int weight = stack.is(RAW_BLOCKS) ? 9 : stack.is(INGOTS) || stack.is(ORES) ? 1 : 0;
        if (weight == 0 || stack.isDamaged()) {
            message(player, "wrong_item");
            return;
        }
        if (player.getAbilities().instabuild) {
            message(player, "survival");
            return;
        }
        int count = quota.accept(id, week, limit, weight, Math.min(stack.getCount(), MAX_ITEMS_PER_USE));
        if (count == 0) {
            message(player, "full");
            return;
        }
        stack.shrink(count);
        player.getInventory().setChanged();
        int tonnes = count * weight;
        int standing = ReputationSources.award(server, id, FACTION, ReputationSources.Source.TRADE, tonnes);
        boolean hasCredits = CurrencyApi.hasRealProvider();
        long credits = tonnes * 2L;
        boolean creditPaid = hasCredits && CurrencyApi.deposit(id, CoreCurrencies.CREDITS, credits,
                NeroFactionsCommon.MOD_ID, "miner_union_quota");
        message(player, "deposited", tonnes, quota.contributed(id, week), limit, standing, creditPaid ? credits : 0);
        if (hasCredits && !creditPaid) {
            message(player, "credit_refused");
        }
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.nerofactions.quota." + key, args));
    }
}
