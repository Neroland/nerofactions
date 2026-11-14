package za.co.neroland.nerofactions.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/** Fabric entry point for NeroFactions. */
public final class NeroFactionsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Fabric bootstrap");
        // Common init declares the payloads; the registration below consumes those declarations,
        // so the ordering is mandatory on every loader.
        NeroFactionsCommon.init();
        FabricFactionsNetwork.registerCommon();
        FabricFactionsEvents.register();
        za.co.neroland.nerolandcore.platform.FabricEnergyLookup.ENERGY.registerForBlockEntity(
                (processor, side) -> processor.sideConfig().energyView(side),
                za.co.neroland.nerofactions.registry.NeroFactionsBlocks.ATMOSPHERE_PROCESSOR_ENTITY.get());
    }
}
