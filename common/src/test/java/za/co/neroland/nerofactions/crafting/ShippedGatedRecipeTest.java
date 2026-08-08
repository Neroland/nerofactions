package za.co.neroland.nerofactions.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerofactions.content.FactionTier;

/**
 * Structural proof for the shipped recipes: the <b>seven</b> {@code nerofactions:gated} perks (one
 * per shipped faction — the "locked gear" of 0.1.0) plus the one plain, ungated trade-terminal
 * recipe. Each gated wrapper must be well-formed, gate on a shipped faction with a real tier (and,
 * where present, a Core arc gate), and wrap an internally coherent vanilla shaped recipe with
 * vanilla-only ingredients and a vanilla result. A full serializer round-trip needs the
 * recipe-serializer registry (a bootstrapped game), so it is deliberately out of scope here — the
 * dispatch is exercised at runtime by the datapack load itself; this test pins everything a plain
 * JVM can pin.
 */
class ShippedGatedRecipeTest {

    /** Exactly one gated perk per shipped faction. */
    private static final Map<String, String> GATED_BY_FILE = Map.of(
            "space_guild_spyglass.json", "nerofactions:space_guild",
            "miner_union_jukebox.json", "nerofactions:miner_union",
            "nero_corporation_name_tag.json", "nerofactions:nero_corporation",
            "void_cult_ender_chest.json", "nerofactions:void_cult",
            "terraforming_authority_grass_block.json", "nerofactions:terraforming_authority",
            "free_colonists_lantern.json", "nerofactions:free_colonists",
            "salvagers_anvil.json", "nerofactions:salvagers");

    /** The plain (deliberately UNGATED) recipes that also ship. */
    private static final Set<String> UNGATED = Set.of("trade_terminal.json");

    private static final Set<String> CORE_ARC_GATES = Set.of(
            "nerolandcore:industrial_power", "nerolandcore:reached_orbit",
            "nerolandcore:first_colony", "nerolandcore:deep_space");

    @Test
    void everyShippedFactionHasExactlyOneGatedPerkAndAllParseClean() throws Exception {
        Path recipeDir = shippedDir(Path.of("data", "nerofactions", "recipe"));
        Path factionsDir = shippedDir(Path.of("data", "nerofactions", "nerofactions", "factions"));

        Map<String, JsonObject> recipes = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(recipeDir)) {
            for (Path file : files.sorted().toList()) {
                if (!file.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    recipes.put(file.getFileName().toString(),
                            JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        }
        Set<String> expected = new LinkedHashSet<>(GATED_BY_FILE.keySet());
        expected.addAll(UNGATED);
        assertEquals(expected, recipes.keySet(),
                "exactly the seven gated perks plus the ungated terminal recipe ship");

        Set<String> gatedFactions = new LinkedHashSet<>();
        for (Map.Entry<String, JsonObject> entry : recipes.entrySet()) {
            String name = entry.getKey();
            JsonObject recipe = entry.getValue();

            if (UNGATED.contains(name)) {
                // The terminal recipe is a plain vanilla shaped recipe with our block as result.
                assertWrappedRecipeCoherent(name, recipe, "nerofactions:");
                continue;
            }

            assertEquals("nerofactions:gated", recipe.get("type").getAsString(),
                    name + " must use the gated serializer");

            String faction = recipe.get("faction").getAsString();
            assertEquals(GATED_BY_FILE.get(name), faction, name + " gates on its own faction");
            assertTrue(gatedFactions.add(faction), "one gated perk per faction: " + faction);
            assertTrue(Files.isRegularFile(factionsDir.resolve(
                            faction.substring("nerofactions:".length()) + ".json")),
                    name + " faction " + faction + " must exist in the shipped content");

            String tier = recipe.get("tier").getAsString();
            assertTrue(FactionTier.byName(tier).isPresent(),
                    name + " tier '" + tier + "' must be a real tier name");

            if (recipe.has("core_gate")) {
                assertTrue(CORE_ARC_GATES.contains(recipe.get("core_gate").getAsString()),
                        name + " core_gate must be a Core arc gate");
            }

            assertWrappedRecipeCoherent(name, recipe.getAsJsonObject("recipe"), "minecraft:");
        }
        assertEquals(GATED_BY_FILE.size(), gatedFactions.size(),
                "all seven shipped factions carry a gated perk");
    }

    /**
     * The wrapped vanilla recipe: shaped, pattern/key agree, vanilla ingredients, and a result
     * from the expected namespace ({@code minecraft:} for gated perks — no custom gear items in
     * 0.1.0 — or {@code nerofactions:} for the terminal itself).
     */
    private static void assertWrappedRecipeCoherent(String name, JsonObject wrapped,
            String resultNamespacePrefix) {
        assertEquals("minecraft:crafting_shaped", wrapped.get("type").getAsString(),
                name + " wraps a plain vanilla shaped recipe");

        JsonObject key = wrapped.getAsJsonObject("key");
        JsonArray pattern = wrapped.getAsJsonArray("pattern");
        assertTrue(pattern.size() >= 1 && pattern.size() <= 3, name + " pattern height sane");
        int width = pattern.get(0).getAsString().length();
        for (JsonElement rowElement : pattern) {
            String row = rowElement.getAsString();
            assertEquals(width, row.length(), name + " pattern rows must be equal width");
            for (char symbol : row.toCharArray()) {
                assertTrue(symbol == ' ' || key.has(String.valueOf(symbol)),
                        name + " pattern symbol '" + symbol + "' must be declared in key");
            }
        }
        for (Map.Entry<String, JsonElement> ingredient : key.entrySet()) {
            String value = ingredient.getValue().getAsString();
            assertTrue(value.startsWith("minecraft:") || value.startsWith("#minecraft:")
                            || value.startsWith("#c:"),
                    name + " ingredient " + value + " must be vanilla or a common tag");
        }

        JsonObject result = wrapped.getAsJsonObject("result");
        assertTrue(result.get("id").getAsString().startsWith(resultNamespacePrefix),
                name + " result must start with " + resultNamespacePrefix);
        assertTrue(!result.has("count") || result.get("count").getAsInt() >= 1);
    }

    /** Walks up from the test JVM's working dir to the shipped resource tree (per-node cwds). */
    private static Path shippedDir(Path underResources) {
        Path relative = Path.of("common", "src", "main", "resources").resolve(underResources);
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return fail("could not locate " + underResources + " from " + System.getProperty("user.dir"));
    }
}
