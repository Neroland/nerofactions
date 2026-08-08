package za.co.neroland.nerofactions.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

/**
 * <b>The load-bearing content test:</b> the seven faction JSONs shipped in
 * {@code common/src/main/resources} must parse with zero codec complaints and cross-validate with
 * zero {@link ValidationIssue}s — which statically proves {@code /nerofactions reload-check}
 * reports clean on a stock install. Runs the same decode ({@code resultOrPartial}) and the same
 * package-private {@link FactionDefinitions#validate} the real loader runs; only the resource
 * walk and the item-registry seam differ (no server, no registry bootstrap in a plain JVM).
 *
 * <p>The item seam is a curated list of the exact vanilla items the shipped content is allowed to
 * reference. A typo'd or non-vanilla item id therefore fails this test the same way an unknown
 * registry id would fail the real load.
 */
class ShippedFactionContentTest {

    private static final Set<String> SHIPPED_FACTIONS = Set.of(
            "space_guild", "miner_union", "nero_corporation", "void_cult",
            "terraforming_authority", "free_colonists", "salvagers");

    /** Every vanilla item the shipped reward tables may name. Extend when content does. */
    private static final Set<String> KNOWN_VANILLA_ITEMS = Set.of(
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
            "minecraft:diamond", "minecraft:emerald", "minecraft:coal", "minecraft:redstone",
            "minecraft:spyglass", "minecraft:name_tag", "minecraft:amethyst_shard",
            "minecraft:ender_pearl", "minecraft:ender_eye", "minecraft:bone_meal",
            "minecraft:glow_berries", "minecraft:oak_sapling", "minecraft:bread",
            "minecraft:cooked_beef", "minecraft:lantern", "minecraft:golden_carrot",
            "minecraft:anvil");

    private static final Predicate<Identifier> ITEM_EXISTS =
            id -> KNOWN_VANILLA_ITEMS.contains(id.toString());

    @Test
    void allSevenShippedFactionsParseAndValidateClean() throws Exception {
        Path factionsDir = shippedFactionsDir();

        Map<Identifier, FactionDefinition> parsed = new LinkedHashMap<>();
        List<String> decodeComplaints = new ArrayList<>();
        try (Stream<Path> files = Files.list(factionsDir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".json")) {
                    continue;
                }
                Identifier id = Identifier.fromNamespaceAndPath(
                        "nerofactions", name.substring(0, name.length() - ".json".length()));
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    Optional<FactionDefinition> definition =
                            FactionDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                                    .resultOrPartial(error ->
                                            decodeComplaints.add(id + ": " + error));
                    if (definition.isEmpty()) {
                        fail("Shipped faction " + id + " did not decode at all");
                    }
                    parsed.put(id, definition.get().withId(id));
                }
            }
        }
        assertEquals(List.of(), decodeComplaints,
                "shipped JSONs must decode without a single codec complaint");
        assertEquals(SHIPPED_FACTIONS,
                parsed.keySet().stream().map(Identifier::getPath)
                        .collect(java.util.stream.Collectors.toSet()),
                "exactly the seven designed factions must ship");

        List<ValidationIssue> issues = new ArrayList<>();
        Map<Identifier, FactionDefinition> validated =
                FactionDefinitions.validate(parsed, ITEM_EXISTS, issues);

        assertEquals(List.of(), issues,
                "shipped content must cross-validate with zero issues (reload-check reports clean)");
        assertEquals(7, validated.size(), "all seven factions must survive validation");

        // Spot-check the designed shape survived end to end.
        FactionDefinition spaceGuild =
                validated.get(Identifier.fromNamespaceAndPath("nerofactions", "space_guild"));
        assertNotNull(spaceGuild);
        assertEquals(0, spaceGuild.threshold(FactionTier.OUTSIDER));
        assertEquals(2500, spaceGuild.threshold(FactionTier.INNER_CIRCLE));
        assertEquals(2, spaceGuild.enemies().size(), "space_guild ships two enemies");
        assertTrue(spaceGuild.cosmetics().isPresent(), "every shipped faction has a cosmetics block");
        for (FactionDefinition faction : validated.values()) {
            assertTrue(faction.cosmetics().isPresent(),
                    faction.id() + " must ship a cosmetics block");
            for (TradeModifier trade : faction.trade()) {
                for (FactionTier tier : FactionTier.values()) {
                    double multiplier = trade.multiplierFor(tier);
                    assertTrue(multiplier >= TradeModifier.MIN_MULTIPLIER
                                    && multiplier <= TradeModifier.MAX_MULTIPLIER,
                            faction.id() + " trade " + trade.tag() + " multiplier in band");
                }
            }
        }
    }

    /**
     * Locates {@code common/src/main/resources/.../factions} robustly under gradle: the test JVM's
     * working directory differs per node, so walk up from {@code user.dir} until the shipped
     * resource tree appears.
     */
    private static Path shippedFactionsDir() {
        Path relative = Path.of("common", "src", "main", "resources",
                "data", "nerofactions", "nerofactions", "factions");
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate the shipped faction resources from "
                + System.getProperty("user.dir"));
    }
}
