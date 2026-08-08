package za.co.neroland.nerofactions.forge;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * Forge server-lifecycle wiring: binds the reputation provider to the server that just started and
 * unbinds it on stop, so a later world in this JVM can never read a stale reference (the same
 * lifecycle shape NeroColonies' {@code ForgeColonyEvents} uses).
 */
public final class ForgeFactionsEvents {

    private ForgeFactionsEvents() {
    }

    /** Called once from the Forge entry point. */
    public static void register() {
        ServerStartedEvent.BUS.addListener(event ->
                NeroFactionsCommon.REPUTATION_PROVIDER.bind(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event ->
                NeroFactionsCommon.REPUTATION_PROVIDER.unbind(event.getServer()));
    }
}
