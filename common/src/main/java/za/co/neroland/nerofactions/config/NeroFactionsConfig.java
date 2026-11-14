package za.co.neroland.nerofactions.config;

import java.util.EnumMap;
import java.util.Map;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;

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
            "On by default: send anonymous, NeroFactions-only crash reports (Sentry, EU servers) - stack trace, "
                    + "mod/MC/loader/OS/Java versions, your other installed mods, this mod's config, "
                    + "recent in-game actions, anonymous stability/timing; no IP, username, UUID, world "
                    + "data, faction membership, reputation values or chat; file paths scrubbed of your "
                    + "account name. Set false to opt out of all of it. See PRIVACY.md");

    // --- Ecosystem integration (server-authoritative) -----------------------

    public static final ConfigValue<Boolean> LINK_MODULE_ENABLED = SCHEMA.bool(
            "linkModuleEnabled", true, true,
            "Whether the NeroLink companion module is registered. Snapshots are per-player scoped and "
                    + "never enumerate other players.");

    public static final ConfigValue<Boolean> LINK_REROLL_ENABLED = SCHEMA.bool(
            "linkRerollEnabled", true, true, "Allow online players to spend their daily contract reroll through NeroLink.");

    public static final ConfigValue<Boolean> OUTPOST_VISITS_ENABLED = SCHEMA.bool(
            "outpostVisitsEnabled", true, true, "Record outpost visits by faction and dimension for export and NeroLink.");

    // --- Factions, tiers and membership (server-authoritative) --------------

    public static final ConfigValue<Integer> HOSTILE_BELOW_STANDING = SCHEMA.intRange(
            "hostileBelowStanding", -100, -1000000, -1, true,
            "Standing below this value triggers hostility at faction outposts.");

    private static final Map<OutpostFaction, ConfigValue<Boolean>> OUTPOSTS = registerOutpostToggles();

    private static Map<OutpostFaction, ConfigValue<Boolean>> registerOutpostToggles() {
        Map<OutpostFaction, ConfigValue<Boolean>> toggles = new EnumMap<>(OutpostFaction.class);
        for (OutpostFaction faction : OutpostFaction.values()) {
            if (faction != OutpostFaction.NONE) {
                String id = faction.getSerializedName();
                toggles.put(faction, SCHEMA.bool("outposts." + id, true, true,
                        "Generate " + id + " outposts in new chunks."));
            }
        }
        return Map.copyOf(toggles);
    }

    public static boolean outpostEnabled(OutpostFaction faction) {
        ConfigValue<Boolean> enabled = OUTPOSTS.get(faction);
        return enabled != null && enabled.get();
    }

    public static final ConfigValue<Boolean> REGISTRY_ENABLED = SCHEMA.bool(
            "registryEnabled", true, true, "Allow faction membership changes at the Faction Registry.");

    public static final ConfigValue<Boolean> CONTRACTS_ENABLED = SCHEMA.bool(
            "contractsEnabled", true, true, "Enable Contract Board assignments, objective tracking and rewards.");

    public static final ConfigValue<Boolean> QUOTA_BOARD_ENABLED = SCHEMA.bool(
            "quotaBoardEnabled", true, true, "Allow Miner Union members to deposit resources at a Quota Board.");

    public static final ConfigValue<Integer> QUOTA_BASE_TONNES = SCHEMA.intRange(
            "quotaBaseTonnes", 64, 1, 819, true,
            "Base weekly Miner Union quota in tonnes. Multiplied by 1 through 5 according to the member's standing tier.");

    public static final ConfigValue<Boolean> NAVIGATION_BEACON_ENABLED = SCHEMA.bool(
            "navigationBeaconEnabled", true, true, "Enable waypoint binding and the Navigation Beacon's 24-block fall protection.");

    public static final ConfigValue<Boolean> VOID_ALTAR_ENABLED = SCHEMA.bool(
            "voidAltarEnabled", true, true, "Enable Void Cult rites.");
    public static final ConfigValue<Integer> VOID_RITE_SECONDS = SCHEMA.intRange(
            "voidRiteSeconds", 90, 15, 180, true, "Seconds a successful rite's reach and wither protection last.");
    public static final ConfigValue<Integer> VOID_BACKLASH_PERCENT = SCHEMA.intRange(
            "voidBacklashPercent", 15, 0, 100, true, "Percent chance that a Void Cult rite backfires.");

    public static final ConfigValue<Boolean> CORPORATE_COUNTER_ENABLED = SCHEMA.bool(
            "corporateCounterEnabled", true, true, "Allow Corporate premium deliveries, stamping and writ redemption.");
    public static final ConfigValue<Integer> MARKET_NUDGE_CAP = SCHEMA.intRange(
            "marketNudgeCapPercent", 5, 0, 15, true, "Weekly Corporate price increase for one metal commodity. Zero disables it.");

    public static final ConfigValue<Boolean> ATMOSPHERE_PROCESSOR_ENABLED = SCHEMA.bool(
            "atmosphereProcessorEnabled", true, true, "Allow Authority members to run Atmosphere Processor pulses.");
    public static final ConfigValue<Integer> ATMOSPHERE_RADIUS = SCHEMA.intRange(
            "atmosphereProcessorRadius", 6, 1, 8, true, "Maximum horizontal conversion radius in blocks.");

    public static final ConfigValue<Boolean> COMMUNITY_WORKBENCH_ENABLED = SCHEMA.bool(
            "communityWorkbenchEnabled", true, true, "Allow Colonists to use Community Workbench crafting and shared storage.");

    public static final ConfigValue<Boolean> DECONSTRUCTOR_ENABLED = SCHEMA.bool(
            "deconstructorEnabled", true, true, "Enable Salvagers to recover materials from eligible crafted items.");
    public static final ConfigValue<Integer> DECONSTRUCTOR_YIELD = SCHEMA.intRange(
            "deconstructorYieldPercent", 50, 1, 75, true, "Maximum percentage of each ingredient recovered per batch.");

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

    public static final ConfigValue<Integer> SALE_AWARD_BASE = SCHEMA.intRange(
            "saleAwardBase", 1, 0, 100, true,
            "With NeroEconomy installed: base reputation awarded (before the trade weight and daily "
                    + "cap) with each faction the seller belongs to when they sell one of that "
                    + "faction's speciality goods on the market. 0 disables sale reputation.");

    public static final ConfigValue<Integer> TERMINAL_CREDITS_PER_EMERALD = SCHEMA.intRange(
            "terminalCreditsPerEmerald", 8, 0, 10_000, true,
            "With an economy installed: credits one emerald of terminal list price is worth on the "
                    + "trade terminal's credit counter (before faction rates). 0 turns the credit "
                    + "counter off; the emerald trades are unaffected.");

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
