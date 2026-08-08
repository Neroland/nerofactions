package za.co.neroland.nerofactions.integration.economy;

import java.util.Map;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.TradeModifier;

/**
 * The pure faction-pricing decision: buyer standing + listing identity + faction definitions in,
 * one price multiplier out. <b>Deliberately free of NeroEconomy imports</b> — this class is plain
 * NeroFactions math, safe to classload (and unit-test) on a server with no NeroEconomy installed;
 * {@link EconomyIntegration} is the thin adapter that feeds it from NeroEconomy's
 * {@code PriceContext} and is the <em>only</em> class importing NeroEconomy types.
 *
 * <p><b>The rules</b> (locked here, documented once):
 *
 * <ol>
 *   <li><b>Speciality match.</b> A faction's {@code trade} table applies to a listing when the
 *       modifier's tag id is among the listing's item tags <em>or</em> equals the listing's item id
 *       (so a pack may specialise a single item without inventing a tag for it).</li>
 *   <li><b>Discount — best-of, never stacked.</b> For every faction in which the buyer holds
 *       {@link FactionTier#ASSOCIATE} or better <em>standing</em> (standing, not membership: tier is
 *       what a faction's traders can see), each matching modifier contributes its per-tier
 *       multiplier at the buyer's tier. The single <em>lowest</em> such multiplier applies; holding
 *       three friendly factions never multiplies three discounts together. A matching multiplier at
 *       or above {@code 1.0} is treated as "no discount" — a faction's own trade table expresses
 *       friendship; hostility is expressed only through the enemy graph.</li>
 *   <li><b>Enemy surcharge — one direction, flat, never stacked.</b> The surcharge applies when a
 *       faction whose specialities match the listing lists as an {@code enemy} some faction the
 *       buyer holds ASSOCIATE+ standing in: the goods' aligned faction dislikes the buyer's
 *       affiliations, so its goods cost the buyer more. The reverse direction (the <em>buyer's</em>
 *       faction hating the goods' faction) deliberately does not surcharge — the seller side sets
 *       the price, and one direction keeps asymmetric enemy graphs meaningful. The surcharge is a
 *       flat {@code 1 + surchargeCapPercent/100}: datapacks cannot express enemy markups (trade
 *       tables are friendship-only, see above), so the operator's cap <em>is</em> the magnitude —
 *       one knob, no hidden second number.</li>
 *   <li><b>Hard caps, regardless of datapack values.</b> The final multiplier (discount ×
 *       surcharge) is clamped to {@code [1 - discountCapPercent/100, 1 + surchargeCapPercent/100]}.
 *       The cap arguments themselves are re-clamped here to their config bands (0–50 / 0–100), so
 *       even a hostile caller cannot widen the band.</li>
 *   <li><b>Nothing to say → exactly 1.0.</b> Unknown buyer, no standing, no matching speciality,
 *       null anything: list price.</li>
 * </ol>
 *
 * <p>NeroEconomy additionally clamps the <em>whole</em> modifier product (all mods' modifiers ×
 * market shocks) to its own configured band, so these caps compose with — never replace — the
 * economy's own safety rail.
 */
public final class FactionPricing {

    private FactionPricing() {
    }

    /**
     * The faction price multiplier for one quote. Pure and side-effect-free: reads only its
     * arguments, never throws on odd input (nulls and out-of-band caps degrade to list price /
     * clamped caps).
     *
     * @param itemId             the listing's item id
     * @param listingTags        the item tags the listing carries
     * @param buyerTiers         the buyer's resolved standing tier per faction id (absent = Outsider)
     * @param factions           the active faction definitions, keyed by id
     * @param discountCapPercent max total discount, percent (config band 0–50)
     * @param surchargeCapPercent max total surcharge, percent (config band 0–100; also the flat
     *                            surcharge magnitude, see the class javadoc)
     * @return the multiplier, always within {@code [1 - dc/100, 1 + sc/100]}
     */
    public static double multiplier(Identifier itemId, Set<Identifier> listingTags,
            Map<Identifier, FactionTier> buyerTiers, Map<Identifier, FactionDefinition> factions,
            int discountCapPercent, int surchargeCapPercent) {
        if (factions == null || factions.isEmpty() || buyerTiers == null || buyerTiers.isEmpty()) {
            return 1.0D;
        }
        int discountCap = Math.clamp(discountCapPercent, 0, 50);
        int surchargeCap = Math.clamp(surchargeCapPercent, 0, 100);
        double floor = 1.0D - discountCap / 100.0D;
        double ceiling = 1.0D + surchargeCap / 100.0D;

        double best = 1.0D;
        boolean hostile = false;
        for (FactionDefinition faction : factions.values()) {
            boolean matchesListing = false;
            FactionTier buyerTier = tierOrOutsider(buyerTiers, faction.id());
            for (TradeModifier modifier : faction.trade()) {
                if (!matches(modifier.tag(), itemId, listingTags)) {
                    continue;
                }
                matchesListing = true;
                if (buyerTier.ordinal() >= FactionTier.ASSOCIATE.ordinal()) {
                    best = Math.min(best, modifier.multiplierFor(buyerTier));
                }
            }
            if (matchesListing && !hostile) {
                for (Identifier enemy : faction.enemies()) {
                    if (tierOrOutsider(buyerTiers, enemy).ordinal()
                            >= FactionTier.ASSOCIATE.ordinal()) {
                        hostile = true;
                        break;
                    }
                }
            }
        }
        double result = best * (hostile ? 1.0D + surchargeCap / 100.0D : 1.0D);
        return Math.clamp(result, floor, ceiling);
    }

    /** The buyer's tier with this faction; an absent or null entry is {@link FactionTier#OUTSIDER}. */
    private static FactionTier tierOrOutsider(Map<Identifier, FactionTier> buyerTiers, Identifier faction) {
        FactionTier tier = buyerTiers.get(faction);
        return tier == null ? FactionTier.OUTSIDER : tier;
    }

    /** Speciality match: the modifier's tag is among the listing's tags, or names the item itself. */
    private static boolean matches(Identifier specialityTag, Identifier itemId, Set<Identifier> listingTags) {
        if (specialityTag == null) {
            return false;
        }
        return (listingTags != null && listingTags.contains(specialityTag))
                || specialityTag.equals(itemId);
    }
}
