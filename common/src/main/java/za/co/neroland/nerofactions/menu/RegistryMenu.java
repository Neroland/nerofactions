package za.co.neroland.nerofactions.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.membership.FactionMembership;
import za.co.neroland.nerofactions.network.StandingSync;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerofactions.registry.NeroFactionsMenus;

public final class RegistryMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final java.util.List<net.minecraft.resources.Identifier> shownFactions;
    private long lastAction = Long.MIN_VALUE;
    private final net.minecraft.world.inventory.DataSlot feedback = net.minecraft.world.inventory.DataSlot.standalone();
    private final net.minecraft.world.inventory.DataSlot feedbackFaction = net.minecraft.world.inventory.DataSlot.standalone();

    public RegistryMenu(int id, Inventory inventory) {
        this(id, ContainerLevelAccess.NULL, java.util.List.of());
    }

    private RegistryMenu(int id, ContainerLevelAccess access,
            java.util.List<net.minecraft.resources.Identifier> factions) {
        super(NeroFactionsMenus.REGISTRY.get(), id);
        this.access = access;
        this.shownFactions = java.util.List.copyOf(factions);
        addDataSlot(feedback);
        addDataSlot(feedbackFaction);
    }

    public static SimpleMenuProvider provider(Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new RegistryMenu(id, ContainerLevelAccess.create(level, pos),
                        FactionDefinitions.factionsForServer(level.getServer()).keySet().stream().sorted().toList()),
                Component.translatable("block.nerofactions.faction_registry"));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NeroFactionsBlocks.FACTION_REGISTRY.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer online) || player.containerMenu != this || !stillValid(player)) {
            return false;
        }
        if (!za.co.neroland.nerofactions.config.NeroFactionsConfig.REGISTRY_ENABLED.get()) {
            feedback.set(-1);
            return true;
        }
        var server = online.level().getServer();
        if (server == null || !server.isSameThread() || button < 0) {
            return false;
        }
        long now = online.level().getGameTime();
        var factions = FactionDefinitions.factionsForServer(server).keySet().stream().sorted().toList();
        if (!factions.equals(shownFactions)) {
            za.co.neroland.nerofactions.platform.VersionCompat.systemMessage(player,
                    Component.translatable("gui.nerofactions.reopen"));
            online.closeContainer();
            return false;
        }
        RegistryIntent intent = RegistryIntent.decode(button, factions.size(), now, lastAction);
        if (intent == null) {
            return false;
        }
        lastAction = now;
        var faction = factions.get(intent.factionIndex());
        feedbackFaction.set(intent.factionIndex());
        String reason;
        if (intent.leave()) {
            var result = FactionMembership.leave(server, player.getUUID(), faction);
            feedback.set(100 + result.ordinal());
            reason = result.translationKey();
        } else {
            var result = FactionMembership.join(server, player.getUUID(), faction);
            feedback.set(1 + result.ordinal());
            reason = result.translationKey();
        }
        za.co.neroland.nerofactions.platform.VersionCompat.systemMessage(player,
                Component.translatable(reason, FactionDefinitions.factionsForServer(server).get(faction).displayName()));
        StandingSync.send(online);
        return true;
    }

    public String feedbackKey() {
        int value = feedback.get();
        if (value == -1) return "gui.nerofactions.registry_disabled";
        if (value == -2) return "gui.nerofactions.contract.place_nearby";
        if (value >= 100 && value - 100 < FactionMembership.LeaveResult.values().length) {
            return FactionMembership.LeaveResult.values()[value - 100].translationKey();
        }
        if (value > 0 && value - 1 < FactionMembership.JoinResult.values().length) {
            return FactionMembership.JoinResult.values()[value - 1].translationKey();
        }
        return "";
    }

    public void openContracts(ServerPlayer player) {
        if (player.containerMenu != this || !stillValid(player) || !player.level().getServer().isSameThread()) return;
        long now = player.level().getGameTime();
        if (lastAction != Long.MIN_VALUE && now - lastAction < 5) return;
        lastAction = now;
        boolean[] opened = {false};
        access.execute((level, origin) -> {
            for (BlockPos candidate : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(4, 4, 4))) {
                if (player.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5) > 64) continue;
                if (level.getBlockState(candidate).is(NeroFactionsBlocks.CONTRACT_BOARD.get())) {
                    opened[0] = MenuOpener.open(player, ContractBoardMenu.provider(level, candidate.immutable())).isPresent();
                    if (opened[0] && player.containerMenu instanceof ContractBoardMenu board) board.refresh();
                    break;
                }
            }
        });
        if (!opened[0]) feedback.set(-2);
    }

    public int feedbackFactionIndex() {
        return feedbackFaction.get();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
