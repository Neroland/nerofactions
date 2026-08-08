package za.co.neroland.nerofactions.reputation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.rewards.RewardGrants;
import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.event.ThresholdEvents.ThresholdCrossing;
import za.co.neroland.nerolandcore.progression.ProgressionGates;
import za.co.neroland.nerolandcore.reputation.ReputationEvents;
import za.co.neroland.nerolandcore.reputation.ReputationEvents.ReputationChange;

/**
 * Publishes faction tier changes to Core's shared {@link ThresholdEvents} bus — the zero-coupling
 * join that lets NeroQuests (via its {@code custom_event} objective), NeroEvents and any other Core
 * consumer react to standing milestones without importing NeroFactions. Subscribed once to
 * {@code ReputationEvents.onChange} in {@link NeroFactionsCommon#init()}, so <em>every</em>
 * reputation mutation that goes through Core's {@code ReputationApi} facade — source awards, enemy
 * bleed, switch penalties, admin grants, decay — is observed here; nothing in NeroFactions writes
 * reputation any other way.
 *
 * <h2>The {@code nerofactions:reputation_tier} channel — exact payload shape</h2>
 *
 * <p>One {@link ThresholdCrossing} is fired <b>per tier boundary crossed</b>, in the order the
 * boundaries are actually crossed (ascending while rising, descending while falling). A single
 * award that jumps a player from 0 to 1200 standing therefore fires three crossings — Associate,
 * Member, Trusted — not one.
 *
 * <ul>
 *   <li>{@code channel} — always {@code nerofactions:reputation_tier}.</li>
 *   <li>{@code scope} — the <b>faction id</b> as a string (e.g. {@code "nerofactions:space_guild"}).
 *       The faction is the system that crossed; per Core's contract the scope is never a person, so
 *       no player UUID or name ever appears here (POPIA/GDPR).</li>
 *   <li>{@code value} — the {@link FactionTier} <b>ordinal of the tier on the upper side of the
 *       boundary being crossed</b>: 1 = Associate, 2 = Member, 3 = Trusted, 4 = Inner Circle
 *       (Outsider, ordinal 0, has no lower boundary and never appears). Rising, that is the tier
 *       just <em>reached</em>; falling, the tier just <em>lost</em>. A quest matching
 *       {@code "direction": "rising", "min_value": 4} means "reached the inner circle";
 *       {@code "direction": "falling", "max_value": 3} means "dropped below Trusted".</li>
 *   <li>{@code threshold} — that upper-side tier's reputation threshold on the faction's own
 *       ladder (data-driven per faction, e.g. 2500 for a stock inner circle).</li>
 *   <li>{@code rising} — {@code true} when standing rose across the boundary, {@code false} when
 *       it fell. Falling crossings are real and routine: decay after leaving a faction, enemy
 *       bleed and switch penalties all erode standing, and each boundary lost fires.</li>
 * </ul>
 *
 * <h2>Gate composition</h2>
 *
 * <p>On a rising crossing into {@link FactionTier#INNER_CIRCLE}, this class calls
 * {@code ProgressionGates.tryOpen(player, nerofactions:<faction_path>_inner_circle)} — composing
 * faction standing into Core's progression-gate arc (the seven shipped gate definitions each
 * require the Core arc gate matching the faction's era). {@code tryOpen} is used deliberately:
 * if the Core prerequisite is not yet met, the gate stays shut, and it opens the next time the
 * player crosses into the inner circle (decay + re-earn) or via any later
 * {@code requirementsMet}-checking path. <b>Known limitation, accepted:</b> the player must be
 * online for the open — the gate mutation needs a {@code ServerPlayer}, and reputation can change
 * for offline players (decay). An offline player who somehow gains inner circle simply gets the
 * gate on their next crossing; standing itself is never lost by the miss, and the gated recipes
 * check live standing rather than the gate alone.
 *
 * <h2>Threading</h2>
 *
 * <p>{@code ThresholdEvents.fire} is server-thread-only by contract. {@code ReputationEvents}
 * listeners run on the caller's thread, and every NeroFactions reputation write happens on the
 * server thread (commands, the tick sweep, membership actions, on-read decay), so this listener
 * inherits the right thread. Defensively, if a bound server exists and this is <em>not</em> its
 * thread, the change is dropped with a warning rather than firing cross-thread.
 */
public final class TierCrossings {

    /** The one channel this mod publishes. The namespace is load-bearing for consumers. */
    public static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID, "reputation_tier");

    private static final String GATE_SUFFIX = "_inner_circle";

    private static boolean initialised;

    /**
     * The link module's <b>single</b> per-player crossing slot ({@link #setPlayerCrossingConsumer}),
     * or {@code null} when no link module is registered. Unlike the shared {@link ThresholdEvents}
     * bus — whose contract forbids player identifiers, which is exactly why the link module cannot
     * ride it — this seam hands over the player UUID so the consumer can route a private,
     * requester-scoped event. It is notified <em>last</em>, after the bus, the reward engine and
     * the gate composition, so a companion client is never told about a milestone before the mod
     * has finished reacting to it; and a consumer failure is swallowed here, so a broken link
     * module can never disturb the reputation flow.
     */
    private static volatile PlayerCrossingConsumer playerCrossingConsumer;

    private TierCrossings() {
    }

    /** One player's boundary crossing, with the player resolved — the link module's event feed. */
    @FunctionalInterface
    public interface PlayerCrossingConsumer {
        void accept(UUID player, FactionDefinition faction, ThresholdCrossing crossing);
    }

    /**
     * Fills the single per-player crossing slot (see {@link #playerCrossingConsumer}). Public only
     * because the link package is a sibling — this is the link module's seam, not API; nothing
     * else should register here, and a second registration replaces the first.
     */
    public static void setPlayerCrossingConsumer(PlayerCrossingConsumer consumer) {
        playerCrossingConsumer = consumer;
    }

    /** Subscribes to Core's reputation bus. Called once from {@code NeroFactionsCommon.init()}. */
    public static void init() {
        if (initialised) {
            return; // Core's bus has no unsubscribe; never stack duplicate listeners.
        }
        initialised = true;
        ReputationEvents.onChange(TierCrossings::onReputationChange);
    }

    private static void onReputationChange(ReputationChange change) {
        MinecraftServer server = NeroFactionsCommon.REPUTATION_PROVIDER.boundServer();
        if (server != null && !server.isSameThread()) {
            // Should be unreachable (see class javadoc); refusing beats corrupting listeners.
            NeroFactionsCommon.LOGGER.warn(
                    "[NeroFactions] Reputation change for {} arrived off the server thread; "
                            + "no tier crossing published.", change.faction());
            return;
        }
        FactionDefinition faction = (server != null
                ? FactionDefinitions.factionsForServer(server)
                : FactionDefinitions.factions()).get(change.faction());
        if (faction == null) {
            // Another mod's faction id through the shared ReputationApi, or content not loaded
            // yet. Not ours to announce.
            NeroFactionsCommon.LOGGER.debug(
                    "[NeroFactions] Reputation change for unknown faction {}; no tier crossing.",
                    change.faction());
            return;
        }
        List<ThresholdCrossing> crossings = crossings(faction, change.oldValue(), change.newValue());
        boolean reachedInnerCircle = fireAll(crossings);
        if (server != null && !crossings.isEmpty()) {
            // Rewards AFTER the bus: the ecosystem always hears about a milestone before its
            // rewards land (and the reward engine's own watermark write is then observable last).
            RewardGrants.onCrossings(server, change.player(), faction, crossings);
        }
        if (reachedInnerCircle && server != null) {
            openInnerCircleGate(server, change, faction);
        }
        // The link module hears about it LAST — after the ecosystem bus, the rewards and the gate
        // — so a companion client is never ahead of the mod itself.
        notifyPlayerCrossingConsumer(change.player(), faction, crossings);
    }

    /**
     * Hands each crossing to the link module's consumer (if one is registered), player resolved.
     * Package-private for tests. Every consumer call is individually guarded: a link failure is
     * logged (faction only, never the player — POPIA/GDPR) and must never reach the reputation
     * flow that triggered it.
     */
    static void notifyPlayerCrossingConsumer(UUID player, FactionDefinition faction,
            List<ThresholdCrossing> crossings) {
        PlayerCrossingConsumer consumer = playerCrossingConsumer;
        if (consumer == null || player == null || crossings.isEmpty()) {
            return;
        }
        for (ThresholdCrossing crossing : crossings) {
            try {
                consumer.accept(player, faction, crossing);
            } catch (RuntimeException e) {
                NeroFactionsCommon.LOGGER.warn(
                        "[NeroFactions] The link crossing consumer failed for faction {}.",
                        faction.id(), e);
            }
        }
    }

    /**
     * Enumerates and fires every boundary crossing between the two values' tiers.
     *
     * @return whether the last crossing fired was a rising arrival at the inner circle
     */
    static boolean publish(FactionDefinition faction, int oldValue, int newValue) {
        return fireAll(crossings(faction, oldValue, newValue));
    }

    /**
     * Fires every crossing in order on Core's shared bus.
     *
     * @return whether the last crossing fired was a rising arrival at the inner circle
     */
    private static boolean fireAll(List<ThresholdCrossing> crossings) {
        for (ThresholdCrossing crossing : crossings) {
            ThresholdEvents.fire(crossing);
        }
        if (crossings.isEmpty()) {
            return false;
        }
        ThresholdCrossing last = crossings.get(crossings.size() - 1);
        return last.rising() && last.value() == FactionTier.INNER_CIRCLE.ordinal();
    }

    /**
     * The pure boundary enumeration (see the class javadoc for the payload shape). Rising
     * boundaries come back in ascending tier order, falling in descending — the order they are
     * actually crossed. Same tier on both sides — however far apart the raw values — is no
     * crossing at all. Public: {@code RewardGrants} (and its tests) consume the same enumeration
     * so the event and reward engines can never drift apart.
     */
    public static List<ThresholdCrossing> crossings(FactionDefinition faction, int oldValue, int newValue) {
        FactionTier oldTier = FactionTiers.tierOf(faction, oldValue);
        FactionTier newTier = FactionTiers.tierOf(faction, newValue);
        if (oldTier == newTier) {
            return List.of();
        }
        String scope = faction.id().toString();
        FactionTier[] tiers = FactionTier.values();
        List<ThresholdCrossing> out = new ArrayList<>(Math.abs(newTier.ordinal() - oldTier.ordinal()));
        if (newTier.ordinal() > oldTier.ordinal()) {
            for (int ordinal = oldTier.ordinal() + 1; ordinal <= newTier.ordinal(); ordinal++) {
                out.add(boundary(faction, scope, tiers[ordinal], true));
            }
        } else {
            for (int ordinal = oldTier.ordinal(); ordinal > newTier.ordinal(); ordinal--) {
                out.add(boundary(faction, scope, tiers[ordinal], false));
            }
        }
        return List.copyOf(out);
    }

    /** One boundary, named by the tier on its upper side. */
    private static ThresholdCrossing boundary(FactionDefinition faction, String scope,
            FactionTier upperTier, boolean rising) {
        Integer threshold = faction.threshold(upperTier);
        return new ThresholdCrossing(CHANNEL, scope, upperTier.ordinal(),
                threshold == null ? 0L : threshold.longValue(), rising);
    }

    /**
     * Composes the arrival into Core's progression arc: opens
     * {@code nerofactions:<faction_path>_inner_circle} if its Core prerequisites are met. The gate
     * id is derived from the faction id's <em>path</em> (datapack factions from other namespaces
     * compose too, as long as their pack ships the matching gate definition).
     */
    private static void openInnerCircleGate(MinecraftServer server, ReputationChange change,
            FactionDefinition faction) {
        ServerPlayer player = server.getPlayerList().getPlayer(change.player());
        if (player == null) {
            // Offline (decay can move offline standing). See the class javadoc: accepted miss —
            // the gate opens on the player's next rising crossing into the inner circle.
            return;
        }
        Identifier gate = Identifier.fromNamespaceAndPath(NeroFactionsCommon.MOD_ID,
                faction.id().getPath() + GATE_SUFFIX);
        ProgressionGates.tryOpen(player, gate);
    }
}
