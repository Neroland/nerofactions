package za.co.neroland.nerofactions.neoforge;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import za.co.neroland.nerofactions.network.FactionsNetwork;

public final class NeoForgeFactionsClient {
    private NeoForgeFactionsClient() {
    }

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(za.co.neroland.nerofactions.registry.NeroFactionsMenus.CONTRACT_BOARD.get(),
                        za.co.neroland.nerofactions.client.ContractBoardScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(za.co.neroland.nerofactions.registry.NeroFactionsMenus.REGISTRY.get(),
                        za.co.neroland.nerofactions.client.RegistryScreen::new));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                FactionsNetwork.clearClientCaches());
    }
}
