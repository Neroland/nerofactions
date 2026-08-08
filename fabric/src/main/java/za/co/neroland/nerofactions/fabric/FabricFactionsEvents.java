package za.co.neroland.nerofactions.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * Fabric server-lifecycle wiring: binds the reputation provider to the server that just started and
 * unbinds it on stop, so a later world in this JVM can never read a stale reference (the same
 * lifecycle shape NeroColonies' {@code FabricColonyEvents} uses).
 */
public final class FabricFactionsEvents {

    private FabricFactionsEvents() {
    }

    /** Called once from the Fabric entry point. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(NeroFactionsCommon.REPUTATION_PROVIDER::bind);
        ServerLifecycleEvents.SERVER_STOPPED.register(NeroFactionsCommon.REPUTATION_PROVIDER::unbind);
    }
}
