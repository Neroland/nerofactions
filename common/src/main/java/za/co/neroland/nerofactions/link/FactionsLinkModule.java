package za.co.neroland.nerofactions.link;

import java.util.List;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.platform.Services;

/**
 * NeroFactions' plug into Neroland Core's link API — the seam a companion client reads a player's
 * <em>own</em> faction standing through, without NeroFactions knowing that any such client exists.
 *
 * <p>The whole module is plain server-side Java against Core's
 * {@link za.co.neroland.nerolandcore.link} package: no loader wiring, no networking of its own, no
 * HTTP. NeroFactions registers what it can show; the separate NeroLink bridge mod reads Core's
 * registry and serves it. With no bridge installed this costs one registry entry.
 *
 * <h2>Deliberately read-only — no actions, ever</h2>
 *
 * <p>Only <b>two</b> surfaces are registered from {@link NeroFactionsCommon#init()}:
 *
 * <ul>
 *   <li><b>Read</b> — {@link FactionsLinkSnapshots}, serving the {@code standing} and
 *       {@code membership} sections;</li>
 *   <li><b>Live</b> — {@link FactionsLinkEvents}, publishing the requester-scoped {@code standing}
 *       tier-change event onto Core's shared event bus.</li>
 * </ul>
 *
 * <p>There is <b>no</b> {@code LinkActionHandler} and {@link LinkModuleInfo#actionIds()} is the
 * empty list — not "empty for now", empty as policy. A faction join is a committal in-world
 * decision: it locks the single-allegiance slot, arms a cooldown on leaving, costs a switch
 * penalty, and shapes what the player can craft and trade. That is a decision to be made standing
 * in front of the world it changes, not an API call — <b>no remote allegiance mutation will ever
 * be exposed through this module</b>. Reputation itself is even further out of reach: standing is
 * earned through play and no client, companion or otherwise, may write it.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p><b>Own data only, structurally.</b> Reputation is exactly the kind of data a server-wide
 * roster would leak — "who is inner circle with whom" maps the server's social graph. Every
 * section {@link FactionsLinkSnapshots} serves is derived from the requesting {@code playerId}'s
 * own rows and nothing else; there is no parameter that names another player, no operator
 * widening, and no aggregate that could be diffed into one. Events are routed by Core to the one
 * player they concern and their payloads carry no player identifier at all (the {@code LinkEvent}'s
 * own routing field is the only place the UUID appears). No alerts are raised in 0.1.0 — Core's
 * {@code LinkAlerts} store is internal API and nothing NeroFactions reports is urgent enough to
 * outlive a session; if a future version raises any, they will be as requester-scoped as
 * everything else here.
 *
 * <p><b>Erasure needs no separate wiring.</b> Every read goes to the live stores, so a player
 * erased through Core's {@code PlayerDataErasure} hook immediately snapshots as empty. See
 * {@code PRIVACY.md}.
 *
 * <p><b>Schema version 1.</b> Bump {@link #SCHEMA_VERSION} whenever the shape of a snapshot
 * section or event payload changes, so a companion client can tell what it is parsing.
 */
public final class FactionsLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroFactionsCommon.MOD_ID;

    /** The snapshot/event schema revision. Bump on any change to a section's or payload's shape. */
    public static final int SCHEMA_VERSION = 1;

    /** Section: the requester's own standing per faction, with the resolved tier. */
    public static final String SECTION_STANDING = "standing";

    /** Section: the requester's own memberships, join cooldown and pending-decay bookkeeping. */
    public static final String SECTION_MEMBERSHIP = "membership";

    /** Topic: the requester's own standing crossed a tier boundary. Requester-scoped, never broadcast. */
    public static final String TOPIC_STANDING = "standing";

    private FactionsLinkModule() {
    }

    /**
     * Register the read and live surfaces with Core. Called <b>last</b> from
     * {@link NeroFactionsCommon#init()}, so a companion client is never told about something before
     * the mod itself has finished reacting to it.
     *
     * <p>A failure here must never take the mod down with it: factions work perfectly well with no
     * link module, so any problem is logged and swallowed. The same is true of the config switch —
     * {@code linkModuleEnabled=false} simply means nothing is registered, and every snapshot and
     * publisher checks the same flag before it speaks.
     */
    public static void init() {
        try {
            if (!NeroFactionsConfig.LINK_MODULE_ENABLED.get()) {
                NeroFactionsCommon.LOGGER.info(
                        "[NeroFactions] The NeroLink module is disabled by config; companion clients "
                                + "will not see NeroFactions data.");
                return;
            }
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION,
                    List.of(SECTION_STANDING, SECTION_MEMBERSHIP),
                    // Empty as POLICY, not as backlog — this module is read-only (class javadoc).
                    List.of());
            NeroLinkRegistry.registerSnapshotProvider(new FactionsLinkSnapshots(), info);
            FactionsLinkEvents.init();
        } catch (RuntimeException e) {
            NeroFactionsCommon.LOGGER.warn(
                    "[NeroFactions] Could not register the NeroLink module; companion clients will "
                            + "not see NeroFactions data. Factions themselves are unaffected.", e);
        }
    }

    /** This mod's public version string for discovery, or {@code "unknown"} if the seam is unhappy. */
    private static String modVersion() {
        try {
            String version = Services.PLATFORM.getModVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }
}
