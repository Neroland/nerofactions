package za.co.neroland.nerofactions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.data.NeroFactionsData;
import za.co.neroland.nerofactions.network.FactionsNetwork;
import za.co.neroland.nerofactions.platform.Services;
import za.co.neroland.nerofactions.reputation.ServerReputationProvider;
import za.co.neroland.nerofactions.telemetry.NeroFactionsTelemetry;

/**
 * Loader-agnostic entry point for NeroFactions. Each loader entry point (Fabric / Forge / NeoForge)
 * calls {@link #init()} once during mod construction.
 *
 * <p>The ordering below is not cosmetic. Fabric registers <em>eagerly</em> — the moment a registry
 * class is touched — so anything that must exist before something else has to be listed before it,
 * on every loader, whether or not that loader would have cared. The numbered steps are the
 * ecosystem convention; later stages fill the slots that are currently placeholders rather than
 * inserting themselves wherever is convenient.
 */
public final class NeroFactionsCommon {

    public static final String MOD_ID = "nerofactions";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroFactions");

    /**
     * The one real Core reputation provider, bound to {@code ReputationApi} in {@link #init()} and
     * rebound to each server lifecycle by the loaders' started/stopped hooks (the NeroEconomy
     * {@code CURRENCY_PROVIDER} shape).
     */
    public static final ServerReputationProvider REPUTATION_PROVIDER = new ServerReputationProvider();

    private NeroFactionsCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroFactions] common init");

        // 0. Platform seams, resolved here during construction and never lazily on a tick path — a
        //    late ServiceLoader read can throw ServiceConfigurationError out of gameplay code
        //    (Nerospace crash precedent MC-NEROSPACE-F).
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroFactionsConfig.init();

        // 2. Anonymous, NeroFactions-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. Inert until a real
        //    Sentry DSN is configured (see NeroFactionsTelemetry's PLACEHOLDER_DSN guard — no
        //    NeroFactions Sentry project exists yet, so this is a hard no-op today).
        NeroFactionsTelemetry.init();

        // 3. (later stages) Registries and content — reputation providers, faction definitions,
        //    items/blocks if any. Ordered before the data layer so stores can name registered
        //    content, and on Fabric "before" is literal.

        // 4. Player-data erasure registration + the real Core reputation provider — registered
        //    before any faction data can exist, because registering late is how an erasure request
        //    silently misses a store (POPIA/GDPR). Also binds REPUTATION_PROVIDER so
        //    ReputationApi.hasRealProvider() is true from startup; the loaders' server
        //    started/stopped hooks bind/unbind the running server it reads.
        NeroFactionsData.init();

        // 5. Declare the payloads before any loader registers them: every loader entry point runs
        //    this method first, then wires its own networking. On Forge in particular the channel
        //    is sealed at build(), so a payload declared later would never exist. Currently
        //    declares nothing — later stages add their payloads inside FactionsNetwork.init().
        FactionsNetwork.init();

        // 6. (later stages) The NeroLink module goes LAST, so a companion client is never told
        //    about something before the mod itself has finished reacting to it, and its own init
        //    swallows any failure — a broken link module must never take the faction layer down
        //    with it.
    }
}
