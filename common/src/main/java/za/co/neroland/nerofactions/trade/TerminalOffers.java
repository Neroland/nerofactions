package za.co.neroland.nerofactions.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.TradeModifier;

/**
 * The pure trade-terminal offer construction: faction definition + the player's resolved tier +
 * the config caps in, a list of {@link OfferSpec}s out. <b>No Minecraft registries, no
 * ItemStacks</b> — the tag→items resolution is injected ({@code specialityItems}), so the
 * plain-JVM tests drive this deterministically and the Merchant/menu layer
 * ({@code TradeTerminalBlock} / {@code TerminalMerchant}) stays a thin adapter.
 *
 * <p><b>The shape of a faction's shop</b> (locked here, documented once):
 *
 * <ol>
 *   <li><b>Buy-side — the earning loop.</b> For each of the faction's {@code trade} speciality
 *       tags, the terminal buys the tag's vanilla items from the player for emeralds (selling
 *       speciality goods to your faction is the promised gather/deliver loop; the completed trade
 *       also awards {@code Source.TRADE} reputation, see {@code TerminalMerchant}). The item
 *       count the terminal demands per emerald is {@code round(4 × m)} where {@code m} is the
 *       faction's per-tier multiplier for that tag, clamped FactionPricing-style to
 *       {@code [1 − discountCap/100, 1 + surchargeCap/100]} — a friendlier multiplier means fewer
 *       items per emerald. Emeralds themselves are filtered out of every speciality list (a tag
 *       like {@code c:gems} may contain them; buying emeralds with emeralds is not a trade).</li>
 *   <li><b>Sell-side — a small curated set.</b> The terminal sells a fixed, curated list of plain
 *       vanilla goods for emeralds. The emerald price scales by the faction's <em>best</em>
 *       (lowest) clamped multiplier at the player's tier — loyalty makes the whole counter
 *       cheaper — and never leaves the same clamped band.</li>
 *   <li><b>Tier scales quality and quantity.</b> Higher tier → more speciality items bought per
 *       tag ({@link #specialityItemsFor}), more curated goods unlocked
 *       ({@link #curatedCountFor}), more uses per offer, and (through the per-tier multipliers)
 *       better rates.</li>
 *   <li><b>The faction banner.</b> At {@link FactionTier#MEMBER}+ (and only when the faction
 *       ships a cosmetics block) the terminal sells the faction's pre-styled vanilla banner
 *       ({@code FactionBanners}) for a flat 3 emeralds — cheap by design; it is identity, not
 *       loot.</li>
 * </ol>
 *
 * <p>Offer counts and base prices are deliberate constants, not config — the config knobs are the
 * price caps (shared with the NeroEconomy integration) and {@code tradeAwardBase}.
 */
public final class TerminalOffers {

    /** Base speciality items demanded per emerald before the per-tier multiplier. */
    static final int BASE_ITEMS_PER_EMERALD = 4;

    /** Flat emerald price of the faction banner (MEMBER+ only). */
    static final int BANNER_EMERALD_COST = 3;

    static final Identifier EMERALD = Identifier.withDefaultNamespace("emerald");

    /**
     * The curated sell-side catalogue, unlocked front-to-back by tier: plain, useful vanilla
     * goods only — nothing that competes with faction rewards or gated recipes.
     */
    static final List<CuratedGood> CURATED = List.of(
            new CuratedGood(1, Identifier.withDefaultNamespace("bread"), 6),
            new CuratedGood(1, Identifier.withDefaultNamespace("torch"), 16),
            new CuratedGood(2, Identifier.withDefaultNamespace("iron_ingot"), 3),
            new CuratedGood(2, Identifier.withDefaultNamespace("glass"), 8),
            new CuratedGood(3, Identifier.withDefaultNamespace("redstone"), 8),
            new CuratedGood(3, Identifier.withDefaultNamespace("ender_pearl"), 1));

    private TerminalOffers() {
    }

    /** One curated sell-side line: {@code emeraldCost} emeralds buy {@code count} × {@code item}. */
    record CuratedGood(int emeraldCost, Identifier item, int count) {
    }

    /**
     * One offer the terminal will show. {@code result} empty means "the faction's banner stack"
     * ({@code FactionBanners} builds it game-side).
     *
     * @param costItem    what the player pays
     * @param costCount   how many of it
     * @param result      what the player receives, or empty for the faction banner
     * @param resultCount how many of the result (ignored for the banner — always one)
     * @param maxUses     vanilla per-offer use cap until the terminal is closed and reopened
     */
    public record OfferSpec(Identifier costItem, int costCount, Optional<Identifier> result,
            int resultCount, int maxUses) {

        static OfferSpec of(Identifier costItem, int costCount, Identifier result,
                int resultCount, int maxUses) {
            return new OfferSpec(costItem, costCount, Optional.of(result), resultCount, maxUses);
        }

        static OfferSpec banner(int emeraldCost, int maxUses) {
            return new OfferSpec(EMERALD, emeraldCost, Optional.empty(), 1, maxUses);
        }

        /** Whether this offer's result is the faction's pre-styled banner. */
        public boolean isBanner() {
            return result.isEmpty();
        }
    }

    /**
     * Builds the offer list for one faction at one tier. Pure: reads only its arguments.
     *
     * @param faction             the (validated) faction definition
     * @param tier                the player's resolved standing tier with it
     * @param specialityItems     resolves a trade-modifier tag id to the vanilla item ids it
     *                            contains (or, when the id names an item directly, that item —
     *                            the {@code FactionPricing} speciality-match convention); the
     *                            game-side resolver filters to the {@code minecraft:} namespace
     * @param discountCapPercent  config cap, re-clamped here to its 0–50 band
     * @param surchargeCapPercent config cap, re-clamped here to its 0–100 band
     */
    public static List<OfferSpec> specs(FactionDefinition faction, FactionTier tier,
            Function<Identifier, List<Identifier>> specialityItems,
            int discountCapPercent, int surchargeCapPercent) {
        if (faction == null || tier == null || specialityItems == null) {
            return List.of();
        }
        double floor = 1.0D - Math.clamp(discountCapPercent, 0, 50) / 100.0D;
        double ceiling = 1.0D + Math.clamp(surchargeCapPercent, 0, 100) / 100.0D;
        int maxUses = maxUsesFor(tier);
        List<OfferSpec> out = new ArrayList<>();

        // 1. Buy-side: the terminal buys the faction's speciality goods for emeralds.
        int perTag = specialityItemsFor(tier);
        double best = 1.0D;
        for (TradeModifier modifier : faction.trade()) {
            double multiplier = Math.clamp(modifier.multiplierFor(tier), floor, ceiling);
            best = Math.min(best, multiplier);
            int taken = 0;
            for (Identifier item : specialityItems.apply(modifier.tag())) {
                if (item == null || EMERALD.equals(item)) {
                    continue; // never buy emeralds with emeralds
                }
                int demanded = (int) Math.clamp(Math.round(BASE_ITEMS_PER_EMERALD * multiplier), 1, 64);
                out.add(OfferSpec.of(item, demanded, EMERALD, 1, maxUses));
                if (++taken >= perTag) {
                    break;
                }
            }
        }

        // 2. Sell-side: the curated vanilla goods, priced by the faction's best clamped rate.
        int curated = curatedCountFor(tier);
        for (int index = 0; index < curated && index < CURATED.size(); index++) {
            CuratedGood good = CURATED.get(index);
            int price = (int) Math.clamp(Math.round(good.emeraldCost() * best), 1, 64);
            out.add(OfferSpec.of(EMERALD, price, good.item(), good.count(), maxUses));
        }

        // 3. The faction banner, MEMBER+ and only for factions that ship a cosmetics block.
        if (tier.ordinal() >= FactionTier.MEMBER.ordinal() && faction.cosmetics().isPresent()) {
            out.add(OfferSpec.banner(BANNER_EMERALD_COST, 4));
        }
        return List.copyOf(out);
    }

    /** How many items of each speciality tag the terminal buys: 1 / 1 / 2 / 2 / 3 by tier. */
    static int specialityItemsFor(FactionTier tier) {
        return 1 + tier.ordinal() / 2;
    }

    /** How many curated goods are unlocked: 2 / 3 / 4 / 5 / 6 by tier. */
    static int curatedCountFor(FactionTier tier) {
        return 2 + tier.ordinal();
    }

    /** Per-offer use cap: 8 / 12 / 16 / 20 / 24 by tier. */
    static int maxUsesFor(FactionTier tier) {
        return 8 + 4 * tier.ordinal();
    }
}
