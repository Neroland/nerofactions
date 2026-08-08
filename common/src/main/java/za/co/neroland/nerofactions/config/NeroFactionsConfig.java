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

    // --- Factions, tiers and membership (server-authoritative) --------------

    public static final ConfigValue<Boolean> ALLOW_MULTIPLE_FACTIONS = SCHEMA.bool(
            "allowMultipleFactions", false, true,
            "Whether a player may belong to more than one faction at once. Off (the default) is the "
                    + "single-allegiance rule: joining a second faction is refused until the first is left.");

    public static final ConfigValue<Integer> JOIN_COOLDOWN_MINUTES = SCHEMA.intRange(
            "joinCooldownMinutes", 30, 0, 10_080, true,
            "Real-time minutes a player must wait after leaving a faction before joining any faction "
                    + "again. 0 disables the cooldown. Max one week (10080).");

    public static final ConfigValue<Integer> SWITCH_PENALTY_POINTS = SCHEMA.intRange(
            "switchPenaltyPoints", 50, 0, 100_000, true,
            "Reputation points LOST with a faction the moment a player leaves it (applied as a negative "
                    + "adjustment; 0 disables). The remainder then decays per decayPointsPerDay.");

    public static final ConfigValue<Integer> DECAY_POINTS_PER_DAY = SCHEMA.intRange(
            "decayPointsPerDay", 25, 0, 100_000, true,
            "How many reputation points a LEFT faction's standing moves toward 0 per whole real-time "
                    + "day after leaving. Standing is never wiped or frozen by leaving; it erodes at this "
                    + "rate and stops at 0. 0 disables decay.");

    public static final ConfigValue<Double> ENEMY_BLEED_RATIO = SCHEMA.doubleRange(
            "enemyBleedRatio", 0.5D, 0.0D, 1.0D, true,
            "When a source award grants reputation with a faction, each faction on its enemies list "
                    + "loses round(award * this ratio). Applies only to source awards (never to decay or "
                    + "direct writes). 0.0 disables enemy bleed entirely.");

    // Per-source weights and per-faction-per-source daily caps. Quests and events weigh highest,
    // trade lowest (the locked source model); ADMIN awards have weight 1, no cap and no enemy
    // bleed by design — operator actions must be exact.

    public static final ConfigValue<Double> QUEST_SOURCE_WEIGHT = SCHEMA.doubleRange(
            "questSourceWeight", 1.0D, 0.0D, 10.0D, true,
            "Multiplier applied to reputation awarded from quests.");

    public static final ConfigValue<Integer> QUEST_DAILY_CAP = SCHEMA.intRange(
            "questDailyCap", 300, 0, 1_000_000, true,
            "Max reputation a player can earn per faction per real-time day from quests.");

    public static final ConfigValue<Double> EVENT_SOURCE_WEIGHT = SCHEMA.doubleRange(
            "eventSourceWeight", 1.0D, 0.0D, 10.0D, true,
            "Multiplier applied to reputation awarded from server events.");

    public static final ConfigValue<Integer> EVENT_DAILY_CAP = SCHEMA.intRange(
            "eventDailyCap", 300, 0, 1_000_000, true,
            "Max reputation a player can earn per faction per real-time day from server events.");

    public static final ConfigValue<Double> COMBAT_SOURCE_WEIGHT = SCHEMA.doubleRange(
            "combatSourceWeight", 0.6D, 0.0D, 10.0D, true,
            "Multiplier applied to reputation awarded from combat.");

    public static final ConfigValue<Integer> COMBAT_DAILY_CAP = SCHEMA.intRange(
            "combatDailyCap", 150, 0, 1_000_000, true,
            "Max reputation a player can earn per faction per real-time day from combat.");

    public static final ConfigValue<Double> TRADE_SOURCE_WEIGHT = SCHEMA.doubleRange(
            "tradeSourceWeight", 0.3D, 0.0D, 10.0D, true,
            "Multiplier applied to reputation awarded from trading.");

    public static final ConfigValue<Integer> TRADE_DAILY_CAP = SCHEMA.intRange(
            "tradeDailyCap", 100, 0, 1_000_000, true,
            "Max reputation a player can earn per faction per real-time day from trading.");

    // --- Data retention (POPIA/GDPR, server-authoritative) ------------------

    public static final ConfigValue<Integer> RETENTION_DAYS = SCHEMA.intRange(
            "retentionDays", 365, 0, 3650, true,
            "Days of player inactivity (per Neroland Core's shared last-seen record) after which "
                    + "this mod's own daily sweep purges that player's NeroFactions data - standing, "
                    + "memberships, cooldown, decay bookkeeping, accrual counters, reward watermarks "
                    + "and the recovery backups. Complements Core's ecosystem-wide dataRetentionDays "
                    + "sweep (opt-in, default 0, driven by /neroland data purge-inactive), which "
                    + "already erases NeroFactions data through the shared erasure hook; this key "
                    + "lets faction data expire on its own schedule even when Core's sweep is off. "
                    + "0 disables this mod's sweep. Players with no Core activity record are never "
                    + "auto-purged (their inactivity cannot be established).");

    // --- Soft integrations (server-authoritative) ---------------------------

    public static final ConfigValue<Integer> COMBAT_AWARD_BASE = SCHEMA.intRange(
            "combatAwardBase", 2, 0, 1_000, true,
            "Base reputation awarded (before the combat weight and daily cap) with EACH faction the "
                    + "player is a member of when they kill a hostile monster. 0 disables the "
                    + "internal combat trigger entirely.");

    public static final ConfigValue<Integer> TRADE_AWARD_BASE = SCHEMA.intRange(
            "tradeAwardBase", 2, 0, 100, true,
            "Base reputation awarded (before the trade weight and daily cap) with a faction each "
                    + "time the player completes a trade at its trade terminal. 0 disables the "
                    + "terminal's reputation earning entirely.");

    public static final ConfigValue<Integer> DISCOUNT_CAP_PERCENT = SCHEMA.intRange(
            "discountCapPercent", 15, 0, 50, true,
            "With NeroEconomy installed: the largest total market discount (percent) faction "
                    + "standing can ever grant a buyer, regardless of datapack trade multipliers.");

    public static final ConfigValue<Integer> SURCHARGE_CAP_PERCENT = SCHEMA.intRange(
            "surchargeCapPercent", 25, 0, 100, true,
            "With NeroEconomy installed: the total market surcharge (percent) applied when a "
                    + "listing's aligned faction lists one of the buyer's factions as an enemy - "
                    + "this single knob is both the surcharge and its cap.");

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
