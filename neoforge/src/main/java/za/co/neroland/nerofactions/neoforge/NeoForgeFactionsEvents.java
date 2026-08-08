package za.co.neroland.nerofactions.neoforge;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * NeoForge server-lifecycle wiring: binds the reputation provider to the server that just started
 * and unbinds it on stop, so a later world in this JVM can never read a stale reference (the same
 * lifecycle shape NeroColonies' {@code NeoForgeColonyEvents} uses).
 */
public final class NeoForgeFactionsEvents {

    private NeoForgeFactionsEvents() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                NeroFactionsCommon.REPUTATION_PROVIDER.bind(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
                NeroFactionsCommon.REPUTATION_PROVIDER.unbind(event.getServer()));
    }
}
