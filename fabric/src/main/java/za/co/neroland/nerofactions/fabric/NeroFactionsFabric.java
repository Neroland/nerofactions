package za.co.neroland.nerofactions.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/** Fabric entry point for NeroFactions. */
public final class NeroFactionsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Fabric bootstrap");
        NeroFactionsCommon.init();
    }
}
