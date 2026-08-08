package za.co.neroland.nerofactions.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.network.FactionsNetwork;

/** Fabric client entry point for NeroFactions. */
public final class NeroFactionsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Fabric client bootstrap");
        // Clientbound receivers (client-only API) — registered here, off the dedicated server.
        FabricFactionsNetwork.registerClient();

        // Drop any synced mirror caches on leaving a world/server, so one session's faction state
        // can never be shown in the next (or on a server that does not run NeroFactions).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                FactionsNetwork.clearClientCaches());
    }
}
