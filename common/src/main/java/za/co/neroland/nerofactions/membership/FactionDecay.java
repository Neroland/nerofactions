package za.co.neroland.nerofactions.membership;

import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * Reputation decay for factions a player has <em>left</em>. The locked design: leaving never wipes
 * or freezes standing — it erodes by {@code decayPointsPerDay} per whole elapsed <b>real-time</b>
 * day toward 0, from either sign (earned goodwill fades; so does the switch-penalty grudge), and
 * stops dead at 0, at which point the player stops being tracked for that faction at all (the
 * {@code left} entry is removed — see {@link FactionMembershipState}, whose rows self-erase).
 *
 * <p><b>Deterministic.</b> All arithmetic is whole-days-since-{@code leftAt} against the injected
 * clock ({@link FactionMembership#clock}, the package's one time seam): a pass that runs late
 * applies exactly the days that elapsed, no more, and the bookmark ({@code leftAt}) advances by
 * exactly the days applied, so no day is ever counted twice regardless of how often
 * {@link #applyDecay} runs. It is therefore safe to call both from the periodic tick pass
 * <em>and</em> on read (Stage 3's gating will do so before resolving a tier).
 *
 * <p>Every mutation routes through {@link ReputationApi#adjust} so Core's {@code ReputationEvents}
 * fire — a threshold crossing caused by decay is as observable as one caused by an award. Decay
 * never triggers enemy bleed: bleed belongs exclusively to source awards
 * ({@code ReputationSources}), never to decay or plain writes.
 */
public final class FactionDecay {

    static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private FactionDecay() {
    }

    /** Applies pending decay for every left faction of every tracked player. The tick-pass body. */
    public static void applyAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        FactionMembershipState state = FactionMembershipState.get(server);
        int pointsPerDay = NeroFactionsConfig.DECAY_POINTS_PER_DAY.get();
        long now = FactionMembership.clock.getAsLong();
        for (UUID player : state.playersWithPendingDecay()) {
            applyDecay(state, player, now, pointsPerDay);
        }
    }

    /** Applies pending decay for one player right now (the on-read helper). */
    public static void apply(MinecraftServer server, UUID player) {
        if (server == null || player == null) {
            return;
        }
        applyDecay(FactionMembershipState.get(server), player,
                FactionMembership.clock.getAsLong(),
                NeroFactionsConfig.DECAY_POINTS_PER_DAY.get());
    }

    /**
     * The deterministic core, parameterised for the plain-JVM tests: for each faction the player
     * has left, applies {@code wholeDays * pointsPerDay} toward 0 (clamped at 0), advances the
     * bookmark by exactly the days applied, and stops tracking the faction once standing is 0.
     *
     * <p>A non-positive {@code pointsPerDay} disables decay arithmetic but still releases factions
     * whose standing is already 0 — tracking something that can never change is pointless.
     */
    static void applyDecay(FactionMembershipState state, UUID player, long now, int pointsPerDay) {
        for (Map.Entry<Identifier, Long> left : state.leftFactions(player).entrySet()) {
            Identifier faction = left.getKey();
            long leftAt = left.getValue();
            int current = ReputationApi.getReputation(player, faction);
            if (current == 0) {
                // Nothing to decay (or a prior pass finished the job): stop tracking.
                state.advanceDecay(player, faction, leftAt, true);
                continue;
            }
            long wholeDays = Math.floorDiv(now - leftAt, DAY_MS);
            if (wholeDays <= 0 || pointsPerDay <= 0) {
                continue;
            }
            long erosion = Math.min(wholeDays * (long) pointsPerDay, Math.abs((long) current));
            int updated = current;
            if (erosion > 0) {
                // Toward zero from either sign, via the facade so ReputationEvents fire.
                updated = ReputationApi.adjust(player, faction, (int) (current > 0 ? -erosion : erosion));
            }
            state.advanceDecay(player, faction, leftAt + wholeDays * DAY_MS, updated == 0);
        }
    }
}
