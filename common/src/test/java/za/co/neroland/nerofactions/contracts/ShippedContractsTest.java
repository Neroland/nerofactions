package za.co.neroland.nerofactions.contracts;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class ShippedContractsTest {
    @Test
    void everyFactionHasSixValidStandaloneContractsAndThreeOutsiderChoices() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("common/src/main/resources"))) root = root.getParent();
        assertNotNull(root);
        var directory = root.resolve("common/src/main/resources/data/nerofactions/nerofactions/contracts");
        var contracts = new ArrayList<ContractDefinition>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path));
                var contract = ContractDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                assertTrue(contract.problem().isEmpty(), path.getFileName().toString());
                assertEquals(path.getParent().getFileName().toString(), contract.faction().getPath());
                assertTrue(contract.target().startsWith("minecraft:") || contract.target().equals("#nerofactions:contract_landmarks"));
                contracts.add(contract);
            }
        }
        assertTrue(contracts.size() >= 42);
        for (String faction : java.util.List.of("free_colonists", "miner_union", "nero_corporation", "salvagers",
                "space_guild", "terraforming_authority", "void_cult")) {
            var id = Identifier.fromNamespaceAndPath("nerofactions", faction);
            var pool = contracts.stream().filter(c -> c.faction().equals(id)).toList();
            assertTrue(pool.size() >= 6, faction);
            assertTrue(pool.stream().filter(c -> c.minTier() == 0).count() >= 3, faction);
            assertTrue(pool.stream().anyMatch(c -> c.minTier() >= 3), faction);
        }
    }

    @Test
    void invalidBoundsTypesAndTargetsAreRejected() {
        String base = """
                {"faction":"nerofactions:miner_union","type":"deliver","target":"minecraft:iron_ingot",
                "count":16,"reputation":25}
                """;
        var json = JsonParser.parseString(base).getAsJsonObject();
        json.addProperty("count", 0);
        assertTrue(ContractDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        json.addProperty("count", 16);
        json.addProperty("type", "free_reward");
        assertTrue(ContractDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().problem().isPresent());
        json.addProperty("type", "visit_structure");
        assertTrue(ContractDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().problem().isPresent());
        json.addProperty("target", "#not a tag");
        assertTrue(ContractDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().problem().isPresent());
    }
}
