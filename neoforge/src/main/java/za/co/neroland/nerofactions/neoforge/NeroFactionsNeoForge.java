package za.co.neroland.nerofactions.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

/** NeoForge entry point for NeroFactions. */
@Mod(NeroFactionsCommon.MOD_ID)
public final class NeroFactionsNeoForge {

    public NeroFactionsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] NeoForge bootstrap");
        // Common init declares the payloads; the registration below consumes those declarations.
        NeroFactionsCommon.init();
        // Common init's registry step created deferred registers; hand them the mod event bus
        // (the NeroColonies pattern — Core's seam drives them from here).
        RegistrationProvider.attach(modEventBus);
        NeoForgeFactionsNetwork.register(modEventBus);
        NeoForgeFactionsEvents.register();
    }
}
