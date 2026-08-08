package za.co.neroland.nerofactions.forge;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.command.FactionCommands;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.data.RetentionSweep;
import za.co.neroland.nerofactions.membership.FactionsTicker;
import za.co.neroland.nerofactions.trade.TerminalSessions;
import za.co.neroland.nerofactions.trigger.InternalTriggers;

/**
 * Forge server-side wiring: binds the reputation provider to the server lifecycle, drives the
 * faction tick pass (/reload pickup + decay day-roll) and registers the {@code /nerofactions}
 * admin tree — the same lifecycle shape NeroColonies' {@code ForgeColonyEvents} and NeroQuests'
 * {@code ForgeQuestEvents} use.
 */
public final class ForgeFactionsEvents {

    private ForgeFactionsEvents() {
    }

    /** Called once from the Forge entry point. */
    public static void register() {
        ServerStartedEvent.BUS.addListener(event ->
                NeroFactionsCommon.REPUTATION_PROVIDER.bind(event.getServer()));
        ServerStoppedEvent.BUS.addListener(event -> {
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
        TickEvent.ServerTickEvent.Post.BUS.addListener(event ->
                FactionsTicker.serverTick(event.server()));

        // Combat reputation: the internal trigger filters to player-credited hostile-monster
        // kills and routes through ReputationSources (weight + daily cap + enemy bleed).
        // A statement-bodied lambda is required: LivingDeathEvent is cancellable, so its BUS
        // offers both Consumer and Predicate overloads and an expression lambda is ambiguous.
        LivingDeathEvent.BUS.addListener(event -> {
            InternalTriggers.entityKilled(event.getEntity(), event.getSource());
        });

        // The /nerofactions admin tree; the tree itself is loader-agnostic.
        RegisterCommandsEvent.BUS.addListener(event -> FactionCommands.register(event.getDispatcher()));
    }
}
