package za.co.neroland.nerofactions.reputation;

import java.util.Locale;
import java.util.UUID;
import java.util.function.LongSupplier;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.data.FactionMembershipState;
import za.co.neroland.nerolandcore.reputation.ReputationApi;

/**
 * The one legitimate way gameplay earns faction standing: <b>weighted, per-source daily-capped
 * awards</b>. Anything in NeroFactions (and, later, sibling-mod integrations) that wants to grant
 * reputation calls {@link #award}; nothing gameplay-facing calls {@code ReputationApi.adjust} raw,
 * because raw adjustments bypass the caps, the weights and the enemy graph.
 *
 * <p><b>The locked source model.</b> Quests and events weigh highest (1.0), combat middles (0.6),
 * trade lowest (0.3) — the weights and the per-faction-per-source daily caps are all
 * server-authoritative config. {@link Source#ADMIN} is the operator lever: weight fixed at 1,
 * bypasses every cap, and <b>never bleeds</b> — operator actions must be exact, with no
 * side-effects on other factions. (Stage 2 shipped ADMIN bleeding; corrected in Stage 5. The
 * admin commands additionally bypass this class entirely and write through
 * {@code ReputationApi} directly.)
 *
 * <p><b>Daily caps</b> are per player, per faction, per source, counted against the real-time UTC
 * day ({@code floor(epochMs / 86_400_000)}) in {@link FactionMembershipState}'s accrual rows; the
 * first award of a new day implicitly resets the counter. Only what was actually applied counts
 * against the cap, so a clamped award never burns headroom it did not use.
 *
 * <p><b>Enemy bleed.</b> After the primary award, each faction listed in the target's
 * {@code enemies} loses {@code round(delta * enemyBleedRatio)} — literally
 * {@code adjust(player, enemy, -round(delta * ratio))}, so a <em>negative</em> award symmetrically
 * pleases the enemies. Bleed follows the target's list only (asymmetric graphs are deliberate:
 * A bleeding B never implies B bleeds A), never cascades (an enemy's own enemies are untouched),
 * never applies to decay or plain {@code setReputation}, and a ratio of {@code 0.0} disables it.
 *
 * <p>Every mutation routes through {@link ReputationApi} statics so Core's
 * {@code ReputationEvents} fire for the primary award and each bleed individually.
 */
public final class ReputationSources {

    static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /**
     * The clock awards are day-stamped against (epoch ms). Package-private seam — tests replace it
     * (or call the parameterised overload); production never touches it.
     */
    static LongSupplier clock = System::currentTimeMillis;

    private ReputationSources() {
    }

    /** Where standing came from. The weight/cap config pair is per constant (ADMIN has neither). */
    public enum Source {
        QUEST,
        /**
         * Server events. Reserved seam: <b>NeroEvents will drive this source when it exists</b>
         * (it is an empty skeleton today, so nothing awards EVENT yet); the weight/cap config
         * ships now so server files are stable across that arrival.
         */
        EVENT,
        COMBAT,
        TRADE,
        /** Operator grants: weight 1, no cap, never bleeds (admin actions must be exact). */
        ADMIN;

        /** The name accrual rows are keyed by (stable across renames — it is stored on disk). */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Awards {@code baseAmount} standing with {@code faction} from {@code source}: weights it,
     * clamps it to the source's remaining daily cap, applies it through
     * {@link ReputationApi#adjust}, then bleeds the faction's enemies.
     *
     * @return the primary delta actually applied (0 when the faction is unknown, the cap is
     *     exhausted, or the weighted amount rounds to nothing)
     */
    public static int award(MinecraftServer server, UUID player, Identifier faction, Source source,
            int baseAmount) {
        if (server == null || player == null || faction == null || source == null) {
            return 0;
        }
        FactionDefinition definition = FactionDefinitions.factionsForServer(server).get(faction);
        if (definition == null) {
            return 0;
        }
        long dayStamp = Math.floorDiv(clock.getAsLong(), DAY_MS);
        return award(FactionMembershipState.get(server), definition, player, source, baseAmount,
                dayStamp, weightFor(source), capFor(source),
                NeroFactionsConfig.ENEMY_BLEED_RATIO.get());
    }

    /**
     * The deterministic core, parameterised for the plain-JVM tests. {@code cap < 0} means
     * uncapped (ADMIN); a weight of exactly 1.0 is applied without rounding artefacts.
     */
    static int award(FactionMembershipState state, FactionDefinition faction, UUID player,
            Source source, int baseAmount, long dayStamp, double weight, int cap, double bleedRatio) {
        int delta = (int) Math.round(baseAmount * weight);
        if (delta > 0 && cap >= 0) {
            int remaining = cap - state.accruedToday(player, faction.id(), source.key(), dayStamp);
            delta = Math.min(delta, Math.max(0, remaining));
        }
        if (delta == 0) {
            return 0;
        }
        if (cap >= 0 && delta > 0) {
            // Only what was actually applied counts against the cap.
            state.addAccrued(player, faction.id(), source.key(), dayStamp, delta);
        }
        ReputationApi.adjust(player, faction.id(), delta);

        // Enemy bleed: the target's own list only — no recursion into the enemies' enemies, and
        // asymmetric graphs stay asymmetric. round(0.0) == 0 keeps ratio 0.0 a true off switch.
        // ADMIN never bleeds: operator actions must be exact (Stage 5 fix — Stage 2 bled).
        int bleed = source == Source.ADMIN ? 0 : (int) Math.round(delta * bleedRatio);
        if (bleed != 0) {
            for (Identifier enemy : faction.enemies()) {
                ReputationApi.adjust(player, enemy, -bleed);
            }
        }
        return delta;
    }

    private static double weightFor(Source source) {
        return switch (source) {
            case QUEST -> NeroFactionsConfig.QUEST_SOURCE_WEIGHT.get();
            case EVENT -> NeroFactionsConfig.EVENT_SOURCE_WEIGHT.get();
            case COMBAT -> NeroFactionsConfig.COMBAT_SOURCE_WEIGHT.get();
            case TRADE -> NeroFactionsConfig.TRADE_SOURCE_WEIGHT.get();
            case ADMIN -> 1.0D;
        };
    }

    /** The per-faction-per-day cap for this source; {@code -1} = uncapped (ADMIN only). */
    private static int capFor(Source source) {
        return switch (source) {
            case QUEST -> NeroFactionsConfig.QUEST_DAILY_CAP.get();
            case EVENT -> NeroFactionsConfig.EVENT_DAILY_CAP.get();
            case COMBAT -> NeroFactionsConfig.COMBAT_DAILY_CAP.get();
            case TRADE -> NeroFactionsConfig.TRADE_DAILY_CAP.get();
            case ADMIN -> -1;
        };
    }
}
