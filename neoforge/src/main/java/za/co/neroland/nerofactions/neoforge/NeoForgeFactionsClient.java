package za.co.neroland.nerofactions.neoforge;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import za.co.neroland.nerofactions.network.FactionsNetwork;
import za.co.neroland.nerofactions.client.OutpostRenderers;
import za.co.neroland.nerofactions.registry.NeroFactionsEntities;

public final class NeoForgeFactionsClient {
    private NeoForgeFactionsClient() {
    }

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(NeroFactionsEntities.ENVOY.get(), OutpostRenderers.Envoy::new);
            event.registerEntityRenderer(NeroFactionsEntities.GUARD.get(), OutpostRenderers.Guard::new);
        });
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
