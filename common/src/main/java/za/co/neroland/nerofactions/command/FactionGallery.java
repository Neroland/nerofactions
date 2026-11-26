package za.co.neroland.nerofactions.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.faction.FactionArmor;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.content.faction.OutpostResident;
import za.co.neroland.nerofactions.cosmetics.FactionBanners;
import za.co.neroland.nerofactions.platform.VersionCompat;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerofactions.registry.NeroFactionsEntities;

final class FactionGallery {
    private static final String DISPLAY = "nerofactions.gallery";
    private static final String MARKER = "nerofactions.gallery.origin";
    private static final int WIDTH = 42;
    private static final int DEPTH = 5;

    private FactionGallery() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("gallery").requires(VersionCompat.gamemasters())
                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin"))))
                .then(Commands.literal("clear").then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .executes(ctx -> clear(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin")))));
    }

    private static int build(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        if (!loaded(level, origin)) {
            source.sendFailure(Component.translatable("command.nerofactions.gallery.unavailable"));
            return 0;
        }
        for (BlockPos pos : volume(origin)) {
            if (!level.getBlockState(pos).isAir()) {
                source.sendFailure(Component.translatable("command.nerofactions.gallery.occupied"));
                return 0;
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        var marker = new ArmorStand(level, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
        marker.setInvisible(true);
        protect(marker);
        marker.setNoGravity(true);
        marker.addTag(DISPLAY);
        marker.addTag(MARKER);
        level.addFreshEntity(marker);
        var definitions = FactionDefinitions.factionsForServer(level.getServer());
        int index = 0;
        for (OutpostFaction faction : OutpostFaction.values()) {
            if (faction == OutpostFaction.NONE) continue;
            BlockPos booth = origin.offset(index++ * 6, 1, 0);
            var definition = definitions.get(faction.factionId());
            if (definition == null) continue;
            level.setBlockAndUpdate(booth.offset(0, 0, 3), NeroFactionsBlocks.FACTION_REGISTRY.get().defaultBlockState());
            level.setBlockAndUpdate(booth.offset(1, 0, 3), NeroFactionsBlocks.CONTRACT_BOARD.get().defaultBlockState());
            level.setBlockAndUpdate(booth.offset(2, 0, 3), NeroFactionsBlocks.TRADE_TERMINAL.get().defaultBlockState());
            ItemStack banner = FactionBanners.build(level.registryAccess(), definition);
            if (banner.getItem() instanceof BlockItem bannerItem) {
                BlockPos bannerPos = booth.offset(3, 0, 3);
                level.setBlockAndUpdate(bannerPos, bannerItem.getBlock().defaultBlockState());
                var bannerEntity = level.getBlockEntity(bannerPos);
                if (bannerEntity != null) {
                    //? if >=1.20.5 {
                    bannerEntity.applyComponentsFromItemStack(banner);
                    //?} else {
                    /*var tag = bannerEntity.getUpdateTag();
                    var patterns = banner.getTagElement("BlockEntityTag");
                    if (patterns != null && patterns.contains("Patterns")) {
                        tag.put("Patterns", patterns.get("Patterns").copy());
                    }
                    tag.putString("CustomName", Component.Serializer.toJson(banner.getHoverName()));
                    bannerEntity.load(tag);
                    *///?}
                    bannerEntity.setChanged();
                    level.sendBlockUpdated(bannerPos, level.getBlockState(bannerPos), level.getBlockState(bannerPos), 3);
                }
            }
            var machine = switch (faction) {
                case MINER_UNION -> NeroFactionsBlocks.QUOTA_BOARD.get();
                case SPACE_GUILD -> NeroFactionsBlocks.NAVIGATION_BEACON.get();
                case VOID_CULT -> NeroFactionsBlocks.VOID_ALTAR.get();
                case SALVAGERS -> NeroFactionsBlocks.DECONSTRUCTOR.get();
                case FREE_COLONISTS -> NeroFactionsBlocks.COMMUNITY_WORKBENCH.get();
                case TERRAFORMING_AUTHORITY -> NeroFactionsBlocks.ATMOSPHERE_PROCESSOR.get();
                case NERO_CORPORATION -> NeroFactionsBlocks.CORPORATE_TERMINAL.get();
                default -> null;
            };
            if (machine != null) level.setBlockAndUpdate(booth.offset(4, 0, 3), machine.defaultBlockState());
            addResident(level, booth.offset(1, 0, 1), faction, true);
            addResident(level, booth.offset(3, 0, 1), faction, false);
            for (var set : FactionArmor.values()) {
                if (set.factionId().equals(faction.factionId())) {
                    addArmorStand(level, booth.offset(5, 0, 2), set);
                }
            }
        }
        source.sendSuccess(() -> Component.translatable("command.nerofactions.gallery.built"), false);
        return 1;
    }

    private static void addResident(ServerLevel level, BlockPos pos, OutpostFaction faction, boolean envoy) {
        OutpostResident resident = envoy ? new za.co.neroland.nerofactions.content.faction.FactionEnvoy(NeroFactionsEntities.ENVOY.get(), level)
                : new za.co.neroland.nerofactions.content.faction.FactionGuard(NeroFactionsEntities.GUARD.get(), level);
        resident.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        resident.setYRot(180);
        resident.displayInGallery(faction);
        protect(resident);
        resident.addTag(DISPLAY);
        level.addFreshEntity(resident);
    }

    private static void addArmorStand(ServerLevel level, BlockPos pos, FactionArmor set) {
        var stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setYRot(180);
        protect(stand);
        stand.addTag(DISPLAY);
        var slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
        for (int i = 0; i < slots.size(); i++) {
            stand.setItemSlot(slots.get(i), new ItemStack(set.pieces().get(i).get()));
        }
        level.addFreshEntity(stand);
    }

    private static int clear(CommandSourceStack source, BlockPos origin) {
        ServerLevel level = source.getLevel();
        if (!loaded(level, origin) || !level.getBlockState(origin).is(Blocks.LODESTONE)
                || level.getEntitiesOfClass(ArmorStand.class, new AABB(origin.above()),
                        entity -> tagged(entity, MARKER)).isEmpty()) {
            source.sendFailure(Component.translatable("command.nerofactions.gallery.unavailable"));
            return 0;
        }
        var bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + WIDTH, origin.getY() + 4, origin.getZ() + DEPTH);
        level.getEntities((Entity) null, bounds, entity -> tagged(entity, DISPLAY)).forEach(Entity::discard);
        for (BlockPos pos : displayBlocks(origin)) {
            var state = level.getBlockState(pos);
            boolean floor = pos.getY() == origin.getY();
            if ((floor && (state.is(Blocks.SMOOTH_STONE) || pos.equals(origin) && state.is(Blocks.LODESTONE)))
                    || (!floor && (state.getBlock() instanceof net.minecraft.world.level.block.BannerBlock
                        || state.is(NeroFactionsBlocks.FACTION_REGISTRY.get())
                        || state.is(NeroFactionsBlocks.CONTRACT_BOARD.get())
                        || state.is(NeroFactionsBlocks.TRADE_TERMINAL.get())
                        || state.is(NeroFactionsBlocks.QUOTA_BOARD.get())
                        || state.is(NeroFactionsBlocks.NAVIGATION_BEACON.get())
                        || state.is(NeroFactionsBlocks.VOID_ALTAR.get())
                        || state.is(NeroFactionsBlocks.DECONSTRUCTOR.get())
                        || state.is(NeroFactionsBlocks.COMMUNITY_WORKBENCH.get())
                        || state.is(NeroFactionsBlocks.ATMOSPHERE_PROCESSOR.get())
                        || state.is(NeroFactionsBlocks.CORPORATE_TERMINAL.get())))) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        source.sendSuccess(() -> Component.translatable("command.nerofactions.gallery.cleared"), false);
        return 1;
    }

    private static boolean tagged(Entity entity, String tag) {
        //? if >=26.1 {
        return entity.entityTags().contains(tag);
        //?} else {
        /*return entity.getTags().contains(tag);
        *///?}
    }

    private static void protect(Entity entity) {
        //? if >=26.3 {
        /*entity.setPermanentlyInvulnerable(true);
        *///?} else {
        entity.setInvulnerable(true);
        //?}
    }

    private static List<BlockPos> displayBlocks(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosed(origin, origin.offset(WIDTH - 1, 0, DEPTH - 1))
                .forEach(pos -> positions.add(pos.immutable()));
        for (int x = 0; x < WIDTH; x++) {
            if (x % 6 < 5) positions.add(origin.offset(x, 1, 3));
        }
        return positions;
    }

    private static Iterable<BlockPos> volume(BlockPos origin) {
        return BlockPos.betweenClosed(origin, origin.offset(WIDTH - 1, 3, DEPTH - 1));
    }

    private static boolean loaded(ServerLevel level, BlockPos origin) {
        if (level.isOutsideBuildHeight(origin) || level.isOutsideBuildHeight(origin.above(3))) return false;
        int maxX = (origin.getX() + WIDTH - 1) >> 4;
        int maxZ = (origin.getZ() + DEPTH - 1) >> 4;
        for (int x = origin.getX() >> 4; x <= maxX; x++) {
            for (int z = origin.getZ() >> 4; z <= maxZ; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }
}
