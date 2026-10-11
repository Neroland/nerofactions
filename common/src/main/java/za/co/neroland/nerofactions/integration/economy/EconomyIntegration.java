package za.co.neroland.nerofactions.integration.economy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroeconomy.api.EconomyCaller;
import za.co.neroland.neroeconomy.api.EconomyEvents;
import za.co.neroland.neroeconomy.api.EconomyResult;
import za.co.neroland.neroeconomy.api.ForeignAccountId;
import za.co.neroland.neroeconomy.api.MarketListingView;
import za.co.neroland.neroeconomy.api.PriceContext;
import za.co.neroland.neroeconomy.api.PriceModifierRegistry;
import za.co.neroland.neroeconomy.api.SiblingEconomy;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;
import za.co.neroland.nerofactions.integration.EconomyOutcome;
import za.co.neroland.nerofactions.reputation.SaleReputation;

/**
 * The NeroEconomy bridge — <b>the only class in NeroFactions that imports
 * {@code za.co.neroland.neroeconomy} types</b>. NeroEconomy is {@code compileOnly}: nothing of it
 * ships in this jar, the loader metadata names it as optional only, and this class is classloaded
 * only from {@code Integrations.init()} (strictly behind the {@code isModLoaded("neroeconomy")}
 * guard) and from {@code FactionEconomy} (only once that guard has passed and the sibling API was
 * claimed). When NeroEconomy is absent this class is never touched.
 *
 * <p><b>What it wires:</b>
 *
 * <ol>
 *   <li><b>Faction pricing</b> ({@link #register()}): one {@code PriceModifier} under the id
 *       {@code "nerofactions"}; the decision itself is {@link FactionPricing}. NeroEconomy applies a
 *       modifier to the buyer's price only, and funds a discount from the Reserve only up to the
 *       market fee collected on that sale (2 % by default). The faction discount (capped by
 *       {@code discountCapPercent}, 15 % by default) is therefore <b>effectively capped at the
 *       market fee</b>: the sale event carries no record of the discount applied, so a treasury
 *       top-up of the difference could not be paid exactly and is not attempted.</li>
 *   <li><b>The sibling API</b> (NeroEconomy 0.3.0+, {@code API_VERSION} 1+, read reflectively
 *       because {@code javac} inlines the constant): the caller {@code nerofactions} is claimed at
 *       common init with the reasons {@code dues}, {@code treasury_payout} and
 *       {@code terminal_purchase}; faction treasuries are {@code ForeignAccountId("nerofactions",
 *       <faction id>)} accounts; and {@code EconomyEvents.onSold} feeds {@link SaleReputation}.
 *       With an older NeroEconomy all of this stays dormant and pricing alone runs.</li>
 * </ol>
 *
 * <p><b>Determinism &amp; side-effects (pricing).</b> NeroEconomy's {@code PriceModifier} contract
 * requires implementations to be deterministic and side-effect-free, so the modifier reads the
 * buyer's standing <em>as currently stored</em> and deliberately does NOT run {@code FactionDecay}
 * first. Standing a decay pass has not yet caught up with is visible here for up to one ticker
 * pass (about a minute) — bounded, harmless staleness.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> player UUIDs are used on the server for the one call at hand.
 * Nothing here logs a player id or name with a payment or charge, request keys are never built
 * from a player id or name (none are used), and the sale event is not kept.
 */
public final class EconomyIntegration {

    /** The reason codes NeroFactions claims; a closed set. */
    static final String REASON_DUES = "dues";
    static final String REASON_TREASURY_PAYOUT = "treasury_payout";
    static final String REASON_TERMINAL_PURCHASE = "terminal_purchase";

    private static volatile EconomyCaller caller;

    private EconomyIntegration() {
    }

    /**
     * Registers the {@code "nerofactions"} price modifier. Called once, from
     * {@code Integrations.init()}, only when NeroEconomy is present; any {@link LinkageError} is
     * caught at that guard boundary, not here.
     */
    public static void register() {
        PriceModifierRegistry.register(NeroFactionsCommon.MOD_ID, EconomyIntegration::factionMultiplier);
    }

    /**
     * Claims the sibling caller and subscribes to sale events, when the loaded NeroEconomy has API
     * version 1 or later. Called once from common init, after {@link #register()}. Never throws.
     *
     * @return true when the caller was claimed (treasuries and the credit counter can run)
     */
    public static boolean registerSiblingApi() {
        int version = loadedApiVersion();
        if (version < 1) {
            NeroFactionsCommon.LOGGER.info("[NeroFactions] NeroEconomy sibling API not available "
                    + "(version {}); treasuries and sale reputation stay off.", version);
            return false;
        }
        try {
            caller = SiblingEconomy.claim(NeroFactionsCommon.MOD_ID,
                    Set.of(REASON_DUES, REASON_TREASURY_PAYOUT, REASON_TERMINAL_PURCHASE));
            EconomyEvents.onSold(sold -> SaleReputation.onSale(sold.seller(), sold.itemId(), sold.quantity()));
            return true;
        } catch (LinkageError | RuntimeException e) {
            caller = null;
            NeroFactionsCommon.LOGGER.warn("[NeroFactions] Could not claim the NeroEconomy sibling API; "
                    + "treasuries and sale reputation stay off.", e);
            return false;
        }
    }

    /**
     * The API version of the NeroEconomy jar actually loaded, read reflectively ({@code javac}
     * copies a {@code static final int} into this class at compile time). 0 when it is missing.
     */
    private static int loadedApiVersion() {
        try {
            return SiblingEconomy.class.getField("API_VERSION").getInt(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return 0;
        }
    }

    // --- treasuries and the terminal (called through FactionEconomy only) ----------------------

    /** Member to treasury, reason {@code dues}. */
    public static EconomyOutcome payDues(MinecraftServer server, UUID member, Identifier faction, long amount) {
        EconomyCaller handle = caller;
        Optional<ForeignAccountId> treasury = treasuryOf(faction);
        if (handle == null) {
            return EconomyOutcome.unavailable();
        }
        if (treasury.isEmpty()) {
            return EconomyOutcome.refused("message.nerofactions.treasury.invalid_faction");
        }
        return outcome(handle.deposit(server, member, treasury.get(), amount, REASON_DUES, null));
    }

    /** Treasury to member, reason {@code treasury_payout}. */
    public static EconomyOutcome payout(MinecraftServer server, Identifier faction, UUID payee, long amount) {
        EconomyCaller handle = caller;
        Optional<ForeignAccountId> treasury = treasuryOf(faction);
        if (handle == null) {
            return EconomyOutcome.unavailable();
        }
        if (treasury.isEmpty()) {
            return EconomyOutcome.refused("message.nerofactions.treasury.invalid_faction");
        }
        return outcome(handle.withdraw(server, treasury.get(), payee, amount, REASON_TREASURY_PAYOUT, null));
    }

    /** What the faction's treasury holds; 0 for an unknown or invalid faction, -1 when unavailable. */
    public static long treasuryBalance(MinecraftServer server, Identifier faction) {
        EconomyCaller handle = caller;
        if (handle == null) {
            return -1L;
        }
        return treasuryOf(faction).map(account -> handle.balance(server, account)).orElse(0L);
    }

    /** Player to Reserve for a terminal purchase, reason {@code terminal_purchase}. */
    public static EconomyOutcome chargeTerminal(MinecraftServer server, UUID buyer, long price) {
        EconomyCaller handle = caller;
        if (handle == null) {
            return EconomyOutcome.unavailable();
        }
        return outcome(handle.charge(server, buyer, price, REASON_TERMINAL_PURCHASE, null));
    }

    private static Optional<ForeignAccountId> treasuryOf(Identifier faction) {
        return FactionAccountId.valueFor(faction)
                .map(value -> ForeignAccountId.parse(FactionAccountId.KIND, value));
    }

    private static EconomyOutcome outcome(EconomyResult result) {
        if (result == null) {
            return EconomyOutcome.unavailable();
        }
        return result.ok() ? EconomyOutcome.applied() : EconomyOutcome.refused(result.translationKey());
    }

    // --- pricing ---------------------------------------------------------------------------------

    /**
     * The modifier body. Never throws: any surprise degrades to list price ({@code 1.0}) — a
     * pricing bridge must never take a market terminal down, and NeroEconomy would only skip a
     * throwing modifier anyway.
     */
    private static double factionMultiplier(PriceContext context) {
        try {
            MinecraftServer server = NeroFactionsCommon.REPUTATION_PROVIDER.boundServer();
            if (server == null || context == null) {
                return 1.0D;
            }
            Map<Identifier, FactionDefinition> factions = FactionDefinitions.factionsForServer(server);
            if (factions.isEmpty()) {
                return 1.0D;
            }
            // Resolve the buyer's tier per faction from standing as stored (see the class javadoc
            // for why decay is deliberately not applied here).
            Map<Identifier, FactionTier> buyerTiers = new LinkedHashMap<>();
            for (FactionDefinition faction : factions.values()) {
                int standing = NeroFactionsCommon.REPUTATION_PROVIDER
                        .getReputation(context.buyer(), faction.id());
                buyerTiers.put(faction.id(), FactionTiers.tierOf(faction, standing));
            }
            MarketListingView listing = context.listing();
            return FactionPricing.multiplier(listing.itemId(), listing.tags(), buyerTiers, factions,
                    NeroFactionsConfig.DISCOUNT_CAP_PERCENT.get(),
                    NeroFactionsConfig.SURCHARGE_CAP_PERCENT.get());
        } catch (RuntimeException e) {
            NeroFactionsCommon.LOGGER.debug(
                    "[NeroFactions] Faction price modifier failed; charging list price.", e);
            return 1.0D;
        }
    }
}
