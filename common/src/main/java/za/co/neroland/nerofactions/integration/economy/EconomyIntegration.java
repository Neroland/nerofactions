package za.co.neroland.nerofactions.integration.economy;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroeconomy.api.MarketListingView;
import za.co.neroland.neroeconomy.api.PriceContext;
import za.co.neroland.neroeconomy.api.PriceModifierRegistry;

import za.co.neroland.nerofactions.NeroFactionsCommon;
import za.co.neroland.nerofactions.config.NeroFactionsConfig;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionDefinitions;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.FactionTiers;

/**
 * The NeroEconomy bridge — <b>the only class in NeroFactions that imports
 * {@code za.co.neroland.neroeconomy} types</b> (with the dormant {@link FactionAccountId} seam).
 * NeroEconomy is {@code compileOnly}: nothing of it ships in this jar, no loader manifest names
 * it, and this class is classloaded exactly once, from {@code Integrations.init()}, strictly
 * behind the {@code isModLoaded("neroeconomy")} guard — the ecosystem's no-reflection rule for
 * soft integrations. When NeroEconomy is absent this class is never touched and the JVM never
 * asks for its types.
 *
 * <p>{@link #register()} contributes one {@code PriceModifier} under the id
 * {@code "nerofactions"} (the registry orders lexicographically by mod id and skips throwing
 * modifiers; NeroEconomy clamps the whole product to its own configured band on top of ours).
 * The decision itself — speciality discounts, the enemy surcharge, both hard caps — is
 * {@link FactionPricing}, pure NeroFactions math documented and unit-tested there; this adapter
 * only assembles its inputs from the running server.
 *
 * <p><b>Determinism &amp; side-effects.</b> NeroEconomy's {@code PriceModifier} contract requires
 * implementations to be deterministic and side-effect-free, so this modifier reads the buyer's
 * standing <em>as currently stored</em> and deliberately does NOT run {@code FactionDecay} first
 * (decay application is a mutation and belongs to the faction ticker alone). Standing a decay
 * pass has not yet caught up with is therefore visible here for up to one ticker pass (the
 * ticker throttles to about a minute) — bounded, harmless staleness, priced in.
 *
 * <p><b>Faction wallets — deliberately deferred.</b> NeroEconomy's ledger accepts non-player
 * {@code AccountId} kinds at runtime, but {@code EconomySavedData.parseAccount} keeps only
 * {@code kind == "player"} on load ({@code if (!"player".equals(kind)) return null;}), so a
 * faction-treasury balance would silently evaporate on every server restart. Shipping that
 * would violate "genuinely works"; the {@link FactionAccountId} record is the ready-made seam
 * and stays dormant until NeroEconomy persists foreign account kinds.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> pricing reads the buyer's own standing in memory, on the
 * server, at quote time; it stores nothing new, logs no player identifier and transmits nothing
 * off the server.
 */
public final class EconomyIntegration {

    private EconomyIntegration() {
    }

    /**
     * Registers the {@code "nerofactions"} price modifier. Called once, from
     * {@code Integrations.init()}, only when NeroEconomy is present; any {@link LinkageError} a
     * future NeroEconomy might provoke is caught at that guard boundary, not here.
     */
    public static void register() {
        PriceModifierRegistry.register(NeroFactionsCommon.MOD_ID, EconomyIntegration::factionMultiplier);
    }

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
