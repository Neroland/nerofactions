package za.co.neroland.nerofactions.content.faction.salvagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.crafting.GatedRecipeCheck;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

public final class SalvageScanner extends Item {
    private static final int COOLDOWN = 100;
    private static final int CHUNK_RADIUS = 8;
    private static final TagKey<Structure> TARGETS = TagKey.create(Registries.STRUCTURE,
            Identifier.parse("nerofactions:salvage_targets"));

    public SalvageScanner(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    //? if >=1.21.2 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
    //?} else {
    /*public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    *///?}
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel world && player instanceof ServerPlayer salvager) {
            scan(world, salvager, stack);
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        *///?}
    }

    private void scan(ServerLevel world, ServerPlayer player, ItemStack stack) {
        var server = world.getServer();
        var faction = OutpostFaction.SALVAGERS.factionId();
        if (player.isSpectator() || !FactionMembership.membershipOf(server, player.getUUID()).contains(faction)
                || !GatedRecipeCheck.standingMet(FactionDefinitions.factionsForServer(server).get(faction),
                        ReputationApi.getReputation(player.getUUID(), faction), FactionTier.MEMBER)) {
            report(player, "unavailable");
            return;
        }
        //? if >=1.21.2 {
        if (player.getCooldowns().isOnCooldown(stack)) return;
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        //?} else {
        /*if (player.getCooldowns().isOnCooldown(this)) return;
        player.getCooldowns().addCooldown(this, COOLDOWN);
        *///?}
        BlockPos origin = player.blockPosition();
        BlockPos target = nearestLoaded(world, origin);
        if (target == null) {
            report(player, "none");
            return;
        }
        int dx = target.getX() - origin.getX();
        int dz = target.getZ() - origin.getZ();
        report(player, "signal", Component.translatable("message.nerofactions.scanner." + Bearing.direction(dx, dz)),
                Bearing.roughDistance(dx, dz));
    }

    private static BlockPos nearestLoaded(ServerLevel world, BlockPos origin) {
        int centerX = origin.getX() >> 4;
        int centerZ = origin.getZ() >> 4;
        var registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var targets = registry.get(TARGETS).stream().flatMap(set -> set.stream())
                .map(holder -> holder.value()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        BlockPos best = null;
        long distance = Long.MAX_VALUE;
        for (int x = centerX - CHUNK_RADIUS; x <= centerX + CHUNK_RADIUS; x++) {
            for (int z = centerZ - CHUNK_RADIUS; z <= centerZ + CHUNK_RADIUS; z++) {
                var chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var entry : chunk.getAllStarts().entrySet()) {
                    if (!entry.getValue().isValid() || !targets.contains(entry.getKey())) continue;
                    var target = entry.getValue().getBoundingBox().getCenter();
                    long dx = (long) target.getX() - origin.getX();
                    long dz = (long) target.getZ() - origin.getZ();
                    long squared = dx * dx + dz * dz;
                    if (squared < distance) {
                        best = target;
                        distance = squared;
                    }
                }
            }
        }
        return best;
    }

    private static void report(ServerPlayer player, String key, Object... args) {
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable("message.nerofactions.scanner." + key, args)));
    }

    static final class Bearing {
        private static final String[] DIRECTIONS = {
            "east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"
        };

        static String direction(int dx, int dz) {
            if (dx == 0 && dz == 0) return "here";
            return DIRECTIONS[Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8)];
        }

        static int roughDistance(int dx, int dz) {
            return 32 * (int) Math.round(Math.hypot(dx, dz) / 32);
        }
    }
}
