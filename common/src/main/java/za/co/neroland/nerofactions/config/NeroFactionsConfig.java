package za.co.neroland.nerofactions.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerofactions.NeroFactionsCommon;

/**
 * NeroFactions config schema, built on Neroland Core's config framework (file
 * {@code config/nerofactions.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroFactionsCommon#init()}, before anything else — every other
 * subsystem reads it, including telemetry's opt-out flag.
 *
 * <p>Only the foundation keys live here for now; the gameplay keys (reputation rates, faction
 * caps, unlock gating) land with the stages that read them, in one block per stage so the file
 * players see always matches what the mod actually does.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative —
 * anonymous crash reporting is a per-client opt-out that a server must never force on or off.
 */
public final class NeroFactionsConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroFactionsCommon.MOD_ID, "NeroFactions configuration.");

    // --- Crash telemetry (client-local opt-out) -----------------------------

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroFactions-only crash reports (Sentry, EU servers) - stack trace, "
                    + "mod/MC/loader/OS/Java versions, your other installed mods, this mod's config, "
                    + "recent in-game actions, anonymous stability/timing; no IP, username, UUID, world "
                    + "data, faction membership, reputation values or chat; file paths scrubbed of your "
                    + "account name. false = opt out of all of it. See PRIVACY.md");

    // --- Ecosystem integration (server-authoritative) -----------------------

    public static final ConfigValue<Boolean> LINK_MODULE_ENABLED = SCHEMA.bool(
            "linkModuleEnabled", true, true,
            "Whether the NeroLink companion module is registered. Snapshots are per-player scoped and "
                    + "never enumerate other players.");

    private NeroFactionsConfig() {
    }

    /**
     * Whether anonymous NeroFactions-only crash reporting is on (default true, opt-out). Read once at
     * bootstrap by {@code NeroFactionsTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
