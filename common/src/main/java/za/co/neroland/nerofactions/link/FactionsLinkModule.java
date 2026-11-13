package za.co.neroland.nerofactions.link;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.platform.Services;

/** Registers requester-scoped faction snapshots, events and the contract reroll. */
public final class FactionsLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroFactionsCommon.MOD_ID;

    /** The snapshot/event schema revision. Bump on any change to a section's or payload's shape. */
    public static final int SCHEMA_VERSION = 2;

    /** Section: the requester's own standing per faction, with the resolved tier. */
    public static final String SECTION_STANDING = "standing";

    /** Section: the requester's own memberships, join cooldown and pending-decay bookkeeping. */
    public static final String SECTION_MEMBERSHIP = "membership";

    /** Topic: the requester's own standing crossed a tier boundary. Requester-scoped, never broadcast. */
    public static final String TOPIC_STANDING = "standing";

    public static final String SECTION_CONTRACTS = "contracts";
    public static final String SECTION_GEAR = "gear";
    public static final String SECTION_OUTPOSTS_VISITED = "outposts_visited";
    public static final String TOPIC_CONTRACT_COMPLETED = "contract_completed";

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
            var snapshots = new FactionsLinkSnapshots();
            var actions = new FactionsLinkActions();
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, modVersion(), SCHEMA_VERSION,
                    snapshots.sections(), actions.actionIds());
            NeroLinkRegistry.registerSnapshotProvider(snapshots, info);
            NeroLinkRegistry.registerActionHandler(actions, info);
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
