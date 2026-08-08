package za.co.neroland.nerofactions.integration.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.TradeModifier;

/**
 * The pricing decision core, tested exhaustively in a plain JVM — deliberately possible because
 * {@link FactionPricing} imports no NeroEconomy type (that is the whole point of splitting it from
 * the {@code EconomyIntegration} adapter). Definitions are constructed directly, so "hostile
 * datapack values" that the real loader would clamp at load can be pushed straight at the math.
 */
class FactionPricingTest {

    private static final Identifier IRON_TAG = Identifier.parse("c:ingots/iron");
    private static final Identifier IRON_INGOT = Identifier.parse("minecraft:iron_ingot");
    private static final Identifier DIAMOND = Identifier.parse("minecraft:diamond");

    private static final Identifier GUILD = Identifier.parse("nerofactions:space_guild");
    private static final Identifier UNION = Identifier.parse("nerofactions:miner_union");
    private static final Identifier SALVAGERS = Identifier.parse("nerofactions:salvagers");

    // --- nothing to say -> exactly 1.0 ----------------------------------------------------------

    @Test
    void unknownBuyerPaysListPrice() {
        Map<Identifier, FactionDefinition> defs = Map.of(GUILD, guildLike(GUILD, 0.9D));
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(), defs, 15, 25));
    }

    @Test
    void outsiderStandingGrantsNoDiscount() {
        Map<Identifier, FactionDefinition> defs = Map.of(GUILD, guildLike(GUILD, 0.9D));
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, FactionTier.OUTSIDER), defs, 15, 25));
    }

    @Test
    void noSpecialityMatchMeansListPriceEvenAtInnerCircle() {
        Map<Identifier, FactionDefinition> defs = Map.of(GUILD, guildLike(GUILD, 0.9D));
        assertEquals(1.0D, FactionPricing.multiplier(DIAMOND, Set.of(),
                Map.of(GUILD, FactionTier.INNER_CIRCLE), defs, 15, 25));
    }

    @Test
    void nullAndEmptyInputsDegradeToListPrice() {
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, FactionTier.MEMBER), Map.of(), 15, 25));
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                null, Map.of(GUILD, guildLike(GUILD, 0.9D)), 15, 25));
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, FactionTier.MEMBER), null, 15, 25));
    }

    // --- the discount side ----------------------------------------------------------------------

    @Test
    void discountAppliesFromAssociateAndFollowsTheTierTable() {
        FactionDefinition guild = faction(GUILD, List.of(new TradeModifier(IRON_TAG, Map.of(
                "associate", 0.95D, "member", 0.9D, "trusted", 0.88D, "inner_circle", 0.86D))));
        Map<Identifier, FactionDefinition> defs = Map.of(GUILD, guild);
        assertEquals(0.95D, priceFor(defs, FactionTier.ASSOCIATE));
        assertEquals(0.9D, priceFor(defs, FactionTier.MEMBER));
        assertEquals(0.88D, priceFor(defs, FactionTier.TRUSTED));
        assertEquals(0.86D, priceFor(defs, FactionTier.INNER_CIRCLE));
    }

    @Test
    void aTierWithNoTableEntryMultipliesByOne() {
        // Shipped packs may start a speciality at Member; Associate then gets no discount.
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("member", 0.9D))));
        assertEquals(1.0D, priceFor(Map.of(GUILD, guild), FactionTier.ASSOCIATE));
        assertEquals(0.9D, priceFor(Map.of(GUILD, guild), FactionTier.MEMBER));
    }

    @Test
    void specialityMayNameTheItemIdDirectly() {
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_INGOT, Map.of("member", 0.9D))));
        // No listing tags at all — the modifier's tag field equals the item id.
        assertEquals(0.9D, FactionPricing.multiplier(IRON_INGOT, Set.of(),
                Map.of(GUILD, FactionTier.MEMBER), Map.of(GUILD, guild), 15, 25));
    }

    @Test
    void bestSingleDiscountWinsAndDiscountsNeverStack() {
        FactionDefinition guild = guildLike(GUILD, 0.95D);
        FactionDefinition union = guildLike(UNION, 0.9D);
        Map<Identifier, FactionDefinition> defs = Map.of(GUILD, guild, UNION, union);
        Map<Identifier, FactionTier> tiers =
                Map.of(GUILD, FactionTier.MEMBER, UNION, FactionTier.MEMBER);
        // Best-of (0.9), never the product (0.855).
        assertEquals(0.9D,
                FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG), tiers, defs, 15, 25));
    }

    @Test
    void ownTableMarkupsAreTreatedAsNoDiscount() {
        // Friendship-only trade tables: a matching multiplier above 1.0 never applies.
        FactionDefinition guild = guildLike(GUILD, 1.4D);
        assertEquals(1.0D, priceFor(Map.of(GUILD, guild), FactionTier.MEMBER));
    }

    // --- the caps, against hostile values -------------------------------------------------------

    @Test
    void hostileDatapackDiscountIsClampedToTheFloor() {
        FactionDefinition guild = guildLike(GUILD, 0.1D); // loader would clamp; the math must too
        assertEquals(0.85D, priceFor(Map.of(GUILD, guild), FactionTier.MEMBER));
    }

    @Test
    void capArgumentsOutsideTheirConfigBandsAreReclamped() {
        FactionDefinition guild = guildLike(GUILD, 0.1D);
        // A caller asking for a 90% discount cap gets the band maximum (50).
        assertEquals(0.5D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, FactionTier.MEMBER), Map.of(GUILD, guild), 90, 25));
        // Negative caps collapse to 0 — no discount, no surcharge, exactly 1.0.
        FactionDefinition hostileGuild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("member", 0.9D))), SALVAGERS);
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, FactionTier.MEMBER, SALVAGERS, FactionTier.MEMBER),
                Map.of(GUILD, hostileGuild), -5, -5));
    }

    @Test
    void resultAlwaysStaysInsideTheBand() {
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("member", 0.1D))), SALVAGERS);
        for (FactionTier buyerTier : FactionTier.values()) {
            for (FactionTier salvagerTier : FactionTier.values()) {
                double result = FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                        Map.of(GUILD, buyerTier, SALVAGERS, salvagerTier),
                        Map.of(GUILD, guild), 15, 25);
                assertTrue(result >= 0.85D && result <= 1.25D,
                        buyerTier + "/" + salvagerTier + " -> " + result);
            }
        }
    }

    // --- the enemy surcharge --------------------------------------------------------------------

    @Test
    void surchargeWhenTheListingsFactionListsTheBuyersFactionAsEnemy() {
        // Guild speciality listing; guild lists salvagers as an enemy; buyer runs with salvagers.
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("member", 0.9D))), SALVAGERS);
        double result = FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(SALVAGERS, FactionTier.ASSOCIATE), Map.of(GUILD, guild), 15, 25);
        assertEquals(1.25D, result, "flat surcharge = 1 + surchargeCapPercent/100");
    }

    @Test
    void surchargeIsOneDirectionOnly() {
        // The BUYER's faction hating the listing's faction does not surcharge: only the goods'
        // aligned faction's own enemy list prices its hostility (see FactionPricing javadoc).
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("member", 0.9D)))); // no enemies
        FactionDefinition salvagers = faction(SALVAGERS, List.of(), GUILD); // salvagers hate guild
        double result = FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(SALVAGERS, FactionTier.MEMBER),
                Map.of(GUILD, guild, SALVAGERS, salvagers), 15, 25);
        assertEquals(1.0D, result);
    }

    @Test
    void surchargeIgnoresOutsiderStandingWithTheEnemy() {
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("member", 0.9D))), SALVAGERS);
        assertEquals(1.0D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(SALVAGERS, FactionTier.OUTSIDER), Map.of(GUILD, guild), 15, 25));
    }

    @Test
    void surchargeAppliesOnceAndComposesWithTheDiscount() {
        // Buyer is Trusted with the guild AND runs with the guild's enemy: both rules apply once —
        // 0.85 * 1.25 = 1.0625, inside the band. Two hostile factions still surcharge only once.
        FactionDefinition guild = faction(GUILD,
                List.of(new TradeModifier(IRON_TAG, Map.of("trusted", 0.85D))), SALVAGERS, UNION);
        double result = FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, FactionTier.TRUSTED,
                        SALVAGERS, FactionTier.ASSOCIATE, UNION, FactionTier.MEMBER),
                Map.of(GUILD, guild), 15, 25);
        assertEquals(0.85D * 1.25D, result, 1.0E-9);
    }

    // --- the shipped content feeds through as designed ------------------------------------------

    @Test
    void shippedSpaceGuildTableFeedsThroughEndToEnd() throws Exception {
        FactionDefinition guild = loadShipped("space_guild").withId(GUILD);
        Map<Identifier, FactionDefinition> defs = Map.of(GUILD, guild);
        // The shipped iron speciality: member 0.9, inner_circle 0.8 (floor 0.85 clamps it).
        assertEquals(0.9D, priceFor(defs, FactionTier.MEMBER));
        assertEquals(0.85D, priceFor(defs, FactionTier.INNER_CIRCLE),
                "the shipped 0.8 is capped by the default 15% discount cap");
        // The shipped enemy graph: space_guild lists salvagers, so a salvager pays the surcharge.
        assertEquals(1.25D, FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(SALVAGERS, FactionTier.MEMBER), defs, 15, 25));
    }

    // --- helpers --------------------------------------------------------------------------------

    private static double priceFor(Map<Identifier, FactionDefinition> defs, FactionTier guildTier) {
        return FactionPricing.multiplier(IRON_INGOT, Set.of(IRON_TAG),
                Map.of(GUILD, guildTier), defs, 15, 25);
    }

    /** A faction whose iron speciality has the same multiplier at every earned tier. */
    private static FactionDefinition guildLike(Identifier id, double ironMultiplier) {
        return faction(id, List.of(new TradeModifier(IRON_TAG, Map.of(
                "associate", ironMultiplier, "member", ironMultiplier,
                "trusted", ironMultiplier, "inner_circle", ironMultiplier))));
    }

    private static FactionDefinition faction(Identifier id, List<TradeModifier> trade,
            Identifier... enemies) {
        return new FactionDefinition(id, id.getPath(), "test faction",
                Map.of("outsider", 0, "associate", 100, "member", 400,
                        "trusted", 1000, "inner_circle", 2500),
                Map.of(), List.of(enemies), trade, Optional.empty());
    }

    /** Decodes one shipped faction JSON (same walk-up locating as ShippedFactionContentTest). */
    private static FactionDefinition loadShipped(String name) throws Exception {
        Path relative = Path.of("common", "src", "main", "resources",
                "data", "nerofactions", "nerofactions", "factions", name + ".json");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                try (BufferedReader reader = Files.newBufferedReader(candidate)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    return FactionDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                            .resultOrPartial(error -> fail(name + ": " + error))
                            .orElseThrow();
                }
            }
            current = current.getParent();
        }
        return fail("could not locate shipped faction " + name);
    }
}
