package za.co.neroland.nerofactions.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.Cosmetics;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.TradeModifier;
import za.co.neroland.nerofactions.trade.TerminalOffers.OfferSpec;

/**
 * Plain-JVM tests of the pure terminal-offer construction: speciality mapping (tag → injected
 * item lists), per-tier price scaling through the faction's own multipliers, FactionPricing-style
 * cap clamping, tier-scaled offer counts, the emerald self-trade filter, and the MEMBER+ banner
 * line. The Merchant/menu layer is a thin adapter over this and is exercised at runtime.
 */
class TerminalOffersTest {

    private static final Identifier ORES = Identifier.parse("c:ores");
    private static final Identifier IRON_ORE = Identifier.parse("minecraft:iron_ore");
    private static final Identifier COPPER_ORE = Identifier.parse("minecraft:copper_ore");
    private static final Identifier GOLD_ORE = Identifier.parse("minecraft:gold_ore");
    private static final Identifier GEMS = Identifier.parse("c:gems");
    private static final Identifier DIAMOND = Identifier.parse("minecraft:diamond");
    private static final Identifier EMERALD = Identifier.parse("minecraft:emerald");

    private static final Function<Identifier, List<Identifier>> RESOLVER = tag -> {
        if (ORES.equals(tag)) {
            return List.of(IRON_ORE, COPPER_ORE, GOLD_ORE);
        }
        if (GEMS.equals(tag)) {
            return List.of(DIAMOND, EMERALD); // emerald must be filtered out
        }
        return List.of();
    };

    private static FactionDefinition faction(List<TradeModifier> trade, boolean cosmetics) {
        return new FactionDefinition(Identifier.parse("nerofactions:test_union"),
                "Test Union", "test",
                Map.of("outsider", 0, "associate", 100, "member", 400, "trusted", 1000,
                        "inner_circle", 2500),
                Map.of(), List.of(), trade,
                cosmetics
                        ? Optional.of(new Cosmetics(Identifier.parse("nerofactions:test_union"),
                                Identifier.parse("nerofactions:test_union")))
                        : Optional.empty());
    }

    private static TradeModifier ores(Map<String, Double> byTier) {
        return new TradeModifier(ORES, byTier);
    }

    @Test
    void buySideDemandScalesWithTheTierMultiplier() {
        FactionDefinition union = faction(List.of(ores(
                Map.of("associate", 1.0D, "inner_circle", 0.5D))), false);

        // Associate: multiplier 1.0 → round(4 × 1.0) = 4 items per emerald.
        List<OfferSpec> associate = TerminalOffers.specs(union, FactionTier.ASSOCIATE, RESOLVER, 15, 25);
        OfferSpec buy = associate.get(0);
        assertEquals(IRON_ORE, buy.costItem());
        assertEquals(4, buy.costCount());
        assertEquals(Optional.of(TerminalOffers.EMERALD), buy.result());
        assertEquals(1, buy.resultCount());

        // Inner circle: multiplier 0.5 clamps to the discount floor 0.85 → round(4 × 0.85) = 3.
        List<OfferSpec> inner = TerminalOffers.specs(union, FactionTier.INNER_CIRCLE, RESOLVER, 15, 25);
        assertEquals(3, inner.get(0).costCount(),
                "the datapack multiplier is clamped by the discount cap before pricing");
    }

    @Test
    void capsClampExactlyLikeFactionPricing() {
        // A hostile 1.5 multiplier is clamped by the surcharge cap; caps themselves re-clamp.
        FactionDefinition union = faction(List.of(ores(Map.of("associate", 1.5D))), false);
        List<OfferSpec> specs = TerminalOffers.specs(union, FactionTier.ASSOCIATE, RESOLVER, 400, 10);
        assertEquals(4, specs.get(0).costCount(),
                "surcharge cap 10 → ceiling 1.1 → round(4 × 1.1) = 4; discount cap re-clamped to 50");
    }

    @Test
    void tierScalesSpecialityCountCuratedCountAndUses() {
        assertEquals(1, TerminalOffers.specialityItemsFor(FactionTier.OUTSIDER));
        assertEquals(1, TerminalOffers.specialityItemsFor(FactionTier.ASSOCIATE));
        assertEquals(2, TerminalOffers.specialityItemsFor(FactionTier.MEMBER));
        assertEquals(2, TerminalOffers.specialityItemsFor(FactionTier.TRUSTED));
        assertEquals(3, TerminalOffers.specialityItemsFor(FactionTier.INNER_CIRCLE));

        assertEquals(2, TerminalOffers.curatedCountFor(FactionTier.OUTSIDER));
        assertEquals(6, TerminalOffers.curatedCountFor(FactionTier.INNER_CIRCLE));

        assertEquals(8, TerminalOffers.maxUsesFor(FactionTier.OUTSIDER));
        assertEquals(24, TerminalOffers.maxUsesFor(FactionTier.INNER_CIRCLE));

        FactionDefinition union = faction(List.of(ores(Map.of())), false);
        List<OfferSpec> outsider = TerminalOffers.specs(union, FactionTier.OUTSIDER, RESOLVER, 15, 25);
        List<OfferSpec> inner = TerminalOffers.specs(union, FactionTier.INNER_CIRCLE, RESOLVER, 15, 25);
        // Outsider: 1 speciality + 2 curated; inner circle: 3 speciality + 6 curated.
        assertEquals(3, outsider.size());
        assertEquals(9, inner.size());
        assertTrue(inner.stream().allMatch(spec -> spec.maxUses() == 24));
    }

    @Test
    void emeraldsAreNeverBoughtWithEmeralds() {
        FactionDefinition union = faction(
                List.of(new TradeModifier(GEMS, Map.of())), false);
        List<OfferSpec> specs = TerminalOffers.specs(union, FactionTier.INNER_CIRCLE, RESOLVER, 15, 25);
        assertTrue(specs.stream().noneMatch(spec ->
                        EMERALD.equals(spec.costItem()) && spec.result().map(EMERALD::equals).orElse(false)),
                "no emerald→emerald self-trade");
        assertEquals(DIAMOND, specs.get(0).costItem(), "the gem tag's non-emerald member is bought");
    }

    @Test
    void curatedPricesFollowTheFactionsBestRate() {
        // Best speciality multiplier at trusted is 0.85 (in-cap): a 2-emerald good costs
        // round(2 × 0.85) = 2, a 3-emerald good round(3 × 0.85) = 3 — small but monotone.
        FactionDefinition generous = faction(List.of(ores(Map.of("trusted", 0.5D))), false);
        List<OfferSpec> specs = TerminalOffers.specs(generous, FactionTier.TRUSTED, RESOLVER, 50, 25);
        // Discount cap 50 → floor 0.5, so the 0.5 multiplier survives whole: 1-emerald goods
        // round(1 × 0.5) = 1 (never below 1), 2-emerald goods round(2 × 0.5) = 1.
        OfferSpec bread = specs.stream()
                .filter(spec -> spec.result().map(id -> id.getPath().equals("bread")).orElse(false))
                .findFirst().orElseThrow();
        assertEquals(1, bread.costCount());
        OfferSpec iron = specs.stream()
                .filter(spec -> spec.result().map(id -> id.getPath().equals("iron_ingot")).orElse(false))
                .findFirst().orElseThrow();
        assertEquals(1, iron.costCount(), "half rate on the 2-emerald good");
    }

    @Test
    void bannerSellsAtMemberPlusOnlyAndOnlyWithACosmeticsBlock() {
        FactionDefinition withCosmetics = faction(List.of(), true);
        FactionDefinition withoutCosmetics = faction(List.of(), false);

        assertFalse(TerminalOffers.specs(withCosmetics, FactionTier.ASSOCIATE, RESOLVER, 15, 25)
                .stream().anyMatch(OfferSpec::isBanner), "no banner below member");
        List<OfferSpec> member = TerminalOffers.specs(withCosmetics, FactionTier.MEMBER, RESOLVER, 15, 25);
        OfferSpec banner = member.get(member.size() - 1);
        assertTrue(banner.isBanner(), "the banner is the last offer at member+");
        assertEquals(TerminalOffers.EMERALD, banner.costItem());
        assertEquals(TerminalOffers.BANNER_EMERALD_COST, banner.costCount());
        assertFalse(TerminalOffers.specs(withoutCosmetics, FactionTier.INNER_CIRCLE, RESOLVER, 15, 25)
                .stream().anyMatch(OfferSpec::isBanner),
                "a faction without a cosmetics block sells no banner");
    }

    @Test
    void degenerateInputsProduceNoOffers() {
        assertTrue(TerminalOffers.specs(null, FactionTier.MEMBER, RESOLVER, 15, 25).isEmpty());
        assertTrue(TerminalOffers.specs(faction(List.of(), false), null, RESOLVER, 15, 25).isEmpty());
        assertTrue(TerminalOffers.specs(faction(List.of(), false), FactionTier.MEMBER, null, 15, 25)
                .isEmpty());
    }
}
