package za.co.neroland.nerofactions.content.faction.freecolonists;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import za.co.neroland.nerolandcore.data.compat.ValueInput;
import za.co.neroland.nerolandcore.data.compat.ValueOutput;
*///?}
import za.co.neroland.nerofactions.platform.VersionCompat;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;
import za.co.neroland.nerofactions.data.WorkbenchAccess;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.menu.MenuOpener;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

public final class CommunityWorkbenchBlock extends BaseEntityBlock {
    private static final MapCodec<CommunityWorkbenchBlock> CODEC = BlockCodecs.simple(CommunityWorkbenchBlock::new);

    public CommunityWorkbenchBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<CommunityWorkbenchBlock> codec() { return CODEC; }

    //? if <26.3 {
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }
    //?}

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Bench(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel world && level.getBlockEntity(pos) instanceof Bench bench) {
            bench.id = UUID.randomUUID();
            if (placer instanceof ServerPlayer player && colonist(player)) {
                WorkbenchAccess.get(world.getServer()).claim(bench.id, player.getUUID());
            }
            bench.setChanged();
        }
    }

    //? if >=1.20.5 {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player user, BlockHitResult hit) {
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player user,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
    *///?}
        if (user instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof Bench bench) {
            if (bench.canUse(player)) bench.open(player);
            else player.sendSystemMessage(Component.translatable("message.nerofactions.workbench.denied"));
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.21.5 {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof Bench bench) bench.dropContents();
        super.onRemove(state, level, pos, replacement, moved);
    }
    *///?}

    private static boolean colonist(ServerPlayer player) {
        var server = player.level().getServer();
        return server != null && !player.isSpectator() && FactionMembership.membershipOf(server, player.getUUID())
                .contains(OutpostFaction.FREE_COLONISTS.factionId());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        var root = Commands.literal("workbench");
        for (String operation : java.util.List.of("grant", "revoke", "recover")) {
            var action = Commands.literal(operation);
            if (operation.equals("recover")) action.requires(VersionCompat.gamemasters());
            root.then(action.then(Commands.argument("bench", BlockPosArgument.blockPos())
                    .then(Commands.argument("colonist", EntityArgument.player()).executes(context -> {
                        var source = context.getSource();
                        var pos = BlockPosArgument.getBlockPos(context, "bench");
                        var level = source.getLevel();
                        var target = EntityArgument.getPlayer(context, "colonist");
                        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Bench bench)
                                || !NeroFactionsConfig.COMMUNITY_WORKBENCH_ENABLED.get()) {
                            source.sendFailure(Component.translatable("message.nerofactions.workbench.denied"));
                            return 0;
                        }
                        var access = WorkbenchAccess.get(level.getServer());
                        boolean changed;
                        if (operation.equals("recover")) {
                            changed = colonist(target) && access.claim(bench.id, target.getUUID());
                        } else {
                            var actor = source.getPlayerOrException();
                            boolean grant = operation.equals("grant");
                            changed = bench.canUse(actor) && (!grant || colonist(target))
                                    && access.setGuest(bench.id, actor.getUUID(), target.getUUID(), grant);
                        }
                        if (!changed) {
                            source.sendFailure(Component.translatable("message.nerofactions.workbench.denied"));
                            return 0;
                        }
                        source.sendSuccess(() -> Component.translatable("message.nerofactions.workbench.updated"), false);
                        return 1;
                    }))));
        }
        return root;
    }

    public static final class Bench extends BlockEntity {
        private UUID id = UUID.randomUUID();
        private final SimpleContainer storage = new SimpleContainer(27) {
            @Override public void setChanged() { super.setChanged(); Bench.this.setChanged(); }
        };

        public Bench(BlockPos pos, BlockState state) {
            super(NeroFactionsBlocks.COMMUNITY_WORKBENCH_ENTITY.get(), pos, state);
        }

        private boolean canUse(Player player) {
            return player instanceof ServerPlayer member && level instanceof ServerLevel world
                    && member.level() == world && !isRemoved() && world.hasChunkAt(worldPosition)
                    && world.getBlockEntity(worldPosition) == this && colonist(member)
                    && member.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5) <= 64
                    && NeroFactionsConfig.COMMUNITY_WORKBENCH_ENABLED.get()
                    && WorkbenchAccess.get(world.getServer()).permits(id, member.getUUID());
        }

        private void open(ServerPlayer player) {
            boolean inventory = player.isShiftKeyDown();
            MenuOpener.open(player, new SimpleMenuProvider((menu, ownerInventory, viewer) -> {
                if (!canUse(viewer)) return null;
                if (inventory) return new ChestMenu(MenuType.GENERIC_9x3, menu, ownerInventory, storage, 3) {
                    @Override public boolean stillValid(Player user) { return canUse(user); }
                };
                return new CraftingMenu(menu, ownerInventory, ContainerLevelAccess.create(level, worldPosition)) {
                    @Override public boolean stillValid(Player user) { return canUse(user); }
                };
            }, Component.translatable(inventory ? "container.nerofactions.workbench.storage" : "block.nerofactions.community_workbench")));
        }

        //? if >=1.21.5 {
        @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            super.preRemoveSideEffects(pos, state);
            dropContents();
        }
        //?}

        private void dropContents() {
            if (level instanceof ServerLevel world) {
                WorkbenchAccess.removeBench(world.getServer(), id);
                Containers.dropContents(world, worldPosition, storage);
            }
        }

        //? if >=1.21.6 {
        @Override protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
        //?} else {
        /*@Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ValueOutput output = ValueOutput.of(tag, registries);
        *///?}
            output.store("Bench", UUIDUtil.CODEC, id);
            //? if >=1.21.6 {
            net.minecraft.world.ContainerHelper.saveAllItems(output, storage.getItems());
            //?} else if >=1.20.3 {
            /*net.minecraft.world.ContainerHelper.saveAllItems(tag, storage.getItems(), registries);
            *///?} else {
            /*var stacks = net.minecraft.core.NonNullList.withSize(storage.getContainerSize(), net.minecraft.world.item.ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                stacks.set(slot, storage.getItem(slot));
            }
            net.minecraft.world.ContainerHelper.saveAllItems(tag, stacks);
            *///?}
        }

        //? if >=1.21.6 {
        @Override protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
        //?} else {
        /*@Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            ValueInput input = ValueInput.of(tag, registries);
        *///?}
            id = input.read("Bench", UUIDUtil.CODEC).orElse(id);
            //? if >=1.20.3 {
            storage.getItems().clear();
            //?}
            //? if >=1.21.6 {
            net.minecraft.world.ContainerHelper.loadAllItems(input, storage.getItems());
            //?} else if >=1.20.3 {
            /*net.minecraft.world.ContainerHelper.loadAllItems(tag, storage.getItems(), registries);
            *///?} else {
            /*var stacks = net.minecraft.core.NonNullList.withSize(storage.getContainerSize(), net.minecraft.world.item.ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(tag, stacks);
            for (int slot = 0; slot < stacks.size(); slot++) {
                storage.setItem(slot, stacks.get(slot));
            }
            *///?}
        }
    }
}
