package za.co.neroland.nerofactions.neoforge;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.command.FactionCommands;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.data.RetentionSweep;
import za.co.neroland.nerofactions.membership.FactionsTicker;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerofactions.trigger.InternalTriggers;

/**
 * NeoForge server-side wiring: binds the reputation provider to the server lifecycle, drives the
 * faction tick pass (/reload pickup + decay day-roll) and registers the {@code /nerofactions}
 * admin tree — the same lifecycle shape NeroColonies' {@code NeoForgeColonyEvents} and NeroQuests'
 * {@code NeoForgeQuestEvents} use.
 */
public final class NeoForgeFactionsEvents {

    private NeoForgeFactionsEvents() {
    }

    /** Called once from the NeoForge entry point. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
                NeroFactionsCommon.REPUTATION_PROVIDER.bind(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            NeroFactionsCommon.REPUTATION_PROVIDER.unbind(event.getServer());
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
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                FactionsTicker.serverTick(event.getServer()));

        // Combat reputation: the internal trigger filters to player-credited hostile-monster
        // kills and routes through ReputationSources (weight + daily cap + enemy bleed).
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) ->
                InternalTriggers.entityKilled(event.getEntity(), event.getSource()));

        // The /nerofactions admin tree; the tree itself is loader-agnostic.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                FactionCommands.register(event.getDispatcher()));
    }
}
