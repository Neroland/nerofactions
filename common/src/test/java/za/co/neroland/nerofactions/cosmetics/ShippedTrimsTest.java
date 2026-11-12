package za.co.neroland.nerofactions.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;

class ShippedTrimsTest {

    @Test
    void eachFactionHasASmithingRecipeForItsOwnMaterialAndInnerCircleRewards() throws Exception {
        Path main = sources();
        Path common = main.resolve("resources");
        for (OutpostFaction faction : OutpostFaction.values()) {
            if (faction == OutpostFaction.NONE) {
                continue;
            }
            String name = faction.getSerializedName();
            String file = name + ".json";
            String pattern = faction.factionId().toString();
            String template = pattern + "_armor_trim_smithing_template";
            String material = pattern + "_trim_material";
            JsonObject recipe = read(common, "data/nerofactions/recipe/" + name + "_armor_trim.json");
            assertEquals("minecraft:smithing_trim", recipe.get("type").getAsString());
            assertEquals("#minecraft:trimmable_armor", recipe.get("base").getAsString());
            assertEquals(pattern, recipe.get("pattern").getAsString());
            assertEquals(template, recipe.get("template").getAsString());
            assertEquals(material, recipe.get("addition").getAsString());
            assertEquals(template, read(common, "data/nerofactions/trim_pattern/" + file)
                    .get("template_item").getAsString());
            assertEquals(material, read(common, "data/nerofactions/trim_material/" + file)
                    .get("ingredient").getAsString());
            assertFalse(read(main.resolve("resources-1.21.5"), "data/nerofactions/trim_material/" + file)
                    .has("ingredient"));
            assertEquals("nerofactions:trim/" + name,
                    read(main.resolve("resources-26.3"), "data/nerofactions/trim_material/" + file)
                            .get("palette_id").getAsString());
            assertTrue(Files.isRegularFile(common.resolve(
                    "assets/nerofactions/textures/palettes/trim/" + name + ".png")));
            FactionDefinition definition = FactionDefinition.CODEC.parse(JsonOps.INSTANCE,
                    read(common, "data/nerofactions/nerofactions/factions/" + file)).getOrThrow();
            assertEquals(faction.factionId(), definition.cosmetics().orElseThrow().trimMaterial());
            for (String item : List.of(template, material)) {
                for (FactionTier tier : FactionTier.values()) {
                    int quantity = definition.rewardsFor(tier).stream()
                            .filter(reward -> reward.item().filter(id -> id.toString().equals(item)).isPresent())
                            .mapToInt(reward -> reward.count()).sum();
                    assertEquals(tier == FactionTier.INNER_CIRCLE ? 4 : 0, quantity, item);
                }
            }
        }
    }

    private static Path sources() {
        Path root = Path.of("").toAbsolutePath();
        Path relative = Path.of("common/src/main/resources");
        while (root != null && !Files.isDirectory(root.resolve(relative))) {
            root = root.getParent();
        }
        assertNotNull(root);
        return root.resolve(relative).getParent();
    }

    private static JsonObject read(Path root, String file) throws Exception {
        return JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject();
    }
}
