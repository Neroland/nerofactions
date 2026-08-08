package za.co.neroland.nerofactions.fabric;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.command.FactionCommands;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.data.RetentionSweep;
import za.co.neroland.nerofactions.membership.FactionsTicker;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerofactions.trigger.InternalTriggers;

/**
 * Fabric server-side wiring: binds the reputation provider to the server lifecycle, drives the
 * faction tick pass (/reload pickup + decay day-roll) and registers the {@code /nerofactions}
 * admin tree — the same lifecycle shape NeroColonies' {@code FabricColonyEvents} and NeroQuests'
 * {@code FabricQuestEvents} use.
 */
public final class FabricFactionsEvents {

    private FabricFactionsEvents() {
    }

    /** Called once from the Fabric entry point. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(NeroFactionsCommon.REPUTATION_PROVIDER::bind);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            NeroFactionsCommon.REPUTATION_PROVIDER.unbind(server);
            // Drop the cached definitions so a later world in this JVM re-reads its own packs.
            FactionDefinitions.forgetServer();
            // And the trade terminal's transient per-player faction selections (POPIA/GDPR:
            // in-memory UUID-keyed convenience data does not outlive the server).
            TerminalSessions.clearAll();
            // Forget the retention sweep's day-roll bookmark so the next world loaded in this
            // JVM sweeps on load instead of waiting for the next calendar day.
            RetentionSweep.onServerStopped();
        });

        // Cheap per-tick pass: /reload pickup (one reference comparison) + the decay day-roll
        // (self-throttled to once a minute inside the ticker).
        ServerTickEvents.END_SERVER_TICK.register(FactionsTicker::serverTick);

        // Combat reputation. AFTER_DEATH is server-side only and hands over exactly the pair the
        // trigger wants: the victim plus the damage source the credit is resolved from; the
        // trigger filters to player-credited hostile-monster kills and routes through
        // ReputationSources (weight + daily cap + enemy bleed).
        ServerLivingEntityEvents.AFTER_DEATH.register(InternalTriggers::entityKilled);

        // The /nerofactions admin tree; the tree itself is loader-agnostic.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                FactionCommands.register(dispatcher));
    }
}
