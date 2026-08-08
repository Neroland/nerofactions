package za.co.neroland.nerofactions.membership;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.data.RetentionSweep;

/**
 * The once-per-tick server hook, registered by each loader's {@code *FactionsEvents}. Three cheap
 * jobs:
 *
 * <ol>
 *   <li>pick up {@code /reload} — {@link FactionDefinitions#refreshIfReloaded} is one reference
 *       comparison in the common case (the NeroColonies pattern);</li>
 *   <li>the decay day-roll — {@link FactionDecay#applyAll}, self-throttled to once per minute of
 *       real time, which is more than enough resolution for whole-day arithmetic. Decay is also
 *       applied on read by later stages, so this pass only exists so standing keeps eroding for
 *       players nobody is looking at;</li>
 *   <li>the POPIA/GDPR retention day-roll — {@link RetentionSweep#tick}, which runs the
 *       inactivity sweep on the first pass after world load and then once per real-time day (its
 *       own bookmark; the once-a-minute throttle here merely rate-limits the check).</li>
 * </ol>
 *
 * <p>Server thread only; the throttle field needs no synchronisation.
 */
public final class FactionsTicker {

    private static final long SWEEP_INTERVAL_MS = 60_000L;

    private static long lastSweepAt = Long.MIN_VALUE;

    private FactionsTicker() {
    }

    /** Called at the end of every server tick, on every loader. */
    public static void serverTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        FactionDefinitions.refreshIfReloaded(server);
        long now = FactionMembership.clock.getAsLong();
        if (lastSweepAt != Long.MIN_VALUE && now - lastSweepAt < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepAt = now;
        FactionDecay.applyAll(server);
        RetentionSweep.tick(server, now);
    }
}
