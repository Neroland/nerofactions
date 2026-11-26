package za.co.neroland.nerofactions.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerofactions.content.FactionDefinition;
import za.co.neroland.nerofactions.content.FactionTier;
import za.co.neroland.nerofactions.content.faction.OutpostFaction;

class ShippedBannerPatternsTest {

    @Test
    void everyFactionShipsOneLoomPatternAndGrantsItsItemOnlyAtInnerCircle() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path relative = Path.of("common/src/main/resources");
        while (root != null && !Files.isDirectory(root.resolve(relative))) {
            root = root.getParent();
        }
        assertNotNull(root);
        Path data = root.resolve(relative).resolve("data/nerofactions");
        Path assets = root.resolve(relative).resolve("assets/nerofactions");
        var loomItems = read(root.resolve(relative).resolve("data/minecraft/tags/item/loom_patterns.json"))
                .getAsJsonArray("values");
        assertEquals(7, loomItems.size());
        for (OutpostFaction faction : OutpostFaction.values()) {
            if (faction == OutpostFaction.NONE) {
                continue;
            }
            String name = faction.getSerializedName();
            String item = "nerofactions:" + name + "_banner_pattern";
            String file = name + ".json";
            assertTrue(loomItems.contains(new JsonPrimitive(item)), item);
            //? if >=1.20.5 {
            var pattern = BannerPattern.DIRECT_CODEC.parse(JsonOps.INSTANCE,
                    read(data.resolve("banner_pattern").resolve(file)));
            assertTrue(pattern.isSuccess(), pattern.toString());
            //?} else {
            /*var pattern = read(data.resolve("banner_pattern").resolve(file));
            assertEquals(faction.factionId().toString(), pattern.get("asset_id").getAsString());
            var legacy = new BannerPattern(faction.factionId().toString());
            assertEquals(faction.factionId().toString(), legacy.getHashname());
            *///?}
            var loomTag = read(data.resolve("tags/banner_pattern/pattern_item").resolve(file)).getAsJsonArray("values");
            assertEquals(1, loomTag.size());
            assertEquals(faction.factionId().toString(), loomTag.get(0).getAsString());
            var definition = FactionDefinition.CODEC.parse(JsonOps.INSTANCE,
                    read(data.resolve("nerofactions/factions").resolve(file))).getOrThrow();
            assertEquals(faction.factionId(), definition.cosmetics().orElseThrow().bannerPattern());
            for (FactionTier tier : FactionTier.values()) {
                assertEquals(tier == FactionTier.INNER_CIRCLE,
                        definition.rewardsFor(tier).stream().anyMatch(reward ->
                                reward.item().filter(id -> id.toString().equals(item)).isPresent()), name);
            }
            assertTrue(Files.isRegularFile(assets.resolve("textures/entity/banner/" + name + ".png")));
            assertTrue(Files.isRegularFile(assets.resolve("textures/entity/shield/" + name + ".png")));
            assertTrue(Files.isRegularFile(assets.resolve("textures/item/" + name + "_banner_pattern.png")));
        }
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
