package za.co.neroland.nerofactions.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/** Fabric client entry point for NeroFactions. */
public final class NeroFactionsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Fabric client bootstrap");
    }
}
