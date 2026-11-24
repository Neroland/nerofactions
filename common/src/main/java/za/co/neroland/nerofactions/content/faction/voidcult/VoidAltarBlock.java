package za.co.neroland.nerofactions.content.faction.voidcult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class VoidAltarBlock extends BaseEntityBlock {
    private static final MapCodec<VoidAltarBlock> CODEC = BlockCodecs.simple(VoidAltarBlock::new);
    private static final Identifier REACH_ID = Identifier.parse("nerofactions:void_rite_reach");
    private static final AttributeModifier REACH = new AttributeModifier(REACH_ID, 0.75, AttributeModifier.Operation.ADD_VALUE);
    private record Boon(int expiresAt, MobEffectInstance vision) {}
    private static final Map<UUID, Boon> BOONS = new HashMap<>();

    public VoidAltarBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<VoidAltarBlock> codec() {
        return CODEC;
    }

    //? if <26.3 {
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    //?}

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarEntity(pos, state);
    }

    public static final class AltarEntity extends BlockEntity {
        public AltarEntity(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.VOID_ALTAR_ENTITY.get(), pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer member) message(member, "instructions");
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer member) perform(member, stack);
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer member) perform(member, stack);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
    *///?}

    private static void perform(ServerPlayer player, ItemStack stack) {
        if (!eligible(player) || player.getAbilities().instabuild || player.isSpectator()) {
            message(player, "unavailable");
            return;
        }
        if (!Rite.hasCost(player.experienceLevel, stack.is(Items.AMETHYST_SHARD), stack.getCount())) {
            message(player, "instructions");
            return;
        }
        if (hasBoon(player) || player.hasEffect(MobEffects.BLINDNESS)) {
            message(player, "already_active");
            return;
        }
        player.giveExperienceLevels(-Rite.LEVELS);
        stack.shrink(Rite.SHARDS);
        if (Rite.backlash(player.getRandom().nextInt(100), NeroFactionsConfig.VOID_BACKLASH_PERCENT.get())) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160));
            message(player, "backlash");
        } else {
            int duration = NeroFactionsConfig.VOID_RITE_SECONDS.get() * 20;
            MobEffectInstance vision = null;
            if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
                vision = new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, true, false, true);
                player.addEffect(vision);
            }
            BOONS.put(player.getUUID(), new Boon(player.level().getServer().getTickCount() + duration, vision));
            applyBoon(player);
            message(player, "boon");
        }
    }

    public static boolean hasBoon(ServerPlayer player) {
        Boon boon = BOONS.get(player.getUUID());
        var server = player.level().getServer();
        return boon != null && server != null && boon.expiresAt() - server.getTickCount() > 0 && eligible(player);
    }

    public static int remaining(MinecraftServer server, UUID player) {
        var online = server.getPlayerList().getPlayer(player);
        return online == null || !hasBoon(online) ? 0 : BOONS.get(player).expiresAt() - server.getTickCount();
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        BOONS.entrySet().removeIf(entry -> {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && hasBoon(player)) {
                return false;
            }
            clearVision(player, entry.getValue());
            return true;
        });
        for (var player : server.getPlayerList().getPlayers()) {
            if (BOONS.containsKey(player.getUUID())) {
                applyBoon(player);
            } else {
                player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).removeModifier(REACH.id());
                player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).removeModifier(REACH.id());
            }
        }
    }

    public static void forget(MinecraftServer server, UUID player) {
        Boon boon = BOONS.remove(player);
        if (boon != null) {
            clearVision(server == null ? null : server.getPlayerList().getPlayer(player), boon);
        }
    }

    public static void onLogout(ServerPlayer player) {
        Boon boon = BOONS.remove(player.getUUID());
        if (boon != null) {
            clearVision(player, boon);
        }
    }

    public static void clear() {
        BOONS.clear();
    }

    private static void applyBoon(ServerPlayer player) {
        player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).addOrUpdateTransientModifier(REACH);
        player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).addOrUpdateTransientModifier(REACH);
        player.removeEffect(MobEffects.WITHER);
    }

    private static void clearVision(ServerPlayer player, Boon boon) {
        if (player != null && boon.vision() != null && player.getEffect(MobEffects.NIGHT_VISION) == boon.vision()
                && boon.vision().isAmbient()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    private static boolean eligible(ServerPlayer player) {
        var server = player.level().getServer();
        var faction = OutpostFaction.VOID_CULT.factionId();
        return server != null && NeroFactionsConfig.VOID_ALTAR_ENABLED.get()
                && FactionMembership.membershipOf(server, player.getUUID()).contains(faction)
                && GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(faction),
                        ReputationApi.getReputation(player.getUUID(), faction), FactionTier.MEMBER);
    }

    private static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.nerofactions.void_altar." + key));
    }

    static final class Rite {
        static final int LEVELS = 3;
        static final int SHARDS = 4;

        private Rite() {
        }

        static boolean hasCost(int levels, boolean reagent, int count) {
            return levels >= LEVELS && reagent && count >= SHARDS;
        }

        static boolean backlash(int roll, int percent) {
            return roll < percent;
        }
    }
}
