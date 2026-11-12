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
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.contracts.ContractService;
import za.co.neroland.nerofactions.data.ContractState;
import za.co.neroland.nerofactions.network.ContractIntent;
import za.co.neroland.nerofactions.network.ContractSnapshot;
import za.co.neroland.nerofactions.platform.Services;
import za.co.neroland.nerofactions.registry.NeroFactionsBlocks;
import za.co.neroland.nerofactions.registry.NeroFactionsMenus;
import za.co.neroland.nerolandcore.economy.CurrencyApi;

public final class ContractBoardMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ServerPlayer owner;
    private long lastAction = Long.MIN_VALUE;
    private long lastSync = Long.MIN_VALUE;
    private String feedback = "";

    public ContractBoardMenu(int id, Inventory inventory) {
        this(id, ContainerLevelAccess.NULL, null);
    }

    private ContractBoardMenu(int id, ContainerLevelAccess access, ServerPlayer owner) {
        super(NeroFactionsMenus.CONTRACT_BOARD.get(), id);
        this.access = access;
        this.owner = owner;
    }

    public static SimpleMenuProvider provider(Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new ContractBoardMenu(id,
                ContainerLevelAccess.create(level, pos), (ServerPlayer) player),
                Component.translatable("block.nerofactions.contract_board"));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, NeroFactionsBlocks.CONTRACT_BOARD.get());
    }

    public void act(ServerPlayer player, ContractIntent intent) {
        if (player != owner || player.containerMenu != this || !stillValid(player)
                || !player.level().getServer().isSameThread()) return;
        long now = player.level().getGameTime();
        if (lastAction != Long.MIN_VALUE && now - lastAction < 5) return;
        lastAction = now;
        ContractService.current(player);
        var state = ContractState.get(player.level().getServer());
        var entry = state.entry(player.getUUID(), intent.faction(), intent.slot());
        if (!NeroFactionsConfig.CONTRACTS_ENABLED.get()) feedback = "gui.nerofactions.contract.disabled";
        else if (!intent.matches(containerId, state.day(player.getUUID()), entry)) feedback = "gui.nerofactions.contract.changed";
        else if (intent.action() == 0) feedback = ContractService.deliver(player, intent.faction(), intent.slot()) > 0
                ? "gui.nerofactions.contract.delivered" : "gui.nerofactions.contract.no_items";
        else if (intent.action() == 2) feedback = ContractService.reroll(player, intent.faction(), intent.slot())
                ? "gui.nerofactions.contract.rerolled" : "gui.nerofactions.contract.reroll_refused";
        else feedback = ContractService.claim(player, intent.faction(), intent.slot());
        refresh();
    }

    public void refresh() {
        if (owner == null || owner.containerMenu != this) return;
        var entries = ContractService.current(owner).stream().sorted(java.util.Comparator
                .comparing((ContractState.Entry e) -> e.contract().faction()).thenComparingInt(ContractState.Entry::slot))
                .limit(ContractSnapshot.MAX_ENTRIES).map(e -> new ContractSnapshot.Row(e.contract().faction(), e.contract().id(),
                        e.slot(), e.contract().type(), e.contract().target(), e.progress(), e.contract().count(),
                        e.contract().reputation(), CurrencyApi.hasRealProvider() ? e.contract().credits() : 0, e.claimed())).toList();
        var state = ContractState.get(owner.level().getServer());
        boolean enabled = NeroFactionsConfig.CONTRACTS_ENABLED.get();
        Services.NETWORK.sendToPlayer(owner, new ContractSnapshot(entries, state.day(owner.getUUID()),
                enabled && state.rerollAvailable(owner.getUUID()), enabled ? feedback : "gui.nerofactions.contract.disabled"));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner != null && (lastSync == Long.MIN_VALUE || owner.level().getGameTime() - lastSync >= 20)) {
            lastSync = owner.level().getGameTime();
            refresh();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
