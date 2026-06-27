package za.co.neroland.nerofactions.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/** MinecraftForge entry point for NeroFactions. */
@Mod(NeroFactionsCommon.MOD_ID)
public final class NeroFactionsForge {

    public NeroFactionsForge(FMLJavaModLoadingContext context) {
        NeroFactionsCommon.LOGGER.info("[NeroFactions] Forge bootstrap");
        NeroFactionsCommon.init();
    }
}
