package za.co.neroland.nerofactions.content.faction.minerunion;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.2 {
import net.minecraft.world.item.ToolMaterial;
//?} else {
/*import net.minecraft.world.item.Tiers;
*///?}
//? if <1.21.5 {
/*import net.minecraft.world.item.PickaxeItem;
*///?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

//? if >=1.21.5 {
public final class UnionBreaker extends Item {
//?} else {
/*public final class UnionBreaker extends PickaxeItem {
*///?}
    private static final Identifier FACTION = OutpostFaction.MINER_UNION.factionId();
    private static final TagKey<Block> EXCAVATABLE = TagKey.create(Registries.BLOCK,
            Identifier.parse("nerofactions:union_excavatable"));
    private static final ThreadLocal<Boolean> EXCAVATING = new ThreadLocal<>();

    public UnionBreaker(Item.Properties properties) {
        //? if >=1.21.5 {
        super(properties.pickaxe(ToolMaterial.DIAMOND, 1.0F, -2.8F));
        //?} else if >=1.21.2 {
        /*super(ToolMaterial.DIAMOND, 1.0F, -2.8F, properties);
        *///?} else {
        /*super(Tiers.DIAMOND, properties.attributes(createAttributes(Tiers.DIAMOND, 1.0F, -2.8F)));
        *///?}
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos origin, LivingEntity miner) {
        boolean mined = super.mineBlock(stack, level, state, origin, miner);
        if (!(level instanceof ServerLevel world) || !(miner instanceof ServerPlayer player)
                || stack.isEmpty() || player.isShiftKeyDown() || player.getAbilities().instabuild
                || Boolean.TRUE.equals(EXCAVATING.get()) || !state.is(EXCAVATABLE)) {
            return mined;
        }
        var server = world.getServer();
        if (server == null || !server.isSameThread()) {
            return mined;
        }
        Direction inward = MiningShape.inward(player.getViewVector(1.0F));
        // NeoForge runs the item hook before it removes the centre block.
        BlockPos center = origin.immutable();
        TickTask task = new TickTask(server.getTickCount(), () -> excavate(world, player, stack, state, center, inward));
        //? if >=1.21.2 {
        server.schedule(task);
        //?} else {
        /*server.tell(task);
        *///?}
        return mined;
    }

    private static void excavate(ServerLevel world, ServerPlayer player, ItemStack stack,
            BlockState original, BlockPos origin, Direction inward) {
        if (player.level() != world || !player.isAlive() || stack.isEmpty() || player.getMainHandItem() != stack
                || !world.hasChunkAt(origin) || world.getBlockState(origin) == original) {
            return;
        }
        var server = world.getServer();
        var id = player.getUUID();
        var faction = FactionDefinitions.factionsForServer(server).get(FACTION);
        if (faction == null || !FactionMembership.membershipOf(server, id).contains(FACTION)) {
            return;
        }
        var tier = FactionTiers.tierOf(faction, ReputationApi.getReputation(id, FACTION));
        EXCAVATING.set(true);
        try {
            for (BlockPos candidate : MiningShape.positions(origin, inward, tier)) {
                if (stack.isEmpty() || player.getMainHandItem() != stack) {
                    break;
                }
                if (canExcavate(world, player, stack, candidate) && player.gameMode.destroyBlock(candidate)
                        && tier == FactionTier.MEMBER && !stack.isEmpty()) {
                    stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                }
            }
        } finally {
            EXCAVATING.remove();
        }
    }

    private static boolean canExcavate(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos) {
        //? if >=1.21.11 {
        if (!player.isWithinBlockInteractionRange(pos, 0.0D)) return false;
        //?} else {
        /*if (!player.canInteractWithBlock(pos, 0.0D)) return false;
        *///?}
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.mayInteract(player, pos) || level.getBlockEntity(pos) != null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(EXCAVATABLE) && stack.isCorrectToolForDrops(state)
                && state.getDestroySpeed(level, pos) >= 0;
    }

    static final class MiningShape {
        static Direction inward(Vec3 look) {
            double x = Math.abs(look.x);
            double y = Math.abs(look.y);
            double z = Math.abs(look.z);
            if (y >= x && y >= z) return look.y >= 0 ? Direction.UP : Direction.DOWN;
            if (x >= z) return look.x >= 0 ? Direction.EAST : Direction.WEST;
            return look.z >= 0 ? Direction.SOUTH : Direction.NORTH;
        }

        static List<BlockPos> positions(BlockPos center, Direction inward, FactionTier tier) {
            if (tier.ordinal() < FactionTier.MEMBER.ordinal()) {
                return List.of();
            }
            int layers = tier.ordinal() >= FactionTier.TRUSTED.ordinal() ? 2 : 1;
            List<BlockPos> blocks = new ArrayList<>(layers * 9 - 1);
            for (int layer = 0; layer < layers; layer++) {
                BlockPos plane = center.relative(inward, layer);
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        if (layer == 0 && a == 0 && b == 0) continue;
                        blocks.add(switch (inward.getAxis()) {
                            case X -> plane.offset(0, a, b);
                            case Y -> plane.offset(a, 0, b);
                            case Z -> plane.offset(a, b, 0);
                        });
                    }
                }
            }
            return blocks;
        }
    }
}
