package za.co.neroland.nerofactions.integration;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.integration.quests.QuestsIntegration;
import za.co.neroland.nerofactions.platform.PlatformInfo;
import za.co.neroland.nerofactions.platform.Services;

/**
 * Sibling-mod soft integrations, detected <b>exactly once</b> during common init and cached —
 * never re-asked per interaction (NeroColonies' {@code CompatRegistry} discipline: asking the
 * loader on a gameplay path is wasteful and can answer differently at different times).
 *
 * <p><b>Classload isolation is structural, not reflective.</b> {@code Class.forName} is banned in
 * this ecosystem. Instead, every class that imports a sibling's types lives in a dedicated
 * package ({@code integration.economy} is the only NeroEconomy-importing code in the mod, built
 * against a {@code compileOnly} jar) and is referenced from exactly one place: the guarded branch
 * below. The JVM loads a class the first time bytecode that mentions it actually <em>executes</em>,
 * so with the sibling absent the branch is never taken, the bridge class is never initialised and
 * its imports are never resolved. {@code QuestsIntegration}, by contrast, imports no sibling type
 * at all (the NeroQuests contract flows entirely through Core seams) and is safe to touch
 * unconditionally.
 *
 * <p>The economy branch additionally catches {@link LinkageError} at the guard boundary: if a
 * future NeroEconomy renames its pricing API, NeroFactions logs one warning and plays on without
 * faction pricing — a sibling changing shape must never crash this mod.
 *
 * <p>No sibling is required for anything: remove them all and the mod runs — the internal combat
 * trigger (see {@code trigger.InternalTriggers}) keeps standing earnable on a Core-only server.
 */
public final class Integrations {

    private static volatile boolean questsPresent;
    private static volatile boolean economyPresent;
    private static volatile boolean economyPricingRegistered;
    private static volatile boolean detected;

    private Integrations() {
    }

    /** Detects the optional siblings and wires what is present. Idempotent; called once from init. */
    public static void init() {
        if (detected) {
            return;
        }
        detected = true;

        questsPresent = questsDetected(Services.PLATFORM);
        QuestsIntegration.init(questsPresent);

        economyPresent = economyDetected(Services.PLATFORM);
        if (economyPresent) {
            try {
                // The ONLY reference to the NeroEconomy-importing bridge — see the class javadoc.
                za.co.neroland.nerofactions.integration.economy.EconomyIntegration.register();
                economyPricingRegistered = true;
                NeroFactionsCommon.LOGGER.info(
                        "[NeroFactions] NeroEconomy present - faction price modifier registered "
                                + "(speciality discounts / enemy surcharge, capped by config).");
            } catch (LinkageError | RuntimeException e) {
                NeroFactionsCommon.LOGGER.warn(
                        "[NeroFactions] NeroEconomy is present but its pricing API could not be "
                                + "reached; faction pricing is disabled and everything else runs on.", e);
            }
        } else {
            NeroFactionsCommon.LOGGER.info(
                    "[NeroFactions] NeroEconomy absent - no faction pricing; standing still gates "
                            + "recipes and progression as normal.");
        }
    }

    // --- the guard predicates (package-private seams for the plain-JVM tests) -------------------
    //
    // The tests exercise these against a stubbed PlatformInfo; that the sibling-importing class is
    // never initialised on "false" is structural (see the class javadoc) — a test cannot observe a
    // classload that, by construction, has no code path reaching it.

    /** Whether the NeroQuests contract's other half is present. */
    static boolean questsDetected(PlatformInfo platform) {
        return platform != null && platform.isModLoaded("neroquests");
    }

    /** Whether the NeroEconomy pricing bridge should be loaded and registered at all. */
    static boolean economyDetected(PlatformInfo platform) {
        return platform != null && platform.isModLoaded("neroeconomy");
    }

    // --- cached answers (read-only after init) --------------------------------------------------

    public static boolean questsPresent() {
        return questsPresent;
    }

    public static boolean economyPresent() {
        return economyPresent;
    }

    /** True only when the price modifier actually registered (present AND the API linked). */
    public static boolean economyPricingRegistered() {
        return economyPricingRegistered;
    }
}
