package za.co.neroland.nerofactions.content;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FactionPresentationTest {
    private static final String OLD = """
            {"display_name":"Legacy faction","theme":"A legacy pack",
            "tiers":{"outsider":0,"associate":100,"member":400,"trusted":1000,"inner_circle":2500}}
            """;

    @Test
    void oldFormatLoadsUnchangedWithPresentationDefaults() {
        var result = FactionDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(OLD)).getOrThrow();
        assertEquals("Legacy faction", result.displayName());
        assertEquals("#90B4A0", result.color());
        assertEquals("", result.motto());
        assertEquals("", result.descriptionKey());
        assertEquals(Identifier.parse("minecraft:book"), result.icon());
    }

    @Test
    void presentationSurvivesValidationWithersAndRejectsBadColor() {
        var json = JsonParser.parseString(OLD).getAsJsonObject();
        json.addProperty("color", "#D5A44C");
        json.addProperty("motto", "Our hands, our share.");
        json.addProperty("icon", "minecraft:iron_pickaxe");
        json.addProperty("description_key", "faction.test.description");
        var faction = FactionDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        var copy = faction.withId(Identifier.parse("nerofactions:test")).withTiers(faction.tiers())
                .withRewards(faction.rewards()).withEnemies(faction.enemies()).withTrade(faction.trade());
        assertEquals(faction.color(), copy.color());
        assertEquals(faction.motto(), copy.motto());
        assertEquals(faction.icon(), copy.icon());
        assertEquals(faction.descriptionKey(), copy.descriptionKey());
        json.addProperty("color", "not a color");
        assertTrue(FactionDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
