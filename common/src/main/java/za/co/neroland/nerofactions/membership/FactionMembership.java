package za.co.neroland.nerofactions.membership;

import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * The server-side membership API: joining, leaving and asking after a player's allegiance.
 * <b>Server-authoritative</b> — the client never asserts membership; nothing here is reachable from
 * client code.
 *
 * <p>Flow control is typed results, never exceptions: every way a join or leave can be refused is a
 * {@link JoinResult}/{@link LeaveResult} constant the caller (a command, a later GUI, a link-module
 * action) can translate for the player. Each result carries a translation key so the lang file
 * already holds the message.
 *
 * <p><b>Rules enforced here</b> (all from server-authoritative config):
 *
 * <ul>
 *   <li><b>Single allegiance</b> by default — a second faction is refused with
 *       {@link JoinResult#OTHER_ALLEGIANCE} unless {@code allowMultipleFactions} is on.</li>
 *   <li><b>Join cooldown</b> — leaving arms a cooldown of {@code joinCooldownMinutes}; a join
 *       before it elapses is refused with {@link JoinResult#ON_COOLDOWN}.</li>
 *   <li><b>Switch penalty</b> — leaving costs {@code switchPenaltyPoints} standing with the left
 *       faction, applied through {@link ReputationApi#adjust} so Core's {@code ReputationEvents}
 *       fire. Leaving never wipes or freezes standing; the rest erodes over real time (see
 *       {@link FactionDecay}).</li>
 * </ul>
 *
 * <p>The package-private overloads take every rule input (clock value, config values, the
 * faction-exists check) as parameters, so the plain-JVM tests drive them deterministically with a
 * directly-constructed state; the public entry points resolve those inputs from the running server
 * and config. State mutations are synchronized on the state itself (its methods are synchronized;
 * the check-then-act sequences here additionally hold the state's monitor so two concurrent joins
 * cannot both pass the single-allegiance check).
 */
public final class FactionMembership {

    static final long MINUTE_MS = 60_000L;

    /**
     * The clock every membership decision reads (epoch ms). Package-private seam — tests replace it
     * (or call the parameterised overloads); production never touches it.
     */
    static LongSupplier clock = System::currentTimeMillis;

    private FactionMembership() {
    }

    /** Why a join succeeded or was refused. */
    public enum JoinResult {
        JOINED,
        /** The faction id names no loaded faction definition. */
        UNKNOWN_FACTION,
        /** The player already belongs to this faction. */
        ALREADY_MEMBER,
        /** The player left a faction too recently ({@code joinCooldownMinutes}). */
        ON_COOLDOWN,
        /** Single-allegiance rule: the player already belongs to another faction. */
        OTHER_ALLEGIANCE,
        /** No server is available (defensive; command sources always have one). */
        NO_SERVER;

        /** {@code message.nerofactions.join.<name>} — resolved client-side from the lang file. */
        public String translationKey() {
            return "message.nerofactions.join." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Why a leave succeeded or was refused. */
    public enum LeaveResult {
        LEFT,
        /** The player does not belong to this faction (unknown ids land here too). */
        NOT_A_MEMBER,
        /** No server is available (defensive; command sources always have one). */
        NO_SERVER;

        /** {@code message.nerofactions.leave.<name>} — resolved client-side from the lang file. */
        public String translationKey() {
            return "message.nerofactions.leave." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // --- public API (server-resolved) -----------------------------------------------------------

    /** Joins {@code player} to {@code faction}, enforcing every rule in the class javadoc. */
    public static JoinResult join(MinecraftServer server, UUID player, Identifier faction) {
        if (server == null) {
            return JoinResult.NO_SERVER;
        }
        return join(FactionMembershipState.get(server),
                FactionDefinitions.factionsForServer(server)::containsKey,
                player, faction, clock.getAsLong(),
                NeroFactionsConfig.ALLOW_MULTIPLE_FACTIONS.get());
    }

    /** Removes {@code player} from {@code faction}, applying the switch penalty and cooldown. */
    public static LeaveResult leave(MinecraftServer server, UUID player, Identifier faction) {
        if (server == null) {
            return LeaveResult.NO_SERVER;
        }
        return leave(FactionMembershipState.get(server), player, faction, clock.getAsLong(),
                NeroFactionsConfig.JOIN_COOLDOWN_MINUTES.get() * MINUTE_MS,
                NeroFactionsConfig.SWITCH_PENALTY_POINTS.get());
    }

    /** The factions {@code player} currently belongs to (empty when no server / unknown player). */
    public static Set<Identifier> membershipOf(MinecraftServer server, UUID player) {
        if (server == null) {
            return Set.of();
        }
        return FactionMembershipState.get(server).membershipsOf(player);
    }

    // --- rule engine (package-private, parameterised for the plain-JVM tests) -------------------

    static JoinResult join(FactionMembershipState state, Predicate<Identifier> factionExists,
            UUID player, Identifier faction, long now, boolean allowMultiple) {
        if (player == null || faction == null) {
            return JoinResult.UNKNOWN_FACTION;
        }
        if (!factionExists.test(faction)) {
            return JoinResult.UNKNOWN_FACTION;
        }
        synchronized (state) {
            if (state.isMember(player, faction)) {
                return JoinResult.ALREADY_MEMBER;
            }
            // An elapsed cooldown is cleared (not just ignored) so an otherwise-empty row prunes.
            state.clearCooldownIfElapsed(player, now);
            if (now < state.cooldownUntil(player)) {
                return JoinResult.ON_COOLDOWN;
            }
            if (!allowMultiple && !state.membershipsOf(player).isEmpty()) {
                return JoinResult.OTHER_ALLEGIANCE;
            }
            state.recordJoin(player, faction, now);
        }
        return JoinResult.JOINED;
    }

    static LeaveResult leave(FactionMembershipState state, UUID player, Identifier faction,
            long now, long cooldownMillis, int switchPenaltyPoints) {
        if (player == null || faction == null) {
            return LeaveResult.NOT_A_MEMBER;
        }
        synchronized (state) {
            if (!state.recordLeave(player, faction, now, now + Math.max(0L, cooldownMillis))) {
                return LeaveResult.NOT_A_MEMBER;
            }
        }
        // The penalty routes through Core's facade so ReputationEvents fire — never through the
        // state directly. Deliberately outside the state monitor: the reputation store has its own.
        if (switchPenaltyPoints != 0) {
            ReputationApi.adjust(player, faction, -Math.abs(switchPenaltyPoints));
        }
        return LeaveResult.LEFT;
    }
}
