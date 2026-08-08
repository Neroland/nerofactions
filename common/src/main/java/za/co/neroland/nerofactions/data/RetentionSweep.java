package za.co.neroland.nerofactions.data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerolandcore.data.PlayerActivity;

/**
 * NeroFactions' own inactivity <b>retention sweep</b> (POPIA/GDPR data minimisation): once per
 * real-time day — and on the first tick pass after a world loads — every player present in either
 * NeroFactions store whose last login (per Core's shared {@link PlayerActivity} record) is older
 * than {@code retentionDays} is purged through {@link NeroFactionsData#eraseLocal}, the same code
 * path an explicit erasure request takes (stores + recovery backups + transient session).
 *
 * <p><b>How this relates to Core's sweep.</b> Core's {@code PlayerDataErasure.purgeInactive}
 * (config {@code dataRetentionDays}, default 0 = off, driven by {@code /neroland data
 * purge-inactive}) already erases NeroFactions data for stale players — it fans out to every
 * registered eraser, ours included. This sweep <em>complements</em> it rather than duplicating it:
 * it is scoped to this mod's stores only (no ecosystem fan-out), runs automatically on its own
 * schedule, and has its own window ({@code retentionDays}, default 365), so faction data expires
 * even on servers that leave Core's global sweep off, and can expire sooner than the global window.
 *
 * <p><b>Activity source.</b> Core's {@link PlayerActivity} is the ecosystem's one last-seen record
 * (UUID → login epoch-ms, itself erasure-registered and retention-swept); reusing its
 * {@link PlayerActivity#stalerThan(int)} means this sweep mints <b>no new personal data</b>. A
 * player present in our stores but absent from the activity record (data granted by an admin to
 * someone who never logged in since Core started recording) is <em>not</em> purged — their
 * inactivity cannot be established, and Core's own sweep makes the same conservative choice.
 *
 * <p><b>Privacy.</b> Logs an anonymous count only, and only when something was purged — never a
 * UUID (the ecosystem's erasure-logging rule).
 *
 * <p>Server thread only; the day-roll bookmark needs no synchronisation.
 */
public final class RetentionSweep {

    static final long DAY_MS = 86_400_000L;

    /** Epoch day of the last run, {@code Long.MIN_VALUE} = not yet run (world load runs it). */
    private static long lastRunDay = Long.MIN_VALUE;

    private RetentionSweep() {
    }

    /**
     * The tick-pass entry point, called from {@code FactionsTicker}'s once-a-minute block: runs
     * {@link #sweep(MinecraftServer)} on the first call after a world loads and then once per
     * epoch-day roll of {@code now} (the ticker's injected clock, so tests drive it).
     */
    public static void tick(MinecraftServer server, long now) {
        if (server != null && dueNow(now)) {
            sweep(server);
        }
    }

    /**
     * Whether a sweep is due at {@code now}, advancing the bookmark when it is — so a day fires
     * exactly once no matter how often the ticker asks. Package-private decision seam for tests.
     */
    static boolean dueNow(long now) {
        long day = Math.floorDiv(now, DAY_MS);
        if (day == lastRunDay) {
            return false;
        }
        lastRunDay = day;
        return true;
    }

    /**
     * Forgets the day-roll bookmark when a server stops, so the next world loaded in this JVM
     * sweeps on load rather than waiting for the next calendar day.
     */
    public static void onServerStopped() {
        lastRunDay = Long.MIN_VALUE;
    }

    /** Runs one sweep now against the live server and config. @return how many players were purged. */
    public static int sweep(MinecraftServer server) {
        Set<UUID> known = new LinkedHashSet<>(FactionReputationState.get(server).knownPlayers());
        known.addAll(FactionMembershipState.get(server).knownPlayers());
        int purged = sweep(NeroFactionsConfig.RETENTION_DAYS.get(), known,
                days -> PlayerActivity.get(server).stalerThan(days),
                player -> NeroFactionsData.eraseLocal(server, player));
        if (purged > 0) {
            NeroFactionsCommon.LOGGER.info(
                    "[NeroFactions] Retention sweep purged {} inactive players' faction data.", purged);
        }
        return purged;
    }

    /**
     * The decision core, parameterised for the plain-JVM tests: purge the intersection of "players
     * this mod stores data for" and "players inactive longer than {@code retentionDays}".
     * {@code retentionDays <= 0} disables the sweep entirely (the activity record is not even
     * consulted).
     */
    static int sweep(int retentionDays, Set<UUID> known, IntFunction<List<UUID>> staleForDays,
            Consumer<UUID> erase) {
        if (retentionDays <= 0 || known.isEmpty()) {
            return 0;
        }
        int purged = 0;
        for (UUID stale : staleForDays.apply(retentionDays)) {
            if (known.contains(stale)) {
                erase.accept(stale);
                purged++;
            }
        }
        return purged;
    }
}
