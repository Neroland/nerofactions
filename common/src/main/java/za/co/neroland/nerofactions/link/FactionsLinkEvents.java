package za.co.neroland.nerofactions.link;

import java.util.UUID;

import com.google.gson.JsonObject;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.reputation.TierCrossings;
import za.co.neroland.nerolandcore.event.ThresholdEvents.ThresholdCrossing;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

/**
 * The live half of the link module: one topic, {@code standing} — the requester's own standing
 * crossed a tier boundary.
 *
 * <p>{@link #init()} fills {@link TierCrossings}' single per-player crossing seam, so this class
 * observes exactly the boundary enumeration the rest of the ecosystem does (one event per boundary
 * crossed, upper-side tier semantics, rising and falling both real) — the link event can never
 * drift from the {@code nerofactions:reputation_tier} channel because both are fed by the same
 * enumeration. The seam is notified <em>after</em> the shared bus, the tier-reward engine and the
 * gate composition have all run, so a companion client is never told about a milestone before the
 * mod itself has finished reacting to it.
 *
 * <h2>Scope and privacy (POPIA/GDPR)</h2>
 *
 * <p>Every event is published with {@link LinkEvent#forPlayer}: Core routes it to the one player
 * whose standing changed and to nobody else's sessions. The payload itself carries <b>no UUID, no
 * name, no player identifier of any kind</b> — the {@code LinkEvent}'s own routing field is the
 * only place the player appears, so a payload logged, cached or forwarded by a client tool names a
 * faction and a tier, never a person. There are <b>no broadcasts</b>: a tier change is one
 * player's business, and a broadcast variant — however anonymised — would let any session count
 * the server's promotions.
 *
 * <p><b>No alerts in 0.1.0, deliberately.</b> Core's {@code LinkAlerts} store is
 * {@code ApiStatus.Internal}, and nothing NeroFactions reports is urgent in the way a failed life
 * support system is: standing moves because the player played, and the event (delivered live or on
 * next connect) covers it. If that judgement ever changes, alerts will arrive with the same
 * requester-only scope as everything else here.
 *
 * <p><b>Nothing here may throw at its caller.</b> The publisher is wrapped: a link failure must
 * never disturb the reputation flow that triggered it.
 *
 * <p>Server thread only (inherited from {@code TierCrossings}' own threading contract).
 */
public final class FactionsLinkEvents {

    private FactionsLinkEvents() {
    }

    /**
     * Fills the tier-crossing seam. Called from {@link FactionsLinkModule#init()} — and only when
     * the module is enabled, so a disabled link module never even observes crossings. Idempotent:
     * the seam is a single slot, so a second init replaces rather than stacks.
     */
    static void init() {
        TierCrossings.setPlayerCrossingConsumer(FactionsLinkEvents::onTierCrossing);
    }

    /**
     * One boundary crossing for one player, straight from {@link TierCrossings}. Package-private
     * so tests can drive it directly.
     */
    static void onTierCrossing(UUID playerId, FactionDefinition faction, ThresholdCrossing crossing) {
        if (playerId == null || faction == null || crossing == null || !enabled()) {
            return;
        }
        try {
            NeroLinkRegistry.eventBus().publish(LinkEvent.forPlayer(
                    FactionsLinkModule.MODULE_ID, FactionsLinkModule.TOPIC_STANDING,
                    playerId, payloadFor(faction, crossing)));
        } catch (RuntimeException e) {
            // Topic only — never who the event was for (POPIA/GDPR).
            NeroFactionsCommon.LOGGER.warn(
                    "[NeroFactions] Publishing the NeroLink '{}' event failed.",
                    FactionsLinkModule.TOPIC_STANDING, e);
        }
    }

    /**
     * The payload: faction + boundary, nothing player-shaped. {@code tier} is the ordinal of the
     * tier on the <b>upper side</b> of the crossed boundary — the tier just reached when
     * {@code rising}, the tier just lost when not — mirroring the
     * {@code nerofactions:reputation_tier} channel exactly. Pure; package-private for tests.
     */
    static JsonObject payloadFor(FactionDefinition faction, ThresholdCrossing crossing) {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", FactionsLinkModule.SCHEMA_VERSION);
        payload.addProperty("faction", faction.id().toString());
        payload.addProperty("tier", crossing.value());
        payload.addProperty("tier_name", tierName(crossing.value()));
        payload.addProperty("rising", crossing.rising());
        return payload;
    }

    /** The tier's lowercase JSON name; defensive against an out-of-band ordinal. */
    private static String tierName(long ordinal) {
        FactionTier[] tiers = FactionTier.values();
        return ordinal >= 0 && ordinal < tiers.length ? tiers[(int) ordinal].jsonName() : "unknown";
    }

    private static boolean enabled() {
        try {
            return NeroFactionsConfig.LINK_MODULE_ENABLED.get();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
