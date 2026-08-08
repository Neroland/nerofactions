package za.co.neroland.nerofactions.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

/** MinecraftForge entry point for NeroFactions. */
@Mod(NeroFactionsCommon.MOD_ID)
public final class NeroFactionsForge {

    public NeroFactionsForge(FMLJavaModLoadingContext context) {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Forge bootstrap");
        // Common init declares the payloads; the channel below is sealed the moment it is built,
        // so that ordering is mandatory on Forge.
        NeroFactionsCommon.init();
        // Common init's registry step created deferred registers; hand them the mod bus group
        // (the NeroColonies pattern — Core's seam drives them from here).
        RegistrationProvider.attach(context.getModBusGroup());
        ForgeFactionsNetwork.register();
        ForgeFactionsEvents.register();
    }
}
