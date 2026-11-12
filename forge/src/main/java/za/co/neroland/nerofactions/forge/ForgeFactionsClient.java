package za.co.neroland.nerofactions.forge;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import za.co.neroland.nerofactions.network.FactionsNetwork;

public final class ForgeFactionsClient {
    private ForgeFactionsClient() {
    }

    public static void init(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        //? if >=1.21.6 {
        net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.getBus(context.getModBusGroup())
                .addListener(event -> event.enqueueWork(ForgeFactionsClient::screens));
        //?} else {
        /*context.getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(ForgeFactionsClient::screens));
        *///?}
        //? if >=1.21.6 {
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> FactionsNetwork.clearClientCaches());
        //?} else {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut event) -> FactionsNetwork.clearClientCaches());
        *///?}
    }

    private static void screens() {
        net.minecraft.client.gui.screens.MenuScreens.register(
                za.co.neroland.nerofactions.registry.NeroFactionsMenus.CONTRACT_BOARD.get(),
                za.co.neroland.nerofactions.client.ContractBoardScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(
                za.co.neroland.nerofactions.registry.NeroFactionsMenus.REGISTRY.get(),
                za.co.neroland.nerofactions.client.RegistryScreen::new);
    }
}
