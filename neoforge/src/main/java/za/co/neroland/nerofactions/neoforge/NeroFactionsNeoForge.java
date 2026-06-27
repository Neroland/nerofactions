package za.co.neroland.nerofactions.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/** NeoForge entry point for NeroFactions. */
@Mod(NeroFactionsCommon.MOD_ID)
public final class NeroFactionsNeoForge {

    public NeroFactionsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] NeoForge bootstrap");
        NeroFactionsCommon.init();
    }
}
