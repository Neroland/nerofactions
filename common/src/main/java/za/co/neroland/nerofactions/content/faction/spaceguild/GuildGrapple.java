package za.co.neroland.nerofactions.content.faction.spaceguild;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class GuildGrapple extends Item {
    private static final int COOLDOWN = 60;
    private static final double RANGE = 12;

    public GuildGrapple(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    //? if >=1.21.2 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    *///?}
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer traveller) {
            useGrapple(traveller, stack);
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        *///?}
    }

    private void useGrapple(ServerPlayer player, ItemStack stack) {
        var level = player.level();
        var server = level.getServer();
        var faction = OutpostFaction.SPACE_GUILD.factionId();
        var playerId = player.getUUID();
        if (server == null || player.isSpectator() || player.isPassenger() || player.isFallFlying()
                || !FactionMembership.membershipOf(server, playerId).contains(faction)
                || !GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(faction),
                        ReputationApi.getReputation(playerId, faction), FactionTier.MEMBER)) {
            message(player, "unavailable");
            return;
        }
        //? if >=1.21.2 {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        //?} else {
        /*if (player.getCooldowns().isOnCooldown(this)) return;
        *///?}
        HitResult hit = player.pick(RANGE, 1, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                || !level.hasChunkAt(block.getBlockPos())
                || level.getBlockState(block.getBlockPos()).getCollisionShape(level, block.getBlockPos()).isEmpty()) {
            message(player, "no_anchor");
            return;
        }
        Vec3 pull = Traversal.impulse(hit.getLocation().subtract(player.getEyePosition()));
        if (pull == Vec3.ZERO) {
            message(player, "no_anchor");
            return;
        }
        //? if >=1.21.2 {
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        //?} else {
        /*player.getCooldowns().addCooldown(this, COOLDOWN);
        *///?}
        player.setDeltaMovement(pull);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        message(player, "attached");
    }

    private static void message(ServerPlayer player, String key) {
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.nerofactions.grapple." + key)));
    }

    static final class Traversal {
        private Traversal() {
        }

        static Vec3 impulse(Vec3 towardAnchor) {
            double length = towardAnchor.length();
            if (!Double.isFinite(length) || length < 2 || length > RANGE + 0.01) {
                return Vec3.ZERO;
            }
            return towardAnchor.scale(Math.min(1.55, 0.55 + 0.09 * length) / length);
        }
    }
}
